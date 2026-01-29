package com.hexvane.stargrappler.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.KeyedCodec;
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
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
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
 * Interaction for left-click launch mechanic of the Star Grappler.
 * Launches the player toward the closest Star Node found along a sphere-swept raycast.
 * Uses ChargingInteraction to stay active while button is held.
 */
public class StarGrapplerLaunchInteraction extends ChargingInteraction {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    
    @Nonnull
    public static final BuilderCodec<StarGrapplerLaunchInteraction> CODEC = BuilderCodec.builder(
            StarGrapplerLaunchInteraction.class, 
            StarGrapplerLaunchInteraction::new, 
            ChargingInteraction.ABSTRACT_CODEC
    )
    .build();

    public StarGrapplerLaunchInteraction() {
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
        // ChargingInteraction.tick0() reads from clientData.chargeValue set by simulateTick0()
        
        // Handle connection establishment on first run (server side)
        if (firstRun) {
        
        LOGGER.atInfo().log("[StarGrapplerLaunch] Interaction started, type: %s", type);
        
        Ref<EntityStore> entityRef = context.getOwningEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();

        if (commandBuffer == null) {
            LOGGER.atWarning().log("[StarGrapplerLaunch] CommandBuffer is null");
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Get player component
        Player playerComponent = commandBuffer.getComponent(entityRef, Player.getComponentType());
        if (playerComponent == null) {
            LOGGER.atWarning().log("[StarGrapplerLaunch] Player component is null");
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Get transform component
        TransformComponent transformComponent = commandBuffer.getComponent(entityRef, TransformComponent.getComponentType());
        if (transformComponent == null) {
            LOGGER.atWarning().log("[StarGrapplerLaunch] Transform component is null");
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Check if already connected - if so, don't search again
        StarGrapplerConnectionComponent existingConnection = commandBuffer.getComponent(
                entityRef,
                StarGrapplerConnectionComponent.getComponentType()
        );
        if (existingConnection != null && existingConnection.isConnected()) {
            LOGGER.atInfo().log("[StarGrapplerLaunch] Already connected to node, skipping search");
            context.getState().state = InteractionState.Finished;
            return;
        }

        // Get world
        World world = commandBuffer.getStore().getExternalData().getWorld();
        if (world == null) {
            LOGGER.atWarning().log("[StarGrapplerLaunch] World is null");
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Get client state for raycast data
        InteractionSyncData clientState = context.getClientState();
        if (clientState == null) {
            LOGGER.atWarning().log("[StarGrapplerLaunch] Client state is null");
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Get look direction - prefer client state, but fall back to HeadRotation (head look direction, not body rotation)
        Direction lookDirection = clientState.attackerRot;
        boolean isFromClient = true;
        if (lookDirection == null) {
            LOGGER.atInfo().log("[StarGrapplerLaunch] Client look direction is null, using HeadRotation component");
            // Use HeadRotation component for actual head/eye look direction (not body rotation)
            HeadRotation headRotation = commandBuffer.getComponent(entityRef, HeadRotation.getComponentType());
            if (headRotation != null) {
                com.hypixel.hytale.math.vector.Vector3f headRot = headRotation.getRotation();
                // HeadRotation stores: x=pitch, y=yaw, z=roll (all in radians)
                // Direction stores: yaw, pitch, roll
                lookDirection = new Direction(headRot.getYaw(), headRot.getPitch(), headRot.getRoll());
                isFromClient = false; // This Direction is from Vector3f, so it's in radians
                LOGGER.atInfo().log("[StarGrapplerLaunch] Using HeadRotation: yaw=%.2f rad (%.2f°), pitch=%.2f rad (%.2f°)", 
                        headRot.getYaw(), Math.toDegrees(headRot.getYaw()), 
                        headRot.getPitch(), Math.toDegrees(headRot.getPitch()));
            } else {
                // Fallback to transform rotation if HeadRotation not available
                LOGGER.atWarning().log("[StarGrapplerLaunch] HeadRotation not found, falling back to transform rotation");
                com.hypixel.hytale.math.vector.Vector3f rotation = transformComponent.getRotation();
                lookDirection = new Direction(rotation.getYaw(), rotation.getPitch(), rotation.getRoll());
                isFromClient = false;
            }
        } else {
            LOGGER.atInfo().log("[StarGrapplerLaunch] Using client look direction: yaw=%.2f, pitch=%.2f (assuming degrees)", 
                    lookDirection.yaw, lookDirection.pitch);
        }

        // Get eye position for raycast start (head/hand level, not feet)
        // Use TargetUtil.getLook() which calculates eye position correctly
        com.hypixel.hytale.math.vector.Transform lookTransform = TargetUtil.getLook(entityRef, commandBuffer);
        Vector3d eyePos = lookTransform.getPosition();
        LOGGER.atInfo().log("[StarGrapplerLaunch] Eye position: %.2f, %.2f, %.2f", eyePos.x, eyePos.y, eyePos.z);

        // Find closest Star Node using sphere-swept raycast
        LOGGER.atInfo().log("[StarGrapplerLaunch] Searching for Star Node...");
        Vector3d starNodePos = StarNodeDetector.findClosestStarNode(
                entityRef,
                eyePos, // Use eye position instead of transform component
                world,
                lookDirection,
                commandBuffer,
                isFromClient
        );

        if (starNodePos == null) {
            // No Star Node found
            LOGGER.atInfo().log("[StarGrapplerLaunch] No Star Node found");
            context.getState().state = InteractionState.Failed;
            return;
        }
        
        LOGGER.atInfo().log("[StarGrapplerLaunch] Found Star Node at: %.2f, %.2f, %.2f", starNodePos.x, starNodePos.y, starNodePos.z);

        // Store connection state - the system will apply continuous force
        StarGrapplerConnectionComponent connectionComponent = commandBuffer.ensureAndGetComponent(
                entityRef,
                StarGrapplerConnectionComponent.getComponentType()
        );
        connectionComponent.setLaunchMode(true);
        connectionComponent.setStarNodePosition(starNodePos);
        connectionComponent.setConnected(true);
        connectionComponent.setConnectionTick(0); // Initialize to 0, system will track ticks
        
        // Play sound effect when hooking onto a node
        // Use staff fire shoot sound for a magical effect that fits the star theme
        try {
            int soundEventIndex = SoundEvent.getAssetMap().getIndex("SFX_Staff_Fire_Shoot");
            if (soundEventIndex != 0) {
                // Play 3D sound at the player position so they can hear it clearly
                Vector3d playerPos = transformComponent.getPosition();
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
                LOGGER.atInfo().log("[StarGrapplerLaunch] Played hook sound (SFX_Staff_Fire_Shoot) at player position");
            } else {
                LOGGER.atWarning().log("[StarGrapplerLaunch] Sound event SFX_Staff_Fire_Shoot not found");
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[StarGrapplerLaunch] Failed to play hook sound");
        }
        
        LOGGER.atInfo().log("[StarGrapplerLaunch] Connection established - system will apply continuous force toward node at %.2f, %.2f, %.2f", 
                starNodePos.x, starNodePos.y, starNodePos.z);
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
                LOGGER.atInfo().log("[StarGrapplerLaunch] Button released (server tick), unhooking");
                connection.setConnected(false);
            }
        }
        
        // Call parent to handle ChargingInteraction logic (forks, next interactions, etc.)
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
        // This runs on client first, then data is sent to server for tick0()
        Ref<EntityStore> ref = context.getEntity();
        IInteractionSimulationHandler simulationHandler = context.getInteractionManager().getInteractionSimulationHandler();
        
        if (simulationHandler.isCharging(firstRun, time, type, context, ref, cooldownHandler) && this.allowIndefiniteHold) {
            // Button still held - keep interaction active
            // Set chargeValue to -1.0F (CHARGING_HELD) to indicate still charging
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
