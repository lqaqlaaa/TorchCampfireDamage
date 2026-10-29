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
 *  - 范围显示方块 / 透明度
 *  - 星环：最多 5 个环，每环可设物品、半径、速度、起始角度、倾角
 */
public class DisplaySettingsScreen extends Screen {

    private final Screen parent;

    /* ===== 范围显示 ===== */
    public static Block blockA = RangeOverlay.DEFAULT_A;
    public static Block blockB = RangeOverlay.DEFAULT_B;
    public static float alpha = 0.45f;

    /* ===== 星环（客户端工作副本） ===== */
    public static final class RingCfg {
        public ItemStack item = ItemStack.EMPTY;
        public String radius = "0.8";
        public String speed = "2.0";
        public String angle = "0";
        public String tilt = "0";

        RingCfg() {}
        RingCfg(ItemStack it, float r, float s, float a, float t) {
            item = it;
            radius = String.format("%.2f", r);
            speed = String.format("%.2f", s);
            angle = String.format("%.0f", a);
            tilt = String.format("%.0f", t);
        }
    }

    /* ===== 中心物品 ===== */
    public static ItemStack centerItem = new ItemStack(Items.NETHER_STAR);
    public static String centerScale = "0.9";
    public static String centerSpin = "1.5";

    public static final RingCfg[] RINGS = new RingCfg[WardSourceBlockEntity.MAX_RINGS];
    public static boolean orbitVisible = true;

    static {
        for (int i = 0; i < WardSourceBlockEntity.MAX_RINGS; i++) RINGS[i] = new RingCfg();
        RINGS[0].item = new ItemStack(Items.NETHER_STAR);
        RINGS[0].radius = "0.8"; RINGS[0].speed = "2.0"; RINGS[0].angle = "0";  RINGS[0].tilt = "0";
        RINGS[1].radius = "1.1"; RINGS[1].speed = "-1.6"; RINGS[1].angle = "72"; RINGS[1].tilt = "0";
        RINGS[2].radius = "1.4"; RINGS[2].speed = "1.2"; RINGS[2].angle = "144"; RINGS[2].tilt = "20";
    }

    /** 从方块实体读取当前配置 */
    public static void loadFrom(WardSourceBlockEntity be) {
        orbitVisible = be.isOrbitVisible();
        centerItem = be.getCenterItem().copy();
        centerScale = String.format("%.2f", be.getCenterScale());
        centerSpin = String.format("%.2f", be.getCenterSpin());
        for (int i = 0; i < WardSourceBlockEntity.MAX_RINGS; i++) {
            var r = be.getRing(i);
            RINGS[i] = new RingCfg(r.item, r.radius, r.speed, r.angle, r.tilt);
        }
    }

    /* ===== 布局 ===== */
    private int px, py;
    private static final int ROW_H = 20;
    private static final int PANEL_W = 400;

    private EditBox alphaBox;
    private EditBox centerScaleBox;
    private EditBox centerSpinBox;
    private final EditBox[] radiusBoxes = new EditBox[WardSourceBlockEntity.MAX_RINGS];
    private final EditBox[] speedBoxes  = new EditBox[WardSourceBlockEntity.MAX_RINGS];
    private final EditBox[] angleBoxes  = new EditBox[WardSourceBlockEntity.MAX_RINGS];
    private final EditBox[] tiltBoxes   = new EditBox[WardSourceBlockEntity.MAX_RINGS];

