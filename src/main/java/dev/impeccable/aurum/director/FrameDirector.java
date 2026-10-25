package dev.impeccable.aurum.director;

import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.render.GpuTimers;
import dev.impeccable.aurum.render.Pipeline;
import dev.impeccable.aurum.render.PostPass;
import dev.impeccable.aurum.render.SkyExtras;
import dev.impeccable.aurum.render.SkyRenderer;
import dev.impeccable.aurum.render.shadow.ShadowRenderer;
import dev.impeccable.aurum.render.terrain.TerrainShaders;
import dev.impeccable.aurum.render.terrain.TerrainUniforms;
import dev.impeccable.aurum.sky.SkyState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Runs once per frame on the CPU before anything is drawn and decides what the GPU passes will do: sun, sky
 * table rebuilds, light colours, fog density, wind, and whether Aurum lighting applies at all this frame.
 */
public final class FrameDirector {
    private FrameDirector() {}

    public static void beginFrame(float tickDelta) {
        dev.impeccable.aurum.render.surface.SurfaceLighting.endWorld();
        GpuTimers.beginFrame();
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        SunState.update(world, tickDelta);

        boolean overworld = world != null
                && world.getDimensionEffects().getSkyType() == DimensionEffects.SkyType.NORMAL
                && world.getDimension().hasSkyLight();
        boolean pipeline = Pipeline.isActive() && overworld;
        if (pipeline) {
            SkyState.update(world, SunState.elevation(), tickDelta);
        }
        boolean haveSky = pipeline && SkyState.current() != null;
        if (haveSky) {
            float gamma = client.options.getGamma().getValue().floatValue();
            Atmosphere.update(SkyState.current(), gamma);
            Camera camera = client.gameRenderer.getCamera();
            FogDirector.update(world, camera.getPos(), tickDelta);
            WindState.update(world, tickDelta, client.isPaused());
        }

        Camera camera = client.gameRenderer.getCamera();
        boolean special = camera.getSubmersionType() != CameraSubmersionType.NONE
                || (client.player != null && (client.player.hasStatusEffect(StatusEffects.BLINDNESS)
                || client.player.hasStatusEffect(StatusEffects.DARKNESS)
                || client.player.hasStatusEffect(StatusEffects.NIGHT_VISION)));
        AurumConfig c = AurumConfig.get();
        TerrainUniforms.mode = haveSky && TerrainShaders.installed() && c.lightingEnabled && !special ? 1 : 0;
        SkyRenderer.active = haveSky && c.skyEnabled && !special;
        SkyExtras.cloudsActive = SkyExtras.wantsClouds(haveSky, special);
        SkyExtras.updateCloudMotion();
        dev.impeccable.aurum.render.WaterPass.tick(client.isPaused());
        dev.impeccable.aurum.render.VoxelLighting.update(world, camera.getPos(),
                TerrainUniforms.mode == 1 && c.voxelLightingEnabled);
    }

    /** Called with the exact camera and matrices this frame's world is drawn with. */
    public static void beforeWorld(Camera camera, Matrix4f view, Matrix4f projection) {
        dev.impeccable.aurum.render.surface.SurfaceLighting.beginWorld(view);
        Vec3d p = camera.getPos();
        ShadowRenderer.cameraX = p.x;
        ShadowRenderer.cameraY = p.y;
        ShadowRenderer.cameraZ = p.z;
        TerrainUniforms.cameraMod[0] = (float) Math.floorMod((long) Math.floor(p.x), 256L) + (float) (p.x - Math.floor(p.x));
        TerrainUniforms.cameraMod[1] = (float) Math.floorMod((long) Math.floor(p.y), 256L) + (float) (p.y - Math.floor(p.y));
        TerrainUniforms.cameraMod[2] = (float) Math.floorMod((long) Math.floor(p.z), 256L) + (float) (p.z - Math.floor(p.z));
        PostPass.captureWorldMatrices(view, projection);
    }
}
