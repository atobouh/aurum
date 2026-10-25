package dev.impeccable.aurum.config;

import com.mojang.serialization.Codec;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The settings screen's pages. Each page builds vanilla {@link SimpleOption}s bound to {@link AurumConfig}
 * fields, so the screen gets vanilla sliders, toggles and tooltips for free. Moving any option switches the
 * preset to CUSTOM.
 */
public enum OptionCategory {
    SKY, LIGHTING, WIND, GLOW, SHADOWS, FOG, WATER, SKY_EXTRAS, POM, GRADE, DEBUG;

    public Text title() {
        return Text.translatable("aurum.category." + name().toLowerCase());
    }

    public SimpleOption<?>[] options() {
        AurumConfig c = AurumConfig.get();
        return switch (this) {
            case SKY -> new SimpleOption<?>[]{
                    bool("sky_enabled", c.skyEnabled, v -> c.skyEnabled = v),
                    percent("sky_clarity", 0, 100, () -> c.skyClarity, v -> c.skyClarity = v),
                    ranged("sky_temperature", -50, 50, "aurum.unit.plain", () -> c.skyTemperature, v -> c.skyTemperature = v),
                    choice("sun_shape", List.of(SunShape.values()), c.sunShape,
                            v -> Text.translatable("aurum.sun_shape." + v.name().toLowerCase()), v -> c.sunShape = v),
                    percent("sunset_intensity", 0, 200, () -> c.sunsetIntensity, v -> c.sunsetIntensity = v),
                    percent("horizon_warmth", 0, 200, () -> c.horizonWarmth, v -> c.horizonWarmth = v),
                    percent("rain_desaturation", 0, 100, () -> c.rainDesaturation, v -> c.rainDesaturation = v),
            };
            case LIGHTING -> new SimpleOption<?>[]{
                    bool("lighting_enabled", c.lightingEnabled, v -> c.lightingEnabled = v),
                    bool("voxel_lighting", c.voxelLightingEnabled, v -> c.voxelLightingEnabled = v),
                    percent("voxel_lighting_strength", 0, 100, () -> c.voxelLightingStrength, v -> c.voxelLightingStrength = v),
                    choice("voxel_detail", List.of(4, 8), c.voxelDetail,
                            v -> Text.literal(v + " / block"), v -> c.voxelDetail = v),
                    percent("sun_strength", 0, 200, () -> c.sunStrength, v -> c.sunStrength = v),
                    percent("ambient_strength", 0, 200, () -> c.ambientStrength, v -> c.ambientStrength = v),
                    percent("block_light_warmth", 0, 100, () -> c.blockLightWarmth, v -> c.blockLightWarmth = v),
                    percent("ao_boost", 0, 100, () -> c.aoBoost, v -> c.aoBoost = v),
                    percent("foliage_depth", 0, 100, () -> c.foliageDepth, v -> c.foliageDepth = v),
                    bool("normals_enabled", c.normalsEnabled, v -> c.normalsEnabled = v),
                    percent("normal_strength", 0, 200, () -> c.normalStrength, v -> c.normalStrength = v),
                    bool("labpbr", c.labPbr, v -> c.labPbr = v),
            };
            case WIND -> new SimpleOption<?>[]{
                    bool("waving_enabled", c.wavingEnabled, v -> c.wavingEnabled = v),
                    percent("waving_strength", 0, 200, () -> c.wavingStrength, v -> c.wavingStrength = v),
                    percent("waving_speed", 25, 200, () -> c.wavingSpeed, v -> c.wavingSpeed = v),
            };
            case GLOW -> new SimpleOption<?>[]{
                    bool("bounce_enabled", c.bounceEnabled, v -> c.bounceEnabled = v),
                    percent("bounce_strength", 0, 200, () -> c.bounceStrength, v -> c.bounceStrength = v),
                    bool("bloom_enabled", c.bloomEnabled, v -> c.bloomEnabled = v),
                    percent("bloom_strength", 0, 200, () -> c.bloomStrength, v -> c.bloomStrength = v),
                    bool("sheen_enabled", c.sheenEnabled, v -> c.sheenEnabled = v),
                    percent("sheen_strength", 0, 200, () -> c.sheenStrength, v -> c.sheenStrength = v),
            };
            case SHADOWS -> new SimpleOption<?>[]{
                    bool("shadows_enabled", c.shadowsEnabled, v -> c.shadowsEnabled = v),
                    choice("light_edge_style", List.of(LightEdgeStyle.values()), c.lightEdgeStyle,
                            v -> Text.translatable("aurum.light_edge_style." + v.name().toLowerCase()), v -> c.lightEdgeStyle = v),
                    choice("light_pixel_grid", List.of(4, 8, 16), c.lightPixelGrid,
                            v -> Text.literal(v + " / block"), v -> c.lightPixelGrid = v),
                    choice("shadow_resolution", List.of(1024, 1536, 2048, 3072), c.shadowResolution,
                            v -> Text.literal(v + " px"), v -> c.shadowResolution = v),
                    ranged("shadow_distance", 2, 8, "aurum.unit.chunks", () -> c.shadowDistance, v -> c.shadowDistance = v),
                    percent("shadow_softness", 0, 200, () -> c.shadowSoftness, v -> c.shadowSoftness = v),
                    bool("entity_shadows", c.entityShadows, v -> c.entityShadows = v),
                    bool("blob_shadows", c.blobShadows, v -> c.blobShadows = v),
            };
            case FOG -> new SimpleOption<?>[]{
                    bool("fog_enabled", c.fogEnabled, v -> c.fogEnabled = v),
                    percent("fog_strength", 0, 200, () -> c.fogStrength, v -> c.fogStrength = v),
                    bool("height_fog", c.heightFog, v -> c.heightFog = v),
                    percent("biome_haze", 0, 100, () -> c.biomeHaze, v -> c.biomeHaze = v),
                    percent("distance_haze", 0, 200, () -> c.distanceHaze, v -> c.distanceHaze = v),
                    bool("edge_fade", c.edgeFade, v -> c.edgeFade = v),
            };
            case WATER -> new SimpleOption<?>[]{
                    bool("water_enabled", c.waterEnabled, v -> c.waterEnabled = v),
                    bool("water_reflections", c.waterReflections, v -> c.waterReflections = v),
                    percent("wave_strength", 0, 200, () -> c.waveStrength, v -> c.waveStrength = v),
                    bool("vertex_waves", c.vertexWaves, v -> c.vertexWaves = v),
                    bool("foam", c.foam, v -> c.foam = v),
                    bool("puddles", c.puddles, v -> c.puddles = v),
                    percent("ground_reflections", 0, 100, () -> c.groundReflections, v -> c.groundReflections = v),
            };
            case SKY_EXTRAS -> new SimpleOption<?>[]{
                    bool("flare_enabled", c.flareEnabled, v -> c.flareEnabled = v),
                    percent("flare_strength", 0, 200, () -> c.flareStrength, v -> c.flareStrength = v),
                    bool("god_rays_enabled", c.godRaysEnabled, v -> c.godRaysEnabled = v),
                    percent("god_ray_strength", 0, 200, () -> c.godRayStrength, v -> c.godRayStrength = v),
                    bool("clouds_enabled", c.cloudsEnabled, v -> c.cloudsEnabled = v),
                    choice("cloud_style", List.of(CloudStyle.values()), c.cloudStyle,
                            v -> Text.translatable("aurum.cloud_style." + v.name().toLowerCase()), v -> c.cloudStyle = v),
                    bool("clouds_full_res", c.cloudsFullRes, v -> c.cloudsFullRes = v),
                    ranged("cloud_height", 0, 160, "aurum.unit.blocks_above", () -> c.cloudHeight, v -> c.cloudHeight = v),
                    percent("cloud_speed", 0, 300, () -> c.cloudSpeed, v -> c.cloudSpeed = v),
                    percent("cloud_motion_variation", 0, 100, () -> c.cloudMotionVariation, v -> c.cloudMotionVariation = v),
                    bool("cloud_shadows_enabled", c.cloudShadowsEnabled, v -> c.cloudShadowsEnabled = v),
                    percent("cloud_shadow_strength", 0, 100, () -> c.cloudShadowStrength, v -> c.cloudShadowStrength = v),
                    bool("clouds_pack_shapes", c.cloudsPackShapes, v -> c.cloudsPackShapes = v),
                    percent("cloud_coverage", 0, 100, () -> c.cloudCoverage, v -> c.cloudCoverage = v),
                    percent("cloud_shading", 0, 200, () -> c.cloudShading, v -> c.cloudShading = v),
            };
            case POM -> new SimpleOption<?>[]{
                    bool("pom_enabled", c.pomEnabled, v -> c.pomEnabled = v),
                    bool("pom_generated", c.pomGenerated, v -> c.pomGenerated = v),
                    percent("pom_depth", 0, 200, () -> c.pomDepth, v -> c.pomDepth = v),
                    ranged("pom_distance", 8, 48, "aurum.unit.blocks", () -> c.pomDistance, v -> c.pomDistance = v),
            };
            case GRADE -> new SimpleOption<?>[]{
                    bool("taa", c.taa, v -> c.taa = v),
                    percent("taa_sharpen", 0, 100, () -> c.taaSharpen, v -> c.taaSharpen = v),
                    bool("sharpness_enabled", c.sharpnessEnabled, v -> c.sharpnessEnabled = v),
                    percent("sharpness", 0, 100, () -> c.sharpness, v -> c.sharpness = v),
                    choice("tonemap", List.of(Tonemap.values()), c.tonemap,
                            v -> Text.translatable("aurum.tonemap." + v.name().toLowerCase()), v -> c.tonemap = v),
                    percent("white_balance", 0, 100, () -> c.whiteBalance, v -> c.whiteBalance = v),
                    percent("exposure", 50, 200, () -> c.exposure, v -> c.exposure = v),
                    percent("saturation", 0, 200, () -> c.saturation, v -> c.saturation = v),
                    percent("vibrance", 0, 200, () -> c.vibrance, v -> c.vibrance = v),
                    percent("contrast", 50, 150, () -> c.contrast, v -> c.contrast = v),
                    ranged("temperature", -50, 50, "aurum.unit.plain", () -> c.temperature, v -> c.temperature = v),
                    ranged("shade_warmth", -50, 50, "aurum.unit.plain", () -> c.shadeWarmth, v -> c.shadeWarmth = v),
                    percent("sky_saturation", 50, 160, () -> c.skySaturation, v -> c.skySaturation = v),
                    ranged("sky_lavender", -50, 50, "aurum.unit.plain", () -> c.skyLavender, v -> c.skyLavender = v),
            };
            case DEBUG -> new SimpleOption<?>[]{
                    bool("show_overlay", c.showOverlay, v -> c.showOverlay = v),
            };
        };
    }

