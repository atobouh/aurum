package dev.impeccable.aurum.bake;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.impeccable.aurum.render.AurumTextures;
import dev.impeccable.aurum.render.GlUtil;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * A tiling ripple texture for the water surface, baked once at startup (docs/research/WATER_PROPOSAL.md, style B,
 * Photon reference). A sum of small waves with whole-number wave vectors, so it tiles exactly; each texel stores
 * the analytic slope (RG) and height (B). The shader samples it two or three times at different scales, scrolling
 * in different directions, which gives calm, never-repeating ripples and, at the finest scale, sun glitter.
 */
public final class WaterBaker {
    private static final int SIZE = 128;
    private static final int WAVES = 48;

    private WaterBaker() {}

    public static void bake() {
        java.util.Random r = new java.util.Random(31337);
        int[] kx = new int[WAVES], ky = new int[WAVES];
        float[] amp = new float[WAVES], phase = new float[WAVES];
        float total = 0f;
        for (int i = 0; i < WAVES; i++) {
            // Mostly long, gentle waves along one broad direction, with shorter ones scattered around it.
            int k;
            do {
                kx[i] = r.nextInt(13) - 6;
                ky[i] = r.nextInt(9) - 2;
                k = kx[i] * kx[i] + ky[i] * ky[i];
            } while (k == 0);
            amp[i] = (float) (1.0 / Math.pow(k, 0.9));
            phase[i] = r.nextFloat() * 6.2831855f;
            total += amp[i];
        }
        float[] gx = new float[SIZE * SIZE], gy = new float[SIZE * SIZE], h = new float[SIZE * SIZE];
        float maxG = 1e-6f;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float u = (float) x / SIZE * 6.2831855f, v = (float) y / SIZE * 6.2831855f;
                float sx = 0, sy = 0, sh = 0;
                for (int i = 0; i < WAVES; i++) {
                    float a = kx[i] * u + ky[i] * v + phase[i];
                    // Slightly sharpened crests (|sin|-like) read more like water than pure sines.
                    float s = (float) Math.sin(a), c = (float) Math.cos(a);
                    sh += amp[i] * s;
                    sx += amp[i] * kx[i] * c;
                    sy += amp[i] * ky[i] * c;
                }
                int k = y * SIZE + x;
                gx[k] = sx;
                gy[k] = sy;
                h[k] = sh / total;
                maxG = Math.max(maxG, Math.max(Math.abs(sx), Math.abs(sy)));
            }
        }
        ByteBuffer px = MemoryUtil.memAlloc(SIZE * SIZE * 4);
        for (int k = 0; k < SIZE * SIZE; k++) {
            px.put(k * 4, (byte) Math.round((gx[k] / maxG * 0.5f + 0.5f) * 255f));
            px.put(k * 4 + 1, (byte) Math.round((gy[k] / maxG * 0.5f + 0.5f) * 255f));
            px.put(k * 4 + 2, (byte) Math.round(Math.max(0f, Math.min(1f, h[k] * 0.5f + 0.5f)) * 255f));
            px.put(k * 4 + 3, (byte) 255);
        }
        GlUtil.resetUnpack();
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR_MIPMAP_LINEAR);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL11C.GL_REPEAT);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL11C.GL_REPEAT);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, SIZE, SIZE, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, px);
        GL30C.glGenerateMipmap(GL11C.GL_TEXTURE_2D);
        GlStateManager._bindTexture(0);
        MemoryUtil.memFree(px);
        AurumTextures.setWaterNormal(id);
    }
}
