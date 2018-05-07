package com.aylmerchen.stack;

/**
 * 协议栈对外暴露的通信接口
 *
 * @author Lasern
 * @date 2018/5/3
 */
public interface IComm {

    /**
     * 由外部调用的协议栈打包完成后回调的方法
     * @param packagedData 打包完成的数据，交由外部发送
     */
    void stackPackageFinish(byte[] packagedData);

    /**
     * 由外部调用的协议栈解包完成后调用的方法
     * @param sendId 接收的信息的发送者的 id
     * @param unPackagedData 解包后的信息
     */
    void stackUnPackageFinish(long sendId, byte[] unPackagedData);

    /**
     * 路由层上传的待转发数据，由外部来切换到写线程调用协议栈的转发方法
     * @param transferData 待转发数据
     */
    void transfer(byte[] transferData);
}
