package com.hexvane.starslinger.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hexvane.starslinger.util.AstralTetherPlacer;
import com.hexvane.starslinger.util.DebugLogger;

import javax.annotation.Nonnull;

/**
 * System that handles Galaxy Bottle projectile hits and generates Star Node fields.
 */
public class GalaxyBottleProjectileSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String GALAXY_BOTTLE_PROJECTILE_ID = "Galaxy_Bottle";

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
                ProjectileComponent.getComponentType(),
                TransformComponent.getComponentType()
        );
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Nothing to do on add
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        
        DebugLogger.debugInfo(LOGGER, "[GalaxyBottleProjectileSystem] Projectile removed, reason: %s", reason);
        
        ProjectileComponent projectileComponent = store.getComponent(ref, ProjectileComponent.getComponentType());
        if (projectileComponent == null) {
            LOGGER.atFine().log("[GalaxyBottleProjectileSystem] No projectile component");
            return;
        }

        // Check if this is a Galaxy Bottle projectile
        String projectileId = projectileComponent.getProjectileAssetName();
        DebugLogger.debugInfo(LOGGER, "[GalaxyBottleProjectileSystem] Projectile ID: %s", projectileId);
        
        if (projectileId == null || !projectileId.equals(GALAXY_BOTTLE_PROJECTILE_ID)) {
            LOGGER.atFine().log("[GalaxyBottleProjectileSystem] Not a Galaxy Bottle projectile");
            return;
        }

        // Get position from transform component
        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        if (transformComponent == null) {
            LOGGER.atWarning().log("[GalaxyBottleProjectileSystem] No transform component");
            return;
        }

        Vector3d position = transformComponent.getPosition();
        DebugLogger.debugInfo(LOGGER, "[GalaxyBottleProjectileSystem] Galaxy Bottle hit at %.2f,%.2f,%.2f", 
                position.x, position.y, position.z);
        
        // Get world
        World world = store.getExternalData().getWorld();
        if (world == null) {
            LOGGER.atWarning().log("[GalaxyBottleProjectileSystem] World is null");
            return;
        }

        // Generate astral tether field at impact location
        int x = (int) Math.floor(position.x);
        int y = (int) Math.floor(position.y);
        int z = (int) Math.floor(position.z);
        
        DebugLogger.debugInfo(LOGGER, "[GalaxyBottleProjectileSystem] Generating astral tether field at %d,%d,%d", x, y, z);
        AstralTetherPlacer.generateStarNodeField(world, x, y, z);
    }
}
