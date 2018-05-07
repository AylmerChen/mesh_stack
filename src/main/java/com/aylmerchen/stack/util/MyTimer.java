package com.aylmerchen.stack.util;

/**
 * Created by Lasern on 2018/1/17.
 */

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 自定义的定时器类，线程可休眠，节省资源，适合需要重复开启和关闭的场合
 */
public class MyTimer {

    public static final String TAG = MyTimer.class.getSimpleName();

    private MyTimerImpl myTimerImpl;

    public void initTimer() {
        myTimerImpl = new MyTimerImpl();
        myTimerImpl.start();
    }

    public void startTimer(Runnable task, int delay) {
        if (myTimerImpl != null) {
            myTimerImpl.startTimer(task, delay);
        }
    }

    public void pauseTimer() {
        if (myTimerImpl != null) {
            myTimerImpl.pauseTimer();
        }
    }

    public void resumeTimer() {
        if (myTimerImpl != null) {
            myTimerImpl.resumeTimer();
        }
    }

    public void resetTimer() {
        if (myTimerImpl != null) {
            myTimerImpl.resetTimer();
        }
    }

    public void cancelTimer() {
        if (myTimerImpl != null) {
            myTimerImpl.cancelTimer();
        }
    }

    private static final class MyTimerImpl extends Thread {

        private static final int TIME_TICK = 10; // 计时时，默认单位时间步进为 10ms
        private int timeTick; //计时时的单位时间步进

        private AtomicBoolean quit = new AtomicBoolean(false);
        private AtomicBoolean isTimerStart = new AtomicBoolean(false);

        private volatile long currentTimeCounter = 0;
        private volatile long triggerTime = 0;

        private Runnable task;

        private final Object threadLock = new Object();

        private MyTimerImpl() {
            super();
            timeTick = TIME_TICK;
        }

        private MyTimerImpl(int timeTick){
            super();
            this.timeTick = timeTick;
        }

        @Override
        public void run() {
            super.run();

            while ( !quit.get() ) {

                if ( !isTimerStart.get() ) {
                    pauseThread();

                } else {

                    threadSleep(timeTick);
                    currentTimeCounter++;

                    // 延时时间到，执行外部任务
                    if (currentTimeCounter >= triggerTime && triggerTime > 0) {
                        if (task != null) {
                            task.run();
                        }
                        resetTimer(); // 执行完延时任务则回到睡眠模式
                    }
                }
            }
        }

        // delay 延时单位时间是毫秒
        private void startTimer(Runnable outerTask, int delay) {
            this.isTimerStart.set(true);
            this.task = outerTask;
            this.triggerTime = currentTimeCounter + delay / TIME_TICK;  // 任务触法时间为 当前时间加上延时时间
            invokeThread();
        }

        private void pauseTimer() {
            if (isTimerStart.get()) {
                this.isTimerStart.set(false);
            }
        }

        private void resumeTimer() {
            if ( !isTimerStart.get() ) {
                invokeThread();
            }
        }

        private void resetTimer() {
            this.isTimerStart.set(false);
            this.triggerTime = 0;
            this.currentTimeCounter = 0;
            task = null;
        }

        private void cancelTimer() {
            quit.set(true);
        }

        private void threadSleep(int ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void pauseThread() {
            synchronized (threadLock){
                while( !isTimerStart.get() ){
                    try {
                        threadLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void invokeThread() {
            synchronized (threadLock) {
                isTimerStart.set(true);
                threadLock.notifyAll();
            }
        }
    }

}
