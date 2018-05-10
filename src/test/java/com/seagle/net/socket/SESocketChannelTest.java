package com.seagle.net.socket;

import org.junit.Test;

import static org.junit.Assert.*;

public class SESocketChannelTest {

    @Test
    public void connect() {
        SESocketChannel channel = new SESocketChannel();
        try {
            channel.connect("192.168.1.4",5555,null);
            assertTrue(true);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}