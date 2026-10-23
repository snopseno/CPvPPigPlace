package com.snopseno.client.gui;

import com.snopseno.config.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;

@Environment(EnvType.CLIENT)
public class ConfigScreen extends Screen {
    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(Text.literal("CpvpPigPlace Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 200;
        int h = 20;
        int step = 21;
        int x = this.width / 2 - w / 2;
        int y = 24;
        ModConfig cfg = ModConfig.get();

        this.addDrawableChild(toggle(x, y, w, h, "Fast Right Click",
                () -> cfg.fastRightClick, v -> cfg.fastRightClick = v));
        this.addDrawableChild(new TickSlider(x, y + step, w, h, "Right click delay",
                cfg.fastRightClickDelay, 20, v -> cfg.fastRightClickDelay = v));

        this.addDrawableChild(toggle(x, y + step * 2, w, h, "Fast Left Click",
                () -> cfg.fastLeftClick, v -> cfg.fastLeftClick = v));
        this.addDrawableChild(new TickSlider(x, y + step * 3, w, h, "Left click delay",
                cfg.fastLeftClickDelay, 20, v -> cfg.fastLeftClickDelay = v));

        this.addDrawableChild(toggle(x, y + step * 4, w, h, "Tick Attack (axe/mace)",
                () -> cfg.tickAttack, v -> cfg.tickAttack = v));
        this.addDrawableChild(new TickSlider(x, y + step * 5, w, h, "Tick attack delay",
                cfg.tickAttackDelay, 40, v -> cfg.tickAttackDelay = v));

        this.addDrawableChild(toggle(x, y + step * 6, w, h, "Anchor Switch (anchor > glowstone)",
                () -> cfg.anchorSwitch, v -> cfg.anchorSwitch = v));
        this.addDrawableChild(new TickSlider(x, y + step * 7, w, h, "Anchor switch delay",
                cfg.anchorSwitchDelay, 10, v -> cfg.anchorSwitchDelay = v));

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Exclusions..."), btn -> {
            if (this.client != null) {
                this.client.setScreen(new ExclusionsScreen(this));
            }
        }).dimensions(x, y + step * 8, w, h).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> {
            ModConfig.get().save();
            if (this.client != null) {
                this.client.setScreen(this.parent);
            }
        }).dimensions(x, y + step * 9, w, h).build());
    }

    private ButtonWidget toggle(int x, int y, int w, int h, String label,
                                java.util.function.BooleanSupplier getter,
                                java.util.function.Consumer<Boolean> setter) {
        return ButtonWidget.builder(toggleText(label, getter.getAsBoolean()), btn -> {
            boolean next = !getter.getAsBoolean();
            setter.accept(next);
            ModConfig.get().save();
            btn.setMessage(toggleText(label, next));
        }).dimensions(x, y, w, h).build();
    }

    private static Text toggleText(String label, boolean on) {
        return Text.literal(label + ": " + (on ? "ON" : "OFF"));
    }

    @Override
    public void removed() {
        ModConfig.get().save();
        super.removed();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
    }

    private static class TickSlider extends SliderWidget {
        private static final int MIN = 1;
        private final int max;
        private final String label;
        private final IntConsumer onChange;

        TickSlider(int x, int y, int width, int height, String label, int current, int max, IntConsumer onChange) {
            super(x, y, width, height, Text.empty(),
                    (Math.max(MIN, Math.min(max, current)) - MIN) / (double) (max - MIN));
            this.max = max;
            this.label = label;
            this.onChange = onChange;
            this.updateMessage();
        }

        private int ticks() {
            return MIN + (int) Math.round(this.value * (this.max - MIN));
        }

        @Override
        protected void updateMessage() {
            int t = this.ticks();
            this.setMessage(Text.literal(this.label + ": " + t + (t == 1 ? " tick" : " ticks")));
        }

        @Override
        protected void applyValue() {
            this.onChange.accept(this.ticks());
        }
    }
}