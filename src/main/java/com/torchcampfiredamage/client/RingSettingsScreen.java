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
 * 星环设置 —— 一条星环一页，左右切换。
 * <p>
 * 布局：
 * <pre>
 *   ◀  第 2 / 5 圈            [+ 添加] [× 删除]  ▶
 *   ┌───────────── 参数 ─────────────┐
 *   │ 物品  [槽]                       │
 *   │ 半径  [  ]  速度  [  ]  角度 [ ] │
 *   │ 倾斜X [  ]  倾斜Y [  ]  倾斜Z[ ] │
 *   └─────────────────────────────────┘
 *   ┌───────────── 外观 ─────────────┐
 *   │ 颜色 [色]  透明度[ ]  线宽 [ ]   │
 *   │ 样式 [按钮]  间距 [ ]            │
 *   └─────────────────────────────────┘
 *                [确定] [取消]
 * </pre>
 * 倾斜说明：X = 前后俯仰，Y = 水平自转，Z = 左右侧倾。三个可叠加。
 */
public class RingSettingsScreen extends Screen {

    /* ================= 数据 ================= */

    public static final class RingCfg {
        public ItemStack item = ItemStack.EMPTY;
        public String radius = "0.8";
        public String speed = "2.0";
        public String angle = "0";
        /** 绕 X 轴倾斜（前后俯仰） */
        public String tiltX = "0";
        /** 绕 Y 轴倾斜（水平自转） */
        public String tiltY = "0";
        /** 绕 Z 轴倾斜（左右侧倾） */
        public String tiltZ = "0";
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
            tiltX = String.format("%.0f", r.tiltX);
            tiltY = String.format("%.0f", r.tiltY);
            tiltZ = String.format("%.0f", r.tiltZ);
            color = r.color;
            alpha = String.format("%.2f", r.alpha);
            width = String.format("%.3f", r.width);
            style = r.style;
            spacing = String.format("%.2f", r.spacing);
        }
    }

    public static final List<RingCfg> RINGS = new ArrayList<>();

    /** 当前查看第几圈（0 开始） */
    public static int page = 0;

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
        page = 0;
    }

    public static List<WardSourceBlockEntity.Ring> toBeanRings() {
        List<WardSourceBlockEntity.Ring> list = new ArrayList<>();
        for (RingCfg c : RINGS) {
            WardSourceBlockEntity.Ring r = new WardSourceBlockEntity.Ring();
            r.item = c.item == null ? ItemStack.EMPTY : c.item.copy();
            r.radius = parse(c.radius, 0.8f);
            r.speed = parse(c.speed, 2.0f);
            r.angle = parse(c.angle, 0f);
            r.tiltX = parse(c.tiltX, 0f);
            r.tiltY = parse(c.tiltY, 0f);
            r.tiltZ = parse(c.tiltZ, 0f);
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

    private static final int ROW_H = 20;
    private static final int LABEL_W = 52;     // 标签宽度
    private static final int FIELD_H = 20;
    private static final int PANEL_W = 400;

    private int px, py;

    public RingSettingsScreen(Screen parent) {
        super(Component.literal("星环设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.px = this.width / 2 - PANEL_W / 2;
        this.py = Math.max(10, (this.height - panelHeight()) / 2);
        if (page >= RINGS.size()) page = Math.max(0, RINGS.size() - 1);
        buildPage();
    }

    /** 面板总高 */
    private int panelHeight() {
        // 标题区 44 + 参数区 3行 + 外观区 2行 + 底部 44
        return 44 + (ROW_H + 8) * 5 + 44;
    }

    private void buildPage() {
        this.clearWidgets();

        int top = py + 44;

        /* ---------- 顶部：切换 + 添加 + 删除 ---------- */
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
            if (page > 0) {
                page--;
                buildPage();
            }
        }).bounds(px + 12, py + 14, 26, 20)
                .tooltip(Tooltip.create(Component.literal("上一圈")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            if (page + 1 < RINGS.size()) {
                page++;
                buildPage();
            }
        }).bounds(px + 44, py + 14, 26, 20)
                .tooltip(Tooltip.create(Component.literal("下一圈")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("+ 添加一圈"), b -> {
            if (RINGS.size() < WardSourceBlockEntity.MAX_RINGS) {
                RingCfg c = new RingCfg();
                c.radius = String.format("%.2f", 0.8 + RINGS.size() * 0.3);
                c.angle = String.valueOf((RINGS.size() * 60) % 360);
                int[] palette = {0x66DDFF, 0xCC66FF, 0xFFD966, 0x66FF88, 0xFF6666, 0xFFFFFF};
                c.color = palette[RINGS.size() % palette.length];
                RINGS.add(c);
                page = RINGS.size() - 1;   // 直接跳到新圈
                pushToServer();
                buildPage();
            }
        }).bounds(px + PANEL_W - 200, py + 14, 92, 20)
                .tooltip(Tooltip.create(Component.literal("上限 " + WardSourceBlockEntity.MAX_RINGS + " 圈")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("× 删除本圈"), b -> {
            if (page < RINGS.size()) {
                RINGS.remove(page);
                if (page >= RINGS.size()) page = Math.max(0, RINGS.size() - 1);
                pushToServer();
                buildPage();
            }
        }).bounds(px + PANEL_W - 100, py + 14, 88, 20)
                .tooltip(Tooltip.create(Component.literal("删除当前这一圈")))
                .build());

        if (RINGS.isEmpty()) {
            // 空状态只保留底部按钮
            buildFooter();
            return;
        }

        RingCfg cfg = RINGS.get(page);

        /* ---------- 第 1 行：物品 ---------- */
        int y = top;
        int fx = px + 12 + LABEL_W;

        addRenderableWidget(Button.builder(Component.literal(""), b ->
                minecraft.setScreen(new ItemPickerScreen(this, page)))
                .bounds(fx, y, 20, ROW_H)
                .tooltip(Tooltip.create(Component.literal("点击选择环上的物品")))
                .build());

        /* ---------- 第 2 行：半径 / 速度 / 角度 ---------- */
        y += ROW_H + 8;
        int colW = 76;
        int gap = (PANEL_W - 24 - 3 * colW) / 2;

        addEdit(fx, y, colW, cfg.radius, "半径", "距中心距离，如 0.8", v -> cfg.radius = v);
        addEdit(fx + colW + gap, y, colW, cfg.speed, "速度", "度/tick，可正可负", v -> cfg.speed = v);
        addEdit(fx + 2 * (colW + gap), y, colW, cfg.angle, "角度", "起始角度，可正可负", v -> cfg.angle = v);

        /* ---------- 第 3 行：倾斜 X / Y / Z ---------- */
        y += ROW_H + 8;
        addEdit(fx, y, colW, cfg.tiltX, "倾斜X", "前后俯仰（绕X轴），-180 ~ 180", v -> cfg.tiltX = v);
        addEdit(fx + colW + gap, y, colW, cfg.tiltY, "倾斜Y", "水平自转（绕Y轴），-180 ~ 180", v -> cfg.tiltY = v);
        addEdit(fx + 2 * (colW + gap), y, colW, cfg.tiltZ, "倾斜Z", "左右侧倾（绕Z轴），-180 ~ 180", v -> cfg.tiltZ = v);

        /* ---------- 第 4 行：颜色 / 透明度 / 线宽 ---------- */
        y += ROW_H + 12;
        addRenderableWidget(Button.builder(Component.literal("颜色"), b ->
                minecraft.setScreen(new ColorPickerScreen(this, cfg.color,
                        parse(cfg.alpha, 0.55f), (rgb, a) -> {
                    cfg.color = rgb;
                    cfg.alpha = String.format("%.2f", a);
                })))
                .bounds(fx, y, 44, ROW_H)
                .tooltip(Tooltip.create(Component.literal("点击打开颜色轮盘")))
                .build());

        addEdit(fx + 52, y, 62, cfg.alpha, "透明度", "0 ~ 1", v -> cfg.alpha = v);
        addEdit(fx + 122, y, 62, cfg.width, "线宽", "方块单位，如 0.03", v -> cfg.width = v);

        /* ---------- 第 5 行：样式 / 间距 ---------- */
        y += ROW_H + 8;
        addRenderableWidget(Button.builder(styleText(cfg.style), b -> {
            cfg.style = (cfg.style + 1) % 3;
            b.setMessage(styleText(cfg.style));
        }).bounds(fx, y, 76, ROW_H)
                .tooltip(Tooltip.create(Component.literal("连续 → 拖尾 → 虚线")))
                .build());

        addEdit(fx + 84, y, 68, cfg.spacing, "间距", "虚线/拖尾间距，0.05 ~ 1", v -> cfg.spacing = v);

        buildFooter();
    }

    private void buildFooter() {
        int by = py + panelHeight() - 34;
        int bw = 76;
        int gap2 = 8;
        int bx = px + (PANEL_W - (bw * 2 + gap2)) / 2;

        addRenderableWidget(Button.builder(Component.literal("确定"), b -> {
            pushToServer();
            minecraft.setScreen(parent);
        }).bounds(bx, by, bw, 20).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b -> {
            if (WardConfigScreen.lastBE != null) {
                RingSettingsScreen.loadFrom(WardConfigScreen.lastBE);
            }
            minecraft.setScreen(parent);
        }).bounds(bx + bw + gap2, by, bw, 20).build());
    }

    private void pushToServer() {
        if (WardConfigScreen.lastBE != null) {
            WardConfigScreen.sendOrbit(WardConfigScreen.lastBE.getBlockPos());
        }
    }

    private void addEdit(int x, int y, int w, String value, String label,
                         String tip, java.util.function.Consumer<String> setter) {
        EditBox box = new EditBox(this.font, x, y, w, FIELD_H, Component.literal(label));
        box.setValue(value);
        box.setTooltip(Tooltip.create(Component.literal(tip)));
        box.setResponder(setter);
        addRenderableWidget(box);
    }

    private String styleTextStr(int style) {
        switch (style) {
            case 1: return "拖尾";
            case 2: return "虚线";
            default: return "连续";
        }
    }

    private Component styleText(int style) {
        return Component.literal(styleTextStr(style));
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);
        super.render(gg, mouseX, mouseY, partialTick);

        int panelH = panelHeight();
        gg.fill(px, py, px + PANEL_W, py + panelH, 0xD0000000);

        // 标题
        gg.drawCenteredString(this.font, "星环设置", px + PANEL_W / 2 - 40, py + 20, 0xFFD700);

        if (RINGS.isEmpty()) {
            gg.drawCenteredString(this.font, "§7还没有任何圈，点右上角「+ 添加一圈」",
                    px + PANEL_W / 2, py + panelH / 2, 0x888888);
            return;
        }

        // 页码
        gg.drawCenteredString(this.font, "第 " + (page + 1) + " / " + RINGS.size() + " 圈",
                px + PANEL_W / 2 - 40, py + 20, 0xFFD700);

        RingCfg cfg = RINGS.get(page);
        int top = py + 44;
        int fx = px + 12 + LABEL_W;
        int labelX = px + 12;

        /* 第 1 行：物品 */
        gg.drawString(this.font, "物品", labelX, top + 6, 0xAAAAAA);
        gg.fill(fx - 1, top - 1, fx + 21, top + ROW_H + 1, 0xFF3A3A3A);
        if (!cfg.item.isEmpty()) {
            gg.renderItem(cfg.item, fx + 2, top + 3);
        } else {
            gg.drawCenteredString(this.font, "§8空", fx + 10, top + 6, 0x666666);
        }
        if (!cfg.item.isEmpty()) {
            gg.drawString(this.font, cfg.item.getHoverName().getString(),
                    fx + 28, top + 6, 0xDDDDDD);
        }

        /* 第 2 行：半径 / 速度 / 角度 */
        int y = top + ROW_H + 8;
        int colW = 76;
        int gap = (PANEL_W - 24 - 3 * colW) / 2;
        gg.drawString(this.font, "半径", labelX, y + 6, 0xAAAAAA);
        gg.drawString(this.font, "速度", fx + colW + gap - 0, y - 11, 0xAAAAAA);
        gg.drawString(this.font, "角度", fx + 2 * (colW + gap) - 0, y - 11, 0xAAAAAA);

        /* 第 3 行：倾斜 X / Y / Z */
        y += ROW_H + 8;
        gg.drawString(this.font, "倾斜X", labelX, y + 6, 0xAAAAAA);
        gg.drawString(this.font, "倾斜Y", fx + colW + gap - 0, y - 11, 0xAAAAAA);
        gg.drawString(this.font, "倾斜Z", fx + 2 * (colW + gap) - 0, y - 11, 0xAAAAAA);

        /* 分隔线 + 外观小标题 */
        y += ROW_H + 12;
        gg.fill(px + 12, y - 8, px + PANEL_W - 12, y - 7, 0x40FFFFFF);
        gg.drawString(this.font, "§7外观", labelX, y - 20, 0x888888);

        /* 颜色预览块 */
        gg.fill(fx - 1, y - 1, fx + 43, y + ROW_H + 1, 0xFF000000 | cfg.color);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        // 滚轮也能翻圈
        if (dy > 0 && page > 0) {
            page--;
            buildPage();
            return true;
        } else if (dy < 0 && page + 1 < RINGS.size()) {
            page++;
            buildPage();
            return true;
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}