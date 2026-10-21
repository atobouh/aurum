package dev.impeccable.aurum.director;

import dev.impeccable.aurum.config.AurumConfig;
import net.minecraft.client.world.ClientWorld;

/**
 * Wind for waving plants. Oscillator phases and the gust scroll are accumulated in double precision on the CPU
 * and sent wrapped to [0,1) / [0,256), so the shader never loses precision over long sessions, and changing the
 * speed slider never makes plants jump (the phase just advances at a new rate).
 */
public final class WindState {
    /** Oscillator frequencies in Hz, deliberately not multiples of each other (after GPU Gems 3 ch. 16). */
    private static final double[] FREQ = {0.31, 0.197, 1.13, 1.71};

    public static final float[] phase = new float[4];
    public static float dirX = 1f, dirZ = 0f;
    public static float strength;
    public static float gustX, gustZ;

    private static final double[] acc = new double[4];
    private static double gustAccX, gustAccZ;
    private static double time;
    private static float smoothedWeather;
    private static long lastNanos;

    private WindState() {}

    public static void update(ClientWorld world, float tickDelta, boolean paused) {
        AurumConfig c = AurumConfig.get();
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min((now - lastNanos) / 1e9, 0.1);
        lastNanos = now;
        if (paused) dt = 0;

        float weather = world.getRainGradient(tickDelta) * 0.7f + world.getThunderGradient(tickDelta) * 0.8f;
        smoothedWeather += (weather - smoothedWeather) * (float) (1 - Math.exp(-dt / 4.0));

        double speed = c.wavingSpeed / 100.0 * (1.0 + 0.6 * smoothedWeather);
        time += dt;
        for (int i = 0; i < 4; i++) {
            acc[i] = (acc[i] + dt * FREQ[i] * speed) % 1.0;
            phase[i] = (float) acc[i];
        }

        // Direction drifts slowly around a prevailing westerly-ish wind.
        double angle = 0.6 + 0.5 * Math.sin(time * 0.013) + 0.25 * Math.sin(time * 0.031 + 1.7);
        dirX = (float) Math.cos(angle);
        dirZ = (float) Math.sin(angle);

        // Gusts travel ~4 blocks/s along the wind, faster in storms.
        double gustSpeed = 4.0 * speed;
        gustAccX = (gustAccX + dirX * gustSpeed * dt) % 256.0;
        gustAccZ = (gustAccZ + dirZ * gustSpeed * dt) % 256.0;
        gustX = (float) gustAccX;
        gustZ = (float) gustAccZ;

        strength = c.wavingEnabled ? (c.wavingStrength / 100f) * (0.75f + 0.9f * smoothedWeather) : 0f;
    }
}
