package com.seagle.net.sesocket;

import java.nio.channels.Channel;

/**
 * <h1>Socket通道事件</h1>
 *
 * @author : xiudong.yuan@midea.com.cn
 * @since : 2016/4/28
 */
class ChannelEvent {

    /**
     * 连接事件
     */
    static final int EVENT_CONNECTED = 0x01;

    /**
     * 断开连接事件
     */
    static final int EVENT_DISCONNECT = 0x02;

    /**
     * 连接失败事件
     */
    static final int EVENT_CONNECT_FAILED = 0x03;

    /**
     * 收到连接事件
     */
    static final int EVENT_ACCEPT = 0x04;

    /**
     * 数据读取事件
     */
    static final int EVENT_READ = 0x05;

    /**
     * 数据通道
     */
    private final Channel mSocketChannel;

    /**
     * 事件码
     */
    private final int mEventCode;

    /**
     * 事件对象
     */
    private final Object mEventObj;

    ChannelEvent(int eventCode, Object eventObj, Channel socketChannel) {
        mEventCode = eventCode;
        mEventObj = eventObj;
        mSocketChannel = socketChannel;
    }

    /**
     * 返回事件码
     *
     * @return 事件码
     */
    int getEventCode() {
        return mEventCode;
    }

    /**
     * 返回事件携带的数据
     *
     * @return 数据
     */
    Object getEventObj() {
        return mEventObj;
    }

    /**
     * 返回事件源Socket通道。
     *
     * @return Channel
     */
    @SuppressWarnings("unused")
    public Channel getSocketChannel() {
        return mSocketChannel;
    }
}
