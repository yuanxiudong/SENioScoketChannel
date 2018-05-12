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
import java.util.concurrent.TimeUnit;

/**
 * Socket channel manager.
 *
 * @author : yuanxiudong66@sina.com
 * @since : 2016/4/28
 */
final class SESocketChannelManager {
    private volatile Selector mSelector;
    private volatile boolean mSelecting;
    private volatile boolean mWaking;
    private final ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

    private volatile static SESocketChannelManager sInstance;

    static synchronized SESocketChannelManager getInstance() {
        if (sInstance == null) {
            sInstance = new SESocketChannelManager();
        }
        return sInstance;
    }

    private SESocketChannelManager() {
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
            //This is too shit.
            mWaking = true;
            mSelector.wakeup();
            SelectionKey selectionKey = channel.register(mSelector, interestSet, handler);
            mWaking = false;
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
                        if (mWaking) {
                            try {
                                TimeUnit.MILLISECONDS.sleep(50);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            continue;
                        }
                        if (mSelector.select() <= 0) {
                            continue;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                    try {
                        for (SelectionKey selectionKey : mSelector.keys()) {
                            System.out.println(selectionKey.interestOps() + "   " + selectionKey.isValid());
                        }
                        Iterator<SelectionKey> iterator = mSelector.selectedKeys().iterator();
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            iterator.remove();
                            if (key.isValid()) {
                                handleSelectionKey(key);
                            }
                        }
                        mSelector.selectedKeys().clear();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            } finally {
                mSelecting = false;
            }
        }
    }

    /**
     * Merge two byte array.
     *
     * @param bytes1 byte array
     * @param bytes2 byte array
     * @return byte array
     */
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

    /**
     * Dispatch channel event to host channel.
     *
     * @param eventCode event code
     * @param object    attach object
     * @param handler   event handler
     */
    private void dispatchEvent(int eventCode, Object object, ChannelEventHandler handler) {
        ChannelEvent channelEvent = ChannelEvent.create(eventCode, object);
        if (handler.handleChannelEvent(channelEvent)) {
            System.out.println("Event handle success!");
        }
        channelEvent.reuse();
    }

    /**
     * Handle selection key.
     *
     * @param selectionKey SelectionKey
     */
    private void handleSelectionKey(SelectionKey selectionKey) {
        if (selectionKey.isConnectable()) {
            System.out.println("isConnectable");
            handleConnectableKey(selectionKey);
        } else if (selectionKey.isReadable()) {
            System.out.println("isReadable");
            handleReadableKey(selectionKey);
        } else if (selectionKey.isWritable()) {
            System.out.println("isWritable");
            handleWritableKey(selectionKey);
        } else if (selectionKey.isAcceptable()) {
            System.out.println("isAcceptable");
            handleAcceptableKey(selectionKey);
        }
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
                    socketChannel.configureBlocking(true);
                    socketChannel.finishConnect();
                }
                socketChannel.configureBlocking(false);
                dispatchEvent(ChannelEvent.EVENT_CONNECTED, null, handler);
            } catch (IOException e) {
                selectionKey.cancel();
                dispatchEvent(ChannelEvent.EVENT_CONNECT_FAILED, e.getMessage(), handler);
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
                    SocketChannel client = ((ServerSocketChannel) socketChannel).accept();
                    ChannelEventHandler handler = (ChannelEventHandler) obj;
                    dispatchEvent(ChannelEvent.EVENT_ACCEPT, client, handler);
                } catch (Exception ex) {
                    selectionKey.cancel();
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
                        socketChannel.close();
                        throw new IOException("Stream end!");
                    }
                }
                if (dataBytes != null && dataBytes.length > 0) {
                    dispatchEvent(ChannelEvent.EVENT_READ, dataBytes, handler);
                }
            } catch (NotYetConnectedException | IOException ex) {
                selectionKey.cancel();
                dispatchEvent(ChannelEvent.EVENT_DISCONNECT, ex, handler);
            }
        }
    }

    /**
     * Socket channel prepared write data.
     *
     * @param selectionKey SelectionKey
     */
    private void handleWritableKey(SelectionKey selectionKey) {
        final Channel socketChannel = selectionKey.channel();
        Object obj = selectionKey.attachment();
        if (null != obj && obj instanceof ChannelEventHandler) {
            if (socketChannel != null) {
                try {
                    ChannelEventHandler handler = (ChannelEventHandler) obj;
                    dispatchEvent(ChannelEvent.EVENT_WRITE, null, handler);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    selectionKey.cancel();
                }
            }
        }
    }

    /**
     * Selection event handler。
     */
    interface ChannelEventHandler {
        boolean handleChannelEvent(ChannelEvent event);
    }
}
