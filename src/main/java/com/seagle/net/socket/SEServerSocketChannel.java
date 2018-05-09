package com.seagle.net.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Server socket channel.
 *
 * @author : yuanxiudong66@sina.com
 * @since : 2016/4/28
 */
public class SEServerSocketChannel {

    private final int mPort;
    private volatile ServerSocketChannel mServerSocketChannel;
    private volatile SelectionKey mSelectionKey;
    private volatile boolean mListening;
    private volatile ServerChannelEventHandler mServerChannelEventHandler;
    private final Set<ServerChannelEventListener> mServerChannelEventListenerSet;

    public SEServerSocketChannel(int port) {
        mPort = port;
        mServerChannelEventListenerSet = new CopyOnWriteArraySet<>();
    }

    /**
     * Start server socket listening.
     *
     * @throws IOException Start exception
     */
    public void startServer() throws IOException {
        if (!mListening) {
            try {
                mServerSocketChannel = ServerSocketChannel.open();
                mServerSocketChannel.socket().bind(new InetSocketAddress(mPort));
                mServerChannelEventHandler = new ServerChannelEventHandler();
                mServerSocketChannel.configureBlocking(false);
                mSelectionKey = SESocketChannelManager.getInstance().registerChannel(mServerSocketChannel, SelectionKey.OP_ACCEPT, mServerChannelEventHandler);
                if (mSelectionKey != null) {
                    mListening = true;
                } else {
                    throw new IOException("Selection key is null!");
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                closeServer();
                throw ex;
            }
        }
    }

    /**
     * Stop server listening.
     */
    public void closeServer() {
        mListening = false;
        SelectionKey selectionKey = mSelectionKey;
        if (selectionKey != null) {
            selectionKey.cancel();
        }
        mSelectionKey = null;

        ServerSocketChannel serverSocketChannel = mServerSocketChannel;
        mServerSocketChannel = null;
        if (serverSocketChannel != null) {
            try {
                serverSocketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mServerChannelEventHandler = null;
    }

    /**
     * Return local listening port.
     *
     * @return Port
     */
    public int getPort() {
        return mPort;
    }

    /**
     * Handler socket connect to server event.
     *
     * @param event ChannelEvent
     * @return true
     */
    private synchronized boolean handleAccept(ChannelEvent event) {
        if (mListening) {
            Object obj = event.getEventObj();
            if (obj != null && obj instanceof SocketChannel) {
                SocketChannel socketChannel = (SocketChannel) obj;
                try {
                    final SESocketChannel socketChannelClient = new SESocketChannel(socketChannel);
                    for (ServerChannelEventListener listener : mServerChannelEventListenerSet) {
                        listener.onAccept(socketChannelClient);
                    }
                    return true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        return false;
    }

    /**
     * Server channel selection event handler.
     */
    private class ServerChannelEventHandler implements SESocketChannelManager.ChannelEventHandler {
        @Override
        public boolean handleChannelEvent(ChannelEvent event) {
            switch (event.getEventCode()) {
                case ChannelEvent.EVENT_ACCEPT:
                    return handleAccept(event);
                default:
                    return false;
            }
        }
    }

    /**
     * Server socket channel listener.
     */
    public interface ServerChannelEventListener {
        void onAccept(SESocketChannel socketChannelClient);
    }
}
