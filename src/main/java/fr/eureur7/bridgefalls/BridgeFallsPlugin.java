package fr.eureur7.bridgefalls;

import co.aikar.commands.PaperCommandManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.Color;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.InventoryHolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.GameMode;

public class BridgeFallsPlugin extends JavaPlugin {
    private boolean bridgeFallsEnabled = true;
    private boolean fallingBlockEnabled = true;
    private Set<Material> noRestBlocksVertical = new HashSet<>();
    private Set<Material> noRestBlocksHorizontal = new HashSet<>();
    private Set<Material> alwaysStableBlocks = new HashSet<>();
    private Set<Material> floatingSupportBlocks = new HashSet<>();
    private Set<GameMode> disabledGamemodes = new HashSet<>();
    private final Map<Location, Long> unstableBlocks = new HashMap<>();
    private long fallDelayMillis = 60_000L;
    private int supportRadius = 2;
    private int topSupportRadius = 0;
    private int anchorSupportRadius = 2;
    private long timeToCheckTicks = 20L;
    private boolean allowPlacingUnstableBlocks = false;
    private boolean fallingBlockDropItem = false;
    private boolean fallingBlockHurtEntities = true;
    private FileConfiguration messagesConfig;

    private Color instabilityColorStart = Color.YELLOW;
    private Color instabilityColorMiddle = Color.ORANGE;
    private Color instabilityColorEnd = Color.RED;
    private BukkitTask unstableCheckTask;

    public static BridgeFallsPlugin getInstance() {
        return JavaPlugin.getPlugin(BridgeFallsPlugin.class);
    }

    @Override
    public void onEnable() {
        new Metrics(this, 21798);
        saveDefaultConfig();

        loadNoRestBlocks();
        loadUnstableBlocks();
        loadMessages();
        loadInstabilityColors();
        loadDisabledGamemodes();

        getServer().getPluginManager().registerEvents(new BridgeFallsListener(), this);

        PaperCommandManager manager = new PaperCommandManager(this);

        manager.getCommandCompletions().registerAsyncCompletion("bf_no_rest_vertical",
                c -> getConfig().getStringList("no-rest-blocks-vertical"));
        manager.getCommandCompletions().registerAsyncCompletion("bf_no_rest_horizontal",
                c -> getConfig().getStringList("no-rest-blocks-horizontal"));
        manager.getCommandCompletions().registerAsyncCompletion("bf_always_stable",
                c -> getConfig().getStringList("always-stable-blocks"));
        manager.getCommandCompletions().registerAsyncCompletion("bf_floating_support",
                c -> getConfig().getStringList("floating-support-blocks"));
        manager.getCommandCompletions().registerAsyncCompletion("bf_instability_colors",
                c -> getConfig().getStringList("instability-colors"));

        manager.getCommandCompletions().registerAsyncCompletion("bf_disabled_gamemodes",
                c -> getConfig().getStringList("disabled-gamemodes"));

        manager.getCommandCompletions().registerAsyncCompletion("bf_disabled_gamemodes_add", c -> {
            List<String> existing = getConfig().getStringList("disabled-gamemodes");
            Set<String> existingUpper = new HashSet<>();
            for (String s : existing) {
                if (s != null) {
                    existingUpper.add(s.toUpperCase());
                }
            }
            List<String> result = new ArrayList<>();
            for (GameMode gm : GameMode.values()) {
                String name = gm.name();
                if (!existingUpper.contains(name)) {
                    result.add(name.toLowerCase());
                }
            }
            return result;
        });

        manager.getCommandCompletions().registerAsyncCompletion("bf_no_rest_vertical_add", c -> {
            List<String> existing = getConfig().getStringList("no-rest-blocks-vertical");
            Set<String> existingUpper = new HashSet<>();
            for (String s : existing) {
                if (s != null) {
                    existingUpper.add(s.toUpperCase());
                }
            }
            List<String> result = new ArrayList<>();
            for (Material m : Material.values()) {
                String name = m.name();
                if (!existingUpper.contains(name)) {
                    result.add(name.toLowerCase());
                }
            }
            return result;
        });

        manager.getCommandCompletions().registerAsyncCompletion("bf_no_rest_horizontal_add", c -> {
            List<String> existing = getConfig().getStringList("no-rest-blocks-horizontal");
            Set<String> existingUpper = new HashSet<>();
            for (String s : existing) {
                if (s != null) {
                    existingUpper.add(s.toUpperCase());
                }
            }
            List<String> result = new ArrayList<>();
            for (Material m : Material.values()) {
                String name = m.name();
                if (!existingUpper.contains(name)) {
                    result.add(name.toLowerCase());
                }
            }
            return result;
        });

        manager.getCommandCompletions().registerAsyncCompletion("bf_always_stable_add", c -> {
            List<String> existing = getConfig().getStringList("always-stable-blocks");
            Set<String> existingUpper = new HashSet<>();
            for (String s : existing) {
                if (s != null) {
                    existingUpper.add(s.toUpperCase());
                }
            }
            List<String> result = new ArrayList<>();
            for (Material m : Material.values()) {
                String name = m.name();
                if (!existingUpper.contains(name)) {
                    result.add(name.toLowerCase());
                }
            }
            return result;
        });

        manager.getCommandCompletions().registerAsyncCompletion("bf_floating_support_add", c -> {
            List<String> existing = getConfig().getStringList("floating-support-blocks");
            Set<String> existingUpper = new HashSet<>();
            for (String s : existing) {
                if (s != null) {
                    existingUpper.add(s.toUpperCase());
                }
            }
            List<String> result = new ArrayList<>();
            for (Material m : Material.values()) {
                String name = m.name();
                if (!existingUpper.contains(name)) {
                    result.add(name.toLowerCase());
                }
            }
            return result;
        });

        manager.registerCommand(new BridgeFallsCommand());

        restartUnstableCheckTask();
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        loadNoRestBlocks();
        loadMessages();
        loadInstabilityColors();
        loadDisabledGamemodes();
        restartUnstableCheckTask();
    }

