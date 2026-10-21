package dev.impeccable.aurum.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.impeccable.aurum.render.GpuTimers;
import dev.impeccable.aurum.render.SkyExtras;
import dev.impeccable.aurum.render.TimerSection;
import dev.impeccable.aurum.render.shadow.ShadowRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * GPU timing of the world stages. Sodium {@code @Overwrite}s renderLayer and renderClouds, so these wrap the call
 * sites inside {@code WorldRenderer.render} (which Sodium leaves alone) instead of the overwritten methods.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    /** Reason: GPU time of the vanilla sky (replaced by Aurum's sky in Phase 1). */
    @WrapOperation(method = "render", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V"))
    private void aurum$timeSky(WorldRenderer self, MatrixStack matrices, Matrix4f projection, float tickDelta,
                               Camera camera, boolean thickFog, Runnable fogCallback, Operation<Void> original) {
        int t = GpuTimers.begin(TimerSection.SKY);
        original.call(self, matrices, projection, tickDelta, camera, thickFog, fogCallback);
        GpuTimers.end(t);
    }

    /** Reason: GPU time of Sodium's terrain passes, opaque and translucent. */
    @WrapOperation(method = "render", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;renderLayer(Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/util/math/MatrixStack;DDDLorg/joml/Matrix4f;)V"))
    private void aurum$timeLayer(WorldRenderer self, RenderLayer layer, MatrixStack matrices, double x, double y, double z,
                                 Matrix4f projection, Operation<Void> original) {
        // Sodium draws its SOLID and CUTOUT passes both inside the solid call; the cutout calls draw nothing.
        TimerSection section = layer == RenderLayer.getTranslucent() || layer == RenderLayer.getTripwire()
                ? TimerSection.TERRAIN_TRANSLUCENT : TimerSection.TERRAIN_SOLID;
        if (layer == RenderLayer.getSolid()) {
            // The shadow map must be ready before Sodium draws the terrain that samples it.
            ShadowRenderer.update();
        }
        int t = GpuTimers.begin(section);
        original.call(self, layer, matrices, x, y, z, projection);
        GpuTimers.end(t);
    }

    /** Reason: GPU time of vanilla clouds, and skipping them while Aurum draws its own (post pass). */
    @WrapOperation(method = "render", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;renderClouds(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FDDD)V"))
    private void aurum$timeClouds(WorldRenderer self, MatrixStack matrices, Matrix4f projection, float tickDelta,
                                  double x, double y, double z, Operation<Void> original) {
        if (SkyExtras.cloudsActive) return;
        int t = GpuTimers.begin(TimerSection.CLOUDS);
        original.call(self, matrices, projection, tickDelta, x, y, z);
        GpuTimers.end(t);
    }

    /** Reason: GPU time of rain and snow. */
    @WrapOperation(method = "render", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;renderWeather(Lnet/minecraft/client/render/LightmapTextureManager;FDDD)V"))
    private void aurum$timeWeather(WorldRenderer self, LightmapTextureManager lightmap, float tickDelta,
                                   double x, double y, double z, Operation<Void> original) {
        int t = GpuTimers.begin(TimerSection.WEATHER);
        original.call(self, lightmap, tickDelta, x, y, z);
        GpuTimers.end(t);
    }
}