    /** Options that belong to the look presets. */
    private static final java.util.Set<String> LOOK_IDS = java.util.Set.of("exposure", "saturation", "vibrance",
            "contrast", "temperature", "shade_warmth", "sky_saturation", "sky_lavender", "sunset_intensity",
            "horizon_warmth", "fog_strength", "bloom_strength", "god_ray_strength", "cloud_style", "cloud_coverage",
            "cloud_shading", "ao_boost");

    private static String key(String id) {
        return "aurum.option." + id;
    }

    private static <T> SimpleOption.TooltipFactory<T> tooltip(String id) {
        return SimpleOption.constantTooltip(Text.translatable(key(id) + ".tooltip"));
    }

    /** Every edit goes through here so the preset flips to CUSTOM. Debug toggles do not count as a look change. */
    private static <T> Consumer<T> edit(String id, Consumer<T> setter) {
        return v -> {
            setter.accept(v);
            if (!id.equals("show_overlay")) {
                AurumConfig.get().preset = Preset.CUSTOM;
                if (LOOK_IDS.contains(id)) AurumConfig.get().look = LookPreset.CUSTOM;
            }
        };
    }

    private static SimpleOption<Boolean> bool(String id, boolean value, Consumer<Boolean> setter) {
        return SimpleOption.ofBoolean(key(id), tooltip(id), value, edit(id, setter));
    }

