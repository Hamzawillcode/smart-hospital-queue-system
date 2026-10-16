package com.hospital.queue.manager;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueManagerTest {

    private final QueueManager manager = new QueueManager();

    @Test
    void departmentLockGivesMutualExclusionWithinADepartment() throws Exception {
        int threads = 8;
        int iterations = 2_000;
        int[] counter = {0}; // deliberately not thread-safe
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        manager.withDepartmentLock("OPD", () -> counter[0]++);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(counter[0]).isEqualTo(threads * iterations);
    }

    @Test
    void differentDepartmentsDoNotBlockEachOther() throws Exception {
        CountDownLatch opdHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseOpd = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> opd = pool.submit(() -> manager.withDepartmentLock("OPD", () -> {
                opdHoldsLock.countDown();
                await(releaseOpd);
                return null;
            }));
            assertThat(opdHoldsLock.await(5, TimeUnit.SECONDS)).isTrue();

            // While OPD's lock is held, EMERGENCY must complete immediately.
            Future<String> emergency = pool.submit(() -> manager.withDepartmentLock("EMERGENCY", () -> "done"));
            assertThat(emergency.get(2, TimeUnit.SECONDS)).isEqualTo("done");

            releaseOpd.countDown();
            opd.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void lockIsReleasedWhenWorkThrows() {
        assertThatThrownBy(() -> manager.withDepartmentLock("OPD", () -> {
            throw new IllegalStateException("boom");
        })).hasMessage("boom");

        // A second acquisition from another thread must succeed.
        assertThat(java.util.concurrent.CompletableFuture
                .supplyAsync(() -> manager.withDepartmentLock("OPD", () -> "ok"))
                .orTimeout(2, TimeUnit.SECONDS)
                .join()).isEqualTo("ok");
    }

    @Test
    void installRequiresTheDepartmentLock() {
        DepartmentSnapshot snapshot = DepartmentSnapshot.of(1, List.of());

        assertThatThrownBy(() -> manager.install("OPD", snapshot)).isInstanceOf(IllegalStateException.class);

        manager.withDepartmentLock("OPD", () -> {
            manager.install("OPD", snapshot);
            return null;
        });
        assertThat(manager.snapshot("OPD").version()).isEqualTo(1);
    }

    @Test
    void snapshotsAreImmutable() {
        DepartmentSnapshot snapshot = DepartmentSnapshot.of(3, new java.util.ArrayList<>());
        assertThatThrownBy(() -> snapshot.waiting().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(manager.snapshot("UNKNOWN").isLoaded()).isFalse();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
