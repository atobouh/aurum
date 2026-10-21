package dev.impeccable.aurum.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.impeccable.aurum.director.FrameDirector;
import dev.impeccable.aurum.render.GpuTimers;
import dev.impeccable.aurum.render.TimerSection;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frame boundaries for the {@link FrameDirector} and GPU timing of the top-level stages (world, hand, HUD).
 * Call sites are wrapped rather than the callees so other mods that overwrite those methods are unaffected.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Unique
    private int aurum$frameTimer = -1;

    /** Reason: the director must run once per frame before any world rendering. */
    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void aurum$beginFrame(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        FrameDirector.beginFrame(tickDelta);
        this.aurum$frameTimer = GpuTimers.begin(TimerSection.FRAME);
    }

    /** Reason: closes the whole-frame GPU timer. */
    @Inject(method = "render", at = @At("RETURN"), require = 1)
    private void aurum$endFrame(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        GpuTimers.end(this.aurum$frameTimer);
        this.aurum$frameTimer = -1;
    }

    /** Reason: GPU time of the HUD, the last stage of the frame. */
    @WrapOperation(method = "render", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;F)V"))
    private void aurum$timeHud(InGameHud hud, DrawContext context, float tickDelta, Operation<Void> original) {
        int t = GpuTimers.begin(TimerSection.HUD);
        original.call(hud, context, tickDelta);
        GpuTimers.end(t);
    }

    /** Reason: GPU time of everything WorldRenderer draws; the per-stage timers live in WorldRendererMixin. */
    @WrapOperation(method = "renderWorld", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V"))
    private void aurum$timeWorld(WorldRenderer worldRenderer, MatrixStack matrices, float tickDelta, long limitTime,
                                 boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                 LightmapTextureManager lightmap, Matrix4f projection, Operation<Void> original) {
        int t = GpuTimers.begin(TimerSection.WORLD);
        original.call(worldRenderer, matrices, tickDelta, limitTime, renderBlockOutline, camera, gameRenderer, lightmap, projection);
        GpuTimers.end(t);
    }

    /** Reason: GPU time of the first-person hand and held item. */
    @WrapOperation(method = "renderWorld", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;renderHand(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/Camera;F)V"))
    private void aurum$timeHand(GameRenderer self, MatrixStack matrices, Camera camera, float tickDelta, Operation<Void> original) {
        int t = GpuTimers.begin(TimerSection.HAND);
        original.call(self, matrices, camera, tickDelta);
        GpuTimers.end(t);
    }
}
