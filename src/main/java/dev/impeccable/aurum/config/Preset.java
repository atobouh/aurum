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
        c.labPbr = medium;

        c.bloomEnabled = medium;

        c.shadowResolution = high ? 2048 : medium ? 1536 : 1024;
        c.shadowDistance = high ? 6 : medium ? 5 : 4;
        c.entityShadows = medium;

        c.vertexWaves = medium;

        c.pomEnabled = high;
    }
}
