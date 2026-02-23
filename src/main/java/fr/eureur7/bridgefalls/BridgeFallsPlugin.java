package fr.eureur7.bridgefalls;

import co.aikar.commands.PaperCommandManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BridgeFallsPlugin extends JavaPlugin {
    private boolean bridgeFallsEnabled = true;
    private Set<Material> noRestBlocksVertical = new HashSet<>();
    private Set<Material> noRestBlocksHorizontal = new HashSet<>();
    private Set<Material> alwaysStableBlocks = new HashSet<>();
    private final Map<Location, Long> unstableBlocks = new HashMap<>();
    private long fallDelayMillis = 60_000L;
    private int supportRadius = 2;
    private int topSupportRadius = 0;
    private FileConfiguration messagesConfig;

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

        getServer().getPluginManager().registerEvents(new BridgeFallsListener(), this);

        PaperCommandManager manager = new PaperCommandManager(this);
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

            synchronized (unstableBlocks) {
                Iterator<Map.Entry<Location, Long>> it = unstableBlocks.entrySet().iterator();
                long now = System.currentTimeMillis();

                while (it.hasNext()) {
                    Map.Entry<Location, Long> entry = it.next();
                    Location loc = entry.getKey();
                    long createdAt = entry.getValue();

                    if (loc.getWorld() == null) {
                        it.remove();
                        changed = true;
                        continue;
                    }

                    Block block = loc.getBlock();

                    // Si le bloc n'existe plus, on le retire
                    if (block.getType() == Material.AIR) {
                        it.remove();
                        changed = true;
                        continue;
                    }

                    // Si le bloc est redevenu stable, on le retire et on arrête d'afficher les
                    // particules
                    if (BridgeFallsListener.isBlockSupported(block)) {
                        it.remove();
                        changed = true;
                        continue;
                    }

                    // Si le délai est écoulé, on déclenche la chute du bloc
                    if (fallDelayMillis > 0 && now - createdAt >= fallDelayMillis) {
                        BridgeFallsListener.startFalling(block);
                        it.remove();
                        changed = true;
                        continue;
                    }

                    // Toujours instable : on affiche/rafraîchit son contour de particules rouges
                    BridgeFallsListener.showRedOutline(block);
                }
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
