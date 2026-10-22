package dev.impeccable.aurum.config;

/**
 * Look presets: tune Aurum's tones, colours and small effects toward the style of a popular shader pack. They
 * do not copy anything from those packs; the values come from measuring their public gallery screenshots
 * (docs/research/LOOK_PRESETS.md). Quality settings (shadows, resolution, reflections) are left alone, so a
 * look works on every quality preset. Every value stays editable afterwards.
 */
public enum LookPreset {
    /** Aurum's own golden-hour look (the defaults). */
    AURUM,
    /** Bliss-inspired: airy and soft, neutral light, pale sky, haze, soft clouds, low saturation. */
    AIRY,
    /** Photon-inspired: clean and clear, neutral daylight, crisp blue sky, very warm sunsets. */
    CLEAR,
    /** Complementary-inspired: warm all day, lavender-pink skies, warm shade, blocky clouds. */
    WARM,
    /** BSL-inspired: vivid and cool, deep blue sky, darker image, strong bloom and rays, deep orange sunsets. */
    VIVID,
    /** Set automatically when a look value no longer matches a preset. */
    CUSTOM;

    public void applyTo(AurumConfig c) {
        if (this == CUSTOM) return;
        AurumConfig d = new AurumConfig();
        // Start from Aurum's defaults so switching looks never stacks.
        c.exposure = d.exposure;
        c.saturation = d.saturation;
        c.vibrance = d.vibrance;
        c.contrast = d.contrast;
        c.temperature = d.temperature;
        c.shadeWarmth = d.shadeWarmth;
        c.skySaturation = d.skySaturation;
        c.skyLavender = d.skyLavender;
        c.sunsetIntensity = d.sunsetIntensity;
        c.horizonWarmth = d.horizonWarmth;
        c.fogStrength = d.fogStrength;
        c.bloomStrength = d.bloomStrength;
        c.godRayStrength = d.godRayStrength;
        c.cloudStyle = d.cloudStyle;
        c.cloudCoverage = d.cloudCoverage;
        c.cloudShading = d.cloudShading;
        c.aoBoost = d.aoBoost;
        switch (this) {
            case AIRY -> {
                // Measured: L 0.55-0.59, saturation 0.18-0.26, warmth ~0, near-white sky, heavy haze.
                c.exposure = 108;
                c.saturation = 78;
                c.vibrance = 100;
                c.contrast = 104;
                c.skySaturation = 62;
                c.sunsetIntensity = 80;
                c.horizonWarmth = 85;
                c.fogStrength = 170;
                c.bloomStrength = 140;
                c.godRayStrength = 130;
                c.cloudStyle = CloudStyle.SOFT;
                c.cloudCoverage = 66;
                c.cloudShading = 125;
                c.aoBoost = 65;
            }
            case CLEAR -> {
                // Measured: daylight warmth ~0, saturation 0.23-0.37, pale blue sky, sunsets warmth +0.49.
                c.exposure = 104;
                c.saturation = 96;
                c.contrast = 108;
                c.temperature = 3;
                c.skySaturation = 92;
                c.skyLavender = -22;
                c.sunsetIntensity = 175;
                c.horizonWarmth = 160;
                c.fogStrength = 75;
                c.bloomStrength = 90;
                c.cloudStyle = CloudStyle.SOFT;
                c.cloudCoverage = 45;
            }
            case WARM -> {
                // Measured: warmth +0.08 to +0.19 all day, lavender-pink sky, warm shade, saturation ~0.34.
                c.exposure = 106;
                c.saturation = 100;
                c.vibrance = 108;
                c.temperature = 16;
                c.shadeWarmth = 22;
                c.skyLavender = 30;
                c.sunsetIntensity = 115;
                c.cloudStyle = CloudStyle.BLOCKY;
                c.cloudCoverage = 55;
            }
            case VIVID -> {
                // Measured: L 0.37-0.48, cool (-0.03 to -0.07), saturated blue sky, sunsets +0.30, strong bloom.
                c.exposure = 92;
                c.saturation = 108;
                c.vibrance = 118;
                c.contrast = 96;
                c.temperature = -8;
                c.shadeWarmth = -12;
                c.skySaturation = 128;
                c.skyLavender = -14;
                c.sunsetIntensity = 130;
                c.horizonWarmth = 125;
                c.fogStrength = 90;
                c.bloomStrength = 150;
                c.godRayStrength = 150;
                c.cloudStyle = CloudStyle.BLOCKY;
            }
            default -> { }
        }
    }
}
