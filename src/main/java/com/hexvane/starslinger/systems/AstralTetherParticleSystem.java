package com.hexvane.starslinger.systems;

import com.hexvane.starslinger.components.StarSlingerConnectionComponent;
import com.hexvane.starslinger.rope.RopeConstants;
import com.hexvane.starslinger.rope.RopeMath;
import com.hexvane.starslinger.util.HandPositionUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Set;
import org.joml.Vector3d;

import javax.annotation.Nonnull;

/**
 * System that manages particle effects for Astral Tethers and Star Slinger connections.
 */
public class AstralTetherParticleSystem extends EntityTickingSystem<EntityStore> {
    private static final int PARTICLE_UPDATE_INTERVAL = 5;
    private int tickCounter = 0;

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemDependency<>(Order.AFTER, StarSlingerRopeSystem.class)
    );

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
                com.hypixel.hytale.server.core.entity.entities.Player.getComponentType(),
                StarSlingerConnectionComponent.getComponentType(),
                TransformComponent.getComponentType()
        );
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        
        StarSlingerConnectionComponent connection = archetypeChunk.getComponent(
                index,
                StarSlingerConnectionComponent.getComponentType()
        );
        
        if (connection == null || !connection.isConnected() || connection.getAstralTetherPosition() == null) {
            return;
        }

        TransformComponent transform = archetypeChunk.getComponent(
                index,
                TransformComponent.getComponentType()
        );
        if (transform == null) {
            return;
        }

        tickCounter++;
        if (tickCounter < PARTICLE_UPDATE_INTERVAL) {
            return;
        }
        tickCounter = 0;

        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        Vector3d handPos = HandPositionUtil.getHandPosition(playerRef, transform, commandBuffer);
        spawnConnectionParticles(handPos, connection, commandBuffer);
    }

    /**
     * Spawns particle effects along the simulated rope polyline.
     */
    private void spawnConnectionParticles(
            Vector3d handPos,
            StarSlingerConnectionComponent connection,
            CommandBuffer<EntityStore> commandBuffer) {
        
        Vector3d tetherPos = connection.getAstralTetherPosition();
        Vector3d[] nodes = connection.getNodePositions();
        Vector3d sampleTip = connection.isRopeSimActive() ? nodes[0] : handPos;
        float arcLength = connection.isRopeSimActive()
            ? RopeMath.ropePolylineLength(nodes, sampleTip, RopeConstants.NODE_COUNT)
            : (float) handPos.distance(tetherPos);

        if (arcLength <= 0) {
            return;
        }

        int numParticles = Math.max(3, (int) (arcLength / 2.0));

        SpatialResource<Ref<EntityStore>, EntityStore> playerSpatialResource =
                commandBuffer.getResource(EntityModule.get().getPlayerSpatialResourceType());
        List<Ref<EntityStore>> playerRefs = SpatialResource.getThreadLocalReferenceList();

        Vector3d midPoint = new Vector3d(
                (handPos.x + tetherPos.x) / 2.0,
                (handPos.y + tetherPos.y) / 2.0,
                (handPos.z + tetherPos.z) / 2.0
        );
        playerSpatialResource.getSpatialStructure().collect(midPoint, Math.max(75.0, arcLength + 10.0), playerRefs);

        if (playerRefs.isEmpty()) {
            return;
        }

        Vector3d particlePos = new Vector3d();
        for (int i = 0; i <= numParticles; i++) {
            float t = i / (float) numParticles;
            if (connection.isRopeSimActive()) {
                RopeMath.sampleRopeAt(nodes, sampleTip, RopeConstants.NODE_COUNT, t, arcLength, particlePos);
            } else {
                particlePos.set(
                    handPos.x + (tetherPos.x - handPos.x) * t,
                    handPos.y + (tetherPos.y - handPos.y) * t,
                    handPos.z + (tetherPos.z - handPos.z) * t
                );
            }

            ParticleUtil.spawnParticleEffect(
                    "StarSlinger_Connection",
                    particlePos,
                    playerRefs,
                    commandBuffer
            );
        }

        playerRefs.clear();
    }
}
