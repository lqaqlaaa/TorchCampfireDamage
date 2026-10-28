package com.torchcampfiredamage.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 显示设置界面：方块选择 + 透明度。
 */
public class DisplaySettingsScreen extends Screen {

    private final Screen parent;

    private EditBox blockABox;
    private EditBox blockBBox;
    private EditBox alphaBox;

    /** 选中的方块 */
    public static Block blockA = RangeOverlay.DEFAULT_A;
    public static Block blockB = RangeOverlay.DEFAULT_B;
    /** 透明度 0.05 ~ 1.0 */
    public static float alpha = 0.45f;

    public DisplaySettingsScreen(Screen parent) {
        super(Component.literal("显示设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = this.height / 2 - 60;

        // 方块 A
        blockABox = new EditBox(this.font, cx - 40, top, 200, 20,
                Component.literal("方块A"));
        blockABox.setValue(BuiltInRegistries.BLOCK.getKey(blockA).toString());
        blockABox.setTooltip(Tooltip.create(Component.literal(
                "棋盘格的第一种方块\n例如：minecraft:black_concrete")));
        addRenderableWidget(blockABox);

        // 方块 B
        blockBBox = new EditBox(this.font, cx - 40, top + 32, 200, 20,
                Component.literal("方块B"));
        blockBBox.setValue(BuiltInRegistries.BLOCK.getKey(blockB).toString());
        blockBBox.setTooltip(Tooltip.create(Component.literal(
                "棋盘格的第二种方块\n例如：minecraft:white_concrete")));
        addRenderableWidget(blockBBox);

        // 透明度
        alphaBox = new EditBox(this.font, cx - 40, top + 64, 80, 20,
                Component.literal("透明度"));
        alphaBox.setValue(String.valueOf(alpha));
        alphaBox.setTooltip(Tooltip.create(Component.literal(
                "0.05 ~ 1.0\n越小越透明\n0.45 = 默认")));
        addRenderableWidget(alphaBox);

        // 常用预设按钮
        addRenderableWidget(Button.builder(Component.literal("黑白混凝土"), b -> {
            blockABox.setValue("minecraft:black_concrete");
            blockBBox.setValue("minecraft:white_concrete");
        }).bounds(cx - 40, top + 92, 96, 20).build());

        addRenderableWidget(Button.builder(Component.literal("黑白羊毛"), b -> {
            blockABox.setValue("minecraft:black_wool");
            blockBBox.setValue("minecraft:white_wool");
        }).bounds(cx + 64, top + 92, 96, 20).build());

        addRenderableWidget(Button.builder(Component.literal("萤石/海晶灯"), b -> {
            blockABox.setValue("minecraft:glowstone");
            blockBBox.setValue("minecraft:sea_lantern");
        }).bounds(cx - 40, top + 116, 96, 20).build());

        addRenderableWidget(Button.builder(Component.literal("染色玻璃"), b -> {
            blockABox.setValue("minecraft:black_stained_glass");
            blockBBox.setValue("minecraft:white_stained_glass");
        }).bounds(cx + 64, top + 116, 96, 20).build());

        // 透明度预设
        addRenderableWidget(Button.builder(Component.literal("很淡 0.2"), b ->
                alphaBox.setValue("0.2")).bounds(cx - 40, top + 140, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("适中 0.45"), b ->
                alphaBox.setValue("0.45")).bounds(cx + 26, top + 140, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("明显 0.75"), b ->
                alphaBox.setValue("0.75")).bounds(cx + 98, top + 140, 66, 20).build());

        // 底部
        addRenderableWidget(Button.builder(Component.literal("确定"), b -> apply())
                .bounds(cx - 84, top + 180, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b ->
                minecraft.setScreen(parent)).bounds(cx + 4, top + 180, 80, 20).build());
    }

    private void apply() {
        blockA = parseBlock(blockABox.getValue(), RangeOverlay.DEFAULT_A);
        blockB = parseBlock(blockBBox.getValue(), RangeOverlay.DEFAULT_B);
        alpha = parseAlpha(alphaBox.getValue(), 0.45f);
        RangeOverlay.alpha = alpha;

        // 回到主界面（主界面会在 onClose 时刷新显示）
        minecraft.setScreen(parent);
    }

    private static Block parseBlock(String s, Block def) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(s.trim().toLowerCase());
            if (rl == null) return def;
            if (!BuiltInRegistries.BLOCK.containsKey(rl)) return def;
            Block b = BuiltInRegistries.BLOCK.get(rl);
            return b == null ? def : b;
        } catch (Exception e) {
            return def;
        }
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

        int cx = this.width / 2;
        int top = this.height / 2 - 60;

        gg.drawCenteredString(this.font, "范围显示设置", cx, top - 24, 0xFFD700);
        gg.drawString(this.font, "方块A", cx - 90, top + 6, 0xFFFFFF);
        gg.drawString(this.font, "方块B", cx - 90, top + 38, 0xFFFFFF);
        gg.drawString(this.font, "透明度", cx - 90, top + 70, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}