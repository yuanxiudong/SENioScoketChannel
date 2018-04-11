package com.seagle.net.socket;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * <h1>SocketChannel管理</h1>
 * <p>封装了一下NIO操作，提供线程能力</P>
 *
 * @author : seagle
 * @since : 2016/4/28
 */
final class SocketChannelManager {

    public static final String TAG = "SocketChannelManager";

    /**
     * 选择器
     */
    private volatile Selector mSelector;

    /**
     * 轮询和事件分发线程池。
     * 守护线程，在应用程序退出自动退出线程。
     */
    private final ExecutorService mExecutor;

    /**
     * 是否正在轮询IO事件。
     */
    private volatile boolean mSelecting;

    /**
     * 单实例
     */
    private volatile static SocketChannelManager sInstance;

    /**
     * 获取单例
     *
     * @return SocketChannelManager
     */
    public static synchronized SocketChannelManager getInstance() {
        if (sInstance == null) {
            sInstance = new SocketChannelManager();
        }
        return sInstance;
    }

    private SocketChannelManager() {
        mExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread thread = new Thread(r, "Socket_Thread");
                thread.setDaemon(true);
                return thread;
            }
        });
        mSelecting = false;
    }

    /**
     * 注册待选择的Channel
     *
     * @param channel     通道
     * @param interestSet 待监听的事件
     * @param handler     事件处理器
     * @return SelectionKey
     * @throws IOException Socket异常
     */
    synchronized SelectionKey registerChannel(AbstractSelectableChannel channel, int interestSet,
                                              ChannelEventHandler handler) throws IOException {
        Log.i(TAG, "registerChannel");
        if (channel != null) {
            if (mSelector == null) {
                mSelector = Selector.open();
            }
            channel.configureBlocking(false);
            SelectionKey selectionKey = channel.register(mSelector, interestSet, handler);
            if (!mSelecting) {
                mExecutor.submit(new SelectKeyTask());
            }
            return selectionKey;
        }
        throw new IllegalArgumentException("Illegal channel!");
    }

    /**
     * 处理轮询的任务
     */
    private class SelectKeyTask implements Runnable {
        @Override
        public void run() {
            if (!mSelecting) {
                mSelecting = true;
                Log.i(TAG, "Start selecting...");
                try {
                    while (mSelecting) {
                        //这里要特别注意，不能使用select()而要使用selectNow()，不然会死锁
                        if (mSelector.selectNow() == 0) {
                            try {
                                TimeUnit.MILLISECONDS.sleep(500);
                            } catch (InterruptedException ex) {
                                ex.printStackTrace();
                            }
                            //如果没有任何Channel需要进行轮询的,就直接退出循环
                            Set<SelectionKey> keySet = mSelector.keys();
                            if (keySet == null || keySet.size() == 0) {
                                break;
                            } else {
                                continue;
                            }
                        }
                        Iterator<SelectionKey> iterator = mSelector.selectedKeys().iterator();
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            iterator.remove();
                            if (key.isConnectable()) {
                                handleConnectableKey(key);
                            } else if (key.isReadable()) {
                                handleReadableKey(key);
                            } else if (key.isAcceptable()) {
                                handleAcceptableKey(key);
                            } else {
                                Log.i("SocketChannelManager", "Not handled key: " + key.readyOps());
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.i(TAG, e.toString());
                    e.printStackTrace();
                    try {
                        mSelector.close();
                        mSelector = null;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } finally {
                    mSelecting = false;
                    Log.i(TAG, "Selecting stopped!");
                }
            }
        }

        /**
         * 客户端处理数据连接成功的消息
         *
         * @param selectionKey key
         */
        private void handleConnectableKey(SelectionKey selectionKey) {
            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
            Object obj = selectionKey.attachment();
            String msg;
            if (null != obj && obj instanceof ChannelEventHandler) {
                ChannelEventHandler handler = (ChannelEventHandler) obj;
                if (socketChannel.isConnectionPending()) {
                    try {
                        //等待连接完成
                        while (!socketChannel.finishConnect()) {
                            try {
                                TimeUnit.MILLISECONDS.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (IOException e) {
                        //等待连接失败，说明Socket被关闭了
                        Log.w(TAG, e.getMessage());
                        msg = e.getMessage();
                        dispatchEvent(ChannelEvent.EVENT_CONNECT_FAILED, msg, socketChannel, handler);
                        return;
                    }
                }

                //这里有个坑,如果判断isConnected=false不要认为连接失败
                if (socketChannel.isConnected()) {
                    dispatchEvent(ChannelEvent.EVENT_CONNECTED, "Connect success", socketChannel, handler);
                }
            } else {
                Log.e(TAG, "SelectionKey attachment invalid!");
            }
        }

        /**
         * 客户端数据读取
         *
         * @param selectionKey key
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
                            byteBuffer.array();
                            byteBuffer.get(newData);
                            dataBytes = mergeBytes(dataBytes, newData);
                        } else if (length == 0) {
                            break;
                        } else {
                            selectionKey.cancel();
                            throw new IOException("Stream end!");
                        }
                    }
                    dispatchEvent(ChannelEvent.EVENT_READ, dataBytes, socketChannel, handler);
                } catch (NotYetConnectedException | ClosedChannelException ex) {
                    ex.printStackTrace();
                    dispatchEvent(ChannelEvent.EVENT_DISCONNECT, null, socketChannel, handler);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            } else {
                Log.e(TAG, "SelectionKey attachment invalid!");
            }
        }

        /**
         * 服务器端监听
         *
         * @param selectionKey key
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
            } else {
                Log.e(TAG, "SelectionKey attachment invalid!");
            }
        }

        /**
         * 将两个byte数组合并成一个更大的byte数组。
         *
         * @param bytes1 数组1
         * @param bytes2 数组2
         * @return 合并后的数组
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
         * 派发事件。
         * 将事件派发到独立线程中进行处理，避免外界的耗时操作影响事件轮询
         *
         * @param eventCode 事件码
         * @param object    事件数据对象
         * @param channel   事件源通道
         * @param handler   事件处理器
         */
        private void dispatchEvent(int eventCode, Object object, Channel channel, ChannelEventHandler handler) {
            EventDispatchTask task = new EventDispatchTask(channel, eventCode, handler, object);
            mExecutor.submit(task);
        }
    }

    /**
     * 通道事件派发任务
     * 将事件独立派发到线程,避免影响Select操作.
     */
    private class EventDispatchTask implements Runnable {
        private final int eventCode;
        private final Object object;
        private final Channel channel;
        private final ChannelEventHandler handler;

        private EventDispatchTask(Channel channel, int eventCode, ChannelEventHandler handler, Object object) {
            this.channel = channel;
            this.eventCode = eventCode;
            this.handler = handler;
            this.object = object;
        }

        @Override
        public void run() {
            ChannelEvent channelEvent = new ChannelEvent(eventCode, object, channel);
            handler.handleChannelEvent(channelEvent);
        }
    }

    /**
     * 选择器事件处理。
     */
    interface ChannelEventHandler {

        /**
         * 处理通道事件
         *
         * @param event 事件
         */
        boolean handleChannelEvent(ChannelEvent event);
    }
}
