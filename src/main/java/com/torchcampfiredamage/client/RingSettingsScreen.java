package com.torchcampfiredamage.client;

import com.torchcampfiredamage.block.WardSourceBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 星环设置（独立界面）：
 *  - 每个环可设：物品、半径、速度、起始角度、倾角
 *  - 可以添加新环、删除任意环
 *  - 环数量不设限制（硬上限 64，防止渲染卡死）
 */
public class RingSettingsScreen extends Screen {

    /** 客户端工作副本 */
    public static final class RingCfg {
        public ItemStack item = ItemStack.EMPTY;
        public String radius = "0.8";
        public String speed = "2.0";
        public String angle = "0";
        public String tilt = "0";

        public RingCfg() {}

        RingCfg(ItemStack it, float r, float s, float a, float t) {
            item = it == null ? ItemStack.EMPTY : it.copy();
            radius = String.format("%.2f", r);
            speed = String.format("%.2f", s);
            angle = String.format("%.0f", a);
            tilt = String.format("%.0f", t);
        }

        public RingCfg copy() {
            RingCfg c = new RingCfg();
            c.item = item.copy();
            c.radius = radius;
            c.speed = speed;
            c.angle = angle;
            c.tilt = tilt;
            return c;
        }
    }

    public static final List<RingCfg> RINGS = new ArrayList<>();

    static {
        // 默认 1 环：下界之星
        RingCfg r0 = new RingCfg();
        r0.item = new ItemStack(net.minecraft.world.item.Items.NETHER_STAR);
        RINGS.add(r0);
    }

    /** 从方块实体读取 */
    public static void loadFrom(WardSourceBlockEntity be) {
        RINGS.clear();
        int n = be.getRingCount();
        for (int i = 0; i < n; i++) {
            var r = be.getRing(i);
            RINGS.add(new RingCfg(r.item, r.radius, r.speed, r.angle, r.tilt));
        }
    }

    /** 转成 BE 的 Ring 列表 */
    public static List<WardSourceBlockEntity.Ring> toBeanRings() {
        List<WardSourceBlockEntity.Ring> list = new ArrayList<>();
        for (RingCfg c : RINGS) {
            WardSourceBlockEntity.Ring r = new WardSourceBlockEntity.Ring();
            r.item = c.item == null ? ItemStack.EMPTY : c.item.copy();
            r.radius = parse(c.radius, 0.8f);
            r.speed = parse(c.speed, 2.0f);
            r.angle = parse(c.angle, 0f);
            r.tilt = parse(c.tilt, 0f);
            list.add(r);
        }
        return list;
    }

