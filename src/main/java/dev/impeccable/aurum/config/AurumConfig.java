package dev.impeccable.aurum.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.impeccable.aurum.AurumMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Every user-facing setting. Strengths are integer percentages (100 = default look) so sliders and the
 * JSON file stay readable. Fields for effects that arrive in later phases already exist so the file format
 * stays stable.
 */
public final class AurumConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("aurum.json");

    private static AurumConfig instance = new AurumConfig();

    public int configVersion = 15;
    public Preset preset = Preset.LOW;
    /** Master switch: off shows plain vanilla (Sodium) visuals, with no Aurum cost. */
    public boolean enabled = true;

    // Sky
    public boolean skyEnabled = true;
    public SunShape sunShape = SunShape.ROUND;
    public int sunsetIntensity = 100;
    public int horizonWarmth = 100;
    public int rainDesaturation = 70;
    /** Display sky only: does not retune the sun/moon or sky irradiance on blocks. */
    public int skyClarity = 0;
    public int skyTemperature = 0;

    // Lighting and colour
    public boolean lightingEnabled = true;
    public int sunStrength = 100;
    public int ambientStrength = 100;
    public int blockLightWarmth = 40;
    public int aoBoost = 50;
    public int foliageDepth = 60;
    public boolean normalsEnabled = true;
    public int normalStrength = 50;
    public boolean labPbr = true;
    public boolean voxelLightingEnabled = true;
    public int voxelLightingStrength = 100;
    /** Samples across each block face; 4 is the integrated-GPU default. */
    public int voxelDetail = 4;

    // Waving plants
    public boolean wavingEnabled = true;
    public int wavingStrength = 100;
    public int wavingSpeed = 100;

    // Glow
    public boolean bounceEnabled = true;
    public int bounceStrength = 100;
    public boolean bloomEnabled = true;
    public int bloomStrength = 100;
    public boolean sheenEnabled = true;
    public int sheenStrength = 100;

    // Shadows
    public boolean shadowsEnabled = true;
    public int shadowResolution = 1024;
    public int shadowDistance = 4;
    public int shadowSoftness = 100;
    public LightEdgeStyle lightEdgeStyle = LightEdgeStyle.NATURAL;
    public int lightPixelGrid = 16;
    public boolean entityShadows = false;
    public boolean blobShadows = true;

    // Fog
    public boolean fogEnabled = true;
    public int fogStrength = 100;
    public int biomeHaze = 40;
    /** Aerial perspective: distant land fades into the pale horizon air, as in the Nostalgia/Photon references. */
    public int distanceHaze = 100;
    public boolean heightFog = true;
    public boolean edgeFade = true;

    // Water and wet ground
    public boolean waterEnabled = true;
    public WaterStyle waterStyle = WaterStyle.CALM_LAKE;
    /** 0 keeps Aurum's original surface; 100 applies the selected lake treatment fully. */
    public int waterStyleBlend = 100;
    public boolean waterReflections = false;
    public int waveStrength = 100;
    public boolean vertexWaves = false;
    public boolean foam = true;
    public boolean puddles = true;
    public int groundReflections = 50;

    // Sky extras
    public boolean flareEnabled = true;
    public int flareStrength = 100;
    public boolean godRaysEnabled = true;
    public int godRayStrength = 100;
    public boolean cloudsEnabled = true;
    public CloudStyle cloudStyle = CloudStyle.SOFT;
    public boolean cloudsFullRes = false;
    public int cloudHeight = 24;
    public boolean cloudsPackShapes = false;
    public int cloudSpeed = 100;
    public int cloudMotionVariation = 65;
    public boolean cloudShadowsEnabled = true;
    public int cloudShadowStrength = 40;
    public int cloudCoverage = 50;
    public int cloudShading = 100;

    // POM (High tier)
    public boolean pomEnabled = true;
    /** 3D relief from the texture's own pixels where the resource pack has no height map. */
    public boolean pomGenerated = true;
    public int pomDepth = 100;
    public int pomDistance = 16;

    // Colour grade
    public Tonemap tonemap = Tonemap.PHOTON;
    public int whiteBalance = 100;
    public int exposure = 110;
    public int saturation = 100;
    public int vibrance = 100;
    public int contrast = 100;
    public LookPreset look = LookPreset.AURUM;
    /** -50 cool .. +50 warm. */
    public int temperature = 12;
    /** -50 cool shade .. +50 warm shade. */
    public int shadeWarmth = 0;
    public int skySaturation = 70;
    /** -50 teal .. +50 lavender. */
    public int skyLavender = 0;
    /** Temporal anti-aliasing: smooth edges and stable leaves (the references' soft, solid look). */
    public boolean taa = true;
    /** Sharpening applied in the TAA resolve so the image stays crisp. */
    public int taaSharpen = 50;
    public boolean sharpnessEnabled = true;
    public int sharpness = 65;

    // Debug
    public boolean showOverlay = false;

    public static AurumConfig get() {
        return instance;
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            instance = new AurumConfig();
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            AurumConfig loaded = GSON.fromJson(reader, AurumConfig.class);
            int version = loaded != null ? loaded.configVersion : 0;
            instance = loaded != null ? loaded.sanitized() : new AurumConfig();
            if (version != instance.configVersion) {
                reader.close();
                save();
            }
        } catch (IOException | JsonParseException e) {
            AurumMod.LOGGER.error("Could not read {}, using defaults", PATH, e);
            instance = new AurumConfig();
        }
    }

    public static void save() {
        try {
            Path tmp = PATH.resolveSibling("aurum.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(instance, writer);
            }
            Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            AurumMod.LOGGER.error("Could not write {}", PATH, e);
        }
    }

    /** Clamps values a hand-edited file could break, and fills enums Gson left null. */
    private AurumConfig sanitized() {
        if (preset == null) preset = Preset.LOW;
        if (tonemap == null) tonemap = Tonemap.FILMIC;
        if (cloudStyle == null) cloudStyle = CloudStyle.BLOCKY;
        if (sunShape == null) sunShape = SunShape.ROUND;
        if (look == null) look = LookPreset.AURUM;
        if (waterStyle == null) waterStyle = WaterStyle.CALM_LAKE;
        if (configVersion < 2) {
            // 0.1.0 shipped a grade that crushed blue (AgX + extra saturation). Move everyone to the new look.
            AurumConfig d = new AurumConfig();
            tonemap = d.tonemap;
            whiteBalance = d.whiteBalance;
            saturation = d.saturation;
            vibrance = d.vibrance;
            contrast = d.contrast;
            aoBoost = d.aoBoost;
            configVersion = 2;
        }
        if (configVersion < 3) {
            // 0.3.0: bloom is on for every preset (it was a placeholder before).
            bloomEnabled = true;
            configVersion = 3;
        }
        if (configVersion < 4) {
            // 0.3.2: clouds sit 24 blocks higher by default (user: vanilla's 192 looked too low; much higher made
            // them small and sparse next to the references).
            if (cloudHeight == 0) cloudHeight = 24;
            configVersion = 4;
        }
        if (configVersion < 5) {
            // 0.3.4: references are less saturated than our daylight (0.19-0.30 vs 0.39-0.46).
            if (saturation == 100) saturation = 92;
            configVersion = 5;
        }
        if (configVersion < 6) {
            // 0.4.0: water. Terrain reflections follow the preset (sky-only on Low).
            if (preset == Preset.LOW) waterReflections = false;
            configVersion = 6;
        }
        if (configVersion < 7) {
            // 0.5.0: LabPBR on for every preset; 3D blocks (parallax) on Medium and High.
            labPbr = true;
            pomEnabled = true;
            pomDistance = preset == Preset.LOW ? 16 : 24;
            configVersion = 7;
        }
        if (configVersion < 8) {
            // Earlier fresh LOW configs retained the field initialiser (POM off, 24-block distance) even though
            // applying the LOW preset enables nearby relief. Upgrade only that old default signature; edited
            // distances and other presets keep their chosen state.
            if (preset == Preset.LOW && !pomEnabled && pomGenerated && pomDepth == 100 && pomDistance == 24) {
                pomEnabled = true;
                pomDistance = 16;
            }
            configVersion = 8;
        }
        if (configVersion < 9) {
            // Upgrade only the untouched Aurum grade. Keep custom and style-preset colours exactly as chosen.
            if (look == LookPreset.AURUM && saturation == 92 && vibrance == 105 && contrast == 100) {
                saturation = 100;
                vibrance = 108;
                contrast = 105;
            }
            configVersion = 9;
        }
        if (configVersion < 10) {
            // New fields retain their initialisers when absent. Existing lighting and colour edits survive.
            configVersion = 10;
        }
        if (configVersion < 11) {
            // 0.7.0 (merged water): one water look with terrain reflections on every preset.
            waterReflections = true;
            configVersion = 11;
        }
        if (configVersion < 12) {
            // 0.7.1 post-processing pass (docs/research/POST_PROCESSING.md): calmer colour, neutral-warm light, a
            // pale horizon sky and distance haze, measured against the Nostalgia/Photon references. Only an
            // untouched Aurum look moves; edited colours and the other looks keep their values.
            if (look == LookPreset.AURUM && exposure == 100 && saturation == 100 && vibrance == 108
                    && contrast == 105 && temperature == 0 && skySaturation == 100) {
                exposure = 110;
                saturation = 88;
                vibrance = 100;
                contrast = 110;
                temperature = 12;
                skySaturation = 70;
                if (skyClarity == 75 && skyTemperature == -10) {
                    skyClarity = 0;
                    skyTemperature = 0;
                }
            }
            configVersion = 12;
        }
        if (configVersion < 13) {
            // 0.7.4: at 65 % cloud shadows left most of the land dark and dull under soft clouds (user screenshots).
            if (cloudShadowStrength == 65) cloudShadowStrength = 40;
            configVersion = 13;
        }
        if (configVersion < 14) {
            // 0.8.8: Photon's tone curve, neutral grade on top (the old desaturating grade washed the image out).
            if (tonemap == Tonemap.FILMIC) tonemap = Tonemap.PHOTON;
            if (look == LookPreset.AURUM) {
                if (saturation == 88) saturation = 100;
                if (contrast == 110) contrast = 100;
            }
            configVersion = 14;
        }
        if (configVersion < 15) {
            // 0.9.0: Photon-style soft cumulus is the default cloud look (user: "skip the blocky, go full Photon").
            cloudStyle = CloudStyle.SOFT;
            configVersion = 15;
        }
        skyClarity = clamp(skyClarity, 0, 100);
        skyTemperature = clamp(skyTemperature, -50, 50);
        waterStyleBlend = clamp(waterStyleBlend, 0, 100);
        sharpness = clamp(sharpness, 0, 100);
        taaSharpen = clamp(taaSharpen, 0, 100);
        voxelLightingStrength = clamp(voxelLightingStrength, 0, 100);
        voxelDetail = voxelDetail == 8 ? 8 : 4;
        if (lightEdgeStyle == null) lightEdgeStyle = LightEdgeStyle.NATURAL;
        lightPixelGrid = lightPixelGrid == 4 || lightPixelGrid == 8 ? lightPixelGrid : 16;
        foliageDepth = clamp(foliageDepth, 0, 100);
        biomeHaze = clamp(biomeHaze, 0, 100);
        distanceHaze = clamp(distanceHaze, 0, 200);
        shadowResolution = switch (shadowResolution) {
            case 1024, 1536, 2048, 3072 -> shadowResolution;
            default -> 1024;
        };
        shadowDistance = clamp(shadowDistance, 2, 8);
        pomDistance = clamp(pomDistance, 8, 48);
        cloudHeight = clamp(cloudHeight, 0, 160);
        cloudSpeed = clamp(cloudSpeed, 0, 300);
        cloudMotionVariation = clamp(cloudMotionVariation, 0, 100);
        cloudShadowStrength = clamp(cloudShadowStrength, 0, 100);
        return this;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
