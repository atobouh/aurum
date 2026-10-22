package dev.impeccable.aurum.render;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.impeccable.aurum.bake.CloudBaker;
import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.config.CloudStyle;
import dev.impeccable.aurum.director.Atmosphere;
import dev.impeccable.aurum.director.FogDirector;
import dev.impeccable.aurum.render.shadow.ShadowRenderer;
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
 *   <li>Clouds, blocky or soft style, at 1/2 or full resolution (replace vanilla's clouds).</li>
 *   <li>God rays at 1/4 resolution: open-sky mask, then a radial blur toward the sun; and volumetric light, the
 *       sunlit share of the air along each ray from the shadow map (1/2 resolution).</li>
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
    private static boolean cloudFullRes;
    private static long lastDriftNanos;
    private static double drift;
    private static int cloudTex, cloudFbo, cloudW, cloudH;
    private static int maskTex, maskFbo, raysTex, raysFbo, vlTex, vlFbo, quarterW, quarterH, halfW, halfH;
    private static final int[] flareTex = new int[2], flareFbo = new int[2];
    private static int flareIndex;
    private static final int[] bloomTex = new int[BLOOM_LEVELS], bloomFbo = new int[BLOOM_LEVELS];
    private static final int[] upTex = new int[BLOOM_LEVELS - 1], upFbo = new int[BLOOM_LEVELS - 1];
    private static final int[] bloomW = new int[BLOOM_LEVELS], bloomH = new int[BLOOM_LEVELS];

    // Results for the composite.
    public static boolean cloudsDrawn, raysDrawn, flareOn, bloomDrawn, volumetricDrawn;
    public static float rayStrength, flareStrength, bloomStrength, volumetricStrength;
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

    public static int volumetricTexture() {
        return vlTex;
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
                && ShaderManager.get(program(AurumConfig.get())) != null;
    }

    public static void run(int sceneTex, int depthTex, Matrix4f view, Matrix4f projection, Matrix4f invProj, Matrix4f invView) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        SkyLut lut = SkyState.current();
        cloudsDrawn = raysDrawn = flareOn = bloomDrawn = volumetricDrawn = false;
        rayStrength = flareStrength = bloomStrength = volumetricStrength = 0f;
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
        rayStrength = c.godRaysEnabled ? c.godRayStrength / 100f * 0.8f * (0.2f + 0.8f * Atmosphere.golden)
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

        // 2b. Volumetric light from the shadow map: beams through trees, past islands and into caves, also with
        // the sun off-screen. Not faded in caves (that is where a beam from an opening looks best).
        volumetricStrength = c.godRaysEnabled && ShadowRenderer.usable()
                ? c.godRayStrength / 100f * 1.0f * (0.6f + 0.4f * Math.max(Atmosphere.golden, FogDirector.enclosed)) * sunUp * (1f - 0.7f * rain) : 0f;
        ShaderProgram vl = ShaderManager.get("post/volumetric");
        if (volumetricStrength > 0.002f && vl != null) {
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, vlFbo);
            GlStateManager._viewport(0, 0, halfW, halfH);
            vl.bind();
            vl.set1i("u_Depth", UNIT_A);
            vl.set1i("u_ShadowMap", UNIT_B);
            vl.setMatrix("u_ShadowMatrix", ShadowRenderer.samplingMatrix());
            vl.setMatrix("u_InvProj", invProj);
            vl.setMatrix("u_InvView", invView);
            vl.set4f("u_Params", ShadowRenderer.DISTORTION, ShadowRenderer.range() * 0.95f, 0.0004f, 0f);
            GlUtil.bindTexture(UNIT_A, depthTex);
            GlUtil.bindTexture(UNIT_B, ShadowRenderer.frontTexture());
            GlUtil.drawFullscreenTriangle();
            volumetricDrawn = true;
        } else {
            volumetricStrength = 0f;
        }

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
        ShaderProgram p = ShaderManager.get(program(c));
        if (p == null) return;
        boolean soft = c.cloudStyle == CloudStyle.SOFT;
        Vec3d cam = camera.getPos();
        int size = CloudBaker.size();
        double blocksPerTexel = 12.0 * 256.0 / size;
        // Drift: 100% is 3x vanilla's 0.03 blocks per tick (user: at vanilla speed the clouds looked still).
        // Accumulated per frame so the speed slider never makes the clouds jump. Wrapped at 12288 blocks, a whole
        // number of both the vanilla layout period (3072) and the shader's noise period (4096).
        long now = System.nanoTime();
        double dt = lastDriftNanos == 0 ? 0 : Math.min((now - lastDriftNanos) / 1e9, 0.25);
        lastDriftNanos = now;
        if (!MinecraftClient.getInstance().isPaused()) drift = (drift + dt * 20.0 * 0.09 * c.cloudSpeed / 100.0) % 12288.0;
        float off1x = (float) mod((cam.x + drift) / blocksPerTexel, size);
        float off1y = (float) mod(cam.z / blocksPerTexel + 0.33, size);

        float base = (float) (world.getDimensionEffects().getCloudsHeight() + c.cloudHeight - cam.y);
        float maxDist = 1400f;
        // Vertical extent of everything the style draws, for the composite's "is the cloud in front" test.
        float lo = soft ? base - 2f : base;
        float hi = soft ? base + 30f : base + 22f;
        float plane = soft ? base + 230f : hi;
        float thunder = world.getThunderGradient(tickDelta);
        // Measured: Nostalgia's clouds fill ~28% of the upper frame. Slightly fuller shapes than vanilla, broken up
        // by the shader's large-scale fields (clusters and clear patches); rain fills the sky in.
        float coverage = (c.cloudCoverage - 50) / 50f * 0.8f + 0.8f * rain + 0.3f * thunder;

        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, cloudFbo);
        GlStateManager._viewport(0, 0, cloudW, cloudH);
        p.bind();
        p.set1i("u_CloudSdf", UNIT_A);
        p.set1i("u_Noise", UNIT_B);
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
        // Noise coordinates wrap at 4096 blocks (a whole number of tiles at every scale the shader uses).
        p.set4f("u_Layers", base, maxDist, (float) mod(drift, 4096.0), 0f);
        GL20C.glUniform2f(p.uniform("u_NoiseCam"), (float) mod(cam.x + drift, 4096.0), (float) mod(cam.z, 4096.0));
        p.set4f("u_Offset", off1x, off1y, 0f, 0f);
        p.set1f("u_ShapeSet", c.cloudsPackShapes ? 1f : 0f);
        p.set4f("u_Shape", (float) blocksPerTexel, coverage, rain, 0f);
        p.set4f("u_Look", Math.min(c.cloudShading / 100f, 1.5f), soft ? 0.85f : 1.3f, 0f, FogDirector.distanceDensity * 0.25f);
        p.set1f("u_TexSize", size);
        float g = Atmosphere.golden * c.sunsetIntensity / 100f;
        p.set3f("u_Tint", 1f, 1f - 0.16f * g, 1f + 0.30f * g);
        p.set1f("u_PixelAngle", 2f / (projection.m11() * cloudH));
        p.set4f("u_ToneParams", c.tonemap.ordinal(), Atmosphere.exposure, 0f, 0f);
        p.set3f("u_WhiteBalance", Atmosphere.whiteBalance[0], Atmosphere.whiteBalance[1], Atmosphere.whiteBalance[2]);
        GlUtil.bindTexture(UNIT_A, CloudBaker.texture());
        GlUtil.bindTexture(UNIT_B, AurumTextures.noise());
        GlUtil.bindTexture(10, SkyState.texture());
        GlUtil.drawFullscreenTriangle();
        cloudsDrawn = true;

        cloudSlab[0] = lo;
        cloudSlab[1] = hi;
        cloudSlab[2] = plane;
        // 1 = plain upsample, 2 = sharpen the edges (blocky at half resolution), 3 = smooth (soft: hides the
        // ray-march jitter).
        cloudSlab[3] = soft ? 3f : c.cloudsFullRes ? 1f : 2f;
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

    private static String program(AurumConfig c) {
        return c.cloudStyle == CloudStyle.SOFT ? "post/clouds_soft" : "post/clouds_blocky";
    }

    private static void ensureTargets(int w, int h) {
        boolean fullRes = AurumConfig.get().cloudsFullRes;
        if (cloudTex != 0 && width == w && height == h && cloudFullRes == fullRes) return;
        cloudFullRes = fullRes;
        delete();
        width = w;
        height = h;
        cloudW = fullRes ? w : Math.max(1, w / 2);
        cloudH = fullRes ? h : Math.max(1, h / 2);
        cloudTex = GlUtil.createTexture(GL11C.GL_RGBA8, cloudW, cloudH, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, GL11C.GL_LINEAR);
        cloudFbo = fbo(cloudTex);
        quarterW = Math.max(1, w / 4);
        quarterH = Math.max(1, h / 4);
        maskTex = GlUtil.createTexture(GL30C.GL_R8, quarterW, quarterH, GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, GL11C.GL_LINEAR);
        maskFbo = fbo(maskTex);
        raysTex = GlUtil.createTexture(GL30C.GL_R8, quarterW, quarterH, GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, GL11C.GL_LINEAR);
        raysFbo = fbo(raysTex);
        // Half resolution: quarter was too coarse for crisp beam edges.
        halfW = Math.max(1, w / 2);
        halfH = Math.max(1, h / 2);
        vlTex = GlUtil.createTexture(GL30C.GL_RG8, halfW, halfH, GL30C.GL_RG, GL11C.GL_UNSIGNED_BYTE, GL11C.GL_LINEAR);
        vlFbo = fbo(vlTex);
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
        int[] textures = {cloudTex, maskTex, raysTex, vlTex, flareTex[0], flareTex[1]};
        int[] fbos = {cloudFbo, maskFbo, raysFbo, vlFbo, flareFbo[0], flareFbo[1]};
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
