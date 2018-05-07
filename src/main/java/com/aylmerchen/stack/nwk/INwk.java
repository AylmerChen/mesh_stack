package com.aylmerchen.stack.nwk;

/**
 * 网络层接口
 * @author Lasern
 * @date 2018/1/26
 */
public interface INwk {

    /**
     * 广播地址
     */
    long BROADCAST_ADDRESS = -1L;

    /**
     * 网络层广播帧帧头长度：序列号(16),路由类型(1)，源地址(5),发信人地址(5),已经过跳数(1)
     */
    int HEAD_SIZE_BROADCAST = 28;

    /**
     * 接收缓冲相对于发送缓冲的冗余大小
     */
    int EXTRA_SIZE = 20;

    /**
     * 发送时的打包方法
     * @param destAddress 目的地址
     * @param data 待发送数据
     */
    void packaging(long destAddress, byte[] data);

    /**
     * 接收时的解包方法
     * @param data 接收的数据
     */
    void unPackaging(byte[] data);

    /**
     * 注销本层
     */
    void cancelLayer();

    interface LayerCallback {
        /**
         * 打包完成后由外部交由下层继续处理
         * @param sendData 打包完成后的数据
         */
        void packageDownward(byte[] sendData);

        /**
         * 解包完成后由外部传递给上层
         * @param senderId 接收信息的发信人
         * @param receiveData 解包完成的数据
         */
        void unPackageUpward(long senderId, byte[] receiveData);

        /**
         * 该信息需要转发
         * @param transferData 带转发数据
         */
        void transfer(byte[] transferData);
    }
}
