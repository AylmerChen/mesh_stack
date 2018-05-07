package com.aylmerchen.stack.mediator;

import android.util.Log;

import com.aylmerchen.stack.util.BaseBuffer;
import com.aylmerchen.stack.util.MyTimer;

import java.util.Random;

import static com.aylmerchen.stack.BuildConfig.debug;


/**
 * 协议栈适配层，将应用层数据进行拆分
 * @author Lasern
 * @date 2018/1/16
 */
public class LayerMediator implements IMediator {

    private static final String TAG = LayerMediator.class.getSimpleName();

    /**
     * 帧超时标准，接收时下一帧超过该时限还未到达则认为超时
     */
    private static final int FRAME_TIMEOUT = 20000;

    /**
     * 每次最多允许发送的帧数，即最多可拆分的包数，最大是 65536, 因为当前帧序号字段由 2 字节组成
     */
    private static final int FRAME_MAX_COUNT = 65536;



    /**
     * 每一帧最大携带的纯数据量（字节)
     */
    private final int FRAME_MAX_ROW_DATA_SIZE;

    /**
     * 最大允许发送的数据量，即应用层传入的数据不应超过这个大小
     */
    private final int FRAME_STREAM_MAX_SIZE;

    /**
     * 用于构建发送的单个蓝牙帧
     */
    private BaseBuffer sendBuffer;

    /**
     * 用于接收单个蓝牙帧，提取各字段数据
     */
    private BaseBuffer receBuffer;

    /**
     * 用于缓存接收的所有帧的数据部分
     */
    private BaseBuffer receCache;

    /**
     * 记录本次传输的发信的源地址
     */
    private long srcAddress = -1;

    /**
     * 记录本次传输的帧流 id
     */
    private int currentFrameStreamId = -1;

    /**
     * 记录本次传输的帧总数
     */
    private int currentFrameNum = -1;

    /**
     * 记录当前传输的帧 id 以便和后续的帧比较
     */
    private int lastFrameId = -1;

    /**
     * 帧超时定时器
     */
    private MyTimer frameTimer;

    /**
     * 帧超时任务
     */
    private Runnable frameTimeoutTask;

    /**
     * 外部回调
     */
    private LayerCallback layerCallback;

