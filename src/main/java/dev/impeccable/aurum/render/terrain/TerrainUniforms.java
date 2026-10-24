package dev.impeccable.aurum.render.terrain;

import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.director.Atmosphere;
import dev.impeccable.aurum.director.WindState;
import dev.impeccable.aurum.render.AurumTextures;
import dev.impeccable.aurum.render.GlUtil;
import dev.impeccable.aurum.render.PostPass;
import dev.impeccable.aurum.render.SkyExtras;
import dev.impeccable.aurum.render.WaterPass;
import dev.impeccable.aurum.render.shadow.EntityShadows;
import dev.impeccable.aurum.render.shadow.ShadowRenderer;
import org.lwjgl.opengl.GL20C;

/**
 * Uploads Aurum's uniforms and textures to a Sodium terrain program. Called from Sodium's
 * {@code ChunkShaderInterface.setupState()} (once per pass, program already bound).
 */
public final class TerrainUniforms {
    static final String[] NAMES = {
            "u_AurumMode", "u_LightDir", "u_LightColor", "u_SkyAmbient", "u_GroundAmbient", "u_BlockLightColor",
            "u_TerrainParams", "u_NormalTex", "u_BounceTex", "u_WindPhase", "u_WindParams", "u_GustOffset",
            "u_CameraMod", "u_NoiseTex", "u_ToneParams", "u_WhiteBalance",
            "u_ShadowMap", "u_ShadowMatrix", "u_ShadowParams", "u_ShadowParams2",
            "u_EntityShadowMap", "u_EntityShadowMatrix", "u_EntityShadowParams",
            "u_ShadowMapPrev", "u_ShadowMatrixPrev",
            "u_AurumPass", "u_SkyLut", "u_SkyGrade", "u_OpaqueDepth", "u_OpaqueColor", "u_WaterNormalTex",
            "u_WaterParams", "u_WaterScroll", "u_SunDirW", "u_SkyTone", "u_PbrTex", "u_PbrParams", "u_PbrScale",
            "u_SkySideToward", "u_SkySideAway", "u_CloudReflection", "u_PrevCloudViewProj", "u_CloudReflectionOn",
            "u_SkyAppearance", "u_WaterLake", "u_VoxelLight", "u_VoxelParams", "u_VoxelOrigin",
            "u_CloudSdf", "u_CloudShadow", "u_CloudLayout", "u_FoliageDepth", "u_PixelLight", "u_LightingDebug"
    };

    public static final int UNIT_NORMAL = 3;
    public static final int UNIT_BOUNCE = 4;
    public static final int UNIT_NOISE = 5;
    public static final int UNIT_SHADOW = 6;
    public static final int UNIT_ENTITY_SHADOW = 7;
    public static final int UNIT_SHADOW_PREV = 8;
    public static final int UNIT_OPAQUE_DEPTH = 9;
    public static final int UNIT_SKY_LUT = 10;
    public static final int UNIT_OPAQUE_COLOR = 11;
    /** Above GlStateManager's 12 tracked units: bound with raw GL. */
    public static final int UNIT_WATER_NORMAL = 12;
    public static final int UNIT_PBR = 13;
    public static final int UNIT_CLOUD_REFLECTION = 14;

    private static final float[] matrix = new float[16];

    /** Set by the frame director: 1 when Aurum lighting applies this frame. */
    public static int mode;
    public static final float[] cameraMod = new float[3];
    /** Shadow-map centre relative to its virtual light camera; used to bound small plant casters. */
    public static final float[] shadowCentre = new float[3];

    private TerrainUniforms() {}

    public static int[] locate(int program) {
        int[] loc = new int[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) loc[i] = GL20C.glGetUniformLocation(program, NAMES[i]);
        return loc;
    }