    private static float parse(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /* ================= 布局 ================= */

    private final Screen parent;

    private static final int ROW_H = 20;
    private static final int ROW_GAP = 4;
    private static final int PANEL_W = 420;
    private static final int VISIBLE_ROWS = 6;

    private int px, py;
    private int scroll = 0;

    /** 每行的输入框（随滚动重建） */
    private final List<EditBox[]> rowBoxes = new ArrayList<>();

    public RingSettingsScreen(Screen parent) {
        super(Component.literal("星环设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        px = cx - PANEL_W / 2;
        py = Math.max(16, cy - 120);

        buildRows();
    }

    /** 重建可视区域的行控件 */
    private void buildRows() {
        this.clearWidgets();
        rowBoxes.clear();

        int startY = py + 40;

        // 顶部按钮：添加环
        addRenderableWidget(Button.builder(Component.literal("+ 添加一圈"), b -> {
            if (RINGS.size() < WardSourceBlockEntity.MAX_RINGS) {
                RingCfg c = new RingCfg();
                c.radius = String.format("%.2f", 0.8 + RINGS.size() * 0.3);
                c.angle = String.valueOf((RINGS.size() * 60) % 360);
                RINGS.add(c);
                // 滚动到末尾
                scroll = Math.max(0, RINGS.size() - VISIBLE_ROWS);
                buildRows();
            }
        }).bounds(px + 300, py + 8, 100, ROW_H)
                .tooltip(Tooltip.create(Component.literal("上限 " + WardSourceBlockEntity.MAX_RINGS + " 圈")))
                .build());

        // 滚动按钮
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
            if (scroll > 0) { scroll--; buildRows(); }
        }).bounds(px + 14, startY - 22, 22, ROW_H).build());

        addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
            if (scroll + VISIBLE_ROWS < RINGS.size()) { scroll++; buildRows(); }
        }).bounds(px + 40, startY - 22, 22, ROW_H).build());

        // 行
        int shown = Math.min(VISIBLE_ROWS, Math.max(0, RINGS.size() - scroll));
        for (int vi = 0; vi < shown; vi++) {
            int ri = scroll + vi;
            int y = startY + vi * (ROW_H + ROW_GAP);

            RingCfg cfg = RINGS.get(ri);

            // 删除按钮
            addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                if (ri < RINGS.size()) {
                    RINGS.remove(ri);
                    if (scroll > 0 && scroll >= RINGS.size()) scroll = Math.max(0, RINGS.size() - 1);
                    buildRows();
                }
            }).bounds(px + 14, y, ROW_H, ROW_H)
                    .tooltip(Tooltip.create(Component.literal("删除这一圈")))
                    .build());

            // 物品按钮
            addRenderableWidget(Button.builder(Component.literal(""), b ->
                    minecraft.setScreen(new ItemPickerScreen(this, ri)))
                    .bounds(px + 40, y, ROW_H, ROW_H)
                    .tooltip(Tooltip.create(Component.literal("点击选择物品")))
                    .build());

            EditBox[] boxes = new EditBox[4];

            boxes[0] = new EditBox(this.font, px + 90, y, 52, ROW_H, Component.literal("半径"));
            boxes[0].setValue(cfg.radius);
            boxes[0].setTooltip(Tooltip.create(Component.literal("距中心距离（方块）")));
            boxes[0].setResponder(v -> cfg.radius = v);
            addRenderableWidget(boxes[0]);

            boxes[1] = new EditBox(this.font, px + 148, y, 52, ROW_H, Component.literal("速度"));
            boxes[1].setValue(cfg.speed);
            boxes[1].setTooltip(Tooltip.create(Component.literal("度/tick，可正可负")));
            boxes[1].setResponder(v -> cfg.speed = v);
            addRenderableWidget(boxes[1]);

            boxes[2] = new EditBox(this.font, px + 206, y, 52, ROW_H, Component.literal("角度"));
            boxes[2].setValue(cfg.angle);
            boxes[2].setTooltip(Tooltip.create(Component.literal("起始角度，可正可负")));
            boxes[2].setResponder(v -> cfg.angle = v);
            addRenderableWidget(boxes[2]);

            boxes[3] = new EditBox(this.font, px + 264, y, 52, ROW_H, Component.literal("倾角"));
            boxes[3].setValue(cfg.tilt);
            boxes[3].setTooltip(Tooltip.create(Component.literal("倾角，可正可负")));
            boxes[3].setResponder(v -> cfg.tilt = v);
            addRenderableWidget(boxes[3]);

            rowBoxes.add(boxes);
        }

        // 底部
        int by = py + 40 + VISIBLE_ROWS * (ROW_H + ROW_GAP) + 8;

        addRenderableWidget(Button.builder(Component.literal("确定"), b -> {
            minecraft.setScreen(parent);
        }).bounds(px + 150, by, 80, ROW_H).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b -> {
            // 取消：从 BE 重新加载
            if (parent instanceof WardConfigScreen) {
                DisplaySettingsScreen.loadFrom(WardConfigScreen.lastBE);
                RingSettingsScreen.loadFrom(WardConfigScreen.lastBE);
            }
            minecraft.setScreen(parent);
        }).bounds(px + 236, by, 80, ROW_H).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dy > 0 && scroll > 0) {
            scroll--;
            buildRows();
            return true;
        } else if (dy < 0 && scroll + VISIBLE_ROWS < RINGS.size()) {
            scroll++;
            buildRows();
            return true;
        }
        return true;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);
        super.render(gg, mouseX, mouseY, partialTick);

        int panelH = 40 + VISIBLE_ROWS * (ROW_H + ROW_GAP) + 40;
        gg.fill(px, py, px + PANEL_W, py + panelH, 0xC0000000);

        gg.drawCenteredString(this.font, "星环设置（共 " + RINGS.size() + " 圈）",
                px + PANEL_W / 2, py + 12, 0xFFD700);

        int startY = py + 40;

        // 表头
        gg.drawString(this.font, "物品", px + 44, startY - 22 + 6, 0xAAAAAA);
        gg.drawString(this.font, "半径", px + 90, startY - 22 + 6, 0xAAAAAA);
        gg.drawString(this.font, "速度", px + 148, startY - 22 + 6, 0xAAAAAA);
        gg.drawString(this.font, "角度", px + 206, startY - 22 + 6, 0xAAAAAA);
        gg.drawString(this.font, "倾角", px + 264, startY - 22 + 6, 0xAAAAAA);

        int shown = Math.min(VISIBLE_ROWS, Math.max(0, RINGS.size() - scroll));

        if (RINGS.isEmpty()) {
            gg.drawCenteredString(this.font, "§7还没有任何环，点右上角「+ 添加一圈」",
                    px + PANEL_W / 2, startY + 40, 0x888888);
        }

        for (int vi = 0; vi < shown; vi++) {
            int ri = scroll + vi;
            int y = startY + vi * (ROW_H + ROW_GAP);

            // 环序号
            gg.drawString(this.font, String.valueOf(ri + 1), px + 78, y + 6, 0x888888);

            // 物品槽
            gg.fill(px + 39, y - 1, px + 61, y + ROW_H + 1, 0xFF3A3A3A);
            ItemStack st = RINGS.get(ri).item;
            if (!st.isEmpty()) {
                gg.renderItem(st, px + 42, y + 2);
            } else {
                gg.drawCenteredString(this.font, "§8空", px + 50, y + 6, 0x666666);
            }
        }

        // 滚动提示
        if (RINGS.size() > VISIBLE_ROWS) {
            String info = "显示 " + (scroll + 1) + "-" + (scroll + shown) + " / " + RINGS.size();
            gg.drawString(this.font, "§7" + info, px + 320, startY + VISIBLE_ROWS * (ROW_H + ROW_GAP) - 12, 0x888888);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}