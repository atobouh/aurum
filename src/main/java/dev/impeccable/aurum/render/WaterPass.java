package dev.impeccable.aurum.render;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.render.terrain.TerrainUniforms;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;

/**
 * Copies the opaque scene (colour and depth) right before Sodium draws translucent terrain, so the water shader
 * can see what lies under the surface (clear water, depth tint, refraction, shore foam) and what it reflects
 * (screen-space reflections). Two blits per frame, skipped when water shading is off.
 */
public final class WaterPass {
    private static int width, height;
    private static int colorTex, depthTex, fbo;
    private static int depthAttachment = GL30C.GL_DEPTH_ATTACHMENT;
    /** True when this frame's copy is valid. */
    public static boolean captured;
    /** Seconds of water animation, wrapped, accumulated per frame. */
    private static double time;
    private static long lastNanos;

    private WaterPass() {}

    public static int colorTexture() {
        return colorTex;
    }

    public static int depthTexture() {
        return depthTex;
    }

    public static float time() {
        return (float) time;
    }

    /** Called once per frame by the frame director. */
    public static void tick(boolean paused) {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min((now - lastNanos) / 1e9, 0.25);
        lastNanos = now;
        if (!paused) time = (time + dt) % 4096.0;
        captured = false;
    }

    public static boolean enabled() {
        return Pipeline.isActive() && TerrainUniforms.mode == 1 && AurumConfig.get().waterEnabled;
    }

    /** Called right before the translucent terrain pass. */
    public static void capture(Framebuffer main) {
        captured = false;
        if (!enabled()) return;
        ensure(main);
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, main.fbo);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, fbo);
        GL30C.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11C.GL_COLOR_BUFFER_BIT | GL11C.GL_DEPTH_BUFFER_BIT, GL11C.GL_NEAREST);
        main.beginWrite(false);
        captured = true;
    }

    private static void ensure(Framebuffer main) {
        if (colorTex != 0 && width == main.textureWidth && height == main.textureHeight) return;
        if (colorTex != 0) {
            GlStateManager._deleteTexture(colorTex);
            GlStateManager._deleteTexture(depthTex);
            GlStateManager._glDeleteFramebuffers(fbo);
        }
        width = main.textureWidth;
        height = main.textureHeight;
        colorTex = GlUtil.createTexture(GL11C.GL_RGBA8, width, height, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, GL11C.GL_LINEAR);
        // Depth in the main buffer's own format, or the blit fails.
        GlStateManager._bindTexture(main.getDepthAttachment());
        int format = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
        boolean stencil = format == GL30C.GL_DEPTH24_STENCIL8 || format == GL30C.GL_DEPTH32F_STENCIL8;
        depthAttachment = stencil ? GL30C.GL_DEPTH_STENCIL_ATTACHMENT : GL30C.GL_DEPTH_ATTACHMENT;
        depthTex = GlUtil.createTexture(format, width, height, stencil ? GL30C.GL_DEPTH_STENCIL : GL11C.GL_DEPTH_COMPONENT,
                stencil ? GL30C.GL_UNSIGNED_INT_24_8 : GL11C.GL_FLOAT, GL11C.GL_NEAREST);
        fbo = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, colorTex, 0);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, depthAttachment, GL11C.GL_TEXTURE_2D, depthTex, 0);
        GlStateManager._bindTexture(0);
        main.beginWrite(false);
    }
}
