package dev.impeccable.aurum.debug;

import com.mojang.brigadier.arguments.BoolArgumentType;
import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.render.SkyExtras;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

/** Reproducible screenshot controls, registered exclusively in Fabric's development environment. */
public final class VisualLab {
    private VisualLab() {}
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                literal("aurumdev")
                        .then(literal("reload").executes(context -> { AurumConfig.load(); return 1; }))
                        .then(literal("freezeclouds").then(argument("frozen", BoolArgumentType.bool())
                                .executes(context -> { SkyExtras.freezeCloudMotion = BoolArgumentType.getBool(context, "frozen"); return 1; })))));
    }
}
