package dev.impeccable.aurum.sky;

/**
 * Scattering atmosphere (Rayleigh, Mie, ozone absorption), evaluated on the CPU into a small lookup
 * table. Physically based scattering is what gives golden hour its real gradient: red-orange toward the sun,
 * blue-violet overhead, a bright aerosol glow around the disc. Artistic controls are applied on top.
 *
 * <p>Units are arbitrary but consistent: radiance for the sky, and {@code irradiance / PI} for the colours fed
 * to terrain lighting, so the sky and lit blocks share one exposure.
 */
public final class SkyModel {
    static final double R_GROUND = 6360e3;
    static final double R_TOP = 6420e3;
    static final double EYE = 200.0;

    static final double[] BETA_R = {5.802e-6, 13.558e-6, 33.1e-6};
    static final double H_R = 8000.0;
    static final double BETA_M_SCATTER = 3.996e-6;
    static final double BETA_M_EXT = 4.40e-6;
    static final double H_M = 1200.0;
    static final double MIE_G = 0.78;
    static final double[] BETA_OZONE = {0.650e-6, 1.881e-6, 0.085e-6};

    /** Sun irradiance at the top of the atmosphere. */
    public static final double SUN = 20.0;

    // Quadratic segment spacing resolves the dense air near the observer without spending most samples
    // in the almost-empty upper atmosphere. This work runs in the background LUT builder, not per pixel.
    private static final int VIEW_STEPS = 24;
    private static final int LIGHT_STEPS = 12;

    private SkyModel() {}

    /** Distance along the ray from height r (from planet centre) with direction cosine mu to a sphere of radius R; -1 if missed. */
    static double sphereExit(double r, double mu, double radius) {
        double b = r * mu;
        double c = r * r - radius * radius;
        double disc = b * b - c;
        if (disc < 0) return -1;
        double s = Math.sqrt(disc);
        double t1 = -b - s, t2 = -b + s;
        if (t1 > 0) return t1;
        return t2 > 0 ? t2 : -1;
    }

    static double ozone(double h) {
        return Math.max(0.0, 1.0 - Math.abs(h - 25000.0) / 15000.0);
    }

    /** Optical depth (Rayleigh, Mie, ozone) from a point toward the sun, or null if the ground blocks it. */
    static boolean opticalDepthToSun(double px, double py, double pz, double sx, double sy, double sz, double[] out) {
        double r = Math.sqrt(px * px + py * py + pz * pz);
        double mu = (px * sx + py * sy + pz * sz) / r;
        // Ground in the way: below the geometric horizon from this point.
        double groundHit = sphereExit(r, mu, R_GROUND);
        if (groundHit > 0 && mu < 0) {
            double b = r * mu;
            if (b * b - (r * r - R_GROUND * R_GROUND) > 0) return false;
        }
        double len = sphereExit(r, mu, R_TOP);
        double dR = 0, dM = 0, dO = 0;
        for (int i = 0; i < LIGHT_STEPS; i++) {
            double start = len * i * i / (LIGHT_STEPS * LIGHT_STEPS);
            double end = len * (i + 1) * (i + 1) / (LIGHT_STEPS * LIGHT_STEPS);
            double ds = end - start;
            double t = (start + end) * 0.5;
            double x = px + sx * t, y = py + sy * t, z = pz + sz * t;
            double h = Math.sqrt(x * x + y * y + z * z) - R_GROUND;
            dR += Math.exp(-h / H_R) * ds;
            dM += Math.exp(-h / H_M) * ds;
            dO += ozone(h) * ds;
        }
        out[0] = dR;
        out[1] = dM;
        out[2] = dO;
        return true;
    }

    /**
     * Sky radiance seen from the eye along (vx,vy,vz) with the sun along (sx,sy,sz); both unit vectors, y up.
     * Writes RGB into out.
     */
    public static void radiance(double vx, double vy, double vz, double sx, double sy, double sz, double[] out) {
        radianceAtHeight(vx, vy, vz, sx, sy, sz, EYE, null, out);
    }

    static void radiance(double vx, double vy, double vz, double sx, double sy, double sz,
                         DiffuseSky diffuse, double[] out) {
        radianceAtHeight(vx, vy, vz, sx, sy, sz, EYE, diffuse, out);
    }

