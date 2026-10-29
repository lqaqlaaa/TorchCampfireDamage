package com.torchcampfiredamage.client;

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
 *  - 范围显示用的方块 A/B + 透明度
 *  - 光环：显示开关、半径、4 个旋转物品
 */
public class DisplaySettingsScreen extends Screen {

    private final Screen parent;

    private EditBox alphaBox;
    private EditBox radiusBox;

    /* ===== 范围显示 ===== */
    public static Block blockA = RangeOverlay.DEFAULT_A;
    public static Block blockB = RangeOverlay.DEFAULT_B;
    public static float alpha = 0.45f;

    /* ===== 光环 ===== */
    /** 4 个旋转物品 */
    private static final ItemStack[] ORBIT = new ItemStack[]{
            new ItemStack(Items.NETHER_STAR),
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            ItemStack.EMPTY
    };
    public static boolean orbitVisible = true;
    public static float orbitRadius = 0.75f;

    public static ItemStack getOrbitItem(int i) {
        if (i < 0 || i > 3) return ItemStack.EMPTY;
        return ORBIT[i];
    }

    public static void setOrbitItem(int i, ItemStack st) {
        if (i < 0 || i > 3) return;
        ORBIT[i] = (st == null) ? ItemStack.EMPTY : st.copyWithCount(1);
    }

    private int px, py;

    public DisplaySettingsScreen(Screen parent) {
        super(Component.literal("显示设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        px = cx - 170;
        py = cy - 110;

        /* ===== 左列：范围显示 ===== */
        addRenderableWidget(Button.builder(blockLabel(blockA), b ->
                minecraft.setScreen(new BlockPickerScreen(this, true)))
                .bounds(px + 70, py + 18, 130, 20).build());

        addRenderableWidget(Button.builder(blockLabel(blockB), b ->
                minecraft.setScreen(new BlockPickerScreen(this, false)))
                .bounds(px + 70, py + 44, 130, 20).build());

        alphaBox = new EditBox(this.font, px + 70, py + 70, 60, 18, Component.literal("透明度"));
        alphaBox.setValue(String.format("%.2f", alpha));
        alphaBox.setTooltip(Tooltip.create(Component.literal("0.05~1.0")));
        addRenderableWidget(alphaBox);

        addRenderableWidget(Button.builder(Component.literal("0.2"), b -> alphaBox.setValue("0.2"))
                .bounds(px + 134, py + 70, 32, 18).build());
        addRenderableWidget(Button.builder(Component.literal("0.45"), b -> alphaBox.setValue("0.45"))
                .bounds(px + 168, py + 70, 36, 18).build());

        /* ===== 右列：光环 ===== */
        addRenderableWidget(Button.builder(orbitVisibleText(), b -> {
            orbitVisible = !orbitVisible;
            b.setMessage(orbitVisibleText());
        }).bounds(px + 250, py + 18, 80, 20).build());

        radiusBox = new EditBox(this.font, px + 250, py + 44, 80, 18, Component.literal("半径"));
        radiusBox.setValue(String.format("%.2f", orbitRadius));
        radiusBox.setTooltip(Tooltip.create(Component.literal("0.3 ~ 2.0")));
        addRenderableWidget(radiusBox);

        // 4 个物品槽（每格 22x22）
        for (int i = 0; i < 4; i++) {
            final int slot = i;
            addRenderableWidget(Button.builder(Component.literal(""), b ->
                    minecraft.setScreen(new ItemPickerScreen(this, slot)))
                    .bounds(px + 220 + i * 26, py + 74, 22, 22)
                    .tooltip(Tooltip.create(Component.literal("槽 " + (i + 1) + "：点击选择物品")))
                    .build());
        }

        /* ===== 底部 ===== */
        addRenderableWidget(Button.builder(Component.literal("确定"), b -> apply())
                .bounds(px + 150, py + 190, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b ->
                minecraft.setScreen(parent)).bounds(px + 240, py + 190, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("恢复默认"), b -> {
            blockA = RangeOverlay.DEFAULT_A;
            blockB = RangeOverlay.DEFAULT_B;
            alphaBox.setValue("0.45");
            radiusBox.setValue("0.75");
            for (int i = 0; i < 4; i++) ORBIT[i] = ItemStack.EMPTY;
            ORBIT[0] = new ItemStack(Items.NETHER_STAR);
            orbitVisible = true;
            this.rebuildWidgets();
        }).bounds(px + 40, py + 190, 100, 20).build());
    }

    private Component blockLabel(Block b) {
        return Component.literal(new ItemStack(b).getHoverName().getString());
    }

    private Component orbitVisibleText() {
        return Component.literal("光环: " + (orbitVisible ? "开" : "关"));
    }

    private void apply() {
        alpha = parseFloat(alphaBox.getValue(), 0.45f, 0.05f, 1.0f);
        orbitRadius = parseFloat(radiusBox.getValue(), 0.75f, 0.3f, 2.0f);
        RangeOverlay.alpha = alpha;
        minecraft.setScreen(parent);
    }

    private static float parseFloat(String s, float def, float min, float max) {
        try {
            float v = Float.parseFloat(s.trim());
            return Math.max(min, Math.min(max, v));
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);
        super.render(gg, mouseX, mouseY, partialTick);

        gg.fill(px, py, px + 360, py + 220, 0xC0000000);

        gg.drawCenteredString(this.font, "显示设置", this.width / 2, py + 4, 0xFFD700);

        // 左列标题
        gg.drawString(this.font, "§e范围显示", px + 14, py + 2, 0xFFFFFF);
        gg.drawString(this.font, "方块 A", px + 14, py + 24, 0xFFFFFF);
        gg.drawString(this.font, "方块 B", px + 14, py + 50, 0xFFFFFF);
        gg.drawString(this.font, "透明度", px + 14, py + 76, 0xFFFFFF);

        // 右列标题
        gg.drawString(this.font, "§e光环", px + 210, py + 2, 0xFFFFFF);
        gg.drawString(this.font, "半径", px + 210, py + 50, 0xFFFFFF);

        // 方块图标预览
        gg.renderItem(new ItemStack(blockA), px + 52, py + 19);
        gg.renderItem(new ItemStack(blockB), px + 52, py + 45);

        // 物品槽内容
        for (int i = 0; i < 4; i++) {
            int x = px + 220 + i * 26;
            int y = py + 74;
            gg.fill(x - 1, y - 1, x + 23, y + 23, 0xFF3A3A3A);
            ItemStack st = ORBIT[i];
            if (!st.isEmpty()) {
                gg.renderItem(st, x + 3, y + 3);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}