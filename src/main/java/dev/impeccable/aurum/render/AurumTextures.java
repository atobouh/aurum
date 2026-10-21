package dev.impeccable.aurum.render;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Texture handles shared between passes, with neutral 1x1 fallbacks so every sampler always has something
 * valid bound (a flat normal with no material, a neutral grey bounce colour, mid-grey noise).
 */
public final class AurumTextures {
    private static int flatNormal, greyBounce, midNoise;
    private static int normalAtlas, bounce, noise;

    private AurumTextures() {}

    public static void init() {
        flatNormal = solid(128, 128, 128, 0);
        greyBounce = solid(128, 128, 128, 255);
        midNoise = solid(128, 128, 128, 255);
    }

    private static int solid(int r, int g, int b, int a) {
        GlUtil.resetUnpack();
        int id = GlUtil.createTexture(GL11C.GL_RGBA8, 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, GL11C.GL_NEAREST);
        ByteBuffer px = MemoryUtil.memAlloc(4);
        px.put((byte) r).put((byte) g).put((byte) b).put((byte) a).flip();
        GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, px);
        MemoryUtil.memFree(px);
        GlStateManager._bindTexture(0);
        return id;
    }

    public static int normalAtlas() {
        return normalAtlas != 0 ? normalAtlas : flatNormal;
    }

    public static void setNormalAtlas(int id) {
        normalAtlas = id;
    }

    public static int bounce() {
        return bounce != 0 ? bounce : greyBounce;
    }

    public static void setBounce(int id) {
        bounce = id;
    }

    public static int noise() {
        return noise != 0 ? noise : midNoise;
    }

    public static void setNoise(int id) {
        noise = id;
    }

}
