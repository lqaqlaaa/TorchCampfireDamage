package com.torchcampfiredamage.client;

import net.minecraft.client.gui.GuiGraphics;

/** Shared responsive geometry and visual language for every native settings screen. */
public final class GuiLayout {
    public static final int BASE_W = 320;
    public static final int BASE_H = 240;

    // Fatality / Neverlose-inspired navy workspace and electric-blue accent.
    public static final int BG = 0xF20B111D;
    public static final int PANEL = 0xD9142031;
    public static final int ROW = 0x66304765;
    public static final int ACCENT = 0xFF398BFF;
    public static final int TEXT = 0xFFE6EDF7;
    public static final int MUTED = 0xFF91A2B8;
    public static final int BORDER = 0xFF344C6D;

    private GuiLayout() {}

    /** Uniform responsive factor in Minecraft logical GUI coordinates. */
    public static float scale(int width, int height) {
        float fit = Math.min(width / (float) BASE_W, height / (float) BASE_H);
        return Math.max(0.55f, Math.min(1.35f, fit));
    }

    public static int px(int base, int width, int height, int min) {
        return Math.max(min, Math.round(base * scale(width, height)));
    }

    public static int densityWidth(int width) {
        return Math.max(1, width - px(16, width, 240, 8));
    }

    /** Paint the same framed canvas on every screen; it adapts to narrow and short windows. */
    public static void drawFrame(GuiGraphics gg, int width, int height) {
        int inset = px(5, width, height, 3);
        int topBar = px(28, width, height, 20);
        int contentTop = topBar + px(5, width, height, 3);
        int right = Math.max(inset + 1, width - inset);
        int bottom = Math.max(contentTop + 1, height - inset);

        gg.fill(0, 0, width, height, 0xC9080D17);
        gg.fill(inset, inset, right, height - inset, BG);
        gg.fill(inset + 2, inset + 2, right - 2, contentTop, 0xFF101B2C);
        gg.fill(inset + 2, contentTop, right - 2, bottom, 0xE6101724);

        // Brand rail and fine separators.
        gg.fill(inset, inset, inset + px(3, width, height, 2), height - inset, ACCENT);
        gg.fill(inset + 2, contentTop, right - 2, contentTop + 1, 0xFF263B59);
        gg.fill(inset + 2, inset + 2, right - 2, inset + 3, 0xFF263B59);

        gg.renderOutline(inset, inset, right - inset, height - inset * 2, BORDER);
        if (width > 260 && height > 120) {
            gg.fill(inset + px(9, width, height, 5), contentTop + px(7, width, height, 4),
                    inset + px(10, width, height, 6), bottom - px(7, width, height, 4), 0x553F82D8);
        }
    }

    /** Shared title treatment; screens provide their own contextual title and subtitle. */
    public static void drawTitle(GuiGraphics gg, net.minecraft.client.gui.Font font,
                                 int width, int y, String title, String subtitle) {
        int left = px(14, width, 240, 7);
        gg.drawString(font, "守卫火把", left, y, 0xFF70AEFF, false);
        int titleX = Math.max(left + font.width("守卫火把") + 8, width / 2 - font.width(title) / 2);
        gg.drawString(font, title, titleX, y, TEXT, false);
        if (subtitle != null && !subtitle.isBlank()) {
            gg.drawCenteredString(font, subtitle, width / 2, y + font.lineHeight + 2, MUTED);
        }
    }

    public static float density(int width, int height) {
        return 1.0f / scale(width, height);
    }

    public static int fit(int base, int width, int height, int min) {
        return Math.max(min, Math.round(base * scale(width, height)));
    }

    public static int cols(int availW, int minColW) {
        return availW >= minColW * 2 ? 2 : 1;
    }

    public static int maxRows(int availH, int rowH, int minRowH) {
        return Math.max(1, availH / Math.max(1, rowH));
    }

    public static int actualRowH(int availH, int rowCount, int rowH, int minRowH) {
        if (rowCount <= 0) return rowH;
        return Math.max(minRowH, Math.min(rowH, availH / rowCount));
    }
}