package dev.impeccable.aurum.render.terrain;

import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.director.Atmosphere;
import dev.impeccable.aurum.director.WindState;
import dev.impeccable.aurum.render.AurumTextures;
import dev.impeccable.aurum.render.GlUtil;
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
            "u_EntityShadowMap", "u_EntityShadowMatrix", "u_EntityShadowParams"
    };

    public static final int UNIT_NORMAL = 3;
    public static final int UNIT_BOUNCE = 4;
    public static final int UNIT_NOISE = 5;
    public static final int UNIT_SHADOW = 6;
    public static final int UNIT_ENTITY_SHADOW = 7;

    private static final float[] matrix = new float[16];

    /** Set by the frame director: 1 when Aurum lighting applies this frame. */
    public static int mode;
    public static final float[] cameraMod = new float[3];

    private TerrainUniforms() {}

    public static int[] locate(int program) {
        int[] loc = new int[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) loc[i] = GL20C.glGetUniformLocation(program, NAMES[i]);
        return loc;
    }

    public static void upload(int[] loc) {
        AurumConfig c = AurumConfig.get();
        GL20C.glUniform1i(loc[0], mode);
        if (mode == 0) return;
        if (mode == 2) {
            // Shadow-casting pass: only the distortion is needed (and the material atlas for plants).
            GL20C.glUniform4f(loc[18], ShadowRenderer.DISTORTION, 0f, 0f, 0f);
            GL20C.glUniform1i(loc[7], UNIT_NORMAL);
            GlUtil.bindTexture(UNIT_NORMAL, AurumTextures.normalAtlas());
            return;
        }

        GL20C.glUniform3f(loc[1], Atmosphere.lightDir[0], Atmosphere.lightDir[1], Atmosphere.lightDir[2]);
        GL20C.glUniform3fv(loc[2], Atmosphere.lightColor);
        GL20C.glUniform3fv(loc[3], Atmosphere.skyAmbient);
        GL20C.glUniform3fv(loc[4], Atmosphere.groundAmbient);
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
        float strength = shadows ? smooth(0.06f, 0.16f, Atmosphere.lightDir[1]) : 0f;
        float radius = 1.0f + 1.5f * (c.shadowSoftness / 100f);
        GL20C.glUniform4f(loc[18], ShadowRenderer.DISTORTION, strength, ShadowRenderer.centreTexelWorld(), radius);
        GL20C.glUniform4f(loc[19], ShadowRenderer.inverseResolution(), c.shadowResolution >= 1536 ? 8f : 4f, 0.00004f, 0f);

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
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }
}
