package dev.impeccable.aurum.compat;

import dev.impeccable.aurum.AurumMod;
import dev.impeccable.aurum.ChatNotices;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;

/**
 * Startup checks. fabric.mod.json already refuses to launch next to known shader mods; this is the safety net
 * for anything that slips through, plus the Sodium version check.
 */
public final class Compat {
    /** The Sodium release whose terrain shaders Aurum's hooks are written against. */
    public static final String TESTED_SODIUM = "0.5.13";

    private static final String[] CONFLICTS = {"iris", "oculus", "luxium", "canvas", "optifabric"};

    private static boolean sodiumMatches;

    private Compat() {}

    public static void check() {
        Optional<ModContainer> sodium = FabricLoader.getInstance().getModContainer("sodium");
        String version = sodium.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("missing");
        sodiumMatches = version.startsWith(TESTED_SODIUM);
        if (sodiumMatches) {
            AurumMod.LOGGER.info("Sodium {} detected (tested version)", version);
        } else {
            AurumMod.LOGGER.warn("Sodium {} detected; Aurum is written against {}", version, TESTED_SODIUM);
            ChatNotices.queue(prefix().append(Text.literal("Built for Sodium " + TESTED_SODIUM + ", found " + version
                    + ". Terrain effects stay off on untested Sodium versions.").formatted(Formatting.YELLOW)));
        }

        if (FabricLoader.getInstance().isModLoaded("windblown")) {
            AurumMod.LOGGER.warn("Windblown is loaded; it edits the same Sodium terrain shader as Aurum");
            ChatNotices.queue(prefix().append(Text.literal("Windblown edits the same terrain shader as Aurum, so Aurum's"
                    + " terrain effects stay off. Aurum has its own waving plants: remove Windblown.").formatted(Formatting.YELLOW)));
        }

        for (String id : CONFLICTS) {
            if (FabricLoader.getInstance().isModLoaded(id)) {
                AurumMod.LOGGER.error("Conflicting rendering mod loaded: {}", id);
                ChatNotices.queue(prefix().append(Text.literal("'" + id + "' replaces the same render pipeline as Aurum."
                        + " Remove one of them.").formatted(Formatting.RED)));
            }
        }
    }

    /** False when Sodium is not the version our terrain shader hooks target. Checked before injecting. */
    public static boolean sodiumMatches() {
        return sodiumMatches;
    }

    private static net.minecraft.text.MutableText prefix() {
        return Text.literal("[Aurum] ").formatted(Formatting.GOLD);
    }
}
