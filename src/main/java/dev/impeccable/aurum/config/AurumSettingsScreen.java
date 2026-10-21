package dev.impeccable.aurum.config;

import dev.impeccable.aurum.AurumMod;
import dev.impeccable.aurum.render.Pipeline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Landing page: preset, overlay toggle, a button per category and the pipeline status. */
public class AurumSettingsScreen extends Screen {
    private final Screen parent;

    public AurumSettingsScreen(Screen parent) {
        super(Text.translatable("aurum.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        AurumConfig c = AurumConfig.get();
        int center = this.width / 2;
        int y = 40;

        this.addDrawableChild(CyclingButtonWidget.<Preset>builder(p -> Text.translatable("aurum.preset." + p.name().toLowerCase()))
                .values(Preset.LOW, Preset.MEDIUM, Preset.HIGH, Preset.CUSTOM)
                .initially(c.preset)
                .tooltip(p -> Tooltip.of(Text.translatable("aurum.preset.tooltip")))
                .build(center - 155, y, 150, 20, Text.translatable("aurum.preset"), (button, preset) -> {
                    preset.applyTo(c);
                    c.preset = preset;
                    AurumConfig.save();
                }));

        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(c.showOverlay)
                .tooltip(v -> Tooltip.of(Text.translatable("aurum.option.show_overlay.tooltip")))
                .build(center + 5, y, 150, 20, Text.translatable("aurum.option.show_overlay"), (button, v) -> {
                    c.showOverlay = v;
                    AurumConfig.save();
                }));

        y += 32;
        OptionCategory[] categories = OptionCategory.values();
        for (int i = 0; i < categories.length; i++) {
            OptionCategory category = categories[i];
            int x = (i % 2 == 0) ? center - 155 : center + 5;
            int row = y + (i / 2) * 24;
            this.addDrawableChild(ButtonWidget.builder(category.title(),
                            b -> this.client.setScreen(new AurumCategoryScreen(this, category)))
                    .dimensions(x, row, 150, 20).build());
        }

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> this.close())
                .dimensions(center - 100, this.height - 27, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Pipeline.statusLine(), this.width / 2, this.height - 52, 0xA0A0A0);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("aurum.settings.phase_note", AurumMod.VERSION), this.width / 2, this.height - 40, 0x707070);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        AurumConfig.save();
        this.client.setScreen(this.parent);
    }
}
