package dev.impeccable.aurum.director;

import dev.impeccable.aurum.config.AurumConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.Biome;

/**
 * Decides how foggy it is, on the CPU, once per frame. Density is near zero on a clear day and rises only at
 * dawn and dusk, in rain, in caves and in swampy biomes. The output is smoothed over seconds so fog never pops.
 */
public final class FogDirector {
    /** Smoothed outputs, sent to the post pass. */
    public static float distanceDensity;
    public static float heightDensity;
    /** 0 = open air, 1 = deep in a cave (fog colour goes dark instead of sky-coloured). */
    public static float cave;
    /** 0 = open sky above, 1 = closed in (cave, room, under an overhang) at any height. Drives cave light beams. */
    public static float enclosed;

    private static float tDistance, tHeight, tCave, tEnclosed;
    private static long lastNanos;
    private static int biomeCheckTicker;
    private static float swamp;

    private FogDirector() {}

    public static void update(ClientWorld world, Vec3d camera, float tickDelta) {
        AurumConfig c = AurumConfig.get();
        float sunY = SunState.elevation();
        float rain = world.getRainGradient(tickDelta);
        float thunder = world.getThunderGradient(tickDelta);

        // Golden-hour haze peaks a little after sunset / before sunrise.
        float golden = bump(sunY, -0.10f, 0.05f, 0.25f);

        BlockPos eye = BlockPos.ofFloored(camera);
        int skyLight = world.getLightLevel(LightType.SKY, eye);
        float underground = clamp01((10f - skyLight) / 10f) * clamp01((70f - (float) camera.y) / 20f);

        if (--biomeCheckTicker <= 0) {
            biomeCheckTicker = 20;
            RegistryEntry<Biome> biome = world.getBiome(eye);
            swamp = biome.getKey().map(k -> k.getValue().getPath().contains("swamp")).orElse(false) ? 1f : 0f;
        }

        float strength = c.fogEnabled ? c.fogStrength / 100f : 0f;
        tDistance = strength * (0.00025f + 0.0010f * golden + 0.006f * rain + 0.006f * thunder
                + 0.004f * swamp + 0.012f * underground);
        tHeight = c.fogEnabled && c.heightFog ? strength * (0.006f * golden + 0.02f * rain + 0.015f * swamp) * (1f - underground) : 0f;
        tCave = underground;
        tEnclosed = clamp01((12f - skyLight) / 8f);

        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 10f : Math.min((now - lastNanos) / 1e9f, 0.25f);
        lastNanos = now;
        float k = 1f - (float) Math.exp(-dt / 3.0f); // ~3 s time constant
        distanceDensity += (tDistance - distanceDensity) * k;
        heightDensity += (tHeight - heightDensity) * k;
        cave += (tCave - cave) * k;
        enclosed += (tEnclosed - enclosed) * k;
    }

    /** 0 outside [lo, hi], 1 at peak, smooth in between. */
    private static float bump(float x, float lo, float peak, float hi) {
        if (x <= lo || x >= hi) return 0f;
        float t = x < peak ? (x - lo) / (peak - lo) : (hi - x) / (hi - peak);
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }
}
