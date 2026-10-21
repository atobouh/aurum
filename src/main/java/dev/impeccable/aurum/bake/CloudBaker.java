package dev.impeccable.aurum.bake;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.impeccable.aurum.AurumMod;
import dev.impeccable.aurum.render.GlUtil;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Optional;

/**
 * Turns Minecraft's own cloud texture (so resource-pack cloud shapes still apply) into a tiling signed distance
 * field, in cloud texels, negative inside a cloud. It is baked at 4x resolution with the outer corners rounded,
 * so the clouds keep Minecraft's block steps but with the soft corners of Story Mode-style clouds, and the
 * shader can sphere-trace it.
 */
public final class CloudBaker {
    private static final Identifier CLOUDS = new Identifier("minecraft", "textures/environment/clouds.png");
    /** Distances are exact up to this many texels and clamped beyond (one vanilla texel is 12 blocks). */
    private static final int RANGE = 8;
    /** Sub-texels per cloud texel in the baked field. */
    private static final int SUB = 4;
    /** Corner rounding radius in sub-texels (1.2 sub-texels = 3.6 blocks). */
    private static final float ROUND = 1.2f;

    private static int texture;
    private static int size = 256;

    private CloudBaker() {}

    public static int texture() {
        return texture;
    }

    /** Width of the source cloud texture in texels; vanilla's is 256 (one texel = 12 blocks). */
    public static int size() {
        return size;
    }

    public static void bake(ResourceManager manager) {
        long start = System.nanoTime();
        boolean[] inside;
        int w, h;
        Optional<Resource> res = manager.getResource(CLOUDS);
        if (res.isEmpty()) {
            AurumMod.LOGGER.warn("No cloud texture found; Aurum clouds stay empty");
            w = h = 1;
            inside = new boolean[1];
        } else {
            try (InputStream in = res.get().getInputStream(); NativeImage img = NativeImage.read(in)) {
                w = img.getWidth();
                h = img.getHeight();
                inside = new boolean[w * h];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        // ABGR: alpha is the top byte.
                        inside[y * w + x] = (img.getColor(x, y) >>> 24) > 127;
                    }
                }
            } catch (Exception e) {
                AurumMod.LOGGER.error("Could not read the cloud texture; Aurum clouds stay empty", e);
                w = h = 1;
                inside = new boolean[1];
            }
        }

        // Bake at 4 sub-texels per cloud texel (3 blocks each) so the block steps survive filtering, then round
        // the outer corners by a fixed radius: erode, take the exact distance again, and grow back.
        int sw = w * SUB, sh = h * SUB;
        boolean[] fine = new boolean[sw * sh];
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) fine[y * sw + x] = inside[(y / SUB) * w + x / SUB];
        }
        float[] sdf = signedDistance(fine, sw, sh);
        boolean[] eroded = new boolean[sw * sh];
        for (int i = 0; i < eroded.length; i++) eroded[i] = sdf[i] < -ROUND;
        sdf = signedDistance(eroded, sw, sh);
        for (int i = 0; i < sdf.length; i++) sdf[i] = (sdf[i] - ROUND) / SUB; // back to cloud texels
        w = sw;
        h = sh;

        if (texture == 0) texture = GlStateManager._genTexture();
        GlUtil.resetUnpack();
        GlStateManager._bindTexture(texture);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL11C.GL_REPEAT);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL11C.GL_REPEAT);
        FloatBuffer buf = MemoryUtil.memAllocFloat(w * h);
        buf.put(sdf).flip();
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_R16F, w, h, 0, GL11C.GL_RED, GL11C.GL_FLOAT, buf);
        MemoryUtil.memFree(buf);
        GlStateManager._bindTexture(0);
        size = w / SUB;
        AurumMod.LOGGER.info("Baked cloud shapes ({}x{}) in {} ms", w, h, (System.nanoTime() - start) / 1_000_000);
    }

    /** Signed distance in texels between texel centres, negative inside, wrapping at the edges, clamped. */
    private static float[] signedDistance(boolean[] inside, int w, int h) {
        float[] out = new float[w * h];
        float[] dOut = edt(inside, w, h, true);   // inside texels: distance to the nearest outside texel
        float[] dIn = edt(inside, w, h, false);   // outside texels: distance to the nearest inside texel
        float max = RANGE * SUB;
        for (int i = 0; i < out.length; i++) {
            // The boundary sits halfway between an inside and an outside texel centre.
            out[i] = inside[i] ? -Math.min((float) Math.sqrt(dOut[i]) - 0.5f, max) : Math.min((float) Math.sqrt(dIn[i]) - 0.5f, max);
        }
        return out;
    }

    /**
     * Squared distance from every texel to the nearest texel on the other side of the boundary (outside texels
     * when {@code fromInside}, inside texels otherwise). Exact and linear time (Felzenszwalb and Huttenlocher
     * 2012), computed on a padded copy so the result wraps like the cloud layer.
     */
    private static float[] edt(boolean[] inside, int w, int h, boolean fromInside) {
        int pad = RANGE * SUB + 2;
        int pw = w + 2 * pad, ph = h + 2 * pad;
        double[] g = new double[pw * ph];
        for (int y = 0; y < ph; y++) {
            int sy = Math.floorMod(y - pad, h);
            for (int x = 0; x < pw; x++) {
                boolean in = inside[sy * w + Math.floorMod(x - pad, w)];
                g[y * pw + x] = in != fromInside ? 0.0 : 1e20;
            }
        }
        int n = Math.max(pw, ph);
        double[] f = new double[n], d = new double[n], z = new double[n + 1];
        int[] v = new int[n];
        for (int x = 0; x < pw; x++) {
            for (int y = 0; y < ph; y++) f[y] = g[y * pw + x];
            pass(f, d, v, z, ph);
            for (int y = 0; y < ph; y++) g[y * pw + x] = d[y];
        }
        for (int y = 0; y < ph; y++) {
            System.arraycopy(g, y * pw, f, 0, pw);
            pass(f, d, v, z, pw);
            System.arraycopy(d, 0, g, y * pw, pw);
        }
        float[] out = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) out[y * w + x] = (float) g[(y + pad) * pw + pad + x];
        }
        return out;
    }

    /** One dimension of the distance transform: the lower envelope of parabolas rooted at each sample. */
    private static void pass(double[] f, double[] d, int[] v, double[] z, int n) {
        int k = 0;
        v[0] = 0;
        z[0] = Double.NEGATIVE_INFINITY;
        z[1] = Double.POSITIVE_INFINITY;
        for (int q = 1; q < n; q++) {
            double s = intersect(f, q, v[k]);
            while (s <= z[k]) {
                k--;
                s = intersect(f, q, v[k]);
            }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = Double.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) k++;
            double dq = q - v[k];
            d[q] = dq * dq + f[v[k]];
        }
    }

    private static double intersect(double[] f, int q, int p) {
        return ((f[q] + (double) q * q) - (f[p] + (double) p * p)) / (2.0 * q - 2.0 * p);
    }
}
