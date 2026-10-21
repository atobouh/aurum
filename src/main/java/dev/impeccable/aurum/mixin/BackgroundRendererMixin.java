package dev.impeccable.aurum.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.impeccable.aurum.render.terrain.TerrainUniforms;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reason: when Aurum's post pass does the fog, vanilla's distance fog on entities, particles and clouds would be
 * applied twice. Push it beyond the render distance for normal terrain fog only; water, lava, powder snow and
 * blindness keep vanilla fog (Aurum lighting is off in those cases anyway).
 */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @Inject(method = "applyFog", at = @At("RETURN"), require = 1)
    private static void aurum$deferFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
                                       boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (TerrainUniforms.mode == 1 && fogType == BackgroundRenderer.FogType.FOG_TERRAIN && !thickFog) {
            RenderSystem.setShaderFogStart(1.0e6F);
            RenderSystem.setShaderFogEnd(1.0e6F + 1.0F);
        }
    }
}
