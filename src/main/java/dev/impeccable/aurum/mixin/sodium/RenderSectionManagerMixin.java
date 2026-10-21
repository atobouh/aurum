package dev.impeccable.aurum.mixin.sodium;

import dev.impeccable.aurum.render.shadow.ShadowRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

/** Reason: the cached shadow map must be redrawn when a chunk mesh near the player changes (a block placed or broken). */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class RenderSectionManagerMixin {
    @Inject(method = "processChunkBuildResults", at = @At("HEAD"), require = 1)
    private void aurum$markShadowDirty(ArrayList<ChunkBuildOutput> results, CallbackInfo ci) {
        for (ChunkBuildOutput result : results) {
            ShadowRenderer.onSectionRebuilt(result.render.getChunkX(), result.render.getChunkY(), result.render.getChunkZ());
        }
    }
}
