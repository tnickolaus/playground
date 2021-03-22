/*
 * Copyright (c) 2021 Informationstechnikzentrum Bund. All rights reserved.
 */
package de.tomtest.basic.concurrency;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.IntStream;

import junit.framework.TestCase;

/**
 * https://winterbe.com/posts/2015/05/22/java8-concurrency-tutorial-atomic-concurrent-map-examples/
 *
 * @author tnickolaus
 */
public class SynchronizedTest extends TestCase {

    int count = 0;

    private void increment() {

        this.count = this.count + 1;
    }

    private synchronized void incrementSync() {

        this.count = this.count + 1;
    }

    private void incrementSync2() {

        synchronized (this) {
            this.count = this.count + 1;
        }
    }

    private void stop(final ExecutorService executor) {

        ExecutorHelper.shutdown(executor, 10);
    }

    private void sleep(final int seconds) {

        try {
            Thread.sleep(seconds * 1000L);
        } catch (Exception e) {
            // ignore
        }
    }

    public void testWithoutSynchronized() {

        System.out.println("testWithoutSynchronized() ...");

        this.count = 0;

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Instant start = Instant.now();
        IntStream.range(0, 1000000).forEach(i -> executor.submit(this::increment));
        Instant stop = Instant.now();

        stop(executor);

        System.out.printf("Count = %d, time = %d\n", this.count, Duration.between(start, stop).toMillis()); // 9997
    }

    public void testSynchronized() {

        System.out.println("testSynchronized() ...");

        this.count = 0;

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Instant start = Instant.now();
        IntStream.range(0, 1000000).forEach(i -> executor.submit(this::incrementSync));
        Instant stop = Instant.now();

        stop(executor);

        System.out.printf("Count = %d, time = %d\n", this.count, Duration.between(start, stop).toMillis()); // 100000
    }

    public void testLock() {

        System.out.println("testLock() ...");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        ReentrantLock lock = new ReentrantLock();

        executor.submit(() -> {
            lock.lock();
            try {
                sleep(1);
            } finally {
                lock.unlock();
            }
        });

        executor.submit(() -> {
            System.out.println("Locked: " + lock.isLocked());
            System.out.println("Held by me: " + lock.isHeldByCurrentThread());
            boolean locked = lock.tryLock();
            System.out.println("Lock acquired: " + locked);
        });

        stop(executor);
    }

    public void testReadWriteLock() {

        System.out.println("testReadWriteLock() ...");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Map<String, String> map = new HashMap<>();
        ReadWriteLock lock = new ReentrantReadWriteLock();

        executor.submit(() -> {
            lock.writeLock().lock();
            try {
                sleep(1);
                map.put("foo", "bar");
            } finally {
                lock.writeLock().unlock();
            }
        });

        Runnable readTask = () -> {
            lock.readLock().lock();
            try {
                System.out.println(map.get("foo"));
                sleep(1);
            } finally {
                lock.readLock().unlock();
            }
        };

        executor.submit(readTask);
        executor.submit(readTask);

        stop(executor);
    }

    public void testStampedLock() {

        System.out.println("testStampedLock() ...");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Map<String, String> map = new HashMap<>();
        StampedLock lock = new StampedLock();

        executor.submit(() -> {
            long stamp = lock.writeLock();
            try {
                sleep(1);
                map.put("foo", "bar");
            } finally {
                lock.unlockWrite(stamp);
            }
        });

        Runnable readTask = () -> {
            long stamp = lock.readLock();
            try {
                System.out.println(map.get("foo"));
                sleep(1);
            } finally {
                lock.unlockRead(stamp);
            }
        };

        executor.submit(readTask);
        executor.submit(readTask);

        stop(executor);
    }

    public void testOptimisticLock() {

        System.out.println("testOptimisticLock() ...");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        StampedLock lock = new StampedLock();

        executor.submit(() -> {
            long stamp = lock.tryOptimisticRead();
            try {
                System.out.println("Optimistic Lock Valid: " + lock.validate(stamp));
                sleep(1);
                System.out.println("Optimistic Lock Valid: " + lock.validate(stamp));
                sleep(2);
                System.out.println("Optimistic Lock Valid: " + lock.validate(stamp));
            } finally {
                lock.unlock(stamp);
            }
        });

        executor.submit(() -> {
            long stamp = lock.writeLock();
            try {
                System.out.println("Write Lock acquired");
                sleep(2);
            } finally {
                lock.unlock(stamp);
                System.out.println("Write done");
            }
        });

        stop(executor);
    }

    public void testSemaphore() {

        System.out.println("testSemaphore() ...");

        ExecutorService executor = Executors.newFixedThreadPool(10);

        Semaphore semaphore = new Semaphore(5);

        Runnable longRunningTask = () -> {
            boolean permit = false;
            try {
                permit = semaphore.tryAcquire(1, TimeUnit.SECONDS);
                if (permit) {
                    System.out.println("Semaphore acquired");
                    sleep(5);
                } else {
                    System.out.println("Could not acquire semaphore");
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            } finally {
                if (permit) {
                    semaphore.release();
                }
            }
        };

        IntStream.range(0, 10).forEach(i -> executor.submit(longRunningTask));

        stop(executor);
    }
}
