package com.aylmerchen.stack.nwk;

import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * 接收帧缓存,基于 linkedHashMap 和 lruCache 不同，没有根据使用频率来调整，只是先进先出，且查找复杂度为O(1)
 * 非线程安全
 *
 * @author Lasern
 * @date 2018/3/23
 */

public class ReceiveCache<T> {

    private LinkedHashSet<T> map;

    /**
     * 缓存最大容量，即所容对象个数
     */
    private int maxSize;

    /**
     * 缓存当前所容对象个数
     */
    private int nowSize;


    public ReceiveCache(int maxSize){
        map = new LinkedHashSet<>(50, 1.1f);
        this.maxSize = maxSize;
    }



    public void add(T uuid){
        if(nowSize == maxSize){
            removeEldest();
            nowSize--;
        }
        map.add(uuid);
        nowSize++;
    }


    public boolean contains(T uuid){
        return map.contains(uuid);
    }

    private void removeEldest(){

        Iterator<T> it = map.iterator();
        if(it.hasNext()){
            it.next();
            it.remove();
        }
    }


    public void clear(){
        map.clear();
    }


    @Override
    public String toString() {
        return map.toString();
    }
}
