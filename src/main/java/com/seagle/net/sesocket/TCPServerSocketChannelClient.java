package com.seagle.net.sesocket;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * <h1>服务器端收到的客户端连接Socket</h1>
 *
 * @author : xiudong.yuan@midea.com.cn
 * @date : 2016/4/28
 */
@SuppressWarnings("unused")
public class TCPServerSocketChannelClient extends ClientSocket {

    /**
     * 监听连接事件和
     */
    private final Set<ServerClientChannelListener> mListenerSet;

    /**
     * Socket通道
     */
    private final SocketChannel mSocketChannel;

    /**
     * Socket IP
     */
    private final String mSocketIPAddress;

    /**
     * Socket 端口
     */
    private final int mSocketPort;

    /**
     * 通道选择器
     */
    private SelectionKey mSelectionKey;

    /**
     * 初始化
     */
    private volatile boolean mStarted;

    /**
     * 选择器事件处理
     */
    private volatile ChannelEventHandler mChannelEventHandler;


    public TCPServerSocketChannelClient(SocketChannel socketChannel) {
        if (socketChannel == null) {
            throw new IllegalArgumentException("Socket channel illegal!");
        }
        mSocketChannel = socketChannel;
        mSocketIPAddress = mSocketChannel.socket().getInetAddress().getHostName();
        mSocketPort = mSocketChannel.socket().getPort();
        mListenerSet = new CopyOnWriteArraySet<>();
    }

    public boolean registerDataListener(ServerClientChannelListener listener) {
        return mListenerSet.add(listener);
    }

    public boolean unRegisterDataListener(ServerClientChannelListener listener) {
        return mListenerSet.remove(listener);
    }

    /**
     * 启动Socket通道读取数据
     *
     * @return true-启动成功
     */
    public synchronized boolean startUp() {
        if (!mStarted) {
            try {
                mSocketChannel.configureBlocking(false);
                mChannelEventHandler = new ChannelEventHandler();
                mSelectionKey = SocketChannelManager.getInstance().registerChannel(mSocketChannel, SelectionKey.OP_READ, mChannelEventHandler);
                if (mSelectionKey != null) {
                    mStarted = true;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                resetConnect();
            }
            return mStarted;
        }
        return true;
    }

    public synchronized void disConnect() {
        if (mStarted) {
            resetConnect();
        }
    }

    @Override
    public boolean isConnected() {
        return (mStarted && mSocketChannel != null && mSocketChannel.isConnected());
    }

    @Override
    public synchronized boolean writeData(byte[] data) {
        if (mStarted && mSocketChannel != null && mSocketChannel.isConnected()) {
            try {
                mSocketChannel.write(ByteBuffer.wrap(data));
                return true;
            } catch (ClosedChannelException ex) {
                ex.printStackTrace();
                resetConnect();
                sMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        for (ServerClientChannelListener listener : mListenerSet) {
                            listener.onDisConnected(TCPServerSocketChannelClient.this);
                        }
                    }
                });
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public String getIP() {
        return mSocketIPAddress;
    }

    @Override
    public int getPort() {
        return mSocketPort;
    }

    /**
     * 释放连接资源
     */
    private void resetConnect() {
        mStarted = false;
        if (mSelectionKey != null) {
            mSelectionKey.cancel();
            mSelectionKey = null;
        }
        mChannelEventHandler = null;
        if (mSocketChannel != null) {
            try {
                mSocketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 连接断开
     *
     * @param event 事件
     */
    private synchronized boolean handleDisConnected(ChannelEvent event) {
        if (mStarted) {
            resetConnect();
            sMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (ServerClientChannelListener listener : mListenerSet) {
                        listener.onDisConnected(TCPServerSocketChannelClient.this);
                    }
                }
            });
            return true;
        }
        return false;
    }

    /**
     * 读取到数据
     *
     * @param event 事件
     */
    private synchronized boolean handleReadData(ChannelEvent event) {
        if (mStarted) {
            if (event.getEventObj() != null) {
                final byte[] data = (byte[]) event.getEventObj();
                sMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        for (ClientSocketDataListener listener : mDataListenerSet) {
                            listener.onReadData(data, TCPServerSocketChannelClient.this);
                        }
                    }
                });
            }
            return true;
        }
        return false;
    }

    private class ChannelEventHandler implements SocketChannelManager.ChannelEventHandler {
        @Override
        public boolean handleChannelEvent(ChannelEvent event) {
            switch (event.getEventCode()) {
                case ChannelEvent.EVENT_DISCONNECT:
                    Log.i(TAG, "Disconnected event!");
                    return handleDisConnected(event);
                case ChannelEvent.EVENT_READ:
                    Log.i(TAG, "Read event!");
                    return handleReadData(event);
                default:
                    return false;
            }
        }
    }

    /**
     * TCPChannel监听器
     */
    public interface ServerClientChannelListener {

        /**
         * 连接断开
         *
         * @param channel 通道
         */
        void onDisConnected(TCPServerSocketChannelClient channel);
    }
}
