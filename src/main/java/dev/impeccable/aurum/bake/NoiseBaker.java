package dev.impeccable.aurum.bake;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.impeccable.aurum.render.AurumTextures;
import dev.impeccable.aurum.render.GlUtil;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * A 64x64 tiling value-noise texture (two octaves), baked once at startup. Pre-baked noise is one filtered
 * texture read in the shader instead of dozens of instructions (research notes: noise costs).
 */
public final class NoiseBaker {
    private static final int SIZE = 64;

    private NoiseBaker() {}

    public static void bake() {
        float[] base = lattice(8, 1234);
        float[] fine = lattice(16, 5678);
        ByteBuffer px = MemoryUtil.memAlloc(SIZE * SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float v = 0.7f * value(base, 8, x, y) + 0.3f * value(fine, 16, x, y);
                px.put(y * SIZE + x, (byte) Math.round(Math.max(0f, Math.min(1f, v)) * 255f));
            }
        }
        GlUtil.resetUnpack();
        GlStateManager._pixelStore(GL11C.GL_UNPACK_ALIGNMENT, 1);
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL11C.GL_REPEAT);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL11C.GL_REPEAT);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_R8, SIZE, SIZE, 0, GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, px);
        GlStateManager._bindTexture(0);
        GlStateManager._pixelStore(GL11C.GL_UNPACK_ALIGNMENT, 4);
        MemoryUtil.memFree(px);
        AurumTextures.setNoise(id);
    }

    private static float[] lattice(int cells, int seed) {
        float[] l = new float[cells * cells];
        java.util.Random r = new java.util.Random(seed);
        for (int i = 0; i < l.length; i++) l[i] = r.nextFloat();
        return l;
    }

    /** Smoothly interpolated lattice noise that tiles across the texture. */
    private static float value(float[] l, int cells, int x, int y) {
        float fx = (float) x / SIZE * cells, fy = (float) y / SIZE * cells;
        int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
        float tx = fx - ix, ty = fy - iy;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        float a = l[Math.floorMod(iy, cells) * cells + Math.floorMod(ix, cells)];
        float b = l[Math.floorMod(iy, cells) * cells + Math.floorMod(ix + 1, cells)];
        float c = l[Math.floorMod(iy + 1, cells) * cells + Math.floorMod(ix, cells)];
        float d = l[Math.floorMod(iy + 1, cells) * cells + Math.floorMod(ix + 1, cells)];
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty;
    }
}
