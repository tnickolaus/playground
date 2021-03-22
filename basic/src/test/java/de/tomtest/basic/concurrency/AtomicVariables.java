package de.tomtest.basic.concurrency;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongBinaryOperator;
import java.util.stream.IntStream;

import junit.framework.TestCase;

/**
 * https://winterbe.com/posts/2015/05/22/java8-concurrency-tutorial-atomic-concurrent-map-examples/
 *
 * @author tnickolaus
 */
public class AtomicVariables extends TestCase {

    public void testAtomicInteger() {

        AtomicInteger atomicInt = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        IntStream.range(0, 1000).forEach(i -> executor.submit(atomicInt::incrementAndGet));

        ExecutorHelper.shutdown(executor, 10);

        System.out.println("testAtomicInteger " + atomicInt.get());
    }

    public void testAtomicInteger2() {

        AtomicInteger atomicInt = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        IntStream.range(0, 1000).forEach(i -> {
            Runnable task = () -> atomicInt.updateAndGet(n -> n + 2);
            executor.submit(task);
        });

        ExecutorHelper.shutdown(executor, 10);

        System.out.println("testAtomicInteger2 " + atomicInt.get());
    }

    public void testAtomicInteger3() {

        AtomicInteger atomicInt = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        IntStream.range(0, 1000).forEach(i -> {
            executor.submit(() -> atomicInt.accumulateAndGet(i, (n, m) -> n + m));
        });

        ExecutorHelper.shutdown(executor, 10);

        System.out.println("testAtomicInteger3 " + atomicInt.get());
    }

    public void testLongAdder() {

        LongAdder adder = new LongAdder();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        IntStream.range(0, 1000).forEach(i -> executor.submit(adder::increment));

        stop(executor);

        System.out.println("testLongAdder " + adder.sumThenReset()); // => 1000
    }

    public void testLongAccumulator() {

        System.out.println("testLongAccumulator ...");

        LongBinaryOperator op = (x, y) -> {
            System.out.println("2 * " + x + " + " + y);
            return 2 * x + y;
        };
        LongAccumulator accumulator = new LongAccumulator(op, 1L);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        IntStream.range(0, 10).forEach(i -> executor.submit(() -> accumulator.accumulate(i)));

        stop(executor);

        System.out.println("testLongAccumulator " + accumulator.getThenReset()); // => 2539
    }

    public void testConcurrentMap() {

        System.out.println("testConcurrentMap ...");

        ConcurrentMap<String, String> map = new ConcurrentHashMap<>();
        map.put("foo", "bar");
        map.put("han", "solo");
        map.put("r2", "d2");
        map.put("c3", "p0");

        map.forEach((key, value) -> System.out.printf("%s = %s\n", key, value));

        String val = map.putIfAbsent("c3", "p1");
        System.out.println(val); // p0

        val = map.getOrDefault("hi", "there");
        System.out.println(val); // there

        map.replaceAll((key, value) -> "r2".equals(key) ? "d3" : value);
        System.out.println(map.get("r2")); // d3

        map.compute("foo", (key, value) -> value + value);
        System.out.println(map.get("foo")); // barbar

        map.merge("foo", "boo", (oldVal, newVal) -> newVal + " was " + oldVal);
        System.out.println(map.get("foo")); // boo was foo
    }

    public void testConcurrentHashMap() {

        System.out.println("testConcurrentHashMap ...");

        ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
        map.put("foo", "bar");
        map.put("han", "solo");
        map.put("r2", "d2");
        map.put("c3", "p0");

        System.out.println("ForkJoinPool.getCommonPoolParallelism() = " + ForkJoinPool.getCommonPoolParallelism()); // 3

        map.forEach(1, (key, value) -> System.out.printf("key: %s; value: %s; thread: %s\n", key, value,
                Thread.currentThread().getName()));

        // key: r2; value: d2; thread: main
        // key: foo; value: bar; thread: ForkJoinPool.commonPool-worker-1
        // key: han; value: solo; thread: ForkJoinPool.commonPool-worker-2
        // key: c3; value: p0; thread: main

        String result = map.search(1, (key, value) -> {
            System.out.println(Thread.currentThread().getName());
            if ("foo".equals(key)) {
                return value;
            }
            return null;
        });
        System.out.println("Result: " + result);

        // ForkJoinPool.commonPool-worker-2
        // main
        // ForkJoinPool.commonPool-worker-3
        // Result: bar

        result = map.searchValues(1, value -> {
            System.out.println(Thread.currentThread().getName());
            if (value.length() > 3) {
                return value;
            }
            return null;
        });

        System.out.println("Result: " + result);

        // ForkJoinPool.commonPool-worker-2
        // main
        // main
        // ForkJoinPool.commonPool-worker-1
        // Result: solo

        result = map.reduce(1, (key, value) -> {
            System.out.println("Transform: " + Thread.currentThread().getName());
            return key + "=" + value;
        }, (s1, s2) -> {
            System.out.println("Reduce: " + Thread.currentThread().getName());
            return s1 + ", " + s2;
        });

        System.out.println("Result: " + result);

        // Transform: ForkJoinPool.commonPool-worker-2
        // Transform: main
        // Transform: ForkJoinPool.commonPool-worker-3
        // Reduce: ForkJoinPool.commonPool-worker-3
        // Transform: main
        // Reduce: main
        // Reduce: main
        // Result: r2=d2, c3=p0, han=solo, foo=bar

    }

    private void stop(final ExecutorService executor) {

        ExecutorHelper.shutdown(executor, 10);
    }
}
