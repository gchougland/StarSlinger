package com.hexvane.starslinger.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Utility class for placing Astral Tethers in a field around an impact point.
 * Uses Poisson disk sampling for even distribution.
 */
public class AstralTetherPlacer {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float FIELD_RADIUS = 15.0f; // Radius of the star node field (increased to accommodate larger spacing)
    private static final float MIN_DISTANCE = 10.0f; // Minimum distance between star nodes (about 10 blocks apart)
    private static final int MAX_ATTEMPTS = 30; // Max attempts to place each node
    private static final int TARGET_NODE_COUNT = 12; // Target number of star nodes

    /**
     * Generates a field of Astral Tethers around the given impact position.
     * 
     * @param world World instance
     * @param centerX Center X coordinate
     * @param centerY Center Y coordinate
     * @param centerZ Center Z coordinate
     */
    public static void generateStarNodeField(World world, int centerX, int centerY, int centerZ) {
        DebugLogger.debugInfo(LOGGER, "[AstralTetherPlacer] Generating astral tether field at %d,%d,%d", centerX, centerY, centerZ);
        
        // Get Astral Tether item to get its block type
        Item starNodeItem = Item.getAssetMap().getAsset("Astral_Tether");
        if (starNodeItem == null || starNodeItem.getBlockId() == null) {
            LOGGER.atWarning().log("[AstralTetherPlacer] Astral Tether item not found or has no blockId");
            return; // Astral Tether item not found
        }
        
        String blockId = starNodeItem.getBlockId();
        DebugLogger.debugInfo(LOGGER, "[AstralTetherPlacer] Astral Tether blockId: %s", blockId);
        
        BlockType starNodeBlockType = BlockType.getAssetMap().getAsset(blockId);
        if (starNodeBlockType == null) {
            LOGGER.atWarning().log("[AstralTetherPlacer] Astral Tether block type not found for id: %s", blockId);
            return; // Block type not found
        }
        
        DebugLogger.debugInfo(LOGGER, "[AstralTetherPlacer] Astral Tether block type found: %s, hasSupport: %s", 
                starNodeBlockType.getId(), starNodeBlockType.hasSupport());
        
        Random random = new Random();
        List<Point> placedNodes = new ArrayList<>();
        
        // Try to place nodes using Poisson disk sampling
        int placedCount = 0;
        for (int i = 0; i < TARGET_NODE_COUNT; i++) {
            Point node = tryPlaceNode(random, centerX, centerY, centerZ, placedNodes, world);
            if (node != null) {
                placedNodes.add(node);
                // Place the block
                boolean placed = placeStarNodeBlock(world, node.x, node.y, node.z, starNodeBlockType);
                if (placed) {
                    placedCount++;
                }
            }
        }
        
        DebugLogger.debugInfo(LOGGER, "[AstralTetherPlacer] Placed %d/%d astral tethers", placedCount, TARGET_NODE_COUNT);
    }
    
    /**
     * Places a Astral Tether block at the specified coordinates.
     * @return true if block was placed, false otherwise
     */
    private static boolean placeStarNodeBlock(World world, int x, int y, int z, BlockType starNodeBlockType) {
        try {
            BlockType existingBlockType = ChunkSectionBlocks.blockType(world, x, y, z);
            if (existingBlockType == null) {
                LOGGER.atFine().log("[AstralTetherPlacer] Section not loaded for %d,%d,%d", x, y, z);
                return false;
            }
            if (existingBlockType.getMaterial() != BlockMaterial.Empty) {
                LOGGER.atFine().log("[AstralTetherPlacer] Position %d,%d,%d is not empty (block: %s)",
                        x, y, z, existingBlockType.getId());
                return false;
            }

            String blockId = starNodeBlockType.getId();
            int blockIndex = BlockType.getAssetMap().getIndex(blockId);
            if (blockIndex == Integer.MIN_VALUE) {
                LOGGER.atWarning().log("[AstralTetherPlacer] Block type %s not found in asset map", blockId);
                return false;
            }

            boolean placed = ChunkSectionBlocks.setBlock(
                    world,
                    x,
                    y,
                    z,
                    blockIndex,
                    starNodeBlockType,
                    RotationTuple.NONE_INDEX,
                    FillerBlockUtil.NO_FILLER,
                    SetBlockSettings.NO_SET_FILLER
                            | SetBlockSettings.NO_BREAK_FILLER
                            | SetBlockSettings.NO_UPDATE_HEIGHTMAP
            );

            if (placed) {
                DebugLogger.debugInfo(LOGGER, "[AstralTetherPlacer] Successfully placed Astral Tether at %d,%d,%d", x, y, z);
            } else {
                LOGGER.atWarning().log("[AstralTetherPlacer] Failed to place Astral Tether at %d,%d,%d", x, y, z);
            }

            return placed;
        } catch (Exception e) {
            LOGGER.atWarning().log("[AstralTetherPlacer] Exception placing block at %d,%d,%d: %s", x, y, z, e.getMessage());
            return false;
        }
    }

