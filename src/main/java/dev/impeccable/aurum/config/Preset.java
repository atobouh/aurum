package dev.impeccable.aurum.config;

/**
 * Quality presets. CUSTOM is set automatically as soon as the user moves any single option.
 */
public enum Preset {
    LOW, MEDIUM, HIGH, CUSTOM;

    /** Writes this preset's values into the config. CUSTOM leaves everything as is. */
    public void applyTo(AurumConfig c) {
        if (this == CUSTOM) return;
        boolean medium = this != LOW;
        boolean high = this == HIGH;

        c.normalStrength = medium ? 100 : 50;
        // LabPBR data is used whenever the resource pack has it (cheap); 3D blocks from Medium up.
        c.labPbr = true;

        // Bloom is cheap (1/4 resolution and below) and is most of the golden-hour gap, so every tier has it.
        c.bloomEnabled = true;

        c.shadowResolution = high ? 2048 : medium ? 1536 : 1024;
        c.shadowDistance = high ? 6 : medium ? 5 : 4;
        c.entityShadows = medium;

        c.vertexWaves = medium;
        // Terrain reflections are part of the one water look, on every preset.
        c.waterReflections = true;

        // 3D blocks on every preset (the user's favourite PBR effect); Low keeps them closer.
        c.pomEnabled = true;
        c.pomDistance = high ? 32 : medium ? 24 : 16;

        c.cloudsFullRes = high;
    }
}
