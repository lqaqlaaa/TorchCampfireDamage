package com.torchcampfiredamage.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 颜色选择器（网格版，性能友好）。
 * <p>
 * 用 12×8 的色块网格 + 右侧亮度条 + 底部透明度条，
 * 全部用 gg.fill 绘制轴对齐矩形，不会卡。
 */
public class ColorPickerScreen extends Screen {

    public interface Callback {
        void onPick(int rgb, float alpha);
    }

    private final Screen parent;
    private final Callback callback;

    private int rgb;
    private float alpha;

    /* 网格 */
    private static final int COLS = 12;
    private static final int ROWS = 8;
    private static final int CELL = 18;
    /** 实际单元格边长（自适应，可能小于 CELL） */
    private int cell = CELL;
    private int gridX, gridY;

    /* 亮度 / 透明度条 */
    private int barX, barW, barH, valueBarY, alphaBarY;

    private float hue = 0.55f;
    private float sat = 0.65f;
    private float val = 1.0f;

    private EditBox hexBox;
    private EditBox alphaBox;

    private boolean draggingValue = false;
    private boolean draggingAlpha = false;

    public ColorPickerScreen(Screen parent, int rgb, float alpha, Callback callback) {
        super(Component.literal("选择颜色"));
        this.parent = parent;
        this.callback = callback;
        this.rgb = rgb & 0xFFFFFF;
        this.alpha = alpha;

        float[] hsv = rgbToHsv(this.rgb);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        /* ---- 自适应：网格尺寸按屏幕可用空间压缩 ---- */
        // 右侧面板最小宽度（滑块 + 输入框 + 按钮）
        final int sideW = 64;
        int availW = this.width - 16;
        int availH = this.height - 20;

        // 网格最多占 availW - sideW - 12
        int maxGridW = Math.max(60, availW - sideW - 12);
        int cell = Math.min(CELL, Math.max(8, maxGridW / COLS));
        int gw = COLS * cell;
        int gh = ROWS * cell;

        // 高度也要能放下
        if (gh > availH - 10) {
            cell = Math.max(6, (availH - 10) / ROWS);
            gw = COLS * cell;
            gh = ROWS * cell;
        }
        this.cell = cell;

        int totalW = gw + 10 + sideW;
        int startX = cx - totalW / 2;

        gridX = startX;
        gridY = Math.max(24, (this.height - gh) / 2);

        barX = gridX + gw + 10;
        barW = Math.max(50, Math.min(sideW, this.width - barX - 6));
        barH = 12;

        // 右侧面板纵向依次排布，且不超出屏幕底部
        int panelTop = gridY;
        valueBarY = panelTop;
        alphaBarY = valueBarY + barH + 10;

        int boxW = barW;
        int boxH = 16;

        hexBox = new EditBox(this.font, barX, alphaBarY + barH + 8, boxW, boxH,
                Component.literal("HEX"));
        hexBox.setValue(String.format("#%06X", rgb));
        hexBox.setTooltip(Tooltip.create(Component.literal("输入 #RRGGBB")));
        hexBox.setResponder(this::onHexInput);
        addRenderableWidget(hexBox);

        alphaBox = new EditBox(this.font, barX, alphaBarY + barH + 8 + boxH + 6, boxW, boxH,
                Component.literal("ALPHA"));
        alphaBox.setValue(String.format("%.2f", alpha));
        alphaBox.setTooltip(Tooltip.create(Component.literal("0 ~ 1")));
        alphaBox.setResponder(v -> {
            try {
                alpha = clampi(Float.parseFloat(v.trim()), 0f, 1f);
            } catch (Exception ignored) {}
        });
        addRenderableWidget(alphaBox);

        // 按钮放底部居中，避免挤在右侧导致超屏
        int by = this.height - 26;
        int bw = Math.min(64, (this.width - 40) / 2);
        int bx = cx - bw - 4;

        addRenderableWidget(Button.builder(Component.literal("确定"), b -> {
            callback.onPick(rgb, alpha);
            minecraft.setScreen(parent);
        }).bounds(bx, by, bw, 20).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b ->
                minecraft.setScreen(parent)).bounds(bx + bw + 8, by, bw, 20).build());
    }

    private static float clampi(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    private void onHexInput(String s) {
        try {
            String t = s.trim();
            if (t.startsWith("#")) t = t.substring(1);
            if (t.length() != 6) return;
            int v = Integer.parseInt(t, 16) & 0xFFFFFF;
            rgb = v;
            float[] hsv = rgbToHsv(v);
            hue = hsv[0]; sat = hsv[1]; val = hsv[2];
        } catch (Exception ignored) {}
    }

    /* ================= 交互 ================= */

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 网格
        int gw = COLS * cell;
        int gh = ROWS * cell;
        if (mx >= gridX && mx < gridX + gw && my >= gridY && my < gridY + gh) {
            int c = (int) ((mx - gridX) / cell);
            int r = (int) ((my - gridY) / cell);
            c = Math.max(0, Math.min(COLS - 1, c));
            r = Math.max(0, Math.min(ROWS - 1, r));

            hue = c / (float) (COLS - 1);
            sat = (r + 0.5f) / ROWS;          // 越往下越饱和
            // 数值用当前 val
            rgb = hsvToRgb(hue, sat, val);
            hexBox.setValue(String.format("#%06X", rgb));
            return true;
        }

        // 亮度条
        if (mx >= barX && mx <= barX + barW && my >= valueBarY && my <= valueBarY + barH) {
            draggingValue = true;
            val = (float) clampi((float) ((mx - barX) / (double) barW), 0f, 1f);
            rgb = hsvToRgb(hue, sat, val);
            hexBox.setValue(String.format("#%06X", rgb));
            return true;
        }

        // 透明度条
        if (mx >= barX && mx <= barX + barW && my >= alphaBarY && my <= alphaBarY + barH) {
            draggingAlpha = true;
            alpha = (float) clampi((float) ((mx - barX) / (double) barW), 0f, 1f);
            alphaBox.setValue(String.format("%.2f", alpha));
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingValue) {
            val = (float) clampi((float) ((mx - barX) / (double) barW), 0f, 1f);
            rgb = hsvToRgb(hue, sat, val);
            hexBox.setValue(String.format("#%06X", rgb));
            return true;
        }
        if (draggingAlpha) {
            alpha = (float) clampi((float) ((mx - barX) / (double) barW), 0f, 1f);
            alphaBox.setValue(String.format("%.2f", alpha));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingValue = false;
        draggingAlpha = false;
        return super.mouseReleased(mx, my, button);
    }

    /* ================= 渲染 ================= */

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);
        super.render(gg, mouseX, mouseY, partialTick);

        int gw = COLS * cell;
        int gh = ROWS * cell;

        // 标题
        gg.drawCenteredString(this.font, "选择颜色", gridX + gw / 2, gridY - 24, 0xFFD700);

        // ===== 网格 =====
        // 外框
        gg.fill(gridX - 1, gridY - 1, gridX + gw + 1, gridY + gh + 1, 0xFF000000);

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float h = c / (float) (COLS - 1);
                float s = (r + 0.5f) / ROWS;
                int col = 0xFF000000 | hsvToRgb(h, s, val);

                int x = gridX + c * cell;
                int y = gridY + r * cell;
                gg.fill(x, y, x + cell - 1, y + cell - 1, col);
            }
        }

        // 当前选中格的高亮框
        int selC = Math.round(hue * (COLS - 1));
        int selR = Math.max(0, Math.min(ROWS - 1, (int) (sat * ROWS)));
        int sx = gridX + selC * cell;
        int sy = gridY + selR * cell;
        // 白色十字框（4 条线）
        gg.fill(sx - 1, sy - 1, sx + cell, sy, 0xFFFFFFFF);
        gg.fill(sx - 1, sy + cell - 1, sx + cell, sy + cell, 0xFFFFFFFF);
        gg.fill(sx - 1, sy, sx, sy + cell - 1, 0xFFFFFFFF);
        gg.fill(sx + cell - 1, sy, sx + cell, sy + cell - 1, 0xFFFFFFFF);

        // ===== 亮度条 =====
        gg.drawString(this.font, "亮度", barX, valueBarY - 12, 0xFFFFFF);
        for (int i = 0; i < barW; i++) {
            float v = i / (float) barW;
            int col = 0xFF000000 | hsvToRgb(hue, sat, v);
            gg.fill(barX + i, valueBarY, barX + i + 1, valueBarY + barH, col);
        }
        int vx = barX + (int) (val * barW);
        gg.fill(vx - 1, valueBarY - 2, vx + 2, valueBarY + barH + 2, 0xFFFFFFFF);

        // ===== 透明度条 =====
        gg.drawString(this.font, "透明度", barX, alphaBarY - 12, 0xFFFFFF);
        for (int i = 0; i < barW; i++) {
            int cellIdx = i / 6;
            int base = (cellIdx % 2 == 0) ? 0xFF707070 : 0xFFA0A0A0;
            gg.fill(barX + i, alphaBarY, barX + i + 1, alphaBarY + barH, base);

            float a = i / (float) barW;
            int col = ((int) (a * 255) << 24) | rgb;
            gg.fill(barX + i, alphaBarY, barX + i + 1, alphaBarY + barH, col);
        }
        int ax = barX + (int) (alpha * barW);
        gg.fill(ax - 1, alphaBarY - 2, ax + 2, alphaBarY + barH + 2, 0xFFFFFFFF);

        // ===== 预览 =====
        int pvY = gridY + gh + 12;
        gg.drawString(this.font, "预览", gridX, pvY, 0xFFFFFF);
        gg.fill(gridX + 34, pvY - 2, gridX + 90, pvY + 14, 0xFFFFFFFF);
        gg.fill(gridX + 36, pvY, gridX + 88, pvY + 12,
                ((int) (alpha * 255) << 24) | rgb);

        // 色值文本
        gg.drawString(this.font, String.format("#%06X", rgb),
                gridX + 100, pvY, 0xAAAAAA);
    }

    /* ================= 颜色转换 ================= */

    static int hsvToRgb(float h, float s, float v) {
        h = ((h % 1f) + 1f) % 1f;
        float r, g, b;
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        int ri = Math.max(0, Math.min(255, (int) (r * 255)));
        int gi = Math.max(0, Math.min(255, (int) (g * 255)));
        int bi = Math.max(0, Math.min(255, (int) (b * 255)));
        return (ri << 16) | (gi << 8) | bi;
    }

    static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;

        float h = 0;
        if (d > 1e-6) {
            if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h /= 6;
            if (h < 0) h += 1;
        }
        float s = max <= 1e-6 ? 0 : d / max;
        return new float[]{h, s, max};
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}