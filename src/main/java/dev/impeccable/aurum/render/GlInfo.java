package dev.impeccable.aurum.render;

import dev.impeccable.aurum.AurumMod;
import org.lwjgl.opengl.GL11C;

/** Driver strings, captured once on the render thread. Shown in the overlay so test reports name the GPU. */
public final class GlInfo {
    private static String renderer = "?";
    private static String version = "?";

    private GlInfo() {}

    public static void capture() {
        renderer = String.valueOf(GL11C.glGetString(GL11C.GL_RENDERER));
        version = String.valueOf(GL11C.glGetString(GL11C.GL_VERSION));
        String vendor = String.valueOf(GL11C.glGetString(GL11C.GL_VENDOR));
        AurumMod.LOGGER.info("GL: {} | {} | {}", vendor, renderer, version);
    }

    public static String renderer() {
        return renderer;
    }

    public static String version() {
        return version;
    }
}
