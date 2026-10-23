package dev.impeccable.aurum.sky;

/**
 * One baked sky: a 128 x 64 radiance table for the current sun elevation and weather, plus the light colours
 * terrain needs, all derived from the same model so blocks and sky always agree.
 *
 * <p>Table axes, chosen so the shader needs no asin/acos (only a sqrt each):
 * x = {@code sin(a/2)} where a is the horizontal angle between view and sun (fine detail near the sun);
 * y = {@code 0.5 + 0.5 * sign(s) * sqrt(|s|)} where s is the sine of the view elevation (most rows near the horizon).
 */
public final class SkyLut {
    public static final int WIDTH = 128;
    public static final int HEIGHT = 64;

    /** Inputs this table was built for. */
    public final float sunY;
    public final float rain;
    public final float thunder;
    public final int sunsetIntensity;
    public final int horizonWarmth;
    public final int rainDesaturation;

    /** RGB radiance, row-major, bottom row = looking straight down. */
    public final float[] rgb = new float[WIDTH * HEIGHT * 3];

    /** Direct sunlight reaching the ground, as irradiance / PI. */
    public final float[] sunColor = new float[3];
    /** Radiance of the sun disc itself (zero below the horizon and in rain). */
    public final float[] sunDisc = new float[3];
    /** Warm light the sun scatters into haze (fog in-scattering and the glow around the sun). */
    public final float[] sunGlow = new float[3];
    /** Light from the sky dome on an upward surface, as irradiance / PI. */
    public final float[] skyAmbient = new float[3];
    /** Sky irradiance on vertical faces looking toward and away from the sun's azimuth. */
    public final float[] skySideToward = new float[3];
    public final float[] skySideAway = new float[3];
    /** Light bounced up from the ground onto a downward surface. */
    public final float[] groundAmbient = new float[3];

    private SkyLut(float sunY, float rain, float thunder, int sunsetIntensity, int horizonWarmth, int rainDesaturation) {
        this.sunY = sunY;
        this.rain = rain;
        this.thunder = thunder;
        this.sunsetIntensity = sunsetIntensity;
        this.horizonWarmth = horizonWarmth;
        this.rainDesaturation = rainDesaturation;
    }

    /** Sine of the view elevation at the centre of a row. */
    public static double sinElevationForRow(int row) {
        double t = ((row + 0.5) / HEIGHT - 0.5) * 2.0;
        return Math.signum(t) * t * t;
    }

