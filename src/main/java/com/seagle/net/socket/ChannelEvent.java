package com.seagle.net.socket;

/**
 * Socket channel event.
 *
 * @author : yuanxiudong66@sina.com
 * @since : 2016/4/28
 */
class ChannelEvent {

    /**
     * Event code:socket channel connected.
     */
    static final int EVENT_CONNECTED = 0x01;
    /**
     * Event code:socket channel disconnected.
     */
    static final int EVENT_DISCONNECT = 0x02;
    /**
     * Event code:socket channel connect failed.
     */
    static final int EVENT_CONNECT_FAILED = 0x03;
    /**
     * Event code:socket channel client connect to server.
     */
    static final int EVENT_ACCEPT = 0x04;
    /**
     * Event code:socket channel read data.
     */
    static final int EVENT_READ = 0x05;
    /**
     * Event code:socket channel read data.
     */
    static final int EVENT_WRITE = 0x06;

    private final int mEventCode;

    private final Object mEventObj;

    ChannelEvent(int eventCode, Object eventObj) {
        mEventCode = eventCode;
        mEventObj = eventObj;
    }

    int getEventCode() {
        return mEventCode;
    }

    Object getEventObj() {
        return mEventObj;
    }
}
