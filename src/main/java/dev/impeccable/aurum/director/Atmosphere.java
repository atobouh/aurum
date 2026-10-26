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
    // Daylight measured against Bliss / Complementary / Photon: their frames average L 0.55-0.61, ours 0.43
    // (docs/research/LOOK_FOG_LIGHT.md).
    private static final float BASE_EXPOSURE = 0.215f;

    public static final float[] lightDir = {0, 1, 0};
    /** Direction toward the sun (even at night), world space. */
    public static final float[] sunDir = {0, 1, 0};
    public static final float[] lightColor = new float[3];
    public static final float[] skyAmbient = new float[3];
    public static final float[] skySideToward = new float[3];
    public static final float[] skySideAway = new float[3];
    public static final float[] groundAmbient = new float[3];
    public static final float[] blockLight = new float[3];
    /** Per-channel gains that make midday sun + sky read as neutral white (camera white balance). */
    public static final float[] whiteBalance = {1f, 1f, 1f};
    /** Undoes the daytime sky-fill reduction for clouds (0.8.1). */
    public static float cloudAmbientScale = 1f;
    /** Look-preset sky tone for the shaders: saturation offset, lavender/teal tint. */
    public static final float[] skyTone = {0f, 0f};
    /** Display-sky clarity / temperature; intentionally independent of surface irradiance. */
    public static final float[] skyAppearance = {1f, -0.2f};
    private static float[] daylight;

    /** Linear multiplier applied before the tonemap; adapts gently between day and night. */
    public static float exposure = BASE_EXPOSURE;
    public static float minLight;
    public static float sunY;
    /** 0..1: how much "golden hour" it is (sun low but up), for the glow and the warm grade. */
    public static float golden;

    private static float adapt = 1f;
    private static long lastColorNanos;
    private static SkyLut lastLut;
    private static long lastNanos;

    private Atmosphere() {}

    public static void update(SkyLut lut, float gamma) {
        AurumConfig c = AurumConfig.get();
        sunY = SunState.elevation();
        float angle = SunState.angle();
        // Sun direction from the sky angle (0 = noon): rises in +X (east), sets in -X (west).
        float sx = (float) -Math.sin(angle), sy = (float) Math.cos(angle);
        float t1 = Math.max(0f, Math.min(1f, (0.45f - sy) / 0.40f)), t2 = Math.max(0f, Math.min(1f, (sy + 0.12f) / 0.10f));
        golden = t1 * t1 * (3f - 2f * t1) * t2 * t2 * (3f - 2f * t2);
        sunDir[0] = sx;
        sunDir[1] = sy;
        sunDir[2] = 0f;
        boolean day = sy > -0.02f;
        lightDir[0] = day ? sx : -sx;
        lightDir[1] = day ? sy : -sy;
        lightDir[2] = 0f;

        float sun = c.sunStrength / 100f;
        float amb = c.ambientStrength / 100f;
        // Golden-hour grade on the light itself (reference study: in every good sunset shot the whole frame is
        // warm, shade included). Physics alone gives near-neutral light until the sun is almost down, so the warmth
        // is pushed in from ~25 degrees: amber sunlight, peach light in the shade. Scaled by the Sunset slider.
        float g = golden * (c.sunsetIntensity / 100f);
        // At a low sun the sky fills the shadow side, but it must not lift every leaf and wall to the same
        // brightness as the lit side. Preserve the sun term and ease only the diffuse sky fill downward.
        amb *= 1f - 0.30f * g;
        // The reference sunset scenes keep a bright gold strip on sun-facing grass while deep silhouettes
        // stay dark. A little more low-angle direct light raises that contrast without adding shader work.
        sun *= 1f + 0.30f * g;
        // 0.8.1 (user: "I feel like there is no sun"): by day the sky fill was too strong against the sun (lit to
        // shade about 3:1; a clear day is 5-8:1), so scenes read overcast under a blue sky. Exposure adapts to the
        // total, so this shifts contrast toward the sun rather than brightening the frame.
        float dayAmt = Math.max(0f, Math.min(1f, (sy - 0.15f) / 0.25f)) * (1f - golden);
        sun *= 1f + 0.50f * dayAmt;
        amb *= 1f - 0.30f * dayAmt;
        // Clouds are lit by the open sky, not shaded ground: keep their sky light where it was.
        cloudAmbientScale = 1f / (1f - 0.30f * dayAmt);
        // Strong enough that lit foliage turns gold (references: foliage red/green ~1.0 at golden hour).
        // A slightly warm sun at every hour: the references' daylight frames are neutral-to-warm (+0.00 to +0.08
        // red over blue), ours read cold blue (-0.12 to -0.21).
        // 0.8.1: a little warmer by day, so sunlit grass and leaves read sunny (yellow-green), not grey-green.
        // 0.9.0: Photon's morning/evening sun tint (1.05, 0.84, 0.93) instead of pulling blue out of an already
        // red low sun - with the per-channel Photon curve that turned whole sunset scenes blood red (user).
        float[] sunTint = {1.10f + (1.05f - 1.10f) * g, 1.03f + (0.84f - 1.03f) * g, 0.93f + (0.93f - 0.93f) * g};
        // Keep the shade readable but cooler than the low sun. Warming both equally flattens the scene.
        float[] ambTint = {1f + 0.12f * g, 1f + 0.04f * g, 1f - 0.08f * g};
        // Look presets: warmer or cooler shade.
        float sw = c.shadeWarmth / 50f;
        ambTint[0] *= 1f + 0.10f * sw;
        ambTint[2] *= 1f - 0.12f * sw;
        // The sky table is rebuilt in small sun steps; ease the light colours toward each new table over ~0.4 s
        // so lighting never steps, even by a fraction of a shade.
        long nowC = System.nanoTime();
        float dtC = lastColorNanos == 0 ? 1f : Math.min((nowC - lastColorNanos) / 1e9f, 0.5f);
        lastColorNanos = nowC;
        float kC = lastLut == null || Math.abs(lastLut.sunY - lut.sunY) > 0.05f ? 1f : 1f - (float) Math.exp(-dtC / 0.4f);
        lastLut = lut;
        float skyGrey = luminance(lut.skyAmbient);
        float towardGrey = luminance(lut.skySideToward);
        float awayGrey = luminance(lut.skySideAway);
        for (int i = 0; i < 3; i++) {
            lightColor[i] += (lut.sunColor[i] * sun * sunTint[i] - lightColor[i]) * kC;
            // Shade takes the sky's light but only part of its blue: in the references shade is cool-neutral, ours
            // turned everything in shadow blue.
            float skyA = lut.skyAmbient[i] + (skyGrey - lut.skyAmbient[i]) * 0.35f;
            skyAmbient[i] += (skyA * amb * ambTint[i] - skyAmbient[i]) * kC;
            float toward = lut.skySideToward[i] + (towardGrey - lut.skySideToward[i]) * 0.35f;
            float away = lut.skySideAway[i] + (awayGrey - lut.skySideAway[i]) * 0.35f;
            skySideToward[i] += (toward * amb * ambTint[i] - skySideToward[i]) * kC;
            skySideAway[i] += (away * amb * ambTint[i] - skySideAway[i]) * kC;
            groundAmbient[i] += (lut.groundAmbient[i] * amb * sunTint[i] - groundAmbient[i]) * kC;
        }

        // Gentle eye adaptation: nights and storms are darker than noon, but not black. Smoothed over ~2 s.
        // Surfaces facing a low sun stay bright, so judge brightness as if the sun were at least 25 degrees up,
        // and only allow a strong boost once the sun is gone. This keeps golden hour saturated, not bleached.
        float illum = luminance(lut.sunColor) * Math.max(sy, 0.42f) + luminance(lut.skyAmbient) * 2.5f + 1e-4f;
        float sunUp = Math.max(0f, Math.min(1f, (sy + 0.05f) / 0.15f));
        // Overcast days stay bright like Bliss's (L 0.55); 0.3.3 rain at noon measured L 0.33, dark and blue.
        // The former golden-hour boost raised the already luminous sun/sky into a broad near-white patch.
        // Hold the camera exposure closer to daylight so the horizon keeps colour and the land can silhouette.
        float dayBoost = 1.35f - 0.10f * golden + 1.1f * lut.rain;
        float maxBoost = 7.0f + (dayBoost - 7.0f) * sunUp * sunUp * (3f - 2f * sunUp);
        float target = (float) Math.min(Math.max(Math.pow(REFERENCE / illum, 0.42), 1.0), maxBoost);
        target *= 1f + 0.35f * gamma;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 1f : Math.min((now - lastNanos) / 1e9f, 0.25f);
        lastNanos = now;
        adapt += (target - adapt) * (1f - (float) Math.exp(-dt * 0.5f));
        // Hold a little more highlight headroom at golden hour. This applies to the entire lit scene, so sky,
        // terrain and water retain their relative brightness while the amber horizon keeps its chroma.
        exposure = BASE_EXPOSURE * adapt * (c.exposure / 100f) * (1f - 0.22f * golden);

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
        // Look presets: overall colour temperature (camera-style warm/cool shift).
        float temp = c.temperature / 50f;
        // Strong enough to move a whole frame by ~0.2 in red-minus-blue at the ends (measured on the look renders).
        whiteBalance[0] *= 1f + 0.24f * temp;
        whiteBalance[1] *= 1f + 0.04f * temp;
        whiteBalance[2] *= 1f - 0.28f * temp;
        skyTone[0] = (c.skySaturation - 100) / 100f;
        skyTone[1] = c.skyLavender / 50f;
        skyAppearance[0] = c.skyClarity / 100f * (1f - 0.85f * lut.rain);
        skyAppearance[1] = c.skyTemperature / 50f * (1f - 0.85f * lut.rain);

        // Torch light is defined in display terms so it looks the same at noon and midnight.
        float warmth = c.blockLightWarmth / 100f;
        float k = 1.9f / (BASE_EXPOSURE * adapt);
        blockLight[0] = k * 1.00f;
        blockLight[1] = k * (0.92f - 0.30f * warmth);
        blockLight[2] = k * (0.84f - 0.56f * warmth);
        minLight = (0.0035f + 0.006f * gamma) / (BASE_EXPOSURE * adapt);
    }

    /** Daytime sky grade amount: full with the sun well up, none at golden hour or night. */
    public static float skyGrade() {
        float day = Math.max(0f, Math.min(1f, (sunY - 0.05f) / 0.20f));
        return day * (1f - golden);
    }

    static float luminance(float[] c) {
        return 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2];
    }
}
