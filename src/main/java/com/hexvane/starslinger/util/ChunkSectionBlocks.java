package com.hexvane.starslinger.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Section-based block read/write without deprecated {@link World} chunk helpers
 * or {@link com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk} accessors.
 */
public final class ChunkSectionBlocks {
    private ChunkSectionBlocks() {}

    @Nullable
    public static Ref<ChunkStore> sectionRefAt(@Nonnull World world, int x, int y, int z) {
        if (y < ChunkUtil.MIN_Y || y > ChunkUtil.HEIGHT_MINUS_1) {
            return null;
        }
        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }
        return sectionRef;
    }

    @Nullable
    public static BlockType blockType(@Nonnull World world, int x, int y, int z) {
        Ref<ChunkStore> sectionRef = sectionRefAt(world, x, y, z);
        if (sectionRef == null) {
            return null;
        }
        BlockSection section = world.getChunkStore().getStore().getComponent(
                sectionRef,
                BlockSection.getComponentType()
        );
        if (section == null) {
            return null;
        }
        return BlockType.getAssetMap().getAsset(section.get(x, y, z));
    }

    public static boolean setBlock(
            @Nonnull World world,
            int x,
            int y,
            int z,
            int blockId,
            @Nonnull BlockType blockType,
            int rotation,
            int filler,
            int settings
    ) {
        Ref<ChunkStore> sectionRef = sectionRefAt(world, x, y, z);
        if (sectionRef == null) {
            return false;
        }
        return BlockOperations.setBlock(
                world.getChunkStore(),
                sectionRef,
                x,
                y,
                z,
                blockId,
                blockType,
                rotation,
                filler,
                settings
        );
    }
}
