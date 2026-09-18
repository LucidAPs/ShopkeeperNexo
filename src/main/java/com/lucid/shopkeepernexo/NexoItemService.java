package com.lucid.shopkeepernexo;

import com.nexomc.nexo.api.NexoItems;
import org.bukkit.inventory.ItemStack;

interface NexoItemService {
    String itemId(ItemStack item);

    boolean definitionExists(String itemId);

    ItemStack update(ItemStack item);

    int registeredItemCount();
}

final class NexoApiItemService implements NexoItemService {
    @Override
    public String itemId(ItemStack item) {
        return NexoItems.idFromItem(item);
    }

    @Override
    public boolean definitionExists(String itemId) {
        return NexoItems.exists(itemId);
    }

    @Override
    public ItemStack update(ItemStack item) {
        return NexoItems.updateItem(item);
    }

    @Override
    public int registeredItemCount() {
        return NexoItems.itemNames().size();
    }
}
