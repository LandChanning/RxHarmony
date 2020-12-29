/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lcn.rxharmony.schedulers;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import ohos.eventhandler.EventHandler;
import ohos.eventhandler.EventRunner;
import ohos.eventhandler.InnerEvent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class HandlerScheduler extends Scheduler {

    private final RunnableObjHandler handler;
    private final boolean async;

    HandlerScheduler(RunnableObjHandler handler, boolean async) {
        this.handler = handler;
        this.async = async;
    }

    @Override
//        @SuppressLint("NewApi") // Async will only be true when the API is available to call.
    public Disposable scheduleDirect(Runnable run, long delay, TimeUnit unit) {
        if (run == null) throw new NullPointerException("run == null");
        if (unit == null) throw new NullPointerException("unit == null");

        run = RxJavaPlugins.onSchedule(run);
        ScheduledRunnable scheduled = new ScheduledRunnable(handler, run);
//        Message message = Message.obtain(handler, scheduled);
//        if (async) {
//            message.setAsynchronous(true);
//        }
//        handler.sendMessageDelayed(message, unit.toMillis(delay));
        InnerEvent message = InnerEvent.get(handler.getInnerEventId());
        message.object = scheduled;
        handler.sendEvent(message, unit.toMillis(delay));
        return scheduled;
    }

    @Override
    public Worker createWorker() {
        return new HandlerWorker(handler, async);
    }

    private static final class HandlerWorker extends Worker {
        private final RunnableObjHandler handler;
        private final boolean async;

        private volatile boolean disposed;

        private static AtomicLong count = new AtomicLong();
        // 每次新建一个 Worker 都定义 param 参数，用于移除该 Worker.dispose() 方法中移除所有该类创建的任务
        private final long paramForDispose;

        HandlerWorker(RunnableObjHandler handler, boolean async) {
            this.handler = handler;
            this.async = async;
            paramForDispose = count.incrementAndGet();
        }

        @Override
//        @SuppressLint("NewApi") // Async will only be true when the API is available to call.
        public Disposable schedule(Runnable run, long delay, TimeUnit unit) {
            if (run == null) throw new NullPointerException("run == null");
            if (unit == null) throw new NullPointerException("unit == null");

            if (disposed) {
                return Disposable.disposed();
            }

            run = RxJavaPlugins.onSchedule(run);

            ScheduledRunnable scheduled = new ScheduledRunnable(handler, run);

//            Message message = Message.obtain(handler, scheduled);
//            message.obj = this; // Used as token for batch disposal of this worker's runnables.
//
//            if (async) {
//                message.setAsynchronous(true);
//            }
//
//            handler.sendMessageDelayed(message, unit.toMillis(delay));
//
//            // Re-check disposed state for removing in case we were racing a call to dispose().
//            if (disposed) {
//                handler.removeCallbacks(scheduled);
//                return Disposable.disposed();
//            }

            // 因为目前鸿蒙的 InnerEvent 无法通过 Runnable 参数获取，所以各种 dispose 逻辑就无法按 Android 的那套实现。
            // 目前的解决方案是将 Runnable 传入 InnerEvent.object，然后自定义 Handler 拿到事件调用 object 参数的 run 方法，同时设置 param 参数（该 Worker 实例化定义的固定值），
            // 这样 Dispose 时，可根据 object 移除单任务，也可通过 param 移除该 Worker 创建的所以任务
            InnerEvent message = InnerEvent.get(handler.getInnerEventId());
            message.object = scheduled;
            message.param = paramForDispose;
            handler.sendEvent(message, unit.toMillis(delay));

            // Re-check disposed state for removing in case we were racing a call to dispose().
            if (disposed) {
                // 移除 obj 是该 scheduled 的任务，InnerEventId 必须指定，所以在自定义 Handler 中提供
                handler.removeEvent(handler.getInnerEventId(), scheduled);
                return Disposable.disposed();
            }

            return scheduled;
        }

        @Override
        public void dispose() {
            disposed = true;
//            handler.removeCallbacksAndMessages(this /* token */);
            handler.removeEvent(handler.getInnerEventId(), paramForDispose);
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    private static final class ScheduledRunnable implements Runnable, Disposable {
        private final RunnableObjHandler handler;
        private final Runnable delegate;

        private volatile boolean disposed; // Tracked solely for isDisposed().

        ScheduledRunnable(RunnableObjHandler handler, Runnable delegate) {
            this.handler = handler;
            this.delegate = delegate;
        }

        @Override
        public void run() {
            try {
                delegate.run();
            } catch (Throwable t) {
                RxJavaPlugins.onError(t);
            }
        }

        @Override
        public void dispose() {
//            handler.removeCallbacks(this);
            handler.removeEvent(handler.getInnerEventId(), this);
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    static class RunnableObjHandler extends EventHandler {

        private static AtomicInteger count = new AtomicInteger();
        private final int innerEventId;

        public RunnableObjHandler(EventRunner runner) throws IllegalArgumentException {
            super(runner);
            innerEventId = HandlerScheduler.class.hashCode() + count.incrementAndGet();
        }

        private int getInnerEventId() {
            return innerEventId;
        }

        @Override
        protected void processEvent(InnerEvent event) {
            super.processEvent(event);
            if (event.eventId != innerEventId) return;
            Object obj = event.object;
            if (obj instanceof Runnable) {
                ((Runnable)obj).run();
            }
        }
    }
}
