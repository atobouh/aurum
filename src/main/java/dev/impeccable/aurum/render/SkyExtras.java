package dev.impeccable.aurum.render;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.impeccable.aurum.bake.CloudBaker;
import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.director.Atmosphere;
import dev.impeccable.aurum.director.FogDirector;
import dev.impeccable.aurum.sky.SkyLut;
import dev.impeccable.aurum.sky.SkyState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

/**
 * Phase 4 sky extras, run inside the post pass before the composite (docs/research/SKY_EXTRAS.md):
 * <ol>
 *   <li>Clouds at 1/2 resolution (replace vanilla's clouds).</li>
 *   <li>God rays at 1/4 resolution: open-sky mask, then a radial blur toward the sun.</li>
 *   <li>Lens-flare occlusion into one texel, read directly by the composite (no CPU readback).</li>
 *   <li>Bloom: threshold to 1/4, dual-filter down to 1/32 and back up to 1/4.</li>
 * </ol>
 * Each step is skipped when it would not be visible (sun behind the camera, below the horizon, effect off).
 */
public final class SkyExtras {
    private static final int UNIT_A = 6, UNIT_B = 7, UNIT_C = 8;
    private static final int BLOOM_LEVELS = 4;

    /** True when Aurum draws the clouds this frame, so vanilla's are skipped (set by the frame director). */
    public static boolean cloudsActive;

    private static int width, height;
    private static int cloudTex, cloudFbo, cloudW, cloudH;
    private static int maskTex, maskFbo, raysTex, raysFbo, quarterW, quarterH;
    private static final int[] flareTex = new int[2], flareFbo = new int[2];
    private static int flareIndex;
    private static final int[] bloomTex = new int[BLOOM_LEVELS], bloomFbo = new int[BLOOM_LEVELS];
    private static final int[] upTex = new int[BLOOM_LEVELS - 1], upFbo = new int[BLOOM_LEVELS - 1];
    private static final int[] bloomW = new int[BLOOM_LEVELS], bloomH = new int[BLOOM_LEVELS];

    // Results for the composite.
    public static boolean cloudsDrawn, raysDrawn, flareOn, bloomDrawn;
    public static float rayStrength, flareStrength, bloomStrength;
    public static final float[] sunScreen = new float[4];
    public static final float[] cloudSlab = new float[4];

    private static final Matrix4f viewProj = new Matrix4f();
    private static final Vector4f clip = new Vector4f();

    private SkyExtras() {}

    public static int cloudTexture() {
        return cloudTex;
    }

    public static int raysTexture() {
        return raysTex;
    }

    public static int flareTexture() {
        return flareTex[flareIndex];
    }

    public static int bloomTexture() {
        return upTex[0];
    }

    /** Decides per frame whether Aurum replaces vanilla's clouds. */
    public static boolean wantsClouds(boolean haveSky, boolean special) {
        MinecraftClient client = MinecraftClient.getInstance();
        return haveSky && !special && AurumConfig.get().cloudsEnabled && CloudBaker.texture() != 0
                && client.world != null && !Float.isNaN(client.world.getDimensionEffects().getCloudsHeight())
                && client.options.getCloudRenderModeValue() != CloudRenderMode.OFF
                && ShaderManager.get("post/clouds") != null;
    }

    public static void run(int sceneTex, int depthTex, Matrix4f view, Matrix4f projection, Matrix4f invProj, Matrix4f invView) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        SkyLut lut = SkyState.current();
        cloudsDrawn = raysDrawn = flareOn = bloomDrawn = false;
        rayStrength = flareStrength = bloomStrength = 0f;
        cloudSlab[3] = 0f;
        if (world == null || lut == null) return;
        ensureTargets(client.getFramebuffer().textureWidth, client.getFramebuffer().textureHeight);
        AurumConfig c = AurumConfig.get();
        float tickDelta = client.getTickDelta();
        float rain = world.getRainGradient(tickDelta);

        // Where the sun is on screen. View is rotation only, so a direction projects with w = 0.
        float[] sun = Atmosphere.sunDir;
        projection.mul(view, viewProj);
        viewProj.transform(clip.set(sun[0], sun[1], sun[2], 0f));
        float front = clip.w > 1e-4f ? 1f : 0f;
        float sx = front > 0 ? clip.x / clip.w * 0.5f + 0.5f : 0.5f;
        float sy = front > 0 ? clip.y / clip.w * 0.5f + 0.5f : 0.5f;
        // Fade out as the sun leaves the screen (rays still reach in from just outside the edge).
        float outside = Math.max(Math.max(-sx, sx - 1f), Math.max(-sy, sy - 1f));
        float onScreen = front * (1f - smooth(0.0f, 0.45f, outside));
        sunScreen[0] = sx;
        sunScreen[1] = sy;
        sunScreen[2] = (float) width / height;
        sunScreen[3] = onScreen;
        float sunUp = smooth(-0.03f, 0.06f, sun[1]);

