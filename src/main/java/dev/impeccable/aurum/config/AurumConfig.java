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

    public int configVersion = 4;
    public Preset preset = Preset.LOW;

    // Sky
    public boolean skyEnabled = true;
    public int sunsetIntensity = 100;
    public int horizonWarmth = 100;
    public int rainDesaturation = 70;

    // Lighting and colour
    public boolean lightingEnabled = true;
    public int sunStrength = 100;
    public int ambientStrength = 100;
    public int blockLightWarmth = 40;
    public int aoBoost = 50;
    public boolean normalsEnabled = true;
    public int normalStrength = 50;
    public boolean labPbr = false;

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
    public boolean entityShadows = false;
    public boolean blobShadows = true;

    // Fog
    public boolean fogEnabled = true;
    public int fogStrength = 100;
    public boolean heightFog = true;
    public boolean edgeFade = true;

    // Water and wet ground
    public boolean waterEnabled = true;
    public boolean waterReflections = true;
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
    public CloudStyle cloudStyle = CloudStyle.BLOCKY;
    public boolean cloudsFullRes = false;
    public int cloudHeight = 24;
    public boolean cloudsPackShapes = false;
    public int cloudSpeed = 100;
    public int cloudCoverage = 50;
    public int cloudShading = 100;

    // POM (High tier)
    public boolean pomEnabled = false;
    public int pomDepth = 100;
    public int pomDistance = 12;

    // Colour grade
    public Tonemap tonemap = Tonemap.FILMIC;
    public int whiteBalance = 100;
    public int exposure = 100;
    public int saturation = 100;
    public int vibrance = 105;
    public int contrast = 100;

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
        shadowResolution = switch (shadowResolution) {
            case 1024, 1536, 2048, 3072 -> shadowResolution;
            default -> 1024;
        };
        shadowDistance = clamp(shadowDistance, 2, 8);
        pomDistance = clamp(pomDistance, 4, 16);
        cloudHeight = clamp(cloudHeight, 0, 160);
        cloudSpeed = clamp(cloudSpeed, 0, 300);
        return this;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
