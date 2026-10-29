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
 *  - 每圈可设：物品、半径、速度、起始角度、倾角
 *  - 外观：颜色（RGB 轮盘）、透明度、线宽、样式（连续/拖尾/虚线）、间距
 *  - 可自由添加 / 删除圈
 */
public class RingSettingsScreen extends Screen {

    /* ================= 数据 ================= */

    public static final class RingCfg {
        public ItemStack item = ItemStack.EMPTY;
        public String radius = "0.8";
        public String speed = "2.0";
        public String angle = "0";
        public String tilt = "0";
        public int color = 0x66DDFF;
        public String alpha = "0.55";
        public String width = "0.030";
        public int style = 0;
        public String spacing = "0.35";

        public RingCfg() {}

        RingCfg(WardSourceBlockEntity.Ring r) {
            item = r.item == null ? ItemStack.EMPTY : r.item.copy();
            radius = String.format("%.2f", r.radius);
            speed = String.format("%.2f", r.speed);
            angle = String.format("%.0f", r.angle);
            tilt = String.format("%.0f", r.tilt);
            color = r.color;
            alpha = String.format("%.2f", r.alpha);
            width = String.format("%.3f", r.width);
            style = r.style;
            spacing = String.format("%.2f", r.spacing);
        }
    }

    public static final List<RingCfg> RINGS = new ArrayList<>();

    static {
        RingCfg r0 = new RingCfg();
        r0.item = new ItemStack(net.minecraft.world.item.Items.NETHER_STAR);
        RINGS.add(r0);
    }

    public static void loadFrom(WardSourceBlockEntity be) {
        RINGS.clear();
        int n = be.getRingCount();
        for (int i = 0; i < n; i++) {
            RINGS.add(new RingCfg(be.getRing(i)));
        }
    }

