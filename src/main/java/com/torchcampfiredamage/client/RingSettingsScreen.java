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
 * 布局完全自适应（见 {@link GuiLayout}）：
 * <ul>
 *   <li>不画半透明黑底，与主界面风格统一</li>
 *   <li>标签在左、控件在右，垂直居中对齐</li>
 *   <li>宽屏双列、窄屏单列，尺寸随屏幕压缩</li>
 * </ul>
 */
public class RingSettingsScreen extends Screen {

    /* ================= 数据 ================= */

    public static final class RingCfg {
        public ItemStack item = ItemStack.EMPTY;
        public String radius = "0.8";
        public String speed = "2.0";
        public String angle = "0";
        public String tiltX = "0";
        public String tiltY = "0";
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
        try { return Float.parseFloat(s.trim()); } catch (Exception e) { return def; }
    }

    /* ================= 布局 ================= */

    private final Screen parent;

    /** 一行的字段定义 */
    private static final class Field {
        final String label;
        final String tip;
        final boolean isColor;
        final boolean isStyle;
        final int row;      // 第几行
        final int col;      // 第几列（0/1）
        final java.util.function.Consumer<String> setter;
        final java.util.function.Function<RingCfg, String> getter;

        Field(String label, String tip, int row, int col,
              java.util.function.Function<RingCfg, String> getter,
              java.util.function.Consumer<String> setter,
              boolean isColor, boolean isStyle) {
            this.label = label; this.tip = tip; this.row = row; this.col = col;
            this.getter = getter; this.setter = setter;
            this.isColor = isColor; this.isStyle = isStyle;
        }
    }

    private static final List<Field> FIELDS = new ArrayList<>();
    static {
        FIELDS.add(new Field("半径", "距中心距离，如 0.8", 0, 0, c -> c.radius, v -> {}, false, false));
        FIELDS.add(new Field("速度", "度/tick，可正可负", 0, 1, c -> c.speed, v -> {}, false, false));
        FIELDS.add(new Field("角度", "起始角度，可正可负", 1, 0, c -> c.angle, v -> {}, false, false));
        FIELDS.add(new Field("间距", "虚线/拖尾间距，0.05 ~ 1", 1, 1, c -> c.spacing, v -> {}, false, false));
        // 倾角单独处理（三行）
        FIELDS.add(new Field("倾斜X", "前后俯仰（绕X轴），-180 ~ 180", 2, 0, c -> c.tiltX, v -> {}, false, false));
        FIELDS.add(new Field("倾斜Y", "水平自转（绕Y轴），-180 ~ 180", 2, 1, c -> c.tiltY, v -> {}, false, false));
        FIELDS.add(new Field("倾斜Z", "左右侧倾（绕Z轴），-180 ~ 180", 3, 0, c -> c.tiltZ, v -> {}, false, false));
        FIELDS.add(new Field("线宽", "方块单位，如 0.03", 3, 1, c -> c.width, v -> {}, false, false));
    }

    private int px, py;
    private int rowH, boxH, boxW, labelW, cols;
    private int reservedTop = 44;
    private int reservedBottom = 56;

