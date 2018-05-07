package com.aylmerchen.stack.phy;

/**
 * 物理层接口
 * @author Lasern
 * @date 2018/1/26
 */
public interface IPhy {

    /**
     * 为了兼容早期的协议，与硬件约定的蓝牙 帧起始标识 ："AT+"
     */
    byte[] HEAD_AT = { 0x41, 0x54, 0x2B };

    /**
     * 物理层协议约定的帧头长度：帧起始标识(3) |  数据长度(1)  |
     */
    int HEAD_SIZE = 4;

    /**
     * 接收缓冲相对于发送缓冲的冗余大小
     */
    int EXTRA_SIZE = 20;

    /**
     * 发送时的打包方法
     * @param data 待发送数据
     */
    void packaging(byte[] data);

    /**
     * 接收时的解包方法
     * @param data 接收的数据
     */
    void receive(byte[] data);

    /**
     * 注销本层资源
     */
    void cancelLayer();

    interface LayerCallback {
        /**
         * 打包完成后对外暴露数据的接口
         * @param sendData 打包完成的数据
         */
        void packageFinish(byte[] sendData);

        /**
         * 解包完成后对外暴露数据的接口
         * @param receiveData 解包完成的数据
         */
        void unPackageFinish(byte[] receiveData);
    }
}
