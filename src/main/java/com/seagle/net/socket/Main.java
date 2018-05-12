package com.seagle.net.socket;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class Main {
    private static final int SERVER_PORT = 55566;
    private static DataSender sSender;
    private static SEServerSocketChannel sServerChannel;

    public static void main(String[] args) {
        if (sSender != null) {
            sSender.interrupt();
        }
        sSender = new DataSender();
        sSender.setDaemon(true);
        sSender.start();
//        connectServerAsync("192.168.1.4", SERVER_PORT);
//        connectServer("192.168.1.4", SERVER_PORT);
        startServer(SERVER_PORT);
    }

    /**
     * Test connect server.
     *
     * @param ipAddress server address
     * @param port      server port
     */
    private static void connectServer(String ipAddress, int port) {
        SESocketChannel channel = new SESocketChannel();
        channel.registerSocketChannelListener(LISTENER);
        try {
            channel.connect(ipAddress, port, null);
            sSender.addSocketChannel(channel);
        } catch (Exception e) {
            e.printStackTrace();
            channel.unRegisterSocketChannelListener(LISTENER);
            sSender.delSocketChannel(channel);
        }
    }

    /**
     * Test connect server by async.
     *
     * @param ipAddress server address
     * @param port      server port
     */
    private static void connectServerAsync(String ipAddress, int port) {
        SESocketChannel channel = new SESocketChannel();
        channel.registerSocketChannelListener(LISTENER);
        try {
            channel.connect(ipAddress, port, new SESocketChannel.ConnectionCallback() {
                @Override
                public void onConnectFailed(SESocketChannel channel, Throwable throwable) {
                    System.out.println("Client connect failed: " + throwable.getMessage());
                    channel.unRegisterSocketChannelListener(LISTENER);
                    sSender.delSocketChannel(channel);
                }

                @Override
                public void onConnected(SESocketChannel channel) {
                    System.out.println("Client connect success!");
                    sSender.addSocketChannel(channel);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Client connect failed: " + e.getMessage());
            channel.unRegisterSocketChannelListener(LISTENER);
            sSender.delSocketChannel(channel);
        }
    }

    /**
     * Start local server listening.
     *
     * @param localPort local server port
     */
    private static void startServer(int localPort) {
        try {
            SEServerSocketChannel server = new SEServerSocketChannel(localPort);
            String serverIP = InetAddress.getLocalHost().getHostAddress();
            server.registerSocketChannelListener(new SEServerSocketChannel.ServerChannelEventListener() {
                @Override
                public void onAccept(SESocketChannel socketChannelClient) {
                    System.out.println("Client connect: " + socketChannelClient.getSocketChannel().socket().getInetAddress());
                    sSender.addSocketChannel(socketChannelClient);
                    socketChannelClient.registerSocketChannelListener(LISTENER);
                }
            });
            server.startServer();
            sServerChannel = server;
            System.out.println("Start server success: " + serverIP + ":" + localPort);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Read form system.in and send data to channel.
     */
    private static final class DataSender extends Thread {
        private Set<SESocketChannel> mSESocketChannelList = new CopyOnWriteArraySet<>();

        public void addSocketChannel(SESocketChannel channel) {
            mSESocketChannelList.add(channel);
        }

        public void delSocketChannel(SESocketChannel channel) {
            mSESocketChannelList.remove(channel);
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                Scanner xx = new Scanner(System.in);
                String readData = xx.nextLine();
                if ("exit".equalsIgnoreCase(readData)) {
                    if (sServerChannel != null) {
                        sServerChannel.closeServer();
                    }
                    return;
                }
                if (readData != null && readData.length() > 0 && mSESocketChannelList.size() > 0) {
                    for (SESocketChannel channel : mSESocketChannelList) {
                        channel.writeData(readData.getBytes());
                    }
                }
            }
            mSESocketChannelList.clear();
        }
    }

    /**
     * Socket channel state listener.
     */
    private static final SESocketChannel.SocketChannelStateListener LISTENER = new SESocketChannel.SocketChannelStateListener() {
        @Override
        public void onDisConnected(SESocketChannel channel) {
            System.out.println("Client disconnected!");
            try {
                System.out.println("Client disconnected!" + channel.getSocketChannel().socket().getInetAddress().getHostAddress());
            } catch (Exception e) {
                e.printStackTrace();
            }
            sSender.delSocketChannel(channel);
            channel.unRegisterSocketChannelListener(this);
        }

        @Override
        public void onReceivedData(SESocketChannel channel, byte[] data) {
            String dataStr = new String(data);
            System.out.println(channel.getSocketChannel().socket().getInetAddress() + " read dta: " + dataStr);
        }
    };
}
