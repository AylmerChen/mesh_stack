package com.aylmerchen.stack.phy;


import android.util.Log;

import com.aylmerchen.stack.util.BaseBuffer;
import com.aylmerchen.stack.util.MyTimer;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aylmerchen.stack.BuildConfig.debug;


/**
 * 协议栈物理层，负责和外设通过蓝牙来通信
 * @author Lasern
 * @date 2018/1/9
 */
public class LayerPhy implements IPhy {

    private static final String TAG = LayerPhy.class.getSimpleName();

    /**
     * 发送的数据部分的最大长度
     */
    private final int MAX_SEND_ROW_DATA_SIZE;

    /**
     * 蓝牙包大小，不能超过远端硬件的蓝牙接收属性值的最大长度
     */
    private static final int PACKAGE_MAX_SIZE = 19;

    /**
     * 蓝牙包发送时，每个包之间的最长间隔时间
     */
    private static final int PACKAGE_SEND_TIMEOUT = 1500;

    /**
     * 蓝牙包接收超时标准
     */
    private static final int PACKAGE_RECEIVE_TIMEOUT = 5000;

    private static final int START = 0;
    private static final int WAIT_PACKAGE = 1;

    /**
     * 接收蓝牙数据 状态机
     */
    private int state;

    private MyTimer receiveTimer;
    private Runnable timeoutTask;

    /**
     * 发送蓝牙包后，远端是否接收完成的标志
     */
    private static volatile AtomicBoolean isPackageSend = new AtomicBoolean(false);

    private BaseBuffer sendBuffer;
    private BaseBuffer receBuffer;

    /**
     * 打包 or 解包 完成回调，把结果交回给协议栈上下文
     */
    private LayerCallback layerCallback;

    /**
     * 本层打包完成后调用的方法
     */
    private void phySend(byte[] dataToSend){
        layerCallback.packageFinish(dataToSend);
    }

    /**
     * 本层解包完成后调用的方法
     */
    private void phyReceive(byte[] dataToUp){
        layerCallback.unPackageFinish(dataToUp);
    }


