package com.seagle.net.socket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Socket channel manager.
 *
 * @author : yuanxiudong66@sina.com
 * @since : 2016/4/28
 */
final class SESocketChannelManager {
    private static final ArrayBlockingQueue<KeyHandleTask> TASK_CACHE = new ArrayBlockingQueue<>(100);
    private volatile Selector mSelector;
    private final ExecutorService mExecutor;
    private volatile boolean mSelecting;
    private volatile static SESocketChannelManager sInstance;

    static synchronized SESocketChannelManager getInstance() {
        if (sInstance == null) {
            sInstance = new SESocketChannelManager();
        }
        return sInstance;
    }

    private SESocketChannelManager() {
        mExecutor = Executors.newSingleThreadExecutor();
        mSelecting = false;
    }

    /**
     * Register socket channel.
     *
     * @param channel     channel
     * @param interestSet select key set
     * @param handler     event handler
     * @return SelectionKey
     * @throws IOException Socket Exception
     */
    synchronized SelectionKey registerChannel(AbstractSelectableChannel channel, int interestSet,
                                              ChannelEventHandler handler) throws IOException {
        if (channel != null) {
            if (mSelector == null) {
                mSelector = Selector.open();
            }
            channel.configureBlocking(false);
            SelectionKey selectionKey = channel.register(mSelector, interestSet, handler);
            if (!mSelecting) {
                mSelecting = true;
                Thread thread = new Thread(new SelectKeyLooper());
                thread.start();
            }
            return selectionKey;
        }
        throw new IllegalArgumentException("Illegal channel!");
    }

    /**
     * Selector key looper.
     */
    private class SelectKeyLooper implements Runnable {
        @Override
        public void run() {
            try {
                while (mSelecting && !Thread.currentThread().isInterrupted()) {
                    try {
                        if (mSelector.select() <= 0) {
                            continue;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                    try {
                        Iterator<SelectionKey> iterator = mSelector.selectedKeys().iterator();
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            KeyHandleTask task = reuseTask(key);
                            mExecutor.submit(task);
                            iterator.remove();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            } finally {
                mSelecting = false;
            }
        }
    }

    private byte[] mergeBytes(byte[] bytes1, byte[] bytes2) {
        int byte1Len = (null != bytes1) ? bytes1.length : 0;
        int byte2Len = (null != bytes2) ? bytes2.length : 0;
        if (byte1Len > 0 || byte2Len > 0) {
            byte[] data = new byte[byte1Len + byte2Len];
            if (byte1Len > 0) {
                System.arraycopy(bytes1, 0, data, 0, byte1Len);
            }
            if (byte2Len > 0) {
                System.arraycopy(bytes2, 0, data, byte1Len, byte2Len);
            }
            return data;
        }
        return null;
    }

    private void dispatchEvent(int eventCode, Object object, Channel channel, ChannelEventHandler handler) {
        ChannelEvent channelEvent = new ChannelEvent(eventCode, object, channel);
        if (handler.handleChannelEvent(channelEvent)) {
            System.out.println("Event handle success!");
        }
    }

    private KeyHandleTask reuseTask(SelectionKey selectionKey) {
        KeyHandleTask task = TASK_CACHE.poll();
        if (task == null) {
            task = new KeyHandleTask(selectionKey);
        } else {
            task.selectionKey = selectionKey;
        }
        return task;
    }

    private void cacheTask(KeyHandleTask task) {
        task.selectionKey = null;
        TASK_CACHE.offer(task);
    }

    /**
     * Selection key handle task.
     */
    private class KeyHandleTask implements Runnable {

        private SelectionKey selectionKey;

        private KeyHandleTask(SelectionKey selectionKey) {
            this.selectionKey = selectionKey;
        }

        @Override
        public void run() {
            if (selectionKey.isConnectable()) {
                handleConnectableKey(selectionKey);
            } else if (selectionKey.isReadable()) {
                handleReadableKey(selectionKey);
            } else if (selectionKey.isWritable()) {
                handleWritableKey(selectionKey);
            } else if (selectionKey.isAcceptable()) {
                handleAcceptableKey(selectionKey);
            }
            cacheTask(this);
        }

        /**
         * Handle connectable key.
         *
         * @param selectionKey SelectionKey
         */
        private void handleConnectableKey(SelectionKey selectionKey) {
            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
            Object obj = selectionKey.attachment();
            if (null != obj && obj instanceof ChannelEventHandler) {
                ChannelEventHandler handler = (ChannelEventHandler) obj;
                try {
                    if (socketChannel.isConnectionPending()) {
                        socketChannel.finishConnect();
                    }
                    socketChannel.configureBlocking(false);
                    dispatchEvent(ChannelEvent.EVENT_CONNECTED, "Connect success", socketChannel, handler);
                } catch (IOException e) {
                    selectionKey.cancel();
                    dispatchEvent(ChannelEvent.EVENT_CONNECT_FAILED, e.getMessage(), socketChannel, handler);
                }
            }
        }

        /**
         * Socket channel client connect accept.
         *
         * @param selectionKey SelectionKey
         */
        private void handleAcceptableKey(SelectionKey selectionKey) {
            final Channel socketChannel = selectionKey.channel();
            Object obj = selectionKey.attachment();
            if (null != obj && obj instanceof ChannelEventHandler) {
                if (socketChannel != null) {
                    try {
                        SocketChannel channel = ((ServerSocketChannel) socketChannel).accept();
                        ChannelEventHandler handler = (ChannelEventHandler) obj;
                        dispatchEvent(ChannelEvent.EVENT_ACCEPT, channel, socketChannel, handler);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        /**
         * Socket channel receive data.
         *
         * @param selectionKey SelectionKey
         */
        private void handleReadableKey(SelectionKey selectionKey) {
            final SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
            Object obj = selectionKey.attachment();
            if (null != obj && obj instanceof ChannelEventHandler) {
                ChannelEventHandler handler = (ChannelEventHandler) obj;
                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                byte[] dataBytes = null;
                try {
                    while (true) {
                        byteBuffer.clear();
                        int length = socketChannel.read(byteBuffer);
                        if (length > 0) {
                            byteBuffer.flip();
                            byte[] newData = new byte[byteBuffer.limit()];
                            byteBuffer.get(newData);
                            dataBytes = mergeBytes(dataBytes, newData);
                        } else if (length == 0) {
                            break;
                        } else {
                            throw new IOException("Stream end!");
                        }
                    }
                    dispatchEvent(ChannelEvent.EVENT_READ, dataBytes, socketChannel, handler);
                } catch (NotYetConnectedException | IOException ex) {
                    ex.printStackTrace();
                    selectionKey.cancel();
                    dispatchEvent(ChannelEvent.EVENT_DISCONNECT, null, socketChannel, handler);
                }
            }
        }

        /**
         * Socket channel prepared write data.
         *
         * @param selectionKey SelectionKey
         */
        private void handleWritableKey(SelectionKey selectionKey) {
            System.out.println("Socket writable event: " + selectionKey);
        }
    }

    /**
     * Selection event handler。
     */
    interface ChannelEventHandler {
        boolean handleChannelEvent(ChannelEvent event);
    }
}
