package dev.impeccable.aurum.debug;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.text.Text;
import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.render.SkyExtras;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

/** Reproducible screenshot controls, registered exclusively in Fabric's development environment. */
public final class VisualLab {
    private VisualLab() {}
    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LightingDebug.reset());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            var lighting = literal("lighting");
            for (var view : LightingDebug.View.values()) {
                lighting.then(literal(view.name().toLowerCase(java.util.Locale.ROOT)).executes(context -> {
                    LightingDebug.select(view);
                    context.getSource().sendFeedback(Text.literal(view == LightingDebug.View.OFF
                            ? "Aurum lighting view: OFF; normal rendering restored."
                            : "Aurum lighting view: " + view
                            + "; terrain only; water purple; core objects unchanged; post effects bypassed."));
                    return 1;
                }));
            }
            lighting.then(literal("snapshot").executes(context -> {
                try {
                    var path = LightingDebug.snapshot();
                    context.getSource().sendFeedback(Text.literal("Lighting snapshot saved: debug/aurum-lighting/" + path.getFileName()));
                    return 1;
                } catch (java.io.IOException e) {
                    dev.impeccable.aurum.AurumMod.LOGGER.error("Cannot save lighting snapshot", e);
                    context.getSource().sendError(Text.literal("Could not save lighting snapshot; see latest.log."));
                    return 0;
                }
            }));
            dispatcher.register(
                literal("aurumdev")
                        .then(lighting)
                        .then(literal("reload").executes(context -> { AurumConfig.load(); return 1; }))
                        .then(literal("freezeclouds").then(argument("frozen", BoolArgumentType.bool())
                                .executes(context -> { SkyExtras.freezeCloudMotion = BoolArgumentType.getBool(context, "frozen"); return 1; }))));
        });
    }
}
