package com.hexvane.starslinger;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hexvane.starslinger.interactions.StarSlingerLaunchInteraction;
import com.hexvane.starslinger.interactions.StarSlingerSwingInteraction;
import com.hexvane.starslinger.interactions.GalaxyBottleInteraction;
import com.hexvane.starslinger.systems.StarSlingerSystem;
import com.hexvane.starslinger.systems.AstralTetherParticleSystem;
import com.hexvane.starslinger.systems.GalaxyBottleProjectileSystem;
import com.hexvane.starslinger.components.StarSlingerConnectionComponent;
import com.hexvane.starslinger.util.DebugLogger;

public class StarSlingerPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public StarSlingerPlugin(JavaPluginInit init) {
        super(init);
        DebugLogger.debugInfo(LOGGER, "Hello from %s version %s", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        // Register custom component
        ComponentType<EntityStore, StarSlingerConnectionComponent> componentType = 
                this.getEntityStoreRegistry().registerComponent(
                        StarSlingerConnectionComponent.class,
                        "StarSlingerConnection",
                        StarSlingerConnectionComponent.CODEC
                );
        StarSlingerConnectionComponent.setComponentType(componentType);
        DebugLogger.debugInfo(LOGGER, "Registered StarSlingerConnectionComponent");
        
        // Register custom interactions
        Interaction.CODEC.register("StarSlingerLaunch", StarSlingerLaunchInteraction.class, StarSlingerLaunchInteraction.CODEC);
        Interaction.CODEC.register("StarSlingerSwing", StarSlingerSwingInteraction.class, StarSlingerSwingInteraction.CODEC);
        Interaction.CODEC.register("GalaxyBottle", GalaxyBottleInteraction.class, GalaxyBottleInteraction.CODEC);
        
        DebugLogger.debugInfo(LOGGER, "Registered Star Slinger interactions");
    }

    @Override
    protected void start() {
        // Register systems (after assets/modules are loaded)
        this.getEntityStoreRegistry().registerSystem(new StarSlingerSystem());
        this.getEntityStoreRegistry().registerSystem(new AstralTetherParticleSystem());
        this.getEntityStoreRegistry().registerSystem(new GalaxyBottleProjectileSystem());
        
        DebugLogger.debugInfo(LOGGER, "Registered Star Slinger systems");
    }
}
