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
 * 显示设置 —— 与主界面统一风格（不画黑底、标签在左、尺寸自适应）。
 * <p>
 * 范围预览方块 A/B + 透明度、中心物品、星环开关。
 * 星环详细配置在 {@link RingSettingsScreen}。
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

    public static void loadFrom(WardSourceBlockEntity be) {
        orbitVisible = be.isOrbitVisible();
        centerItem = be.getCenterItem().copy();
        centerScale = String.format("%.2f", be.getCenterScale());
        centerSpin = String.format("%.2f", be.getCenterSpin());
    }

    /* ===== 布局参数（自适应） ===== */
    private int px, py;
    private int rowH, boxH, boxW, labelW;

    private EditBox alphaBox;

    public DisplaySettingsScreen(Screen parent) {
        super(Component.literal("显示设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        labelW = GuiLayout.fit(60, this.width, this.height, 40);
        boxW = GuiLayout.fit(80, this.width, this.height, 50);

        int availW = this.width - 16;
        if (labelW + boxW > availW) {
            boxW = Math.max(44, availW - labelW);
        }

        int availH = this.height - 70;
        int rows = 5;
        rowH = GuiLayout.actualRowH(availH, rows, 22, 14);
        boxH = Math.min(20, rowH - 2);
        if (boxH < 10) boxH = 10;

        int blockW = labelW + boxW;
        px = (this.width - blockW) / 2;
        py = 26 + Math.max(0, (availH - rows * rowH) / 2);

        buildPage();
    }

    private void buildPage() {
        this.clearWidgets();

        int x = px + labelW;

        /* 第 1、2 行：预览方块 A / B */
        addRenderableWidget(Button.builder(blockLabel(blockA), b ->
                minecraft.setScreen(new BlockPickerScreen(this, true)))
                .bounds(x, py, boxW, boxH).build());

        addRenderableWidget(Button.builder(blockLabel(blockB), b ->
                minecraft.setScreen(new BlockPickerScreen(this, false)))
                .bounds(x, py + rowH, boxW, boxH).build());

        /* 第 3 行：透明度 */
        alphaBox = new EditBox(this.font, x, py + rowH * 2, boxW, boxH, Component.literal("透明度"));
        alphaBox.setValue(String.format("%.2f", alpha));
        alphaBox.setTooltip(Tooltip.create(Component.literal("0.05 ~ 1.0")));
        alphaBox.setResponder(v -> {
            try { alpha = Float.parseFloat(v.trim()); } catch (Exception ignored) {}
        });
        addRenderableWidget(alphaBox);

        /* 第 4 行：中心物品 + 大小 + 转速 */
        int y4 = py + rowH * 3;
        addRenderableWidget(Button.builder(Component.literal(""), b ->
                minecraft.setScreen(new ItemPickerScreen(this, 99)))
                .bounds(x, y4, boxH, boxH)
                .tooltip(Tooltip.create(Component.literal("点击选择中心物品")))
                .build());

        int restW = Math.max(40, boxW - boxH - 6);
        int halfW = Math.max(34, restW / 2 - 3);

        EditBox scaleBox = new EditBox(this.font, x + boxH + 6, y4, halfW, boxH,
                Component.literal("大小"));
        scaleBox.setValue(centerScale);
        scaleBox.setTooltip(Tooltip.create(Component.literal("物品大小")));
        scaleBox.setResponder(v -> centerScale = v);
        addRenderableWidget(scaleBox);

        EditBox spinBox = new EditBox(this.font, x + boxH + 6 + halfW + 6, y4, halfW, boxH,
                Component.literal("转速"));
        spinBox.setValue(centerSpin);
        spinBox.setTooltip(Tooltip.create(Component.literal("度/tick，可正可负")));
        spinBox.setResponder(v -> centerSpin = v);
        addRenderableWidget(spinBox);

        /* 第 5 行：星环开关 */
        addRenderableWidget(Button.builder(orbitVisibleText(), b -> {
            orbitVisible = !orbitVisible;
            b.setMessage(orbitVisibleText());
        }).bounds(x, py + rowH * 4, boxW, boxH).build());

        /* 底部按钮 */
        int cx = this.width / 2;
        int bw = Math.min(80, (this.width - 40) / 2);
        int gap = 8;
        int by = this.height - 30;

        addRenderableWidget(Button.builder(Component.literal("确定"), b -> {
            apply();
            minecraft.setScreen(parent);
        }).bounds(cx - bw - gap / 2, by, bw, 20).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b ->
                minecraft.setScreen(parent)).bounds(cx + gap / 2, by, bw, 20).build());
    }

    private Component blockLabel(Block b) {
        return Component.literal(new ItemStack(b).getHoverName().getString());
    }

    private Component orbitVisibleText() {
        return Component.literal("星环: " + (orbitVisible ? "§a开" : "§c关"));
    }

    private void apply() {
        try {
            alpha = Math.max(0.05f, Math.min(1.0f, Float.parseFloat(alphaBox.getValue().trim())));
        } catch (Exception ignored) {
        }
        RangeOverlay.alpha = alpha;
    }

    /* ================= 渲染 ================= */

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);
        super.render(gg, mouseX, mouseY, partialTick);

        gg.drawCenteredString(this.font, "显示设置", this.width / 2, 8, 0xFFD700);

        int x = px;
        int tx = px + labelW;
        int textOff = (boxH - 8) / 2;

        // 第 1、2 行：方块预览（图标画在标签和按钮之间）
        gg.drawString(this.font, "方块 A", x, py + textOff, 0xFFFFFF);
        gg.drawString(this.font, "方块 B", x, py + rowH + textOff, 0xFFFFFF);

        // 第 3 行：透明度
        gg.drawString(this.font, "透明度", x, py + rowH * 2 + textOff, 0xFFFFFF);

        // 第 4 行：中心物品
        int y4 = py + rowH * 3;
        gg.drawString(this.font, "中心物品", x, y4 + textOff, 0xFFFFFF);
        gg.fill(tx - 1, y4 - 1, tx + boxH + 1, y4 + boxH + 1, 0xFF3A3A3A);
        if (!centerItem.isEmpty()) {
            gg.renderItem(centerItem, tx + 2, y4 + 2);
        }

        // 第 5 行：星环
        gg.drawString(this.font, "星环显示", x, py + rowH * 4 + textOff, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}