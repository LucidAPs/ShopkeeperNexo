package com.lucid.shopkeepernexo;

import com.nisovin.shopkeepers.api.ShopkeepersAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

public final class ShopkeeperNexoPlugin extends JavaPlugin {
    private SyncCoordinator synchronization;

    @Override
    public void onEnable() {
        if (!ShopkeepersAPI.isEnabled()) {
            getLogger().severe("Shopkeepers API is not enabled; disabling ShopkeeperNexo.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        NexoItemService nexoItems = new NexoApiItemService();
        synchronization = new SyncCoordinator(
                getLogger(),
                new ShopkeepersApiBridge(),
                task -> {
                    BukkitTask bukkitTask = getServer().getScheduler().runTask(this, task);
                    return bukkitTask::cancel;
                }
        );

        NexoItemUpdateListener listener = new NexoItemUpdateListener(
                nexoItems,
                synchronization,
                getLogger()
        );
        getServer().getPluginManager().registerEvents(listener, this);

        ShopkeeperNexoCommand commandHandler = new ShopkeeperNexoCommand(
                synchronization,
                nexoItems,
                this::dependencyStatus
        );
        PluginCommand command = Objects.requireNonNull(
                getCommand("shopkeepernexo"),
                "shopkeepernexo command is missing from plugin.yml"
        );
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        if (nexoItems.registeredItemCount() > 0) {
            synchronization.requestSync(SyncTrigger.STARTUP, null);
        } else {
            getLogger().info("Waiting for Nexo to finish loading its item definitions.");
        }
    }

    @Override
    public void onDisable() {
        if (synchronization != null) {
            synchronization.shutdown();
        }
    }

    private String dependencyStatus() {
        return "Version " + getPluginMeta().getVersion()
                + "; Nexo " + pluginVersion("Nexo")
                + "; Shopkeepers " + pluginVersion("Shopkeepers") + ".";
    }

    private String pluginVersion(String pluginName) {
        Plugin plugin = getServer().getPluginManager().getPlugin(pluginName);
        return plugin == null ? "missing" : plugin.getPluginMeta().getVersion();
    }
}
