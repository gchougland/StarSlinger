package com.hexvane.starslinger.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Interaction;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hexvane.starslinger.util.AstralTetherPlacer;

import javax.annotation.Nonnull;

/**
 * Interaction handler for Galaxy in a Bottle throwing potion impact.
 * Generates a field of Star Nodes around the impact location.
 */
public class GalaxyBottleInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<GalaxyBottleInteraction> CODEC = BuilderCodec.builder(
            GalaxyBottleInteraction.class,
            GalaxyBottleInteraction::new,
            SimpleInstantInteraction.CODEC
    ).build();

    @Override
    protected void firstRun(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {
        
        Ref<EntityStore> entityRef = context.getOwningEntity();
        Store<EntityStore> store = entityRef.getStore();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();

        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Get world
        World world = store.getExternalData().getWorld();
        if (world == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Get impact position from projectile
        // TODO: Get actual impact position from projectile hit event
        // For now, this is a placeholder that will need to be connected to projectile hit handler
        
        // Placeholder: Generate star nodes around a position
        // This will need to be called from projectile hit handler
        // AstralTetherPlacer.generateStarNodeField(world, impactX, impactY, impactZ);

        context.getState().state = InteractionState.Finished;
    }

    @Nonnull
    @Override
    protected Interaction generatePacket() {
        return new com.hypixel.hytale.protocol.SimpleInteraction();
    }

    @Override
    protected void configurePacket(Interaction packet) {
        super.configurePacket(packet);
    }
}
