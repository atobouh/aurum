package dev.impeccable.aurum.director;

import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.sky.SkyLut;

/**
 * Per-frame lighting values derived from the current sky table and the settings. Everything here is computed
 * once per frame on the CPU and sent as uniforms (spec rule 7).
 */
public final class Atmosphere {
    /** Noon reference brightness (sun + sky on an upward surface), used to anchor exposure. */
    private static final float REFERENCE = 6.2f;
    private static final float BASE_EXPOSURE = 0.25f;

    public static final float[] lightDir = {0, 1, 0};
    /** Direction toward the sun (even at night), world space. */
    public static final float[] sunDir = {0, 1, 0};
    public static final float[] lightColor = new float[3];
    public static final float[] skyAmbient = new float[3];
    public static final float[] groundAmbient = new float[3];
    public static final float[] blockLight = new float[3];
    /** Per-channel gains that make midday sun + sky read as neutral white (camera white balance). */
    public static final float[] whiteBalance = {1f, 1f, 1f};
    private static float[] daylight;

    /** Linear multiplier applied before the tonemap; adapts gently between day and night. */
    public static float exposure = BASE_EXPOSURE;
    public static float minLight;
    public static float sunY;

    private static float adapt = 1f;
    private static long lastNanos;

    private Atmosphere() {}

    public static void update(SkyLut lut, float gamma) {
        AurumConfig c = AurumConfig.get();
        sunY = SunState.elevation();
        float angle = SunState.angle();
        // Sun direction from the sky angle (0 = noon): rises in +X (east), sets in -X (west).
        float sx = (float) -Math.sin(angle), sy = (float) Math.cos(angle);
        sunDir[0] = sx;
        sunDir[1] = sy;
        sunDir[2] = 0f;
        boolean day = sy > -0.02f;
        lightDir[0] = day ? sx : -sx;
        lightDir[1] = day ? sy : -sy;
        lightDir[2] = 0f;

        float sun = c.sunStrength / 100f;
        float amb = c.ambientStrength / 100f;
        for (int i = 0; i < 3; i++) {
            lightColor[i] = lut.sunColor[i] * sun;
            skyAmbient[i] = lut.skyAmbient[i] * amb;
            groundAmbient[i] = lut.groundAmbient[i] * amb;
        }

        // Gentle eye adaptation: nights and storms are darker than noon, but not black. Smoothed over ~2 s.
        // Surfaces facing a low sun stay bright, so judge brightness as if the sun were at least 25 degrees up,
        // and only allow a strong boost once the sun is gone. This keeps golden hour saturated, not bleached.
        float illum = luminance(lut.sunColor) * Math.max(sy, 0.42f) + luminance(lut.skyAmbient) * 2.5f + 1e-4f;
        float sunUp = Math.max(0f, Math.min(1f, (sy + 0.05f) / 0.15f));
        float maxBoost = 7.0f + (1.8f - 7.0f) * sunUp * sunUp * (3f - 2f * sunUp);
        float target = (float) Math.min(Math.max(Math.pow(REFERENCE / illum, 0.42), 1.0), maxBoost);
        target *= 1f + 0.35f * gamma;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 1f : Math.min((now - lastNanos) / 1e9f, 0.25f);
        lastNanos = now;
        adapt += (target - adapt) * (1f - (float) Math.exp(-dt * 0.5f));
        exposure = BASE_EXPOSURE * adapt * (c.exposure / 100f);

        // White balance: measured once from a clear midday sky (sun ~58 degrees up), then blended by the slider.
        // Golden hour stays warm because the balance is fixed to daylight, as a camera on "daylight" would be.
        if (daylight == null) {
            SkyLut ref = SkyLut.build(0.85f, 0f, 0f, 100, 100, 0);
            daylight = new float[3];
            for (int i = 0; i < 3; i++) daylight[i] = ref.sunColor[i] * 0.85f + ref.skyAmbient[i];
        }
        float dl = luminance(daylight);
        float wb = c.whiteBalance / 100f;
        for (int i = 0; i < 3; i++) whiteBalance[i] = 1f + (dl / daylight[i] - 1f) * wb;

        // Torch light is defined in display terms so it looks the same at noon and midnight.
        float warmth = c.blockLightWarmth / 100f;
        float k = 1.9f / (BASE_EXPOSURE * adapt);
        blockLight[0] = k * 1.00f;
        blockLight[1] = k * (0.92f - 0.30f * warmth);
        blockLight[2] = k * (0.84f - 0.56f * warmth);
        minLight = (0.0035f + 0.006f * gamma) / (BASE_EXPOSURE * adapt);
    }

    static float luminance(float[] c) {
        return 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2];
    }
}
