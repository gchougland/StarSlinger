package com.hexvane.starslinger.util;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Approximate held-item hand position for rope and particle anchors. */
public final class HandPositionUtil {
    private HandPositionUtil() {}

    @Nonnull
    public static Vector3d getHandPosition(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull TransformComponent transform,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Vector3d playerPos = transform.getPosition();
        HeadRotation headRotation = commandBuffer.getComponent(playerRef, HeadRotation.getComponentType());
        if (headRotation == null) {
            Rotation3f rotation = transform.getRotation();
            return calculateHandPosition(playerPos, rotation.yaw());
        }
        Rotation3f headRot = headRotation.getRotation();
        return calculateHandPosition(playerPos, headRot.yaw());
    }

    @Nonnull
    public static Vector3d calculateHandPosition(@Nonnull Vector3d playerPos, float yaw) {
        double handHeightOffset = 1.1;
        double forwardOffset = 0.0;
        double rightOffset = -0.25;

        double forwardX = -Math.sin(yaw) * forwardOffset;
        double forwardZ = -Math.cos(yaw) * forwardOffset;
        double rightX = -Math.cos(yaw) * rightOffset;
        double rightZ = Math.sin(yaw) * rightOffset;

        Vector3d handPos = new Vector3d(playerPos);
        handPos.y += handHeightOffset;
        handPos.x += forwardX + rightX;
        handPos.z += forwardZ + rightZ;
        return handPos;
    }
}