    public static void upload(int[] loc, boolean translucent) {
        AurumConfig c = AurumConfig.get();
        GL20C.glUniform1i(loc[0], mode);
        GL20C.glUniform1i(loc[53], dev.impeccable.aurum.debug.LightingDebug.mode());
        if (mode == 0) return;
        if (mode == 2) {
            // Reuse terrain parameters in depth-only mode to bound small plant casters near the map centre.
            float plantRadius = c.shadowResolution <= 1024 ? 8f : 16f;
            GL20C.glUniform4f(loc[6], shadowCentre[0], shadowCentre[1], shadowCentre[2], plantRadius);
            GL20C.glUniform4f(loc[18], ShadowRenderer.DISTORTION, 0f, 0f, 0f);
            GL20C.glUniform1i(loc[7], UNIT_NORMAL);
            GlUtil.bindTexture(UNIT_NORMAL, AurumTextures.normalAtlas());
            return;
        }

        GL20C.glUniform3f(loc[1], Atmosphere.lightDir[0], Atmosphere.lightDir[1], Atmosphere.lightDir[2]);
        GL20C.glUniform3fv(loc[2], Atmosphere.lightColor);
        GL20C.glUniform3fv(loc[3], Atmosphere.skyAmbient);
        GL20C.glUniform3fv(loc[4], Atmosphere.groundAmbient);
        GL20C.glUniform3fv(loc[38], Atmosphere.skySideToward);
        GL20C.glUniform3fv(loc[39], Atmosphere.skySideAway);
        GL20C.glUniform3fv(loc[5], Atmosphere.blockLight);
        float normals = c.normalsEnabled ? c.normalStrength / 100f * 0.9f : 0f;
        float bounce = c.bounceEnabled ? c.bounceStrength / 100f * 0.35f : 0f;
        GL20C.glUniform4f(loc[6], normals, c.aoBoost / 100f, Atmosphere.minLight, bounce);
        GL20C.glUniform1i(loc[7], UNIT_NORMAL);
        GL20C.glUniform1i(loc[8], UNIT_BOUNCE);
        GL20C.glUniform4fv(loc[9], WindState.phase);
        GL20C.glUniform4f(loc[10], WindState.dirX, WindState.dirZ, WindState.strength, 1f);
        GL20C.glUniform2f(loc[11], WindState.gustX, WindState.gustZ);
        GL20C.glUniform3fv(loc[12], cameraMod);
        GL20C.glUniform1i(loc[13], UNIT_NOISE);
        GL20C.glUniform4f(loc[14], c.tonemap.ordinal(), Atmosphere.exposure, 0f, 0f);
        GL20C.glUniform3fv(loc[15], Atmosphere.whiteBalance);

        boolean shadows = ShadowRenderer.usable();
        GL20C.glUniform1i(loc[16], UNIT_SHADOW);
        if (shadows) {
            ShadowRenderer.samplingMatrix().get(matrix);
            GL20C.glUniformMatrix4fv(loc[17], false, matrix);
        }
        // Preserve the map through golden hour. The previous fade began at ~5 degrees and did not reach full
        // strength until ~12 degrees, erasing the long low-sun shadows seen in the matched reference scenes.
        float strength = shadows ? smooth(0.0f, 0.08f, Atmosphere.lightDir[1]) : 0f;
        float radius = 1.0f + 1.5f * (c.shadowSoftness / 100f);
        GL20C.glUniform4f(loc[18], ShadowRenderer.DISTORTION, strength, ShadowRenderer.centreTexelWorld(), radius);
        float blend = ShadowRenderer.blend();
        GL20C.glUniform4f(loc[19], ShadowRenderer.inverseResolution(), c.shadowResolution >= 1536 ? 8f : 4f, 0.00004f, blend);
        GL20C.glUniform1i(loc[23], UNIT_SHADOW_PREV);
        if (shadows && blend < 1f) {
            ShadowRenderer.previousSamplingMatrix().get(matrix);
            GL20C.glUniformMatrix4fv(loc[24], false, matrix);
        }
        GlUtil.bindTexture(UNIT_SHADOW_PREV, ShadowRenderer.previousTexture());

        GlUtil.bindTexture(UNIT_NORMAL, AurumTextures.normalAtlas());
        GlUtil.bindTexture(UNIT_BOUNCE, AurumTextures.bounce());
        GlUtil.bindTexture(UNIT_NOISE, AurumTextures.noise());
        GlUtil.bindTexture(UNIT_SHADOW, ShadowRenderer.frontTexture());

        boolean entities = shadows && EntityShadows.usable();
        GL20C.glUniform1i(loc[20], UNIT_ENTITY_SHADOW);
        if (entities) {
            EntityShadows.samplingMatrix().get(matrix);
            GL20C.glUniformMatrix4fv(loc[21], false, matrix);
        }
        GL20C.glUniform4f(loc[22], entities ? 1f : 0f, EntityShadows.inverseResolution(), 0f, 0f);
        GlUtil.bindTexture(UNIT_ENTITY_SHADOW, entities ? EntityShadows.texture() : ShadowRenderer.frontTexture());

        // Sky table and sun direction: water reflections and PBR sky reflections, every pass.
        GL20C.glUniform1i(loc[26], UNIT_SKY_LUT);
        GL20C.glUniform1f(loc[27], Atmosphere.skyGrade());
        GL20C.glUniform4f(loc[34], Atmosphere.skyTone[0], Atmosphere.skyTone[1], Atmosphere.golden, 0f);
        GL20C.glUniform4f(loc[43], Atmosphere.skyAppearance[0], Atmosphere.skyAppearance[1], 0f, 0f);
        GL20C.glUniform3f(loc[33], Atmosphere.sunDir[0], Atmosphere.sunDir[1], Atmosphere.sunDir[2]);
        GlUtil.bindTexture(UNIT_SKY_LUT, dev.impeccable.aurum.sky.SkyState.texture());
        boolean voxel = mode == 1 && dev.impeccable.aurum.render.VoxelLighting.usable();
        GL20C.glUniform1i(loc[45], 15);
        GL20C.glUniform4f(loc[46], dev.impeccable.aurum.render.VoxelLighting.SIDE,
                dev.impeccable.aurum.render.VoxelLighting.detail(),
                voxel ? (float) dev.impeccable.aurum.render.VoxelLighting.width() / dev.impeccable.aurum.render.VoxelLighting.detail() : 1f,
                voxel ? c.voxelLightingStrength / 100f : 0f);
        GL20C.glUniform3f(loc[47],
                (float) (dev.impeccable.aurum.render.VoxelLighting.originX() - ShadowRenderer.cameraX),
                (float) (dev.impeccable.aurum.render.VoxelLighting.originY() - ShadowRenderer.cameraY),
                (float) (dev.impeccable.aurum.render.VoxelLighting.originZ() - ShadowRenderer.cameraZ));
        GlUtil.bindTextureHigh(15, voxel ? dev.impeccable.aurum.render.VoxelLighting.texture() : 0);

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        boolean cloudShadow = SkyExtras.cloudsActive && c.cloudShadowsEnabled && client.world != null;
        GL20C.glUniform1f(loc[51], c.foliageDepth / 100f);
        GL20C.glUniform4f(loc[52], c.lightEdgeStyle == dev.impeccable.aurum.config.LightEdgeStyle.VOXEL_CRISP ? 1f : 0f,
                c.lightPixelGrid, 0f, 0f);
        GL20C.glUniform1i(loc[48], 2);
        GL20C.glUniform4f(loc[49], client.world == null ? 0f :
                        (float) (client.world.getDimensionEffects().getCloudsHeight() + c.cloudHeight - ShadowRenderer.cameraY + 7),
                cloudShadow ? c.cloudShadowStrength / 100f : 0f,
                SkyExtras.cloudDrift(), c.cloudMotionVariation / 100f);
        GL20C.glUniform4f(loc[50], SkyExtras.cloudOffsetX(ShadowRenderer.cameraX),
                SkyExtras.cloudOffsetZ(ShadowRenderer.cameraZ),
                client.world == null ? 0f : Math.max(-SkyExtras.cloudCoverage(client.world, client.getTickDelta()),
                        -0.8f * client.world.getRainGradient(client.getTickDelta())), c.cloudsPackShapes ? 1f : 0f);
        // Sodium uses units 0/1 for block atlas/lightmap. Unit 2 is free; preserve both bindings.
        GlUtil.bindTexture(2, dev.impeccable.aurum.bake.CloudBaker.texture());

        // LabPBR: specular and emission whenever pack data exists; parallax (3D blocks) only in opaque passes.
        boolean pbr = c.labPbr && dev.impeccable.aurum.bake.AtlasBaker.packHasPbr;
        boolean relief = pbr || dev.impeccable.aurum.bake.AtlasBaker.generatedRelief;
        GL20C.glUniform1i(loc[35], UNIT_PBR);
        float pomDepth = relief && c.pomEnabled && !translucent ? 0.22f * c.pomDepth / 100f : 0f;
        float gloss = c.sheenEnabled ? c.sheenStrength / 100f : 0f;
        GL20C.glUniform4f(loc[36], pbr ? Math.max(gloss, 0.001f) : 0f, pomDepth, (float) c.pomDistance, pbr ? 2.5f : 0f);
        GlUtil.bindTextureHigh(UNIT_PBR, AurumTextures.pbrAtlas());
        GL20C.glUniform1f(loc[37], dev.impeccable.aurum.bake.AtlasBaker.scale);

        boolean water = translucent && WaterPass.captured && dev.impeccable.aurum.sky.SkyState.current() != null;
        GL20C.glUniform1i(loc[25], water ? 1 : 0);
        if (water) {
            GL20C.glUniform1f(loc[44], c.waterStyle == dev.impeccable.aurum.config.WaterStyle.CALM_LAKE
                    ? c.waterStyleBlend / 100f : 0f);
            GL20C.glUniform1i(loc[28], UNIT_OPAQUE_DEPTH);
            GL20C.glUniform1i(loc[29], UNIT_OPAQUE_COLOR);
            GL20C.glUniform1i(loc[30], UNIT_WATER_NORMAL);
            net.minecraft.client.gl.Framebuffer fb = net.minecraft.client.MinecraftClient.getInstance().getFramebuffer();
            float waves = c.waveStrength / 100f;
            GL20C.glUniform4f(loc[31], 1f / fb.textureWidth, 1f / fb.textureHeight, c.waterReflections ? 1f : 0f, waves);
            // Ripple layers scroll in different directions at different speeds; offsets wrapped to one tile.
            double t = WaterPass.time();
            GL20C.glUniform4f(loc[32], (float) ((t * 0.045) % 1.0), (float) ((t * 0.018) % 1.0),
                    (float) ((t * -0.040) % 1.0), (float) ((t * 0.055) % 1.0));
            GlUtil.bindTexture(UNIT_OPAQUE_DEPTH, WaterPass.depthTexture());
            GlUtil.bindTexture(UNIT_OPAQUE_COLOR, WaterPass.colorTexture());
            GlUtil.bindTextureHigh(UNIT_WATER_NORMAL, AurumTextures.waterNormal());
            boolean clouds = PostPass.hasCloudReflection();
            GL20C.glUniform1i(loc[40], UNIT_CLOUD_REFLECTION);
            GL20C.glUniform1f(loc[42], clouds ? 1f : 0f);
            if (clouds) {
                PostPass.cloudViewProjection().get(matrix);
                GL20C.glUniformMatrix4fv(loc[41], false, matrix);
                GlUtil.bindTextureHigh(UNIT_CLOUD_REFLECTION, SkyExtras.cloudTexture());
            } else {
                GlUtil.bindTextureHigh(UNIT_CLOUD_REFLECTION, 0);
            }
        }
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }
}
