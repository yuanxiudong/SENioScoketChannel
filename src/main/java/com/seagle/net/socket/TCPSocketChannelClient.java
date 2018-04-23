package com.seagle.net.socket;

import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <h1>SocketChannel的客户端连接通道</h1>
 * <p>缺点：全部使用synchronized</P>
 *
 * @author : xiudong.yuan@midea.com.cn
 * @date : 2016/4/28
 */
@SuppressWarnings("unused")
public class TCPSocketChannelClient extends ClientSocket {

    /**
     * 服务器地址
     */
    private final String mServerIPAddress;

    /**
     * 服务器端口
     */
    private final int mServerPort;

    /**
     * 监听连接事件和
     */
    private final Set<ClientSocketChannelListener> mListenerSet;

    /**
     * Socket
     */
    private SocketChannel mSocketChannel;

    /**
     * 通道选择器
     */
    private SelectionKey mSelectionKey;

    /**
     * 是否连接
     */
    private volatile boolean mConnected;

    /**
     * 正在执行连接
     */
    private volatile boolean mConnectWaiting;

    /**
     * 事件处理器
     */
    private ChannelEventHandler mChannelEventHandler;

    public TCPSocketChannelClient(String serverIPAddress, int serverPort) {
        super();
        if (TextUtils.isEmpty(serverIPAddress) || serverPort <= 0) {
            throw new IllegalArgumentException("Ip address or port illegal!");
        }
        mServerIPAddress = serverIPAddress;
        mServerPort = serverPort;
        mListenerSet = new CopyOnWriteArraySet<>();
        mConnected = false;
        mConnectWaiting = false;
    }

    /**
     * 注册客户端Socket事件监听器
     *
     * @param listener 监听器
     * @return true-成功
     */
    public boolean registerSocketChannelListener(ClientSocketChannelListener listener) {
        return mListenerSet.add(listener);
    }

    /**
     * 移除客户端Socket事件监听器
     *
     * @param listener 事件监听器
     * @return true-成功
     */
    public boolean unRegisterSocketChannelListener(ClientSocketChannelListener listener) {
        return mListenerSet.remove(listener);
    }

    /**
     * 执行连接。
     * 超时时间<=500MS,超时设置无效，不进行超时检测。
     *
     * @param timeout 超时时间。
     * @return true-执行成功
     */
    public synchronized boolean connect(int timeout) {
        Log.d(TAG, "connect: " + timeout);
        if (!mConnected && !mConnectWaiting) {
            try {
                mSocketChannel = SocketChannel.open();
                mSocketChannel.configureBlocking(false);
                mSocketChannel.socket().setSoTimeout(timeout);
                mSocketChannel.socket().setKeepAlive(true);
                mChannelEventHandler = new ChannelEventHandler();
                mSelectionKey = SESocketManager.getInstance().registerChannel(mSocketChannel, SelectionKey.OP_CONNECT, mChannelEventHandler);
                if (mSelectionKey != null) {
                    SocketAddress address = new InetSocketAddress(mServerIPAddress, mServerPort);
                    mSocketChannel.connect(address);
                    mConnectWaiting = true;
                    if (timeout > 500) {
                        sMainHandler.postDelayed(mTimeoutCheckTask, timeout);
                    }
                } else {
                    throw new IOException("Selection key is null!");
                }
            } catch (IOException e) {
                e.printStackTrace();
                resetConnect();
            }
            return mConnectWaiting;
        }
        return true;
    }

    public synchronized boolean connectSync(final int timeout) {
        Log.d(TAG, "connect: " + timeout);
        if (!mConnected && !mConnectWaiting) {

            Future<Boolean> connectFuture = sExecutor.submit(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    mSocketChannel = SocketChannel.open();
                    mSocketChannel.configureBlocking(true);
                    mSocketChannel.socket().setSoTimeout(timeout);
                    mSocketChannel.socket().setKeepAlive(true);
                    mChannelEventHandler = new ChannelEventHandler();
                    final SocketAddress address = new InetSocketAddress(mServerIPAddress, mServerPort);
                    mSocketChannel.connect(address);
                    return true;
                }
            });

