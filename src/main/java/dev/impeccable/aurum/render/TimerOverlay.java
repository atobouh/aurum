package dev.impeccable.aurum.render;

import dev.impeccable.aurum.AurumMod;
import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.director.SunState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The "show FPS cost" overlay: per-section GPU milliseconds (average and worst over half a second). Drawn top
 * right, or bottom right while F3 is open so both can be screenshotted together.
 */
public final class TimerOverlay {
    private record Row(String label, String avg, String max, int color) {}

    private static final int HEADER = 0xFFFFC34D;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int DIM = 0xFFA0A0A0;

    private TimerOverlay() {}

    public static void render(DrawContext ctx, float tickDelta) {
        AurumConfig cfg = AurumConfig.get();
        MinecraftClient client = MinecraftClient.getInstance();
        if (!cfg.showOverlay || client.options.hudHidden) return;

        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Aurum " + AurumMod.VERSION + " | " + cfg.preset + " | " + Pipeline.state(), "", "", HEADER));
        rows.add(new Row(GlInfo.renderer(), "", "", DIM));
        rows.add(new Row(String.format("FPS %d   %dx%d   sun %+.1f°", client.getCurrentFps(),
                client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), SunState.elevationDegrees()), "", "", TEXT));

        if (!GpuTimers.isSupported()) {
            rows.add(new Row("GPU timers unsupported by this driver", "", "", DIM));
        } else {
            rows.add(new Row("GPU ms", "avg", "max", DIM));
            double worldChildren = 0;
            for (TimerSection s : TimerSection.VALUES) {
                if (s.indent == 2) worldChildren += GpuTimers.avgMs(s);
                rows.add(new Row("  ".repeat(s.indent) + s.label, ms(GpuTimers.avgMs(s)), ms(GpuTimers.maxMs(s)),
                        s.indent == 0 ? HEADER : TEXT));
                if (s == TimerSection.WEATHER) {
                    double other = Math.max(0, GpuTimers.avgMs(TimerSection.WORLD) - worldChildren);
                    rows.add(new Row("    Other (entities, particles)", ms(other), "", DIM));
                }
            }
            int dropped = GpuTimers.droppedLastWindow();
            if (dropped > 0) rows.add(new Row("readbacks skipped: " + dropped, "", "", DIM));
        }

        TextRenderer tr = client.textRenderer;
        int labelW = 0, numW = tr.getWidth("00.00");
        for (Row r : rows) {
            if (r.avg.isEmpty()) continue;
            labelW = Math.max(labelW, tr.getWidth(r.label));
        }
        int tableW = labelW + 8 + numW + 8 + numW;
        int width = tableW;
        for (Row r : rows) if (r.avg.isEmpty()) width = Math.max(width, tr.getWidth(r.label));

        int lineH = tr.fontHeight + 1;
        int boxH = rows.size() * lineH + 4;
        int x = ctx.getScaledWindowWidth() - width - 6;
        int y = client.options.debugEnabled ? ctx.getScaledWindowHeight() - boxH - 2 : 2;
        int colAvg = x + labelW + 8 + numW;
        int colMax = colAvg + 8 + numW;

        ctx.fill(x - 3, y, x + width + 3, y + boxH, 0x90000000);
        int ty = y + 2;
        for (Row r : rows) {
            ctx.drawText(tr, r.label, x, ty, r.color, false);
            if (!r.avg.isEmpty()) ctx.drawText(tr, r.avg, colAvg - tr.getWidth(r.avg), ty, r.color, false);
            if (!r.max.isEmpty()) ctx.drawText(tr, r.max, colMax - tr.getWidth(r.max), ty, DIM, false);
            ty += lineH;
        }
    }

    private static String ms(double v) {
        return String.format("%.2f", v);
    }
}
