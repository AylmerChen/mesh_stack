package com.aylmerchen.stack;

/**
 * 对外暴露的协议栈全局信息配置接口
 *
 * @author Lasern
 * @date 2018/5/3
 */
public interface IConfig {

    /**
     * 获取外部设置的物理层最大发送长度
     * @return 最大发送长度，单位：字节
     */
    int getPhyMaxSendSize();

    /**
     * 获取本机的地址
     * @return 本机地址，即手机号
     */
    long getMyAddress();

    /**
     * 获取外部设置的连接层蓝牙帧发送间隔
     * @return 帧间隔，单位:ms
     */
    long getFrameGap();

}
