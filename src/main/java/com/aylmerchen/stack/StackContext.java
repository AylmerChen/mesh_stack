package com.aylmerchen.stack;

import com.aylmerchen.stack.mediator.IMediator;
import com.aylmerchen.stack.mediator.LayerMediator;
import com.aylmerchen.stack.nwk.INwk;
import com.aylmerchen.stack.nwk.LayerNwk;
import com.aylmerchen.stack.phy.IPhy;
import com.aylmerchen.stack.phy.LayerPhy;

/**
 * 协议栈的客户端类，协议栈不包括应用层，只含适配层，路由层，硬件层（蓝牙层）
 *
 * @author AylmerChen
 * @date 2018/1/15
 */
public class StackContext {

    /**
     * 广播地址
     */
    public static final long BROADCAST_ADDRESS = INwk.BROADCAST_ADDRESS;

    private IMediator mediatorLayer;
    private INwk nwkLayer;
    private IPhy phyLayer;


    /**
     * 初始化协议栈上下文
     *
     * @param stackConfig 协议栈配置回调
     * @param stackComm 协议栈的打包和解包回调
     */
    public StackContext(final IConfig stackConfig, final IComm stackComm) {

        // 物理层 单次最大允许发送长度，即蓝牙外设的缓冲区大小 128
        int phyMaxSendSize = stackConfig.getPhyMaxSendSize();

        // 网络层 单次最大允许发送长度，128 - 4 = 124
        int nwkMaxSendSize = phyMaxSendSize - IPhy.HEAD_SIZE;

        // 连接层 单次最大允许发送长度(发送广播帧)，124 - 28 = 96
        int medMaxSendSize = nwkMaxSendSize - INwk.HEAD_SIZE_BROADCAST;

        // 初始化连接层
        this.mediatorLayer = new LayerMediator(medMaxSendSize, new IMediator.LayerCallback() {
            @Override
            public void packageFinish(long destAddress, byte[] sendData) {
                nwkLayer.packaging(destAddress, sendData);
            }

            @Override
            public void unPackageFinish(long srcAddress, byte[] receiveData) {
                stackComm.stackUnPackageFinish(srcAddress, receiveData);
            }

            @Override
            public long getFrameGap() {
                return stackConfig.getFrameGap();
            }
        });


        // 初始化 网络层
        this.nwkLayer = new LayerNwk(stackConfig.getMyAddress(), nwkMaxSendSize, new INwk.LayerCallback() {

            @Override
            public void packageDownward(byte[] sendData) {
                phyLayer.packaging(sendData);
            }

            @Override
            public void unPackageUpward(long srcAddress, byte[] receiveData) {
                mediatorLayer.unPackaging(srcAddress, receiveData);
            }

            @Override
            public void transfer(byte[] transferData) {
                stackComm.transfer(transferData);
            }
        });


        // 初始化 物理层
        this.phyLayer = new LayerPhy(phyMaxSendSize, new IPhy.LayerCallback() {
            @Override
            public void packageFinish(byte[] sendData) {
                stackComm.stackPackageFinish(sendData);
            }

            @Override
            public void unPackageFinish(byte[] receiveData) {
                nwkLayer.unPackaging(receiveData);
            }
        });
    }

    /**
     * 发送广播帧和普通帧方法
     */
    public void send(long destAddress, byte[] msg) {
        if (destAddress != BROADCAST_ADDRESS) {
            mediatorLayer.packaging(destAddress, msg);
        } else {
            mediatorLayer.packaging(BROADCAST_ADDRESS, msg);
        }
    }


    /**
     * 发送转发帧
     * 待转发数据实际来自路由层，所以直接交给物理层转发
     */
    public void transfer(byte[] transferData){
        phyLayer.packaging(transferData);
    }

    /**
     * TODO 本例的特殊性，物理层每发送一个蓝牙包就要暂停，等待唤醒
     */
    public void invokePHYSending() {
        ((LayerPhy)phyLayer).invokePHYSending();
    }

    /**
     * 协议栈对外部暴露的接收方法
     * @param msg 接收的信息
     */
    public void receive(byte[] msg) {
        // 外部传入的接收信息先交给物理层处理
        phyLayer.receive(msg);
    }

    /**
     * 注销协议栈
     */
    public void cancelStack() {
        mediatorLayer.cancelLayer();
        nwkLayer.cancelLayer();
        phyLayer.cancelLayer();
    }
}
