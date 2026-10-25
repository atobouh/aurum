package dev.impeccable.aurum.sky;

import dev.impeccable.aurum.AurumMod;
import dev.impeccable.aurum.config.AurumConfig;
import net.minecraft.client.world.ClientWorld;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns the current {@link SkyLut} and its GPU texture. Rebuilds happen on a background thread only when the sun
 * has moved past a threshold or the weather or sky settings changed, so the frame never pays for them
 * (spec rule 3). The finished table is uploaded on the render thread with one small glTexSubImage2D.
 */
public final class SkyState {
    /** Rebuild when the sun elevation sine moves this much (about 0.15 degrees). */
    private static final float SUN_THRESHOLD = 0.0026f;
    private static final float WEATHER_THRESHOLD = 0.01f;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Aurum sky builder");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private static SkyLut current;
    private static CompletableFuture<SkyLut> pending;
    private static int texture;
    private static FloatBuffer upload;
    private static final float[] displayed = new float[SkyLut.WIDTH * SkyLut.HEIGHT * 3];
    private static final float[] blendFrom = new float[displayed.length];
    private static long blendStarted;
    private static boolean blending;

    private SkyState() {}

    /** Called once per frame on the render thread with the smoothed sun elevation. */
    public static void update(ClientWorld world, float sunY, float tickDelta) {
        if (pending != null && pending.isDone()) {
            SkyLut built = pending.getNow(null);
            pending = null;
            if (built != null) {
                SkyLut previous = current;
                current = built;
                if (previous != null && Math.abs(previous.sunY - built.sunY) < 0.05f
                        && Math.abs(previous.rain - built.rain) < 0.2f) {
                    System.arraycopy(displayed, 0, blendFrom, 0, displayed.length);
                    blendStarted = System.nanoTime();
                    blending = true;
                } else {
                    System.arraycopy(built.rgb, 0, displayed, 0, displayed.length);
                    blending = false;
                    uploadTexture(displayed);
                }
            }
        }
        if (blending) {
            // The CPU has already computed both LUTs. Blend the small texture for a quarter second so sky and
            // terrain lighting ease together without adding a second sky fetch to every screen pixel.
            float t = Math.min(1f, (System.nanoTime() - blendStarted) / 250_000_000f);
            for (int i = 0; i < displayed.length; i++) displayed[i] = blendFrom[i] + (current.rgb[i] - blendFrom[i]) * t;
            uploadTexture(displayed);
            if (t >= 1f) blending = false;
        }
        if (pending != null) return;

        float rain = world.getRainGradient(tickDelta);
        float thunder = world.getThunderGradient(tickDelta);
        AurumConfig c = AurumConfig.get();
        SkyLut cur = current;
        boolean stale = cur == null
                || Math.abs(cur.sunY - sunY) > SUN_THRESHOLD
                || Math.abs(cur.rain - rain) > WEATHER_THRESHOLD
                || Math.abs(cur.thunder - thunder) > WEATHER_THRESHOLD
                || cur.sunsetIntensity != c.sunsetIntensity
                || cur.horizonWarmth != c.horizonWarmth
                || cur.rainDesaturation != c.rainDesaturation;
        if (!stale) return;

        int si = c.sunsetIntensity, hw = c.horizonWarmth, rd = c.rainDesaturation;
        if (cur == null) {
            // First frame: build synchronously once so there is never a frame without a sky.
            current = SkyLut.build(sunY, rain, thunder, si, hw, rd);
            System.arraycopy(current.rgb, 0, displayed, 0, displayed.length);
            uploadTexture(displayed);
        } else {
            pending = CompletableFuture.supplyAsync(() -> SkyLut.build(sunY, rain, thunder, si, hw, rd), WORKER)
                    .exceptionally(e -> {
                        AurumMod.LOGGER.error("Sky build failed", e);
                        return null;
                    });
        }
    }

    public static SkyLut current() {
        return current;
    }

    public static int texture() {
        return texture;
    }

    private static void uploadTexture(float[] rgb) {
        dev.impeccable.aurum.render.GlUtil.resetUnpack();
        int prevBinding = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        if (texture == 0) {
            texture = GL11C.glGenTextures();
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGB16F, SkyLut.WIDTH, SkyLut.HEIGHT, 0,
                    GL11C.GL_RGB, GL11C.GL_FLOAT, (FloatBuffer) null);
            upload = MemoryUtil.memAllocFloat(SkyLut.WIDTH * SkyLut.HEIGHT * 3);
        } else {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
        }
        upload.clear();
        upload.put(rgb).flip();
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
        GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, SkyLut.WIDTH, SkyLut.HEIGHT, GL11C.GL_RGB, GL11C.GL_FLOAT, upload);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, prevBinding);
    }
}
