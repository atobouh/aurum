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
 * Bakes the cloud layouts into one tiling signed distance field texture, in cloud cells (12 blocks), negative
 * inside a cloud, at 4 sub-texels per cell so the block steps survive filtering and the shader can sphere-trace
 * it. R: Aurum's own layout (clusters and puffs), G: its second tier, B: the resource pack's clouds.png layout.
 */
public final class CloudBaker {
    private static final Identifier CLOUDS = new Identifier("minecraft", "textures/environment/clouds.png");
    /** Distances are exact up to this many texels and clamped beyond (one vanilla texel is 12 blocks). */
    private static final int RANGE = 8;
    /** Sub-texels per cloud texel in the baked field. */
    private static final int SUB = 4;
    /** Cloud cells across the tiling layout (vanilla's 256; one cell = 12 blocks). */
    private static final int CELLS = 256;

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

        // Vanilla (resource pack) layout, resampled to the cell grid, with clear patches: whole cells are cleared
        // where a slow, tiling noise is low (whole cells only, so every edge stays a crisp Minecraft step).
        boolean[] vanilla = new boolean[CELLS * CELLS];
        for (int y = 0; y < CELLS; y++) {
            for (int x = 0; x < CELLS; x++) vanilla[y * CELLS + x] = inside[(y * h / CELLS) * w + x * w / CELLS];
        }
        float[] field = fieldNoise(CELLS, CELLS);
        for (int i = 0; i < vanilla.length; i++) if (field[i] > 0.5f) vanilla[i] = false;

        // Aurum's own layout (default): compact clusters of cells in natural, varied sizes, slightly stretched along
        // the wind, with scattered small puffs around them, and a smaller second tier on the biggest clusters so
        // they step up like blocky cumulus. The user found vanilla's long streaks ugly.
        boolean[][] aurum = aurumLayout();

        float[] base = toSubTexelSdf(aurum[0]);
        float[] top = toSubTexelSdf(aurum[1]);
        float[] van = toSubTexelSdf(vanilla);
        w = CELLS * SUB;
        h = CELLS * SUB;
        float[] sdf = new float[w * h * 3];
        for (int i = 0; i < w * h; i++) {
            sdf[i * 3] = base[i];
            sdf[i * 3 + 1] = top[i];
            sdf[i * 3 + 2] = van[i];
        }

