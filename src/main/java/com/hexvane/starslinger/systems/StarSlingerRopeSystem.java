package com.hexvane.starslinger.systems;

import com.hexvane.starslinger.components.StarSlingerConnectionComponent;
import com.hexvane.starslinger.rope.RopeConstants;
import com.hexvane.starslinger.rope.RopeMath;
import com.hexvane.starslinger.rope.RopeSegmentPool;
import com.hexvane.starslinger.util.HandPositionUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Verlet simulation and segment prop placement for connected Star Slinger ropes. */
public class StarSlingerRopeSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemDependency<>(Order.AFTER, StarSlingerSystem.class)
    );

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            Player.getComponentType(),
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
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        StarSlingerConnectionComponent connection = archetypeChunk.getComponent(
            index,
            StarSlingerConnectionComponent.getComponentType()
        );
        if (connection == null) {
            return;
        }

        if (!connection.isConnected() || connection.getAstralTetherPosition() == null) {
            teardownRope(commandBuffer, connection);
            return;
        }

        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }

        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        Vector3d handPos = HandPositionUtil.getHandPosition(playerRef, transform, commandBuffer);
        Vector3d tetherPos = connection.getAstralTetherPosition();
        Vector3d[] nodes = connection.getNodePositions();
        Vector3d[] oldNodes = connection.getNodeOldPositions();

        if (!connection.isRopeSimActive()) {
            RopeMath.layoutNodesOnLine(nodes, handPos, tetherPos, RopeConstants.NODE_COUNT);
            for (int i = 0; i < RopeConstants.NODE_COUNT; i++) {
                oldNodes[i].set(nodes[i]);
            }
            connection.setRopeSimActive(true);
        }

        float tipToTether = (float) handPos.distance(tetherPos);
        boolean shortLine = tipToTether < RopeConstants.SHORT_LINE_STRAIGHTEN_BLOCKS;

        if (shortLine) {
            RopeMath.layoutNodesOnLine(nodes, handPos, tetherPos, RopeConstants.NODE_COUNT);
            for (int i = 0; i < RopeConstants.NODE_COUNT; i++) {
                oldNodes[i].set(nodes[i]);
            }
        } else {
            boolean launchMode = connection.isLaunchMode();
            float gravity = launchMode ? RopeConstants.LAUNCH_GRAVITY : RopeConstants.SWING_GRAVITY;
            RopeMath.integrateVerlet(nodes, oldNodes, RopeConstants.NODE_COUNT, gravity, dt);

            nodes[0].set(handPos);
            nodes[RopeConstants.NODE_COUNT - 1].set(tetherPos);

            float restTotal;
            float straighten;
            if (launchMode) {
                restTotal = tipToTether * RopeConstants.LAUNCH_ROPE_SLACK_FACTOR;
                straighten = RopeConstants.LAUNCH_ROPE_STRAIGHTEN;
            } else {
                restTotal = Math.max((float) connection.getRopeLength(), tipToTether * RopeConstants.ROPE_SLACK_FACTOR);
                straighten = RopeConstants.SWING_ROPE_STRAIGHTEN;
            }
            float segmentLength = Math.max(
                restTotal / RopeConstants.SEGMENT_COUNT,
                RopeConstants.BASE_SEGMENT_LENGTH * 0.1f
            );

            RopeMath.satisfyDistanceConstraints(
                nodes,
                RopeConstants.NODE_COUNT,
                segmentLength,
                RopeConstants.CONSTRAINT_ITERATIONS
            );
            RopeMath.straightenIntermediateNodes(nodes, handPos, tetherPos, RopeConstants.NODE_COUNT, straighten);
            RopeMath.satisfyDistanceConstraints(nodes, RopeConstants.NODE_COUNT, segmentLength, 4);

            nodes[0].set(handPos);
            nodes[RopeConstants.NODE_COUNT - 1].set(tetherPos);
        }

        nodes[0].set(handPos);
        nodes[RopeConstants.NODE_COUNT - 1].set(tetherPos);

        updateSegmentVisuals(commandBuffer, connection, handPos);
    }

    private static void teardownRope(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull StarSlingerConnectionComponent connection
    ) {
        if (!connection.isRopeSimActive()) {
            boolean hasSegments = false;
            for (Ref<EntityStore> segmentRef : connection.getSegmentRefs()) {
                if (segmentRef != null) {
                    hasSegments = true;
                    break;
                }
            }
            if (!hasSegments) {
                return;
            }
        }
        RopeSegmentPool.despawnAll(commandBuffer, connection.getSegmentRefs());
        connection.setRopeSimActive(false);
    }

    private static void updateSegmentVisuals(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull StarSlingerConnectionComponent connection,
        @Nonnull Vector3d handPos
    ) {
        Vector3d[] nodes = connection.getNodePositions();
        Ref<EntityStore>[] segmentRefs = connection.getSegmentRefs();
        Vector3d dir = new Vector3d();
        Rotation3f rotation = new Rotation3f();
        Vector3d position = new Vector3d();
        Vector3d start = new Vector3d();
        Vector3d end = new Vector3d();

        float arcLength = RopeMath.ropePolylineLength(nodes, handPos, RopeConstants.NODE_COUNT);
        int visibleCount = RopeMath.visibleSegmentCount(arcLength);

        for (int i = visibleCount; i < RopeConstants.SEGMENT_COUNT; i++) {
            Ref<EntityStore> excess = segmentRefs[i];
            if (excess != null) {
                RopeSegmentPool.despawnSegment(commandBuffer, excess);
                segmentRefs[i] = null;
            }
        }

        for (int i = 0; i < visibleCount; i++) {
            float t0 = i / (float) visibleCount;
            float t1 = (i + 1) / (float) visibleCount;
            RopeMath.sampleRopeAt(nodes, handPos, RopeConstants.NODE_COUNT, t0, arcLength, start);
            RopeMath.sampleRopeAt(nodes, handPos, RopeConstants.NODE_COUNT, t1, arcLength, end);

            dir.set(end).sub(start);
            float dist = (float) dir.length();
            if (dist < 1.0e-4f) {
                Ref<EntityStore> segmentRef = segmentRefs[i];
                if (segmentRef != null) {
                    RopeSegmentPool.despawnSegment(commandBuffer, segmentRef);
                    segmentRefs[i] = null;
                }
                continue;
            }
            dir.div(dist);
            RopeMath.rotationFromDirection(dir, rotation);

            if (i == 0) {
                position.set(start);
            } else {
                position
                    .set(start)
                    .sub(
                        dir.x * RopeConstants.SEGMENT_JOINT_OVERLAP,
                        dir.y * RopeConstants.SEGMENT_JOINT_OVERLAP,
                        dir.z * RopeConstants.SEGMENT_JOINT_OVERLAP
                    );
            }

            Ref<EntityStore> segmentRef = segmentRefs[i];
            if (segmentRef == null || !segmentRef.isValid()) {
                segmentRefs[i] = RopeSegmentPool.spawnSegment(commandBuffer, position, rotation);
                continue;
            }
            RopeSegmentPool.updateSegment(commandBuffer, segmentRef, position, rotation);
        }
    }
}