    private static void radianceAtHeight(double vx, double vy, double vz, double sx, double sy, double sz,
                                        double eyeHeight, DiffuseSky diffuse, double[] out) {
        double ex = 0, ey = R_GROUND + eyeHeight, ez = 0;
        double r = ey;
        double mu = vy;
        double len;
        double ground = sphereExit(r, mu, R_GROUND);
        if (ground > 0 && mu < 0) {
            len = ground;
        } else {
            len = sphereExit(r, mu, R_TOP);
        }
        double cosTheta = vx * sx + vy * sy + vz * sz;
        double phaseR = 3.0 / (16.0 * Math.PI) * (1.0 + cosTheta * cosTheta);
        double g2 = MIE_G * MIE_G;
        double phaseM = 3.0 / (8.0 * Math.PI) * ((1.0 - g2) * (1.0 + cosTheta * cosTheta))
                / ((2.0 + g2) * Math.pow(1.0 + g2 - 2.0 * MIE_G * cosTheta, 1.5));

        double sumRr = 0, sumRg = 0, sumRb = 0, sumMr = 0, sumMg = 0, sumMb = 0;
        double multiR = 0, multiG = 0, multiB = 0;
        double odR = 0, odM = 0, odO = 0;
        double[] light = new double[3];
        double[] incident = diffuse == null ? null : new double[3];
        for (int i = 0; i < VIEW_STEPS; i++) {
            double start = len * i * i / (VIEW_STEPS * VIEW_STEPS);
            double end = len * (i + 1) * (i + 1) / (VIEW_STEPS * VIEW_STEPS);
            double ds = end - start;
            double t = (start + end) * 0.5;
            double px = ex + vx * t, py = ey + vy * t, pz = ez + vz * t;
            double h = Math.sqrt(px * px + py * py + pz * pz) - R_GROUND;
            double hr = Math.exp(-h / H_R) * ds;
            double hm = Math.exp(-h / H_M) * ds;
            double ho = ozone(h) * ds;
            // Integrate attenuation through the segment rather than putting all its scattering at the far
            // end. The same view transmittance carries direct and diffuse scattering.
            double ar = segmentTransmission(0, odR, odM, odO, hr, hm, ho);
            double ag = segmentTransmission(1, odR, odM, odO, hr, hm, ho);
            double ab = segmentTransmission(2, odR, odM, odO, hr, hm, ho);
            if (opticalDepthToSun(px, py, pz, sx, sy, sz, light)) {
                double lr = ar * Math.exp(-BETA_R[0] * light[0] - BETA_M_EXT * light[1] - BETA_OZONE[0] * light[2]);
                double lg = ag * Math.exp(-BETA_R[1] * light[0] - BETA_M_EXT * light[1] - BETA_OZONE[1] * light[2]);
                double lb = ab * Math.exp(-BETA_R[2] * light[0] - BETA_M_EXT * light[1] - BETA_OZONE[2] * light[2]);
                sumRr += lr * hr; sumRg += lg * hr; sumRb += lb * hr;
                sumMr += lr * hm; sumMg += lg * hm; sumMb += lb * hm;
            }
            if (diffuse != null) {
                diffuse.sample(h, incident);
                multiR += ar * (BETA_R[0] * hr + BETA_M_SCATTER * hm) * incident[0];
                multiG += ag * (BETA_R[1] * hr + BETA_M_SCATTER * hm) * incident[1];
                multiB += ab * (BETA_R[2] * hr + BETA_M_SCATTER * hm) * incident[2];
            }
            odR += hr;
            odM += hm;
            odO += ho;
        }
        out[0] = SUN * (sumRr * BETA_R[0] * phaseR + sumMr * BETA_M_SCATTER * phaseM) + multiR;
        out[1] = SUN * (sumRg * BETA_R[1] * phaseR + sumMg * BETA_M_SCATTER * phaseM) + multiG;
        out[2] = SUN * (sumRb * BETA_R[2] * phaseR + sumMb * BETA_M_SCATTER * phaseM) + multiB;
    }

    /**
     * Approximate second scattering: integrate first-order incident light over a sphere at seven heights,
     * then redistribute it isotropically along the view ray. This adds diffuse blue/violet light to long
     * amber paths instead of painting a white horizon. It is a height-only approximation at the observer's
     * solar elevation, not a full position-dependent multiple-scattering solution or ground-bounce model.
     */
    static final class DiffuseSky {
        private static final double[] HEIGHTS = {EYE, 1200, 4000, 8000, 16000, 32000, 55000};
        private static final int DIRECTIONS = 32;
        private final double[][] mean = new double[HEIGHTS.length][3];

        static DiffuseSky build(double sx, double sy) {
            DiffuseSky sky = new DiffuseSky();
            double[] radiance = new double[3];
            for (int h = 0; h < HEIGHTS.length; h++) {
                for (int i = 0; i < DIRECTIONS; i++) {
                    // Equal-area Fibonacci sphere. Mean radiance already includes the 1/(4 PI) phase term.
                    double y = 1.0 - 2.0 * (i + 0.5) / DIRECTIONS;
                    double horizontal = Math.sqrt(1.0 - y * y);
                    double a = i * Math.PI * (3.0 - Math.sqrt(5.0));
                    radianceAtHeight(horizontal * Math.cos(a), y, horizontal * Math.sin(a),
                            sx, sy, 0.0, HEIGHTS[h], null, radiance);
                    for (int c = 0; c < 3; c++) sky.mean[h][c] += radiance[c] / DIRECTIONS;
                }
            }
            return sky;
        }

        private void sample(double height, double[] out) {
            int h = 0;
            while (h < HEIGHTS.length - 2 && height > HEIGHTS[h + 1]) h++;
            double t = Math.max(0, Math.min(1, (height - HEIGHTS[h]) / (HEIGHTS[h + 1] - HEIGHTS[h])));
            for (int c = 0; c < 3; c++) out[c] = mean[h][c] + (mean[h + 1][c] - mean[h][c]) * t;
        }
    }

    private static double segmentTransmission(int c, double odR, double odM, double odO,
                                             double hr, double hm, double ho) {
        double opticalDepth = BETA_R[c] * odR + BETA_M_EXT * odM + BETA_OZONE[c] * odO;
        double segmentDepth = BETA_R[c] * hr + BETA_M_EXT * hm + BETA_OZONE[c] * ho;
        double integral = segmentDepth > 1e-8 ? -Math.expm1(-segmentDepth) / segmentDepth : 1.0;
        return Math.exp(-opticalDepth) * integral;
    }

    /** Sunlight transmittance at the eye for a sun at the given elevation sine. Writes RGB into out. */
    public static void sunTransmittance(double sunY, double[] out) {
        double sx = Math.sqrt(Math.max(0.0, 1.0 - sunY * sunY));
        double[] od = new double[3];
        if (!opticalDepthToSun(0, R_GROUND + EYE, 0, sx, sunY, 0, od)) {
            out[0] = out[1] = out[2] = 0;
            return;
        }
        for (int c = 0; c < 3; c++) {
            out[c] = Math.exp(-(BETA_R[c] * od[0] + BETA_M_EXT * od[1] + BETA_OZONE[c] * od[2]));
        }
    }
}
