package com.torchcampfiredamage.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * 显示设置：方块选择（点击弹出方块列表）+ 透明度。
 */
public class DisplaySettingsScreen extends Screen {

    private final Screen parent;

    private EditBox alphaBox;

    public static Block blockA = RangeOverlay.DEFAULT_A;
    public static Block blockB = RangeOverlay.DEFAULT_B;
    public static float alpha = 0.45f;

    /* 布局：以屏幕中心为基准 */
    private int px, py;

    public DisplaySettingsScreen(Screen parent) {
        super(Component.literal("显示设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        // 面板左上角：面板宽 320，高 200
        px = cx - 160;
        py = cy - 100;

        // ===== 方块 A 按钮 =====
        addRenderableWidget(Button.builder(blockLabel(blockA), b ->
                minecraft.setScreen(new BlockPickerScreen(this, true)))
                .bounds(px + 80, py + 20, 160, 20)
                .tooltip(Tooltip.create(Component.literal("点击选择棋盘格的第一种方块")))
                .build());

        // ===== 方块 B 按钮 =====
        addRenderableWidget(Button.builder(blockLabel(blockB), b ->
                minecraft.setScreen(new BlockPickerScreen(this, false)))
                .bounds(px + 80, py + 48, 160, 20)
                .tooltip(Tooltip.create(Component.literal("点击选择棋盘格的第二种方块")))
                .build());

        // ===== 透明度 =====
        alphaBox = new EditBox(this.font, px + 80, py + 78, 70, 20, Component.literal("透明度"));
        alphaBox.setValue(String.format("%.2f", alpha));
        alphaBox.setTooltip(Tooltip.create(Component.literal("0.05 ~ 1.0，越小越透明")));
        addRenderableWidget(alphaBox);

        // 透明度快捷
        addRenderableWidget(Button.builder(Component.literal("0.2"), b -> alphaBox.setValue("0.2"))
                .bounds(px + 156, py + 78, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("0.45"), b -> alphaBox.setValue("0.45"))
                .bounds(px + 200, py + 78, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("0.75"), b -> alphaBox.setValue("0.75"))
                .bounds(px + 244, py + 78, 40, 20).build());

        // ===== 底部按钮 =====
        addRenderableWidget(Button.builder(Component.literal("确定"), b -> apply())
                .bounds(px + 80, py + 160, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b ->
                minecraft.setScreen(parent)).bounds(px + 164, py + 160, 78, 20).build());

        addRenderableWidget(Button.builder(Component.literal("恢复默认"), b -> {
            blockA = RangeOverlay.DEFAULT_A;
            blockB = RangeOverlay.DEFAULT_B;
            alphaBox.setValue("0.45");
            // 重建界面以刷新按钮文字
            this.rebuildWidgets();
        }).bounds(px + 80, py + 130, 162, 20).build());
    }

    private Component blockLabel(Block b) {
        String name = new ItemStack(b).getHoverName().getString();
        return Component.literal(name);
    }

    private void apply() {
        alpha = parseAlpha(alphaBox.getValue(), 0.45f);
        RangeOverlay.alpha = alpha;
        minecraft.setScreen(parent);
    }

    private static float parseAlpha(String s, float def) {
        try {
            float v = Float.parseFloat(s.trim());
            return Math.max(0.05f, Math.min(1.0f, v));
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);
        super.render(gg, mouseX, mouseY, partialTick);

        // 面板背景
        gg.fill(px, py, px + 320, py + 200, 0xC0000000);

        gg.drawCenteredString(this.font, "显示设置", px + 160, py + 6, 0xFFD700);
        gg.drawString(this.font, "方块 A", px + 20, py + 26, 0xFFFFFF);
        gg.drawString(this.font, "方块 B", px + 20, py + 54, 0xFFFFFF);
        gg.drawString(this.font, "透明度", px + 20, py + 84, 0xFFFFFF);

        // 方块图标预览
        gg.renderItem(new ItemStack(blockA), px + 60, py + 21);
        gg.renderItem(new ItemStack(blockB), px + 60, py + 49);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}