        // 1. Clouds.
        if (cloudsActive) {
            int t = GpuTimers.begin(TimerSection.AURUM_CLOUDS);
            drawClouds(world, client.gameRenderer.getCamera(), invProj, invView, projection, rain, tickDelta, c);
            GpuTimers.end(t);
        }

        int t = GpuTimers.begin(TimerSection.AURUM_EFFECTS);
        // 2. God rays: strongest in golden-hour haze, faint at clear noon, none in caves or with the sun away.
        float haze = Math.min(1f, FogDirector.distanceDensity / 0.0015f);
        rayStrength = c.godRaysEnabled ? c.godRayStrength / 100f * 0.30f * (0.25f + 0.75f * Atmosphere.golden)
                * (0.7f + 0.3f * haze) * sunUp * onScreen * (1f - FogDirector.cave) * (1f - 0.8f * rain) : 0f;
        if (rayStrength > 0.002f) {
            ShaderProgram mask = ShaderManager.get("post/rays_mask");
            ShaderProgram blur = ShaderManager.get("post/rays_blur");
            if (mask != null && blur != null) {
                GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, maskFbo);
                GlStateManager._viewport(0, 0, quarterW, quarterH);
                mask.bind();
                mask.set1i("u_Depth", UNIT_A);
                mask.set1i("u_Clouds", UNIT_B);
                GlUtil.bindTexture(UNIT_A, depthTex);
                GlUtil.bindTexture(UNIT_B, cloudsDrawn ? cloudTex : AurumTextures.clear());
                GlUtil.drawFullscreenTriangle();

                GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, raysFbo);
                blur.bind();
                blur.set1i("u_Mask", UNIT_A);
                GL20C.glUniform2f(blur.uniform("u_SunUv"), sx, sy);
                GlUtil.bindTexture(UNIT_A, maskTex);
                GlUtil.drawFullscreenTriangle();
                raysDrawn = true;
            }
        }
        if (!raysDrawn) rayStrength = 0f;

        // 3. Flare occlusion (kept running while the sun is near the screen so it fades in smoothly).
        flareStrength = c.flareEnabled ? c.flareStrength / 100f * sunUp * (1f - 0.9f * rain) * (1f - FogDirector.cave) : 0f;
        ShaderProgram occ = ShaderManager.get("post/flare_occlusion");
        if (flareStrength > 0.002f && front > 0 && outside < 0.2f && occ != null) {
            int prev = flareIndex, next = 1 - flareIndex;
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, flareFbo[next]);
            GlStateManager._viewport(0, 0, 1, 1);
            occ.bind();
            occ.set1i("u_Depth", UNIT_A);
            occ.set1i("u_Clouds", UNIT_B);
            occ.set1i("u_Previous", UNIT_C);
            // Disc radius in uv: ~0.75 degrees over the vertical field of view, padded a little.
            float radius = (float) (0.016 * projection.m11() * 0.5);
            occ.set4f("u_Sun", sx, sy, radius, (float) width / height);
            occ.set1f("u_Blend", 0.3f);
            GlUtil.bindTexture(UNIT_A, depthTex);
            GlUtil.bindTexture(UNIT_B, cloudsDrawn ? cloudTex : AurumTextures.clear());
            GlUtil.bindTexture(UNIT_C, flareTex[prev]);
            GlUtil.drawFullscreenTriangle();
            flareIndex = next;
            flareOn = true;
        } else {
            clearFlare();
        }
        if (!flareOn) flareStrength = 0f;

        // 4. Bloom.
        bloomStrength = c.bloomEnabled ? c.bloomStrength / 100f * 0.25f : 0f;
        if (bloomStrength > 0.002f && drawBloom(sceneTex)) {
            bloomDrawn = true;
        } else {
            bloomStrength = 0f;
        }
        GpuTimers.end(t);
    }

    private static void drawClouds(ClientWorld world, Camera camera, Matrix4f invProj, Matrix4f invView,
                                   Matrix4f projection, float rain, float tickDelta, AurumConfig c) {
        ShaderProgram p = ShaderManager.get("post/clouds");
        if (p == null) return;
        Vec3d cam = camera.getPos();
        int size = CloudBaker.size();
        double blocksPerTexel = 12.0 * 256.0 / size;
        double drift = ((world.getTime() % 1_000_000L) + tickDelta) * 0.03;
        double scaleB = blocksPerTexel * 2.6;
        float offAx = (float) mod((cam.x + drift) / blocksPerTexel, size);
        float offAy = (float) mod(cam.z / blocksPerTexel + 0.33, size);
        float offBx = (float) mod((cam.z + drift * 0.6) / scaleB + 37.0, size);
        float offBy = (float) mod(-cam.x / scaleB + 91.0, size);

        float base = world.getDimensionEffects().getCloudsHeight();
        float halfThick = 3.0f;
        float centre = (float) (base + 2.0 - cam.y);
        float heightB = (float) (base + 130.0 - cam.y);
        float maxDist = 1400f;
        float thunder = world.getThunderGradient(tickDelta);
        float coverage = (c.cloudCoverage - 50) / 50f * 0.8f + 0.6f * rain + 0.3f * thunder;

        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, cloudFbo);
        GlStateManager._viewport(0, 0, cloudW, cloudH);
        p.bind();
        p.set1i("u_CloudSdf", UNIT_A);
        p.set1i("u_SkyLut", 10);
        p.setMatrix("u_InvProj", invProj);
        p.setMatrix("u_InvView", invView);
        float[] sun = Atmosphere.sunDir;
        p.set3f("u_SunDir", sun[0], sun[1], sun[2]);
        p.set3f("u_LightDir", Atmosphere.lightDir[0], Atmosphere.lightDir[1], Atmosphere.lightDir[2]);
        p.set3f("u_LightColor", Atmosphere.lightColor[0], Atmosphere.lightColor[1], Atmosphere.lightColor[2]);
        p.set3f("u_SkyAmbient", Atmosphere.skyAmbient[0], Atmosphere.skyAmbient[1], Atmosphere.skyAmbient[2]);
        SkyLut lut = SkyState.current();
        p.set4f("u_SunGlow", lut.sunGlow[0], lut.sunGlow[1], lut.sunGlow[2], Atmosphere.golden);
        // Flat top and bottom with rounded edges (rounding 2 of the 3-block half thickness).
        p.set4f("u_Slab", centre, halfThick, 2.0f, maxDist);
        p.set4f("u_Offset", offAx, offAy, offBx, offBy);
        p.set4f("u_Shape", (float) blocksPerTexel, coverage, rain, heightB);
        p.set4f("u_Look", Math.min(c.cloudShading / 100f, 1.5f), 0.75f, 0.4f * (1f - 0.6f * rain), FogDirector.distanceDensity * 0.25f);
        p.set1f("u_TexSize", size);
        float g = Atmosphere.golden * c.sunsetIntensity / 100f;
        p.set3f("u_Tint", 1f, 1f - 0.16f * g, 1f + 0.30f * g);
        p.set1f("u_PixelAngle", 2f / (projection.m11() * cloudH * 0.5f));
        p.set4f("u_ToneParams", c.tonemap.ordinal(), Atmosphere.exposure, 0f, 0f);
        p.set3f("u_WhiteBalance", Atmosphere.whiteBalance[0], Atmosphere.whiteBalance[1], Atmosphere.whiteBalance[2]);
        GlUtil.bindTexture(UNIT_A, CloudBaker.texture());
        GlUtil.bindTexture(10, SkyState.texture());
        GlUtil.drawFullscreenTriangle();
        cloudsDrawn = true;

        cloudSlab[0] = centre - halfThick;
        cloudSlab[1] = centre + halfThick;
        cloudSlab[2] = heightB;
        cloudSlab[3] = 1f;
    }

    private static boolean drawBloom(int sceneTex) {
        ShaderProgram pre = ShaderManager.get("post/bloom_prefilter");
        ShaderProgram down = ShaderManager.get("post/bloom_down");
        ShaderProgram up = ShaderManager.get("post/bloom_up");
        if (pre == null || down == null || up == null) return false;

        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, bloomFbo[0]);
        GlStateManager._viewport(0, 0, bloomW[0], bloomH[0]);
        pre.bind();
        pre.set1i("u_Scene", UNIT_A);
        GL20C.glUniform2f(pre.uniform("u_Texel"), 1f / width, 1f / height);
        GL20C.glUniform2f(pre.uniform("u_Threshold"), 0.85f, 0.10f);
        GlUtil.bindTexture(UNIT_A, sceneTex);
        GlUtil.drawFullscreenTriangle();

        down.bind();
        down.set1i("u_Src", UNIT_A);
        for (int i = 1; i < BLOOM_LEVELS; i++) {
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, bloomFbo[i]);
            GlStateManager._viewport(0, 0, bloomW[i], bloomH[i]);
            GL20C.glUniform2f(down.uniform("u_Texel"), 1f / bloomW[i - 1], 1f / bloomH[i - 1]);
            GlUtil.bindTexture(UNIT_A, bloomTex[i - 1]);
            GlUtil.drawFullscreenTriangle();
        }

        up.bind();
        up.set1i("u_Src", UNIT_A);
        up.set1i("u_Add", UNIT_B);
        for (int i = BLOOM_LEVELS - 2; i >= 0; i--) {
            int src = i == BLOOM_LEVELS - 2 ? bloomTex[BLOOM_LEVELS - 1] : upTex[i + 1];
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, upFbo[i]);
            GlStateManager._viewport(0, 0, bloomW[i], bloomH[i]);
            GL20C.glUniform2f(up.uniform("u_Texel"), 1f / bloomW[i + 1], 1f / bloomH[i + 1]);
            GlUtil.bindTexture(UNIT_A, src);
            GlUtil.bindTexture(UNIT_B, bloomTex[i]);
            GlUtil.drawFullscreenTriangle();
        }
        return true;
    }

    private static void clearFlare() {
        if (flareTex[0] == 0) return;
        for (int i = 0; i < 2; i++) {
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, flareFbo[i]);
            GL11C.glClearColor(0f, 0f, 0f, 0f);
            GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
        }
    }

    public static void unbindAll() {
        for (int unit : new int[]{UNIT_A, UNIT_B, UNIT_C, 10}) GlUtil.bindTexture(unit, 0);
    }

    private static void ensureTargets(int w, int h) {
        if (cloudTex != 0 && width == w && height == h) return;
        delete();
        width = w;
        height = h;
        cloudW = Math.max(1, w / 2);
        cloudH = Math.max(1, h / 2);
        cloudTex = GlUtil.createTexture(GL11C.GL_RGBA8, cloudW, cloudH, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, GL11C.GL_LINEAR);
        cloudFbo = fbo(cloudTex);
        quarterW = Math.max(1, w / 4);
        quarterH = Math.max(1, h / 4);
        maskTex = GlUtil.createTexture(GL30C.GL_R8, quarterW, quarterH, GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, GL11C.GL_LINEAR);
        maskFbo = fbo(maskTex);
        raysTex = GlUtil.createTexture(GL30C.GL_R8, quarterW, quarterH, GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, GL11C.GL_LINEAR);
        raysFbo = fbo(raysTex);
        for (int i = 0; i < 2; i++) {
            flareTex[i] = GlUtil.createTexture(GL30C.GL_R8, 1, 1, GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, GL11C.GL_NEAREST);
            flareFbo[i] = fbo(flareTex[i]);
        }
        clearFlare();
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            bloomW[i] = Math.max(1, w >> (2 + i));
            bloomH[i] = Math.max(1, h >> (2 + i));
            bloomTex[i] = GlUtil.createTexture(GL30C.GL_R11F_G11F_B10F, bloomW[i], bloomH[i], GL11C.GL_RGB, GL11C.GL_FLOAT, GL11C.GL_LINEAR);
            bloomFbo[i] = fbo(bloomTex[i]);
            if (i < BLOOM_LEVELS - 1) {
                upTex[i] = GlUtil.createTexture(GL30C.GL_R11F_G11F_B10F, bloomW[i], bloomH[i], GL11C.GL_RGB, GL11C.GL_FLOAT, GL11C.GL_LINEAR);
                upFbo[i] = fbo(upTex[i]);
            }
        }
        GlStateManager._bindTexture(0);
    }

    private static int fbo(int colorTex) {
        int fbo = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, colorTex, 0);
        return fbo;
    }

    private static void delete() {
        if (cloudTex == 0) return;
        int[] textures = {cloudTex, maskTex, raysTex, flareTex[0], flareTex[1]};
        int[] fbos = {cloudFbo, maskFbo, raysFbo, flareFbo[0], flareFbo[1]};
        for (int tex : textures) GlStateManager._deleteTexture(tex);
        for (int f : fbos) GlStateManager._glDeleteFramebuffers(f);
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            GlStateManager._deleteTexture(bloomTex[i]);
            GlStateManager._glDeleteFramebuffers(bloomFbo[i]);
            if (i < BLOOM_LEVELS - 1) {
                GlStateManager._deleteTexture(upTex[i]);
                GlStateManager._glDeleteFramebuffers(upFbo[i]);
            }
        }
        cloudTex = 0;
    }

    private static double mod(double v, double m) {
        double r = v % m;
        return r < 0 ? r + m : r;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }
}
