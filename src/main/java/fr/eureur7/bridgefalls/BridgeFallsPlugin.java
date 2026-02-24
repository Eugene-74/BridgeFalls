package fr.eureur7.bridgefalls;

import co.aikar.commands.PaperCommandManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.Color;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.InventoryHolder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.plugin.Plugin;

public class BridgeFallsPlugin extends JavaPlugin {
    private boolean bridgeFallsEnabled = true;
    private boolean fallingBlockEnabled = true;
    private Set<Material> noRestBlocksVertical = new HashSet<>();
    private Set<Material> noRestBlocksHorizontal = new HashSet<>();
    private Set<Material> alwaysStableBlocks = new HashSet<>();
    private Set<Material> floatingSupportBlocks = new HashSet<>();
    private Set<GameMode> disabledGamemodes = new HashSet<>();
    private final Map<Location, Long> unstableBlocks = new HashMap<>();
    private final Map<Location, Long> unstableBlocksLastAnchorCheck = new HashMap<>();
    private final Map<Location, Boolean> unstableBlocksMissingAnchor = new HashMap<>();
    private long fallDelayMillis = 60_000L;
    private int supportRadius = 2;
    private int topSupportRadius = 0;
    private int anchorSupportRadius = 2;
    private int anchorSupportRadiusCheckWhenBreaking = 2;
    private long anchorMaxTimerNanos = 5_000_000L;

    private long timeToCheckTicks = 20L;
    private long timeToCheckAnchorTicks = 20L;
    private int recheckQueueMaxSize = 4096;
    private int recheckDrainBatchSize = 8;
    private boolean allowPlacingUnstableBlocks = false;
    private boolean fallingBlockDropItem = false;
    private boolean fallingBlockHurtEntities = true;
    private boolean fallingBlockDisableDuringSiege = false;
    private FileConfiguration messagesConfig;

    private Color instabilityColorStart = Color.YELLOW;
    private Color instabilityColorMiddle = Color.ORANGE;
    private Color instabilityColorEnd = Color.RED;

    public static Color defaultInstabilityColor = Color.BLUE;

    private ScheduledTask unstableCheckTask;

    private boolean townyReflectionInitialized = false;
    private boolean townyReflectionAvailable = false;
    private Method townyApiGetInstanceMethod;
    private Method townyApiGetTownBlockMethod;
    private Method townBlockGetTownOrNullMethod;
    private Method townHasActiveWarMethod;
    private Method townHasNationMethod;
    private Method townGetNationOrNullMethod;
    private Method nationHasActiveWarMethod;

    public static BridgeFallsPlugin getInstance() {
        return JavaPlugin.getPlugin(BridgeFallsPlugin.class);
    }