    public RingSettingsScreen(Screen parent) {
        super(Component.literal("星环设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        /* ---- 列数 ---- */
        int minColW = 44 + 56;
        cols = GuiLayout.cols(this.width - 16, minColW);

        /* ---- 尺寸 ---- */
        labelW = GuiLayout.fit(52, this.width, this.height, 34);
        boxW = GuiLayout.fit(70, this.width, this.height, 44);

        int availW = this.width - 16;
        int colStepMin = labelW + boxW + 8;
        if (cols == 2 && colStepMin * 2 > availW) {
            cols = 1;
        }
        if (cols == 1 && labelW + boxW > availW) {
            boxW = Math.max(40, availW - labelW);
        }

        /* ---- 行高 ---- */
        int availH = this.height - reservedTop - reservedBottom;
        int totalRows = 5;   // 物品行 + 4 行参数
        rowH = GuiLayout.actualRowH(availH, totalRows, 22, 14);
        boxH = Math.min(20, rowH - 2);
        if (boxH < 10) boxH = 10;

        /* ---- 定位 ---- */
        int blockW = (labelW + boxW) * cols + (cols > 1 ? 8 : 0);
        px = (this.width - blockW) / 2;
        int contentH = totalRows * rowH;
        py = reservedTop + Math.max(0, (availH - contentH) / 2);

        if (page >= RINGS.size()) page = Math.max(0, RINGS.size() - 1);
        buildPage();
    }

    private void buildPage() {
        this.clearWidgets();

        int colStep = labelW + boxW + 8;

        /* ---- 顶部：上一环 / 下一环 / 添加 / 删除 ---- */
        int btnW = Math.min(76, (this.width - 60) / 4);
        int bx = px;
        int by = 6;

        addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
            if (page > 0) { page--; buildPage(); }
        }).bounds(bx, by, 22, 18)
                .tooltip(Tooltip.create(Component.literal("上一圈")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            if (page + 1 < RINGS.size()) { page++; buildPage(); }
        }).bounds(bx + 26, by, 22, 18)
                .tooltip(Tooltip.create(Component.literal("下一圈")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            if (RINGS.size() < WardSourceBlockEntity.MAX_RINGS) {
                RingCfg c = new RingCfg();
                c.radius = String.format("%.2f", 0.8 + RINGS.size() * 0.3);
                c.angle = String.valueOf((RINGS.size() * 60) % 360);
                int[] palette = {0x66DDFF, 0xCC66FF, 0xFFD966, 0x66FF88, 0xFF6666, 0xFFFFFF};
                c.color = palette[RINGS.size() % palette.length];
                RINGS.add(c);
                page = RINGS.size() - 1;
                pushToServer();
                buildPage();
            }
        }).bounds(this.width - 76, by, 34, 18)
                .tooltip(Tooltip.create(Component.literal("添加一圈（上限 " + WardSourceBlockEntity.MAX_RINGS + "）")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("×"), b -> {
            if (page < RINGS.size()) {
                RINGS.remove(page);
                if (page >= RINGS.size()) page = Math.max(0, RINGS.size() - 1);
                pushToServer();
                buildPage();
            }
        }).bounds(this.width - 38, by, 30, 18)
                .tooltip(Tooltip.create(Component.literal("删除当前圈")))
                .build());

        if (RINGS.isEmpty()) {
            buildFooter();
            return;
        }

        RingCfg cfg = RINGS.get(page);

        /* ---- 第 0 行：物品槽 ---- */
        int y0 = py;
        addRenderableWidget(Button.builder(Component.literal(""), b ->
                minecraft.setScreen(new ItemPickerScreen(this, page)))
                .bounds(px + labelW, y0 + (rowH - boxH) / 2, boxH, boxH)
                .tooltip(Tooltip.create(Component.literal("点击选择环上的物品")))
                .build());

        /* ---- 第 1~4 行：参数 ---- */
        for (Field f : FIELDS) {
            if (cols == 1 && f.col == 1) continue;   // 单列时第二列改成顺延行
            int rowIdx = f.row;
            int colIdx = f.col;
            if (cols == 1) {
                // 单列：把 (row,col) 展平成顺序行
                rowIdx = f.row * 2 + f.col;
                colIdx = 0;
            }
            int x = px + colIdx * colStep + labelW;
            int y = py + (rowIdx + 1) * rowH + (rowH - boxH) / 2;
            addFieldBox(f, cfg, x, y);
        }

        /* ---- 颜色 / 透明度 / 样式 一行 ---- */
        int cy = py + (cols == 1 ? 10 : 5) * rowH + (rowH - boxH) / 2;

        addRenderableWidget(Button.builder(Component.literal("颜色"), b ->
                minecraft.setScreen(new ColorPickerScreen(this, cfg.color,
                        parse(cfg.alpha, 0.55f), (rgb, a) -> {
                    cfg.color = rgb;
                    cfg.alpha = String.format("%.2f", a);
                })))
                .bounds(px + labelW, cy, 40, boxH)
                .tooltip(Tooltip.create(Component.literal("点击打开颜色轮盘")))
                .build());

        // 透明度输入框
        EditBox alphaBox = new EditBox(this.font, px + labelW + 44, cy, boxW, boxH,
                Component.literal("透明度"));
        alphaBox.setValue(cfg.alpha);
        alphaBox.setTooltip(Tooltip.create(Component.literal("0 ~ 1")));
        alphaBox.setResponder(v -> cfg.alpha = v);
        addRenderableWidget(alphaBox);

        // 样式按钮
        addRenderableWidget(Button.builder(styleText(cfg.style), b -> {
            cfg.style = (cfg.style + 1) % 3;
            b.setMessage(styleText(cfg.style));
        }).bounds(px + (cols > 1 ? colStep : 0) + labelW, cy, 52, boxH)
                .tooltip(Tooltip.create(Component.literal("连续 → 拖尾 → 虚线")))
                .build());

        buildFooter();
    }

