package com.lucid.shopkeepernexo;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

final class ShopkeeperNexoCommand implements CommandExecutor, TabCompleter {
    static final String ADMIN_PERMISSION = "shopkeepernexo.admin";
    private static final String PREFIX = "[ShopkeeperNexo] ";
    private static final List<String> SUBCOMMANDS = List.of("sync", "status");

    private final SynchronizationService synchronization;
    private final NexoItemService nexoItems;
    private final Supplier<String> dependencyStatus;

    ShopkeeperNexoCommand(
            SynchronizationService synchronization,
            NexoItemService nexoItems,
            Supplier<String> dependencyStatus
    ) {
        this.synchronization = synchronization;
        this.nexoItems = nexoItems;
        this.dependencyStatus = dependencyStatus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(PREFIX + "You do not have permission to use this command.");
            return true;
        }

        if (args.length != 1) {
            sendUsage(sender, label);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sync" -> {
                synchronization.requestSync(
                        SyncTrigger.MANUAL,
                        snapshot -> sender.sendMessage(PREFIX + snapshot.summary())
                );
                sender.sendMessage(PREFIX + "Synchronization queued for the next server tick.");
                yield true;
            }
            case "status" -> {
                sendStatus(sender);
                yield true;
            }
            default -> {
                sendUsage(sender, label);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION) || args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream()
                .filter(subcommand -> subcommand.startsWith(prefix))
                .toList();
    }

    private void sendStatus(CommandSender sender) {
        String state;
        if (synchronization.isRunning()) {
            state = "running";
        } else if (synchronization.isQueued()) {
            state = "queued";
        } else {
            state = "idle";
        }

        sender.sendMessage(PREFIX + dependencyStatus.get());
        sender.sendMessage(PREFIX + "State: " + state
                + "; registered Nexo definitions: " + nexoItems.registeredItemCount() + ".");

        SyncSnapshot snapshot = synchronization.lastSnapshot();
        if (snapshot == null) {
            sender.sendMessage(PREFIX + "No synchronization has completed since this plugin was enabled.");
            return;
        }

        sender.sendMessage(PREFIX + "Last run: " + snapshot.trigger().description()
                + " at " + snapshot.completedAt() + ".");
        String shopkeepersSummary = snapshot.successful()
                ? " Shopkeepers reported " + snapshot.shopkeepersReportedUpdates() + " total changed item(s)."
                : "";
        sender.sendMessage(PREFIX + snapshot.summary() + shopkeepersSummary);
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(PREFIX + "Usage: /" + label + " <sync|status>");
    }
}