    private static SimpleOption<Integer> percent(String id, int min, int max, IntSupplier get, IntConsumer set) {
        return new SimpleOption<>(key(id), tooltip(id),
                (text, v) -> GameOptions.getGenericValueText(text, Text.literal(v + "%")),
                new SimpleOption.ValidatingIntSliderCallbacks(min, max),
                get.getAsInt(), edit(id, set::accept));
    }

    private static SimpleOption<Integer> ranged(String id, int min, int max, String unitKey, IntSupplier get, IntConsumer set) {
        return new SimpleOption<>(key(id), tooltip(id),
                (text, v) -> GameOptions.getGenericValueText(text, Text.translatable(unitKey, v)),
                new SimpleOption.ValidatingIntSliderCallbacks(min, max),
                get.getAsInt(), edit(id, set::accept));
    }

    private static <T> SimpleOption<T> choice(String id, List<T> values, T value,
                                             java.util.function.Function<T, Text> label, Consumer<T> setter) {
        Codec<T> codec = Codec.INT.xmap(values::get, values::indexOf);
        return new SimpleOption<>(key(id), tooltip(id),
                // Cycling buttons add their caption; returning it here too duplicates and clips the label.
                (text, v) -> label.apply(v),
                new SimpleOption.PotentialValuesBasedCallbacks<>(values, codec),
                value, edit(id, setter));
    }
}
