package dev.impeccable.aurum;

import dev.impeccable.aurum.compat.Compat;
import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.config.AurumSettingsScreen;
import dev.impeccable.aurum.render.GlInfo;
import dev.impeccable.aurum.render.GpuTimers;
import dev.impeccable.aurum.render.Pipeline;
import dev.impeccable.aurum.render.ShaderManager;
import dev.impeccable.aurum.render.TimerOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AurumMod implements ClientModInitializer {
    public static final String MOD_ID = "aurum";
    public static final Logger LOGGER = LoggerFactory.getLogger("Aurum");
    public static final String VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");

    private static KeyBinding settingsKey;
    private static KeyBinding overlayKey;

    @Override
    public void onInitializeClient() {
        AurumConfig.load();
        Compat.check();

        settingsKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.aurum.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, "key.categories.aurum"));
        overlayKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.aurum.overlay", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.aurum"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (settingsKey.wasPressed()) {
                client.setScreen(new AurumSettingsScreen(client.currentScreen));
            }
            while (overlayKey.wasPressed()) {
                AurumConfig.get().showOverlay = !AurumConfig.get().showOverlay;
                AurumConfig.save();
            }
        });

        // GL work needs the render thread and a live context, which exist once the client has started.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            GlInfo.capture();
            GpuTimers.init();
            if (ShaderManager.compileAll()) {
                Pipeline.markActive();
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> ChatNotices.deliver(client)));
        HudRenderCallback.EVENT.register(TimerOverlay::render);
    }
}
