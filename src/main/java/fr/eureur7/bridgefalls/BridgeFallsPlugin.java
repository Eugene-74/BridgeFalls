package fr.eureur7.bridgefalls;

import co.aikar.commands.PaperCommandManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.Color;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BridgeFallsPlugin extends JavaPlugin {
    private boolean bridgeFallsEnabled = true;
    private boolean fallingEnabled = true;
    private Set<Material> noRestBlocksVertical = new HashSet<>();
    private Set<Material> noRestBlocksHorizontal = new HashSet<>();
    private Set<Material> alwaysStableBlocks = new HashSet<>();
    private Set<Material> floatingSupportBlocks = new HashSet<>();
    private final Map<Location, Long> unstableBlocks = new HashMap<>();
    private long fallDelayMillis = 60_000L;
    private int supportRadius = 2;
    private int topSupportRadius = 0;
    private boolean allowPlacingUnstableBlocks = false;
    private boolean fallingBlockDropItem = false;
    private boolean fallingBlockHurtEntities = true;
    private FileConfiguration messagesConfig;

    private Color instabilityColorStart = Color.YELLOW;
    private Color instabilityColorMiddle = Color.ORANGE;
    private Color instabilityColorEnd = Color.RED;

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

        getServer().getPluginManager().registerEvents(new BridgeFallsListener(), this);

        PaperCommandManager manager = new PaperCommandManager(this);

        // Complétions pour les listes de configuration
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

        // Complétions pour les ajouts : tous les matériaux - ceux déjà dans la liste
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

        // Tâche répétée :
        // - afficher en continu les particules autour des blocs instables
        // - les faire tomber après un certain délai
        // - retirer ceux qui redeviennent stables ou sont détruits
        getServer().getScheduler().runTaskTimer(this, () -> {
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
                        continue;
                    }

                    Block block = loc.getBlock();

                    // Si le bloc n'existe plus, on le retire
                    if (block.getType() == Material.AIR) {
                        toRemove.add(loc);
                        continue;
                    }

                    // Si le bloc est redevenu stable, on le retire et on arrête d'afficher les
                    // particules
                    if (BridgeFallsListener.isBlockSupported(block)) {
                        toRemove.add(loc);
                        continue;
                    }

                    // Si la chute est activée et que le délai est écoulé,
                    // on marquera ce bloc pour qu'il tombe.
                    if (fallingEnabled && fallDelayMillis > 0 && now - createdAt >= fallDelayMillis) {
                        toRemove.add(loc);
                        toFall.add(block);
                        continue;
                    }

                    // Toujours instable : on affiche/rafraîchit son contour de particules
                    if (!fallingEnabled) {
                        // Mode "pause" : on continue juste à montrer les blocs instables
                        // sans faire avancer leur chute.
                        BridgeFallsListener.showRedOutline(block);
                        continue;
                    }

                    // Chute active : couleur selon le temps restant avant la chute (configurable) :
                    // - 1er tiers : couleur 1
                    // - 2e tiers : couleur 2
                    // - 3e tiers : couleur 3
                    if (fallDelayMillis <= 0) {
                        BridgeFallsListener.showRedOutline(block);
                    } else {
                        long elapsed = now - createdAt;
                        double ratio = Math.max(0.0, Math.min(1.0, (double) elapsed / (double) fallDelayMillis));

                        if (ratio < (1.0 / 3.0)) {
                            BridgeFallsListener.showColoredOutline(block, instabilityColorStart);
                        } else if (ratio < (2.0 / 3.0)) {
                            BridgeFallsListener.showColoredOutline(block, instabilityColorMiddle);
                        } else {
                            BridgeFallsListener.showColoredOutline(block, instabilityColorEnd);
                            // Phase rouge : le bloc va bientôt s'effondrer, on joue un son d'alerte
                            BridgeFallsListener.playRedPhaseWarningSound(block.getLocation());
                        }
                    }
                }

                if (!toRemove.isEmpty()) {
                    for (Location loc : toRemove) {
                        unstableBlocks.remove(loc);
                    }
                    changed = true;
                }
            }

            // On déclenche la chute des blocs identifiés en dehors du bloc synchronized
            // pour éviter toute modification concurrente de la map pendant l'itération.
            for (Block blockToFall : toFall) {
                BridgeFallsListener.startFalling(blockToFall);
            }

            if (changed) {
                saveUnstableBlocks();
            }
        }, 20L, 20L);
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        loadNoRestBlocks();
        loadMessages();
        loadInstabilityColors();
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

        // L'air ne peut jamais servir de support vertical
        if (material == Material.AIR) {
            return true;
        }

        // La décision vient uniquement de la liste de config
        return noRestBlocksVertical.contains(material);
    }

    public boolean isNoRestBlockHorizontal(Material material) {
        if (material == null) {
            return true;
        }

        // L'air ne compte jamais comme bloc structurant horizontalement
        if (material == Material.AIR) {
            return true;
        }

        // La décision vient uniquement de la liste de config
        return noRestBlocksHorizontal.contains(material);
    }

    public boolean isAlwaysStable(Material material) {
        if (material == null) {
            return false;
        }

        return alwaysStableBlocks.contains(material);
    }

    public int getSupportRadius() {
        return supportRadius;
    }

    public int getTopSupportRadius() {
        return topSupportRadius;
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

    /**
     * Indique si un matériau peut servir de support horizontal dans le réseau
     * de structure.
     *
     * Cas particulier souhaité :
     * - si un bloc est à la fois dans always-stable-blocks et
     * no-rest-blocks-horizontal, il est stable pour lui-même mais ne sert
     * jamais de support horizontal pour les autres.
     */
    public boolean isHorizontalSupportProvider(Material material) {
        if (material == null) {
            return false;
        }

        if (material == Material.AIR) {
            return false;
        }

        // Bloc explicitement listé comme "no-rest-blocks-horizontal" ne doit pas
        // être utilisé comme support horizontal.
        if (noRestBlocksHorizontal.contains(material)) {
            return false;
        }

        // Un bloc toujours stable mais non marqué comme no-rest-blocks-horizontal
        // peut servir de support horizontal (comportement actuel conservé).
        return true;
    }

    public void addUnstableBlock(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        synchronized (unstableBlocks) {
            if (!unstableBlocks.containsKey(location)) {
                unstableBlocks.put(location, System.currentTimeMillis());
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
            // Config absente : garder les valeurs par défaut
            instabilityColorStart = Color.YELLOW;
            instabilityColorMiddle = Color.ORANGE;
            instabilityColorEnd = Color.RED;
            return;
        }

        // On lit au plus 3 couleurs dans l'ordre
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

        // Noms simples
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

        // Hex RGB du type "#RRGGBB" ou "RRGGBB"
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

        return raw;
    }

    private void loadNoRestBlocks() {
        noRestBlocksVertical.clear();
        noRestBlocksHorizontal.clear();
        alwaysStableBlocks.clear();
        floatingSupportBlocks.clear();

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

        // Blocs qui peuvent flotter et donc reposer sur l'eau
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

        // Autoriser ou non la pose de blocs instables
        allowPlacingUnstableBlocks = getConfig().getBoolean("allow-placing-unstable-blocks", false);

        // Comportement des FallingBlock créés par le plugin
        fallingBlockDropItem = getConfig().getBoolean("falling-block-drop-item", false);
        fallingBlockHurtEntities = getConfig().getBoolean("falling-block-hurt-entities", true);

        // Activation / désactivation temporaire de la chute des blocs instables
        fallingEnabled = getConfig().getBoolean("falling-enabled", true);
    }

    public boolean isFallingEnabled() {
        return fallingEnabled;
    }

    /**
     * Active ou désactive la chute des blocs instables.
     *
     * Si on réactive alors que c'était désactivé, tous les timers
     * des blocs instables sont remis à zéro et repartent depuis maintenant.
     */
    public void setFallingEnabled(boolean enabled) {
        boolean wasEnabled = this.fallingEnabled;
        this.fallingEnabled = enabled;

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

    private void saveUnstableBlocks() {
        File file = new File(getDataFolder(), "unstable-blocks.yml");
        if (!file.getParentFile().exists()) {
            // noinspection ResultOfMethodCallIgnored
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
            getLogger().warning("Impossible de sauvegarder les blocs instables: " + e.getMessage());
        }
    }
}
