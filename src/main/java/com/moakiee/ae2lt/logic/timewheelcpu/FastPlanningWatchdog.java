package com.moakiee.ae2lt.logic.timewheelcpu;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Diagnostic watchdog for the fast crafting planner.
 * <p>
 * Each {@code runCraftAttempt} registers the calculating thread here for the duration of the attempt.
 * A single shared daemon thread ticks once a second and, for any attempt that has been running longer
 * than {@link #WARN_AFTER_MS}, logs at WARN:
 * <ul>
 *   <li>the requested output + amount,</li>
 *   <li>a live {@link FastPlanningStats} snapshot (branch / binary-search / fuzzy counters), and</li>
 *   <li>a full stack trace of the (otherwise unresponsive) calculating thread.</li>
 * </ul>
 * Comparing consecutive dumps tells us instantly whether we are stuck in a tight loop (identical stack,
 * counters frozen), or drowning in exponential re-simulation (counters racing upward) — without having
 * to guess. The watchdog only observes; it never changes the calculation (no interrupts), so the bug
 * being captured behaves exactly as it does for the user.
 * <p>
 * Thresholds are overridable via system properties for tuning during a session:
 * {@code -Dae2lt.fastcraft.watchdogMs} (first warn) and {@code -Dae2lt.fastcraft.watchdogRepeatMs}.
 */
public final class FastPlanningWatchdog {
    private static final Logger LOG = LoggerFactory.getLogger("ae2lt-fast-crafting");

    private static final long WARN_AFTER_MS = Long.getLong("ae2lt.fastcraft.watchdogMs", 4_000L);
    private static final long REPEAT_MS = Long.getLong("ae2lt.fastcraft.watchdogRepeatMs", 4_000L);

    private static final Map<Thread, Watch> ACTIVE = new ConcurrentHashMap<>();
    private static volatile ScheduledExecutorService exec;

    private FastPlanningWatchdog() {
    }

    public static void start(String label, FastPlanningStats stats) {
        ensureTicker();
        long now = System.currentTimeMillis();
        ACTIVE.put(Thread.currentThread(), new Watch(Thread.currentThread(), label, stats, now));
    }

    public static void stop() {
        ACTIVE.remove(Thread.currentThread());
    }

    private static void ensureTicker() {
        if (exec != null) {
            return;
        }
        synchronized (FastPlanningWatchdog.class) {
            if (exec != null) {
                return;
            }
            var service = Executors.newSingleThreadScheduledExecutor(runnable -> {
                var thread = new Thread(runnable, "ae2lt-fastcraft-watchdog");
                thread.setDaemon(true);
                return thread;
            });
            service.scheduleWithFixedDelay(FastPlanningWatchdog::tick, 1, 1, TimeUnit.SECONDS);
            exec = service;
        }
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        for (Watch watch : ACTIVE.values()) {
            long elapsed = now - watch.startMs;
            if (elapsed < WARN_AFTER_MS) {
                continue;
            }
            if (now - watch.lastReportMs < REPEAT_MS) {
                continue;
            }
            watch.lastReportMs = now;
            dump(watch, elapsed);
        }
    }

    private static void dump(Watch watch, long elapsed) {
        StackTraceElement[] stack = watch.thread.getStackTrace();
        var sb = new StringBuilder(512);
        sb.append("[ae2lt] SLOW crafting calc still running after ").append(elapsed).append("ms\n")
                .append("    ").append(watch.label).append('\n')
                .append("    stats: ").append(watch.stats.live()).append('\n')
                .append("    thread '").append(watch.thread.getName()).append("' stack (")
                .append(stack.length).append(" frames):\n");
        for (StackTraceElement element : stack) {
            sb.append("\tat ").append(element).append('\n');
        }
        LOG.warn(sb.toString());
    }

    private static final class Watch {
        final Thread thread;
        final String label;
        final FastPlanningStats stats;
        final long startMs;
        volatile long lastReportMs;

        Watch(Thread thread, String label, FastPlanningStats stats, long startMs) {
            this.thread = thread;
            this.label = label;
            this.stats = stats;
            this.startMs = startMs;
            this.lastReportMs = 0L;
        }
    }
}
