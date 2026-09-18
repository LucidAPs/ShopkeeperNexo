package com.lucid.shopkeepernexo;

import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import com.nisovin.shopkeepers.api.events.UpdateItemEvent;
import com.nisovin.shopkeepers.api.util.UnmodifiableItemStack;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NexoItemUpdateListenerTest {
    private NexoItemService nexoItems;
    private SynchronizationService synchronization;
    private UpdatedItemFactory updatedItemFactory;
    private NexoItemUpdateListener listener;

    @BeforeEach
    void setUp() {
        nexoItems = mock(NexoItemService.class);
        synchronization = mock(SynchronizationService.class);
        updatedItemFactory = mock(UpdatedItemFactory.class);
        listener = new NexoItemUpdateListener(
                nexoItems,
                synchronization,
                mock(java.util.logging.Logger.class),
                updatedItemFactory
        );
    }

    @Test
    void ignoresVanillaItems() {
        TestItemEvent testEvent = mockedEvent();
        when(nexoItems.itemId(any(ItemStack.class))).thenReturn(null);

        listener.onShopkeeperItemUpdate(testEvent.event());

        verify(nexoItems, never()).update(any(ItemStack.class));
        verify(synchronization, never()).recordNexoItem();
        verify(testEvent.event(), never()).setItem(any(UnmodifiableItemStack.class));
    }

    @Test
    void leavesItemsWithMissingDefinitionsUntouched() {
        TestItemEvent testEvent = mockedEvent();
        when(nexoItems.itemId(any(ItemStack.class))).thenReturn("retired_gem");
        when(nexoItems.definitionExists("retired_gem")).thenReturn(false);

        listener.onShopkeeperItemUpdate(testEvent.event());

        verify(synchronization).recordNexoItem();
        verify(synchronization).recordMissingDefinition();
        verify(nexoItems, never()).update(any(ItemStack.class));
        verify(testEvent.event(), never()).setItem(any(UnmodifiableItemStack.class));
    }

    @Test
    void doesNotReplaceAnAlreadyCurrentItem() {
        TestItemEvent testEvent = mockedEvent();
        when(nexoItems.itemId(any(ItemStack.class))).thenReturn("gem");
        when(nexoItems.definitionExists("gem")).thenReturn(true);
        when(nexoItems.update(testEvent.candidate())).thenReturn(testEvent.original());

        listener.onShopkeeperItemUpdate(testEvent.event());

        verify(synchronization).recordNexoItem();
        verify(synchronization, never()).recordUpdatedItem();
        verify(testEvent.event(), never()).setItem(any(UnmodifiableItemStack.class));
    }

    @Test
    void replacesAStaleNexoItem() {
        TestItemEvent testEvent = mockedEvent();
        ItemStack updated = mock(ItemStack.class);
        UnmodifiableItemStack wrappedUpdated = mock(UnmodifiableItemStack.class);
        when(nexoItems.itemId(any(ItemStack.class))).thenReturn("gem");
        when(nexoItems.definitionExists("gem")).thenReturn(true);
        when(nexoItems.update(testEvent.candidate())).thenReturn(updated);
        when(updatedItemFactory.wrap(updated)).thenReturn(wrappedUpdated);

        listener.onShopkeeperItemUpdate(testEvent.event());

        verify(synchronization).recordNexoItem();
        verify(synchronization).recordUpdatedItem();
        verify(testEvent.event()).setItem(wrappedUpdated);
    }

    @Test
    void isolatesUpdaterFailures() {
        TestItemEvent testEvent = mockedEvent();
        when(nexoItems.itemId(any(ItemStack.class))).thenReturn("broken_gem");
        when(nexoItems.definitionExists("broken_gem")).thenReturn(true);
        when(nexoItems.update(any(ItemStack.class))).thenThrow(new IllegalStateException("broken"));

        listener.onShopkeeperItemUpdate(testEvent.event());

        verify(synchronization).recordFailure();
        verify(testEvent.event(), never()).setItem(any(UnmodifiableItemStack.class));
    }

    @Test
    void queuesSynchronizationWhenNexoFinishesLoading() {
        listener.onNexoItemsLoaded(new NexoItemsLoadedEvent());

        verify(synchronization).requestSync(eq(SyncTrigger.NEXO_ITEMS_LOADED), isNull());
    }

    private static TestItemEvent mockedEvent() {
        UpdateItemEvent event = mock(UpdateItemEvent.class);
        UnmodifiableItemStack eventItem = mock(UnmodifiableItemStack.class);
        ItemStack original = mock(ItemStack.class);
        ItemStack candidate = mock(ItemStack.class);
        when(event.getItem()).thenReturn(eventItem);
        when(eventItem.copy()).thenReturn(original);
        when(original.clone()).thenReturn(candidate);
        return new TestItemEvent(event, original, candidate);
    }

    private record TestItemEvent(UpdateItemEvent event, ItemStack original, ItemStack candidate) {
    }
}
