package dev.impeccable.aurum.mixin.sodium;

import dev.impeccable.aurum.render.terrain.TerrainShaders;
import dev.impeccable.aurum.render.terrain.TerrainUniforms;
import me.jellysquid.mods.sodium.client.gl.GlObject;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ShaderBindingContext;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reason: Aurum's terrain shader has extra uniforms. Sodium's own binder throws on unknown names, so Aurum looks
 * them up itself when Sodium creates the program interface, and uploads them when Sodium sets up each pass.
 */
@Mixin(value = ChunkShaderInterface.class, remap = false)
public abstract class ChunkShaderInterfaceMixin {
    @Unique
    private int[] aurum$locations;
    @Unique
    private boolean aurum$translucent;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void aurum$locateUniforms(ShaderBindingContext context, ChunkShaderOptions options, CallbackInfo ci) {
        if (TerrainShaders.installed() && context instanceof GlObject program) {
            this.aurum$locations = TerrainUniforms.locate(program.handle());
            this.aurum$translucent = options.pass().isReverseOrder();
        }
    }

    @Inject(method = "setupState", at = @At("RETURN"), require = 1)
    private void aurum$uploadUniforms(CallbackInfo ci) {
        if (this.aurum$locations != null) {
            TerrainUniforms.upload(this.aurum$locations, this.aurum$translucent);
        }
    }
}
