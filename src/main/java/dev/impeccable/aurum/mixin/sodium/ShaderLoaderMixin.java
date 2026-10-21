package dev.impeccable.aurum.mixin.sodium;

import dev.impeccable.aurum.render.terrain.TerrainShaders;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reason: Sodium reads its terrain shader through this single static method. Returning Aurum's source for
 * {@code sodium:blocks/block_layer_opaque.*} (and only that, and only after the fingerprint check in
 * TerrainShaders passed) installs Aurum's lighting and waving plants. Every other path is untouched.
 */
@Mixin(value = ShaderLoader.class, remap = false)
public abstract class ShaderLoaderMixin {
    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, require = 1)
    private static void aurum$replaceTerrainShader(Identifier name, CallbackInfoReturnable<String> cir) {
        String replacement = TerrainShaders.replacement(name);
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }
}
