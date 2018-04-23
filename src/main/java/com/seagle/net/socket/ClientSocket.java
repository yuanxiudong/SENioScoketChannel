package com.seagle.net.socket;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * <h1>客户端Socket</h1>
 *
 * @author : xiudong.yuan@midea.com.cn
 * @date : 2016/4/28
 */
@SuppressWarnings("unused")
public abstract class ClientSocket {

    public static final String TAG = "ClientSocket";

    /**
     * 连接异常
     */
    public static final int ERROR_CODE_SOCKET_EXCEPTION = -1;

    /**
     * 连接超时
     */
    public static final int ERROR_CODE_CONNECT_TIMEOUT = -2;

    /**
     * 数据接收监听
     */
    protected final Set<ClientSocketDataListener> mDataListenerSet;

    /**
     * 全局线程
     */
    public final static ScheduledExecutorService sExecutor = Executors.newScheduledThreadPool(2);

    public ClientSocket() {
        mDataListenerSet = new CopyOnWriteArraySet<>();
    }

    /**
     * 注册数据监听
     *
     * @param listener 监听器
     * @return true or false
     */
    public boolean registerDataListener(ClientSocketDataListener listener) {
        return mDataListenerSet.add(listener);
    }

    /**
     * 取消数据监听
     *
     * @param listener 监听器
     * @return true or false
     */
    public boolean unRegisterDataListener(ClientSocketDataListener listener) {
        return mDataListenerSet.remove(listener);
    }

    /**
     * Socket是否连接
     *
     * @return true - 连接
     */
    public abstract boolean isConnected();

    /**
     * 写数据
     *
     * @param data 带写入的数据
     * @return true - 写入成功
     */
    public abstract boolean writeData(byte[] data);

    /**
     * 返回IP地址
     *
     * @return IP
     */
    public abstract String getIP();

    /**
     * 获取端口
     *
     * @return 端口
     */
    public abstract int getPort();

    /**
     * 断开Socket连接。不会触发Socket断开连接回调
     */
    public abstract void disConnect();

    /**
     * 客户端Socket数据监听
     */
    public interface ClientSocketDataListener {
        /**
         * 读取到数据
         *
         * @param channel 通道
         * @param data    数据
         */
        void onReadData(byte[] data, ClientSocket channel);
    }
}