    /**
     * 物理层 constructor
     * @param sendBufferSize 最大缓冲最大长度
     * @param callback 本层对外的接口
     */
    public LayerPhy(int sendBufferSize, LayerCallback callback) {

        MAX_SEND_ROW_DATA_SIZE = sendBufferSize - HEAD_SIZE;

        layerCallback = callback;

        sendBuffer = new BaseBuffer(sendBufferSize);

        receBuffer = new BaseBuffer(sendBufferSize + EXTRA_SIZE);

        receiveTimer = new MyTimer();
        timeoutTask = new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "蓝牙包接收超时");
                resetReceBuffer();
            }
        };
    }

    /**
     *  注意：这里为了兼容蓝牙硬件做出了规定： 数据长度指的是除去 帧起始标识 和 数据长度 这两个字段之外的字节数
     *
     *  帧起始标识(3) |  数据长度(1)  || 数据（n <= 124 ）  |
     *    AT+       |      <=124   ||                   |
     *
     * @param data  待发送的数据
     */
    @Override
    public void packaging(byte[] data) {
        if (data.length <= MAX_SEND_ROW_DATA_SIZE) {
            sendPhyPackage(getPhyPackage(data));
        }
    }

    /**
     * 生成物理层待发送包
     * @param data 待发送数据
     * @return
     */
    private byte[] getPhyPackage(byte[] data) {
        sendBuffer.put(HEAD_AT);
        sendBuffer.putByte((byte) data.length);
        sendBuffer.put(data);
        return sendBuffer.getAllBytes();
    }

    /**
     * 发送单个物理层包，单个帧需要分成若干蓝牙包来发送
     * @param phyPackage 物理层包
     */
    private void sendPhyPackage(byte[] phyPackage) {

        if (phyPackage.length <= 0) {
            return ;
        }

        int position = 0;
        int remainSize = phyPackage.length;
        byte[] btPackage = new byte[PACKAGE_MAX_SIZE];

        while ( remainSize > 0 ) {

            if (remainSize < PACKAGE_MAX_SIZE) {
                btPackage = new byte[remainSize];
                System.arraycopy(phyPackage, position, btPackage, 0, remainSize);
            } else {
                System.arraycopy(phyPackage, position, btPackage, 0, PACKAGE_MAX_SIZE);
            }

            position += PACKAGE_MAX_SIZE;
            remainSize -= PACKAGE_MAX_SIZE;

            // 发送前先将远端接收标识置为false
            isPackageSend.set(false);

            if(debug) {
                Log.e(TAG, "发送蓝牙包, 包长：" + btPackage.length);
            }

            // 具体与硬件相关的发送函数由外部实现
            phySend(btPackage);

            if (remainSize > 0) {
                pauseBetweenBtPackages();
            }
        }
    }

    /**
     * 发送完单个蓝牙包后需要暂停当前的发送线程，等待包发送完毕后的系统回调来唤醒，从而继续发送
     */
    private void pauseBetweenBtPackages(){
        long startTime = System.currentTimeMillis();
        boolean timeout = false;

        // 当远端唤醒或是超时时跳出
        while( !isPackageSend.get() && !timeout){
            if (System.currentTimeMillis() - startTime > PACKAGE_SEND_TIMEOUT) {
                timeout = true;
            }
        }
    }

    /**
     * TODO 该方法需要在外部调用，从其他线程唤醒发送线程继续发送
     */
    public void invokePHYSending(){
        isPackageSend.set(true);
    }




    @Override
    public void receive(byte[] btPackage) {

        // 每次接收到数据都需要重启定时器进行延时检测
        receiveTimer.resetTimer();

        // PACKAGE_RECEIVE_TIMEOUT 秒没接收到下一包 即认为超时
        receiveTimer.startTimer(timeoutTask, PACKAGE_RECEIVE_TIMEOUT);

        appendPackage(btPackage);
        if(debug) {
            Log.e(TAG, "接收到蓝牙包，包长：" + btPackage.length + "， 当前 packageBuffer 长度：" + receBuffer.getBufferLength());
        }

        switch (state) {
            case START:
                if (isFirstPackage()) {
                    if(debug) {
                        Log.e(TAG, "处于 START 状态，接收到蓝牙帧的第一个蓝牙包");
                    }

                    if (isPackageValid()) {
                        if (isPackageTruncated()) {
                            if (debug) {
                                Log.e(TAG, "该帧的所有蓝牙包没有接收完成，继续等待");
                            }
                            state = WAIT_PACKAGE;
                        } else {

                            if(debug) {
                                Log.e(TAG, "该帧的所有蓝牙包接收完成, 复位包定时器");
                            }

                            byte[] packageData = getData();

                            // 一帧接收完成，复位定时器
                            receiveTimer.resetTimer();
                            resetReceBuffer();

                            // 接收完成，将数据传递给上层
                            phyReceive(packageData);
                        }
                    } else {
                        receiveTimer.resetTimer();
                        resetReceBuffer();
                    }

                } else {
                    if(debug) {
                        Log.e(TAG, "处于 START 状态，接收到非蓝牙包");
                    }
                    receiveTimer.resetTimer();
                    resetReceBuffer();
                }
                break;

            case WAIT_PACKAGE:

                // 如果接收到的数据比预定接收的数据长，或者接收超时，则认为包已传输结束
                if ( !isPackageTruncated() ) {

                    if(debug) {
                        Log.e(TAG, "该帧的所有蓝牙包接收完成，复位包定时器");
                    }
                    byte[] packageData = getData();

                    // 一帧接收完成，复位定时器
                    receiveTimer.resetTimer();
                    resetReceBuffer();
                    phyReceive(packageData);
                }
                break;

            default:
                break;
        }
    }

    /**
     * 追加接收的蓝牙包
     * @param btPackage 待追加数据
     */
    private void appendPackage(byte[] btPackage) {
        receBuffer.put(btPackage);
    }

    /**
     * 只有当有效数据长达大于 4 时( “AT+”（3）+ 数据长度（1）)，接收到的数据包才是有效的
     * @return
     */
    private boolean isPackageValid() {
        return getDataLength() > 4;
    }

    /**
     * 读取 帧起始标识 AT+, 判断是不是第一个包
     * @return
     */
    private boolean isFirstPackage() {
        return Arrays.equals(HEAD_AT, receBuffer.get(0, 3));
    }

    /**
     * 读取数据长度
     * @return
     */
    private int getDataLength(){
        return receBuffer.getByte(3) & 0x000000FF;
    }

    /**
     * 读取数据
     * @return
     */
    private byte[] getData() {
        return receBuffer.get(4, getDataLength());
    }

    /**
     * 预定接收的收据长度大于实际接收到的数据长度，则认为数据包接收未完成
     * @return
     */
    private boolean isPackageTruncated(){
        return getDataLength() > receBuffer.getBufferLength() - 4;
    }

    private void resetReceBuffer(){
        state = START;
        receBuffer.clear();
    }

    @Override
    public void cancelLayer() {

        if (receiveTimer != null) {
            receiveTimer.cancelTimer();
            timeoutTask = null;
        }

        if (sendBuffer != null) {
            sendBuffer = null;
        }

        if (receBuffer != null) {
            receBuffer = null;
        }

        layerCallback = null;
    }
}