    @Override
    public void onEnable() {
        new Metrics(this, 29737);
        saveDefaultConfig();

        loadNoRestBlocks();
        loadUnstableBlocks();
        loadMessages();
        loadInstabilityColors();
        loadDisabledGamemodes();

        resetTimersForAllUnstableBlocks();

        getServer().getPluginManager().registerEvents(new BridgeFallsListener(), this);

        PaperCommandManager manager = new PaperCommandManager(this);

        manager.getCommandCompletions().registerAsyncCompletion("bf_no_rest_vertical",
                c -> getConfig().getStringList("no-rest-blocks-vertical"));
        manager.getCommandCompletions().registerAsyncCompletion("bf_no_rest_horizontal",
                c -> getConfig().getStringList("no-rest-blocks-horizontal"));
        manager.getCommandCompletions().registerAsyncCompletion("bf_always_stable",
                c -> getConfig().getStringList("always-stable-blocks"));
        manager.getCommandCompletions().registerAsyncCompletion("bf_always_stable_no_support",
                c -> getConfig().getStringList("always-stable-blocks-but-with-no-support"));
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

        manager.getCommandCompletions().registerAsyncCompletion("bf_always_stable_no_support_add", c -> {
            List<String> existing = getConfig().getStringList("always-stable-blocks-but-with-no-support");
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
        if (isEnabled()) {
            restartUnstableCheckTask();
        }
    }

    public boolean isBridgeFallsEnabled() {
        return bridgeFallsEnabled;
    }

    public void setBridgeFallsEnabled(boolean bridgeFallsEnabled) {
        this.bridgeFallsEnabled = bridgeFallsEnabled;
        resetTimersForAllUnstableBlocks();
    }

    private void resetTimersForAllUnstableBlocks() {
        long now = System.currentTimeMillis();

        synchronized (unstableBlocks) {
            for (Map.Entry<Location, Long> entry : unstableBlocks.entrySet()) {
                entry.setValue(now);
            }

            unstableBlocksLastAnchorCheck.keySet().retainAll(unstableBlocks.keySet());
            for (Location location : unstableBlocks.keySet()) {
                unstableBlocksLastAnchorCheck.put(location, now);
            }

            unstableBlocksMissingAnchor.keySet().retainAll(unstableBlocks.keySet());
            for (Location location : unstableBlocks.keySet()) {
                unstableBlocksMissingAnchor.putIfAbsent(location, false);
            }
        }

        saveUnstableBlocks();
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

    public int getAnchorSupportRadiusCheckWhenBreaking() {
        return anchorSupportRadiusCheckWhenBreaking;
    }

    public long getAnchorMaxTimerNanos() {
        return anchorMaxTimerNanos;
    }

    public long getTimeToCheckTicks() {
        return timeToCheckTicks;
    }

    public long getTimeToCheckAnchorTicks() {
        return timeToCheckAnchorTicks;
    }

    public int getRecheckQueueMaxSize() {
        return recheckQueueMaxSize;
    }

    public int getRecheckDrainBatchSize() {
        return recheckDrainBatchSize;
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
        addUnstableBlock(location, false);
    }

    public void addUnstableBlock(Location location, boolean missingAnchor) {
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
                long now = System.currentTimeMillis();
                unstableBlocks.put(location, now);
                unstableBlocksLastAnchorCheck.put(location, now);
                unstableBlocksMissingAnchor.put(location, missingAnchor);
                saveUnstableBlocks();
            }
        }
    }

    public void removeUnstableBlock(Location location) {
        if (location == null) {
            return;
        }

        synchronized (unstableBlocks) {
            if (unstableBlocks.remove(location) != null) {
                unstableBlocksLastAnchorCheck.remove(location);
                unstableBlocksMissingAnchor.remove(location);
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
        instabilityColorStart = Color.YELLOW;
        instabilityColorMiddle = Color.ORANGE;
        instabilityColorEnd = Color.RED;
        defaultInstabilityColor = Color.BLUE;

        List<String> entries = getConfig().getStringList("instability-colors");
        if (entries != null && !entries.isEmpty() && entries.size() >= 1) {
            Color c = parseColor(entries.get(0));
            if (c != null) {
                instabilityColorStart = c;
            }
        }
        if (entries != null && !entries.isEmpty() && entries.size() >= 2) {
            Color c = parseColor(entries.get(1));
            if (c != null) {
                instabilityColorMiddle = c;
            }
        }
        if (entries != null && !entries.isEmpty() && entries.size() >= 3) {
            Color c = parseColor(entries.get(2));
            if (c != null) {
                instabilityColorEnd = c;
            }
        }

        Color configuredDefaultColor = parseConfiguredColor("instable-color");
        if (configuredDefaultColor != null) {
            defaultInstabilityColor = configuredDefaultColor;
        }
    }

    private Color parseConfiguredColor(String path) {
        Object rawValue = getConfig().get(path);
        if (rawValue instanceof String) {
            return parseColor((String) rawValue);
        }

        if (rawValue instanceof List<?>) {
            for (Object item : (List<?>) rawValue) {
                if (item instanceof String) {
                    Color parsed = parseColor((String) item);
                    if (parsed != null) {
                        return parsed;
                    }
                }
            }
        }

        return null;
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

        int anchorRadiusCheckWhenBreaking = getConfig().getInt("anchor-support-radius-check-when-breaking",
                anchorSupportRadius);
        if (anchorRadiusCheckWhenBreaking < 1) {
            anchorRadiusCheckWhenBreaking = 1;
        }
        anchorSupportRadiusCheckWhenBreaking = anchorRadiusCheckWhenBreaking;

        long anchorMaxTimeMs = getConfig().getLong("anchor-max-time-ms", 5L);
        if (anchorMaxTimeMs < 0L) {
            anchorMaxTimeMs = 0L;
        }
        anchorMaxTimerNanos = anchorMaxTimeMs * 1_000_000L;

        long configuredTimeToCheck = getConfig().getLong("time-to-check", 20L);
        if (configuredTimeToCheck < 1L) {
            configuredTimeToCheck = 1L;
        }
        timeToCheckTicks = configuredTimeToCheck;

        long configuredTimeToCheckAnchor = getConfig().getLong("time-to-check-anchor", configuredTimeToCheck);
        if (configuredTimeToCheckAnchor < 1L) {
            configuredTimeToCheckAnchor = 1L;
        }
        timeToCheckAnchorTicks = configuredTimeToCheckAnchor;

        int configuredRecheckQueueMaxSize = getConfig().getInt("recheck-queue-max-size", 4096);
        if (configuredRecheckQueueMaxSize < 1) {
            configuredRecheckQueueMaxSize = 1;
        }
        recheckQueueMaxSize = configuredRecheckQueueMaxSize;

        int configuredRecheckDrainBatchSize = getConfig().getInt("recheck-drain-batch-size", 8);
        if (configuredRecheckDrainBatchSize < 1) {
            configuredRecheckDrainBatchSize = 1;
        }
        recheckDrainBatchSize = configuredRecheckDrainBatchSize;

        allowPlacingUnstableBlocks = getConfig().getBoolean("allow-placing-unstable-blocks", false);

        fallingBlockDropItem = getConfig().getBoolean("falling-block-drop-item", false);
        fallingBlockHurtEntities = getConfig().getBoolean("falling-block-hurt-entities", true);
        fallingBlockDisableDuringSiege = getConfig().getBoolean("falling-block-disable-during-siege", false);

        fallingBlockEnabled = getConfig().getBoolean("falling-block", true);
    }

    public boolean isFallingBlockDisableDuringSiegeEnabled() {
        return this.fallingBlockDisableDuringSiege;
    }

    public boolean isFallingBlockEnabled() {
        return isFallingBlockEnabledAt(null);
    }

    public boolean isFallingBlockEnabledAt(Location location) {
        if (!this.fallingBlockEnabled) {
            return false;
        }

        if (!this.fallingBlockDisableDuringSiege || location == null) {
            return true;
        }

        return !isSiegeActiveAt(location);
    }

    public void setFallingBlockEnabled(boolean enabled) {
        boolean wasEnabled = this.fallingBlockEnabled;
        this.fallingBlockEnabled = enabled;

        if (enabled && !wasEnabled) {
            resetTimersForAllUnstableBlocks();
        }
    }

    private boolean isSiegeActiveAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        Plugin townyPlugin = getServer().getPluginManager().getPlugin("Towny");
        if (townyPlugin == null || !townyPlugin.isEnabled()) {
            return false;
        }

        if (!initTownyReflection()) {
            return false;
        }

        try {
            Object townyApi = townyApiGetInstanceMethod.invoke(null);
            if (townyApi == null) {
                return false;
            }

            Object townBlock = townyApiGetTownBlockMethod.invoke(townyApi, location);
            if (townBlock == null) {
                return false;
            }

            Object town = townBlockGetTownOrNullMethod.invoke(townBlock);
            if (town == null) {
                return false;
            }

            if (Boolean.TRUE.equals(townHasActiveWarMethod.invoke(town))) {
                return true;
            }

            if (!Boolean.TRUE.equals(townHasNationMethod.invoke(town))) {
                return false;
            }

            Object nation = townGetNationOrNullMethod.invoke(town);
            return nation != null && Boolean.TRUE.equals(nationHasActiveWarMethod.invoke(nation));
        } catch (ReflectiveOperationException exception) {
            if (getConfig().getBoolean("debug", false)) {
                getLogger().warning("Towny siege lookup failed: " + exception.getMessage());
            }
            return false;
        }
    }

    private boolean initTownyReflection() {
        if (townyReflectionInitialized) {
            return townyReflectionAvailable;
        }

        townyReflectionInitialized = true;

        try {
            Class<?> townyApiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Class<?> townBlockClass = Class.forName("com.palmergames.bukkit.towny.object.TownBlock");
            Class<?> townClass = Class.forName("com.palmergames.bukkit.towny.object.Town");
            Class<?> nationClass = Class.forName("com.palmergames.bukkit.towny.object.Nation");

            townyApiGetInstanceMethod = townyApiClass.getMethod("getInstance");
            townyApiGetTownBlockMethod = townyApiClass.getMethod("getTownBlock", Location.class);
            townBlockGetTownOrNullMethod = townBlockClass.getMethod("getTownOrNull");
            townHasActiveWarMethod = townClass.getMethod("hasActiveWar");
            townHasNationMethod = townClass.getMethod("hasNation");
            townGetNationOrNullMethod = townClass.getMethod("getNationOrNull");
            nationHasActiveWarMethod = nationClass.getMethod("hasActiveWar");

            townyReflectionAvailable = true;
        } catch (ReflectiveOperationException exception) {
            townyReflectionAvailable = false;
            if (getConfig().getBoolean("debug", false)) {
                getLogger().warning("Towny API not available for siege checks: " + exception.getMessage());
            }
        }

        return townyReflectionAvailable;
    }

    private void loadUnstableBlocks() {
        unstableBlocks.clear();
        unstableBlocksLastAnchorCheck.clear();
        unstableBlocksMissingAnchor.clear();

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
            Object missingAnchorObj = raw.get("missingAnchor");

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

            boolean missingAnchor = false;
            if (missingAnchorObj instanceof Boolean) {
                missingAnchor = (Boolean) missingAnchorObj;
            }

            Location location = new Location(Bukkit.getWorld(worldName), x, y, z);
            unstableBlocks.put(location, createdAt);
            unstableBlocksLastAnchorCheck.put(location, now);
            unstableBlocksMissingAnchor.put(location, missingAnchor);
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
                boolean missingAnchor = unstableBlocksMissingAnchor.getOrDefault(loc, false);

                if (loc.getWorld() == null) {
                    continue;
                }

                Map<String, Object> map = new HashMap<>();
                map.put("world", loc.getWorld().getName());
                map.put("x", loc.getBlockX());
                map.put("y", loc.getBlockY());
                map.put("z", loc.getBlockZ());
                map.put("createdAt", createdAt);
                map.put("missingAnchor", missingAnchor);
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

        unstableCheckTask = getServer().getGlobalRegionScheduler().runAtFixedRate(
                this,
                task -> runUnstableCheckCycle(),
                timeToCheckTicks,
                timeToCheckTicks);
    }

    private void runUnstableCheckCycle() {
        if (!bridgeFallsEnabled) {
            return;
        }

        java.util.List<Location> locationsToCheck;
        synchronized (unstableBlocks) {
            locationsToCheck = new java.util.ArrayList<>(unstableBlocks.keySet());
        }

        for (Location location : locationsToCheck) {
            if (location == null || location.getWorld() == null) {
                removeUnstableBlockAndSave(location,
                        "Removed unstable block at " + location + " because world is null.");
                continue;
            }

            Location regionLocation = location.clone();
            getServer().getRegionScheduler().execute(this, regionLocation,
                    () -> runUnstableCheckAtLocation(regionLocation));
        }
    }

    private void runUnstableCheckAtLocation(Location location) {
        long createdAt;
        synchronized (unstableBlocks) {
            Long storedCreatedAt = unstableBlocks.get(location);
            if (storedCreatedAt == null) {
                return;
            }
            createdAt = storedCreatedAt;
        }

        long now = System.currentTimeMillis();

        Block block = location.getBlock();

        if (block.getType() == Material.AIR) {
            removeUnstableBlockAndSave(location,
                    "Removed unstable block at " + location + " because block is now AIR.");
            return;
        }

        boolean hasStructuralSupport = BridgeFallsListener.isBlockSupported(block);

        long anchorCheckIntervalMillis = timeToCheckAnchorTicks * 50L;
        boolean shouldCheckAnchor = false;
        boolean wasMissingAnchor = false;
        synchronized (unstableBlocks) {
            wasMissingAnchor = Boolean.TRUE.equals(unstableBlocksMissingAnchor.get(location));
            if (hasStructuralSupport || wasMissingAnchor) {
                Long lastAnchorCheck = unstableBlocksLastAnchorCheck.get(location);
                shouldCheckAnchor = lastAnchorCheck == null || now - lastAnchorCheck >= anchorCheckIntervalMillis;
                if (shouldCheckAnchor) {
                    unstableBlocksLastAnchorCheck.put(location, now);
                }
            }
        }

        if (shouldCheckAnchor) {
            boolean hasAnchor = BridgeFallsListener.hasAnchor(block, anchorSupportRadius);
            if (hasAnchor) {
                if (hasStructuralSupport) {
                    removeUnstableBlockAndSave(location,
                            "Block at " + block.getLocation()
                                    + " is now supported again and removed from unstable blocks.");
                    return;
                }
                if (wasMissingAnchor) {
                    synchronized (unstableBlocks) {
                        unstableBlocksMissingAnchor.put(location, false);
                    }
                    saveUnstableBlocks();
                }
            } else if (hasStructuralSupport && !wasMissingAnchor) {
                synchronized (unstableBlocks) {
                    unstableBlocksMissingAnchor.put(location, true);
                }
                saveUnstableBlocks();
            }
        }

        boolean fallingBlockEnabledAtLocation = isFallingBlockEnabledAt(location);

        if (fallingBlockEnabledAtLocation && fallDelayMillis >= 0 && now - createdAt >= fallDelayMillis) {
            if (removeUnstableBlockAndSave(location, "Block at " + block.getLocation() + " is now falling.")) {
                BridgeFallsListener.startFalling(block);
            }
            return;
        }

        if (!fallingBlockEnabledAtLocation) {
            BridgeFallsListener.showColoredOutline(block, defaultInstabilityColor);
            return;
        }

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

    private boolean removeUnstableBlockAndSave(Location location, String reason) {
        if (location == null) {
            return false;
        }

        boolean removed;
        synchronized (unstableBlocks) {
            removed = unstableBlocks.remove(location) != null;
            unstableBlocksLastAnchorCheck.remove(location);
            unstableBlocksMissingAnchor.remove(location);
        }

        if (removed) {
            log(reason);
            saveUnstableBlocks();
        }

        return removed;
    }
}