    public boolean isBridgeFallsEnabled() {
        return bridgeFallsEnabled;
    }

    public void setBridgeFallsEnabled(boolean bridgeFallsEnabled) {
        this.bridgeFallsEnabled = bridgeFallsEnabled;
    }

    public boolean isNoRestBlockVertical(Material material) {
        if (material == null) {
            return true;
        }

        if (material == Material.AIR) {
            return true;
        }

        return noRestBlocksVertical.contains(material);
    }

    public boolean isNoRestBlockHorizontal(Material material) {
        if (material == null) {
            return true;
        }

        if (material == Material.AIR) {
            return true;
        }

        return noRestBlocksHorizontal.contains(material);
    }

    public boolean isAlwaysStable(Material material) {
        if (material == null) {
            return false;
        }
        if (material == Material.AIR) {
            return true;
        }

        return alwaysStableBlocks.contains(material);
    }

    public int getSupportRadius() {
        return supportRadius;
    }

    public int getTopSupportRadius() {
        return topSupportRadius;
    }

    public int getAnchorSupportRadius() {
        return anchorSupportRadius;
    }

    public long getTimeToCheckTicks() {
        return timeToCheckTicks;
    }

    public boolean isAllowPlacingUnstableBlocks() {
        return allowPlacingUnstableBlocks;
    }

    public boolean isFallingBlockDropItem() {
        return fallingBlockDropItem;
    }

    public boolean isFallingBlockHurtEntities() {
        return fallingBlockHurtEntities;
    }

    public static void log(String message) {
        if (getInstance().getConfig().getBoolean("debug", false)) {
            getInstance().getLogger().info(message);
        }
    }

    public Set<Material> getNoRestBlocksVertical() {
        return new HashSet<>(noRestBlocksVertical);
    }

    public Set<Material> getNoRestBlocksHorizontal() {
        return new HashSet<>(noRestBlocksHorizontal);
    }

    public boolean isFloatingSupport(Material material) {
        if (material == null) {
            return false;
        }

        return floatingSupportBlocks.contains(material);
    }

    public boolean isGamemodeDisabled(GameMode gameMode) {
        if (gameMode == null) {
            return false;
        }
        return disabledGamemodes.contains(gameMode);
    }

    public Set<GameMode> getDisabledGamemodes() {
        return new HashSet<>(disabledGamemodes);
    }

    public boolean isHorizontalSupportProvider(Material material) {
        if (material == null) {
            return false;
        }

        if (material == Material.AIR) {
            return false;
        }

        if (noRestBlocksHorizontal.contains(material)) {
            return false;
        }

        return true;
    }

    public void addUnstableBlock(Location location) {
        Block block = location.getBlock();

        BlockState state = block.getState();

        if (state instanceof InventoryHolder ||
                state instanceof TileState) {
            log("Not marking block at " + location + " as unstable because it is a container.");
            return;
        }

        if (location == null || location.getWorld() == null) {
            log("Attempted to add an unstable block with null location or world.");
            return;
        }

        synchronized (unstableBlocks) {
            if (!unstableBlocks.containsKey(location)) {
                unstableBlocks.put(location, System.currentTimeMillis());
                saveUnstableBlocks();
            } else {
                log("Attempted to add an unstable block at " + location + " but it is already marked as unstable.");
            }
        }
    }

