package com.seagle.net.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * <h1>建立服务器</h1>
 * <p>详细功能描述</P>
 *
 * @author : xiudong.yuan@midea.com.cn
 * @date : 2016/4/28
 */
@SuppressWarnings("unused")
public class TCPSocketChannelServer {

    public static final String TAG = "TCPSocketChannelServer";

    /**
     * 本地服务器端口
     */
    private final int mPort;

    /**
     * 服务器通道
     */
    private ServerSocketChannel mServerSocketChannel;

    /**
     * 通道选择器
     */
    private SelectionKey mSelectionKey;

    /**
     * 服务器已经启动
     */
    private volatile boolean mStarted;

    /**
     * 事件处理器
     */
    private ServerChannelEventHandler mServerChannelEventHandler;

    /**
     * 服务器事件监听器
     */
    private final Set<ServerChannelEventListener> mServerChannelEventListenerSet;

    /**
     * 主线程，为了将回调接口放在主线程中运行。
     */
    public static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    public TCPSocketChannelServer(int port) {
        mPort = port;
        mServerChannelEventListenerSet = new CopyOnWriteArraySet<>();
    }

    public synchronized boolean startServer(int timeOut) {
        if (!mStarted) {
            try {
                mServerSocketChannel = ServerSocketChannel.open();
                mServerSocketChannel.configureBlocking(false);
                mServerSocketChannel.socket().setSoTimeout(timeOut);
                mServerSocketChannel.socket().setReuseAddress(true);
                mServerSocketChannel.socket().bind(new InetSocketAddress(mPort));
                mServerChannelEventHandler = new ServerChannelEventHandler();
                mSelectionKey = SESocketManager.getInstance().registerChannel(mServerSocketChannel, SelectionKey.OP_ACCEPT, mServerChannelEventHandler);
                if (mSelectionKey != null) {
                    mStarted = true;
                } else {
                    throw new IOException("Selection key is null!");
                }
            } catch (IOException e) {
                e.printStackTrace();
                resetChannel();
            }
        }
        return mStarted;
    }

    public synchronized boolean closeServer() {
        if (mStarted) {
            resetChannel();
        }
        return false;
    }

    /**
     * 获取端口
     *
     * @return 端口
     */
    public int getPort() {
        return mPort;
    }

    /**
     * 释放全部连接资源
     */
    private void resetChannel() {
        mStarted = false;
        if (mServerSocketChannel != null) {
            try {
                mServerSocketChannel.close();
            } catch (IOException e) {
                Log.i(TAG, e.getMessage());
            } finally {
                mServerSocketChannel = null;
                mServerChannelEventHandler = null;
                mSelectionKey = null;
            }
        }
    }

    /**
     * 处理收到连接事件
     *
     * @param event 事件
     * @return true
     */
    private synchronized boolean handleAccept(ChannelEvent event) {
        if (mStarted) {
            Object obj = event.getEventObj();
            if (obj != null && obj instanceof SocketChannel) {
                SocketChannel socketChannel = (SocketChannel) obj;
                final TCPServerSocketChannelClient socketChannelClient = new TCPServerSocketChannelClient(socketChannel);
                if (socketChannelClient.startUp()) {
                    sMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            for (ServerChannelEventListener listener : mServerChannelEventListenerSet) {
                                listener.onAccept(socketChannelClient);
                            }
                        }
                    });
                }
            }
        }
        return false;
    }


    /**
     * Socket通道事件处理
     */
    private class ServerChannelEventHandler implements SESocketManager.ChannelEventHandler {
        @Override
        public boolean handleChannelEvent(ChannelEvent event) {
            switch (event.getEventCode()) {
                case ChannelEvent.EVENT_ACCEPT:
                    Log.i(TAG, "Connected event!");
                    return handleAccept(event);
                default:
                    return false;
            }
        }
    }

    /**
     * 服务器事件接口
     */
    public interface ServerChannelEventListener {

        /**
         * 收到连接
         *
         * @param socketChannelClient 客户端
         */
        void onAccept(TCPServerSocketChannelClient socketChannelClient);
    }
}
