package dev.impeccable.aurum.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/**
 * Background for Aurum's settings screens. In game it draws only a soft, narrow dark panel behind the widgets,
 * so the world stays visible and every change shows live. From the title screen it uses the normal background.
 */
final class SeeThrough {
    private static final int PANEL_HALF_WIDTH = 170;

    private SeeThrough() {}

    static void background(Screen screen, DrawContext context) {
        if (MinecraftClient.getInstance().world == null) {
            screen.renderBackground(context);
            return;
        }
        int cx = screen.width / 2;
        int x0 = cx - PANEL_HALF_WIDTH, x1 = cx + PANEL_HALF_WIDTH;
        // Soft edges: a few fading strips on each side of a light panel.
        context.fill(x0, 0, x1, screen.height, 0x58000000);
        for (int i = 1; i <= 6; i++) {
            int a = 0x58 * (7 - i) / 7;
            context.fill(x0 - i * 3, 0, x0 - (i - 1) * 3, screen.height, a << 24);
            context.fill(x1 + (i - 1) * 3, 0, x1 + i * 3, screen.height, a << 24);
        }
    }
}