    private void addFieldBox(Field f, RingCfg cfg, int x, int y) {
        String val;
        switch (f.label) {
            case "半径": val = cfg.radius; break;
            case "速度": val = cfg.speed; break;
            case "角度": val = cfg.angle; break;
            case "倾斜X": val = cfg.tiltX; break;
            case "倾斜Y": val = cfg.tiltY; break;
            case "倾斜Z": val = cfg.tiltZ; break;
            case "线宽": val = cfg.width; break;
            case "间距": val = cfg.spacing; break;
            default: val = "0";
        }
        EditBox box = new EditBox(this.font, x, y, boxW, boxH, Component.literal(f.label));
        box.setValue(val);
        box.setTooltip(Tooltip.create(Component.literal(f.tip)));
        box.setResponder(t -> {
            switch (f.label) {
                case "半径": cfg.radius = t; break;
                case "速度": cfg.speed = t; break;
                case "角度": cfg.angle = t; break;
                case "倾斜X": cfg.tiltX = t; break;
                case "倾斜Y": cfg.tiltY = t; break;
                case "倾斜Z": cfg.tiltZ = t; break;
                case "线宽": cfg.width = t; break;
                case "间距": cfg.spacing = t; break;
            }
        });
        addRenderableWidget(box);
    }

    private void buildFooter() {
        int cx = this.width / 2;
        int bw = Math.max(56, Math.min(84, (this.width - 32) / 2));
        int gap = 8;
        int by = this.height - 24;

        // 保存并返回：直接写回服务端，不需要退回主界面再点保存
        addRenderableWidget(Button.builder(Component.literal("保存并返回"), b -> {
            pushToServer();
            WardConfigScreen.saveNow();
            minecraft.setScreen(parent);
        }).bounds(cx - bw - gap / 2, by, bw, 20)
                .tooltip(Tooltip.create(Component.literal("保存星环设置并返回上一级")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b -> {
            if (WardConfigScreen.lastBE != null) {
                RingSettingsScreen.loadFrom(WardConfigScreen.lastBE);
            }
            minecraft.setScreen(parent);
        }).bounds(cx + gap / 2, by, bw, 20).build());
    }

    private void pushToServer() {
        if (WardConfigScreen.lastBE != null) {
            WardConfigScreen.sendOrbit(WardConfigScreen.lastBE.getBlockPos());
        }
    }

    private static Component styleText(int style) {
        switch (style) {
            case 1: return Component.literal("拖尾");
            case 2: return Component.literal("虚线");
            default: return Component.literal("连续");
        }
    }

    /* ================= 渲染 ================= */

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);
        super.render(gg, mouseX, mouseY, partialTick);

        // 标题 + 页码
        String title = RINGS.isEmpty()
                ? "星环设置"
                : "星环设置  " + (page + 1) + " / " + RINGS.size();
        gg.drawCenteredString(this.font, title, this.width / 2, 8, 0xFFD700);

        if (RINGS.isEmpty()) {
            gg.drawCenteredString(this.font, "§7还没有任何圈，点右上角 + 添加",
                    this.width / 2, this.height / 2, 0x888888);
            return;
        }

        RingCfg cfg = RINGS.get(page);
        int colStep = labelW + boxW + 8;

        // 第 0 行：物品
        int y0 = py;
        gg.drawString(this.font, "物品", px, y0 + (rowH - 8) / 2, 0xFFFFFF);
        gg.fill(px + labelW - 1, y0 + (rowH - boxH) / 2 - 1,
                px + labelW + boxH + 1, y0 + (rowH - boxH) / 2 + boxH + 1, 0xFF3A3A3A);
        if (!cfg.item.isEmpty()) {
            gg.renderItem(cfg.item, px + labelW + 2, y0 + (rowH - boxH) / 2 + 2);
        }

        // 参数行标签
        for (Field f : FIELDS) {
            int rowIdx = f.row;
            int colIdx = f.col;
            if (cols == 1) {
                rowIdx = f.row * 2 + f.col;
                colIdx = 0;
            }
            if (cols == 1 && f.col == 1) continue;
            int lx = px + colIdx * colStep;
            int ly = py + (rowIdx + 1) * rowH + (rowH - 8) / 2;
            gg.drawString(this.font, f.label, lx, ly, 0xFFFFFF);
        }

        // 颜色预览
        int cy = py + (cols == 1 ? 10 : 5) * rowH + (rowH - boxH) / 2;
        gg.fill(px + labelW - 1, cy - 1, px + labelW + 41, cy + boxH + 1, 0xFF000000 | cfg.color);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dy > 0 && page > 0) { page--; buildPage(); return true; }
        if (dy < 0 && page + 1 < RINGS.size()) { page++; buildPage(); return true; }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}