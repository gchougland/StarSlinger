package com.hexvane.stargrappler.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerProcessMovementSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hexvane.stargrappler.components.StarGrapplerConnectionComponent;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * System that manages Star Grappler physics and connections.
 * Handles launch mode velocity updates and swing mode pendulum physics.
 */
public class StarGrapplerSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float UNHOOK_DISTANCE = 1.5f; // Distance threshold for unhooking
    private static final float MAX_CONNECTION_DISTANCE = 30.0f; // Max distance before auto-unhook
    
    // Run AFTER PlayerProcessMovementSystem so our velocity changes aren't overridden by movement input
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
            new SystemDependency<>(Order.AFTER, PlayerProcessMovementSystem.class)
    );

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
                Player.getComponentType(),
                StarGrapplerConnectionComponent.getComponentType(),
                TransformComponent.getComponentType(),
                Velocity.getComponentType()
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
        
        StarGrapplerConnectionComponent connection = archetypeChunk.getComponent(
                index,
                StarGrapplerConnectionComponent.getComponentType()
        );
        
        if (connection == null || !connection.isConnected() || connection.getStarNodePosition() == null) {
            return;
        }

        // Note: Interactions handle unhooking when buttons are released via tick0() checks
        // This system just applies physics while connected

        TransformComponent transform = archetypeChunk.getComponent(
                index,
                TransformComponent.getComponentType()
        );
        if (transform == null) {
            return;
        }

        Vector3d playerPos = transform.getPosition();
        Vector3d starNodePos = connection.getStarNodePosition();
        
        // Calculate distance to star node
        double dx = starNodePos.x - playerPos.x;
        double dy = starNodePos.y - playerPos.y;
        double dz = starNodePos.z - playerPos.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Check if connection should be broken
        if (distance > MAX_CONNECTION_DISTANCE) {
            // Too far away, disconnect
            connection.setConnected(false);
            return;
        }

        if (connection.isLaunchMode()) {
            // Launch mode: apply continuous force toward the star node
            // Check if player reached the star node
            if (distance <= UNHOOK_DISTANCE) {
                // Player reached the node, unhook but preserve momentum
                connection.setConnected(false);
                return;
            }
            
            // Apply continuous force toward the star node
            applyLaunchForce(archetypeChunk, index, commandBuffer, playerPos, starNodePos, distance, dt);
        } else {
            // Swing mode: apply pendulum physics with rope length constraint
            double ropeLength = connection.getRopeLength();
            if (ropeLength <= 0) {
                // Fallback: use current distance if rope length wasn't set
                ropeLength = distance;
                connection.setRopeLength(ropeLength);
            }
            applySwingPhysics(archetypeChunk, index, commandBuffer, connection, transform, playerPos, starNodePos, distance, ropeLength, dt);
        }
    }

    /**
     * Applies continuous launch force toward the star node.
     * Uses axis-specific speeds based on distance per axis for balanced movement.
     */
    private void applyLaunchForce(
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            int index,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            Vector3d playerPos,
            Vector3d starNodePos,
            double distance,
            float dt) {
        
        // Calculate direction from player to star node
        double dx = starNodePos.x - playerPos.x;
        double dy = starNodePos.y - playerPos.y;
        double dz = starNodePos.z - playerPos.z;
        
        // Normalize direction
        if (distance > 0) {
            dx /= distance;
            dy /= distance;
            dz /= distance;
        }
        
        // Get velocity component
        Velocity velocity = archetypeChunk.getComponent(index, Velocity.getComponentType());
        if (velocity == null) {
            return;
        }
        
        // Calculate horizontal and vertical distances separately
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double verticalDistance = Math.abs(dy);
        
        // Calculate desired speed per axis based on distance per axis
        // This ensures balanced movement: faster in directions where you're further away
        float baseSpeed = 12.0f; // Base speed (blocks per tick)
        float maxDistance = 20.0f; // Maximum effective distance
        
        // Calculate speed factors for horizontal and vertical separately
        float horizontalDistanceFactor = Math.min(1.0f, (float)(horizontalDistance / maxDistance));
        float verticalDistanceFactor = Math.min(1.0f, (float)(verticalDistance / maxDistance));
        
        // Desired speeds increase with distance in each axis
        // Double horizontal speed to compensate for friction
        float desiredHorizontalSpeed = baseSpeed * 2.0f * (0.4f + horizontalDistanceFactor * 0.6f);
        float desiredVerticalSpeed = baseSpeed * (0.4f + verticalDistanceFactor * 0.6f);
        
        // Normalize direction components (handle zero distance cases)
        double horizontalDirX = horizontalDistance > 0.001 ? dx / horizontalDistance : 0.0;
        double horizontalDirZ = horizontalDistance > 0.001 ? dz / horizontalDistance : 0.0;
        double verticalDirY = verticalDistance > 0.001 ? dy / verticalDistance : 0.0;
        
        // Calculate desired velocity vector using axis-specific speeds
        Vector3d desiredVelocity = new Vector3d(
                horizontalDirX * desiredHorizontalSpeed,
                verticalDirY * desiredVerticalSpeed,
                horizontalDirZ * desiredHorizontalSpeed
        );
        
        // Get current velocity
        Vector3d currentVel = velocity.getVelocity();
        
        // Calculate velocity change using spring-like approach
        float horizontalSpringStrength = 1.2f; // Stronger for horizontal to overcome friction
        float verticalSpringStrength = 0.4f; // Moderate for vertical
        Vector3d velocityChange = new Vector3d(
                (desiredVelocity.x - currentVel.x) * horizontalSpringStrength,
                (desiredVelocity.y - currentVel.y) * verticalSpringStrength,
                (desiredVelocity.z - currentVel.z) * horizontalSpringStrength
        );
        
        // Use addInstruction with ChangeVelocityType.Add for proper velocity application
        velocity.addInstruction(velocityChange, null, ChangeVelocityType.Add);
        
        LOGGER.atInfo().log("[StarGrapplerSystem] Applied launch velocity change: %.2f,%.2f,%.2f (desired hSpeed: %.2f, desired vSpeed: %.2f, distance: %.2f, hDist: %.2f, vDist: %.2f)", 
                velocityChange.x, velocityChange.y, velocityChange.z, desiredHorizontalSpeed, desiredVerticalSpeed, distance, horizontalDistance, verticalDistance);
    }
    
    /**
     * Applies smooth pendulum/swing physics with rope length constraint.
     * Uses a spring-damper approach to prevent oscillation:
     * - Spring force: Pulls toward rope length (proportional to excess distance)
     * - Damper force: Dampens radial velocity to prevent overshoot
     * - Only cancel OUTWARD radial velocity (let gravity work naturally)
     * 
     * Key insight: Prevent slingshot effect by:
     * 1. Using soft spring constant (low pull-back strength)
     * 2. Adding damping to radial velocity to prevent overshoot
     * 3. Only cancel OUTWARD radial velocity - let INWARD work with gravity
     */
    private void applySwingPhysics(
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            int index,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull StarGrapplerConnectionComponent connection,
            @Nonnull TransformComponent transform,
            Vector3d playerPos,
            Vector3d starNodePos,
            double currentDistance,
            double ropeLength,
            float dt) {
        
        // Calculate radial direction (from node to player)
        double dx = playerPos.x - starNodePos.x;
        double dy = playerPos.y - starNodePos.y;
        double dz = playerPos.z - starNodePos.z;
        
        // Normalize radial direction
        double radialX = 0.0, radialY = 0.0, radialZ = 0.0;
        if (currentDistance > 0.001) {
            radialX = dx / currentDistance;
            radialY = dy / currentDistance;
            radialZ = dz / currentDistance;
        }

        // Get current velocity
        Velocity velocity = archetypeChunk.getComponent(index, Velocity.getComponentType());
        if (velocity == null) {
            return;
        }

        Vector3d currentVel = velocity.getVelocity();
        
        // DEBUG: Log current velocity before any modifications
        LOGGER.atInfo().log("[StarGrapplerSystem] VELOCITY DEBUG: currentVel=(%.2f,%.2f,%.2f), speed=%.2f", 
                currentVel.x, currentVel.y, currentVel.z, 
                Math.sqrt(currentVel.x*currentVel.x + currentVel.y*currentVel.y + currentVel.z*currentVel.z));
        
        // Calculate radial velocity component (toward/away from node)
        // Positive = moving away from node, Negative = moving toward node (falling)
        double radialVelocity = currentVel.x * radialX + currentVel.y * radialY + currentVel.z * radialZ;
        
        // Calculate tangential velocity (perpendicular to radial - this is what creates swing)
        Vector3d radialVelVector = new Vector3d(
                radialX * radialVelocity,
                radialY * radialVelocity,
                radialZ * radialVelocity
        );
        Vector3d tangentialVel = new Vector3d(
                currentVel.x - radialVelVector.x,
                currentVel.y - radialVelVector.y,
                currentVel.z - radialVelVector.z
        );
        
        // DEBUG: Log velocity decomposition
        double tangentialSpeed = Math.sqrt(
                tangentialVel.x*tangentialVel.x + tangentialVel.y*tangentialVel.y + tangentialVel.z*tangentialVel.z
        );
        LOGGER.atInfo().log("[StarGrapplerSystem] VELOCITY DECOMP: radialVel=%.2f, tangentialVel=(%.2f,%.2f,%.2f), tangentialSpeed=%.2f", 
                radialVelocity, tangentialVel.x, tangentialVel.y, tangentialVel.z, tangentialSpeed);
        
        // Calculate excess distance
        double excessDistance = currentDistance - ropeLength;
        
        // HYSTERESIS: Use different thresholds for entering vs leaving constraint zone
        // This prevents oscillation from constantly crossing the threshold
        // But keep thresholds reasonable so constraint actually applies
        boolean wasPastRopeLength = connection.wasPastRopeLength();
        
        // Larger thresholds with hysteresis - smoother constraint, less jerky
        double constraintEnterThreshold = 0.6; // Apply constraint when exceeding this (increased for smoother)
        double constraintExitThreshold = 0.3; // Stop constraint when below this (increased for smoother)
        
        boolean shouldApplyConstraint;
        if (wasPastRopeLength) {
            // Was past rope length - use exit threshold (lower) to prevent rapid on/off
            shouldApplyConstraint = excessDistance > constraintExitThreshold;
        } else {
            // Wasn't past rope length - use enter threshold (higher) to prevent premature constraint
            shouldApplyConstraint = excessDistance > constraintEnterThreshold;
        }
        
        // Update state for next tick
        connection.setWasPastRopeLength(shouldApplyConstraint);
        
        // MAXIMUM SPEED CAP to prevent slingshotting
        double maxSpeed = 15.0; // Increased for faster swinging (blocks/second)
        double currentSpeed = Math.sqrt(
                currentVel.x * currentVel.x +
                currentVel.y * currentVel.y +
                currentVel.z * currentVel.z
        );
        
        // Apply speed cap if exceeding maximum
        if (currentSpeed > maxSpeed) {
            double scale = maxSpeed / currentSpeed;
            Vector3d speedLimitedVel = new Vector3d(
                    currentVel.x * scale,
                    currentVel.y * scale,
                    currentVel.z * scale
            );
            Vector3d speedLimitChange = new Vector3d(
                    speedLimitedVel.x - currentVel.x,
                    speedLimitedVel.y - currentVel.y,
                    speedLimitedVel.z - currentVel.z
            );
            velocity.addInstruction(speedLimitChange, null, ChangeVelocityType.Add);
            
            LOGGER.atInfo().log("[StarGrapplerSystem] Speed cap applied: %.2f -> %.2f", currentSpeed, maxSpeed);
        }
        
        // Apply constraint when exceeding rope length (with hysteresis)
        // Use SPRING-DAMPER model: F = -k(|x| - d)(x/|x|) - b·v
        // Where k = spring stiffness (smooth curve based on distance), d = rope length, b = damping, v = radial velocity
        if (shouldApplyConstraint) {
            // Check if player is above the node (player Y > node Y)
            boolean isAboveNode = playerPos.y > starNodePos.y;
            
            // SPRING FORCE: F_spring = -k * (currentDistance - ropeLength) * direction
            // Use smooth force curve: stiffness increases smoothly with excess distance
            // This creates natural, smooth constraint that feels good
            
            // Base spring stiffness (N/m equivalent, scaled for game units)
            double baseStiffness = 25.0; // Base stiffness when just past rope length
            
            // Smooth force curve: stiffness increases with excess distance using exponential curve
            // This makes the constraint stronger when further away, but smoothly transitions
            // Using: k = baseStiffness * (1 + excessDistance^1.5)
            // The 1.5 power creates a smooth curve that's stronger than linear but gentler than quadratic
            double stiffnessMultiplier = 1.0 + Math.pow(excessDistance / ropeLength, 1.5);
            double springStiffness = baseStiffness * stiffnessMultiplier;
            
            // Spring force magnitude: k * (distance - desiredDistance)
            // Scale by dt to make it frame-rate independent (we're applying as velocity change directly)
            double springForceMagnitude = springStiffness * excessDistance * dt;
            
            // Cap spring force to prevent excessive forces
            // Use smooth cap that increases with excess distance but never gets too extreme
            double maxSpringForce = 0.5 + excessDistance * 0.3; // Smooth cap: 0.5 at small excess, scales up
            springForceMagnitude = Math.min(springForceMagnitude, maxSpringForce);
            
            // DAMPING FORCE: F_damping = -b * radialVelocity * direction
            // Damping coefficient should scale with stiffness for critical damping feel
            // Critical damping ratio ~0.7-0.9 feels smooth and responsive
            double dampingRatio = 0.8; // Slightly under-damped for responsive feel
            double dampingCoefficient = dampingRatio * 2.0 * Math.sqrt(springStiffness); // Critical damping formula
            double dampingForceMagnitude = dampingCoefficient * radialVelocity * dt;
            
            // Cap damping force to prevent over-damping
            double maxDampingForce = Math.abs(radialVelocity) * 0.4; // Don't damp more than 40% of velocity per tick
            dampingForceMagnitude = Math.max(-maxDampingForce, Math.min(maxDampingForce, dampingForceMagnitude));
            
            // Calculate total constraint force direction (toward node)
            Vector3d constraintDirection = new Vector3d(-radialX, -radialY, -radialZ);
            
            // Combine spring and damping forces
            double totalForceMagnitude = springForceMagnitude - dampingForceMagnitude; // Negative damping = resistance to motion
            
            // CRITICAL: If player is ABOVE node and falling inward, don't apply vertical force (prevents slingshot)
            Vector3d constraintForce;
            if (isAboveNode && radialVelocity < -0.1) {
                // Only horizontal constraint when above node and falling
                constraintForce = new Vector3d(
                        constraintDirection.x * totalForceMagnitude,
                        0.0, // NO vertical force when above node
                        constraintDirection.z * totalForceMagnitude
                );
            } else {
                // Normal constraint (all directions)
                constraintForce = new Vector3d(
                        constraintDirection.x * totalForceMagnitude,
                        constraintDirection.y * totalForceMagnitude,
                        constraintDirection.z * totalForceMagnitude
                );
            }
            
            // Apply constraint force as velocity change
            Vector3d velocityChange = constraintForce;
            
            // Apply velocity change
            velocity.addInstruction(velocityChange, null, ChangeVelocityType.Add);
            
            double springForceMag = springForceMagnitude;
            double dampingForceMag = Math.abs(dampingForceMagnitude);
            LOGGER.atInfo().log("[StarGrapplerSystem] Spring-Damper constraint: distance=%.2f > ropeLength=%.2f, excess=%.2f, radialVel=%.2f, stiffness=%.2f, springForce=%.2f, dampingForce=%.2f, totalForce=%.2f, tangentialSpeed=%.2f, dt=%.3f", 
                    currentDistance, ropeLength, excessDistance, radialVelocity,
                    springStiffness, springForceMag, dampingForceMag, totalForceMagnitude, tangentialVel.length(), dt);
        }
        
        // BOOST HORIZONTAL (X/Z) VELOCITY ONLY to increase swing speed
        // This compensates for air drag on horizontal motion only - preserves horizontal momentum
        // Does NOT affect vertical (Y) motion - gravity still works naturally
        // Calculate horizontal tangential velocity (X and Z components only, no Y)
        Vector3d horizontalTangentialVel = new Vector3d(
                tangentialVel.x,
                0.0, // NO Y component - don't boost vertical motion
                tangentialVel.z
        );
        double horizontalTangentialSpeed = Math.sqrt(
                horizontalTangentialVel.x * horizontalTangentialVel.x +
                horizontalTangentialVel.z * horizontalTangentialVel.z
        );
        
        LOGGER.atInfo().log("[StarGrapplerSystem] HORIZONTAL BOOST DEBUG: horizontalTangentialVel=(%.2f,0.00,%.2f), speed=%.2f, threshold=0.1", 
                horizontalTangentialVel.x, horizontalTangentialVel.z, horizontalTangentialSpeed);
        
        Vector3d horizontalBoost = new Vector3d(0.0, 0.0, 0.0);
        
        // Always ensure minimum horizontal velocity when swinging to prevent vertical bobbing
        // Calculate desired horizontal direction: TOWARD the node (not perpendicular)
        // This creates a swing arc that goes toward/under the node, not around it
        double horizontalRadialLength = Math.sqrt(radialX * radialX + radialZ * radialZ);
        double towardNodeX = 0.0, towardNodeZ = 0.0;
        double perpX = 0.0, perpZ = 0.0; // Keep perpendicular for existing motion direction
        if (horizontalRadialLength > 0.01) {
            // Direction TOWARD the node (opposite of radial, since radial is FROM node TO player)
            towardNodeX = -radialX / horizontalRadialLength;
            towardNodeZ = -radialZ / horizontalRadialLength;
            // Perpendicular direction (for maintaining existing motion direction)
            perpX = -radialZ / horizontalRadialLength;
            perpZ = radialX / horizontalRadialLength;
        }
        
        // Minimum horizontal speed to maintain swing motion (overcome air resistance)
        double minHorizontalSpeed = 8.0; // Increased for faster swinging (blocks/second)
        
        if (horizontalTangentialSpeed > 0.1) {
            // Compensate for air drag on horizontal velocity (~4% loss per tick)
            // Add a MUCH larger boost for faster, more fun swinging
            double airDragCompensation = 0.04; // Compensate for ~4% air drag loss
            double swingBoost = 8.0; // MUCH larger boost for faster swinging (8x boost per tick)
            double totalBoostFactor = airDragCompensation + swingBoost; // Total 8.04x boost per tick
            
            double boostMagnitude = horizontalTangentialSpeed * totalBoostFactor;
            
            // Apply boost ONLY in horizontal direction (X/Z) - preserves horizontal swing momentum
            // NO Y component - vertical motion is unaffected
            horizontalBoost = new Vector3d(
                    horizontalTangentialVel.x / horizontalTangentialSpeed * boostMagnitude,
                    0.0, // NO vertical boost - gravity works naturally
                    horizontalTangentialVel.z / horizontalTangentialSpeed * boostMagnitude
            );
            
            LOGGER.atInfo().log("[StarGrapplerSystem] APPLYING BOOST: boost=(%.2f,0.00,%.2f), magnitude=%.2f, boostFactor=%.2f", 
                    horizontalBoost.x, horizontalBoost.z, boostMagnitude, totalBoostFactor);
            
            // Also ensure minimum horizontal speed is maintained
            // Use TOWARD NODE direction (not perpendicular) to maintain swing arc toward node
            if (horizontalTangentialSpeed < minHorizontalSpeed && horizontalRadialLength > 0.01) {
                double speedDeficit = minHorizontalSpeed - horizontalTangentialSpeed;
                // Use toward-node direction to maintain swing toward node, not around it
                Vector3d minSpeedBoost = new Vector3d(
                        towardNodeX * speedDeficit,
                        0.0,
                        towardNodeZ * speedDeficit
                );
                horizontalBoost = new Vector3d(
                        horizontalBoost.x + minSpeedBoost.x,
                        0.0,
                        horizontalBoost.z + minSpeedBoost.z
                );
                LOGGER.atInfo().log("[StarGrapplerSystem] ADDING MIN SPEED BOOST: deficit=%.2f, minSpeedBoost=(%.2f,0.00,%.2f)", 
                        speedDeficit, minSpeedBoost.x, minSpeedBoost.z);
            }
        } else {
            // CRITICAL FIX: If there's no horizontal motion, generate it to create pendulum swing
            // Always generate horizontal motion when swinging (connected to node) to prevent vertical bobbing
            // This ensures natural pendulum motion even without WASD input
            
            // Use the direction TOWARD the node (not perpendicular) to create swing arc toward node
            if (horizontalRadialLength > 0.01) {
                
                // DEBUG: Log the radial direction and direction toward node
                LOGGER.atInfo().log("[StarGrapplerSystem] TOWARD NODE CALC: radialDir=(%.2f,%.2f,%.2f), horizontalRadialLen=%.2f, towardNodeDir=(%.2f,0.00,%.2f)", 
                        radialX, radialY, radialZ, horizontalRadialLength, towardNodeX, towardNodeZ);
                
                // Generate horizontal velocity to create pendulum swing TOWARD the node
                // This creates a swing arc that goes toward/under the node, not around it
                // MUCH STRONGER base push: We need significant horizontal motion to overcome air resistance
                double basePush = 12.0; // Much increased base horizontal speed (blocks/second)
                
                // Additional push based on falling speed: Convert vertical motion to horizontal
                double fallingSpeed = Math.abs(Math.min(radialVelocity, 0)); // Only when falling (negative radialVel)
                double fallingPush = fallingSpeed * 2.0; // Convert 200% of falling speed to horizontal
                
                // Additional push based on excess distance: More push when further from rope length
                double excessPush = excessDistance * 2.0; // 2.0 blocks/s per block of excess
                
                double totalPush = basePush + fallingPush + excessPush;
                
                // Cap the push to prevent excessive speeds, but allow higher speeds
                totalPush = Math.min(totalPush, 20.0);
                
                // Push TOWARD the node (creates swing arc toward/under node)
                horizontalBoost = new Vector3d(
                        towardNodeX * totalPush,
                        0.0,
                        towardNodeZ * totalPush
                );
                
                LOGGER.atInfo().log("[StarGrapplerSystem] GENERATING INITIAL HORIZONTAL MOTION: boost=(%.2f,0.00,%.2f), fallingSpeed=%.2f, excessDist=%.2f, basePush=%.2f, totalPush=%.2f", 
                        horizontalBoost.x, horizontalBoost.z, fallingSpeed, excessDistance, basePush, totalPush);
            } else {
                // Player is directly above/below node - can't generate horizontal motion
                LOGGER.atInfo().log("[StarGrapplerSystem] BOOST SKIPPED: horizontalRadialLength=%.2f too small (player directly above/below node)", 
                        horizontalRadialLength);
            }
        }
        
        if (horizontalBoost.length() > 0.01) {
            LOGGER.atInfo().log("[StarGrapplerSystem] APPLYING HORIZONTAL BOOST: boost=(%.2f,0.00,%.2f), magnitude=%.2f", 
                    horizontalBoost.x, horizontalBoost.z, horizontalBoost.length());
            velocity.addInstruction(horizontalBoost, null, ChangeVelocityType.Add);
            
            // DEBUG: Get velocity AFTER addInstruction to see if it was queued correctly
            // Note: addInstruction queues the change, so this still shows pre-change velocity
            // But we can at least verify the instruction was queued
            Vector3d afterVel = velocity.getVelocity();
            LOGGER.atInfo().log("[StarGrapplerSystem] VELOCITY AFTER QUEUING BOOST: vel=(%.2f,%.2f,%.2f), speed=%.2f (NOTE: boost is queued, will apply next tick)", 
                    afterVel.x, afterVel.y, afterVel.z, Math.sqrt(afterVel.x*afterVel.x + afterVel.y*afterVel.y + afterVel.z*afterVel.z));
        } else {
            LOGGER.atInfo().log("[StarGrapplerSystem] NO HORIZONTAL BOOST APPLIED: horizontalBoost.length()=%.2f <= 0.01", horizontalBoost.length());
        }
        
        // WASD MOMENTUM BOOST: Apply additional momentum based on player's look direction
        // This makes W (forward) give forward momentum in the swing, making swinging feel more responsive
        Vector3d wasdMomentumBoost = new Vector3d(0.0, 0.0, 0.0);
        
        // Get player's rotation to determine forward direction
        Vector3f rotation = transform.getRotation();
        float yaw = rotation.getYaw();
        
        // Calculate forward direction from yaw (forward = -sin(yaw), 0, -cos(yaw))
        // Hytale uses standard yaw: 0 = north (+Z), increases clockwise
        double forwardX = -Math.sin(yaw);
        double forwardZ = -Math.cos(yaw);
        
        // Normalize forward direction (should already be normalized, but be safe)
        double forwardLength = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        if (forwardLength > 0.01) {
            forwardX /= forwardLength;
            forwardZ /= forwardLength;
            
            // Project forward direction onto tangential plane (perpendicular to radial)
            // This gives us the direction the player wants to swing
            // Forward direction in horizontal plane
            Vector3d forwardDir = new Vector3d(forwardX, 0.0, forwardZ);
            
            // Project forward onto tangential plane by removing radial component
            double forwardRadialComponent = forwardX * radialX + forwardZ * radialZ; // Dot product (Y is 0)
            Vector3d forwardRadialVector = new Vector3d(
                    radialX * forwardRadialComponent,
                    0.0,
                    radialZ * forwardRadialComponent
            );
            Vector3d forwardTangential = new Vector3d(
                    forwardX - forwardRadialVector.x,
                    0.0,
                    forwardZ - forwardRadialVector.z
            );
            
            // Normalize tangential forward direction
            double forwardTangentialLength = Math.sqrt(
                    forwardTangential.x * forwardTangential.x + forwardTangential.z * forwardTangential.z
            );
            
            if (forwardTangentialLength > 0.01) {
                forwardTangential.x /= forwardTangentialLength;
                forwardTangential.z /= forwardTangentialLength;
                
                // Apply momentum boost in forward tangential direction
                // Strength scales with existing horizontal speed (more momentum when already moving)
                // Base boost ensures momentum even when stationary
                double baseMomentumBoost = 3.0; // Base momentum boost (blocks/second)
                double speedBasedBoost = horizontalTangentialSpeed * 0.5; // 50% of current speed as additional boost
                double totalMomentumBoost = baseMomentumBoost + speedBasedBoost;
                
                // Cap momentum boost
                totalMomentumBoost = Math.min(totalMomentumBoost, 8.0);
                
                wasdMomentumBoost = new Vector3d(
                        forwardTangential.x * totalMomentumBoost,
                        0.0, // NO vertical boost
                        forwardTangential.z * totalMomentumBoost
                );
                
                LOGGER.atInfo().log("[StarGrapplerSystem] WASD MOMENTUM BOOST: forwardDir=(%.2f,0.00,%.2f), tangentialDir=(%.2f,0.00,%.2f), boost=(%.2f,0.00,%.2f), magnitude=%.2f", 
                        forwardX, forwardZ, forwardTangential.x, forwardTangential.z, 
                        wasdMomentumBoost.x, wasdMomentumBoost.z, totalMomentumBoost);
            }
        }
        
        // Apply WASD momentum boost
        if (wasdMomentumBoost.length() > 0.01) {
            velocity.addInstruction(wasdMomentumBoost, null, ChangeVelocityType.Add);
        }
        
        // Enhanced debugging: Log constraint decision details
        if (!shouldApplyConstraint) {
            LOGGER.atInfo().log("[StarGrapplerSystem] Within rope length: distance=%.2f, ropeLength=%.2f, excess=%.2f, wasPast=%.1f, enterThresh=%.2f, exitThresh=%.2f, tangentialSpeed=%.2f", 
                    currentDistance, ropeLength, excessDistance, wasPastRopeLength ? 1.0 : 0.0, 
                    constraintEnterThreshold, constraintExitThreshold, tangentialVel.length());
        }
    }
}
