package com.torchcampfiredamage.client;

/**
 * 界面自适应布局工具（与 NeoForge 版一致）。
 * <p>
 * 参考原版 Minecraft 的 GUI Scale 计算：
 * 保证逻辑分辨率不小于 320×240。
 */
public final class GuiLayout {

    public static final int BASE_W = 320;
    public static final int BASE_H = 240;

    private GuiLayout() {}

    /** 屏幕密度系数：小屏系数大（需压缩），大屏系数小 */
    public static float density(int width, int height) {
        float wRatio = (float) BASE_W / Math.max(1, width);
        float hRatio = (float) BASE_H / Math.max(1, height);
        float d = Math.max(wRatio, hRatio);
        return Math.max(0.5f, Math.min(2.0f, d));
    }

    /** 按屏幕密度缩放基准尺寸 */
    public static int fit(int base, int width, int height, int min) {
        float d = density(width, height);
        int v = Math.round(base / d);
        return Math.max(min, v);
    }

    /** 根据可用宽度决定列数 */
    public static int cols(int availW, int minColW) {
        return availW >= minColW * 2 ? 2 : 1;
    }

    /** 可用高度内最多能放几行 */
    public static int maxRows(int availH, int rowH, int minRowH) {
        int rows = availH / Math.max(1, rowH);
        if (rows < 1) {
            rows = Math.max(1, availH / Math.max(1, minRowH));
        }
        return rows;
    }

    /** 在可用高度内求实际行高 */
    public static int actualRowH(int availH, int rowCount, int rowH, int minRowH) {
        if (rowCount <= 0) return rowH;
        int v = availH / rowCount;
        return Math.max(minRowH, Math.min(rowH, v));
    }
}