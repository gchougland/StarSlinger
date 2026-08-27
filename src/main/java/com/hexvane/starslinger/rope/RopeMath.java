package com.hexvane.starslinger.rope;

import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import javax.annotation.Nonnull;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class RopeMath {
    private RopeMath() {}

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float ropeSegmentLength(float tipToTetherDistance, float maxLength, float slackFactor) {
        float ropeLength = Math.min(tipToTetherDistance * slackFactor, maxLength);
        return Math.max(ropeLength / RopeConstants.SEGMENT_COUNT, RopeConstants.BASE_SEGMENT_LENGTH * 0.1f);
    }

    /** How many fixed-length segment props should be visible along the line. */
    public static int visibleSegmentCount(float arcLengthBlocks) {
        if (arcLengthBlocks < 1.0e-4f) {
            return 0;
        }
        int densityCount =
            (int) Math.ceil(arcLengthBlocks / RopeConstants.BASE_SEGMENT_LENGTH * RopeConstants.SEGMENT_VISUAL_DENSITY);
        int minCoverage = Math.max(1, (int) Math.ceil(arcLengthBlocks / RopeConstants.BASE_SEGMENT_LENGTH));
        return Math.min(RopeConstants.SEGMENT_COUNT, Math.max(densityCount, minCoverage));
    }

    /** Total length along the rope polyline from the hand through each node. */
    public static float ropePolylineLength(@Nonnull Vector3d[] nodes, @Nonnull Vector3d tip, int nodeCount) {
        float total = 0.0f;
        Vector3d prev = tip;
        for (int i = 1; i < nodeCount; i++) {
            total += (float) prev.distance(nodes[i]);
            prev = nodes[i];
        }
        return total;
    }

    /** Samples a point at normalized arc length {@code t} in [0, 1] along the rope polyline. */
    public static void sampleRopeAt(
        @Nonnull Vector3d[] nodes,
        @Nonnull Vector3d tip,
        int nodeCount,
        float t,
        float totalLength,
        @Nonnull Vector3d out
    ) {
        t = clamp(t, 0.0f, 1.0f);
        if (totalLength < 1.0e-6f) {
            out.set(tip);
            return;
        }
        float target = totalLength * t;
        float walked = 0.0f;
        Vector3d prev = tip;
        out.set(tip);
        for (int i = 1; i < nodeCount; i++) {
            Vector3d curr = nodes[i];
            float seg = (float) prev.distance(curr);
            if (seg > 1.0e-8f && walked + seg >= target) {
                float local = (target - walked) / seg;
                out.x = prev.x + (curr.x - prev.x) * local;
                out.y = prev.y + (curr.y - prev.y) * local;
                out.z = prev.z + (curr.z - prev.z) * local;
                return;
            }
            walked += seg;
            prev = curr;
        }
        out.set(nodes[nodeCount - 1]);
    }

    /** Evenly distributes rope nodes on a straight line between the hand and tether. */
    public static void layoutNodesOnLine(
        @Nonnull Vector3d[] nodes,
        @Nonnull Vector3d tip,
        @Nonnull Vector3d tether,
        int nodeCount
    ) {
        if (nodeCount <= 0) {
            return;
        }
        nodes[0].set(tip);
        if (nodeCount == 1) {
            return;
        }
        nodes[nodeCount - 1].set(tether);
        for (int i = 1; i < nodeCount - 1; i++) {
            double t = i / (double) (nodeCount - 1);
            Vector3d node = nodes[i];
            node.x = tip.x + (tether.x - tip.x) * t;
            node.y = tip.y + (tether.y - tip.y) * t;
            node.z = tip.z + (tether.z - tip.z) * t;
        }
    }

    /** Pulls intermediate rope nodes toward a straight line. */
    public static void straightenIntermediateNodes(
        @Nonnull Vector3d[] nodes,
        @Nonnull Vector3d tip,
        @Nonnull Vector3d tether,
        int nodeCount,
        float strength
    ) {
        if (nodeCount <= 2 || strength <= 0.0f) {
            return;
        }
        for (int i = 1; i < nodeCount - 1; i++) {
            double t = i / (double) (nodeCount - 1);
            double targetX = tip.x + (tether.x - tip.x) * t;
            double targetY = tip.y + (tether.y - tip.y) * t;
            double targetZ = tip.z + (tether.z - tip.z) * t;
            Vector3d node = nodes[i];
            node.x += (targetX - node.x) * strength;
            node.y += (targetY - node.y) * strength;
            node.z += (targetZ - node.z) * strength;
        }
    }

    /**
     * Orients an entity so local +Y (the segment model length axis) points along {@code direction}.
     */
    public static void rotationFromDirection(@Nonnull Vector3d direction, @Nonnull Rotation3f rotation) {
        double lenSq = direction.lengthSquared();
        if (lenSq < 1.0e-8) {
            rotation.set(0.0f, 0.0f, 0.0f);
            return;
        }
        double invLen = 1.0 / Math.sqrt(lenSq);
        double dx = direction.x * invLen;
        double dy = direction.y * invLen;
        double dz = direction.z * invLen;

        Vector3d euler = new Quaterniond().rotationTo(0.0, 1.0, 0.0, dx, dy, dz).getEulerAnglesYXZ(new Vector3d());
        rotation.setPitch(PhysicsMath.normalizeTurnAngle((float) euler.x));
        rotation.setYaw(PhysicsMath.normalizeTurnAngle((float) euler.y));
        rotation.setRoll(PhysicsMath.normalizeTurnAngle((float) euler.z));
    }

    public static void integrateVerlet(
        @Nonnull Vector3d[] positions,
        @Nonnull Vector3d[] oldPositions,
        int nodeCount,
        float gravity,
        float dt
    ) {
        float dtSq = dt * dt;
        for (int i = 1; i < nodeCount - 1; i++) {
            Vector3d pos = positions[i];
            Vector3d old = oldPositions[i];
            double vx = pos.x - old.x;
            double vy = pos.y - old.y;
            double vz = pos.z - old.z;
            old.set(pos);
            pos.x += vx;
            pos.y += vy - gravity * dtSq;
            pos.z += vz;
        }
    }

    public static void satisfyDistanceConstraints(
        @Nonnull Vector3d[] positions,
        int nodeCount,
        float segmentLength,
        int iterations
    ) {
        for (int iter = 0; iter < iterations; iter++) {
            for (int i = 0; i < nodeCount - 1; i++) {
                constrainPair(positions[i], positions[i + 1], segmentLength);
            }
        }
    }

    private static void constrainPair(@Nonnull Vector3d a, @Nonnull Vector3d b, float restLength) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < 1.0e-10) {
            return;
        }
        double dist = Math.sqrt(distSq);
        double diff = (dist - restLength) / dist;
        double offsetX = dx * diff * 0.5;
        double offsetY = dy * diff * 0.5;
        double offsetZ = dz * diff * 0.5;
        a.x += offsetX;
        a.y += offsetY;
        a.z += offsetZ;
        b.x -= offsetX;
        b.y -= offsetY;
        b.z -= offsetZ;
    }
}
