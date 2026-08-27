package com.hexvane.starslinger.rope;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Spawns fixed-scale rope segment props (prop entities do not stretch reliably at runtime). */
public final class RopeSegmentPool {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static boolean loggedMissingModel;

    private RopeSegmentPool() {}

    @Nullable
    public static Ref<EntityStore> spawnSegment(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3d position,
        @Nonnull Rotation3f rotation
    ) {
        ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(RopeConstants.ROPE_SEGMENT_MODEL_ID);
        if (modelAsset == null) {
            logMissingModelOnce();
            return null;
        }

        Model model = Model.createUnitScaleModel(modelAsset);
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(commandBuffer.getExternalData().takeNextNetworkId()));
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(
            PersistentModel.getComponentType(),
            new PersistentModel(new Model.ModelReference(RopeConstants.ROPE_SEGMENT_MODEL_ID, 1.0f, null, true))
        );
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
        holder.addComponent(PropComponent.getComponentType(), PropComponent.get());
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        holder.ensureComponent(UUIDComponent.getComponentType());
        return commandBuffer.addEntity(holder, AddReason.SPAWN);
    }

    public static void updateSegment(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> segmentRef,
        @Nonnull Vector3d position,
        @Nonnull Rotation3f rotation
    ) {
        if (!segmentRef.isValid()) {
            return;
        }
        TransformComponent transform = commandBuffer.getComponent(segmentRef, TransformComponent.getComponentType());
        if (transform != null) {
            transform.setPosition(position);
            transform.setRotation(rotation);
        }
        HeadRotation headRotation = commandBuffer.getComponent(segmentRef, HeadRotation.getComponentType());
        if (headRotation != null) {
            headRotation.setRotation(rotation);
        }
    }

    public static void despawnSegment(@Nonnull CommandBuffer<EntityStore> commandBuffer, @Nullable Ref<EntityStore> segmentRef) {
        if (segmentRef != null && segmentRef.isValid()) {
            commandBuffer.removeEntity(segmentRef, RemoveReason.REMOVE);
        }
    }

    public static void despawnAll(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore>[] segmentRefs
    ) {
        for (int i = 0; i < segmentRefs.length; i++) {
            Ref<EntityStore> segmentRef = segmentRefs[i];
            if (segmentRef != null) {
                commandBuffer.removeEntity(segmentRef, RemoveReason.REMOVE);
                segmentRefs[i] = null;
            }
        }
    }

    private static void logMissingModelOnce() {
        if (loggedMissingModel) {
            return;
        }
        loggedMissingModel = true;
        LOGGER.atWarning().log("Missing rope segment model asset %s", RopeConstants.ROPE_SEGMENT_MODEL_ID);
    }
}