    public static List<WardSourceBlockEntity.Ring> toBeanRings() {
        List<WardSourceBlockEntity.Ring> list = new ArrayList<>();
        for (RingCfg c : RINGS) {
            WardSourceBlockEntity.Ring r = new WardSourceBlockEntity.Ring();
            r.item = c.item == null ? ItemStack.EMPTY : c.item.copy();
            r.radius = parse(c.radius, 0.8f);
            r.speed = parse(c.speed, 2.0f);
            r.angle = parse(c.angle, 0f);
            r.tilt = parse(c.tilt, 0f);
            r.color = c.color;
            r.alpha = Math.max(0f, Math.min(1f, parse(c.alpha, 0.55f)));
            r.width = parse(c.width, 0.03f);
            r.style = c.style;
            r.spacing = parse(c.spacing, 0.35f);
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

    private static final int ROW_H = 22;
    private static final int ROW_GAP = 30;      // 每行两排（参数 + 外观）
    private static final int PANEL_W = 460;
    private static final int VISIBLE_ROWS = 4;

    private int px, py;
    private int scroll = 0;

    public RingSettingsScreen(Screen parent) {
        super(Component.literal("星环设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.px = this.width / 2 - PANEL_W / 2;
        this.py = Math.max(16, this.height / 2 - 130);
        buildRows();
    }

    private void buildRows() {
        this.clearWidgets();

        int startY = py + 50;

        /* 顶部：添加 */
        addRenderableWidget(Button.builder(Component.literal("+ 添加一圈"), b -> {
            if (RINGS.size() < WardSourceBlockEntity.MAX_RINGS) {
                RingCfg c = new RingCfg();
                c.radius = String.format("%.2f", 0.8 + RINGS.size() * 0.3);
                c.angle = String.valueOf((RINGS.size() * 60) % 360);
                // 自动给不同颜色
                int[] palette = {0x66DDFF, 0xCC66FF, 0xFFD966, 0x66FF88, 0xFF6666, 0xFFFFFF};
                c.color = palette[RINGS.size() % palette.length];
                RINGS.add(c);
                scroll = Math.max(0, RINGS.size() - VISIBLE_ROWS);
                buildRows();
            }
        }).bounds(px + 340, py + 12, 110, 20)
                .tooltip(Tooltip.create(Component.literal("上限 " + WardSourceBlockEntity.MAX_RINGS + " 圈")))
                .build());

        /* 翻页 */
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
            if (scroll > 0) { scroll--; buildRows(); }
        }).bounds(px + 14, startY - 26, 22, 18).build());

        addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
            if (scroll + VISIBLE_ROWS < RINGS.size()) { scroll++; buildRows(); }
        }).bounds(px + 40, startY - 26, 22, 18).build());

        int shown = Math.min(VISIBLE_ROWS, Math.max(0, RINGS.size() - scroll));

        for (int vi = 0; vi < shown; vi++) {
            final int ri = scroll + vi;
            final RingCfg cfg = RINGS.get(ri);
            int y = startY + vi * ROW_GAP * 2;

            // ---------- 第一排：删除 / 物品 / 半径 / 速度 / 角度 / 倾角 ----------
            addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                if (ri < RINGS.size()) {
                    RINGS.remove(ri);
                    if (scroll > 0 && scroll >= RINGS.size()) scroll = Math.max(0, RINGS.size() - 1);
                    buildRows();
                }
            }).bounds(px + 14, y, 20, ROW_H)
                    .tooltip(Tooltip.create(Component.literal("删除这一圈")))
                    .build());

            addRenderableWidget(Button.builder(Component.literal(""), b ->
                    minecraft.setScreen(new ItemPickerScreen(this, ri)))
                    .bounds(px + 38, y, 20, ROW_H)
                    .tooltip(Tooltip.create(Component.literal("选择物品")))
                    .build());

            addEdit(px + 86, y, 52, cfg.radius, "半径", "距中心距离", v -> cfg.radius = v);
            addEdit(px + 142, y, 52, cfg.speed, "速度", "度/tick，可正可负", v -> cfg.speed = v);
            addEdit(px + 198, y, 52, cfg.angle, "角度", "起始角度，可正可负", v -> cfg.angle = v);
            addEdit(px + 254, y, 52, cfg.tilt, "倾角", "倾角，可正可负", v -> cfg.tilt = v);

            // ---------- 第二排：颜色 / 透明度 / 线宽 / 样式 / 间距 ----------
            int y2 = y + ROW_H + 4;

            // 颜色按钮（显示当前色）
            addRenderableWidget(Button.builder(Component.literal("颜色"), b ->
                    minecraft.setScreen(new ColorPickerScreen(this, cfg.color,
                            parse(cfg.alpha, 0.55f), (rgb, a) -> {
                        cfg.color = rgb;
                        cfg.alpha = String.format("%.2f", a);
                    })))
                    .bounds(px + 38, y2, 40, ROW_H)
                    .tooltip(Tooltip.create(Component.literal("点击打开颜色轮盘")))
                    .build());

            addEdit(px + 82, y2, 46, cfg.alpha, "透明度", "0 ~ 1", v -> cfg.alpha = v);
            addEdit(px + 132, y2, 54, cfg.width, "线宽", "方块单位，如 0.03", v -> cfg.width = v);

            // 样式按钮（循环切换）
            addRenderableWidget(Button.builder(styleText(cfg.style), b -> {
                cfg.style = (cfg.style + 1) % 3;
                b.setMessage(styleText(cfg.style));
                buildRows();
            }).bounds(px + 190, y2, 76, ROW_H)
                    .tooltip(Tooltip.create(Component.literal("连续 → 拖尾 → 虚线")))
                    .build());

            addEdit(px + 270, y2, 52, cfg.spacing, "间距", "虚线/拖尾间距", v -> cfg.spacing = v);
        }

        /* 底部 */
        int by = startY + VISIBLE_ROWS * ROW_GAP * 2 + 4;
        addRenderableWidget(Button.builder(Component.literal("确定"), b -> {
            minecraft.setScreen(parent);
        }).bounds(px + 160, by, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> {
            if (WardConfigScreen.lastBE != null) {
                RingSettingsScreen.loadFrom(WardConfigScreen.lastBE);
            }
            minecraft.setScreen(parent);
        }).bounds(px + 246, by, 80, 20).build());
    }

    private void addEdit(int x, int y, int w, String value, String label,
                         String tip, java.util.function.Consumer<String> setter) {
        EditBox box = new EditBox(this.font, x, y, w, ROW_H, Component.literal(label));
        box.setValue(value);
        box.setTooltip(Tooltip.create(Component.literal(tip)));
        box.setResponder(setter);
        addRenderableWidget(box);
    }

    private Component styleText(int style) {
        switch (style) {
            case 1: return Component.literal("拖尾");
            case 2: return Component.literal("虚线");
            default: return Component.literal("连续");
        }
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

        int panelH = 50 + VISIBLE_ROWS * ROW_GAP * 2 + 40;
        gg.fill(px, py, px + PANEL_W, py + panelH, 0xC0000000);

        gg.drawCenteredString(this.font, "星环设置（共 " + RINGS.size() + " 圈）",
                px + PANEL_W / 2, py + 14, 0xFFD700);

        int startY = py + 50;

        // 表头
        int hy = startY - 22;
        gg.drawString(this.font, "物品", px + 38, hy, 0xAAAAAA);
        gg.drawString(this.font, "半径", px + 86, hy, 0xAAAAAA);
        gg.drawString(this.font, "速度", px + 142, hy, 0xAAAAAA);
        gg.drawString(this.font, "角度", px + 198, hy, 0xAAAAAA);
        gg.drawString(this.font, "倾角", px + 254, hy, 0xAAAAAA);

        int shown = Math.min(VISIBLE_ROWS, Math.max(0, RINGS.size() - scroll));

        if (RINGS.isEmpty()) {
            gg.drawCenteredString(this.font, "§7还没有任何圈，点右上角「+ 添加一圈」",
                    px + PANEL_W / 2, startY + 40, 0x888888);
        }

        for (int vi = 0; vi < shown; vi++) {
            int ri = scroll + vi;
            RingCfg c = RINGS.get(ri);
            int y = startY + vi * ROW_GAP * 2;
            int y2 = y + ROW_H + 4;

            // 序号
            gg.drawString(this.font, String.valueOf(ri + 1), px + 62, y + 7, 0x888888);

            // 物品槽
            gg.fill(px + 37, y - 1, px + 59, y + ROW_H + 1, 0xFF3A3A3A);
            if (!c.item.isEmpty()) {
                gg.renderItem(c.item, px + 40, y + 3);
            } else {
                gg.drawCenteredString(this.font, "§8空", px + 48, y + 7, 0x666666);
            }

            // 颜色预览块
            gg.fill(px + 14, y2, px + 34, y2 + ROW_H, 0xFF000000 | c.color);
        }

        // 滚动提示
        if (RINGS.size() > VISIBLE_ROWS) {
            gg.drawString(this.font, "§7显示 " + (scroll + 1) + "-" + (scroll + shown)
                            + " / " + RINGS.size(),
                    px + 330, startY - 20, 0x888888);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}