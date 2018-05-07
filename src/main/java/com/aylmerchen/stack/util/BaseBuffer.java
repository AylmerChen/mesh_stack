package com.aylmerchen.stack.util;

import android.support.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 基础功能：1.发送时构造本层的发送包
 *          2.接收时，对本层接收数据进行解包
 * @author Lasern
 * @date 2018/1/10
 */
public class BaseBuffer {

    public static final String TAG = BaseBuffer.class.getSimpleName();

    private ByteBuffer mBuffer;
    private int mLength;

    public BaseBuffer(int payload) {
        mBuffer = ByteBuffer.allocate(payload);
        mBuffer.order(ByteOrder.BIG_ENDIAN);
        mLength = 0;
    }

    public int getBufferLength() {
        return mLength;
    }

    /**
     * 读取待发送数据，会将缓冲清空
     * @return 待发送数据
     */
    public byte[] getAllBytes(){
        byte[] temp = get(0, mLength);
        clear();
        return temp;
    }



    //--------------------------------------------------------------------------------


    /**
     * 若接收还未完成，即还有后续包，则可用该方法继续添加数据
     * @param receiveMessage 追加的数据
     */
    public void put(@NonNull byte[] receiveMessage){
        mBuffer.put(receiveMessage, 0, receiveMessage.length);
        mLength += receiveMessage.length;
    }

    /**
     * 绝对读取方法(不影响position的值)， 从 startPosition 位置开始读取 length 个字节
     * @param startPosition 起始位置下标
     * @param length 预读取长度
     * @return 读取的字节数组
     */
    public byte[] get(int startPosition, int length) {

        // 标记原先的 position 位置
        int mMark = mBuffer.position();

        // 移动到需要读取的起始位置
        mBuffer.position(startPosition);

        if (length > mBuffer.remaining()) {
            length = mBuffer.remaining();
        }

        byte[] temp = new byte[length];
        mBuffer.get(temp, 0, length);

        // 恢复原先的 position 位置，以便继续加入数据
        mBuffer.position(mMark);
        return temp;
    }

    public void putUnsignedShort(int num) {
        mBuffer.putShort((short) (num & 0x0000ffff));
        mLength += 2;
    }

    public void putLong(long num) {
        mBuffer.putLong(num);
        mLength += 8;
    }

    /**
     * 相对 add
     * @param num
     */
    public void putInt(int num) {
        mBuffer.putInt(num);
        mLength += 4;
    }

    public void putByte(byte b) {
        mBuffer.put(b);
        mLength += 1;
    }

    public int getUnsignedShort(int index) {
        return mBuffer.getShort(index) & 0x0000ffff;
    }

    public long getLong(int index) {
        return mBuffer.getLong(index);
    }

    /**
     * 绝对 get
     * @param index
     * @return
     */
    public int getInt(int index) {
        return mBuffer.getInt(index);
    }

    public double getDouble(int index) {
        return mBuffer.getDouble(index);
    }

    public byte getByte(int index) {
        return mBuffer.get(index);
    }

    /**
     * 清空缓冲
     */
    public void clear(){
        mBuffer.clear();
        mLength = 0;
    }
}
