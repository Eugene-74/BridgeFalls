package fr.eureur7.bridgefalls;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.entity.FallingBlock;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class BridgeFallsListener implements Listener {
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!BridgeFallsPlugin.getInstance().isBridgeFallsEnabled()) {
            return;
        }

        Block brokenBlock = event.getBlock();
        Player player = event.getPlayer();

        // On attend 1 tick pour que le monde soit mis à jour (bloc vraiment cassé)
        BridgeFallsPlugin.getInstance().getServer().getScheduler().runTaskLater(BridgeFallsPlugin.getInstance(), () -> {
            int radius = BridgeFallsPlugin.getInstance().getSupportRadius();
            checkAndHighlightUnsupportedBlocksAround(brokenBlock, radius, player);
        }, 1L);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!BridgeFallsPlugin.getInstance().isBridgeFallsEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        Block block = event.getBlock();
        Material placedType = block.getType();

        // Certains blocs n'ont jamais besoin de structure
        if (plugin.isAlwaysStable(placedType)) {
            player.sendMessage(plugin.getMessage("block.always-stable-placed"));
            return;
        }

        int radius = plugin.getSupportRadius();

        Block belowBlock = block.getRelative(BlockFace.DOWN);
        boolean hasDirectSupport = hasDirectVerticalSupport(belowBlock);

        boolean hasIndirectSupport = !hasDirectSupport && hasSupportWithinDistance(block, radius);

        boolean hasTopSupport = false;
        int topRadius = plugin.getTopSupportRadius();

        if (!hasDirectSupport && !hasIndirectSupport && topRadius > 0) {
            hasTopSupport = hasTopSupportWithinDistance(block, topRadius);
        }

        // Messages d'information détaillés uniquement si debug=true dans la config
        if (plugin.getConfig().getBoolean("debug", false)) {
            Map<String, String> infoPlaceholders = new HashMap<>();
            infoPlaceholders.put("block", placedType.name().toLowerCase());
            infoPlaceholders.put("supportRadius", String.valueOf(radius));
            infoPlaceholders.put("topSupportRadius", String.valueOf(topRadius));
            infoPlaceholders.put("direct", hasDirectSupport ? "oui" : "non");
            infoPlaceholders.put("horizontal", hasIndirectSupport ? "oui" : "non");
            infoPlaceholders.put("top", hasTopSupport ? "oui" : "non");

            player.sendMessage(plugin.getMessage("block.place.info.block", infoPlaceholders));
            player.sendMessage(plugin.getMessage("block.place.info.direct", infoPlaceholders));
            player.sendMessage(plugin.getMessage("block.place.info.horizontal", infoPlaceholders));
            if (topRadius > 0) {
                player.sendMessage(plugin.getMessage("block.place.info.top", infoPlaceholders));
            }
        }

        if (!hasDirectSupport && !hasIndirectSupport && !hasTopSupport) {
            // Selon la config, soit on interdit la pose de blocs instables,
            // soit on les autorise mais ils deviennent instables.
            playUnstableDenySound(player);

            if (plugin.isAllowPlacingUnstableBlocks()) {
                plugin.addUnstableBlock(block.getLocation());

                Map<String, String> ph = new HashMap<>();
                ph.put("block", placedType.name().toLowerCase());
                double minutes = plugin.getConfig().getDouble("fall-delay-minutes", 1.0D);
                ph.put("minutes", String.valueOf(minutes));

                player.sendMessage(plugin.getMessage("block.place.marked-unstable", ph));
                return;
            }

            block.setType(Material.AIR);
            event.setCancelled(true);
            Material below = block.getRelative(BlockFace.DOWN).getType();

            Map<String, String> headerPlaceholders = new HashMap<>();
            headerPlaceholders.put("block", placedType.name().toLowerCase());
            headerPlaceholders.put("x", String.valueOf(block.getX()));
            headerPlaceholders.put("y", String.valueOf(block.getY()));
            headerPlaceholders.put("z", String.valueOf(block.getZ()));
            String reasonPrefix = plugin.getMessage("block.place.error.header", headerPlaceholders);

            String reasonCore;
            Map<String, String> belowPlaceholder = new HashMap<>();
            belowPlaceholder.put("below", below.name().toLowerCase());

            if (below == Material.AIR) {
                reasonCore = plugin.getMessage("block.place.error.no-below");
            } else if (plugin.isNoRestBlockVertical(below)) {
                reasonCore = plugin.getMessage("block.place.error.below-no-support", belowPlaceholder);
            } else {
                reasonCore = plugin.getMessage("block.place.error.below-invalid", belowPlaceholder);
            }

            boolean hasHorizontalStructure = hasHorizontalStructureWithinDistance(block, radius);
            String reasonHorizontal;
            if (!hasHorizontalStructure) {
                Map<String, String> radiusPlaceholder = new HashMap<>();
                radiusPlaceholder.put("radius", String.valueOf(radius));
                reasonHorizontal = plugin.getMessage("block.place.error.no-horizontal", radiusPlaceholder);
            } else {
                reasonHorizontal = plugin.getMessage("block.place.error.weak-horizontal");
            }

            player.sendMessage("§c" + reasonPrefix + reasonCore + reasonHorizontal);
        }
    }

    public static boolean isBlockSupported(Block block) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        // Un bloc marqué comme toujours stable est forcément supporté
        if (plugin.isAlwaysStable(block.getType())) {
            return true;
        }

        if (isBlockSupportedByBelowOrHorizontal(block)) {
            return true;
        }

        int topRadius = plugin.getTopSupportRadius();
        return topRadius > 0 && hasTopSupportWithinDistance(block, topRadius);
    }

    private static boolean isBlockSupportedByBelowOrHorizontal(Block block) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        // Un bloc d'air ne peut jamais être considéré comme supporté
        // pour servir de "support par le haut".
        if (block.getType() == Material.AIR) {
            return false;
        }

        // Support direct par en dessous (en tenant compte des blocs flottants
        // et des "vrais" piliers qui vont jusqu'au sol)
        if (hasDirectVerticalSupport(block)) {
            return true;
        }

        // Sinon, support indirect via un réseau horizontal
        int radius = plugin.getSupportRadius();
        return hasSupportWithinDistance(block, radius);
    }

    private static boolean hasSupportWithinDistance(Block startBlock, int maxDistance) {
        BlockFace[] horizontalFaces = new BlockFace[] {
                BlockFace.NORTH,
                BlockFace.SOUTH,
                BlockFace.EAST,
                BlockFace.WEST
        };

        Queue<Block> toVisit = new LinkedList<>();
        Map<Block, Integer> distances = new HashMap<>();
        Set<Block> visited = new HashSet<>();

        toVisit.add(startBlock);
        distances.put(startBlock, 0);
        visited.add(startBlock);

        while (!toVisit.isEmpty()) {
            Block current = toVisit.poll();
            int distance = distances.get(current);

            if (distance > maxDistance) {
                continue;
            }

            if (hasDirectVerticalSupport(current)) {
                return true;
            }

            if (distance == maxDistance) {
                continue;
            }

            for (BlockFace face : horizontalFaces) {
                Block next = current.getRelative(face);

                if (visited.contains(next)) {
                    continue;
                }

                visited.add(next);

                if (!BridgeFallsPlugin.getInstance().isHorizontalSupportProvider(next.getType())) {
                    continue;
                }

                toVisit.add(next);
                distances.put(next, distance + 1);
            }
        }

        return false;
    }

    private static boolean hasTopSupportWithinDistance(Block startBlock, int maxUp) {
        if (maxUp <= 0) {
            return false;
        }

        for (int dy = 1; dy <= maxUp; dy++) {
            Block above = startBlock.getRelative(0, dy, 0);

            // Stop if we encounter air or no-rest blocks
            if (above.getType() == Material.AIR ||
                    BridgeFallsPlugin.getInstance().isNoRestBlockVertical(above.getType())) {
                break;
            }

            int horizontalRadius = BridgeFallsPlugin.getInstance().getSupportRadius();
            if (hasHorizontalSupportFromAbove(startBlock, above, horizontalRadius)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasHorizontalSupportFromAbove(Block origin, Block startAbove, int maxDistance) {
        if (maxDistance <= 0) {
            return false;
        }

        BlockFace[] horizontalFaces = new BlockFace[] {
                BlockFace.NORTH,
                BlockFace.SOUTH,
                BlockFace.EAST,
                BlockFace.WEST
        };

        Queue<Block> toVisit = new LinkedList<>();
        Map<Block, Integer> distances = new HashMap<>();
        Set<Block> visited = new HashSet<>();

        toVisit.add(startAbove);
        distances.put(startAbove, 0);
        visited.add(startAbove);

        while (!toVisit.isEmpty()) {
            Block current = toVisit.poll();
            int distance = distances.get(current);

            if (distance > maxDistance) {
                continue;
            }

            // On ne valide un support vertical qu'après au moins
            // 1 pas horizontal pour éviter de compter le bloc
            // d'origine comme seul support.
            if (distance > 0) {
                if (hasDirectVerticalSupport(current)) {
                    return true;
                }
            }

            if (distance == maxDistance) {
                continue;
            }

            for (BlockFace face : horizontalFaces) {
                Block next = current.getRelative(face);

                if (visited.contains(next)) {
                    continue;
                }

                visited.add(next);

                if (!BridgeFallsPlugin.getInstance().isHorizontalSupportProvider(next.getType())) {
                    continue;
                }

                toVisit.add(next);
                distances.put(next, distance + 1);
            }
        }

        return false;
    }

    private static boolean hasHorizontalStructureWithinDistance(Block startBlock, int maxDistance) {
        BlockFace[] horizontalFaces = new BlockFace[] {
                BlockFace.NORTH,
                BlockFace.SOUTH,
                BlockFace.EAST,
                BlockFace.WEST
        };

        Queue<Block> toVisit = new LinkedList<>();
        Map<Block, Integer> distances = new HashMap<>();
        Set<Block> visited = new HashSet<>();

        toVisit.add(startBlock);
        distances.put(startBlock, 0);
        visited.add(startBlock);

        while (!toVisit.isEmpty()) {
            Block current = toVisit.poll();
            int distance = distances.get(current);

            if (distance > maxDistance) {
                continue;
            }

            if (distance > 0 && BridgeFallsPlugin.getInstance().isHorizontalSupportProvider(current.getType())) {
                return true;
            }

            if (distance == maxDistance) {
                continue;
            }

            for (BlockFace face : horizontalFaces) {
                Block next = current.getRelative(face);

                if (visited.contains(next)) {
                    continue;
                }

                visited.add(next);

                if (!BridgeFallsPlugin.getInstance().isHorizontalSupportProvider(next.getType())) {
                    continue;
                }

                toVisit.add(next);
                distances.put(next, distance + 1);
            }
        }

        return false;
    }

    private static boolean hasDirectVerticalSupport(Block block) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        Material blockType = block.getType();

        Block below = block.getRelative(BlockFace.DOWN);
        Material belowType = below.getType();

        if (plugin.isFloatingSupport(blockType) && belowType == Material.WATER) {
            return true;
        }

        if (!plugin.isNoRestBlockVertical(belowType) && belowType != Material.AIR) {
            return true;
        }

        int maxDepth = plugin.getTopSupportRadius();
        if (maxDepth <= 0) {
            // Fallback : comportement standard si le rayon vertical est désactivé
            return !plugin.isNoRestBlockVertical(belowType) && belowType != Material.AIR;
        }

        // On demande un pilier d'épaisseur 2 * top-support-radius
        int requiredDepth = maxDepth * 2;

        Block current = block;
        for (int i = 0; i < requiredDepth; i++) {
            Block belowBlock = current.getRelative(BlockFace.DOWN);
            Material mat = belowBlock.getType();

            // Si on atteint un bloc flottant reposant sur l'eau, on considère
            // que le pilier est valide, même si on n'a pas encore parcouru
            // toute la profondeur requise.
            if (plugin.isFloatingSupport(mat)
                    && belowBlock.getRelative(BlockFace.DOWN).getType() == Material.WATER) {
                return true;
            }

            if (mat == Material.AIR || plugin.isNoRestBlockVertical(mat)) {
                return false;
            }

            current = belowBlock;
        }

        // On a trouvé une colonne continue suffisante :
        // on considère cela comme un pilier.
        return true;
    }

    private static void checkAndHighlightUnsupportedBlocksAround(Block origin, int radius, Player player) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();
        Set<Location> alreadyUnstable = plugin.getUnstableBlocks();
        int newlyUnstableCount = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block candidate = origin.getWorld().getBlockAt(
                            origin.getX() + dx,
                            origin.getY() + dy,
                            origin.getZ() + dz);

                    Material type = candidate.getType();
                    if (type == Material.AIR || plugin.isAlwaysStable(type)) {
                        continue;
                    }

                    if (!isBlockSupported(candidate)) {
                        Location loc = candidate.getLocation();
                        if (!alreadyUnstable.contains(loc)) {
                            newlyUnstableCount++;
                            playUnstableDenySound(loc);
                        }

                        plugin.addUnstableBlock(loc);
                        showRedOutline(candidate);
                        playUnstableDenySound(candidate.getLocation());
                    }
                }
            }
        }

        if (player != null && newlyUnstableCount > 0) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("count", String.valueOf(newlyUnstableCount));
            placeholders.put("plural", newlyUnstableCount > 1 ? "s" : "");
            placeholders.put("radius", String.valueOf(radius));
            player.sendMessage(plugin.getMessage("break.made-unstable", placeholders));
        }
    }

    public static void showRedOutline(Block block) {
        showColoredOutline(block, Color.RED);
    }

    public static void showColoredOutline(Block block, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.2F);

        World world = block.getWorld();

        double minX = block.getX();
        double minY = block.getY();
        double minZ = block.getZ();
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        double step = 0.25;

        for (double x = minX; x <= maxX; x += step) {
            world.spawnParticle(Particle.DUST, x, minY, minZ, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, x, minY, maxZ, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, x, maxY, minZ, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, x, maxY, maxZ, 1, 0, 0, 0, 0, dust);
        }

        for (double z = minZ; z <= maxZ; z += step) {
            world.spawnParticle(Particle.DUST, minX, minY, z, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, maxX, minY, z, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, minX, maxY, z, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, maxX, maxY, z, 1, 0, 0, 0, 0, dust);
        }

        for (double y = minY; y <= maxY; y += step) {
            world.spawnParticle(Particle.DUST, minX, y, minZ, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, maxX, y, minZ, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, minX, y, maxZ, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, maxX, y, maxZ, 1, 0, 0, 0, 0, dust);
        }

    }

    public static void startFalling(Block block) {
        World world = block.getWorld();

        if (block.getType() == Material.AIR) {
            return;
        }

        world.playSound(block.getLocation(), org.bukkit.Sound.BLOCK_WOOD_BREAK, 1.0F, 1.0F);

        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        world.spawn(block.getLocation().add(0.5, 0, 0.5), FallingBlock.class, fb -> {
            fb.setBlockData(block.getBlockData());
            fb.setDropItem(plugin.isFallingBlockDropItem());
            fb.setHurtEntities(plugin.isFallingBlockHurtEntities());
        });

        block.setType(Material.AIR);

        int radius = BridgeFallsPlugin.getInstance().getSupportRadius();

        // pas de joueur associé ici, on ne spamme pas de message
        checkAndHighlightUnsupportedBlocksAround(block, radius, null);
    }

    private static void playUnstableDenySound(Player player) {
        // Petit son "refus" quand un bloc devient instable
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.8F);
    }

    private static void playUnstableDenySound(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        // Petit son "refus" quand un bloc devient instable
        loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.8F);
    }

    public static void playRedPhaseWarningSound(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        // Son d'alerte discret autour d'un bloc très instable (phase rouge)
        loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5F, 1.8F);
    }
}
