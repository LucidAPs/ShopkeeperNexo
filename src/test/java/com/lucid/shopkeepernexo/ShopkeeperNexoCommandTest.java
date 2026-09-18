package com.lucid.shopkeepernexo;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopkeeperNexoCommandTest {
    private FakeSynchronizationService synchronization;
    private NexoItemService nexoItems;
    private ShopkeeperNexoCommand commandHandler;
    private Command command;
    private CommandSender sender;

    @BeforeEach
    void setUp() {
        synchronization = new FakeSynchronizationService();
        nexoItems = mock(NexoItemService.class);
        commandHandler = new ShopkeeperNexoCommand(
                synchronization,
                nexoItems,
                () -> "Version 1.0; Nexo 1.28.0; Shopkeepers 2.27.0."
        );
        command = mock(Command.class);
        sender = mock(CommandSender.class);
    }

    @Test
    void rejectsSendersWithoutPermission() {
        when(sender.hasPermission(ShopkeeperNexoCommand.ADMIN_PERMISSION)).thenReturn(false);

        assertTrue(commandHandler.onCommand(sender, command, "sknexo", new String[]{"sync"}));

        verify(sender).sendMessage(contains("do not have permission"));
        assertEquals(0, synchronization.requests);
    }

    @Test
    void queuesManualSyncAndReportsCompletion() {
        when(sender.hasPermission(ShopkeeperNexoCommand.ADMIN_PERMISSION)).thenReturn(true);

        assertTrue(commandHandler.onCommand(sender, command, "sknexo", new String[]{"sync"}));

        assertEquals(1, synchronization.requests);
        assertEquals(SyncTrigger.MANUAL, synchronization.requestedTrigger);
        verify(sender).sendMessage(contains("queued"));

        synchronization.complete(new SyncSnapshot(
                SyncTrigger.MANUAL,
                Instant.parse("2026-09-16T12:00:00Z"),
                8,
                3,
                2,
                0,
                0,
                2,
                true,
                null
        ));
        verify(sender).sendMessage(contains("2 Nexo item(s) updated"));
    }

    @Test
    void statusReportsDependenciesRegistryAndLastRun() {
        when(sender.hasPermission(ShopkeeperNexoCommand.ADMIN_PERMISSION)).thenReturn(true);
        when(nexoItems.registeredItemCount()).thenReturn(42);
        synchronization.lastSnapshot = new SyncSnapshot(
                SyncTrigger.STARTUP,
                Instant.parse("2026-09-16T12:00:00Z"),
                5,
                1,
                1,
                0,
                0,
                1,
                true,
                null
        );

        assertTrue(commandHandler.onCommand(sender, command, "shopkeepernexo", new String[]{"status"}));

        verify(sender).sendMessage(contains("Nexo 1.28.0"));
        verify(sender).sendMessage(contains("registered Nexo definitions: 42"));
        verify(sender).sendMessage(contains("Last run: startup"));
    }

    @Test
    void tabCompletionIsPermissionAwareAndPrefixFiltered() {
        when(sender.hasPermission(ShopkeeperNexoCommand.ADMIN_PERMISSION)).thenReturn(true);

        List<String> completions = commandHandler.onTabComplete(
                sender,
                command,
                "sknexo",
                new String[]{"st"}
        );

        assertEquals(List.of("status"), completions);

        when(sender.hasPermission(ShopkeeperNexoCommand.ADMIN_PERMISSION)).thenReturn(false);
        assertTrue(commandHandler.onTabComplete(
                sender,
                command,
                "sknexo",
                new String[]{""}
        ).isEmpty());
        verify(sender, never()).sendMessage(contains("Usage"));
    }

    private static final class FakeSynchronizationService implements SynchronizationService {
        private int requests;
        private SyncTrigger requestedTrigger;
        private Consumer<SyncSnapshot> completion;
        private SyncSnapshot lastSnapshot;

        @Override
        public void requestSync(SyncTrigger trigger, Consumer<SyncSnapshot> completion) {
            requests++;
            requestedTrigger = trigger;
            this.completion = completion;
        }

        @Override
        public void recordNexoItem() {
        }

        @Override
        public void recordUpdatedItem() {
        }

        @Override
        public void recordMissingDefinition() {
        }

        @Override
        public void recordFailure() {
        }

        @Override
        public boolean isQueued() {
            return false;
        }

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public SyncSnapshot lastSnapshot() {
            return lastSnapshot;
        }

        void complete(SyncSnapshot snapshot) {
            completion.accept(snapshot);
        }
    }
}
