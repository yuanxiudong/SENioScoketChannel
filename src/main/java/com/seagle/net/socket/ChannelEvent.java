package com.seagle.net.socket;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * Socket channel event.
 *
 * @author : yuanxiudong66@sina.com
 * @since : 2016/4/28
 */
class ChannelEvent {

    /**
     * As socket event is very frequency,so cache it for reuse.
     */
    private static final ArrayBlockingQueue<ChannelEvent> CACHE_QUEUE = new ArrayBlockingQueue<>(50);

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

    /**
     * event code.
     */
    private int eventCode;

    /**
     * event attachment
     */
    private Object eventObj;

    private ChannelEvent(int eventCode, Object eventObj) {
        this.eventCode = eventCode;
        this.eventObj = eventObj;
    }

    /**
     * Return event code.
     *
     * @return event code
     */
    int getEventCode() {
        return eventCode;
    }

    /**
     * Return attachment.
     *
     * @return attachment
     */
    Object getEventObj() {
        return eventObj;
    }

    /**
     * Cache and reuse this object.
     */
    void reuse() {
        eventCode = -1;
        eventObj = null;
        CACHE_QUEUE.offer(this);
    }

    /**
     * Create a object.
     *
     * @param eventCode event code
     * @param eventObj  attachment
     * @return ChannelEvent
     */
    static ChannelEvent create(int eventCode, Object eventObj) {
        ChannelEvent event = CACHE_QUEUE.poll();
        if (event == null) {
            event = new ChannelEvent(eventCode, eventObj);
        } else {
            event.eventCode = eventCode;
            event.eventObj = eventObj;
        }
        return event;
    }
}
