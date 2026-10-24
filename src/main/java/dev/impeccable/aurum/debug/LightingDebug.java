package dev.impeccable.aurum.debug;

import com.google.gson.GsonBuilder;
import dev.impeccable.aurum.AurumMod;
import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.director.Atmosphere;
import dev.impeccable.aurum.director.FogDirector;
import dev.impeccable.aurum.render.VoxelLighting;
import dev.impeccable.aurum.render.terrain.TerrainUniforms;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Development-only isolation, never saved to player settings and never enabled in a release client. */
public final class LightingDebug {
    public enum View { OFF, DIRECT, SKY, BLOCK, FALLBACK, VOXEL, COVERAGE, NORMAL, AO, PATH }
    private static View selected = View.OFF;
    private LightingDebug() {}

    public static int mode() {
        return FabricLoader.getInstance().isDevelopmentEnvironment() && TerrainUniforms.mode == 1
                ? selected.ordinal() : 0;
    }

    public static void select(View view) { selected = view; }
    public static void reset() { selected = View.OFF; }

    /** Immutable-by-serialization snapshot at command time; no GL readback and no per-frame allocation. */
    public static Path snapshot() throws IOException {
        MinecraftClient client = MinecraftClient.getInstance();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("capturedUtc", Instant.now().toString());
        report.put("aurum", AurumMod.VERSION);
        report.put("requestedView", selected.name().toLowerCase(Locale.ROOT));
        report.put("activeView", View.values()[mode()].name().toLowerCase(Locale.ROOT));
        report.put("terrainMode", TerrainUniforms.mode);
        report.put("scope", "Terrain contribution diagnostic only. Water is purple; entities/block entities/hand retain core shading. Post effects bypassed while active.");
        if (client.world != null) {
            var camera = client.gameRenderer.getCamera();
            var p = camera.getPos();
            BlockPos eye = BlockPos.ofFloored(p);
            report.put("dimension", client.world.getRegistryKey().getValue().toString());
            report.put("worldTime", client.world.getTimeOfDay());
            report.put("camera", Map.of("x", p.x, "y", p.y, "z", p.z, "yaw", camera.getYaw(), "pitch", camera.getPitch()));
            report.put("eyeLight", Map.of("sky", client.world.getLightLevel(LightType.SKY, eye),
                    "block", client.world.getLightLevel(LightType.BLOCK, eye)));
            report.put("biome", client.world.getBiome(eye).getKey().map(k -> k.getValue().toString()).orElse("unregistered"));
            if (client.crosshairTarget instanceof BlockHitResult hit) {
                var state = client.world.getBlockState(hit.getBlockPos());
                report.put("target", Map.of("state", state.toString(), "face", hit.getSide().asString(),
                        "position", hit.getBlockPos().toShortString(), "emission", state.getLuminance(),
                        "blockEntity", state.hasBlockEntity(), "blockClass", state.getBlock().getClass().getName()));
            }
        }
        report.put("lighting", Map.of("direct", Atmosphere.lightColor.clone(), "sky", Atmosphere.skyAmbient.clone(),
                "block", Atmosphere.blockLight.clone(), "exposure", Atmosphere.exposure, "minimum", Atmosphere.minLight));
        report.put("fog", Map.of("density", FogDirector.distanceDensity, "heightDensity", FogDirector.heightDensity,
                "cave", FogDirector.cave, "enclosed", FogDirector.enclosed, "clearZone", FogDirector.clearZone));
        report.put("voxel", VoxelLighting.debugState());
        report.put("config", AurumConfig.get());
        report.put("mods", FabricLoader.getInstance().getAllMods().stream()
                .map(m -> Map.of("id", m.getMetadata().getId(), "version", m.getMetadata().getVersion().getFriendlyString()))
                .sorted(java.util.Comparator.comparing(m -> m.get("id"))).toList());
        Path dir = FabricLoader.getInstance().getGameDir().resolve("debug/aurum-lighting");
        Files.createDirectories(dir);
        Path output = Files.createTempFile(dir, "lighting-", ".json");
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        return output;
    }
}