    public void removeUnstableBlock(Location location) {
        if (location == null) {
            return;
        }

        synchronized (unstableBlocks) {
            if (unstableBlocks.remove(location) != null) {
                saveUnstableBlocks();
            }
        }
    }

    public Set<Location> getUnstableBlocks() {
        synchronized (unstableBlocks) {
            return new HashSet<>(unstableBlocks.keySet());
        }
    }

    private void loadInstabilityColors() {
        List<String> entries = getConfig().getStringList("instability-colors");
        if (entries == null || entries.isEmpty()) {
            instabilityColorStart = Color.YELLOW;
            instabilityColorMiddle = Color.ORANGE;
            instabilityColorEnd = Color.RED;
            return;
        }

        if (entries.size() >= 1) {
            Color c = parseColor(entries.get(0));
            if (c != null) {
                instabilityColorStart = c;
            }
        }
        if (entries.size() >= 2) {
            Color c = parseColor(entries.get(1));
            if (c != null) {
                instabilityColorMiddle = c;
            }
        }
        if (entries.size() >= 3) {
            Color c = parseColor(entries.get(2));
            if (c != null) {
                instabilityColorEnd = c;
            }
        }
    }

    public Color parseColor(String value) {
        if (value == null) {
            return null;
        }

        String v = value.trim().toLowerCase();
        if (v.isEmpty()) {
            return null;
        }

        switch (v) {
            case "red":
                return Color.RED;
            case "green":
                return Color.GREEN;
            case "blue":
                return Color.BLUE;
            case "yellow":
                return Color.YELLOW;
            case "orange":
                return Color.ORANGE;
            case "white":
                return Color.WHITE;
            case "black":
                return Color.BLACK;
            case "gray":
            case "grey":
                return Color.GRAY;
            case "aqua":
            case "cyan":
                return Color.AQUA;
            case "fuchsia":
            case "magenta":
                return Color.FUCHSIA;
            case "lime":
                return Color.LIME;
            case "navy":
                return Color.NAVY;
            case "maroon":
                return Color.MAROON;
            case "olive":
                return Color.OLIVE;
            case "purple":
                return Color.PURPLE;
            case "silver":
                return Color.SILVER;
            case "teal":
                return Color.TEAL;
        }

        String hex = v.startsWith("#") ? v.substring(1) : v;
        if (hex.length() == 6) {
            try {
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                return Color.fromRGB(r, g, b);
            } catch (NumberFormatException ignored) {
            }
        }

        return null;
    }

