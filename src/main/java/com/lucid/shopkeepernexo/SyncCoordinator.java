package com.lucid.shopkeepernexo;

import com.nisovin.shopkeepers.api.ShopkeepersAPI;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class SyncCoordinator implements SynchronizationService {
    private final Logger logger;
    private final ShopkeepersBridge shopkeepers;
    private final NextTickScheduler scheduler;
    private final EnumSet<SyncTrigger> queuedTriggers = EnumSet.noneOf(SyncTrigger.class);
    private final List<Consumer<SyncSnapshot>> queuedCompletions = new ArrayList<>();

    private CancellableTask queuedTask;
    private MutableCounters activeCounters;
    private SyncSnapshot lastSnapshot;
    private boolean enabled = true;
    private boolean running;

    SyncCoordinator(Logger logger, ShopkeepersBridge shopkeepers, NextTickScheduler scheduler) {
        this.logger = logger;
        this.shopkeepers = shopkeepers;
        this.scheduler = scheduler;
    }

    @Override
    public synchronized void requestSync(SyncTrigger trigger, Consumer<SyncSnapshot> completion) {
        if (!enabled) {
            return;
        }

        queuedTriggers.add(trigger);
        if (completion != null) {
            queuedCompletions.add(completion);
        }

        if (!running && queuedTask == null) {
            scheduleQueuedRun();
        }
    }

    @Override
    public synchronized void recordNexoItem() {
        if (activeCounters != null) {
            activeCounters.nexoItems++;
        }
    }

    @Override
    public synchronized void recordUpdatedItem() {
        if (activeCounters != null) {
            activeCounters.updatedNexoItems++;
        }
    }

    @Override
    public synchronized void recordMissingDefinition() {
        if (activeCounters != null) {
            activeCounters.missingDefinitions++;
        }
    }

    @Override
    public synchronized void recordFailure() {
        if (activeCounters != null) {
            activeCounters.failures++;
        }
    }

    @Override
    public synchronized boolean isQueued() {
        return queuedTask != null || !queuedTriggers.isEmpty();
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }

    @Override
    public synchronized SyncSnapshot lastSnapshot() {
        return lastSnapshot;
    }

    synchronized void shutdown() {
        enabled = false;
        if (queuedTask != null) {
            queuedTask.cancel();
            queuedTask = null;
        }
        queuedTriggers.clear();
        queuedCompletions.clear();
    }

    private void scheduleQueuedRun() {
        queuedTask = scheduler.runNextTick(this::runQueuedSync);
    }

    private void runQueuedSync() {
        final SyncTrigger trigger;
        final List<Consumer<SyncSnapshot>> completions;

        synchronized (this) {
            queuedTask = null;
            if (!enabled || queuedTriggers.isEmpty()) {
                return;
            }

            trigger = selectTrigger(queuedTriggers);
            queuedTriggers.clear();
            completions = List.copyOf(queuedCompletions);
            queuedCompletions.clear();
            running = true;
            activeCounters = new MutableCounters();
        }

        long startedAt = System.nanoTime();
        int shopkeepersReportedUpdates = -1;
        boolean successful = false;
        String failureMessage = null;

        try {
            if (!shopkeepers.isEnabled()) {
                failureMessage = "Shopkeepers API is not enabled";
            } else {
                shopkeepersReportedUpdates = shopkeepers.updateItems();
                successful = true;
            }
        } catch (RuntimeException exception) {
            failureMessage = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            logger.log(Level.SEVERE, "Shopkeepers item synchronization failed.", exception);
        }

        final SyncSnapshot snapshot;
        synchronized (this) {
            long durationMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            MutableCounters counters = activeCounters;
            snapshot = new SyncSnapshot(
                    trigger,
                    Instant.now(),
                    durationMillis,
                    counters.nexoItems,
                    counters.updatedNexoItems,
                    counters.missingDefinitions,
                    counters.failures,
                    shopkeepersReportedUpdates,
                    successful,
                    failureMessage
            );
            activeCounters = null;
            running = false;
            lastSnapshot = snapshot;

            if (enabled && !queuedTriggers.isEmpty() && queuedTask == null) {
                scheduleQueuedRun();
            }
        }

        logSnapshot(snapshot);
        notifyCompletions(completions, snapshot);
    }

    private void logSnapshot(SyncSnapshot snapshot) {
        if (!snapshot.successful()) {
            logger.warning("Synchronization requested by " + snapshot.trigger().description()
                    + " failed: " + snapshot.failureMessage());
            return;
        }

        logger.info("Synchronization requested by " + snapshot.trigger().description()
                + " completed: " + snapshot.updatedNexoItems() + " Nexo item(s) updated, "
                + snapshot.nexoItems() + " inspected, "
                + snapshot.missingDefinitions() + " missing definition(s), "
                + snapshot.failures() + " item failure(s), "
                + snapshot.durationMillis() + " ms.");
    }

    private void notifyCompletions(List<Consumer<SyncSnapshot>> completions, SyncSnapshot snapshot) {
        for (Consumer<SyncSnapshot> completion : completions) {
            try {
                completion.accept(snapshot);
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "A synchronization completion callback failed.", exception);
            }
        }
    }

    private static SyncTrigger selectTrigger(EnumSet<SyncTrigger> triggers) {
        if (triggers.contains(SyncTrigger.MANUAL)) {
            return SyncTrigger.MANUAL;
        }
        if (triggers.contains(SyncTrigger.NEXO_ITEMS_LOADED)) {
            return SyncTrigger.NEXO_ITEMS_LOADED;
        }
        return SyncTrigger.STARTUP;
    }

    private static final class MutableCounters {
        private int nexoItems;
        private int updatedNexoItems;
        private int missingDefinitions;
        private int failures;
    }
}

interface ShopkeepersBridge {
    boolean isEnabled();

    int updateItems();
}

final class ShopkeepersApiBridge implements ShopkeepersBridge {
    @Override
    public boolean isEnabled() {
        return ShopkeepersAPI.isEnabled();
    }

    @Override
    public int updateItems() {
        return ShopkeepersAPI.updateItems();
    }
}

@FunctionalInterface
interface NextTickScheduler {
    CancellableTask runNextTick(Runnable task);
}

@FunctionalInterface
interface CancellableTask {
    void cancel();
}
