package dev.impeccable.aurum.director;

import dev.impeccable.aurum.render.GpuTimers;
import net.minecraft.client.MinecraftClient;

/**
 * Runs once per frame on the CPU before anything is drawn and decides what the GPU passes will do this frame.
 * Phase 0: sun state and GPU timer frame boundaries. Later phases add fog density and cache rebuild decisions.
 */
public final class FrameDirector {
    private FrameDirector() {}

    public static void beginFrame(float tickDelta) {
        GpuTimers.beginFrame();
        SunState.update(MinecraftClient.getInstance().world, tickDelta);
    }
}
