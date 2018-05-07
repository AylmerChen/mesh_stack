package com.aylmerchen.stack.nwk;

import android.util.LongSparseArray;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * 相邻表
 *
 * @author Lasern
 * @date 2018/3/22
 */

public class NearTable {

   /**
    * 临近节点更新超时标准，单位 ms,即临近节点的记录超过该时间没有更新则应删除该条记录
    */
   private final long TIME_OUT;

   /**
    * key:临近节点地址 value:更新时间
    */
   private LongSparseArray<Long> nearTable;

   public NearTable(){
       this.TIME_OUT = 5 * 60 * 1000;
   }

   public NearTable(int timeOut) {
       this.TIME_OUT = timeOut;
       this.nearTable = new LongSparseArray<>();
   }


   /**
    * 更新相邻表，有则更新，无则添加
    * @param address 待更新的地址
    */
   public void updateNeighbour(long address) {
       nearTable.put(address, System.currentTimeMillis());
   }


   /**
    * 供外部定时线程调用，定时对相邻表进行检查
    * @param checkTime 检查时刻
    */
   public void checkNeighbour(long checkTime) {

       for(int i = 0; i < nearTable.size(); i++){
           long time = nearTable.valueAt(i);
           if (checkTime - time >= TIME_OUT) {
               nearTable.removeAt(i);
           }
       }
   }

   /**
    * 查看相邻表中是否有指定的地址
    * @param address 待查询地址
    * @return true/false
    */
   public boolean contains(long address) {
       return nearTable.get(address) != null;
   }


   @Override
   public String toString() {
       final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss:SSS", Locale.CHINA);

       StringBuilder temp = new StringBuilder();

       for(int i = 0; i < nearTable.size(); i++){
           long address = nearTable.keyAt(i);
           long time = nearTable.get(address);
           temp.append("Address:").append(address).append(" ,time:").append(sdf.format(time)).append("\n");
       }
       return temp.toString();
   }
}
