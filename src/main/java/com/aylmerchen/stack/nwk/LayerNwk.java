package com.aylmerchen.stack.nwk;


import com.aylmerchen.stack.util.BaseBuffer;

import java.util.UUID;

/**
 * 网络层，实现多跳传输功能
 * @author Lasern
 * @date 2018/1/9
 */
public class LayerNwk implements INwk {

    /**
     * 不同路由类型的路由帧:广播帧
     */
    public static final byte BROADCAST = 1;

//    private static final byte SPECIFIC_RECEIVER = 0;   // 指定目的地址的路由
//    private static final byte REQ_NEXT_HELLO = 2;      // 请求验证路由有效,验证下一跳路由是否在线
//    private static final byte REP_NEXT_ACK  = 3;       // 下一跳地址路径验证成功
//    private static final byte REQ_SEND = 4;            // 下一跳可达，传输数据
//    private static final byte REP_REMOTE_ACK = 5;      // 整个静态路由验证过程成功
//    private static final byte REP_REMOTE_ERROR  = 6;   // 静态路由验证失败

    /**
     * 接收帧缓存最大长度
     */
    private static final int MAX_RECE_CACHE_SIZE = 100;

    /**
     * 相邻表中记录的超时时间,单位 ms
     */
    private static final int MAX_NEAR_TIME_OUT = 5 * 60 * 1000;

    /**
     * 最大跳数
     */
    private static final int MAX_RIP = 3;

    /**
     * 发送的数据部分的最大长度
     */
    private final int MAX_SEND_ROW_DATA_SIZE;

    private final long USER_ADDRESS;

    private BaseBuffer sendBuffer;
    private BaseBuffer receBuffer;

    private LayerCallback layerCallback;

    /**
     * 接收缓冲，缓存接收到的路由帧，用于对比，防止重复接收转发
     */
    private ReceiveCache<UUID> receCache;

    /**
     * 路由表
     */
    private RouteTable routeTable;

    /**
     * 相邻表
     */
    private NearTable nearTable;


    /**
     * 由外部初始化路由层的配置
     *
     * @param myAddress 自己的发送地址
     * @param sendBufferSize 网络层收发缓冲的最大长度
     * @param callback 本层对外的回调接口
     */
    public LayerNwk(long myAddress, int sendBufferSize, LayerCallback callback) {

        MAX_SEND_ROW_DATA_SIZE = sendBufferSize - HEAD_SIZE_BROADCAST;

        layerCallback = callback;

        USER_ADDRESS = myAddress;

        sendBuffer = new BaseBuffer(sendBufferSize);
        receBuffer = new BaseBuffer(sendBufferSize + EXTRA_SIZE);

        receCache = new ReceiveCache<>(MAX_RECE_CACHE_SIZE);
        routeTable = new RouteTable();
        nearTable = new NearTable(MAX_NEAR_TIME_OUT);
    }

    /**
     *  destAddress(5) |   srcAddress  | 数据(<=114)  |
     *
     * @param destAddress 目的地址
     * @param data   上层传来的数据
     */
    @Override
    public void packaging(long destAddress, byte[] data) {
        if (data.length <= MAX_SEND_ROW_DATA_SIZE) {
            // TODO
            byte[] sendData = (destAddress == BROADCAST_ADDRESS) ? getBroadcastFrame(data) : getSpecificFrame(destAddress, data) ;
            layerCallback.packageDownward(sendData);
        }
    }

    /**
     * 生成待发送广播帧
     */
    private byte[] getBroadcastFrame(byte[] data) {
        return getBroadcastFrame(UUID.randomUUID(), USER_ADDRESS, USER_ADDRESS, 0, data);
    }

    /**
     * 生成待发送的广播帧，帧结构：
     * 序列号(16),路由类型(1)，源地址(5),发信人地址(5),已经过跳数(1),数据(n)
     */
    private byte[] getBroadcastFrame(UUID uuid, long srcAddress, long senderAddress, int rip, byte[] data){

        // 序列号
        sendBuffer.putLong(uuid.getMostSignificantBits());
        sendBuffer.putLong(uuid.getLeastSignificantBits());

        // 路由类型
        sendBuffer.putByte(BROADCAST);

        // 源地址
        setAddress(srcAddress);

        // 发信人地址
        setAddress(senderAddress);

        // 已经过跳数
        sendBuffer.putByte((byte) rip);

        // 纯数据
        sendBuffer.put(data);
        return sendBuffer.getAllBytes();

    }

