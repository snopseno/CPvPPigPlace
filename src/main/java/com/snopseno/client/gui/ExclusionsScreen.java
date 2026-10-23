package com.snopseno.client.gui;

import com.snopseno.config.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class ExclusionsScreen extends Screen {
    private static final int ROW_H = 18;
    private static final int LIST_Y = 62;
    private static final String[] KINDS = {"Items", "Blocks", "Entities"};
    private static final List<List<String>> CACHE = new ArrayList<>();
    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private final Screen parent;
    private int kind = 0;
    private int page = 0;
    private int rowCount = 5;
    private int colW;
    private int leftX;
    private int rightX;
    private String query = "";
    private List<String> left = new ArrayList<>();
    private List<String> right = new ArrayList<>();
    private ButtonWidget[] leftRows;
    private ButtonWidget[] rightRows;
    private ButtonWidget kindBtn;
    private ButtonWidget prevBtn;
    private ButtonWidget nextBtn;
    private TextFieldWidget search;

    public ExclusionsScreen(Screen parent) {
        super(Text.literal("Click exclusions"));
        this.parent = parent;
    }

    private static List<String> ids(int kind) {
        if (CACHE.isEmpty()) {
            CACHE.add(sortedIds(Registries.ITEM.getIds()));
            CACHE.add(sortedIds(Registries.BLOCK.getIds()));
            CACHE.add(sortedIds(Registries.ENTITY_TYPE.getIds()));
        }
        return CACHE.get(kind);
    }

    private static List<String> sortedIds(Set<Identifier> set) {
        List<String> out = new ArrayList<>(set.size());
        for (Identifier id : set) {
            out.add(id.toString());
        }
        Collections.sort(out);
        return out;
    }

    private static ItemStack icon(String id) {
        return ICONS.computeIfAbsent(id, key -> {
            Identifier ident = Identifier.tryParse(key);
            if (ident == null) return ItemStack.EMPTY;
            if (Registries.ITEM.containsId(ident)) return new ItemStack(Registries.ITEM.get(ident));
            if (Registries.BLOCK.containsId(ident)) return new ItemStack(Registries.BLOCK.get(ident).asItem());
            return ItemStack.EMPTY;
        });
    }

    @Override
    protected void init() {
        this.colW = Math.min(200, (this.width - 30) / 2);
        this.leftX = this.width / 2 - this.colW - 4;
        this.rightX = this.width / 2 + 4;
        this.rowCount = Math.max(3, (this.height - LIST_Y - 42) / ROW_H);

        this.search = new TextFieldWidget(this.textRenderer, this.leftX, 28, this.colW, 20, Text.empty());
        this.search.setText(this.query);
        this.search.setChangedListener(s -> {
            this.query = s;
            this.page = 0;
            this.refresh();
        });
        this.addDrawableChild(this.search);

        this.kindBtn = this.addDrawableChild(ButtonWidget.builder(Text.empty(), btn -> {
            this.kind = (this.kind + 1) % KINDS.length;
            this.page = 0;
            this.refresh();
        }).dimensions(this.rightX, 28, this.colW, 20).build());

        this.leftRows = new ButtonWidget[this.rowCount];
        this.rightRows = new ButtonWidget[this.rowCount];
        for (int i = 0; i < this.rowCount; i++) {
            final int row = i;
            int y = LIST_Y + i * ROW_H;
            this.leftRows[i] = this.addDrawableChild(ButtonWidget.builder(Text.empty(), btn -> this.click(true, row))
                    .dimensions(this.leftX, y, this.colW, ROW_H - 2).build());
            this.rightRows[i] = this.addDrawableChild(ButtonWidget.builder(Text.empty(), btn -> this.click(false, row))
                    .dimensions(this.rightX, y, this.colW, ROW_H - 2).build());
        }

        int by = this.height - 24;
        this.prevBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), btn -> {
            this.page--;
            this.refresh();
        }).dimensions(this.width / 2 - 100, by, 40, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> {
            ModConfig.get().save();
            if (this.client != null) {
                this.client.setScreen(this.parent);
            }
        }).dimensions(this.width / 2 - 55, by, 110, 20).build());

        this.nextBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), btn -> {
            this.page++;
            this.refresh();
        }).dimensions(this.width / 2 + 60, by, 40, 20).build());

        this.refresh();
    }

    private void click(boolean fromLeft, int row) {
        List<String> src = fromLeft ? this.left : this.right;
        int idx = this.page * this.rowCount + row;
        if (idx >= src.size()) return;
        String id = src.get(idx);
        List<String> ex = ModConfig.get().excluded;
        if (fromLeft) {
            ex.remove(id);
        } else if (!ex.contains(id)) {
            ex.add(id);
        }
        ModConfig.get().save();
        this.refresh();
    }

    private int maxPage() {
        return Math.max(0, (Math.max(this.left.size(), this.right.size()) - 1) / this.rowCount);
    }

    private void refresh() {
        String q = this.query.toLowerCase(Locale.ROOT).trim();
        List<String> ex = ModConfig.get().excluded;
        Set<String> exSet = new HashSet<>(ex);

        this.left = new ArrayList<>();
        for (String s : ex) {
            if (s.contains(q)) this.left.add(s);
        }
        Collections.sort(this.left);

        this.right = new ArrayList<>();
        for (String s : ids(this.kind)) {
            if (s.contains(q) && !exSet.contains(s)) this.right.add(s);
        }

        this.page = Math.max(0, Math.min(this.page, this.maxPage()));
        this.kindBtn.setMessage(Text.literal("Show: " + KINDS[this.kind]));

        for (int i = 0; i < this.rowCount; i++) {
            int idx = this.page * this.rowCount + i;
            this.leftRows[i].visible = idx < this.left.size();
            this.rightRows[i].visible = idx < this.right.size();
        }
        this.prevBtn.active = this.page > 0;
        this.nextBtn.active = this.page < this.maxPage();
    }

    @Override
    public void removed() {
        ModConfig.get().save();
        super.removed();
    }

    private void drawRow(DrawContext ctx, ButtonWidget btn, String id) {
        ItemStack icon = icon(id);
        int x = btn.getX();
        int y = btn.getY();
        if (!icon.isEmpty()) {
            ctx.drawItem(icon, x + 2, y);
        }
        String text = this.textRenderer.trimToWidth(id, this.colW - 28);
        ctx.drawTextWithShadow(this.textRenderer, text, x + 22, y + (btn.getHeight() - 8) / 2, 0xFFFFFFFF);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 10, 0xFFFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer, "Excluded (" + ModConfig.get().excluded.size() + ")",
                this.leftX, LIST_Y - 11, 0xFFAAAAAA);
        ctx.drawTextWithShadow(this.textRenderer, "Available", this.rightX, LIST_Y - 11, 0xFFAAAAAA);

        for (int i = 0; i < this.rowCount; i++) {
            int idx = this.page * this.rowCount + i;
            if (idx < this.left.size()) this.drawRow(ctx, this.leftRows[i], this.left.get(idx));
            if (idx < this.right.size()) this.drawRow(ctx, this.rightRows[i], this.right.get(idx));
        }

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal((this.page + 1) + " / " + (this.maxPage() + 1)),
                cx, this.height - 36, 0xFFAAAAAA);
    }
}