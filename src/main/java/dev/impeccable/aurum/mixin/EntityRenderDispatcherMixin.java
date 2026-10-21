package dev.impeccable.aurum.mixin;

import dev.impeccable.aurum.render.shadow.EntityShadows;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reason: entities near the player get a real shadow from Aurum; skip vanilla's round blob for them only. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true, require = 1)
    private static void aurum$skipBlob(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Entity entity,
                                       float opacity, float tickDelta, WorldView world, float radius, CallbackInfo ci) {
        if (EntityShadows.replacesBlob(entity)) {
            ci.cancel();
        }
    }
}
