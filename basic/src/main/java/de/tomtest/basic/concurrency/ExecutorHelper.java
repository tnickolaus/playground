/*
 * Copyright (c) 2021 Informationstechnikzentrum Bund. All rights reserved.
 */
package de.tomtest.basic.concurrency;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * TODO NicTh38-ext: Kurzbeschreibung der Klasse ...
 *
 * @author NicTh38-ext
 */
public class ExecutorHelper {

    public static void shutdown(final ExecutorService executor, final int seconds) {

        try {
            executor.shutdown();
            executor.awaitTermination(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("tasks interrupted");
        } finally {
            if (!executor.isTerminated()) {
                System.err.println("cancel non-finished tasks");
            }
            executor.shutdownNow();
        }
    }

}
