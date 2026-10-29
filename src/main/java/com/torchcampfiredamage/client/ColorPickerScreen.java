package com.torchcampfiredamage.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 颜色选择器：HSV 轮盘 + 亮度条 + 透明度条 + 预览。
 */
public class ColorPickerScreen extends Screen {

    public interface Callback {
        void onPick(int rgb, float alpha);
    }

    private final Screen parent;
    private final Callback callback;

    private int rgb;
    private float alpha;

    /* 色相环参数 */
    private int wheelCx, wheelCy, wheelR;
    private static final float RING_INNER = 0.58f;
    private static final float RING_OUTER = 0.95f;

    /* 亮度条 / 透明度条 */
    private int barX, barW, barH;
    private int valueBarY, alphaBarY;

    private float hue = 0.55f;   // 0~1
    private float sat = 0.6f;
    private float val = 1.0f;

    private EditBox hexBox;
    private EditBox alphaBox;

    private boolean draggingValue = false;
    private boolean draggingAlpha = false;

    public ColorPickerScreen(Screen parent, int rgb, float alpha, Callback callback) {
        super(Component.literal("颜色选择"));
        this.parent = parent;
        this.callback = callback;
        this.rgb = rgb & 0xFFFFFF;
        this.alpha = alpha;

        // 反推 HSV
        float[] hsv = rgbToHsv(this.rgb);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        wheelR = Math.min(this.width, this.height) / 3;
        wheelCx = cx;
        wheelCy = cy - 20;

        barX = cx + wheelR + 30;
        barW = 100;
        barH = 16;
        valueBarY = wheelCy - wheelR + 40;
        alphaBarY = valueBarY + 40;

        // 色值输入
        hexBox = new EditBox(this.font, barX, alphaBarY + 40, 100, 18, Component.literal("HEX"));
        hexBox.setValue(String.format("#%06X", rgb));
        hexBox.setTooltip(Tooltip.create(Component.literal("输入 #RRGGBB")));
        hexBox.setResponder(this::onHexInput);
        addRenderableWidget(hexBox);

        alphaBox = new EditBox(this.font, barX, alphaBarY + 64, 100, 18, Component.literal("ALPHA"));
        alphaBox.setValue(String.format("%.2f", alpha));
        alphaBox.setTooltip(Tooltip.create(Component.literal("0 ~ 1")));
        alphaBox.setResponder(v -> {
            try {
                alpha = Math.max(0f, Math.min(1f, Float.parseFloat(v.trim())));
            } catch (Exception ignored) {
            }
        });
        addRenderableWidget(alphaBox);

        // 预设色
        int[][] presets = {
                {0x66DDFF}, {0xFF6666}, {0x66FF88},
                {0xFFD966}, {0xCC66FF}, {0xFFFFFF},
        };
        int bw = 28;
        for (int i = 0; i < presets.length; i++) {
            final int c = presets[i][0];
            addRenderableWidget(Button.builder(Component.literal(""), b -> {
                rgb = c;
                float[] hsv = rgbToHsv(c);
                hue = hsv[0]; sat = hsv[1]; val = hsv[2];
                hexBox.setValue(String.format("#%06X", c));
            }).bounds(barX + (i % 3) * (bw + 4), alphaBarY + 90 + (i / 3) * (bw - 8), bw, 20).build());
        }

        // 确定 / 取消
        int by = alphaBarY + 145;
        addRenderableWidget(Button.builder(Component.literal("确定"), b -> {
            callback.onPick(rgb, alpha);
            minecraft.setScreen(parent);
        }).bounds(barX, by, 48, 20).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b ->
                minecraft.setScreen(parent)).bounds(barX + 52, by, 48, 20).build());
    }

    private void onHexInput(String s) {
        try {
            String t = s.trim();
            if (t.startsWith("#")) t = t.substring(1);
            if (t.length() != 6) return;
            int v = Integer.parseInt(t, 16);
            rgb = v & 0xFFFFFF;
            float[] hsv = rgbToHsv(rgb);
            hue = hsv[0]; sat = hsv[1]; val = hsv[2];
        } catch (Exception ignored) {
        }
    }

    /* ================= 交互 ================= */

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 色相环
        double dx = mx - wheelCx;
        double dy = my - wheelCy;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d >= wheelR * RING_INNER && d <= wheelR * RING_OUTER) {
            double ang = Math.atan2(dy, dx);
            hue = (float) ((ang / (Math.PI * 2)) + 0.5f);
            if (hue < 0) hue += 1f;
            if (hue >= 1f) hue -= 1f;
            updateRgb();
            return true;
        }

        // 亮度条
        if (mx >= barX && mx <= barX + barW && my >= valueBarY && my <= valueBarY + barH) {
            draggingValue = true;
            val = (float) ((mx - barX) / (double) barW);
            updateRgb();
            return true;
        }

        // 透明度条
        if (mx >= barX && mx <= barX + barW && my >= alphaBarY && my <= alphaBarY + barH) {
            draggingAlpha = true;
            alpha = (float) ((mx - barX) / (double) barW);
            alphaBox.setValue(String.format("%.2f", alpha));
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingValue) {
            val = (float) Math.max(0, Math.min(1, (mx - barX) / (double) barW));
            updateRgb();
            return true;
        }
        if (draggingAlpha) {
            alpha = (float) Math.max(0, Math.min(1, (mx - barX) / (double) barW));
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

    private void updateRgb() {
        rgb = hsvToRgb(hue, sat, val) & 0xFFFFFF;
        hexBox.setValue(String.format("#%06X", rgb));
    }

    /* ================= 渲染 ================= */

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);

        // 标题
        gg.drawCenteredString(this.font, "选择颜色", this.width / 2, wheelCy - wheelR - 40, 0xFFD700);

        // ===== 1. 色相环（用大量小方块拼出）=====
        int steps = 180;
        for (int i = 0; i < steps; i++) {
            float a0 = (float) (Math.PI * 2 * i / steps);
            float a1 = (float) (Math.PI * 2 * (i + 1) / steps);

            float hh = (i / (float) steps);
            int c = hsvToRgb(hh, 1f, 1f) | 0xFF000000;

            float rIn = wheelR * RING_INNER;
            float rOut = wheelR * RING_OUTER;

            // 用一小段四边形近似
            float x0i = wheelCx + (float) Math.cos(a0) * rIn;
            float y0i = wheelCy + (float) Math.sin(a0) * rIn;
            float x0o = wheelCx + (float) Math.cos(a0) * rOut;
            float y0o = wheelCy + (float) Math.sin(a0) * rOut;

            float x1i = wheelCx + (float) Math.cos(a1) * rIn;
            float y1i = wheelCy + (float) Math.sin(a1) * rIn;
            float x1o = wheelCx + (float) Math.cos(a1) * rOut;
            float y1o = wheelCy + (float) Math.sin(a1) * rOut;

            drawQuad(gg, x0i, y0i, x0o, y0o, x1o, y1o, x1i, y1i, c);
        }

        // 环内画当前颜色圆点
        int innerR = (int) (wheelR * RING_INNER) - 12;
        gg.fill(wheelCx - innerR, wheelCy - innerR, wheelCx + innerR, wheelCy + innerR, 0xFF000000);
        gg.fill(wheelCx - innerR + 2, wheelCy - innerR + 2,
                wheelCx + innerR - 2, wheelCy + innerR - 2, 0xFF000000 | rgb);

        // 色相指示点
        float hu = hue;
        double ang = (hu - 0.5f) * Math.PI * 2;
        float midR = wheelR * (RING_INNER + RING_OUTER) / 2f;
        int px = (int) (wheelCx + Math.cos(ang) * midR);
        int py = (int) (wheelCy + Math.sin(ang) * midR);
        gg.fill(px - 3, py - 3, px + 3, py + 3, 0xFFFFFFFF);

        // ===== 2. 亮度条 =====
        gg.drawString(this.font, "亮度 " + String.format("%.0f%%", val * 100), barX, valueBarY - 12, 0xFFFFFF);
        for (int i = 0; i < barW; i++) {
            float v = i / (float) barW;
            int c = hsvToRgb(hue, sat, v) | 0xFF000000;
            gg.fill(barX + i, valueBarY, barX + i + 1, valueBarY + barH, c);
        }
        // 指示器
        int vx = barX + (int) (val * barW);
        gg.fill(vx - 1, valueBarY - 2, vx + 2, valueBarY + barH + 2, 0xFFFFFFFF);

        // ===== 3. 透明度条（棋盘格底 + 颜色渐变）=====
        gg.drawString(this.font, "透明度 " + String.format("%.0f%%", alpha * 100), barX, alphaBarY - 12, 0xFFFFFF);
        for (int i = 0; i < barW; i++) {
            // 棋盘格底
            int cell = i / 8;
            int base = (cell % 2 == 0) ? 0xFF666666 : 0xFF999999;
            gg.fill(barX + i, alphaBarY, barX + i + 1, alphaBarY + barH, base);

            // 叠加当前色（带 alpha）
            float a = i / (float) barW;
            int c = ((int) (a * 255) << 24) | rgb;
            gg.fill(barX + i, alphaBarY, barX + i + 1, alphaBarY + barH, c);
        }
        int ax = barX + (int) (alpha * barW);
        gg.fill(ax - 1, alphaBarY - 2, ax + 2, alphaBarY + barH + 2, 0xFFFFFFFF);

        // 预览
        gg.fill(barX + 104, valueBarY, barX + 124, alphaBarY + barH, 0xFFFFFFFF);
        gg.fill(barX + 106, valueBarY + 2, barX + 122, alphaBarY + barH - 2,
                ((int) (alpha * 255) << 24) | rgb);

        super.render(gg, mouseX, mouseY, partialTick);
    }

    /** 画任意四边形（用 2 个三角形填充） */
    private void drawQuad(GuiGraphics gg, float x0, float y0, float x1, float y1,
                          float x2, float y2, float x3, float y3, int color) {
        // GuiGraphics 只有 fill（轴对齐矩形），这里用细分三角形近似
        // 采样三角形内部点
        fillTriangle(gg, x0, y0, x1, y1, x3, y3, color);
        fillTriangle(gg, x1, y1, x2, y2, x3, y3, color);
    }

    private void fillTriangle(GuiGraphics gg, float ax, float ay, float bx, float by,
                              float cx, float cy, int color) {
        int minX = (int) Math.floor(Math.min(ax, Math.min(bx, cx)));
        int maxX = (int) Math.ceil(Math.max(ax, Math.max(bx, cx)));
        int minY = (int) Math.floor(Math.min(ay, Math.min(by, cy)));
        int maxY = (int) Math.ceil(Math.max(ay, Math.max(by, cy)));

        // 扫描线填充
        for (int y = minY; y <= maxY; y++) {
            float yc = y + 0.5f;
            float xa = Float.MAX_VALUE, xb = -Float.MAX_VALUE;

            // 三条边求交点
            float[][] edges = {{ax, ay, bx, by}, {bx, by, cx, cy}, {cx, cy, ax, ay}};
            for (float[] e : edges) {
                float x1 = e[0], y1 = e[1], x2 = e[2], y2 = e[3];
                if ((y1 <= yc && y2 > yc) || (y2 <= yc && y1 > yc)) {
                    float x = x1 + (yc - y1) / (y2 - y1) * (x2 - x1);
                    xa = Math.min(xa, x);
                    xb = Math.max(xb, x);
                }
            }
            if (xa < xb) {
                gg.fill((int) xa, y, (int) Math.ceil(xb), y + 1, color);
            }
        }
    }

    /* ================= 颜色转换 ================= */

    static int hsvToRgb(float h, float s, float v) {
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
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
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