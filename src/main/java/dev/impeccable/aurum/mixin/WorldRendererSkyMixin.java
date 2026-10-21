package dev.impeccable.aurum.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.impeccable.aurum.render.SkyRenderer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Replaces the parts of vanilla's overworld sky that Aurum draws itself, and keeps the rest (stars, moon).
 * Each hook does nothing unless {@link SkyRenderer#active} is set for this frame.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererSkyMixin {
    private static final String RENDER_SKY = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V";
    private static final String VB_DRAW = "Lnet/minecraft/client/gl/VertexBuffer;draw(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/gl/ShaderProgram;)V";

    /** Reason: the first VertexBuffer draw in renderSky is the sky dome; Aurum's sky is drawn in its place. */
    @WrapOperation(method = RENDER_SKY, require = 1, at = @At(value = "INVOKE", target = VB_DRAW, ordinal = 0))
    private void aurum$replaceDome(VertexBuffer buffer, Matrix4f view, Matrix4f projection, ShaderProgram shader, Operation<Void> original) {
        if (!SkyRenderer.draw(view, projection)) {
            original.call(buffer, view, projection, shader);
        }
    }

    /** Reason: the third draw is the black "void" disc below the horizon; Aurum's sky covers that range. */
    @WrapOperation(method = RENDER_SKY, require = 1, at = @At(value = "INVOKE", target = VB_DRAW, ordinal = 2))
    private void aurum$skipDarkSky(VertexBuffer buffer, Matrix4f view, Matrix4f projection, ShaderProgram shader, Operation<Void> original) {
        if (!SkyRenderer.active) {
            original.call(buffer, view, projection, shader);
        }
    }

    /** Reason: vanilla's sunrise/sunset fan; Aurum's sky has its own golden hour. */
    @WrapOperation(method = RENDER_SKY, require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/DimensionEffects;getFogColorOverride(FF)[F"))
    private float[] aurum$skipSunsetFan(DimensionEffects effects, float skyAngle, float tickDelta, Operation<float[]> original) {
        return SkyRenderer.active ? null : original.call(effects, skyAngle, tickDelta);
    }

    /** Reason: 30.0 is the vanilla sun quad's size; zero hides the square sun, Aurum draws a round disc. */
    @ModifyConstant(method = RENDER_SKY, require = 1, constant = @Constant(floatValue = 30.0F))
    private float aurum$hideSquareSun(float size) {
        return SkyRenderer.active ? 0.0F : size;
    }
}
