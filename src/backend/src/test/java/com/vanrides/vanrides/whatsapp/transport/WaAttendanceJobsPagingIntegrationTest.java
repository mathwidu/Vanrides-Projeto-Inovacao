package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.attendance.WaAttendanceJobs;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaAttendanceJobsPagingIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_attendance_job_pages");
    private WaAttendanceCalendarTestSupport f;

    @BeforeEach
    void setup() {
        f = new WaAttendanceCalendarTestSupport(POSTGRES);
    }

    @Test
    void moreThanOneHundredMaterializationsUseDurableCursorAndReplay() {
        for (int i = 0; i < 17; i++) {
            f.member(f.routine, true);
        }
        var first = f.tick("MATERIALIZE_OCCURRENCES", 100, null);
        assertEquals(100, first.path("processed").asInt());
        assertTrue(first.path("nextCursor").asText().startsWith("jobcursor_"));
        long before = f.count("SELECT count(*) FROM wa.attendance");
        assertEquals(first, f.tick("MATERIALIZE_OCCURRENCES", 100, null));
        assertEquals(before, f.count("SELECT count(*) FROM wa.attendance"));
        var restarted = f.h.proxy(new WaAttendanceJobs(f.h.jdbc, f.h.access, f.h.settings, f.h.clock));
        var request = WaAttendanceCalendarTestSupport.json().put("jobType", "MATERIALIZE_OCCURRENCES")
                .put("windowKey", "2030-01-01T23:00:00Z").put("batchLimit", 100)
                .put("cursor", first.path("nextCursor").asText());
        var second = restarted.tick(f.job, request);
        assertEquals(26, second.path("processed").asInt());
        assertTrue(second.path("nextCursor").isNull());
        assertEquals(126, f.count("SELECT count(*) FROM wa.attendance"));
        assertEquals(126, f.count("SELECT count(*) FROM wa.attendance_question WHERE state='OPEN'"));
    }

    @Test
    void nextMinuteStartsWithUnfinishedWorkAfterProcessLosesItsCursor() {
        for (int i = 0; i < 17; i++) {
            f.member(f.routine, true);
        }
        assertEquals(100, f.tick("MATERIALIZE_OCCURRENCES", 100, null).path("processed").asInt());
        f.nextWindow();
        var next = f.tick("MATERIALIZE_OCCURRENCES", 100, null);
        assertEquals(26, next.path("processed").asInt());
        assertTrue(next.path("nextCursor").isNull());
        assertEquals(126, f.count("SELECT count(*) FROM wa.attendance"));
    }

    @Test
    void reminderBacklogLargerThanOneHundredAdvancesWithoutDuplicates() {
        for (int i = 0; i < 100; i++) {
            f.member(f.routine, true);
        }
        f.materialize();
        var first = f.tick("DUE_REMINDERS", 100, null);
        assertEquals(100, first.path("processed").asInt());
        assertFalse(first.path("nextCursor").isNull());
        var second = f.tick("DUE_REMINDERS", 100, first.path("nextCursor").asText());
        assertEquals(1, second.path("processed").asInt());
        assertTrue(second.path("nextCursor").isNull());
        assertEquals(101, f.count("SELECT count(*) FROM wa.message_outbox WHERE purpose='REMINDER'"));
        f.nextWindow();
        assertEquals(0, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
    }

    @Test
    void cursorIsBoundToJobTypeWindowAndInstance() {
        var first = f.tick("MATERIALIZE_OCCURRENCES", 1, null);
        String cursor = first.path("nextCursor").asText();
        var request = WaAttendanceCalendarTestSupport.json().put("jobType", "DUE_REMINDERS")
                .put("windowKey", "2030-01-01T23:00:00Z").put("batchLimit", 1).put("cursor", cursor);
        assertThrows(WaProblem.class, () -> f.jobs.tick(f.job, request));
        request.put("jobType", "MATERIALIZE_OCCURRENCES").put("windowKey", "2030-01-01T22:59:00Z");
        assertThrows(WaProblem.class, () -> f.jobs.tick(f.job, request));
        request.put("windowKey", "2030-01-01T23:00:00Z");
        var foreign = f.h.principal(WaPrincipal.Kind.SYSTEM_JOB, "another-instance");
        assertThrows(WaProblem.class, () -> f.jobs.tick(foreign, request));
    }

    @Test
    void concurrentTicksCommitOnePageAndOneSetOfEffects() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var one = pool.submit(() -> { start.await(); return f.tick("MATERIALIZE_OCCURRENCES", 100, null); });
            var two = pool.submit(() -> { start.await(); return f.tick("MATERIALIZE_OCCURRENCES", 100, null); });
            start.countDown();
            assertEquals(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(1, f.count("SELECT count(*) FROM wa.attendance_job_page"));
        assertEquals(7, f.count("SELECT count(*) FROM wa.attendance"));
    }

    @Test
    void expiredCursorCannotContinueAWindow() {
        var first = f.tick("MATERIALIZE_OCCURRENCES", 1, null);
        f.h.time.advance(301);
        var request = WaAttendanceCalendarTestSupport.json().put("jobType", "MATERIALIZE_OCCURRENCES")
                .put("windowKey", "2030-01-01T23:00:00Z").put("batchLimit", 1)
                .put("cursor", first.path("nextCursor").asText());
        long before = f.count("SELECT count(*) FROM wa.attendance");
        assertThrows(WaProblem.class, () -> f.jobs.tick(f.job, request));
        assertEquals(before, f.count("SELECT count(*) FROM wa.attendance"));
    }

    @Test
    void credentialExpiringWhileWaitingForRoutineRollsBackEveryEffect() throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>> pending = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            f.h.transaction.executeWithoutResult(status -> {
                f.h.jdbc.queryForList("SELECT id FROM wa.routine WHERE id=? FOR UPDATE", f.routine);
                pending.set(pool.submit(() -> f.tick("MATERIALIZE_OCCURRENCES", 100, null)));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (true) {
                    f.h.jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    if (f.count("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%wa.routine%' AND pid<>pg_backend_pid()") > 0) {
                        break;
                    }
                    if (pending.get().isDone()) {
                        try {
                            pending.get().get();
                            fail("Job completed without waiting for the held routine");
                        } catch (Exception failure) {
                            throw new AssertionError("Job failed before its lock barrier", failure);
                        }
                    }
                    assertTrue(System.nanoTime() < deadline, "Job did not wait on the routine lock");
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                }
                f.h.time.set(Instant.parse("2035-01-01T00:00:00Z"));
            });
            var error = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> pending.get().get(20, TimeUnit.SECONDS));
            assertInstanceOf(WaProblem.class, error.getCause());
            assertEquals(WaProblem.Code.INVALID_CREDENTIAL, ((WaProblem) error.getCause()).code());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(0, f.count("SELECT count(*) FROM wa.attendance_job_page"));
        assertEquals(0, f.count("SELECT count(*) FROM wa.attendance"));
        assertEquals(0, f.count("SELECT count(*) FROM wa.routine_occurrence"));
    }
}
