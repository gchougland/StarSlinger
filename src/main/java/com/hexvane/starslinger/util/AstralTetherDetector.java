package com.hexvane.starslinger.util;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Utility class for detecting Astral Tethers using sphere-swept raycast.
 * Casts a ray 20 blocks in length with a 5 block radius along the path.
 */
public class AstralTetherDetector {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float RAY_LENGTH = 20.0f;
    private static final float DETECTION_RADIUS = 3.0f;
    private static final float SAMPLE_INTERVAL = 0.5f;
    private static final String ASTRAL_TETHER_ITEM_ID = "Astral_Tether";

    /**
     * Finds the closest Astral Tether along a sphere-swept raycast from the player's position and look direction.
     * 
     * @param playerRef Reference to the player entity
     * @param startPos Starting position for the raycast (typically eye/hand position)
     * @param world World instance
     * @param lookDirection Look direction (yaw/pitch)
     * @param componentAccessor Component accessor for accessing components
     * @param isFromClient Whether the look direction is from client (degrees) or server (radians)
     * @return Position of the closest Astral Tether found, or null if none found
     */
    @Nullable
    public static Vector3d findClosestAstralTether(
            Ref<EntityStore> playerRef,
            Vector3d startPos,
            World world,
            Direction lookDirection,
            ComponentAccessor<EntityStore> componentAccessor,
            boolean isFromClient) {
        
        Vector3d playerPos = startPos;
        Vector3f direction = calculateLookDirection(lookDirection, isFromClient);
        
        DebugLogger.debugInfo(LOGGER, "[AstralTetherDetector] Starting search from player pos %.2f,%.2f,%.2f with direction %.2f,%.2f,%.2f", 
                playerPos.x, playerPos.y, playerPos.z, direction.x, direction.y, direction.z);
        
        // Sample points along the ray
        float closestDistance = Float.MAX_VALUE;
        Vector3d closestNodePos = null;
        
        int numSamples = (int) (RAY_LENGTH / SAMPLE_INTERVAL);
        DebugLogger.debugInfo(LOGGER, "[AstralTetherDetector] Sampling %d points along ray (length: %.2f, radius: %.2f)", 
                numSamples, RAY_LENGTH, DETECTION_RADIUS);
        
        // Don't spawn debug particles here - only spawn when a node is actually found
        // spawnDebugRayVisual(world, playerPos, direction, RAY_LENGTH, componentAccessor);
        
        int samplesChecked = 0;
        int nodesFound = 0;
        for (int i = 0; i <= numSamples; i++) {
            float t = i * SAMPLE_INTERVAL;
            Vector3d samplePoint = new Vector3d(
                playerPos.x + direction.x * t,
                playerPos.y + direction.y * t,
                playerPos.z + direction.z * t
            );
            
            // Check for star nodes within radius at this sample point
            Vector3d nodePos = findAstralTetherInRadius(world, samplePoint, DETECTION_RADIUS);
            samplesChecked++;
            
            if (nodePos != null) {
                nodesFound++;
                float distance = (float) Math.sqrt(
                    Math.pow(nodePos.x - playerPos.x, 2) +
                    Math.pow(nodePos.y - playerPos.y, 2) +
                    Math.pow(nodePos.z - playerPos.z, 2)
                );
                
                DebugLogger.debugInfo(LOGGER, "[AstralTetherDetector] Found candidate at %.2f,%.2f,%.2f (distance from player: %.2f, sample point: %.2f,%.2f,%.2f)", 
                        nodePos.x, nodePos.y, nodePos.z, distance, samplePoint.x, samplePoint.y, samplePoint.z);
                
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestNodePos = nodePos;
                }
            }
        }
        
        DebugLogger.debugInfo(LOGGER, "[AstralTetherDetector] Checked %d samples, found %d nodes, closest node: %s", 
                samplesChecked, nodesFound, closestNodePos != null ? String.format("%.2f,%.2f,%.2f", closestNodePos.x, closestNodePos.y, closestNodePos.z) : "null");
        
        // Don't spawn debug particles - connection particles are handled by AstralTetherParticleSystem
        // when a connection is actually established
        
