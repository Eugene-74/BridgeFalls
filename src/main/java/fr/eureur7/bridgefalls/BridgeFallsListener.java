package fr.eureur7.bridgefalls;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Color;
import org.bukkit.World;
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
        int blockHeight = block.getY();
        Material placedType = block.getType();

        // Certains blocs n'ont jamais besoin de structure
        if (plugin.isAlwaysStable(placedType)) {
            player.sendMessage(plugin.getMessage("block.always-stable-placed"));
            return;
        }

        int radius = plugin.getSupportRadius();

        boolean hasDirectSupport = !plugin
                .isNoRestBlockVertical(block.getRelative(BlockFace.DOWN).getType());

        boolean hasIndirectSupport = !hasDirectSupport && hasSupportWithinDistance(block, radius);

        boolean hasTopSupport = false;
        int topRadius = plugin.getTopSupportRadius();

        player.sendMessage("§eSupport direct en dessous : " + (hasDirectSupport ? "oui" : "non"));
        player.sendMessage("§eSupport horizontal : " + (hasIndirectSupport ? "oui" : "non"));
        player.sendMessage("§eRayon de support topRadius : " + topRadius);

        if (!hasDirectSupport && !hasIndirectSupport && topRadius > 0) {
            hasTopSupport = hasTopSupportWithinDistance(block, topRadius);
            player.sendMessage("§eSupport vertical supérieur : " + (hasTopSupport ? "oui" : "non"));
        }

        if (!hasDirectSupport && !hasIndirectSupport && !hasTopSupport) {
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
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("height", String.valueOf(blockHeight));
            player.sendMessage(plugin.getMessage("block.height-ok", placeholders));
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

        // Support direct par en dessous
        Material belowType = block.getRelative(BlockFace.DOWN).getType();
        boolean hasDirectSupport = !plugin.isNoRestBlockVertical(belowType);
        if (hasDirectSupport) {
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

            if (!BridgeFallsPlugin.getInstance()
                    .isNoRestBlockVertical(current.getRelative(BlockFace.DOWN).getType())) {
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

                if (BridgeFallsPlugin.getInstance().isNoRestBlockHorizontal(next.getType())) {
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
                Material belowType = current.getRelative(BlockFace.DOWN).getType();
                if (!BridgeFallsPlugin.getInstance().isNoRestBlockVertical(belowType)) {
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

                if (BridgeFallsPlugin.getInstance().isNoRestBlockHorizontal(next.getType())) {
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

            if (distance > 0 && !BridgeFallsPlugin.getInstance().isNoRestBlockHorizontal(current.getType())) {
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

                if (BridgeFallsPlugin.getInstance().isNoRestBlockHorizontal(next.getType())) {
                    continue;
                }

                toVisit.add(next);
                distances.put(next, distance + 1);
            }
        }

        return false;
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
                        }

                        plugin.addUnstableBlock(loc);
                        showRedOutline(candidate);
                    }
                }
            }
        }

        if (player != null && newlyUnstableCount > 0) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("count", String.valueOf(newlyUnstableCount));
            placeholders.put("plural", newlyUnstableCount > 1 ? "s" : "");
            player.sendMessage(plugin.getMessage("break.made-unstable", placeholders));
        }
    }

    public static void showRedOutline(Block block) {
        Particle.DustOptions red = new Particle.DustOptions(Color.RED, 1.2F);

        World world = block.getWorld();

        double minX = block.getX();
        double minY = block.getY();
        double minZ = block.getZ();
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        double step = 0.25;

        for (double x = minX; x <= maxX; x += step) {
            world.spawnParticle(Particle.DUST, x, minY, minZ, 1, 0, 0, 0, 0, red);
            world.spawnParticle(Particle.DUST, x, minY, maxZ, 1, 0, 0, 0, 0, red);
            world.spawnParticle(Particle.DUST, x, maxY, minZ, 1, 0, 0, 0, 0, red);
            world.spawnParticle(Particle.DUST, x, maxY, maxZ, 1, 0, 0, 0, 0, red);
        }

        for (double z = minZ; z <= maxZ; z += step) {
            world.spawnParticle(Particle.DUST, minX, minY, z, 1, 0, 0, 0, 0, red);
            world.spawnParticle(Particle.DUST, maxX, minY, z, 1, 0, 0, 0, 0, red);
            world.spawnParticle(Particle.DUST, minX, maxY, z, 1, 0, 0, 0, 0, red);
            world.spawnParticle(Particle.DUST, maxX, maxY, z, 1, 0, 0, 0, 0, red);
        }

        for (double y = minY; y <= maxY; y += step) {
            world.spawnParticle(Particle.DUST, minX, y, minZ, 1, 0, 0, 0, 0, red);
            world.spawnParticle(Particle.DUST, maxX, y, minZ, 1, 0, 0, 0, 0, red);
            world.spawnParticle(Particle.DUST, minX, y, maxZ, 1, 0, 0, 0, 0, red);
            world.spawnParticle(Particle.DUST, maxX, y, maxZ, 1, 0, 0, 0, 0, red);
        }

    }

    public static void startFalling(Block block) {
        World world = block.getWorld();

        if (block.getType() == Material.AIR) {
            return;
        }

        world.playSound(block.getLocation(), org.bukkit.Sound.BLOCK_WOOD_BREAK, 1.0F, 1.0F);

        world.spawn(
                block.getLocation().add(0.5, 0, 0.5),
                FallingBlock.class,
                fb -> {
                    fb.setBlockData(block.getBlockData());
                    fb.setDropItem(false);
                    fb.setHurtEntities(false);
                });

        block.setType(Material.AIR);

        int radius = BridgeFallsPlugin.getInstance().getSupportRadius();
        // pas de joueur associé ici, on ne spamme pas de message
        checkAndHighlightUnsupportedBlocksAround(block, radius, null);
    }
}