    public LayerMediator(int bufferSize, LayerCallback callback) {

        FRAME_MAX_ROW_DATA_SIZE = bufferSize - HEAD_SIZE;
        FRAME_STREAM_MAX_SIZE = FRAME_MAX_ROW_DATA_SIZE * FRAME_MAX_COUNT;

        layerCallback = callback;

        sendBuffer = new BaseBuffer(bufferSize + EXTRA_SIZE);
        receBuffer = new BaseBuffer(bufferSize + EXTRA_SIZE);

        frameTimer = new MyTimer();
        frameTimer.initTimer();
        frameTimeoutTask = new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "帧接收超时");
                resetReceiveState();
            }
        };
    }

    /** 将原始数据拆分传输
     *
     *  数据流表示的目的：因为帧最后需要 lora 转发，所以网络可能出现同一节点发出的不同帧同时抵达了某个中间节点，
     *  所以每次发送的信息还是需要区分的
     *
     *  帧结构：
     *  数据流标识(2) |   帧总数(2)  |    当前帧序号(2)  | 数据(<=108)  |
     *  随机生成     |  最多65536帧  |   表明是第几帧    |   拆分的数据  |
     *
     * @param rowMessage  待发送的数据
     */
    @Override
    public void packaging(long destAddress, byte[] rowMessage) {
        if ( rowMessage.length <= 0) {
            return ;
        }

        byte[] message;

        // 如果待发送信息超过允许范围则截断数据
        if ( rowMessage.length > FRAME_STREAM_MAX_SIZE) {
            message = new byte[FRAME_STREAM_MAX_SIZE];
            System.arraycopy(rowMessage, 0, message, 0, FRAME_STREAM_MAX_SIZE);
        } else {
            message = rowMessage;
        }

        // 计算数据帧的总帧数
        int consult = message.length / FRAME_MAX_ROW_DATA_SIZE;
        int remainder = message.length % FRAME_MAX_ROW_DATA_SIZE;
        int frameNum = ( remainder == 0 )? consult : consult + 1;

        // 帧流标识的范围 0 ~ 65535
        int frameStreamID = new Random().nextInt(FRAME_MAX_COUNT);

        if (debug) {
            Log.e(TAG, "待发送帧流的总长度:" + message.length + ", 帧流ID:" + frameStreamID + ", 帧总数:" + frameNum);
        }

        int frameID = 0;
        int position = 0;
        int remainSize = message.length;
        byte[] frame;
        byte[] data = new byte[FRAME_MAX_ROW_DATA_SIZE];

        while ( remainSize > 0 ) {

            if (remainSize < FRAME_MAX_ROW_DATA_SIZE) {
                data = new byte[remainSize];
                System.arraycopy(message, position, data, 0, remainSize);
                frame = getSendBytes(frameStreamID, frameNum, frameID, data);
            } else {
                System.arraycopy(message, position, data, 0, FRAME_MAX_ROW_DATA_SIZE);
                frame = getSendBytes(frameStreamID, frameNum, frameID, data);
            }

            position += FRAME_MAX_ROW_DATA_SIZE;
            remainSize -= FRAME_MAX_ROW_DATA_SIZE;

            if (debug) {
                Log.e(TAG, "发送第 " + frameID + " 帧, 帧长:" + frame.length);
            }


            // 把帧交给协议栈上下文，由上下文转交给下一层去处理，这里之后的一层是路由层
            layerCallback.packageFinish(destAddress, frame);

            // TODO 除最后一帧外，每帧发送之间都需要暂停，给外设硬件处理时间，提升蓝牙速度后，可以加入 ack
            if (remainSize > 0) {
                pauseBetweenFrames();
                if (debug) {
                    Log.e(TAG, "帧流恢复发送");
                }
            }

            frameID++;
        }
    }

    private byte[] getSendBytes(int frameStreamID, int frameCount, int frameID, byte[] data) {
        sendBuffer.putUnsignedShort(frameStreamID);
        sendBuffer.putUnsignedShort(frameCount);
        sendBuffer.putUnsignedShort(frameID);
        sendBuffer.put(data);
        return sendBuffer.getAllBytes();
    }

    /**
     * 每帧之间进行延时，让远端硬件有时间处理数据
     */
    private void pauseBetweenFrames() {
        try {
            Thread.sleep(layerCallback.getFrameGap());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }




    @Override
    public void unPackaging(long srcAddress, byte[] frame) {

        if(debug) {
            Log.e(TAG, "连接层接收到数据，源地址:" + srcAddress +  " 数据长度：" + frame.length);
        }

        // 获取蓝牙帧中的各字段
        receBuffer.put(frame);
        int streamId = getStreamId();
        int frameNum = getFrameNum();
        int frameId = getFrameId();
        byte[] frameData = getFrameData();
        receBuffer.clear();

        if (debug) {
            Log.e(TAG, "接收到帧, 帧流ID:" + streamId + ", 帧总数：" + frameNum + ", 帧序号:" + frameId);
        }

        // 大部分情况下都不会超过一帧,所以可以省去后续步骤
        if (frameNum == 1) {
            // receCache.put(frameData);
            // layerCallback.unPackageFinish(receCache.getAllBytes());
            // resetReceiveState();
            layerCallback.unPackageFinish(srcAddress, frameData);
            return;
        }

        // 当需要接收多帧时，每次接收到新的一帧都需要重启定时器检测延时
        frameTimer.resetTimer();
        frameTimer.startTimer(frameTimeoutTask, FRAME_TIMEOUT);

        // 需要接收多帧时，第一帧时需要做一些初始化工作
        if (frameId == 0) {

            this.srcAddress = srcAddress;
            currentFrameStreamId = streamId;
            currentFrameNum = frameNum;
            lastFrameId = frameId;

            // TODO 根据具体的帧总数来分配本次的接收总缓冲
            receCache = new BaseBuffer(frameNum * FRAME_MAX_ROW_DATA_SIZE + EXTRA_SIZE);
            receCache.put(frameData);

            if (debug) {
                Log.e(TAG, "第 0 帧接收成功， 帧数据长度:" + frameData.length);
            }
            return;
        }


        // 判断后续的 数据帧 是否属于此次传输的帧流
        if (this.srcAddress == srcAddress && currentFrameStreamId == streamId ) {

            if (debug) {
                Log.e(TAG, "该数据帧 属于 本次传输帧流");
            }

            // 帧序号要连续,且在指定范围
            if (frameId > lastFrameId && frameId < currentFrameNum ) {

                if (debug) {
                    Log.e(TAG, "第 " + frameId + " 帧接收成功， 帧数据长度:" + frameData.length);
                }

                lastFrameId = frameId;
                receCache.put(frameData);

                // 本次的数据帧是否是最后一个数据帧
                if (frameId == currentFrameNum - 1) {

                    if (debug) {
                        Log.e(TAG, "帧流接收完成，复位帧定时器，本次传输的数据总长度:" + receCache.getBufferLength());
                    }

                    // 帧流传输完毕，复位帧定时器
                    frameTimer.resetTimer();
                    layerCallback.unPackageFinish(this.srcAddress, receCache.getAllBytes());
                    resetReceiveState();
                }
            }

        } else if (debug) {
            Log.e(TAG, "该帧 不属于 本次传输帧流");
        }

    }

    /**
     * 提取帧流标识
     */
    private int getStreamId() {
        return receBuffer.getUnsignedShort(0);
    }

    /**
     * 提取帧总数
     */
    private int getFrameNum() {
        return receBuffer.getUnsignedShort(2);
    }

    /**
     * 提取帧序号
     */
    private int getFrameId() {
        return receBuffer.getUnsignedShort(4);
    }

    /**
     * 提取纯数据
     */
    private byte[] getFrameData() {
        return receBuffer.get(6, receBuffer.getBufferLength() - 6);
    }

    /**
     * 重置接收状态
     */
    private void resetReceiveState() {
        srcAddress = -1;
        currentFrameStreamId = -1;
        currentFrameNum = -1;
        lastFrameId = -1;

        receBuffer.clear();
        receCache.clear();
    }

    @Override
    public void cancelLayer() {
        frameTimer.cancelTimer();
        if (sendBuffer != null) {
            sendBuffer = null;
        }
        if (receBuffer != null) {
            receBuffer = null;
        }
        if (receCache != null) {
            receCache = null;
        }
    }
}