            try {
                mConnectWaiting = true;
                connectFuture.get(timeout, TimeUnit.MILLISECONDS);
                mConnectWaiting = false;
                mConnected = true;
                mSelectionKey = SESocketManager.getInstance().registerChannel(mSocketChannel, SelectionKey.OP_READ, mChannelEventHandler);
                mSelectionKey.interestOps(SelectionKey.OP_READ);
                return true;

            } catch (TimeoutException e) {
                e.printStackTrace();
                resetConnect();
                return false;
            } catch (final InterruptedException | ExecutionException e) {
                e.printStackTrace();
                resetConnect();
                return false;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    @Override
    public synchronized void disConnect() {
        Log.i(TAG, "disConnect");
        if (mConnectWaiting || mConnected) {
            resetConnect();
        }
    }

    @Override
    public boolean isConnected() {
        return mConnected;
    }

    @Override
    public synchronized boolean writeData(byte[] data) {
        if (mConnected && mSocketChannel != null) {
            try {
                mSocketChannel.write(ByteBuffer.wrap(data));
                return true;
            } catch (ClosedChannelException ex) {
                ex.printStackTrace();
                resetConnect();
                sMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        for (ClientSocketChannelListener listener : mListenerSet) {
                            listener.onDisConnected(TCPSocketChannelClient.this);
                        }
                    }
                });
            } catch (NotYetConnectedException | IOException ex) {
                ex.printStackTrace();
            }
        } else {
            Log.w(TAG, "Socket not connect!");
        }
        return false;
    }

    @Override
    public String getIP() {
        return mServerIPAddress;
    }

    @Override
    public int getPort() {
        return mServerPort;
    }

    /**
     * 释放全部连接资源
     */
    private void resetConnect() {
        Log.d(TAG, "resetConnect");

        mConnectWaiting = false;
        mConnected = false;
        if (mSocketChannel != null) {
            try {
                mSocketChannel.socket().shutdownInput();
            } catch (IOException e) {
                Log.i(TAG, e.getMessage());
            }

            try {
                mSocketChannel.socket().shutdownOutput();
            } catch (IOException e) {
                Log.i(TAG, e.getMessage());
            }

            try {
                mSocketChannel.close();
            } catch (IOException e) {
                Log.i(TAG, e.getMessage());
            } finally {
                mSocketChannel = null;
                mChannelEventHandler = null;
                mSelectionKey = null;
            }
        }
    }


    /**
     * Socket通道事件处理
     */
    private class ChannelEventHandler implements SESocketManager.ChannelEventHandler {
        @Override
        public boolean handleChannelEvent(ChannelEvent event) {
            switch (event.getEventCode()) {
                case ChannelEvent.EVENT_CONNECTED:
                    Log.i(TAG, "Connected event!");
                    return handleConnected(event);
                case ChannelEvent.EVENT_DISCONNECT:
                    Log.i(TAG, "Disconnected event!");
                    return handleDisConnected(event);
                case ChannelEvent.EVENT_READ:
                    Log.i(TAG, "Read event!");
                    return handleReadData(event);
                case ChannelEvent.EVENT_CONNECT_FAILED:
                    Log.i(TAG, "Connect failed event!");
                    return handleConnectFailed(event);
                default:
                    return false;
            }
        }
    }

    /**
     * 连接超时检测
     */
    private Runnable mTimeoutCheckTask = new Runnable() {
        @Override
        public void run() {
            synchronized (TCPSocketChannelClient.this) {
                if (mConnectWaiting) {
                    resetConnect();
                    sMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            for (ClientSocketChannelListener listener : mListenerSet) {
                                listener.onConnectFailed(TCPSocketChannelClient.this, ERROR_CODE_CONNECT_TIMEOUT, "Connect TimeOut");
                            }
                        }
                    });
                }
            }
        }
    };

    /**
     * 连接失败
     *
     * @param event 事件
     */
    private synchronized boolean handleConnectFailed(ChannelEvent event) {
        if (mConnectWaiting) {
            resetConnect();
            String eventMsg = null;
            if (event.getEventObj() != null) {
                eventMsg = (String) event.getEventObj();
            }
            final String errorMsg = eventMsg;
            sMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (ClientSocketChannelListener listener : mListenerSet) {
                        listener.onConnectFailed(TCPSocketChannelClient.this, ERROR_CODE_SOCKET_EXCEPTION, errorMsg);
                    }
                }
            });
            return true;
        }
        return false;
    }

    /**
     * 连接成功
     *
     * @param event 事件
     */
    private synchronized boolean handleConnected(ChannelEvent event) {
        if (mConnectWaiting) {
            mConnectWaiting = false;
            mConnected = true;
            mSelectionKey.interestOps(SelectionKey.OP_READ);
            sMainHandler.removeCallbacks(mTimeoutCheckTask);
            sMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (ClientSocketChannelListener listener : mListenerSet) {
                        listener.onConnected(TCPSocketChannelClient.this);
                    }
                }
            });
            return true;
        }
        return false;
    }

    /**
     * 连接断开
     *
     * @param event 事件
     */
    private synchronized boolean handleDisConnected(ChannelEvent event) {
        if (mConnected) {
            resetConnect();
            sMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (ClientSocketChannelListener listener : mListenerSet) {
                        listener.onDisConnected(TCPSocketChannelClient.this);
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
    private boolean handleReadData(ChannelEvent event) {
        boolean connected;
        synchronized (TCPSocketChannelClient.this) {
            connected = mConnected;
        }
        if (connected) {
            if (event.getEventObj() != null) {
                final byte[] data = (byte[]) event.getEventObj();
                sMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        for (ClientSocketDataListener listener : mDataListenerSet) {
                            listener.onReadData(data, TCPSocketChannelClient.this);
                        }
                    }
                });
            }
        }
        return false;
    }

    /**
     * 客户端Socket通道的事件监听
     */
    public interface ClientSocketChannelListener {
        /**
         * 连接失败
         *
         * @param channel 通道
         */
        void onConnectFailed(TCPSocketChannelClient channel, int errorCode, String errorMsg);

        /**
         * 连接成功
         *
         * @param channel 通道
         */
        void onConnected(TCPSocketChannelClient channel);

        /**
         * 连接断开
         *
         * @param channel 通道
         */
        void onDisConnected(TCPSocketChannelClient channel);
    }
}
