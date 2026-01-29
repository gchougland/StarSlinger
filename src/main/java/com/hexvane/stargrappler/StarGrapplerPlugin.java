package com.hexvane.stargrappler;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hexvane.stargrappler.interactions.StarGrapplerLaunchInteraction;
import com.hexvane.stargrappler.interactions.StarGrapplerSwingInteraction;
import com.hexvane.stargrappler.interactions.GalaxyBottleInteraction;
import com.hexvane.stargrappler.systems.StarGrapplerSystem;
import com.hexvane.stargrappler.systems.StarNodeParticleSystem;
import com.hexvane.stargrappler.systems.GalaxyBottleProjectileSystem;
import com.hexvane.stargrappler.components.StarGrapplerConnectionComponent;

public class StarGrapplerPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public StarGrapplerPlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from %s version %s", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        // Register custom component
        ComponentType<EntityStore, StarGrapplerConnectionComponent> componentType = 
                this.getEntityStoreRegistry().registerComponent(
                        StarGrapplerConnectionComponent.class,
                        "StarGrapplerConnection",
                        StarGrapplerConnectionComponent.CODEC
                );
        StarGrapplerConnectionComponent.setComponentType(componentType);
        LOGGER.atInfo().log("Registered StarGrapplerConnectionComponent");
        
        // Register custom interactions
        Interaction.CODEC.register("StarGrapplerLaunch", StarGrapplerLaunchInteraction.class, StarGrapplerLaunchInteraction.CODEC);
        Interaction.CODEC.register("StarGrapplerSwing", StarGrapplerSwingInteraction.class, StarGrapplerSwingInteraction.CODEC);
        Interaction.CODEC.register("GalaxyBottle", GalaxyBottleInteraction.class, GalaxyBottleInteraction.CODEC);
        
        LOGGER.atInfo().log("Registered StarGrappler interactions");
    }

    @Override
    protected void start() {
        // Register systems (after assets/modules are loaded)
        this.getEntityStoreRegistry().registerSystem(new StarGrapplerSystem());
        this.getEntityStoreRegistry().registerSystem(new StarNodeParticleSystem());
        this.getEntityStoreRegistry().registerSystem(new GalaxyBottleProjectileSystem());
        
        LOGGER.atInfo().log("Registered StarGrappler systems");
    }
}
