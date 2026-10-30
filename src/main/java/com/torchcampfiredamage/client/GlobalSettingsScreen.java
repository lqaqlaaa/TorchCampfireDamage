package com.torchcampfiredamage.client;

import com.torchcampfiredamage.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 全局设置界面 —— 把 ModConfig 里所有运行时配置项都暴露出来。
 * <p>
 * 分两页，左右箭头切换：
 * <pre>
 *   第 1 页「全局」：掉落/经验传送、算玩家击杀、忽略命名生物、
 *                    被激怒视为敌对、播报总开关、播报半径、
 *                    音效、发光持续、减速持续
 *   第 2 页「副手」：副手火把效果的全部 8 项
 * </pre>
 * 改动即时写入 .toml 配置文件（{@code ConfigValue#set} + {@code save}）。
 */
public class GlobalSettingsScreen extends Screen {

    private final Screen parent;

    private static final int ROW_H = 20;
    private static final int LABEL_W = 96;
    private static final int PANEL_W = 360;
    private static final int FIELD_W = 76;

    private int px, py;
    /** 0 = 全局页，1 = 副手页 */
    private int page = 0;
    private static final int PAGE_COUNT = 3;

    public GlobalSettingsScreen(Screen parent) {
        super(Component.literal("全局设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.px = this.width / 2 - PANEL_W / 2;
        this.py = Math.max(10, (this.height - panelHeight()) / 2);
        buildPage();
    }

    private int rowsOnPage() {
        if (page == 0) return 10;
        if (page == 1) return 8;
        return 11;   // 默认值页
    }

    private int panelHeight() {
        // 标题区 44 + 行 * (ROW_H+6) + 底部 44
        return 44 + rowsOnPage() * (ROW_H + 6) + 44;
    }

    private void buildPage() {
        this.clearWidgets();

        int y = py + 44;
        int fx = px + 12 + LABEL_W;

        /* ---------- 顶部：翻页 ---------- */
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
            if (page > 0) { page--; buildPage(); }
        }).bounds(px + 12, py + 14, 26, 20)
                .tooltip(Tooltip.create(Component.literal("上一页")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            if (page + 1 < PAGE_COUNT) { page++; buildPage(); }
        }).bounds(px + 44, py + 14, 26, 20)
                .tooltip(Tooltip.create(Component.literal("下一页")))
                .build());

        if (page == 0) {
            buildGeneralPage(fx, y);
        } else if (page == 1) {
            buildOffhandPage(fx, y);
        } else {
            buildDefaultsPage(fx, y);
        }

        /* ---------- 底部 ---------- */
        int by = py + panelHeight() - 34;
        int bw = 88, gap = 8;
        int bx = px + (PANEL_W - (bw * 2 + gap)) / 2;

        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> {
            saveConfig();
            minecraft.setScreen(parent);
        }).bounds(bx, by, bw, ROW_H).build());

        addRenderableWidget(Button.builder(Component.literal("恢复默认"), b -> {
            resetToDefaults();
            buildPage();
        }).bounds(bx + bw + gap, by, bw, ROW_H)
                .tooltip(Tooltip.create(Component.literal("把所有全局配置恢复为默认值")))
                .build());
    }

    /* ================= 第 1 页：全局 ================= */

    private void buildGeneralPage(int fx, int y) {
        addBool(fx, y, "传送掉落物", "击杀后掉落物传送到火把位置",
                () -> ModConfig.TELEPORT_DROPS.get(), v -> ModConfig.TELEPORT_DROPS.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "传送经验", "击杀后经验球传送到火把位置",
                () -> ModConfig.TELEPORT_XP.get(), v -> ModConfig.TELEPORT_XP.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "算玩家击杀", "伤害归属于玩家（有击杀数、掉落正常）",
                () -> ModConfig.COUNT_AS_PLAYER_KILL.get(), v -> ModConfig.COUNT_AS_PLAYER_KILL.set(v));
        y += ROW_H + 6;

        // ★ 用户重点要求的：命名生物白名单
        addBool(fx, y, "忽略命名生物", "已命名的生物不会被伤害（白名单保护），副手火把同样生效",
                () -> ModConfig.IGNORE_NAMED_MOBS.get(), v -> ModConfig.IGNORE_NAMED_MOBS.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "被激怒算敌对", "被激怒的中立生物（如狼、铁傀儡）也视作敌对目标",
                () -> ModConfig.TREAT_PROVOKED_AS_HOSTILE.get(), v -> ModConfig.TREAT_PROVOKED_AS_HOSTILE.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "击杀播报", "击杀时在聊天栏播报（总开关）",
                () -> ModConfig.GLOBAL_ANNOUNCE_ENABLED.get(), v -> ModConfig.GLOBAL_ANNOUNCE_ENABLED.set(v));
        y += ROW_H + 6;

        addDouble(fx, y, "播报半径", "播报可见距离（方块），默认 64",
                () -> ModConfig.ANNOUNCE_RANGE.get(), v -> ModConfig.ANNOUNCE_RANGE.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "吸经验音效", "吸取经验球时播放提示音",
                () -> ModConfig.PLAY_SOUND.get(), v -> ModConfig.PLAY_SOUND.set(v));
        y += ROW_H + 6;

        addInt(fx, y, "发光持续", "发光效果持续 tick 数，20 = 1 秒",
                () -> ModConfig.GLOW_DURATION.get(), v -> ModConfig.GLOW_DURATION.set(v));
        y += ROW_H + 6;

        addInt(fx, y, "减速持续", "减速效果持续 tick 数，20 = 1 秒",
                () -> ModConfig.SLOW_DURATION.get(), v -> ModConfig.SLOW_DURATION.set(v));
    }

    /* ================= 第 2 页：副手 ================= */

    private void buildOffhandPage(int fx, int y) {
        addBool(fx, y, "启用副手效果", "副手持守卫火把/装备护符时是否生效",
                () -> ModConfig.OFFHAND_ENABLED.get(), v -> ModConfig.OFFHAND_ENABLED.set(v));
        y += ROW_H + 6;

        addInt(fx, y, "区块半径", "伤害范围（区块），1 = 3x3 区块",
                () -> ModConfig.OFFHAND_CHUNK_RADIUS.get(), v -> ModConfig.OFFHAND_CHUNK_RADIUS.set(v));
        y += ROW_H + 6;

        addInt(fx, y, "伤害间隔", "tick 数，1 = 每 tick，20 = 1 秒",
                () -> ModConfig.OFFHAND_INTERVAL.get(), v -> ModConfig.OFFHAND_INTERVAL.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "击杀播报", "副手击杀时是否播报",
                () -> ModConfig.OFFHAND_ANNOUNCE.get(), v -> ModConfig.OFFHAND_ANNOUNCE.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "目标发光", "被攻击目标获得发光效果",
                () -> ModConfig.OFFHAND_GLOWING.get(), v -> ModConfig.OFFHAND_GLOWING.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "目标减速", "被攻击目标获得减速效果",
                () -> ModConfig.OFFHAND_SLOW.get(), v -> ModConfig.OFFHAND_SLOW.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "传送掉落物", "掉落物传送到玩家位置",
                () -> ModConfig.OFFHAND_TELEPORT_DROPS.get(), v -> ModConfig.OFFHAND_TELEPORT_DROPS.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "传送经验", "经验球传送到玩家位置",
                () -> ModConfig.OFFHAND_TELEPORT_XP.get(), v -> ModConfig.OFFHAND_TELEPORT_XP.set(v));
    }

    /* ================= 第 3 页：新火把默认值 ================= */

    private void buildDefaultsPage(int fx, int y) {
        addBool(fx, y, "默认启用", "新放置的火把是否默认开启",
                () -> ModConfig.DEFAULT_ENABLED.get(), v -> ModConfig.DEFAULT_ENABLED.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "默认区块模式", "开 = 区块范围；关 = 方块范围",
                () -> ModConfig.DEFAULT_CHUNK_MODE.get(), v -> ModConfig.DEFAULT_CHUNK_MODE.set(v));
        y += ROW_H + 6;

        addInt(fx, y, "默认区块半径", "0 = 1x1，1 = 3x3，2 = 5x5",
                () -> ModConfig.DEFAULT_CHUNK_RADIUS.get(), v -> ModConfig.DEFAULT_CHUNK_RADIUS.set(v));
        y += ROW_H + 6;

        addInt(fx, y, "默认方块半径", "方块模式下的半径（格）",
                () -> ModConfig.DEFAULT_BLOCK_RADIUS.get(), v -> ModConfig.DEFAULT_BLOCK_RADIUS.set(v));
        y += ROW_H + 6;

        addDouble(fx, y, "默认伤害", "2.0 = 1 颗心；0 = 只标记不伤害",
                () -> ModConfig.DEFAULT_DAMAGE.get(), v -> ModConfig.DEFAULT_DAMAGE.set(v));
        y += ROW_H + 6;

        addInt(fx, y, "默认间隔", "伤害间隔 tick，20 = 1 秒",
                () -> ModConfig.DEFAULT_INTERVAL.get(), v -> ModConfig.DEFAULT_INTERVAL.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "默认发光", "新火把是否默认给目标发光",
                () -> ModConfig.DEFAULT_GLOWING.get(), v -> ModConfig.DEFAULT_GLOWING.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "默认减速", "新火把是否默认给目标减速",
                () -> ModConfig.DEFAULT_SLOW.get(), v -> ModConfig.DEFAULT_SLOW.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "默认播报", "新火把是否默认播报击杀",
                () -> ModConfig.DEFAULT_ANNOUNCE.get(), v -> ModConfig.DEFAULT_ANNOUNCE.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "默认传送掉落", "新火把是否默认传送掉落物",
                () -> ModConfig.DEFAULT_TELEPORT_DROPS.get(), v -> ModConfig.DEFAULT_TELEPORT_DROPS.set(v));
        y += ROW_H + 6;

        addBool(fx, y, "默认传送经验", "新火把是否默认传送经验",
                () -> ModConfig.DEFAULT_TELEPORT_XP.get(), v -> ModConfig.DEFAULT_TELEPORT_XP.set(v));
    }

    /* ================= 控件工厂 ================= */

    private void addBool(int x, int y, String label, String tip,
                         Supplier<Boolean> getter, Consumer<Boolean> setter) {
        boolean cur = safeGetB(getter);
        Button b = Button.builder(onOffText(cur), btn -> {
            boolean now = !safeGetB(getter);
            setter.accept(now);
            btn.setMessage(onOffText(now));
        }).bounds(x, y, FIELD_W, ROW_H)
                .tooltip(Tooltip.create(Component.literal(tip)))
                .build();
        addRenderableWidget(b);
    }

    private void addInt(int x, int y, String label, String tip,
                        Supplier<Integer> getter, Consumer<Integer> setter) {
        EditBox box = new EditBox(this.font, x, y, FIELD_W, ROW_H, Component.literal(label));
        box.setValue(String.valueOf(safeGetI(getter)));
        box.setTooltip(Tooltip.create(Component.literal(tip)));
        box.setResponder(t -> {
            try {
                setter.accept(Integer.parseInt(t.trim()));
            } catch (Exception ignored) {
            }
        });
        addRenderableWidget(box);
    }

    private void addDouble(int x, int y, String label, String tip,
                           Supplier<Double> getter, Consumer<Double> setter) {
        EditBox box = new EditBox(this.font, x, y, FIELD_W, ROW_H, Component.literal(label));
        box.setValue(String.format("%.1f", safeGetD(getter)));
        box.setTooltip(Tooltip.create(Component.literal(tip)));
        box.setResponder(t -> {
            try {
                setter.accept(Double.parseDouble(t.trim()));
            } catch (Exception ignored) {
            }
        });
        addRenderableWidget(box);
    }

    /* ================= 读写工具 ================= */

    private static boolean safeGetB(Supplier<Boolean> s) {
        try { return s.get(); } catch (Throwable t) { return false; }
    }

    private static int safeGetI(Supplier<Integer> s) {
        try { return s.get(); } catch (Throwable t) { return 0; }
    }

    private static double safeGetD(Supplier<Double> s) {
        try { return s.get(); } catch (Throwable t) { return 0; }
    }

    private Component onOffText(boolean on) {
        return Component.literal(on ? "§a开" : "§c关");
    }

    /**
     * 把所有全局配置写回 .toml。
     * <p>
     * {@code ConfigValue#set} 已经即时改了内存值（所以立即生效），
     * 这里是再把改动落到磁盘。用反射调用 {@code save()}，
     * 不同 NeoForge 小版本里方法位置可能有差异，避免编译期硬依赖。
     */
    private void saveConfig() {
        try {
            java.lang.reflect.Method m = ModConfig.SPEC.getClass().getMethod("save");
            m.invoke(ModConfig.SPEC);
        } catch (Throwable t) {
            // 保存失败不阻断界面关闭；内存值依然已生效
        }
    }

    /** 恢复默认值 */
    private void resetToDefaults() {
        try {
            ModConfig.TELEPORT_DROPS.set(true);
            ModConfig.TELEPORT_XP.set(true);
            ModConfig.COUNT_AS_PLAYER_KILL.set(true);
            ModConfig.IGNORE_NAMED_MOBS.set(true);
            ModConfig.TREAT_PROVOKED_AS_HOSTILE.set(true);
            ModConfig.GLOBAL_ANNOUNCE_ENABLED.set(true);
            ModConfig.ANNOUNCE_RANGE.set(64.0);
            ModConfig.PLAY_SOUND.set(true);
            ModConfig.GLOW_DURATION.set(100);
            ModConfig.SLOW_DURATION.set(100);

            ModConfig.OFFHAND_ENABLED.set(true);
            ModConfig.OFFHAND_CHUNK_RADIUS.set(1);
            ModConfig.OFFHAND_INTERVAL.set(1);
            ModConfig.OFFHAND_ANNOUNCE.set(false);
            ModConfig.OFFHAND_GLOWING.set(false);
            ModConfig.OFFHAND_SLOW.set(false);
            ModConfig.OFFHAND_TELEPORT_DROPS.set(false);
            ModConfig.OFFHAND_TELEPORT_XP.set(false);

            // 新火把默认值
            ModConfig.DEFAULT_ENABLED.set(true);
            ModConfig.DEFAULT_CHUNK_MODE.set(true);
            ModConfig.DEFAULT_CHUNK_RADIUS.set(1);
            ModConfig.DEFAULT_BLOCK_RADIUS.set(16);
            ModConfig.DEFAULT_DAMAGE.set(2.0);
            ModConfig.DEFAULT_INTERVAL.set(20);
            ModConfig.DEFAULT_GLOWING.set(false);
            ModConfig.DEFAULT_SLOW.set(false);
            ModConfig.DEFAULT_ANNOUNCE.set(true);
            ModConfig.DEFAULT_TELEPORT_DROPS.set(true);
            ModConfig.DEFAULT_TELEPORT_XP.set(true);

            saveConfig();
        } catch (Throwable ignored) {
        }
    }

    /* ================= 渲染 ================= */

    /** 当前页的标签文字 */
    private String[] labelsForPage() {
        if (page == 0) {
            return new String[]{
                    "传送掉落物", "传送经验", "算玩家击杀", "忽略命名生物",
                    "被激怒算敌对", "击杀播报", "播报半径", "吸经验音效",
                    "发光持续", "减速持续"
            };
        }
        if (page == 1) {
            return new String[]{
                    "启用副手效果", "区块半径", "伤害间隔", "击杀播报",
                    "目标发光", "目标减速", "传送掉落物", "传送经验"
            };
        }
        return new String[]{
                "默认启用", "默认区块模式", "默认区块半径", "默认方块半径",
                "默认伤害", "默认间隔", "默认发光", "默认减速",
                "默认播报", "默认传送掉落", "默认传送经验"
        };
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);
        super.render(gg, mouseX, mouseY, partialTick);

        int panelH = panelHeight();
        gg.fill(px, py, px + PANEL_W, py + panelH, 0xD0000000);

        String title;
        if (page == 0) title = "全局设置 · 通用";
        else if (page == 1) title = "全局设置 · 副手火把";
        else title = "全局设置 · 新火把默认值";
        gg.drawCenteredString(this.font, title, px + PANEL_W / 2 - 40, py + 20, 0xFFD700);
        gg.drawCenteredString(this.font, "第 " + (page + 1) + " / " + PAGE_COUNT + " 页",
                px + PANEL_W - 60, py + 20, 0x888888);

        // 标签
        String[] labels = labelsForPage();
        int y = py + 44;
        for (String lab : labels) {
            gg.drawString(this.font, lab, px + 12, y + 6, 0xEEEEEE);
            y += ROW_H + 6;
        }

        // 第 1 页把「忽略命名生物」高亮提示一下
        if (page == 0) {
            int hy = py + 44 + 3 * (ROW_H + 6);
            gg.fill(px + 8, hy - 2, px + PANEL_W - 8, hy + ROW_H + 2, 0x30FFD700);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dy > 0 && page > 0) {
            page--;
            buildPage();
            return true;
        } else if (dy < 0 && page + 1 < PAGE_COUNT) {
            page++;
            buildPage();
            return true;
        }
        return true;
    }

    @Override
    public void onClose() {
        saveConfig();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}