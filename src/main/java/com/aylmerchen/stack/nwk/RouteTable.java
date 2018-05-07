package com.aylmerchen.stack.nwk;


import android.util.LongSparseArray;

import java.util.ArrayList;
import java.util.List;

/**
 * 路由表
 *
 * @author Lasern
 * @date 2018/3/21
 */

public class RouteTable {

    /**
     * 具体路由表，
     * 键：目的地址
     * 值：到达该目的地址的所有路由
     */
    private LongSparseArray<List<SingleRoute>> routeTable;

    public RouteTable() {
        this.routeTable = new LongSparseArray<>();
    }

    /**
     * 新增加一条路由记录
     * @param destAddress 目的地址
     * @param nextAddress 下一跳地址
     * @param rip 新的跳数
     */
    public void updateRoute(long destAddress, long nextAddress, int rip) {

        if (contains(destAddress)) {

            // 若已存在记录则更新
            List<SingleRoute> routeList = routeTable.get(destAddress);
            for (SingleRoute temp : routeList) {

                // 如果存在对应的下一跳地址记录且跳数更小，则更新跳数
                if(temp.getNextAddress() == nextAddress){
                    if(temp.getRip() > rip){
                        temp.updateRip(rip);
                        temp.updateTime(System.currentTimeMillis());
                    }
                    break;
                }
            }

        } else {

            SingleRoute newRoute = new SingleRoute(destAddress, nextAddress, rip, System.currentTimeMillis());
            List<SingleRoute> routeList = new ArrayList<>(5);
            routeList.add(newRoute);
            routeTable.put(destAddress, routeList);
        }
    }

    /**
     * 是否包含指定目的地址的路由记录
     * @param destAddress 目的地址
     * @return 是否存在该记录
     */
    public boolean contains(long destAddress) {
        return routeTable.get(destAddress) != null;
    }

    /**
     * 删除指定目的地址的全部路由记录
     *
     * @param destAddress 目的地址
     */
    public void removeRoute(long destAddress){
        routeTable.remove(destAddress);
    }

    /**
     * 删除指定目的地址和下一跳地址的某条路由记录
     *
     * @param destAddress 目的地址
     * @param nextAddress 下一跳地址
     */
    public void removeRoute(long destAddress, long nextAddress){
        routeTable.remove(destAddress);
    }



    /**
     * 路由表单条记录
     */
    private static final class SingleRoute{

        /**
         * 目的地址
         */
        private long destAddress;

        /**
         * 下一跳地址
         */
        private long nextAddress;

        /**
         * 所需跳数
         */
        private int rip;

        /**
         * 上一次的路由更新时间
         */
        private long time;

        public SingleRoute(long destAddress, long nextAddress, int rip, long time) {
            this.destAddress = destAddress;
            this.nextAddress = nextAddress;
            this.rip = rip;
            this.time = time;
        }

        public long getDestAddress() {
            return destAddress;
        }

        public long getNextAddress() {
            return nextAddress;
        }

        public int getRip() {
            return rip;
        }

        public void updateRip(int rip) {
            this.rip = rip;
        }

        public long getTime() {
            return time;
        }

        public void updateTime(long time){
            this.time = time;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SingleRoute && destAddress == ((SingleRoute) obj).destAddress && nextAddress == ((SingleRoute) obj).nextAddress;
        }
    }
}