    public static SkyLut build(float sunY, float rain, float thunder, int sunsetIntensity, int horizonWarmth, int rainDesaturation) {
        SkyLut lut = new SkyLut(sunY, rain, thunder, sunsetIntensity, horizonWarmth, rainDesaturation);
        double sy = Math.max(-1.0, Math.min(1.0, sunY));
        double sx = Math.sqrt(Math.max(0.0, 1.0 - sy * sy));
        double[] out = new double[3];

        // How "golden hour" it is: 1 with the sun near the horizon, 0 at noon and deep night.
        double low = smooth(0.45, 0.05, sy) * smooth(-0.22, -0.02, sy);
        // At 100% golden hour is 20% more saturated than the raw model; 0% gives the raw model.
        double sat = 1.0 + 0.10 * (sunsetIntensity / 100.0) * low;
        double warmth = horizonWarmth / 100.0;
        double grey = rain * (rainDesaturation / 100.0);
        double dim = 1.0 - 0.55 * rain - 0.25 * thunder;
        double night = smooth(0.02, -0.25, sy);

        double irrR = 0, irrG = 0, irrB = 0;
        double[] sideToward = new double[3], sideAway = new double[3];
        for (int row = 0; row < HEIGHT; row++) {
            double se = sinElevationForRow(row);
            double e = Math.asin(se);
            double ce = Math.cos(e);
            // Irradiance integrand: L sin(e) cos(e) de da = L y dy da, with y = t^2 so dy = 2|t| dt, dt = 2/H.
            double dy = 4.0 * Math.abs(((row + 0.5) / HEIGHT - 0.5) * 2.0) / HEIGHT;
            for (int col = 0; col < WIDTH; col++) {
                double sHalf = (col + 0.5) / WIDTH;
                double a = 2.0 * Math.asin(sHalf);
                double da = 2.0 / Math.sqrt(1.0 - sHalf * sHalf) / WIDTH;
                double vx = ce * Math.cos(a), vz = ce * Math.sin(a);
                SkyModel.radiance(vx, se, vz, sx, sy, 0.0, out);
                double r = out[0], g = out[1], b = out[2];

                // Artistic: saturation at golden hour, extra warmth toward the sun near the horizon.
                double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                r = lum + (r - lum) * sat;
                g = lum + (g - lum) * sat;
                b = lum + (b - lum) * sat;
                // Golden hour warmth: push the low sky toward orange (not yellow), strongest toward the sun.
                double toward = 0.35 + 0.65 * Math.pow(Math.max(0.0, Math.cos(a)), 2.0);
                // Spread over the lower sky (not a thin strip) and kept gentle so it reads as light, not paint.
                double wk = warmth * toward * Math.exp(-Math.abs(e) * 1.5) * low * (sunsetIntensity / 100.0);
                r *= 1.0 + 0.30 * wk;
                g *= 1.0 - 0.02 * wk;
                b *= 1.0 - 0.40 * wk;

                // With the sun high, single scattering leaves the horizon slightly yellow (no multiple
                // scattering). Real skies are white-blue there; whiten it. Also lift daytime saturation a little,
                // since the AgX display transform greys blues.
                double high = smooth(0.08, 0.35, sy);
                double nearHorizon = Math.exp(-Math.max(e, 0.0) * 9.0) * high;
                lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                r = r + (lum * 0.86 - r) * 0.7 * nearHorizon;
                g = g + (lum * 0.98 - g) * 0.7 * nearHorizon;
                b = b + (lum * 1.22 - b) * 0.7 * nearHorizon;
                double daySat = 1.0 + 0.45 * high;
                lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                r = lum + (r - lum) * daySat;
                g = lum + (g - lum) * daySat;
                b = lum + (b - lum) * daySat;

                // Golden-hour palette (from the reference study, docs/research/LOOK_PHASE1_2.md): the good packs
                // turn the whole upper sky peach-pink-lavender at low sun, not just the horizon strip. Blend the
                // sky's chroma toward that palette, strongest away from the horizon glow.
                double gh = low * (sunsetIntensity / 100.0);
                if (gh > 0.0 && e > 0.0) {
                    double l2 = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                    double upper = Math.min(1.0, e * 3.0);
                    double tr = 1.30, tg = 0.86, tb = 0.80; // warm peach near the horizon side
                    double ur = 1.16, ug = 0.80, ub = 1.22; // clear pink-lavender higher up
                    double pr = tr + (ur - tr) * upper, pg = tg + (ug - tg) * upper, pb = tb + (ub - tb) * upper;
                    double k = 0.8 * gh;
                    r = r + (l2 * pr - r) * k;
                    g = g + (l2 * pg - g) * k;
                    b = b + (l2 * pb - b) * k;
                }

                // Night: a simple dark blue gradient under the vanilla stars and moon.
                double up = Math.sqrt(Math.max(0.0, se));
                r += night * (0.0045 + 0.0010 * (1 - up));
                g += night * (0.0065 + 0.0012 * (1 - up));
                b += night * (0.0140 - 0.0030 * up);

                // Weather: pull toward grey and darken.
                lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                r = (r + (lum * 0.95 - r) * grey) * dim;
                g = (g + (lum * 1.00 - g) * grey) * dim;
                b = (b + (lum * 1.08 - b) * grey) * dim;

                int i = (row * WIDTH + col) * 3;
                lut.rgb[i] = (float) Math.max(r, 0);
                lut.rgb[i + 1] = (float) Math.max(g, 0);
                lut.rgb[i + 2] = (float) Math.max(b, 0);

                if (e > 0) {
                    // Irradiance on an upward surface: L * cos(zenith) * dOmega.
                    double w = se * dy * da;
                    irrR += r * w;
                    irrG += g * w;
                    irrB += b * w;
                    // Integrate the same sky for vertical faces. The table stores half the azimuths;
                    // the other half is its mirror about the sun, hence the factor of two below.
                    double sideCos = ce * Math.cos(a);
                    double sideW = Math.abs(sideCos) * dy * da;
                    double[] side = sideCos >= 0.0 ? sideToward : sideAway;
                    side[0] += r * sideW;
                    side[1] += g * sideW;
                    side[2] += b * sideW;
                }
            }
        }
        // The table covers azimuth 0..PI; the sky is symmetric about the sun, so double it. Store E / PI.
        // Single scattering underestimates skylight (no multiple scattering); compensate, but keep the sun
        // clearly stronger than the sky so lit and shaded sides read as 3D.
        double multi = 1.75;
        lut.skyAmbient[0] = (float) (irrR * 2.0 / Math.PI * multi);
        lut.skyAmbient[1] = (float) (irrG * 2.0 / Math.PI * multi);
        lut.skyAmbient[2] = (float) (irrB * 2.0 / Math.PI * multi);
        for (int c = 0; c < 3; c++) {
            lut.skySideToward[c] = (float) (sideToward[c] * 2.0 / Math.PI * multi);
            lut.skySideAway[c] = (float) (sideAway[c] * 2.0 / Math.PI * multi);
        }

        SkyModel.sunTransmittance(sy, out);
        double sunUp = smooth(-0.03, 0.04, sy);
        double cloudCover = 1.0 - 0.85 * rain;
        for (int c = 0; c < 3; c++) {
            lut.sunColor[c] = (float) (SkyModel.SUN * out[c] / Math.PI * sunUp * cloudCover);
        }
        double discVisible = smooth(-0.02, 0.01, sy) * (1.0 - rain);
        for (int c = 0; c < 3; c++) {
            lut.sunDisc[c] = (float) (SkyModel.SUN * out[c] * 2.5 * discVisible);
            // Glow: the transmitted sun colour, a little extra warm at golden hour, fading out once the sun is gone.
            // Saturated gold-orange, not pale: the glow is what makes golden-hour shots read as golden.
            double warmBias = c == 0 ? 1.0 : c == 1 ? 0.68 : 0.30;
            // Scaled to the sky's own brightness near the sun (the physical aureole is bright but pale there).
            lut.sunGlow[c] = (float) (SkyModel.SUN * 0.35 * out[c] * Math.pow(warmBias, low) * smooth(-0.06, 0.02, sy) * (1.0 - 0.8 * rain));
        }
        // Moonlight, cool and dim, crossfades in as the sun sets.
        double moon = smooth(-0.02, -0.15, sy) * (1.0 - 0.7 * rain);
        lut.sunColor[0] += (float) (0.030 * moon);
        lut.sunColor[1] += (float) (0.040 * moon);
        lut.sunColor[2] += (float) (0.070 * moon);

        // Ground bounce: a fraction of the light hitting the ground, tinted warm (soil, grass average).
        double groundAlbedo = 0.12;
        double up = Math.max(0.0, sy);
        for (int c = 0; c < 3; c++) {
            double hit = lut.sunColor[c] * up + lut.skyAmbient[c];
            double tint = c == 0 ? 1.05 : c == 1 ? 1.0 : 0.85;
            lut.groundAmbient[c] = (float) (hit * groundAlbedo * tint);
        }
        return lut;
    }

    /** Smoothstep that also works with edge0 > edge1. */
    static double smooth(double edge0, double edge1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }
}
