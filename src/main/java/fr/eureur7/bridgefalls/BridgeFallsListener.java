package fr.eureur7.bridgefalls;

import org.bukkit.Bukkit;
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
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class BridgeFallsListener implements Listener {
    private static final int RECHECK_CELL_SIZE = 16;
    private static final long RECHECK_DEBOUNCE_MS = 300L;
    private static final int MAX_RECHECK_ORIGINS_PER_EVENT = 32;
    private static final int MAX_RECHECK_TRACKED_CELLS = 30_000;
    private static final Map<Long, Long> recentRechecksByCell = new ConcurrentHashMap<>();
    private static final Map<Long, StabilityRecheckRequest> pendingRechecksByKey = new ConcurrentHashMap<>();
    private static final Queue<StabilityRecheckRequest> pendingRecheckQueue = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean recheckDrainScheduled = new AtomicBoolean(false);

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!BridgeFallsPlugin.getInstance().isBridgeFallsEnabled()) {
            return;
        }

        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();
        Player player = event.getPlayer();
        if (plugin.isGamemodeDisabled(player.getGameMode())) {
            return;
        }

        scheduleStabilityRecheck(event.getBlock(), player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!BridgeFallsPlugin.getInstance().isBridgeFallsEnabled()) {
            return;
        }

        scheduleStabilityRecheck(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!BridgeFallsPlugin.getInstance().isBridgeFallsEnabled()) {
            return;
        }

        scheduleStabilityRecheck(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!BridgeFallsPlugin.getInstance().isBridgeFallsEnabled()) {
            return;
        }

        scheduleStabilityRecheck(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (!BridgeFallsPlugin.getInstance().isBridgeFallsEnabled()) {
            return;
        }

        scheduleStabilityRecheck(event.getBlock());
    }

    private void scheduleStabilityRecheck(Block brokenBlock) {
        scheduleStabilityRecheck(brokenBlock, null);
    }

    private void scheduleStabilityRecheck(Collection<Block> brokenBlocks) {
        if (brokenBlocks == null || brokenBlocks.isEmpty()) {
            return;
        }

        int scheduled = 0;
        for (Block brokenBlock : brokenBlocks) {
            if (scheduled >= MAX_RECHECK_ORIGINS_PER_EVENT) {
                break;
            }

            if (scheduleStabilityRecheck(brokenBlock, null)) {
                scheduled++;
            }
        }

        if (scheduled < brokenBlocks.size()) {
            BridgeFallsPlugin.log("Throttled rechecks after mass break: scheduled=" + scheduled
                    + " totalBroken=" + brokenBlocks.size());
        }
    }

    private boolean scheduleStabilityRecheck(Block brokenBlock, Player player) {
        if (brokenBlock == null) {
            return false;
        }

        Location brokenLocation = brokenBlock.getLocation();
        if (!shouldScheduleRecheckForLocation(brokenLocation)) {
            return false;
        }

        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();
        int maxQueueSize = plugin.getRecheckQueueMaxSize();
        if (pendingRecheckQueue.size() >= maxQueueSize) {
            BridgeFallsPlugin.log("Recheck queue full, dropping recheck at " + brokenLocation);
            return false;
        }

        long key = packRecheckOrigin(brokenLocation);
        UUID playerId = player != null ? player.getUniqueId() : null;
        StabilityRecheckRequest request = new StabilityRecheckRequest(key, brokenLocation, playerId);

        StabilityRecheckRequest existing = pendingRechecksByKey.putIfAbsent(key, request);
        if (existing != null) {
            return false;
        }

        pendingRecheckQueue.offer(request);
        ensureRecheckDrainScheduled();
        return true;
    }

    private void ensureRecheckDrainScheduled() {
        if (!recheckDrainScheduled.compareAndSet(false, true)) {
            return;
        }

        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();
        plugin.getServer().getGlobalRegionScheduler().run(
                plugin,
                task -> drainQueuedStabilityRechecks());
    }

    private void drainQueuedStabilityRechecks() {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();
        int maxRechecksPerDrain = plugin.getRecheckDrainBatchSize();
        int processed = 0;

        while (processed < maxRechecksPerDrain) {
            StabilityRecheckRequest request = pendingRecheckQueue.poll();
            if (request == null) {
                break;
            }

            pendingRechecksByKey.remove(request.key);

            Location location = request.location;
            if (location == null || location.getWorld() == null) {
                continue;
            }

            UUID playerId = request.playerId;
            plugin.getServer().getRegionScheduler().runDelayed(
                    plugin,
                    location,
                    task -> {
                        Player player = playerId != null ? Bukkit.getPlayer(playerId) : null;
                        int radius = plugin.getSupportRadius();
                        checkAndHighlightUnsupportedBlocksAround(location.getBlock(), radius, player);
                    },
                    1L);

            processed++;
        }

        if (!pendingRecheckQueue.isEmpty()) {
            plugin.getServer().getGlobalRegionScheduler().run(
                    plugin,
                    task -> drainQueuedStabilityRechecks());
            return;
        }

        recheckDrainScheduled.set(false);

        if (!pendingRecheckQueue.isEmpty()) {
            ensureRecheckDrainScheduled();
        }
    }

    private static long packRecheckOrigin(Location location) {
        UUID worldId = location.getWorld().getUID();
        int combined = Objects.hash(
                worldId,
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
        return combined & 0xFFFFFFFFL;
    }

    private static final class StabilityRecheckRequest {
        private final long key;
        private final Location location;
        private final UUID playerId;

        private StabilityRecheckRequest(long key, Location location, UUID playerId) {
            this.key = key;
            this.location = location;
            this.playerId = playerId;
        }
    }

    private static boolean shouldScheduleRecheckForLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        long cellKey = packRecheckCell(location);
        Long last = recentRechecksByCell.get(cellKey);
        if (last != null && now - last < RECHECK_DEBOUNCE_MS) {
            return false;
        }

        recentRechecksByCell.put(cellKey, now);

        if (recentRechecksByCell.size() > MAX_RECHECK_TRACKED_CELLS) {
            long cutoff = now - (RECHECK_DEBOUNCE_MS * 8L);
            recentRechecksByCell.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }

        return true;
    }

    private static long packRecheckCell(Location location) {
        UUID worldId = location.getWorld().getUID();
        int cellX = Math.floorDiv(location.getBlockX(), RECHECK_CELL_SIZE);
        int cellY = Math.floorDiv(location.getBlockY(), RECHECK_CELL_SIZE);
        int cellZ = Math.floorDiv(location.getBlockZ(), RECHECK_CELL_SIZE);

        int worldHash = worldId.hashCode();
        int combined = 31 * (31 * (31 * worldHash + cellX) + cellY) + cellZ;
        return combined & 0xFFFFFFFFL;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!BridgeFallsPlugin.getInstance().isBridgeFallsEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        if (plugin.isGamemodeDisabled(player.getGameMode())) {
            return;
        }

        Block block = event.getBlock();
        Material placedType = block.getType();

        if (plugin.isAlwaysStable(placedType)) {
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

            player.sendMessage("§c" + reasonPrefix + reasonCore + reasonHorizontal + "\n" + delayInfo);
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
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();
        Material sourceBlockType = startBlock.getType();

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

                if (!isValidHorizontalSupport(next.getType(), sourceBlockType, plugin)) {
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

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block blockInGrid = startBlock.getRelative(dx, dy, dz);

                    if (blockInGrid.getType() != Material.AIR &&
                            !BridgeFallsPlugin.getInstance().isNoRestBlockVertical(blockInGrid.getType())) {

                        int horizontalRadius = BridgeFallsPlugin.getInstance().getSupportRadius();
                        if (hasHorizontalSupportFromAbove(startBlock, blockInGrid, horizontalRadius)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean hasHorizontalSupportFromAbove(Block origin, Block startAbove, int maxDistance) {
        if (maxDistance <= 0) {
            return false;
        }

        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();
        Material sourceBlockType = origin.getType();

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

                if (!isValidHorizontalSupport(next.getType(), sourceBlockType, plugin)) {
                    continue;
                }

                toVisit.add(next);
                distances.put(next, distance + 1);
            }
        }

        return false;
    }

    private static boolean hasHorizontalStructureWithinDistance(Block startBlock, int maxDistance) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();
        Material sourceBlockType = startBlock.getType();

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

            if (distance > 0 && isValidHorizontalSupport(current.getType(), sourceBlockType, plugin)) {
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

                if (!isValidHorizontalSupport(next.getType(), sourceBlockType, plugin)) {
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
        boolean blockCanUseLeafSupport = canUseLeafSupport(blockType);

        Block below = block.getRelative(BlockFace.DOWN);
        Material belowType = below.getType();

        if (isLeafBlock(belowType)) {
            return true;
        }

        if (plugin.isFloatingSupport(blockType) && belowType == Material.WATER) {
            return true;
        }

        // Check 3x3 grid below for at least one solid block
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block checkBlock = block.getRelative(dx, -1, dz);
                Material supportType = checkBlock.getType();
                if (supportType == Material.AIR || plugin.isNoRestBlockVertical(supportType)) {
                    continue;
                }

                if (isLeafBlock(supportType) && !blockCanUseLeafSupport) {
                    continue;
                }

                return true;
            }
        }

        return false;
    }

    private static boolean canUseLeafSupport(Material material) {
        return material != null && material.name().contains("LOG");
    }

    private static boolean isLeafBlock(Material material) {
        return material != null && material.name().endsWith("_LEAVES");
    }

    private static boolean isValidHorizontalSupport(Material supportType, Material sourceBlockType,
            BridgeFallsPlugin plugin) {
        if (!plugin.isHorizontalSupportProvider(supportType)) {
            return false;
        }

        if (isLeafBlock(supportType) && !canUseLeafSupport(sourceBlockType)) {
            return false;
        }

        return true;
    }

    private static long packBlockPos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (long) (y & 0xFFF);
    }

    private static int unpackBlockPosX(long packed) {
        return (int) (packed >> 38);
    }

    private static int unpackBlockPosY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    private static int unpackBlockPosZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    private static boolean finishHasAnchor(boolean result, long startNanos, Block block, int radius) {
        long elapsedNanos = System.nanoTime() - startNanos;
        double elapsedMs = elapsedNanos / 1_000_000.0D;

        String blockLocation = (block == null || block.getWorld() == null)
                ? "null"
                : block.getWorld().getName() + ":" + block.getX() + "," + block.getY() + "," + block.getZ();

        BridgeFallsPlugin.log(String.format(
                "hasAnchor result=%s radius=%d timeMs=%.3f block=%s",
                result,
                radius,
                elapsedMs,
                blockLocation));

        return result;
    }

    static boolean hasAnchor(Block block, int radius) {
        long startNanos = System.nanoTime();

        if (block == null || block.getType() == Material.AIR) {
            return finishHasAnchor(false, startNanos, block, radius);
        }

        if (radius <= 0) {
            return finishHasAnchor(true, startNanos, block, radius);
        }

        int originX = block.getX();
        int originY = block.getY();
        int originZ = block.getZ();
        int radiusSquared = radius * radius;

        World world = block.getWorld();
        if (world == null) {
            return finishHasAnchor(false, startNanos, block, radius);
        }

        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;

        ArrayDeque<Long> toVisit = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();

        long origin = packBlockPos(originX, originY, originZ);
        visited.add(origin);

        long current = origin;
        while (true) {
            int currentX = unpackBlockPosX(current);
            int currentY = unpackBlockPosY(current);
            int currentZ = unpackBlockPosZ(current);

            long bestNext = Long.MIN_VALUE;
            int bestNextDistanceSquared = -1;

            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetY = -1; offsetY <= 1; offsetY++) {
                    for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                        if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
                            continue;
                        }

                        int nextX = currentX + offsetX;
                        int nextY = currentY + offsetY;
                        int nextZ = currentZ + offsetZ;

                        if (nextY < minY || nextY > maxY) {
                            continue;
                        }

                        if (world.getBlockAt(nextX, nextY, nextZ).getType() == Material.AIR) {
                            continue;
                        }

                        int dx = nextX - originX;
                        int dy = nextY - originY;
                        int dz = nextZ - originZ;
                        int distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);
                        if (distanceSquared > radiusSquared) {
                            return finishHasAnchor(true, startNanos, block, radius);
                        }

                        long nextPacked = packBlockPos(nextX, nextY, nextZ);
                        if (visited.contains(nextPacked)) {
                            continue;
                        }

                        if (distanceSquared > bestNextDistanceSquared) {
                            bestNextDistanceSquared = distanceSquared;
                            bestNext = nextPacked;
                        }
                    }
                }
            }

            if (bestNext == Long.MIN_VALUE) {
                break;
            }

            visited.add(bestNext);
            current = bestNext;
        }

        toVisit.addAll(visited);

        while (!toVisit.isEmpty()) {
            long currentNode = toVisit.poll();

            int currentX = unpackBlockPosX(currentNode);
            int currentY = unpackBlockPosY(currentNode);
            int currentZ = unpackBlockPosZ(currentNode);

            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetY = -1; offsetY <= 1; offsetY++) {
                    for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                        if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
                            continue;
                        }

                        int nextX = currentX + offsetX;
                        int nextY = currentY + offsetY;
                        int nextZ = currentZ + offsetZ;

                        if (nextY < minY || nextY > maxY) {
                            continue;
                        }

                        int dx = nextX - originX;
                        int dy = nextY - originY;
                        int dz = nextZ - originZ;
                        int distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);

                        if (world.getBlockAt(nextX, nextY, nextZ).getType() == Material.AIR) {
                            continue;
                        }

                        if (distanceSquared > radiusSquared) {
                            return finishHasAnchor(true, startNanos, block, radius);
                        }

                        long nextPacked = packBlockPos(nextX, nextY, nextZ);
                        if (!visited.add(nextPacked)) {
                            continue;
                        }

                        toVisit.add(nextPacked);
                    }
                }
            }
        }

        return finishHasAnchor(false, startNanos, block, radius);
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
                        boolean isAlreadyUnstable = alreadyUnstable.contains(loc);
                        if (!isAlreadyUnstable) {
                            newlyUnstableCount++;
                            playUnstableDenySound(loc);
                            plugin.addUnstableBlock(loc);
                            alreadyUnstable.add(loc);
                        }
                        BridgeFallsListener.showColoredOutline(candidate, BridgeFallsPlugin.defaultInstabilityColor);

                        if (!isAlreadyUnstable) {
                            playUnstableDenySound(candidate.getLocation());
                        }
                    } else {
                        if (plugin.isAlwaysStable(candidate.getType())) {
                            continue;
                        }

                        int anchorRadius = plugin.getAnchorSupportRadius();

                        int distance = plugin.getAnchorSupportRadiusCheckWhenBreaking();
                        if (dx <= -distance || dx >= distance || dy <= -distance || dy >= distance
                                || dz <= -distance || dz >= distance) {
                            continue;
                        }
                        boolean hasAnchor = hasAnchor(candidate, anchorRadius);
                        if (!hasAnchor) {
                            Location loc = candidate.getLocation();
                            boolean isAlreadyUnstable = alreadyUnstable.contains(loc);
                            if (!isAlreadyUnstable) {
                                newlyUnstableCount++;
                                playUnstableDenySound(loc);
                                plugin.addUnstableBlock(loc);
                                alreadyUnstable.add(loc);
                            }
                            showColoredOutline(candidate, BridgeFallsPlugin.defaultInstabilityColor);

                            if (!isAlreadyUnstable) {
                                playUnstableDenySound(candidate.getLocation());
                            }
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

    public static void showColoredOutline(Block block, Color color) {
        Color safeColor = color != null ? color : Color.BLUE;
        Particle.DustOptions dust = new Particle.DustOptions(safeColor, 1.2F);

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
