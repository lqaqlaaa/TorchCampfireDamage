package com.torchcampfiredamage.client;

/**
 * 界面自适应布局工具。
 * <p>
 * 参考原版 Minecraft 的 GUI Scale 计算方式（{@code Window#calculateScale}）：
 * <pre>
 *   scale = 1
 *   while (scale &lt; maxScale &amp;&amp; w/(scale+1) &gt;= 320 &amp;&amp; h/(scale+1) &gt;= 240)
 *       scale++
 * </pre>
 * 原版保证「逻辑分辨率不小于 320×240」。
 * <p>
 * 本工具不改变玩家的缩放设置，而是让界面**按当前逻辑分辨率自适应**：
 * <ul>
 *   <li>{@link #density} —— 当前屏幕的「拥挤程度」，越小的屏幕值越大</li>
 *   <li>{@link #fit} —— 按密度缩放一个基准尺寸</li>
 *   <li>{@link #cols} —— 根据可用宽度决定单列还是双列</li>
 * </ul>
 */
public final class GuiLayout {

    /** 原版的逻辑下限 */
    public static final int BASE_W = 320;
    public static final int BASE_H = 240;

    private GuiLayout() {}

    /**
     * 屏幕密度系数。
     * <p>
     * 以 320×240 为基准（系数 1.0）；
     * 屏幕越小系数越大（说明空间越紧张，需要压缩），
     * 屏幕越大系数越小（可以放大）。
     *
     * @return 0.5 ~ 2.0 之间的系数
     */
    public static float density(int width, int height) {
        float wRatio = (float) BASE_W / Math.max(1, width);
        float hRatio = (float) BASE_H / Math.max(1, height);
        // 取两者中更紧张的那个
        float d = Math.max(wRatio, hRatio);
        return Math.max(0.5f, Math.min(2.0f, d));
    }

    /**
     * 按屏幕密度缩放基准尺寸。
     *
     * @param base  基准像素
     * @param width  当前逻辑宽
     * @param height 当前逻辑高
     * @return 缩放后的像素（至少为 min）
     */
    public static int fit(int base, int width, int height, int min) {
        float d = density(width, height);
        int v = Math.round(base / d);
        return Math.max(min, v);
    }

    /**
     * 根据可用宽度决定列数。
     *
     * @param availW     可用宽度
     * @param minColW    单列所需最小宽度
     * @return 1 或 2
     */
    public static int cols(int availW, int minColW) {
        return availW >= minColW * 2 ? 2 : 1;
    }

    /**
     * 计算「每页最多能放几行」。
     *
     * @param availH     可用高度
     * @param rowH       期望行高
     * @param minRowH    最小行高
     * @return 行数（至少 1）
     */
    public static int maxRows(int availH, int rowH, int minRowH) {
        int rows = availH / Math.max(1, rowH);
        if (rows < 1) {
            // 空间太小，用最小行高硬挤
            rows = Math.max(1, availH / Math.max(1, minRowH));
        }
        return rows;
    }

    /**
     * 在可用高度内求实际行高（尽量接近 rowH，不够则压缩）。
     */
    public static int actualRowH(int availH, int rowCount, int rowH, int minRowH) {
        if (rowCount <= 0) return rowH;
        int v = availH / rowCount;
        return Math.max(minRowH, Math.min(rowH, v));
    }
}