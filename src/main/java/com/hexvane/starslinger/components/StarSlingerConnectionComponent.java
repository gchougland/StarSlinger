package com.hexvane.starslinger.components;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Component to track Star Slinger connection state for a player.
 */
public class StarSlingerConnectionComponent implements Component<EntityStore> {
    @Override
    @Nonnull
    public Component<EntityStore> clone() {
        StarSlingerConnectionComponent cloned = new StarSlingerConnectionComponent();
        cloned.connected = this.connected;
        cloned.launchMode = this.launchMode;
        cloned.astralTetherPosition = this.astralTetherPosition != null ? new Vector3d(this.astralTetherPosition.x, this.astralTetherPosition.y, this.astralTetherPosition.z) : null;
        cloned.connectionTick = this.connectionTick;
        cloned.ropeLength = this.ropeLength;
        cloned.wasPastRopeLength = this.wasPastRopeLength;
        cloned.ticksSinceLastCorrection = this.ticksSinceLastCorrection;
        return cloned;
    }
    private static ComponentType<EntityStore, StarSlingerConnectionComponent> componentType;

    @Nonnull
    public static final BuilderCodec<StarSlingerConnectionComponent> CODEC = BuilderCodec.builder(
            StarSlingerConnectionComponent.class,
            StarSlingerConnectionComponent::new
    )
            .append(new KeyedCodec<>("Connected", Codec.BOOLEAN),
                    (c, v) -> c.connected = v,
                    c -> c.connected)
            .add()
            .append(new KeyedCodec<>("LaunchMode", Codec.BOOLEAN),
                    (c, v) -> c.launchMode = v,
                    c -> c.launchMode)
            .add()
            .append(new KeyedCodec<>("AstralTetherX", Codec.DOUBLE),
                    (c, v) -> {
                        if (c.astralTetherPosition == null) {
                            c.astralTetherPosition = new Vector3d();
                        }
                        c.astralTetherPosition.x = v;
                    },
                    c -> c.astralTetherPosition != null ? c.astralTetherPosition.x : 0.0)
            .add()
            .append(new KeyedCodec<>("AstralTetherY", Codec.DOUBLE),
                    (c, v) -> {
                        if (c.astralTetherPosition == null) {
                            c.astralTetherPosition = new Vector3d();
                        }
                        c.astralTetherPosition.y = v;
                    },
                    c -> c.astralTetherPosition != null ? c.astralTetherPosition.y : 0.0)
            .add()
            .append(new KeyedCodec<>("AstralTetherZ", Codec.DOUBLE),
                    (c, v) -> {
                        if (c.astralTetherPosition == null) {
                            c.astralTetherPosition = new Vector3d();
                        }
                        c.astralTetherPosition.z = v;
                    },
                    c -> c.astralTetherPosition != null ? c.astralTetherPosition.z : 0.0)
            .add()
            .append(new KeyedCodec<>("ConnectionTick", Codec.INTEGER),
                    (c, v) -> c.connectionTick = v,
                    c -> c.connectionTick)
            .add()
            .append(new KeyedCodec<>("RopeLength", Codec.DOUBLE),
                    (c, v) -> c.ropeLength = v,
                    c -> c.ropeLength)
            .add()
            .build();

    private boolean connected = false;
    private boolean launchMode = true; // true for launch, false for swing
    @Nullable
    private Vector3d astralTetherPosition = null;
    private int connectionTick = 0; // Tick when connection was established (for button state check delay)
    private double ropeLength = 0.0; // Initial rope length for swing mode (distance when right-click was first pressed)
    private boolean wasPastRopeLength = false; // Hysteresis: track if we were past rope length last tick
    private int ticksSinceLastCorrection = 0; // Cooldown: don't correct every single tick

    public StarSlingerConnectionComponent() {
    }

    @Nonnull
    public static ComponentType<EntityStore, StarSlingerConnectionComponent> getComponentType() {
        return componentType;
    }

    public static void setComponentType(ComponentType<EntityStore, StarSlingerConnectionComponent> type) {
        componentType = type;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isLaunchMode() {
        return launchMode;
    }

    public void setLaunchMode(boolean launchMode) {
        this.launchMode = launchMode;
    }

    @Nullable
    public Vector3d getAstralTetherPosition() {
        return astralTetherPosition;
    }

    public void setAstralTetherPosition(@Nullable Vector3d astralTetherPosition) {
        this.astralTetherPosition = astralTetherPosition;
    }

    public int getConnectionTick() {
        return connectionTick;
    }

    public void setConnectionTick(int connectionTick) {
        this.connectionTick = connectionTick;
    }

    public double getRopeLength() {
        return ropeLength;
    }

    public void setRopeLength(double ropeLength) {
        this.ropeLength = ropeLength;
    }

    public boolean wasPastRopeLength() {
        return wasPastRopeLength;
    }

    public void setWasPastRopeLength(boolean wasPastRopeLength) {
        this.wasPastRopeLength = wasPastRopeLength;
    }

    public int getTicksSinceLastCorrection() {
        return ticksSinceLastCorrection;
    }

    public void setTicksSinceLastCorrection(int ticksSinceLastCorrection) {
        this.ticksSinceLastCorrection = ticksSinceLastCorrection;
    }
}
