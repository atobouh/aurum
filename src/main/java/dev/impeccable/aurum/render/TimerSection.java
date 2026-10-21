package dev.impeccable.aurum.render;

/**
 * Everything the overlay times, in display order. Vanilla stages are timed so Aurum's own passes have a
 * baseline to be compared against; Aurum passes are added here as each phase lands.
 */
public enum TimerSection {
    FRAME("Frame (GPU)", 0),
    WORLD("World", 1),
    SKY("Sky", 2),
    TERRAIN_SOLID("Terrain (solid+cutout)", 2),
    TERRAIN_TRANSLUCENT("Translucent", 2),
    CLOUDS("Clouds", 2),
    WEATHER("Weather", 2),
    HAND("Hand", 1),
    HUD("HUD", 1);

    public static final TimerSection[] VALUES = values();

    public final String label;
    public final int indent;

    TimerSection(String label, int indent) {
        this.label = label;
        this.indent = indent;
    }
}