    /**
     * Attempts to place a single Astral Tether using Poisson disk sampling.
     */
    private static Point tryPlaceNode(
            Random random,
            int centerX,
            int centerY,
            int centerZ,
            List<Point> existingNodes,
            World world) {
        
        // Find ground level at impact point (first solid block below)
        int groundY = findGroundLevel(world, centerX, centerY, centerZ);
        int spawnYMin = groundY + 10; // 10 blocks above ground
        int spawnYMax = groundY + 20; // 20 blocks above ground
        
        DebugLogger.debugInfo(LOGGER, "[AstralTetherPlacer] Ground level: %d, spawning tethers between Y=%d and Y=%d", 
                groundY, spawnYMin, spawnYMax);
        
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            // Generate random angle and distance
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * FIELD_RADIUS;
            
            int x = centerX + (int) (Math.cos(angle) * distance);
            int y = spawnYMin + random.nextInt(spawnYMax - spawnYMin + 1); // Random Y between 10-20 blocks above ground
            int z = centerZ + (int) (Math.sin(angle) * distance);
            
            Point candidate = new Point(x, y, z);
            
            // Check if this position is valid (far enough from other nodes)
            boolean valid = true;
            for (Point existing : existingNodes) {
                double dist = Math.sqrt(
                        Math.pow(candidate.x - existing.x, 2) +
                        Math.pow(candidate.y - existing.y, 2) +
                        Math.pow(candidate.z - existing.z, 2)
                );
                if (dist < MIN_DISTANCE) {
                    valid = false;
                    break;
                }
            }
            
            if (valid && isValidPlacementPosition(world, x, y, z)) {
                return candidate;
            }
        }
        
        return null; // Failed to place after max attempts
    }
    
    /**
     * Finds the ground level (first solid block) below the given position.
     * Searches downward from the position until it finds a solid block.
     */
    private static int findGroundLevel(World world, int x, int startY, int z) {
        // Start searching from the impact Y position and go down
        // Search up to 50 blocks below to find ground
        int searchRange = 50;
        int minY = Math.max(0, startY - searchRange);
        
        for (int y = startY; y >= minY; y--) {
            try {
                BlockType blockType = ChunkSectionBlocks.blockType(world, x, y, z);
                if (blockType != null && blockType.getMaterial() != BlockMaterial.Empty) {
                    DebugLogger.debugInfo(LOGGER, "[AstralTetherPlacer] Found ground at Y=%d (solid block at Y=%d)", y + 1, y);
                    return y + 1;
                }
            } catch (Exception e) {
                // Continue searching
            }
        }
        
        // If no ground found, use the impact Y position as fallback
        LOGGER.atWarning().log("[AstralTetherPlacer] Could not find ground level, using impact Y=%d", startY);
        return startY;
    }
    
    /**
     * Checks if a position is valid for placing a Astral Tether (air block or replaceable).
     */
    private static boolean isValidPlacementPosition(World world, int x, int y, int z) {
        try {
            BlockType blockType = ChunkSectionBlocks.blockType(world, x, y, z);
            if (blockType == null) {
                return false;
            }
            return blockType.getMaterial() == BlockMaterial.Empty;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Simple point class for node placement.
     */
    private static class Point {
        final int x, y, z;
        
        Point(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
