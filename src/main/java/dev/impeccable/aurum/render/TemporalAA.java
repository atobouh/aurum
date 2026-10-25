package dev.impeccable.aurum.render;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.impeccable.aurum.config.AurumConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;

/**
 * Temporal anti-aliasing: the world is drawn with a sub-pixel camera offset that changes every frame (Halton 2,3),
 * and each frame is blended with the reprojected history of earlier ones. Edges, leaf speckle and thin lines then
 * resolve to their true coverage instead of stair-steps and shimmer, which is the soft, solid look of the
 * Nostalgia and Photon references (docs/research/TAA.md). One full-screen pass, two history textures.
 */
public final class TemporalAA {
    private static final int SAMPLES = 8;
    private static int frame;
    /** Jitter of the current frame in normalised device coordinates (0 when inactive). */
    private static float jx, jy;
    private static boolean active;
    /** True while the hand is drawn: it keeps an exact, unjittered projection. */
    private static boolean inHand;

    private static int width, height;
    private static final int[] histTex = new int[2], histFbo = new int[2];
    private static int current;
    private static boolean historyValid;
    private static final Matrix4f prevViewProj = new Matrix4f();
    private static Vec3d prevCamera;
    private static Object prevWorld;

    private TemporalAA() {}

    /** Decides at the start of a frame whether TAA runs, and picks this frame's jitter. */
    public static void beginFrame() {
        AurumConfig c = AurumConfig.get();
        boolean want = c.enabled && c.taa && Pipeline.isActive() && MinecraftClient.getInstance().world != null
                && ShaderManager.get("post/taa") != null;
        if (!want) {
            active = false;
            historyValid = false;
            jx = jy = 0f;
            return;
        }
        active = true;
        frame = (frame + 1) % SAMPLES;
        var fb = MinecraftClient.getInstance().getFramebuffer();
        jx = (halton(frame + 1, 2) - 0.5f) * 2f / fb.textureWidth;
        jy = (halton(frame + 1, 3) - 0.5f) * 2f / fb.textureHeight;
    }

    public static void setInHand(boolean hand) { inHand = hand; }

    /** Offsets a projection matrix by this frame's jitter (a translation in clip space). */
    public static void jitter(Matrix4f p) {
        if (!active || inHand) return;
        offset(p, jx, jy);
    }

    /** Removes this frame's jitter from a projection captured after {@link #jitter}. */
    public static void unjitter(Matrix4f p) {
        if (!active) return;
        offset(p, -jx, -jy);
    }

    private static void offset(Matrix4f p, float x, float y) {
        // clip.xy += j * clip.w: add j times the w row to the x and y rows.
        p.m00(p.m00() + x * p.m03()).m10(p.m10() + x * p.m13()).m20(p.m20() + x * p.m23()).m30(p.m30() + x * p.m33());
        p.m01(p.m01() + y * p.m03()).m11(p.m11() + y * p.m13()).m21(p.m21() + y * p.m23()).m31(p.m31() + y * p.m33());
    }

    public static boolean active() { return active; }

    /**
     * Resolves the composited frame (already in {@code main}) against the history and writes the result back.
     * {@code sceneTex/sceneFbo} is a free full-resolution scratch copy at this point.
     */
    public static void resolve(net.minecraft.client.gl.Framebuffer main, int sceneTex, int sceneFbo, int depthTex,
                               int handDepthTex, boolean handValid, Matrix4f invProj, Matrix4f invView,
                               Matrix4f view, Matrix4f projection) {
        if (!active) return;
        ShaderProgram taa = ShaderManager.get("post/taa");
        if (taa == null) return;
        ensureTargets(main.textureWidth, main.textureHeight);

        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cam = client.gameRenderer.getCamera().getPos();
        if (prevCamera == null || prevWorld != client.world || cam.squaredDistanceTo(prevCamera) > 64.0) {
            historyValid = false;
        }
        Vec3d delta = prevCamera == null ? Vec3d.ZERO : cam.subtract(prevCamera);

        // Current colour -> scratch copy.
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, main.fbo);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, sceneFbo);
        GL30C.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11C.GL_COLOR_BUFFER_BIT, GL11C.GL_NEAREST);

        int prev = current;
        int next = 1 - current;
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, histFbo[next]);
        GlStateManager._viewport(0, 0, width, height);
        taa.bind();
        taa.set1i("u_Current", 6);
        taa.set1i("u_History", 7);
        taa.set1i("u_Depth", 8);
        taa.set1i("u_HandDepth", 9);
        taa.set1i("u_OpaqueDepth", 10);
        GlUtil.bindTexture(10, WaterPass.captured ? WaterPass.depthTexture() : AurumTextures.clear());
        GlUtil.bindTexture(6, sceneTex);
        GlUtil.bindTexture(7, histTex[prev]);
        GlUtil.bindTexture(8, depthTex);
        GlUtil.bindTexture(9, handDepthTex);
        taa.setMatrix("u_InvProj", invProj);
        taa.setMatrix("u_InvView", invView);
        taa.setMatrix("u_PrevViewProj", prevViewProj);
        taa.set3f("u_CamDelta", (float) delta.x, (float) delta.y, (float) delta.z);
        AurumConfig c = AurumConfig.get();
        taa.set4f("u_Params", historyValid ? 1f : 0f, 0.15f, c.taaSharpen / 100f, handValid ? 1f : 0f);
        taa.set4f("u_Jitter", jx * 0.5f, jy * 0.5f, 1f / width, 1f / height);
        GlUtil.drawFullscreenTriangle();
        ShaderProgram.unbind();
        for (int unit = 6; unit <= 10; unit++) GlUtil.bindTexture(unit, 0);

        // Result -> main framebuffer; it also stays as next frame's history.
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, histFbo[next]);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, main.fbo);
        GL30C.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11C.GL_COLOR_BUFFER_BIT, GL11C.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, main.fbo);

        current = next;
        historyValid = true;
        // Reprojection uses the unjittered camera, so the history stays put while the jitter moves.
        Matrix4f unjittered = new Matrix4f(projection);
        unjitter(unjittered);
        prevViewProj.set(unjittered).mul(view);
        prevCamera = cam;
        prevWorld = client.world;
    }

    private static void ensureTargets(int w, int h) {
        if (histTex[0] != 0 && w == width && h == height) return;
        for (int i = 0; i < 2; i++) {
            if (histTex[i] != 0) {
                GlStateManager._deleteTexture(histTex[i]);
                GlStateManager._glDeleteFramebuffers(histFbo[i]);
            }
        }
        width = w;
        height = h;
        for (int i = 0; i < 2; i++) {
            histTex[i] = GlUtil.createTexture(GL30C.GL_RGBA16F, w, h, GL11C.GL_RGBA, GL11C.GL_FLOAT, GL11C.GL_LINEAR);
            histFbo[i] = GlStateManager.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, histFbo[i]);
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, histTex[i], 0);
        }
        historyValid = false;
    }

    private static float halton(int i, int base) {
        float f = 1f, r = 0f;
        while (i > 0) {
            f /= base;
            r += f * (i % base);
            i /= base;
        }
        return r;
    }
}
