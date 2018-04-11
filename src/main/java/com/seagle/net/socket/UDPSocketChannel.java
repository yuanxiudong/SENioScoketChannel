package com.seagle.net.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;

/**
 * <h1>UDP客户端</h1>
 * <p>提供UDP发送功能</P>
 *
 * @author : xiudong.yuan@midea.com.cn
 * @since : 2016/4/28
 */
public class UDPSocketChannel extends ClientSocket {

    /**
     * UDP地址
     */
    private final String mUdpAddress;

    /**
     * UDP端口
     */
    private final int mUdpPort;

    /**
     * 通道选择器
     */
    private SelectionKey mSelectionKey;

    /**
     * UDP通道
     */
    private DatagramChannel mDatagramChannel;

    /**
     * 初始化
     */
    private volatile boolean mStarted;

    /**
     * 事件接收器
     */
    private UdpChannelEventHandler mChannelEventHandler;

    public UDPSocketChannel(String udpAddress, int udpPort) {
        mUdpAddress = udpAddress;
        mUdpPort = udpPort;
    }

    /**
     * 绑定UDP端口
     *
     * @param localAddress
     * @param port         端口
     * @return true 绑定成功
     */
    public synchronized boolean bindUdpPort(String localAddress, int port) {
        if (mDatagramChannel == null) {
            try {
                mDatagramChannel = DatagramChannel.open();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        try {
            mDatagramChannel.socket().bind(new InetSocketAddress(localAddress, port));
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 发送UDP数据
     *
     * @param remoteAddress IP地址
     * @param remotePort    端口
     * @param data          数据
     * @return true 发送成功
     */
    public synchronized boolean sendUdpData(String remoteAddress, int remotePort, byte[] data) {
        return false;
    }

    public synchronized boolean startUp() {
        if (mUdpPort <= 0) {
            throw new IllegalArgumentException("UDP port illegal!");
        }
        if (!mStarted) {
            try {
                mDatagramChannel = DatagramChannel.open();
                mDatagramChannel.configureBlocking(false);
                mChannelEventHandler = new UdpChannelEventHandler();
                mSelectionKey = SocketChannelManager.getInstance().registerChannel(mDatagramChannel, SelectionKey.OP_READ, mChannelEventHandler);
                if (mSelectionKey != null) {
                    mSelectionKey.interestOps(SelectionKey.OP_READ);
                    mDatagramChannel.connect(new InetSocketAddress("127.0.0.1", mUdpPort));
                    mStarted = false;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return mStarted;
        }
        return true;
    }

    @Override
    public boolean isConnected() {
        return (mStarted && mDatagramChannel != null && mDatagramChannel.isConnected());
    }

    /**
     * 写数据
     *
     * @param data 带写入的数据
     * @return true - 写入成功
     */
    @Override
    public synchronized boolean writeData(byte[] data) {
        if (mStarted && mDatagramChannel != null && mDatagramChannel.isConnected()) {
            try {
                mDatagramChannel.write(ByteBuffer.wrap(data));
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public String getIP() {
        return null;
    }

    @Override
    public int getPort() {
        return 0;
    }

    @Override
    public synchronized void disConnect() {
        if (mStarted) {
            mStarted = false;
            try {

                if (mSelectionKey != null) {
                    mSelectionKey.cancel();
                }
                if (mDatagramChannel != null) {
                    mDatagramChannel.close();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                mSelectionKey = null;
                mDatagramChannel = null;
                mChannelEventHandler = null;
            }
        }
    }

    /**
     * 读取到数据
     *
     * @param event 事件
     */
    private boolean handleReadData(ChannelEvent event) {
        if (mStarted) {
            if (event.getEventObj() != null) {
                final byte[] data = (byte[]) event.getEventObj();
                sMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        for (ClientSocketDataListener listener : mDataListenerSet) {
                            listener.onReadData(data, UDPSocketChannel.this);
                        }
                    }
                });
            }
            return true;
        }
        return false;
    }

    private class UdpChannelEventHandler implements SocketChannelManager.ChannelEventHandler {

        @Override
        public boolean handleChannelEvent(ChannelEvent event) {
            switch (event.getEventCode()) {
                case ChannelEvent.EVENT_READ:
                    return handleReadData(event);
                default:
                    return false;
            }
        }
    }
}
