package com.seagle.net.socket;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static final List<SESocketChannel> CLIENT_LIST = new ArrayList<>();
    public static void main(String[] args) {
        try {
            SEServerSocketChannel server = new SEServerSocketChannel(5555);
            String serverIP = InetAddress.getLocalHost().getHostAddress();
            server.registerSocketChannelListener(new SEServerSocketChannel.ServerChannelEventListener() {
                @Override
                public void onAccept(SESocketChannel socketChannelClient) {
                    System.out.println("Client connect: " + socketChannelClient.getSocketChannel().socket().getInetAddress());
                    CLIENT_LIST.add(socketChannelClient);
                    socketChannelClient.registerSocketChannelListener(LISTENER);
                }
            });
            server.startServer();
            System.out.println("Start server success: " + serverIP + ":" + 5555);
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while(!Thread.currentThread().isInterrupted()){
                        Scanner xx = new Scanner( System.in );
                        String readData = xx.nextLine();
                        if(readData!= null && readData.length()>0 && CLIENT_LIST.size() >0){
                            for(SESocketChannel channel : CLIENT_LIST){
                                channel.writeData(readData.getBytes());
                            }
                        }
                    }
                }
            });
            thread.start();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static final SESocketChannel.SocketChannelStateListener LISTENER = new SESocketChannel.SocketChannelStateListener() {
        @Override
        public void onDisConnected(SESocketChannel channel) {
            System.out.println("Client disconnected!");
            try {
                System.out.println("Client disconnected!"+channel.getSocketChannel().getRemoteAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
            CLIENT_LIST.remove(channel);
        }

        @Override
        public void onReceivedData(SESocketChannel channel, byte[] data) {
            String dataStr = new String(data);
            System.out.println(channel.getSocketChannel().socket().getInetAddress() + " read dta: " + dataStr);
        }
    };


//        SESocketChannel channel = new SESocketChannel();
//        try {
//            channel.connect("192.168.1.4", 5555, null);
//            channel.registerSocketChannelListener(new SESocketChannel.SocketChannelStateListener() {
//                @Override
//                public void onDisConnected(SESocketChannel channel) {
//                    System.out.println("Disconnected");
//                }
//
//                @Override
//                public void onReceivedData(SESocketChannel channel, byte[] data) {
//                    System.out.println("Read data: " + new String(data));
//                }
//            });
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

}