    // TODO 生成待发送的非广播帧
    private byte[] getSpecificFrame(long dest, byte[] data) {
        return new byte[0];
    }



    @Override
    public void unPackaging(byte[] receiveData) {

        receBuffer.put(receiveData);
        UUID uuid = getId();
        byte nwkType = getNWKType();
        long srcAddress = getSrcAddress();
        long senderAddress = getSenderAddress();
        int  rip = getRip();
        byte[] data = getData();

        // 不接收自己发送过的包
        if (srcAddress == USER_ADDRESS) {
            receBuffer.clear();
            return;
        }

        // 检查缓冲，是否之前接收过相同的路由包
        if (receCache.contains(uuid)) {
            receBuffer.clear();
            return;
        }
        receCache.add(uuid);

        // 路由表保存非相邻的节点的路由，如果发信者在路由表中，则之前的路由有误，需要删除
        if(routeTable.contains(senderAddress)){
            routeTable.removeRoute(senderAddress);
        }

        // 更新相邻表
        nearTable.updateNeighbour(senderAddress);

        // 准备转发，已经过跳数+1
        rip++;

        // 相邻表不包含源地址，说明是非相邻节点，需要更新路由表
        if (!nearTable.contains(srcAddress)) {
            routeTable.updateRoute(srcAddress, senderAddress, rip);
        }

        // TODO 目前只完成针对广播帧的接收转发处理
        if (nwkType == BROADCAST) {

            // 读取数据送往应用层
            layerCallback.unPackageUpward(srcAddress, data);

            // 小于跳数界限需要转发
            if(rip < MAX_RIP){

                // TODO 也可以直接在接收的 receiveData 中修改
                // 将发信人改为自己，更新跳数，交给下层转发
                layerCallback.transfer(getBroadcastFrame(uuid, srcAddress, USER_ADDRESS, rip, data));
            }

        }

        receBuffer.clear();
    }



    /**
     * 将 地址 拆分存储，即将 5 字节的手机号拆成 1 + 4 两部分
     * @param address 手机号
     */
    private void setAddress(long address) {
        byte top = (byte)((0x000000ff00000000L & address) >>> 32 );
        int nextInt = (int)((0x00000000ffffffffL & address));
        sendBuffer.putByte(top);
        sendBuffer.putInt(nextInt);
    }

    /**
     * 将 高字节 和 低字节 合成地址读取
     */
    private long generateAddress(byte topByte, int nextBytes) {
        return ((((long) topByte) & 0x00000000000000ffL) << 32) | ((long) nextBytes & 0x00000000ffffffffL);
    }

    /**
     * 读取序列号
     * @return 序列号
     */
    private UUID getId() {
        return new UUID(receBuffer.getLong(0), receBuffer.getLong(8));
    }

    /**
     * 读取路由类型
     * @return 路由类型
     */
    private byte getNWKType() {
        return receBuffer.getByte(16);
    }

    /**
     * 读取 源地址 字段
     * @return 源地址
     */
    private long getSrcAddress() {
        byte top = receBuffer.getByte(17);
        int sec = receBuffer.getInt(18);
        return generateAddress(top, sec);
    }

    /**
     * 读取 发信者 字段
     * @return 发信者
     */
    private long getSenderAddress() {
        byte top = receBuffer.getByte(22);
        int sec = receBuffer.getInt(23);
        return generateAddress(top, sec);
    }

    /**
     * 读取已经过的跳数
     * @return 已经过的跳数
     */
    private byte getRip() {
        return receBuffer.getByte(27);
    }

    /**
     * 去掉路由头的纯数据部分
     * @return 纯数据
     */
    private byte[] getData() {
        return receBuffer.get(28, receBuffer.getBufferLength() - 28);
    }

    /** TODO
     * 保存路由表，相邻表等临时文件，并打上时间戳，下次读取的时候做对比，超过某个时限则认为临时文件已过期
     */
    private void saveTempFile(){

    }


    @Override
    public void cancelLayer() {

        // saveTempFile();

        if (sendBuffer != null) {
            sendBuffer = null;
        }

        if (receBuffer != null) {
            receBuffer = null;
        }
        layerCallback = null;
    }

}
