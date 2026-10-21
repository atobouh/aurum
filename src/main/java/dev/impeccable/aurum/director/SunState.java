package dev.impeccable.aurum.director;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

/**
 * Sun angle read from the client world every frame, so any day length (Custom Time Cycle included) is followed
 * automatically. Small steps are smoothed over a few frames so a mod that advances time in uneven ticks cannot
 * make the sun jitter; large jumps (/time set, sleeping) snap instantly.
 */
public final class SunState {
    /** Above this many radians in one frame the change is a jump, not drift. */
    private static final float SNAP_RADIANS = 0.2f;
    /** Smoothing rate in 1/seconds. Higher follows faster. */
    private static final float RATE = 12.0f;

    private static boolean valid;
    private static float angle;
    private static long lastNanos;

    private SunState() {}

    public static void update(ClientWorld world, float tickDelta) {
        if (world == null) {
            valid = false;
            return;
        }
        // getSkyAngle is 0 at noon and 0.5 at midnight, in turns
        float target = world.getSkyAngle(tickDelta) * MathHelper.TAU;
        long now = System.nanoTime();
        if (!valid) {
            angle = target;
            valid = true;
        } else {
            float delta = MathHelper.wrapDegrees((target - angle) * MathHelper.DEGREES_PER_RADIAN) * MathHelper.RADIANS_PER_DEGREE;
            if (Math.abs(delta) > SNAP_RADIANS) {
                angle = target;
            } else {
                float dt = Math.min((now - lastNanos) / 1.0e9f, 0.1f);
                angle += delta * (1.0f - (float) Math.exp(-RATE * dt));
            }
        }
        lastNanos = now;
    }

    /** Smoothed sky angle in radians: 0 at noon, PI at midnight. */
    public static float angle() {
        return angle;
    }

    /** Sun height above the horizon, -1 (nadir) to 1 (zenith). */
    public static float elevation() {
        return MathHelper.cos(angle);
    }

    public static float elevationDegrees() {
        return valid ? (float) Math.toDegrees(Math.asin(MathHelper.clamp(elevation(), -1f, 1f))) : 0f;
    }
}
