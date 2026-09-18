package com.lucid.shopkeepernexo;

import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import com.nisovin.shopkeepers.api.events.UpdateItemEvent;
import com.nisovin.shopkeepers.api.util.UnmodifiableItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;
import java.util.logging.Logger;

final class NexoItemUpdateListener implements Listener {
    private final NexoItemService nexoItems;
    private final SynchronizationService synchronization;
    private final Logger logger;
    private final UpdatedItemFactory updatedItemFactory;

    NexoItemUpdateListener(
            NexoItemService nexoItems,
            SynchronizationService synchronization,
            Logger logger
    ) {
        this(nexoItems, synchronization, logger, UnmodifiableItemStack::ofNonNull);
    }

    NexoItemUpdateListener(
            NexoItemService nexoItems,
            SynchronizationService synchronization,
            Logger logger,
            UpdatedItemFactory updatedItemFactory
    ) {
        this.nexoItems = nexoItems;
        this.synchronization = synchronization;
        this.logger = logger;
        this.updatedItemFactory = updatedItemFactory;
    }

    @EventHandler
    public void onNexoItemsLoaded(NexoItemsLoadedEvent event) {
        synchronization.requestSync(SyncTrigger.NEXO_ITEMS_LOADED, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShopkeeperItemUpdate(UpdateItemEvent event) {
        ItemStack original = event.getItem().copy();
        String itemId = null;

        try {
            itemId = nexoItems.itemId(original);
            if (itemId == null) {
                return;
            }

            synchronization.recordNexoItem();
            if (!nexoItems.definitionExists(itemId)) {
                synchronization.recordMissingDefinition();
                return;
            }

            ItemStack updated = nexoItems.update(original.clone());
            if (updated == null) {
                throw new IllegalStateException("Nexo returned a null updated item");
            }

            if (!original.equals(updated)) {
                event.setItem(updatedItemFactory.wrap(updated));
                synchronization.recordUpdatedItem();
            }
        } catch (RuntimeException exception) {
            synchronization.recordFailure();
            String itemDescription = itemId == null ? original.getType().toString() : itemId;
            logger.log(Level.WARNING, "Failed to update Nexo item '" + itemDescription + "'.", exception);
        }
    }
}

@FunctionalInterface
interface UpdatedItemFactory {
    UnmodifiableItemStack wrap(ItemStack item);
}