    public DisplaySettingsScreen(Screen parent) {
        super(Component.literal("显示设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        px = cx - PANEL_W / 2;
        py = Math.max(20, cy - 130);

        /* ===== 顶部：范围显示 ===== */
        addRenderableWidget(Button.builder(blockLabel(blockA), b ->
                minecraft.setScreen(new BlockPickerScreen(this, true)))
                .bounds(px + 90, py + 22, 110, ROW_H).build());

        addRenderableWidget(Button.builder(blockLabel(blockB), b ->
                minecraft.setScreen(new BlockPickerScreen(this, false)))
                .bounds(px + 220, py + 22, 110, ROW_H).build());

        alphaBox = new EditBox(this.font, px + 90, py + 46, 55, ROW_H, Component.literal("透明度"));
        alphaBox.setValue(String.format("%.2f", alpha));
        alphaBox.setTooltip(Tooltip.create(Component.literal("0.05 ~ 1.0")));
        addRenderableWidget(alphaBox);

        addRenderableWidget(Button.builder(Component.literal("0.2"), b -> alphaBox.setValue("0.2"))
                .bounds(px + 150, py + 46, 36, ROW_H).build());
        addRenderableWidget(Button.builder(Component.literal("0.45"), b -> alphaBox.setValue("0.45"))
                .bounds(px + 190, py + 46, 40, ROW_H).build());

        /* ===== 中心物品 ===== */
        int cY = py + 72;

        addRenderableWidget(Button.builder(Component.literal(""), b ->
                minecraft.setScreen(new ItemPickerScreen(this, 99)))
                .bounds(px + 90, cY, ROW_H, ROW_H)
                .tooltip(Tooltip.create(Component.literal("点击选择中心物品")))
                .build());

        centerScaleBox = new EditBox(this.font, px + 150, cY, 52, ROW_H, Component.literal("大小"));
        centerScaleBox.setValue(centerScale);
        centerScaleBox.setTooltip(Tooltip.create(Component.literal("0.2 ~ 2.0")));
        centerScaleBox.setResponder(v -> centerScale = v);
        addRenderableWidget(centerScaleBox);

        centerSpinBox = new EditBox(this.font, px + 208, cY, 52, ROW_H, Component.literal("转速"));
        centerSpinBox.setValue(centerSpin);
        centerSpinBox.setTooltip(Tooltip.create(Component.literal("-20 ~ 20 度/tick")));
        centerSpinBox.setResponder(v -> centerSpin = v);
        addRenderableWidget(centerSpinBox);

        /* ===== 星环 ===== */
        int ringTop = py + 122;

        // 表头
        // 列：物品 | 半径 | 速度 | 角度 | 倾角

        for (int i = 0; i < WardSourceBlockEntity.MAX_RINGS; i++) {
            final int idx = i;
            int y = ringTop + i * (ROW_H + 4);

            // 物品按钮
            addRenderableWidget(Button.builder(Component.literal(""), b ->
                    minecraft.setScreen(new ItemPickerScreen(this, idx)))
                    .bounds(px + 46, y, ROW_H, ROW_H)
                    .tooltip(Tooltip.create(Component.literal("环 " + (i + 1) + "：点击选择物品")))
                    .build());

            radiusBoxes[i] = new EditBox(this.font, px + 100, y, 52, ROW_H, Component.literal("半径"));
            radiusBoxes[i].setValue(RINGS[i].radius);
            radiusBoxes[i].setTooltip(Tooltip.create(Component.literal("0.3 ~ 3.0")));
            radiusBoxes[i].setResponder(v -> RINGS[idx].radius = v);
            addRenderableWidget(radiusBoxes[i]);

            speedBoxes[i] = new EditBox(this.font, px + 158, y, 52, ROW_H, Component.literal("速度"));
            speedBoxes[i].setValue(RINGS[i].speed);
            speedBoxes[i].setTooltip(Tooltip.create(Component.literal("-20 ~ 20 度/tick\n负数为反向")));
            speedBoxes[i].setResponder(v -> RINGS[idx].speed = v);
            addRenderableWidget(speedBoxes[i]);

            angleBoxes[i] = new EditBox(this.font, px + 216, y, 52, ROW_H, Component.literal("角度"));
            angleBoxes[i].setValue(RINGS[i].angle);
            angleBoxes[i].setTooltip(Tooltip.create(Component.literal("起始角度 0 ~ 360")));
            angleBoxes[i].setResponder(v -> RINGS[idx].angle = v);
            addRenderableWidget(angleBoxes[i]);

            tiltBoxes[i] = new EditBox(this.font, px + 274, y, 52, ROW_H, Component.literal("倾角"));
            tiltBoxes[i].setValue(RINGS[i].tilt);
            tiltBoxes[i].setTooltip(Tooltip.create(Component.literal("0 = 水平\n90 = 垂直")));
            tiltBoxes[i].setResponder(v -> RINGS[idx].tilt = v);
            addRenderableWidget(tiltBoxes[i]);
        }

        /* ===== 底部按钮 ===== */
        int by = ringTop + WardSourceBlockEntity.MAX_RINGS * (ROW_H + 4) + 12;

        addRenderableWidget(Button.builder(orbitVisibleText(), b -> {
            orbitVisible = !orbitVisible;
            b.setMessage(orbitVisibleText());
        }).bounds(px + 40, by, 96, ROW_H).build());

        addRenderableWidget(Button.builder(Component.literal("确定"), b -> apply())
                .bounds(px + 150, by, 80, ROW_H).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b ->
                minecraft.setScreen(parent)).bounds(px + 236, by, 80, ROW_H).build());
    }

    private Component blockLabel(Block b) {
        return Component.literal(new ItemStack(b).getHoverName().getString());
    }

    private Component orbitVisibleText() {
        return Component.literal("星环: " + (orbitVisible ? "开" : "关"));
    }

    private void apply() {
        alpha = parse(alphaBox.getValue(), 0.45f, 0.05f, 1.0f);
        RangeOverlay.alpha = alpha;
        minecraft.setScreen(parent);
    }

    private static float parse(String s, float def, float min, float max) {
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

        int panelH = 122 + WardSystemHeight() + 46;
        gg.fill(px, py - 4, px + PANEL_W, py + panelH, 0xC0000000);

        gg.drawCenteredString(this.font, "显示设置", px + PANEL_W / 2, py + 2, 0xFFD700);

        /* ===== 范围显示 ===== */
        gg.drawString(this.font, "§e范围显示", px + 12, py + 10, 0xFFFFFF);
        gg.drawString(this.font, "方块", px + 12, py + 28, 0xFFFFFF);
        gg.drawString(this.font, "透明度", px + 12, py + 52, 0xFFFFFF);
        gg.renderItem(new ItemStack(blockA), px + 78, py + 23);
        gg.renderItem(new ItemStack(blockB), px + 208, py + 23);

        /* ===== 星环 ===== */
        gg.drawString(this.font, "§e中心物品", px + 12, py + 78, 0xFFFFFF);
        gg.fill(px + 89, py + 71, px + 111, py + 93, 0xFF3A3A3A);
        if (!centerItem.isEmpty()) {
            gg.renderItem(centerItem, px + 92, py + 74);
        }
        gg.drawString(this.font, "§e星环（最多 5 环）", px + 12, py + 104, 0xFFFFFF);

        int ringTop = py + 122;
        // 表头
        gg.drawString(this.font, "物品", px + 46, ringTop - 12, 0xAAAAAA);
        gg.drawString(this.font, "半径", px + 100, ringTop - 12, 0xAAAAAA);
        gg.drawString(this.font, "速度", px + 158, ringTop - 12, 0xAAAAAA);
        gg.drawString(this.font, "角度", px + 216, ringTop - 12, 0xAAAAAA);
        gg.drawString(this.font, "倾角", px + 274, ringTop - 12, 0xAAAAAA);

        for (int i = 0; i < WardSourceBlockEntity.MAX_RINGS; i++) {
            int y = ringTop + i * (ROW_H + 4);

            // 环序号
            gg.drawString(this.font, String.valueOf(i + 1), px + 28, y + 6, 0x888888);

            // 物品槽底
            gg.fill(px + 45, y - 1, px + 67, y + ROW_H + 1, 0xFF3A3A3A);
            ItemStack st = RINGS[i].item;
            if (!st.isEmpty()) {
                gg.renderItem(st, px + 48, y + 2);
            }
        }
    }

    private static int WardSystemHeight() {
        return WardSourceBlockEntity.MAX_RINGS * (ROW_H + 4);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}