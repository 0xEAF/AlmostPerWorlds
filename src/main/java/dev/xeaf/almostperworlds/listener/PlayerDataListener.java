package dev.xeaf.almostperworlds.listener;

import dev.xeaf.almostperworlds.data.PlayerSnapshot;
import dev.xeaf.almostperworlds.group.GroupManager;
import dev.xeaf.almostperworlds.group.WorldGroup;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.UUID;

/**
 * Swaps a player's inventory (and related state) whenever they cross a world-group boundary.
 * <p>
 * Folia safety: every operation here only ever touches the single player who triggered the
 * event. File I/O is dispatched to the async scheduler (never touches game state, so it's
 * always safe off-thread); applying the loaded snapshot back onto the player is dispatched
 * through {@code player.getScheduler()}, which runs on whatever region currently owns that
 * player - exactly the pattern Folia expects for entity-scoped work. Nothing here ever reaches
 * into a second world or a second entity while holding onto the first, which is what made the
 * original plugin's world-wide sync features unsafe.
 */
public final class PlayerDataListener implements Listener {

    private final Plugin plugin;
    private final GroupManager groupManager;
    private final boolean syncGameMode;

    public PlayerDataListener(Plugin plugin, GroupManager groupManager, boolean syncGameMode) {
        this.plugin = plugin;
        this.groupManager = groupManager;
        this.syncGameMode = syncGameMode;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        var player = event.getPlayer();
        var fromGroup = groupManager.resolve(event.getFrom());
        var toGroup = groupManager.resolve(player.getWorld());

        if (fromGroup.name().equals(toGroup.name())) return; // same group, nothing to swap

        // Capture now, synchronously, on the thread the event fired on - this is the state
        // as it was in the world the player just left, before we touch anything.
        var outgoing = PlayerSnapshot.capture(player, syncGameMode);
        var uuid = player.getUniqueId();

        persistAndLoad(outgoing, uuid, fromGroup, toGroup, player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var group = groupManager.resolve(player.getWorld());
        var file = snapshotFile(group, player.getUniqueId());

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            var snapshot = PlayerSnapshot.load(file, syncGameMode);
            // Nothing to do if there's no stored data for this group AND no forced game mode.
            if (snapshot.isEmpty() && group.defaultGameMode().isEmpty()) return;

            player.getScheduler().run(plugin, scheduledTask -> {
                if (!player.isOnline()) return;
                snapshot.ifPresent(s -> s.apply(player));
                // Forced game mode always wins over whatever the snapshot (or lack of one) set.
                group.defaultGameMode().ifPresent(player::setGameMode);
            }, null);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var group = groupManager.resolve(player.getWorld());
        var snapshot = PlayerSnapshot.capture(player, syncGameMode);
        var file = snapshotFile(group, player.getUniqueId());

        Bukkit.getAsyncScheduler().runNow(plugin, task -> snapshot.save(file));
    }

    private void persistAndLoad(PlayerSnapshot outgoing, UUID uuid, WorldGroup fromGroup, WorldGroup toGroup, Player player) {
        var outgoingFile = snapshotFile(fromGroup, uuid);
        var incomingFile = snapshotFile(toGroup, uuid);

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            outgoing.save(outgoingFile);
            var incoming = PlayerSnapshot.load(incomingFile, syncGameMode);

            player.getScheduler().run(plugin, scheduledTask -> {
                if (!player.isOnline()) return;
                // No stored data yet for the destination group: clear so the player doesn't
                // carry the previous group's items into a group that's never seen them before.
                incoming.ifPresentOrElse(s -> s.apply(player), () -> clear(player));
                // Forced game mode always wins over whatever the snapshot (or clearing) set.
                toGroup.defaultGameMode().ifPresent(player::setGameMode);
            }, null);
        });
    }

    private void clear(Player player) {
        player.getInventory().clear();
        player.getEnderChest().clear();
    }

    private File snapshotFile(WorldGroup group, UUID uuid) {
        return new File(groupManager.dataFolder(group), uuid + ".yml");
    }
}
