package dev.impeccable.aurum.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** One page of options, laid out by the vanilla option list (two columns, scrolls). */
public class AurumCategoryScreen extends Screen {
    private final Screen parent;
    private final OptionCategory category;
    private OptionListWidget list;

    public AurumCategoryScreen(Screen parent, OptionCategory category) {
        super(Text.translatable("aurum.settings.title").append(" · ").append(category.title()));
        this.parent = parent;
        this.category = category;
    }

    @Override
    protected void init() {
        this.list = new OptionListWidget(this.client, this.width, this.height, 32, this.height - 32, 25);
        this.list.addAll(this.category.options());
        // See-through in game: the world stays visible (and live) behind the options while tweaking.
        if (this.client.world != null) {
            this.list.setRenderBackground(false);
            this.list.setRenderHorizontalShadows(false);
        }
        this.addSelectableChild(this.list);
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> this.close())
                .dimensions(this.width / 2 - 100, this.height - 27, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        SeeThrough.background(this, context);
        this.list.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        // The world keeps moving (wind, clouds, water) so changes show as they would in play.
        return this.client.world == null;
    }

    @Override
    public void close() {
        AurumConfig.save();
        this.client.setScreen(this.parent);
    }
}
