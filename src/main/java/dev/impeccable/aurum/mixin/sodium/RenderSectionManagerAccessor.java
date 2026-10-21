package dev.impeccable.aurum.mixin.sodium;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reason: the shadow pass looks sections up by position (the camera's visibility lists only cover what the player
 * sees, not what casts shadows into view) and draws them with Sodium's own chunk renderer. Read only.
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public interface RenderSectionManagerAccessor {
    @Accessor("sectionByPosition")
    Long2ReferenceMap<RenderSection> aurum$getSectionByPosition();

    @Accessor("chunkRenderer")
    ChunkRenderer aurum$getChunkRenderer();
}
