package dev.impeccable.aurum.render;

import dev.impeccable.aurum.AurumMod;
import dev.impeccable.aurum.ChatNotices;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Global on/off state for Aurum's rendering. Anything that fails at startup (a shader that will not compile on
 * this driver, an unexpected Sodium version) calls {@link #disable} and the game falls back to vanilla visuals.
 */
public final class Pipeline {
    public enum State { STARTING, ACTIVE, DISABLED }

    private static State state = State.STARTING;
    private static String disabledReason = "";

    private Pipeline() {}

    public static void markActive() {
        if (state == State.STARTING) state = State.ACTIVE;
    }

    public static void disable(String reason) {
        if (state == State.DISABLED) return;
        state = State.DISABLED;
        disabledReason = reason;
        AurumMod.LOGGER.error("Aurum rendering disabled: {}", reason);
        ChatNotices.queue(Text.literal("[Aurum] ").formatted(Formatting.GOLD)
                .append(Text.literal("Rendering is off, vanilla visuals are used. " + reason + " See latest.log.")
                        .formatted(Formatting.RED)));
    }

    /** True when Aurum renders this frame: started fine and switched on by the player (F6 or the settings). */
    public static boolean isActive() {
        return state == State.ACTIVE && dev.impeccable.aurum.config.AurumConfig.get().enabled;
    }

    public static State state() {
        return state;
    }

    public static Text statusLine() {
        return switch (state) {
            case STARTING -> Text.translatable("aurum.status.starting");
            case ACTIVE -> Text.translatable("aurum.status.active", ShaderManager.programCount());
            case DISABLED -> Text.translatable("aurum.status.disabled", disabledReason);
        };
    }
}