    public String getMessage(String key) {
        return getMessage(key, Collections.emptyMap());
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String raw;
        if (messagesConfig != null && messagesConfig.isString("messages." + key)) {
            raw = messagesConfig.getString("messages." + key, key);
        } else {
            raw = key;
        }

        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                String token = "{" + e.getKey() + "}";
                raw = raw.replace(token, e.getValue());
            }
        }

        String colored = LegacyComponentSerializer.legacyAmpersand().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
        return applyColorTags(colored);
    }

    private String applyColorTags(String input) {
        if (input == null) {
            return null;
        }

        String result = input;
        result = result.replace("[GREEN]", "§a");
        result = result.replace("[RED]", "§c");
        result = result.replace("[YELLOW]", "§e");
        result = result.replace("[GRAY]", "§7");
        return result;
    }

    private void loadNoRestBlocks() {
        noRestBlocksVertical.clear();
        noRestBlocksHorizontal.clear();
        alwaysStableBlocks.clear();
        floatingSupportBlocks.clear();

        List<String> stableBlocksWithNoSupport = getConfig().getStringList("always-stable-blocks-but-with-no-support");
        for (String name : stableBlocksWithNoSupport) {
            if (name == null) {
                continue;
            }

            Material material = Material.matchMaterial(name.toUpperCase());
            if (material == null) {
                getLogger().warning("Unknown material in always-stable-blocks-but-with-no-support: " + name);
                continue;
            }
            noRestBlocksVertical.add(material);
            noRestBlocksHorizontal.add(material);
            alwaysStableBlocks.add(material);
        }

        List<String> verticalEntries = getConfig().getStringList("no-rest-blocks-vertical");
        for (String name : verticalEntries) {
            if (name == null) {
                continue;
            }

            Material material = Material.matchMaterial(name.toUpperCase());
            if (material == null) {
                getLogger().warning("Unknown material in no-rest-blocks-vertical: " + name);
                continue;
            }

            noRestBlocksVertical.add(material);
        }

        List<String> horizontalEntries = getConfig().getStringList("no-rest-blocks-horizontal");
        for (String name : horizontalEntries) {
            if (name == null) {
                continue;
            }

            Material material = Material.matchMaterial(name.toUpperCase());
            if (material == null) {
                getLogger().warning("Unknown material in no-rest-blocks-horizontal: " + name);
                continue;
            }

            noRestBlocksHorizontal.add(material);
        }

        List<String> stableEntries = getConfig().getStringList("always-stable-blocks");

        alwaysStableBlocks.add(Material.AIR); // Always consider AIR as stable

        for (String name : stableEntries) {
            if (name == null) {
                continue;
            }

            Material material = Material.matchMaterial(name.toUpperCase());
            if (material == null) {
                getLogger().warning("Unknown material in always-stable-blocks: " + name);
                continue;
            }

            alwaysStableBlocks.add(material);
        }

        List<String> floatingEntries = getConfig().getStringList("floating-support-blocks");
        for (String name : floatingEntries) {
            if (name == null) {
                continue;
            }

            Material material = Material.matchMaterial(name.toUpperCase());
            if (material == null) {
                getLogger().warning("Unknown material in floating-support-blocks: " + name);
                continue;
            }

            floatingSupportBlocks.add(material);
        }

        double minutes = getConfig().getDouble("fall-delay-minutes", 1.0D);
        if (minutes < 0) {
            minutes = 0;
        }
        fallDelayMillis = (long) (minutes * 60_000L);

        int radius = getConfig().getInt("support-radius", 2);
        if (radius < 1) {
            radius = 1;
        }
        supportRadius = radius;

        int topRadius = getConfig().getInt("top-support-radius", 0);
        if (topRadius < 0) {
            topRadius = 0;
        }
        topSupportRadius = topRadius;

        int anchorRadius = getConfig().getInt("anchor-support-radius", supportRadius);
        if (anchorRadius < 1) {
            anchorRadius = 1;
        }
        anchorSupportRadius = anchorRadius;

        long configuredTimeToCheck = getConfig().getLong("time-to-check", 20L);
        if (configuredTimeToCheck < 1L) {
            configuredTimeToCheck = 1L;
        }
        timeToCheckTicks = configuredTimeToCheck;

        allowPlacingUnstableBlocks = getConfig().getBoolean("allow-placing-unstable-blocks", false);

        fallingBlockDropItem = getConfig().getBoolean("falling-block-drop-item", false);
        fallingBlockHurtEntities = getConfig().getBoolean("falling-block-hurt-entities", true);

        fallingBlockEnabled = getConfig().getBoolean("falling-block", true);
    }

    public boolean isFallingBlockEnabled() {
        return this.fallingBlockEnabled;
    }

    public void setFallingBlockEnabled(boolean enabled) {
        boolean wasEnabled = this.fallingBlockEnabled;
        this.fallingBlockEnabled = enabled;

        if (enabled && !wasEnabled) {
            long now = System.currentTimeMillis();

            synchronized (unstableBlocks) {
                for (Map.Entry<Location, Long> entry : unstableBlocks.entrySet()) {
                    entry.setValue(now);
                }
            }

            saveUnstableBlocks();
        }
    }

    private void loadUnstableBlocks() {
        unstableBlocks.clear();

        File file = new File(getDataFolder(), "unstable-blocks.yml");
        if (!file.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> entries = config.getMapList("blocks");

        long now = System.currentTimeMillis();

        for (Map<?, ?> raw : entries) {
            Object worldNameObj = raw.get("world");
            Object xObj = raw.get("x");
            Object yObj = raw.get("y");
            Object zObj = raw.get("z");
            Object createdAtObj = raw.get("createdAt");

            if (!(worldNameObj instanceof String) || !(xObj instanceof Number)
                    || !(yObj instanceof Number) || !(zObj instanceof Number)) {
                continue;
            }

            String worldName = (String) worldNameObj;
            int x = ((Number) xObj).intValue();
            int y = ((Number) yObj).intValue();
            int z = ((Number) zObj).intValue();

            if (Bukkit.getWorld(worldName) == null) {
                continue;
            }

            long createdAt;
            if (createdAtObj instanceof Number) {
                createdAt = ((Number) createdAtObj).longValue();
            } else {
                createdAt = now;
            }

            unstableBlocks.put(new Location(Bukkit.getWorld(worldName), x, y, z), createdAt);
        }
    }

    private void loadMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        if (!file.exists()) {
            saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(file);
    }

    private void loadDisabledGamemodes() {
        disabledGamemodes.clear();

        List<String> gamemodeEntries = getConfig().getStringList("disabled-gamemodes");
        for (String name : gamemodeEntries) {
            if (name == null) {
                continue;
            }

            GameMode gameMode = GameMode.valueOf(name.toUpperCase());

            disabledGamemodes.add(gameMode);
        }
    }

    private void saveUnstableBlocks() {
        File file = new File(getDataFolder(), "unstable-blocks.yml");
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        FileConfiguration config = new YamlConfiguration();

        List<Map<String, Object>> serialized = new ArrayList<>();
        synchronized (unstableBlocks) {
            for (Map.Entry<Location, Long> entry : unstableBlocks.entrySet()) {
                Location loc = entry.getKey();
                Long createdAt = entry.getValue();

                if (loc.getWorld() == null) {
                    continue;
                }

                Map<String, Object> map = new HashMap<>();
                map.put("world", loc.getWorld().getName());
                map.put("x", loc.getBlockX());
                map.put("y", loc.getBlockY());
                map.put("z", loc.getBlockZ());
                map.put("createdAt", createdAt);
                serialized.add(map);
            }
        }

        config.set("blocks", serialized);

        try {
            config.save(file);
        } catch (IOException e) {
            getLogger().warning("Unable to save unstable blocks: " + e.getMessage());
        }
    }

    private void restartUnstableCheckTask() {
        if (unstableCheckTask != null) {
            unstableCheckTask.cancel();
            unstableCheckTask = null;
        }

        unstableCheckTask = getServer().getScheduler().runTaskTimer(
                this,
                this::runUnstableCheckCycle,
                timeToCheckTicks,
                timeToCheckTicks);
    }

    private void runUnstableCheckCycle() {
        if (!bridgeFallsEnabled) {
            return;
        }

        boolean changed = false;
        java.util.List<Location> toRemove = new java.util.ArrayList<>();
        java.util.List<Block> toFall = new java.util.ArrayList<>();

        synchronized (unstableBlocks) {
            long now = System.currentTimeMillis();

            for (Map.Entry<Location, Long> entry : unstableBlocks.entrySet()) {
                Location loc = entry.getKey();
                long createdAt = entry.getValue();

                if (loc.getWorld() == null) {
                    toRemove.add(loc);
                    log("Removed unstable block at " + loc + " because world is null.");
                    continue;
                }

                Block block = loc.getBlock();

                if (block.getType() == Material.AIR) {
                    toRemove.add(loc);
                    log("Removed unstable block at " + loc + " because block is now AIR.");
                    continue;
                }

                if (BridgeFallsListener.isBlockSupported(block)) {
                    if (BridgeFallsListener.hasAnchor(block, anchorSupportRadius)) {
                        log("Block at " + block.getLocation()
                                + " is now supported again and removed from unstable blocks.");
                        toRemove.add(loc);
                        continue;
                    }
                }

                if (isFallingBlockEnabled() && fallDelayMillis > 0 && now - createdAt >= fallDelayMillis) {
                    toRemove.add(loc);
                    log("Block at " + block.getLocation() + " is now falling.");
                    toFall.add(block);
                    continue;
                }

                if (!isFallingBlockEnabled()) {
                    BridgeFallsListener.showBlueOutline(block);
                } else {

                    long elapsed = now - createdAt;
                    double ratio = Math.max(0.0, Math.min(1.0, (double) elapsed / (double) fallDelayMillis));

                    if (ratio < (1.0 / 3.0)) {
                        BridgeFallsListener.showColoredOutline(block, instabilityColorStart);
                    } else if (ratio < (2.0 / 3.0)) {
                        BridgeFallsListener.showColoredOutline(block, instabilityColorMiddle);
                    } else {
                        BridgeFallsListener.showColoredOutline(block, instabilityColorEnd);
                        BridgeFallsListener.playRedPhaseWarningSound(block.getLocation());
                    }
                }
            }

            if (!toRemove.isEmpty()) {
                for (Location loc : toRemove) {
                    unstableBlocks.remove(loc);
                    log("Removed unstable block at " + loc);
                }
                changed = true;
            }
        }

        for (Block blockToFall : toFall) {
            BridgeFallsListener.startFalling(blockToFall);
        }

        if (changed) {
            saveUnstableBlocks();
        }
    }
}
