package dev.impeccable.aurum;

import dev.impeccable.aurum.bake.AtlasBaker;
import dev.impeccable.aurum.bake.NoiseBaker;
import dev.impeccable.aurum.compat.Compat;
import dev.impeccable.aurum.render.AurumTextures;
import dev.impeccable.aurum.render.terrain.TerrainShaders;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import java.util.Collection;
import java.util.List;
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
            AurumTextures.init();
            NoiseBaker.bake();
            if (ShaderManager.compileAll()) {
                TerrainShaders.decide();
                Pipeline.markActive();
            }
        });

        // The normal and material atlas is rebuilt whenever resource packs (and so the block atlas) change.
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return new Identifier(MOD_ID, "normal_atlas");
            }

            @Override
            public Collection<Identifier> getFabricDependencies() {
                return List.of(ResourceReloadListenerKeys.MODELS, ResourceReloadListenerKeys.TEXTURES);
            }

            @Override
            public void reload(ResourceManager manager) {
                try {
                    AtlasBaker.bake();
                } catch (RuntimeException e) {
                    LOGGER.error("Normal atlas bake failed; terrain uses flat normals", e);
                }
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> ChatNotices.deliver(client)));
        HudRenderCallback.EVENT.register(TimerOverlay::render);
    }
}
