package com.hexvane.stargrappler.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Interaction;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction;
import com.hypixel.hytale.server.core.modules.interaction.IInteractionSimulationHandler;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hexvane.stargrappler.components.StarGrapplerConnectionComponent;
import com.hexvane.stargrappler.util.StarNodeDetector;

import javax.annotation.Nonnull;

/**
 * Interaction for right-click swing mechanic of the Star Grappler.
 * Hooks onto a Star Node and allows swinging like a rope/pendulum.
 * Uses ChargingInteraction to stay active while button is held.
 */
public class StarGrapplerSwingInteraction extends ChargingInteraction {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    
    @Nonnull
    public static final BuilderCodec<StarGrapplerSwingInteraction> CODEC = BuilderCodec.builder(
            StarGrapplerSwingInteraction.class, 
            StarGrapplerSwingInteraction::new, 
            ChargingInteraction.ABSTRACT_CODEC
    )
    .build();

    public StarGrapplerSwingInteraction() {
        super();
        // Allow indefinite hold - interaction stays active while button is held
        this.allowIndefiniteHold = true;
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Client; // Keep Client for ChargingInteraction compatibility
    }

    @Override
    protected void tick0(
            boolean firstRun,
            float time,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {
        
        // Server-side tick - handle connection establishment and unhooking here
        
        // Handle connection establishment on first run (server side)
        if (firstRun) {
        
        LOGGER.atInfo().log("[StarGrapplerSwing] Interaction started, type: %s", type);
        
        Ref<EntityStore> entityRef = context.getOwningEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();

        if (commandBuffer == null) {
            LOGGER.atWarning().log("[StarGrapplerSwing] CommandBuffer is null");
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Get player component
        Player playerComponent = commandBuffer.getComponent(entityRef, Player.getComponentType());
        if (playerComponent == null) {
            LOGGER.atWarning().log("[StarGrapplerSwing] Player component is null");
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Get transform component
        TransformComponent transformComponent = commandBuffer.getComponent(entityRef, TransformComponent.getComponentType());
        if (transformComponent == null) {
            LOGGER.atWarning().log("[StarGrapplerSwing] Transform component is null");
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Check if already connected - if so, don't search again
        StarGrapplerConnectionComponent existingConnection = commandBuffer.getComponent(
                entityRef,
                StarGrapplerConnectionComponent.getComponentType()
        );
        if (existingConnection != null && existingConnection.isConnected()) {
            LOGGER.atInfo().log("[StarGrapplerSwing] Already connected to node, skipping search");
            context.getState().state = InteractionState.Finished;
            return;
        }

        // Get world
        World world = commandBuffer.getStore().getExternalData().getWorld();
        if (world == null) {
            LOGGER.atWarning().log("[StarGrapplerSwing] World is null");
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Get client state for raycast data
        InteractionSyncData clientState = context.getClientState();
        if (clientState == null) {
            LOGGER.atWarning().log("[StarGrapplerSwing] Client state is null");
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Get look direction - prefer client state, but fall back to HeadRotation
        Direction lookDirection = clientState.attackerRot;
        boolean isFromClient = true;
        if (lookDirection == null) {
            LOGGER.atInfo().log("[StarGrapplerSwing] Client look direction is null, using HeadRotation component");
            HeadRotation headRotation = commandBuffer.getComponent(entityRef, HeadRotation.getComponentType());
            if (headRotation != null) {
                com.hypixel.hytale.math.vector.Vector3f headRot = headRotation.getRotation();
                lookDirection = new Direction(headRot.getYaw(), headRot.getPitch(), headRot.getRoll());
                isFromClient = false;
            } else {
                com.hypixel.hytale.math.vector.Vector3f rotation = transformComponent.getRotation();
                lookDirection = new Direction(rotation.getYaw(), rotation.getPitch(), rotation.getRoll());
                isFromClient = false;
            }
        }

        // Get eye position for raycast start
        com.hypixel.hytale.math.vector.Transform lookTransform = TargetUtil.getLook(entityRef, commandBuffer);
        Vector3d eyePos = lookTransform.getPosition();
        LOGGER.atInfo().log("[StarGrapplerSwing] Eye position: %.2f, %.2f, %.2f", eyePos.x, eyePos.y, eyePos.z);

        // Find closest Star Node using sphere-swept raycast
        LOGGER.atInfo().log("[StarGrapplerSwing] Searching for Star Node...");
        Vector3d starNodePos = StarNodeDetector.findClosestStarNode(
                entityRef,
                eyePos,
                world,
                lookDirection,
                commandBuffer,
                isFromClient
        );

        if (starNodePos == null) {
            // No Star Node found
            LOGGER.atInfo().log("[StarGrapplerSwing] No Star Node found");
            context.getState().state = InteractionState.Failed;
            return;
        }
        
        LOGGER.atInfo().log("[StarGrapplerSwing] Found Star Node at: %.2f, %.2f, %.2f", starNodePos.x, starNodePos.y, starNodePos.z);

        // Calculate initial rope length (distance when first hooking on)
        Vector3d playerPos = transformComponent.getPosition();
        double dx = starNodePos.x - playerPos.x;
        double dy = starNodePos.y - playerPos.y;
        double dz = starNodePos.z - playerPos.z;
        double actualDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        // Rope length is 50% of initial distance, minimum 2 blocks
        double initialRopeLength = Math.max(2.0, actualDistance * 0.7);
        
        LOGGER.atInfo().log("[StarGrapplerSwing] Calculated rope length: %.2f (from distance: %.2f)", initialRopeLength, actualDistance);

        // Store connection state - the system will apply pendulum physics
        StarGrapplerConnectionComponent connectionComponent = commandBuffer.ensureAndGetComponent(
                entityRef,
                StarGrapplerConnectionComponent.getComponentType()
        );
        connectionComponent.setLaunchMode(false); // Swing mode
        connectionComponent.setStarNodePosition(starNodePos);
        connectionComponent.setRopeLength(initialRopeLength);
        connectionComponent.setConnected(true);
        connectionComponent.setConnectionTick(0);
        
        // Play sound effect when hooking onto a node
        // Use staff fire shoot sound for a magical effect that fits the star theme
        try {
            int soundEventIndex = SoundEvent.getAssetMap().getIndex("SFX_Staff_Fire_Shoot");
            if (soundEventIndex != 0) {
                // Play 3D sound at the player position so they can hear it clearly
                // playerPos is already defined above
                // Use 0.5 volume modifier to make it quieter
                SoundUtil.playSoundEvent3dToPlayer(
                        entityRef,
                        soundEventIndex,
                        SoundCategory.SFX,
                        playerPos.x,
                        playerPos.y,
                        playerPos.z,
                        0.5f, // volumeModifier - make it quieter
                        1.0f, // pitchModifier
                        commandBuffer
                );
                LOGGER.atInfo().log("[StarGrapplerSwing] Played hook sound (SFX_Staff_Fire_Shoot) at player position");
            } else {
                LOGGER.atWarning().log("[StarGrapplerSwing] Sound event SFX_Staff_Fire_Shoot not found");
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[StarGrapplerSwing] Failed to play hook sound");
        }
        
        LOGGER.atInfo().log("[StarGrapplerSwing] Connection established - system will apply pendulum physics with rope length %.2f", initialRopeLength);
        }
        
        // Check if button was released based on client data
        InteractionSyncData clientData = context.getClientState();
        if (clientData != null && clientData.chargeValue != -1.0F && clientData.chargeValue != -2.0F) {
            // Button released - unhook
            Ref<EntityStore> ref = context.getEntity();
            CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
            StarGrapplerConnectionComponent connection = commandBuffer.getComponent(
                    ref,
                    StarGrapplerConnectionComponent.getComponentType()
            );
            if (connection != null && connection.isConnected()) {
                LOGGER.atInfo().log("[StarGrapplerSwing] Button released (server tick), unhooking");
                connection.setConnected(false);
            }
        }
        
        // Call parent to handle ChargingInteraction logic
        super.tick0(firstRun, time, type, context, cooldownHandler);
    }

    @Override
    protected void simulateTick0(
            boolean firstRun,
            float time,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {
        
        // Client-side simulation - handle charging state
        Ref<EntityStore> ref = context.getEntity();
        IInteractionSimulationHandler simulationHandler = context.getInteractionManager().getInteractionSimulationHandler();
        
        if (simulationHandler.isCharging(firstRun, time, type, context, ref, cooldownHandler) && this.allowIndefiniteHold) {
            // Button still held - keep interaction active
            context.getState().chargeValue = -1.0F;
            context.getState().state = InteractionState.NotFinished;
        } else {
            // Button released - finish interaction
            float chargeValue = simulationHandler.getChargeValue(firstRun, time, type, context, ref, cooldownHandler);
            context.getState().chargeValue = chargeValue;
            context.getState().state = InteractionState.Finished;
        }
    }

    @Nonnull
    @Override
    protected Interaction generatePacket() {
        return new com.hypixel.hytale.protocol.ChargingInteraction();
    }

    @Override
    protected void configurePacket(Interaction packet) {
        super.configurePacket(packet);
    }
}
