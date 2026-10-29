package com.torchcampfiredamage.client;

import com.torchcampfiredamage.block.WardSourceBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * 显示设置：
 *  - 范围预览方块 A/B + 透明度
 *  - 中心物品（外观）
 *  - 星环开关
 * <p>
 * 星环的详细配置在 {@link RingSettingsScreen}（独立界面）。
 */
public class DisplaySettingsScreen extends Screen {

    private final Screen parent;

    /* ===== 范围显示 ===== */
    public static Block blockA = RangeOverlay.DEFAULT_A;
    public static Block blockB = RangeOverlay.DEFAULT_B;
    public static float alpha = 0.45f;

    /* ===== 中心物品 ===== */
    public static ItemStack centerItem = new ItemStack(Items.NETHER_STAR);
    public static String centerScale = "0.9";
    public static String centerSpin = "1.5";

    /* ===== 星环总开关 ===== */
    public static boolean orbitVisible = true;

    /** 从方块实体读取 */
    public static void loadFrom(WardSourceBlockEntity be) {
        orbitVisible = be.isOrbitVisible();
        centerItem = be.getCenterItem().copy();
        centerScale = String.format("%.2f", be.getCenterScale());
        centerSpin = String.format("%.2f", be.getCenterSpin());
    }

    private int px, py;
    private static final int ROW_H = 20;
    private static final int PANEL_W = 340;

    private EditBox alphaBox;
    private EditBox centerScaleBox;
    private EditBox centerSpinBox;

    public DisplaySettingsScreen(Screen parent) {
        super(Component.literal("显示设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        px = cx - PANEL_W / 2;
        py = Math.max(16, cy - 90);

        /* ===== 范围显示 ===== */
        addRenderableWidget(Button.builder(blockLabel(blockA), b ->
                minecraft.setScreen(new BlockPickerScreen(this, true)))
                .bounds(px + 78, py + 28, 110, ROW_H).build());

        addRenderableWidget(Button.builder(blockLabel(blockB), b ->
                minecraft.setScreen(new BlockPickerScreen(this, false)))
                .bounds(px + 196, py + 28, 110, ROW_H).build());

        alphaBox = new EditBox(this.font, px + 78, py + 54, 56, ROW_H, Component.literal("透明度"));
        alphaBox.setValue(String.format("%.2f", alpha));
        alphaBox.setTooltip(Tooltip.create(Component.literal("0.05 ~ 1.0")));
        addRenderableWidget(alphaBox);

        addRenderableWidget(Button.builder(Component.literal("0.2"), b -> alphaBox.setValue("0.2"))
                .bounds(px + 138, py + 54, 34, ROW_H).build());
        addRenderableWidget(Button.builder(Component.literal("0.45"), b -> alphaBox.setValue("0.45"))
                .bounds(px + 176, py + 54, 40, ROW_H).build());

        /* ===== 中心物品 ===== */
        addRenderableWidget(Button.builder(Component.literal(""), b ->
                minecraft.setScreen(new ItemPickerScreen(this, 99)))
                .bounds(px + 78, py + 84, ROW_H, ROW_H)
                .tooltip(Tooltip.create(Component.literal("点击选择中心物品")))
                .build());

        centerScaleBox = new EditBox(this.font, px + 106, py + 84, 56, ROW_H, Component.literal("大小"));
        centerScaleBox.setValue(centerScale);
        centerScaleBox.setTooltip(Tooltip.create(Component.literal("物品大小（不限制）")));
        centerScaleBox.setResponder(v -> centerScale = v);
        addRenderableWidget(centerScaleBox);

        centerSpinBox = new EditBox(this.font, px + 168, py + 84, 56, ROW_H, Component.literal("转速"));
        centerSpinBox.setValue(centerSpin);
        centerSpinBox.setTooltip(Tooltip.create(Component.literal("度/tick，可正可负")));
        centerSpinBox.setResponder(v -> centerSpin = v);
        addRenderableWidget(centerSpinBox);

        /* ===== 底部 ===== */
        addRenderableWidget(Button.builder(orbitVisibleText(), b -> {
            orbitVisible = !orbitVisible;
            b.setMessage(orbitVisibleText());
        }).bounds(px + 30, py + 120, 96, ROW_H).build());

        addRenderableWidget(Button.builder(Component.literal("星环设置"), b -> {
            RingSettingsScreen.loadFrom(WardConfigScreen.lastBE);
            minecraft.setScreen(new RingSettingsScreen(this));
        }).bounds(px + 132, py + 120, 88, ROW_H)
                .tooltip(Tooltip.create(Component.literal("配置每一圈圈的物品/半径/速度/角度")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("确定"), b -> apply())
                .bounds(px + 224, py + 120, 60, ROW_H).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b ->
                minecraft.setScreen(parent)).bounds(px + 288, py + 120, 40, ROW_H).build());
    }

    private Component blockLabel(Block b) {
        return Component.literal(new ItemStack(b).getHoverName().getString());
    }

    private Component orbitVisibleText() {
        return Component.literal("星环: " + (orbitVisible ? "开" : "关"));
    }

    private void apply() {
        alpha = parse(alphaBox.getValue(), 0.45f);
        RangeOverlay.alpha = alpha;
        minecraft.setScreen(parent);
    }

    private static float parse(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);
        super.render(gg, mouseX, mouseY, partialTick);

        gg.fill(px, py, px + PANEL_W, py + 150, 0xC0000000);
        gg.drawCenteredString(this.font, "显示设置", px + PANEL_W / 2, py + 6, 0xFFD700);

        // 范围显示
        gg.drawString(this.font, "§e范围预览", px + 12, py + 12, 0xFFFFFF);
        gg.drawString(this.font, "方块", px + 12, py + 34, 0xFFFFFF);
        gg.drawString(this.font, "透明度", px + 12, py + 60, 0xFFFFFF);

        gg.renderItem(new ItemStack(blockA), px + 62, py + 29);
        gg.renderItem(new ItemStack(blockB), px + 180, py + 29);

        // 中心物品
        gg.drawString(this.font, "§e中心物品", px + 12, py + 68, 0xFFFFFF);
        gg.fill(px + 77, py + 83, px + 99, py + 105, 0xFF3A3A3A);
        if (!centerItem.isEmpty()) {
            gg.renderItem(centerItem, px + 80, py + 86);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}