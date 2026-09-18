package com.lucid.shopkeepernexo;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncCoordinatorTest {
    @Test
    void coalescesRequestsAndReportsNexoSpecificMetrics() {
        CapturingScheduler scheduler = new CapturingScheduler();
        FakeShopkeepersBridge shopkeepers = new FakeShopkeepersBridge();
        SyncCoordinator coordinator = new SyncCoordinator(
                Logger.getLogger("SyncCoordinatorTest"),
                shopkeepers,
                scheduler
        );
        shopkeepers.duringUpdate = () -> {
            coordinator.recordNexoItem();
            coordinator.recordNexoItem();
            coordinator.recordUpdatedItem();
            coordinator.recordMissingDefinition();
            coordinator.recordFailure();
        };

        AtomicReference<SyncSnapshot> firstCompletion = new AtomicReference<>();
        AtomicReference<SyncSnapshot> secondCompletion = new AtomicReference<>();
        coordinator.requestSync(SyncTrigger.STARTUP, firstCompletion::set);
        coordinator.requestSync(SyncTrigger.MANUAL, secondCompletion::set);

        assertTrue(coordinator.isQueued());
        assertEquals(1, scheduler.scheduledCount);
        scheduler.runPending();

        SyncSnapshot snapshot = coordinator.lastSnapshot();
        assertNotNull(snapshot);
        assertSame(snapshot, firstCompletion.get());
        assertSame(snapshot, secondCompletion.get());
        assertEquals(SyncTrigger.MANUAL, snapshot.trigger());
        assertEquals(2, snapshot.nexoItems());
        assertEquals(1, snapshot.updatedNexoItems());
        assertEquals(1, snapshot.missingDefinitions());
        assertEquals(1, snapshot.failures());
        assertEquals(7, snapshot.shopkeepersReportedUpdates());
        assertTrue(snapshot.successful());
        assertEquals(1, shopkeepers.updateCalls);
        assertFalse(coordinator.isQueued());
        assertFalse(coordinator.isRunning());
    }

    @Test
    void reportsDisabledShopkeepersAsAFailedRun() {
        CapturingScheduler scheduler = new CapturingScheduler();
        FakeShopkeepersBridge shopkeepers = new FakeShopkeepersBridge();
        shopkeepers.enabled = false;
        SyncCoordinator coordinator = new SyncCoordinator(
                Logger.getLogger("SyncCoordinatorDisabledTest"),
                shopkeepers,
                scheduler
        );

        coordinator.requestSync(SyncTrigger.STARTUP, null);
        scheduler.runPending();

        SyncSnapshot snapshot = coordinator.lastSnapshot();
        assertNotNull(snapshot);
        assertFalse(snapshot.successful());
        assertEquals("Shopkeepers API is not enabled", snapshot.failureMessage());
        assertEquals(0, shopkeepers.updateCalls);
    }

    @Test
    void shutdownCancelsAPendingRun() {
        CapturingScheduler scheduler = new CapturingScheduler();
        SyncCoordinator coordinator = new SyncCoordinator(
                Logger.getLogger("SyncCoordinatorShutdownTest"),
                new FakeShopkeepersBridge(),
                scheduler
        );

        coordinator.requestSync(SyncTrigger.STARTUP, null);
        coordinator.shutdown();

        assertTrue(scheduler.cancelled);
        assertFalse(coordinator.isQueued());
        assertFalse(scheduler.hasPendingTask());
    }

    private static final class FakeShopkeepersBridge implements ShopkeepersBridge {
        private boolean enabled = true;
        private int updateCalls;
        private Runnable duringUpdate = () -> { };

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public int updateItems() {
            updateCalls++;
            duringUpdate.run();
            return 7;
        }
    }

    private static final class CapturingScheduler implements NextTickScheduler {
        private Runnable pendingTask;
        private int scheduledCount;
        private boolean cancelled;

        @Override
        public CancellableTask runNextTick(Runnable task) {
            pendingTask = task;
            scheduledCount++;
            return () -> {
                cancelled = true;
                pendingTask = null;
            };
        }

        void runPending() {
            Runnable task = pendingTask;
            pendingTask = null;
            task.run();
        }

        boolean hasPendingTask() {
            return pendingTask != null;
        }
    }
}
