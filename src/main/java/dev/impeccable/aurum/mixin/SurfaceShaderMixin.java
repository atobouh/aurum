package dev.impeccable.aurum.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.impeccable.aurum.AurumMod;
import dev.impeccable.aurum.render.surface.CoreShaderSources;
import dev.impeccable.aurum.render.surface.SurfaceLighting;
import dev.impeccable.aurum.render.terrain.TerrainUniforms;
import net.minecraft.client.gl.GlImportProcessor;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderStage;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.resource.ResourceFactory;
import org.lwjgl.opengl.GL20C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Mixin(ShaderProgram.class)
public abstract class SurfaceShaderMixin {
    @Unique private int[] aurum$surfaceLocations;
    @Unique private int aurum$inverseView;
    @Unique private long aurum$surfaceFrame = -1;

    @WrapOperation(method = "loadShader", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/ShaderStage;createFromResource(Lnet/minecraft/client/gl/ShaderStage$Type;Ljava/lang/String;Ljava/io/InputStream;Ljava/lang/String;Lnet/minecraft/client/gl/GlImportProcessor;)Lnet/minecraft/client/gl/ShaderStage;"), require = 1)
    private static ShaderStage aurum$adaptStage(ShaderStage.Type type, String name, InputStream stream,
            String pack, GlImportProcessor imports, Operation<ShaderStage> original,
            ResourceFactory resources, ShaderStage.Type enclosingType, String enclosingName) throws IOException {
        if (!CoreShaderSources.eligible(resources, name)) return original.call(type, name, stream, pack, imports);
        byte[] bytes = stream.readAllBytes();
        try {
            String source = CoreShaderSources.patch(new String(bytes, StandardCharsets.UTF_8), type == ShaderStage.Type.VERTEX);
            return original.call(type, name, new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), pack, imports);
        } catch (Exception e) {
            CoreShaderSources.failed(name);
            AurumMod.LOGGER.error("Shared lighting shader {} failed; disabling Aurum rendering", name, e);
            dev.impeccable.aurum.render.Pipeline.disable("A shared model shader failed to compile");
            return original.call(type, name, new ByteArrayInputStream(bytes), pack, imports);
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void aurum$locateSurface(ResourceFactory resources, String name, VertexFormat format, CallbackInfo ci) throws IOException {
        ShaderProgram self = (ShaderProgram) (Object) this;
        if (CoreShaderSources.eligible(resources, name) && GL20C.glGetUniformLocation(self.getGlRef(), "u_AurumMode") >= 0) {
            aurum$surfaceLocations = TerrainUniforms.locate(self.getGlRef());
            aurum$inverseView = GL20C.glGetUniformLocation(self.getGlRef(), "u_AurumInvView");
            SurfaceLighting.registerAdapter();
            AurumMod.LOGGER.info("Shared surface lighting adapter: {}", name);
        }
    }
    @Inject(method = "bind", at = @At("RETURN"), require = 1)
    private void aurum$bindSurface(CallbackInfo ci) {
        if (aurum$surfaceLocations != null) {
            SurfaceLighting.upload(aurum$surfaceLocations, aurum$inverseView, aurum$surfaceFrame == SurfaceLighting.frame());
            if (SurfaceLighting.active()) aurum$surfaceFrame = SurfaceLighting.frame();
        }
    }
}