        if (texture == 0) texture = GlStateManager._genTexture();
        GlUtil.resetUnpack();
        GlStateManager._bindTexture(texture);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL11C.GL_REPEAT);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL11C.GL_REPEAT);
        FloatBuffer buf = MemoryUtil.memAllocFloat(w * h * 3);
        buf.put(sdf).flip();
        // R: Aurum layout, G: its second tier, B: the resource pack's layout.
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGB16F, w, h, 0, GL11C.GL_RGB, GL11C.GL_FLOAT, buf);
        MemoryUtil.memFree(buf);
        GlStateManager._bindTexture(0);
        size = CELLS;
        AurumMod.LOGGER.info("Baked cloud shapes ({}x{}) in {} ms", w, h, (System.nanoTime() - start) / 1_000_000);
    }

    /** Signed distance (cloud cells, negative inside) of a cell mask, sampled at SUB sub-texels per cell. */
    private static float[] toSubTexelSdf(boolean[] cells) {
        int n = CELLS * SUB;
        boolean[] fine = new boolean[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) fine[y * n + x] = cells[(y / SUB) * CELLS + x / SUB];
        }
        float[] sdf = signedDistance(fine, n, n);
        for (int i = 0; i < sdf.length; i++) sdf[i] /= SUB;
        return sdf;
    }

    /**
     * [0] base layer, [1] second tier. Deterministic, tiling over the cell grid.
     *
     * <p>Each cloud is a cluster of 3-6 small overlapping rectangles (longer along the wind), the way a player would
     * build one: clean rectangular runs, as in the Story Mode references (thresholded noise gave sawtooth
     * outlines). Big clusters get one smaller box stacked on top. A slow noise field decides where clusters
     * gather and where the sky stays clear; lone small puffs fill in around them.
     */
    private static boolean[][] aurumLayout() {
        int n = CELLS;
        boolean[] base = new boolean[n * n], top = new boolean[n * n];
        float[] field = valueNoise(n, 8, 8, 9001);
        java.util.Random r = new java.util.Random(1234);
        int region = 16; // divides the 256-cell grid
        for (int ry = 0; ry < n; ry += region) {
            for (int rx = 0; rx < n; rx += region) {
                for (int attempt = 0; attempt < 7; attempt++) {
                    int cx = rx + r.nextInt(region), cy = ry + r.nextInt(region);
                    float density = field[Math.floorMod(cy, n) * n + Math.floorMod(cx, n)];
                    if (r.nextFloat() > 0.3f + 0.7f * density * density) continue;
                    int rects = 3 + r.nextInt(4);
                    int area = 0, bigW = 0, bigH = 0;
                    for (int k = 0; k < rects; k++) {
                        // Small, chunky boxes overlapping: volume, not flat sheets (side by side with Nostalgia).
                        int w = 1 + r.nextInt(5), h = 1 + r.nextInt(3);
                        int ox = cx + r.nextInt(5) - 2 - w / 2, oy = cy + r.nextInt(3) - 1 - h / 2;
                        fill(base, n, ox, oy, w, h);
                        area += w * h;
                        if (w * h > bigW * bigH) { bigW = w; bigH = h; }
                    }
                    if (area >= 16 && bigW >= 3 && bigH >= 2) {
                        int tw = Math.max(2, bigW * 3 / 5), th = Math.max(1, bigH * 3 / 5);
                        fill(top, n, cx - tw / 2, cy - th / 2, tw, th);
                    }
                }
                // Lone puffs.
                if (r.nextFloat() < 0.5f) {
                    fill(base, n, rx + r.nextInt(region), ry + r.nextInt(region), 1 + r.nextInt(2), 1);
                }
            }
        }
        // The second tier must stand on the base layer.
        for (int i = 0; i < top.length; i++) top[i] &= base[i];
        int covered = 0;
        for (boolean b : base) if (b) covered++;
        AurumMod.LOGGER.info("Aurum cloud layout covers {}% of the sky", covered * 100 / base.length);
        return new boolean[][]{base, top};
    }

    private static void fill(boolean[] mask, int n, int x0, int y0, int w, int h) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) mask[Math.floorMod(y, n) * n + Math.floorMod(x, n)] = true;
        }
    }

    private static float percentile(float[] v, float p) {
        float[] c = v.clone();
        java.util.Arrays.sort(c);
        return c[Math.min(c.length - 1, (int) (p * c.length))];
    }

    /** Tiling, smoothly interpolated value noise on an n x n grid with the given lattice counts. */
    private static float[] valueNoise(int n, int cx, int cy, int seed) {
        java.util.Random r = new java.util.Random(seed);
        float[] lattice = new float[cx * cy];
        for (int i = 0; i < lattice.length; i++) lattice[i] = r.nextFloat();
        float[] out = new float[n * n];
        for (int y = 0; y < n; y++) {
            float fy = (float) y / n * cy;
            int iy = (int) fy;
            float ty = fy - iy;
            ty = ty * ty * (3 - 2 * ty);
            for (int x = 0; x < n; x++) {
                float fx = (float) x / n * cx;
                int ix = (int) fx;
                float tx = fx - ix;
                tx = tx * tx * (3 - 2 * tx);
                float a = lattice[(iy % cy) * cx + ix % cx], b = lattice[(iy % cy) * cx + (ix + 1) % cx];
                float c = lattice[((iy + 1) % cy) * cx + ix % cx], d = lattice[((iy + 1) % cy) * cx + (ix + 1) % cx];
                out[y * n + x] = (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty;
            }
        }
        return out;
    }

    /** Tiling value noise in [0, 1] with 6 smooth cells across the texture (about 500 blocks each), shaped. */
    private static float[] fieldNoise(int w, int h) {
        int cells = 6;
        java.util.Random r = new java.util.Random(4711);
        float[] lattice = new float[cells * cells];
        for (int i = 0; i < lattice.length; i++) lattice[i] = r.nextFloat();
        float[] out = new float[w * h];
        for (int y = 0; y < h; y++) {
            float fy = (float) y / h * cells;
            int iy = (int) fy;
            float ty = fy - iy;
            ty = ty * ty * (3 - 2 * ty);
            for (int x = 0; x < w; x++) {
                float fx = (float) x / w * cells;
                int ix = (int) fx;
                float tx = fx - ix;
                tx = tx * tx * (3 - 2 * tx);
                float a = lattice[(iy % cells) * cells + ix % cells], b = lattice[(iy % cells) * cells + (ix + 1) % cells];
                float c = lattice[((iy + 1) % cells) * cells + ix % cells], d = lattice[((iy + 1) % cells) * cells + (ix + 1) % cells];
                float v = (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty;
                // Most of the sky keeps its clouds; roughly the lowest quarter of the field clears out.
                float t = Math.max(0f, Math.min(1f, (0.45f - v) / 0.2f));
                out[y * w + x] = t * t * (3 - 2 * t);
            }
        }
        return out;
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
