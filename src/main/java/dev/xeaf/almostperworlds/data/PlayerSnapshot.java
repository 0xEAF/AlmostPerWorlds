package dev.xeaf.almostperworlds.data;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A snapshot of everything about a player that "belongs" to a world group: their inventory,
 * ender chest, XP, food and health state, and (optionally) game mode.
 * <p>
 * Capturing and applying a snapshot only ever touches the single player it was made for, so
 * both operations are safe to run directly on that player's own region thread on Folia -
 * nothing here ever reaches across into another world's or another entity's state.
 */
public final class PlayerSnapshot {

    private ItemStack[] contents = new ItemStack[0];
    private ItemStack[] armor = new ItemStack[0];
    private ItemStack offHand = new ItemStack(Material.AIR);
    private ItemStack[] enderChest = new ItemStack[0];

    private int level;
    private float exp;
    private int foodLevel = 20;
    private float saturation = 5f;
    private float exhaustion;
    private double health = 20;
    private List<PotionEffect> potionEffects = List.of();
    private GameMode gameMode;

    private boolean syncGameMode;

    private PlayerSnapshot() {
    }

    public static PlayerSnapshot capture(Player player, boolean syncGameMode) {
        var snapshot = new PlayerSnapshot();
        var inventory = player.getInventory();

        snapshot.contents = inventory.getContents().clone();
        snapshot.armor = inventory.getArmorContents().clone();
        snapshot.offHand = inventory.getItemInOffHand().clone();
        snapshot.enderChest = player.getEnderChest().getContents().clone();

        snapshot.level = player.getLevel();
        snapshot.exp = player.getExp();
        snapshot.foodLevel = player.getFoodLevel();
        snapshot.saturation = player.getSaturation();
        snapshot.exhaustion = player.getExhaustion();

        var maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        var maxHealth = maxHealthAttribute != null ? maxHealthAttribute.getValue() : 20;
        snapshot.health = Math.min(player.getHealth(), maxHealth);

        snapshot.potionEffects = new ArrayList<>(player.getActivePotionEffects());
        snapshot.syncGameMode = syncGameMode;
        if (syncGameMode) snapshot.gameMode = player.getGameMode();

        return snapshot;
    }

    /**
     * Applies this snapshot to the given player. Must be called on the player's own
     * scheduler/thread (e.g. inside {@code player.getScheduler().run(...)} on Folia).
     */
    public void apply(Player player) {
        var inventory = player.getInventory();
        inventory.setContents(contents);
        inventory.setArmorContents(armor);
        inventory.setItemInOffHand(offHand);
        player.getEnderChest().setContents(enderChest);

        player.setLevel(level);
        player.setExp(exp);
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);

        var maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        var maxHealth = maxHealthAttribute != null ? maxHealthAttribute.getValue() : 20;
        player.setHealth(Math.max(0, Math.min(health, maxHealth)));

        for (var effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        for (var effect : potionEffects) {
            player.addPotionEffect(effect);
        }

        if (syncGameMode && gameMode != null) player.setGameMode(gameMode);
    }

    /**
     * Writes this snapshot to disk. Safe to call off the main/region thread -
     * it only touches file I/O, never Bukkit game state.
     */
    public void save(File file) {
        var config = new YamlConfiguration();
        config.set("contents", List.of(contents));
        config.set("armor", List.of(armor));
        config.set("off-hand", offHand);
        config.set("ender-chest", List.of(enderChest));
        config.set("level", level);
        config.set("exp", exp);
        config.set("food-level", foodLevel);
        config.set("saturation", saturation);
        config.set("exhaustion", exhaustion);
        config.set("health", health);
        config.set("potion-effects", potionEffects);
        if (syncGameMode && gameMode != null) config.set("game-mode", gameMode.name());
        try {
            file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Failed to save player snapshot to " + file, e);
        }
    }

    /**
     * Reads a snapshot from disk. Safe to call off the main/region thread.
     */
    public static Optional<PlayerSnapshot> load(File file, boolean syncGameMode) {
        if (!file.exists()) return Optional.empty();
        var config = YamlConfiguration.loadConfiguration(file);
        var snapshot = new PlayerSnapshot();

        snapshot.contents = toItemStackArray(config.getList("contents"));
        snapshot.armor = toItemStackArray(config.getList("armor"));
        var offHand = config.getItemStack("off-hand");
        snapshot.offHand = offHand != null ? offHand : new ItemStack(Material.AIR);
        snapshot.enderChest = toItemStackArray(config.getList("ender-chest"));

        snapshot.level = config.getInt("level");
        snapshot.exp = (float) config.getDouble("exp");
        snapshot.foodLevel = config.getInt("food-level", 20);
        snapshot.saturation = (float) config.getDouble("saturation", 5);
        snapshot.exhaustion = (float) config.getDouble("exhaustion", 0);
        snapshot.health = config.getDouble("health", 20);

        var effects = new ArrayList<PotionEffect>();
        for (var raw : config.getList("potion-effects", List.of())) {
            if (raw instanceof PotionEffect effect) effects.add(effect);
        }
        snapshot.potionEffects = effects;

        snapshot.syncGameMode = syncGameMode;
        var gameModeName = config.getString("game-mode");
        if (syncGameMode && gameModeName != null) {
            try {
                snapshot.gameMode = GameMode.valueOf(gameModeName);
            } catch (IllegalArgumentException ignored) {
                // stale/unknown value, just skip applying game mode for this load
            }
        }

        return Optional.of(snapshot);
    }

    private static ItemStack[] toItemStackArray(List<?> list) {
        if (list == null) return new ItemStack[0];
        var array = new ItemStack[list.size()];
        for (int i = 0; i < list.size(); i++) {
            var element = list.get(i);
            array[i] = element instanceof ItemStack stack ? stack : null;
        }
        return array;
    }
}
