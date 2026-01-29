package com.hexvane.stargrappler.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hexvane.stargrappler.components.StarGrapplerConnectionComponent;
import it.unimi.dsi.fastutil.objects.ObjectList;

import javax.annotation.Nonnull;

/**
 * System that manages particle effects for Star Nodes and grappler connections.
 */
public class StarNodeParticleSystem extends EntityTickingSystem<EntityStore> {
    private static final int PARTICLE_UPDATE_INTERVAL = 5; // Update particles every N ticks (reduced frequency to prevent over-spawning)
    private int tickCounter = 0;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
                com.hypixel.hytale.server.core.entity.entities.Player.getComponentType(),
                StarGrapplerConnectionComponent.getComponentType(),
                TransformComponent.getComponentType()
        );
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        
        StarGrapplerConnectionComponent connection = archetypeChunk.getComponent(
                index,
                StarGrapplerConnectionComponent.getComponentType()
        );
        
        if (connection == null || !connection.isConnected() || connection.getStarNodePosition() == null) {
            return;
        }

        TransformComponent transform = archetypeChunk.getComponent(
                index,
                TransformComponent.getComponentType()
        );
        if (transform == null) {
            return;
        }

        // Get world for debug line rendering
        World world = store.getExternalData().getWorld();
        
        // Draw larger sphere at star node position every tick to ensure smooth visibility
        drawEnlargedStarNode(connection.getStarNodePosition(), world);

        tickCounter++;
        if (tickCounter < PARTICLE_UPDATE_INTERVAL) {
            return;
        }
        tickCounter = 0;

        // Get hand position for particle start (instead of eye position)
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        Vector3d handPos = getHandPosition(playerRef, transform, commandBuffer);

        // Draw white line for the rope
        drawRopeLine(handPos, connection.getStarNodePosition(), world);

        // Spawn particle trail from player hand to star node
        spawnGrapplerParticles(
                handPos,
                connection.getStarNodePosition(),
                commandBuffer,
                playerRef
        );
    }

    /**
     * Spawns particle effects along the grappler connection line.
     */
    private void spawnGrapplerParticles(
            Vector3d playerPos,
            Vector3d starNodePos,
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> playerRef) {
        
        // Calculate direction and distance
        double dx = starNodePos.x - playerPos.x;
        double dy = starNodePos.y - playerPos.y;
        double dz = starNodePos.z - playerPos.z;
        
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        if (distance <= 0) {
            return;
        }
        
        // Normalize direction
        dx /= distance;
        dy /= distance;
        dz /= distance;
        
        // Spawn particles along the line at regular intervals
        // Spawn fewer particle systems to prevent over-spawning (every 2 blocks)
        int numParticles = Math.max(3, (int) (distance / 2.0));
        double stepSize = distance / numParticles;
        
        // Get player spatial resource for particle visibility
        SpatialResource<Ref<EntityStore>, EntityStore> playerSpatialResource = 
                commandBuffer.getResource(EntityModule.get().getPlayerSpatialResourceType());
        ObjectList<Ref<EntityStore>> playerRefs = SpatialResource.getThreadLocalReferenceList();
        
        // Use custom Star Grappler connection particle system
        String particleSystemId = "StarGrappler_Connection";
        
        // Collect players once for all particles (more efficient)
        Vector3d midPoint = new Vector3d(
                (playerPos.x + starNodePos.x) / 2.0,
                (playerPos.y + starNodePos.y) / 2.0,
                (playerPos.z + starNodePos.z) / 2.0
        );
        playerSpatialResource.getSpatialStructure().collect(midPoint, Math.max(75.0, distance + 10.0), playerRefs);
        
        if (playerRefs.isEmpty()) {
            return; // No players nearby to see particles
        }
        
        // Spawn particle systems at key points along the line (start, middle, end)
        // The particle systems have their own lifespan and will despawn automatically
        for (int i = 0; i <= numParticles; i++) {
            double t = i * stepSize;
            Vector3d particlePos = new Vector3d(
                    playerPos.x + dx * t,
                    playerPos.y + dy * t,
                    playerPos.z + dz * t
            );
            
            // Spawn particle effect - the system will create a continuous line
            // Each system has a lifespan and will despawn automatically
            ParticleUtil.spawnParticleEffect(
                    particleSystemId,
                    particlePos,
                    playerRefs,
                    commandBuffer
            );
        }
        
        playerRefs.clear();
    }

    /**
     * Draws a white line (cylinder) between the player and the star node to visualize the rope.
     */
    private void drawRopeLine(Vector3d startPos, Vector3d endPos, World world) {
        // Calculate direction and distance
        Vector3d direction = new Vector3d(
                endPos.x - startPos.x,
                endPos.y - startPos.y,
                endPos.z - startPos.z
        );
        
        double distance = Math.sqrt(
                direction.x * direction.x +
                direction.y * direction.y +
                direction.z * direction.z
        );
        
        if (distance <= 0.01) {
            return; // Too close, don't draw
        }
        
        // Normalize direction
        direction.x /= distance;
        direction.y /= distance;
        direction.z /= distance;
        
        // Calculate midpoint (center of the line)
        Vector3d midPoint = new Vector3d(
                (startPos.x + endPos.x) / 2.0,
                (startPos.y + endPos.y) / 2.0,
                (startPos.z + endPos.z) / 2.0
        );
        
        // Create transformation matrix for the cylinder
        // Cylinder in DebugUtils is oriented along Y-axis, so we need to rotate it to match our direction
        Matrix4d matrix = new Matrix4d();
        matrix.identity();
        matrix.translate(midPoint);
        
        // Calculate rotation angles to align cylinder with direction
        // Match the exact rotation logic from DebugUtils.addArrow
        double angleY = Math.atan2(direction.z, direction.x);
        double horizontalLength = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        double angleX = Math.atan2(horizontalLength, direction.y);
        
        // Apply rotations (exactly like DebugUtils.addArrow)
        Matrix4d tmp = new Matrix4d();
        matrix.rotateAxis(angleY + (Math.PI / 2.0), 0.0, 1.0, 0.0, tmp);
        matrix.rotateAxis(angleX, 1.0, 0.0, 0.0, tmp);
        
        // Scale cylinder: radius (X/Z) is small for thin line, height (Y) is the distance
        // Cylinder extends along Y-axis from center, so scale Y to half the distance
        double lineRadius = 0.05; // Thin line (5cm radius)
        matrix.scale(lineRadius, distance / 2.0, lineRadius);
        
        // White color for the rope line
        Vector3f whiteColor = new Vector3f(1.0f, 1.0f, 1.0f);
        
        // Draw the line (cylinder) using matrix-based approach for full control
        // Duration: 0.1 seconds (will be redrawn every tick), no fade
        DebugUtils.add(world, DebugShape.Cylinder, matrix, whiteColor, 0.2f, false);
    }
    
    /**
     * Calculates the hand position for the player based on their position and rotation.
     * Hand position is lower than eye height (around chest/shoulder level) and slightly forward/to the right.
     */
    private Vector3d getHandPosition(
            Ref<EntityStore> playerRef,
            TransformComponent transform,
            CommandBuffer<EntityStore> commandBuffer) {
        
        Vector3d playerPos = transform.getPosition();
        
        // Get head rotation for look direction
        HeadRotation headRotation = commandBuffer.getComponent(playerRef, HeadRotation.getComponentType());
        if (headRotation == null) {
            // Fallback to transform rotation
            com.hypixel.hytale.math.vector.Vector3f rotation = transform.getRotation();
            return calculateHandPosition(playerPos, rotation.getYaw(), rotation.getPitch());
        }
        
        com.hypixel.hytale.math.vector.Vector3f headRot = headRotation.getRotation();
        return calculateHandPosition(playerPos, headRot.getYaw(), headRot.getPitch());
    }
    
    /**
     * Calculates hand position offset from player position based on yaw and pitch.
     */
    private Vector3d calculateHandPosition(Vector3d playerPos, float yaw, float pitch) {
        // Hand height offset: around chest/shoulder level (about 1.2 blocks above feet)
        // Player eye height is typically 1.6, so hand is about 0.4 blocks below eye
        double handHeightOffset = 1.1;
        
        // Forward offset: hand extends forward from body
        double forwardOffset = 0.0;
        
        // Right offset: hand is slightly to the right (for right-handed players)
        double rightOffset = -0.25;
        
        // Calculate forward direction from yaw (in radians)
        // Hytale uses standard yaw: 0 = north (-Z), PI/2 = east (+X), PI = south (+Z), -PI/2 = west (-X)
        double forwardX = -Math.sin(yaw) * forwardOffset;
        double forwardZ = -Math.cos(yaw) * forwardOffset;
        
        // Calculate right direction (perpendicular to forward, 90 degrees clockwise)
        double rightX = -Math.cos(yaw) * rightOffset;
        double rightZ = Math.sin(yaw) * rightOffset;
        
        // Calculate hand position
        Vector3d handPos = playerPos.clone();
        handPos.y += handHeightOffset;
        handPos.x += forwardX + rightX;
        handPos.z += forwardZ + rightZ;
        
        return handPos;
    }
    
    /**
     * Draws an enlarged sphere at the star node position to make it appear bigger when connected.
     * The sphere will disappear when the connection is released (not drawn when not connected).
     */
    private void drawEnlargedStarNode(Vector3d starNodePos, World world) {
        // Draw a larger sphere at the star node position
        // Use white color to match the rope line and provide subtle visual feedback
        Vector3f starColor = new Vector3f(1.0f, 1.0f, 1.0f); // White color
        double scale = 0.6; // Make it appear larger (normal block is ~0.5, so this makes it ~0.6)
        
        // Create matrix for the sphere
        Matrix4d matrix = new Matrix4d();
        matrix.identity();
        matrix.translate(starNodePos);
        matrix.scale(scale, scale, scale);
        
        // Duration: 0.1 seconds - drawn every tick, so short duration is fine
        // No fade to prevent blinking
        DebugUtils.add(world, DebugShape.Sphere, matrix, starColor, 0.1f, false);
    }
}