        return closestNodePos;
    }

    /**
     * Calculates the look direction vector from yaw and pitch.
     * Uses Hytale's PhysicsMath.vectorFromAngles formula:
     * X = -sin(heading) * cos(pitch)
     * Y = sin(pitch)
     * Z = -cos(heading) * cos(pitch)
     * 
     * @param direction The Direction containing yaw/pitch
     * @param isFromClient True if Direction came from client protocol (degrees), false if from Vector3f (radians)
     * 
     * Note: Direction from client protocol stores degrees, but Direction created from Vector3f stores radians.
     * PhysicsMath.vectorFromAngles expects radians.
     * In Hytale: heading 0 = North (negative Z), π/2 = East (positive X), π = South (positive Z), 3π/2 = West (negative X)
     */
    private static Vector3f calculateLookDirection(Direction direction, boolean isFromClient) {
        float yawRad, pitchRad;
        
        if (isFromClient) {
            // Direction from client protocol is in degrees
            yawRad = (float) Math.toRadians(direction.yaw);
            pitchRad = (float) Math.toRadians(direction.pitch);
            DebugLogger.debugInfo(LOGGER, "[AstralTetherDetector] Converting from degrees: yaw=%.2f° -> %.2f rad, pitch=%.2f° -> %.2f rad", 
                    direction.yaw, yawRad, direction.pitch, pitchRad);
        } else {
            // Direction from Vector3f is already in radians
            yawRad = direction.yaw;
            pitchRad = direction.pitch;
            DebugLogger.debugInfo(LOGGER, "[AstralTetherDetector] Using radians directly: yaw=%.2f rad (%.2f°), pitch=%.2f rad (%.2f°)", 
                    yawRad, Math.toDegrees(yawRad), pitchRad, Math.toDegrees(pitchRad));
        }
        
        // Use Hytale's PhysicsMath formula for consistency
        Vector3d directionVec = new Vector3d();
        PhysicsMath.vectorFromAngles(yawRad, pitchRad, directionVec);
        
        DebugLogger.debugInfo(LOGGER, "[AstralTetherDetector] Calculated direction vector: %.3f,%.3f,%.3f", 
                directionVec.x, directionVec.y, directionVec.z);
        
        return new Vector3f((float)directionVec.x, (float)directionVec.y, (float)directionVec.z);
    }

    /**
     * Searches for a Astral Tether block within the specified radius of the given position.
     * 
     * @param world World instance
     * @param center Center position to search around
     * @param radius Search radius
     * @return Position of a Astral Tether if found, null otherwise
     */
    @Nullable
    private static Vector3d findAstralTetherInRadius(World world, Vector3d center, float radius) {
        int centerX = (int) Math.floor(center.x);
        int centerY = (int) Math.floor(center.y);
        int centerZ = (int) Math.floor(center.z);
        
        int radiusInt = (int) Math.ceil(radius);
        
        // Search in a cube around the center point
        // Expand search slightly to ensure we catch nodes
        for (int x = centerX - radiusInt - 1; x <= centerX + radiusInt + 1; x++) {
            for (int y = centerY - radiusInt - 1; y <= centerY + radiusInt + 1; y++) {
                for (int z = centerZ - radiusInt - 1; z <= centerZ + radiusInt + 1; z++) {
                    // Check distance from center point (not block center)
                    double dx = (x + 0.5) - center.x;
                    double dy = (y + 0.5) - center.y;
                    double dz = (z + 0.5) - center.z;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    
                    if (distance <= radius) {
                        // Check if this block is a Astral Tether
                        if (isAstralTether(world, x, y, z)) {
                            DebugLogger.debugInfo(LOGGER, "[AstralTetherDetector] Found Astral Tether in radius at %d,%d,%d (distance from center: %.2f)", 
                                    x, y, z, distance);
                            return new Vector3d(x + 0.5, y + 0.5, z + 0.5); // Center of block
                        }
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * Checks if the block at the given coordinates is a Astral Tether.
     */
    private static boolean isAstralTether(World world, int x, int y, int z) {
        try {
            BlockType blockType = ChunkSectionBlocks.blockType(world, x, y, z);
            if (blockType == null) {
                return false;
            }
            
            String blockTypeId = blockType.getId();
            
            // Check if this block type corresponds to Astral_Tether item
            // Get Astral Tether item and check if its blockId matches
            Item astralTetherItem = Item.getAssetMap().getAsset(ASTRAL_TETHER_ITEM_ID);
            if (astralTetherItem == null || astralTetherItem.getBlockId() == null) {
                LOGGER.atWarning().log("[AstralTetherDetector] Astral Tether item not found or has no blockId");
                return false;
            }
            
            String astralTetherBlockId = astralTetherItem.getBlockId();
            
            // Get block type from blockId and compare
            BlockType astralTetherBlockType = BlockType.getAssetMap().getAsset(astralTetherBlockId);
            if (astralTetherBlockType == null) {
                LOGGER.atWarning().log("[AstralTetherDetector] Astral Tether block type not found for id: %s", astralTetherBlockId);
                return false;
            }
            
            // Compare block types by ID
            boolean isMatch = blockTypeId.equals(astralTetherBlockType.getId());
            if (isMatch) {
                DebugLogger.debugInfo(LOGGER, "[AstralTetherDetector] Found Astral Tether at %d,%d,%d (blockId: %s)", x, y, z, blockTypeId);
            }
            return isMatch;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[AstralTetherDetector] Exception checking block at %d,%d,%d", x, y, z);
            // If any error occurs, assume it's not a star node
            return false;
        }
    }
    
    /**
     * Spawns debug visual particles along the ray to show where it's being cast.
     */
    private static void spawnDebugRayVisual(World world, Vector3d startPos, Vector3f direction, float length, ComponentAccessor<EntityStore> componentAccessor) {
        try {
            // Spawn particles every 1 block along the ray for visible debug line
            int numParticles = (int) length;
            for (int i = 0; i <= numParticles; i++) {
                float t = i;
                Vector3d particlePos = new Vector3d(
                    startPos.x + direction.x * t,
                    startPos.y + direction.y * t,
                    startPos.z + direction.z * t
                );
                
                // Spawn a visible particle to show the ray (use a simple glow particle for debug)
                ParticleUtil.spawnParticleEffect("StarSlinger_Connection", particlePos, componentAccessor);
            }
        } catch (Exception e) {
            LOGGER.atFine().log("[AstralTetherDetector] Error spawning debug visual: %s", e.getMessage());
        }
    }
}
