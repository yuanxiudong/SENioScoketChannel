package com.seagle.net.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * <h1>SocketChannel</h1>
 *
 * @author : yuanxiudong66@sina.com
 * @since : 2016/4/28
 */
@SuppressWarnings("unused")
public class SESocketChannel {

    private enum ConnectState {
        STATE_IDLE, STATE_CONNECTING, STATE_CONNECTED
    }

    private volatile SocketChannel mSocketChannel;
    private volatile SelectionKey mSelectionKey;
    private volatile ChannelEventHandler mChannelEventHandler;
    private volatile ConnectState mState;
    private final Set<SocketChannelStateListener> mListenerSet;
    private volatile ConnectionCallback mCallback;

    public SESocketChannel() {
        mListenerSet = new CopyOnWriteArraySet<>();
        mState = ConnectState.STATE_IDLE;
    }

    public SESocketChannel(SocketChannel socketChannel) throws IOException {
        if (socketChannel == null || !socketChannel.isConnected()) {
            throw new NotYetConnectedException();
        }
        mListenerSet = new CopyOnWriteArraySet<>();
        mChannelEventHandler = new ChannelEventHandler();
        SelectionKey selectionKey = SESocketChannelManager.getInstance().registerChannel(socketChannel, SelectionKey.OP_READ, mChannelEventHandler);
        mSocketChannel = socketChannel;
        mState = ConnectState.STATE_CONNECTED;
        mSelectionKey = selectionKey;
    }

    public void registerSocketChannelListener(SocketChannelStateListener listener) {
        mListenerSet.add(listener);
    }

    public void unRegisterSocketChannelListener(SocketChannelStateListener listener) {
        mListenerSet.remove(listener);
    }

