package com.aylmerchen.stack.mediator;

/**
 * 适配层接口
 * @author Lasern
 * @date 2018/1/26
 */
public interface IMediator {

    /**
     * 帧头长度,  数据流标识(2) |   帧总数(2)  |    当前帧序号(2)
     */
    int HEAD_SIZE = 6;

    /**
     * 接收缓冲相对于发送缓冲的冗余大小
     */
    int EXTRA_SIZE = 20;

    /**
     * 发送时的打包方法
     * @param destAddress 上层传来的发送信息的目的地址
     * @param data 待发送数据
     */
    void packaging(long destAddress, byte[] data);


    /**
     * 接收时的解包方法
     * @param srcAddress 下层传来的接收信息的源地址
     * @param data 接收的数据
     */
    void unPackaging(long srcAddress, byte[] data);


    /**
     * 退出注销本层资源
     */
    void cancelLayer();

    /**
     * 打包、解包完成对外所暴露的接口
     */
    interface LayerCallback {
        /**
         * 打包完成后对外暴露数据的接口，
         * @param destAddress 本次发送的信息的目的地址
         * @param sendData 打包完成的数据
         */
        void packageFinish(long destAddress, byte[] sendData);

        /**
         * 解包完成后对外暴露数据的接口
         * @param srcAddress 本次接收的信息的源地址
         * @param receiveData 解包完成的数据
         */
        void unPackageFinish(long srcAddress, byte[] receiveData);

        /**
         * 获取外部设置的蓝牙帧间隔
         * @return 帧间隔，单位 ms
         */
        long getFrameGap();
    }
}
