/*
 * Copyright (C) 2013 Markus Junginger, greenrobot (http://greenrobot.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.greenrobot.event.test;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.os.Handler;
import android.os.Looper;

/**
 * @author Markus Junginger, greenrobot
 */
public class EventBusMainThreadRacingTest extends AbstractEventBusTest {

    private static final boolean REALTEST = false;
    private static final int ITERATIONS = REALTEST ? 100000 : 1000;

    protected boolean unregistered;
    private CountDownLatch startLatch;
    private volatile RuntimeException failed;

    public void testRacingThreads() throws InterruptedException {
        Runnable register = new Runnable() {
            @Override
            public void run() {
                eventBus.register(EventBusMainThreadRacingTest.this);
                unregistered = false;
            }
        };

        Runnable unregister = new Runnable() {
            @Override
            public void run() {
                eventBus.unregister(EventBusMainThreadRacingTest.this);
                unregistered = true;
            }
        };

        startLatch = new CountDownLatch(2);
        BackgroundPoster backgroundPoster = new BackgroundPoster();
        backgroundPoster.start();
        Handler handler = new Handler(Looper.getMainLooper());
        Random random = new Random();
        countDownAndAwaitLatch();
        for (int i = 0; i < ITERATIONS; i++) {
            handler.post(register);
            Thread.sleep(0, random.nextInt(300)); // Sleep just some nanoseconds, timing is crucial here
            handler.post(unregister);
            if (failed != null) {
                throw new RuntimeException("Failed in iteration " + i, failed);
            }
        }

        backgroundPoster.shutdown();
        backgroundPoster.join();
    }

    public void onEventMainThread(String event) {
        trackEvent(event);
        if (unregistered) {
            failed = new RuntimeException("Main thread event delivered while unregistered on received event #"
                    + eventCount);
        }
    }

    protected void countDownAndAwaitLatch() {
        startLatch.countDown();
        try {
            assertTrue(startLatch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    class BackgroundPoster extends Thread {
        private boolean running = true;

        public BackgroundPoster() {
            super("BackgroundPoster");
        }

        @Override
        public void run() {
            countDownAndAwaitLatch();
            while (running) {
                eventBus.post("Posted in background");
            }
        }

        void shutdown() {
            running = false;
        }

    }

}