    /**
     * Connect to remote address.
     * Support sync connection type if callback is null and async connection type if callback not null.
     * If the socket channel has connected,return.
     *
     * @param ipAddress remote address.
     * @param port      Remote port
     * @param callback  ConnectionCallback
     * @throws Exception Connect exception
     */
    public void connect(String ipAddress, int port, ConnectionCallback callback) throws Exception {
        if (ipAddress == null || ipAddress.length() == 0 || port <= 0) {
            throw new IllegalArgumentException("Remote address or port illegal.");
        }
        if (ConnectState.STATE_IDLE == mState) {
            mCallback = null;
            mChannelEventHandler = new ChannelEventHandler();
            SocketAddress address = new InetSocketAddress(ipAddress, port);
            SocketChannel socketChannel = null;
            SelectionKey selectionKey = null;
            try {
                if (callback == null) {
                    socketChannel = SocketChannel.open(address);
                    selectionKey = SESocketChannelManager.getInstance().registerChannel(socketChannel, SelectionKey.OP_READ, mChannelEventHandler);
                    mState = ConnectState.STATE_CONNECTED;
                } else {
                    socketChannel = SocketChannel.open();
                    socketChannel.configureBlocking(false);
                    selectionKey = SESocketChannelManager.getInstance().registerChannel(mSocketChannel, SelectionKey.OP_CONNECT, mChannelEventHandler);
                    socketChannel.connect(address);
                    mCallback = callback;
                    mState = ConnectState.STATE_CONNECTED;
                }
                mSocketChannel = socketChannel;
                mSelectionKey = selectionKey;
            } catch (Exception ex) {
                ex.printStackTrace();
                if (selectionKey != null) {
                    selectionKey.cancel();
                }
                if (socketChannel != null) {
                    try {
                        socketChannel.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                mCallback = null;
                throw ex;
            }
        }
    }

    /**
     * Disconnect socket channel.
     */
    public void disConnect() {
        mState = ConnectState.STATE_IDLE;
        SelectionKey selectionKey = mSelectionKey;
        if (selectionKey != null) {
            selectionKey.cancel();
        }
        mSelectionKey = null;

        SocketChannel socketChannel = mSocketChannel;
        mSocketChannel = null;
        if (socketChannel != null) {
            try {
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mChannelEventHandler = null;
        mCallback = null;
    }

    /**
     * Return socket channel.
     * @return SocketChannel
     */
    public SocketChannel getSocketChannel() {
        if(ConnectState.STATE_CONNECTED == mState) {
            return mSocketChannel;
        }else{
            return null;
        }
    }

    /**
     * Return connect state.
     *
     * @return Connection State
     */
    public boolean isConnected() {
        return ConnectState.STATE_CONNECTED == mState;
    }

    /**
     * Write data.
     *
     * @param data Data
     */
    public void writeData(byte[] data) {
        if (ConnectState.STATE_CONNECTED == mState) {
            try {
                mSocketChannel.write(ByteBuffer.wrap(data));
            } catch (NotYetConnectedException | IOException ex) {
                ex.printStackTrace();
                disConnect();
                for (SocketChannelStateListener listener : mListenerSet) {
                    listener.onDisConnected(SESocketChannel.this);
                }
            }
        }
    }

    /**
     * Socket event handler.
     */
    private class ChannelEventHandler implements SESocketChannelManager.ChannelEventHandler {
        @Override
        public boolean handleChannelEvent(ChannelEvent event) {
            switch (event.getEventCode()) {
                case ChannelEvent.EVENT_CONNECTED:
                    return handleConnected(event);
                case ChannelEvent.EVENT_DISCONNECT:
                    return handleDisConnected(event);
                case ChannelEvent.EVENT_READ:
                    return handleReadData(event);
                case ChannelEvent.EVENT_CONNECT_FAILED:
                    return handleConnectFailed(event);
                default:
                    return false;
            }
        }
    }

    /**
     * Socket channel connect failed.
     *
     * @param event ChannelEvent
     * @return result
     */
    private boolean handleConnectFailed(ChannelEvent event) {
        if (ConnectState.STATE_CONNECTING == mState) {
            disConnect();
            String eventMsg = null;
            if (event.getEventObj() != null) {
                eventMsg = (String) event.getEventObj();
            }
            final String errorMsg = eventMsg;
            if (mCallback != null) {
                mCallback.onConnectFailed(SESocketChannel.this);
            }
            mCallback = null;
            return true;
        }
        return false;
    }

    /**
     * Socket channel connected.
     *
     * @param event ChannelEvent
     * @return result
     */
    private boolean handleConnected(ChannelEvent event) {
        if (ConnectState.STATE_CONNECTING == mState) {
            mState = ConnectState.STATE_CONNECTED;
            mSelectionKey.interestOps(SelectionKey.OP_READ);
            if (mCallback != null) {
                mCallback.onConnected(SESocketChannel.this);
            }
            mCallback = null;
            return true;
        }
        return false;
    }

    /**
     * Socket channel disconnected.
     *
     * @param event ChannelEvent
     * @return result
     */
    private boolean handleDisConnected(ChannelEvent event) {
        if (ConnectState.STATE_CONNECTED == mState) {
            disConnect();
            for (SocketChannelStateListener listener : mListenerSet) {
                listener.onDisConnected(SESocketChannel.this);
            }
            return true;
        }
        return false;
    }

    /**
     * Socket channel receive data.
     *
     * @param event ChannelEvent
     * @return result
     */
    private boolean handleReadData(ChannelEvent event) {
        if (ConnectState.STATE_CONNECTED == mState) {
            if (event.getEventObj() != null) {
                final byte[] data = (byte[]) event.getEventObj();
                for (SocketChannelStateListener listener : mListenerSet) {
                    listener.onReceivedData(SESocketChannel.this, data);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Socket channel state listener.
     */
    public interface SocketChannelStateListener {
        /**
         * Socket disconnected.
         *
         * @param channel SocketChannel
         */
        void onDisConnected(SESocketChannel channel);

        /**
         * Receive data from socket channel.
         *
         * @param channel SocketChannel
         * @param data    bytes data
         */
        void onReceivedData(SESocketChannel channel, byte[] data);
    }

    /**
     * Connection callback.
     */
    public interface ConnectionCallback {
        /**
         * Connect failed.
         *
         * @param channel SocketChannel
         */
        void onConnectFailed(SESocketChannel channel);

        /**
         * Connect success
         *
         * @param channel SocketChannel
         */
        void onConnected(SESocketChannel channel);
    }
}
