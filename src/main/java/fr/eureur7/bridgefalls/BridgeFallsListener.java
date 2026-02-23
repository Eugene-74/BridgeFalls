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

        if (plugin.isAlwaysStable(placedType)) {
            player.sendMessage(plugin.getMessage("block.always-stable-placed"));
            return;
        }

        int radius = plugin.getSupportRadius();

        boolean hasDirectSupport = hasDirectVerticalSupport(block);

        boolean hasIndirectSupport = !hasDirectSupport && hasSupportWithinDistance(block, radius);

        boolean hasTopSupport = false;
        int topRadius = plugin.getTopSupportRadius();

        if (!hasDirectSupport && !hasIndirectSupport && topRadius > 0) {
            hasTopSupport = hasTopSupportWithinDistance(block, topRadius);
        }
        int anchorRadius = plugin.getAnchorSupportRadius();
        boolean hasAnchor = hasAnchor(block, anchorRadius);

        boolean hasStructuralSupport = hasDirectSupport || hasIndirectSupport || hasTopSupport;

        if (!hasAnchor || !hasStructuralSupport) {
            playUnstableDenySound(player);

            if (plugin.isAllowPlacingUnstableBlocks()) {
                plugin.addUnstableBlock(block.getLocation());

                BridgeFallsPlugin.log("Player : " + player.getName() + " placed block " + block.getType() + " at "
                        + block.getLocation() + " witch is unstable");

            } else {
                block.setType(Material.AIR);
                event.setCancelled(true);
            }

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

            if (!hasAnchor) {
                Map<String, String> anchorPlaceholder = new HashMap<>();
                anchorPlaceholder.put("radius", String.valueOf(anchorRadius));
                reasonCore = plugin.getMessage("block.place.error.no-anchor", anchorPlaceholder);
                BridgeFallsPlugin.log("Player : " + player.getName() + " tried to place block " + block.getType()
                        + " at " + block.getLocation() + " but no anchor support was found");
            } else {
                if (below == Material.AIR) {
                    reasonCore = plugin.getMessage("block.place.error.no-below");
                    BridgeFallsPlugin
                            .log("Player : " + player.getName() + " tried to place block " + block.getType() + " at "
                                    + block.getLocation() + " but there is no block below");
                } else if (plugin.isNoRestBlockVertical(below)) {
                    reasonCore = plugin.getMessage("block.place.error.below-no-support", belowPlaceholder);
                    BridgeFallsPlugin
                            .log("Player : " + player.getName() + " tried to place block " + block.getType() + " at "
                                    + block.getLocation() + " but the block below is not a valid support");
                } else {
                    reasonCore = plugin.getMessage("block.place.error.below-invalid", belowPlaceholder);
                    BridgeFallsPlugin
                            .log("Player : " + player.getName() + " tried to place block " + block.getType() + " at "
                                    + block.getLocation() + " but the block below is not providing support");
                }
            }

            String reasonHorizontal = "";
            if (!hasStructuralSupport) {
                boolean hasHorizontalStructure = hasHorizontalStructureWithinDistance(block, radius);
                if (!hasHorizontalStructure) {
                    Map<String, String> radiusPlaceholder = new HashMap<>();
                    radiusPlaceholder.put("radius", String.valueOf(radius));
                    reasonHorizontal = plugin.getMessage("block.place.error.no-horizontal", radiusPlaceholder);
                    BridgeFallsPlugin
                            .log("Player : " + player.getName() + " tried to place block " + block.getType() + " at "
                                    + block.getLocation() + " but there is no horizontal support within radius");
                } else {
                    reasonHorizontal = plugin.getMessage("block.place.error.weak-horizontal");
                    BridgeFallsPlugin
                            .log("Player : " + player.getName() + " tried to place block " + block.getType() + " at "
                                    + block.getLocation() + " but there is only weak horizontal support within radius");
                }
            }

            String delayInfo = "";
            if (plugin.isAllowPlacingUnstableBlocks()) {
                Map<String, String> ph = new HashMap<>();
                ph.put("block", placedType.name().toLowerCase());
                double minutes = plugin.getConfig().getDouble("fall-delay-minutes", 1.0D);
                ph.put("minutes", String.valueOf(minutes));
                delayInfo = " " + plugin.getMessage("block.place.marked-unstable", ph);
            } else {
                delayInfo = "";
            }

            player.sendMessage("§c" + reasonPrefix + reasonCore + reasonHorizontal + delayInfo);
        }
    }

    public static boolean isBlockSupported(Block block) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

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

        if (block.getType() == Material.AIR) {
            return false;
        }

        if (hasDirectVerticalSupport(block)) {
            return true;
        }

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

        if (belowType == Material.AIR) {
            return false;
        }

        if (plugin.isNoRestBlockVertical(belowType)) {
            return false;
        }

        return true;
    }

    static boolean hasAnchor(Block block, int radius) {
        if (block == null || block.getType() == Material.AIR) {
            return false;
        }

        if (radius <= 0) {
            return true;
        }

        int originX = block.getX();
        int originY = block.getY();
        int originZ = block.getZ();
        int radiusSquared = radius * radius;

        BlockFace[] faces = new BlockFace[] {
                BlockFace.NORTH,
                BlockFace.SOUTH,
                BlockFace.EAST,
                BlockFace.WEST,
                BlockFace.UP,
                BlockFace.DOWN
        };

        Queue<Block> toVisit = new LinkedList<>();
        Set<Block> visited = new HashSet<>();

        toVisit.add(block);
        visited.add(block);

        while (!toVisit.isEmpty()) {
            Block current = toVisit.poll();

            int dx = current.getX() - originX;
            int dy = current.getY() - originY;
            int dz = current.getZ() - originZ;

            if ((dx * dx) + (dy * dy) + (dz * dz) > radiusSquared) {
                return true;
            }

            for (BlockFace face : faces) {
                Block next = current.getRelative(face);

                if (visited.contains(next)) {
                    continue;
                }

                if (next.getType() == Material.AIR) {
                    continue;
                }

                visited.add(next);
                toVisit.add(next);
            }
        }

        return false;
    }

    private static void checkAndHighlightUnsupportedBlocksAround(Block origin, int radius, Player player) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();
        Set<Location> alreadyUnstable = plugin.getUnstableBlocks();
        int newlyUnstableCount = 0;
        radius = radius / 2;
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
                    } else {
                        int anchorRadius = plugin.getAnchorSupportRadius();
                        if (dx <= -anchorRadius || dx >= anchorRadius || dy <= -anchorRadius || dy >= anchorRadius
                                || dz <= -anchorRadius || dz >= anchorRadius) {
                            continue;
                        }
                        boolean hasAnchor = hasAnchor(candidate, anchorRadius);
                        if (!hasAnchor) {
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

        int particlesPerEdge = 1;
        double step = 1.0 / particlesPerEdge;

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
        BridgeFallsPlugin.log("Block at " + block.getLocation() + " is starting to fall");

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

        checkAndHighlightUnsupportedBlocksAround(block, radius, null);
    }

    private static void playUnstableDenySound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.8F);
    }

    private static void playUnstableDenySound(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.8F);
    }

    public static void playRedPhaseWarningSound(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5F, 1.8F);
    }
}
