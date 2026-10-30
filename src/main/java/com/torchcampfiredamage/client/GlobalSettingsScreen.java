package com.torchcampfiredamage.client;

import com.torchcampfiredamage.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 全局设置 —— 沿用主界面风格，并且**完全自适应屏幕尺寸**。
 * <p>
 * 适配策略（参考原版 GUI Scale 的处理方式）：
 * <ul>
 *   <li>所有尺寸都基于 {@code this.width} / {@code this.height}（逻辑分辨率）实时计算</li>
 *   <li>宽屏两列，窄屏（&lt; 340 逻辑像素）自动降为单列</li>
 *   <li>控件高度、行间距按可用高度压缩，保证内容永不超出屏幕</li>
 *   <li>每页项目数按当前屏幕能容纳的行数动态切分</li>
 *   <li>不画半透明黑底，标题在上方，与主界面统一</li>
 * </ul>
 */
public class GlobalSettingsScreen extends Screen {

    private final Screen parent;

    /* ===== 基准尺寸（会被屏幕实际大小压缩） ===== */
    private static final int BOX_H = 20;         // 控件高度
    private static final int MIN_ROW_H = 12;     // 压缩后的最小行高
    private static final int BOX_W_MAX = 88;     // 控件最大宽度

    /* ===== 运行时计算出的布局参数 ===== */
    private int px, py;          // 内容左上角
    private int rowH;            // 实际行高（含间距）
    private int boxH;            // 实际控件高
    private int boxW;            // 实际控件宽
    private int labelW;          // 实际标签宽
    private int cols;            // 实际列数（1 或 2）
    private int rowsPerPage;     // 每页行数

    /* ===== 数据 ===== */
    private interface Row {
        void build(GlobalSettingsScreen s, int x, int y);
        String label();
    }

    private final List<Row> allRows = new ArrayList<>();
    private int page = 0;
    private int pageCount = 1;

    public GlobalSettingsScreen(Screen parent) {
        super(Component.literal("全局设置"));
        this.parent = parent;
        buildRows();
    }

    /* ================= 行定义 ================= */

    private void buildRows() {
        allRows.clear();

        // --- 伤害与目标 ---
        allRows.add(boolRow("传送掉落物", "击杀后掉落物传送到火把位置",
                () -> ModConfig.TELEPORT_DROPS.get(), v -> ModConfig.TELEPORT_DROPS.set(v)));
        allRows.add(boolRow("传送经验", "击杀后经验球传送到火把位置",
                () -> ModConfig.TELEPORT_XP.get(), v -> ModConfig.TELEPORT_XP.set(v)));
        allRows.add(boolRow("算玩家击杀", "伤害归属于玩家（有击杀数、掉落正常）",
                () -> ModConfig.COUNT_AS_PLAYER_KILL.get(), v -> ModConfig.COUNT_AS_PLAYER_KILL.set(v)));
        allRows.add(boolRow("忽略命名生物", "已命名的生物不会被伤害（白名单），副手火把同样生效",
                () -> ModConfig.IGNORE_NAMED_MOBS.get(), v -> ModConfig.IGNORE_NAMED_MOBS.set(v)));
        allRows.add(boolRow("被激怒算敌对", "被激怒的中立生物也视作敌对",
                () -> ModConfig.TREAT_PROVOKED_AS_HOSTILE.get(), v -> ModConfig.TREAT_PROVOKED_AS_HOSTILE.set(v)));
        allRows.add(boolRow("吸经验音效", "吸取经验球时播放提示音",
                () -> ModConfig.PLAY_SOUND.get(), v -> ModConfig.PLAY_SOUND.set(v)));

        // --- 播报与时长 ---
        allRows.add(boolRow("击杀播报", "击杀时在聊天栏播报（总开关）",
                () -> ModConfig.GLOBAL_ANNOUNCE_ENABLED.get(), v -> ModConfig.GLOBAL_ANNOUNCE_ENABLED.set(v)));
        allRows.add(dblRow("播报半径", "播报可见距离（方块），默认 64",
                () -> ModConfig.ANNOUNCE_RANGE.get(), v -> ModConfig.ANNOUNCE_RANGE.set(v)));
        allRows.add(intRow("发光持续", "发光效果持续 tick，20 = 1 秒",
                () -> ModConfig.GLOW_DURATION.get(), v -> ModConfig.GLOW_DURATION.set(v)));
        allRows.add(intRow("减速持续", "减速效果持续 tick，20 = 1 秒",
                () -> ModConfig.SLOW_DURATION.get(), v -> ModConfig.SLOW_DURATION.set(v)));

        // --- 副手火把 ---
        allRows.add(boolRow("启用副手", "副手持火把/装备护符时是否生效",
                () -> ModConfig.OFFHAND_ENABLED.get(), v -> ModConfig.OFFHAND_ENABLED.set(v)));
        allRows.add(intRow("副手区块半径", "伤害范围（区块），1 = 3x3 区块",
                () -> ModConfig.OFFHAND_CHUNK_RADIUS.get(), v -> ModConfig.OFFHAND_CHUNK_RADIUS.set(v)));
        allRows.add(intRow("副手间隔", "tick 数，1 = 每 tick，20 = 1 秒",
                () -> ModConfig.OFFHAND_INTERVAL.get(), v -> ModConfig.OFFHAND_INTERVAL.set(v)));
        allRows.add(boolRow("副手播报", "副手击杀时是否播报",
                () -> ModConfig.OFFHAND_ANNOUNCE.get(), v -> ModConfig.OFFHAND_ANNOUNCE.set(v)));
        allRows.add(boolRow("副手发光", "被攻击目标获得发光效果",
                () -> ModConfig.OFFHAND_GLOWING.get(), v -> ModConfig.OFFHAND_GLOWING.set(v)));
        allRows.add(boolRow("副手减速", "被攻击目标获得减速效果",
                () -> ModConfig.OFFHAND_SLOW.get(), v -> ModConfig.OFFHAND_SLOW.set(v)));
        allRows.add(boolRow("副手传掉落", "副手击杀掉落物传送到玩家",
                () -> ModConfig.OFFHAND_TELEPORT_DROPS.get(), v -> ModConfig.OFFHAND_TELEPORT_DROPS.set(v)));
        allRows.add(boolRow("副手传经验", "副手击杀经验球传送到玩家",
                () -> ModConfig.OFFHAND_TELEPORT_XP.get(), v -> ModConfig.OFFHAND_TELEPORT_XP.set(v)));

        // --- 新火把默认值 ---
        allRows.add(boolRow("默认启用", "新放置的火把是否默认开启",
                () -> ModConfig.DEFAULT_ENABLED.get(), v -> ModConfig.DEFAULT_ENABLED.set(v)));
        allRows.add(boolRow("默认区块模式", "开 = 区块范围；关 = 方块范围",
                () -> ModConfig.DEFAULT_CHUNK_MODE.get(), v -> ModConfig.DEFAULT_CHUNK_MODE.set(v)));
        allRows.add(intRow("默认区块半径", "0 = 1x1，1 = 3x3，2 = 5x5",
                () -> ModConfig.DEFAULT_CHUNK_RADIUS.get(), v -> ModConfig.DEFAULT_CHUNK_RADIUS.set(v)));
        allRows.add(intRow("默认方块半径", "方块模式下的半径（格）",
                () -> ModConfig.DEFAULT_BLOCK_RADIUS.get(), v -> ModConfig.DEFAULT_BLOCK_RADIUS.set(v)));
        allRows.add(dblRow("默认伤害", "2.0 = 1 颗心；0 = 只标记不伤害",
                () -> ModConfig.DEFAULT_DAMAGE.get(), v -> ModConfig.DEFAULT_DAMAGE.set(v)));
        allRows.add(intRow("默认间隔", "伤害间隔 tick，20 = 1 秒",
                () -> ModConfig.DEFAULT_INTERVAL.get(), v -> ModConfig.DEFAULT_INTERVAL.set(v)));
        allRows.add(boolRow("默认发光", "新火把是否默认给目标发光",
                () -> ModConfig.DEFAULT_GLOWING.get(), v -> ModConfig.DEFAULT_GLOWING.set(v)));
        allRows.add(boolRow("默认减速", "新火把是否默认给目标减速",
                () -> ModConfig.DEFAULT_SLOW.get(), v -> ModConfig.DEFAULT_SLOW.set(v)));
        allRows.add(boolRow("默认播报", "新火把是否默认播报击杀",
                () -> ModConfig.DEFAULT_ANNOUNCE.get(), v -> ModConfig.DEFAULT_ANNOUNCE.set(v)));
        allRows.add(boolRow("默认传掉落", "新火把是否默认传送掉落物",
                () -> ModConfig.DEFAULT_TELEPORT_DROPS.get(), v -> ModConfig.DEFAULT_TELEPORT_DROPS.set(v)));
        allRows.add(boolRow("默认传经验", "新火把是否默认传送经验",
                () -> ModConfig.DEFAULT_TELEPORT_XP.get(), v -> ModConfig.DEFAULT_TELEPORT_XP.set(v)));
    }

    /* ================= 行工厂 ================= */

    private Row boolRow(String label, String tip,
                        Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return new Row() {
            public String label() { return label; }
            public void build(GlobalSettingsScreen s, int x, int y) {
                Button b = Button.builder(onOff(safeGetB(getter)), btn -> {
                    boolean now = !safeGetB(getter);
                    setter.accept(now);
                    btn.setMessage(onOff(now));
                }).bounds(x, y, s.boxW, s.boxH)
                        .tooltip(Tooltip.create(Component.literal(tip)))
                        .build();
                s.addRenderableWidget(b);
            }
        };
    }

    private Row intRow(String label, String tip,
                       Supplier<Integer> getter, Consumer<Integer> setter) {
        return new Row() {
            public String label() { return label; }
            public void build(GlobalSettingsScreen s, int x, int y) {
                EditBox box = new EditBox(s.font, x, y, s.boxW, s.boxH, Component.literal(label));
                box.setValue(String.valueOf(safeGetI(getter)));
                box.setTooltip(Tooltip.create(Component.literal(tip)));
                box.setResponder(t -> {
                    try { setter.accept(Integer.parseInt(t.trim())); } catch (Exception ignored) {}
                });
                s.addRenderableWidget(box);
            }
        };
    }

    private Row dblRow(String label, String tip,
                       Supplier<Double> getter, Consumer<Double> setter) {
        return new Row() {
            public String label() { return label; }
            public void build(GlobalSettingsScreen s, int x, int y) {
                EditBox box = new EditBox(s.font, x, y, s.boxW, s.boxH, Component.literal(label));
                box.setValue(String.format("%.1f", safeGetD(getter)));
                box.setTooltip(Tooltip.create(Component.literal(tip)));
                box.setResponder(t -> {
                    try { setter.accept(Double.parseDouble(t.trim())); } catch (Exception ignored) {}
                });
                s.addRenderableWidget(box);
            }
        };
    }

    /* ================= 自适应布局计算 ================= */

    /** 预留：标题 + 页码 + 底部两行按钮 */
    private static final int RESERVED_TOP = 26;
    private static final int RESERVED_BOTTOM = 56;

    @Override
    protected void init() {
        /* -------- 1. 决定列数：窄屏单列，宽屏双列 -------- */
        // 单列最小需要 ≈ 标签 68 + 控件 76 = 144
        int minColW = 68 + 76;
        cols = (this.width >= minColW * 2 + 24) ? 2 : 1;

        /* -------- 2. 决定控件宽度 -------- */
        int usableW = this.width - 16;                       // 左右各留 8
        boxW = BOX_W_MAX;
        labelW = 68;
        int colW = labelW + boxW;
        int needW = colW * cols + (cols > 1 ? 10 : 0);
        if (needW > usableW) {
            // 宽度不够：先压控件宽度
            int shrink = needW - usableW;
            boxW = Math.max(48, boxW - shrink / cols);
            colW = labelW + boxW;
            needW = colW * cols + (cols > 1 ? 10 : 0);
            // 还不够就压标签宽度
            if (needW > usableW) {
                labelW = Math.max(44, labelW - (needW - usableW) / cols);
                colW = labelW + boxW;
            }
        }

        /* -------- 3. 决定行高：按可用高度压缩 -------- */
        int availH = this.height - RESERVED_TOP - RESERVED_BOTTOM;
        int maxRowsByHeight = Math.max(1, availH / (BOX_H + 2));

        // 每页能放多少「项」（项 = 一个控件）
        int itemsPerPageByHeight = maxRowsByHeight * cols;
        // 这里固定每页 8 项，避免翻页太碎；若屏幕装不下则按能装的走
        int wantPerPage = 8;
        int itemsPerPage = Math.min(wantPerPage, Math.max(cols, itemsPerPageByHeight));
        rowsPerPage = Math.max(1, itemsPerPage / cols);

        // 行高 = 可用高度 / 实际行数，夹在 [MIN_ROW_H, BOX_H+2]
        rowH = Math.min(BOX_H + 2, Math.max(MIN_ROW_H, availH / rowsPerPage));
        boxH = Math.min(BOX_H, rowH - 1);
        if (boxH < 10) boxH = 10;

        /* -------- 4. 分页数 -------- */
        pageCount = Math.max(1, (allRows.size() + itemsPerPage - 1) / itemsPerPage);
        if (page >= pageCount) page = pageCount - 1;

        /* -------- 5. 定位 -------- */
        int blockW = colW * cols + (cols > 1 ? 10 : 0);
        this.px = (this.width - blockW) / 2;
        // 内容垂直居中于「标题之下、按钮之上」的区域
        int contentH = rowsPerPage * rowH;
        this.py = RESERVED_TOP + Math.max(0, (availH - contentH) / 2);

        buildPage();
    }

    /** 当前页对应的行切片 */
    private int itemsPerPage() {
        return rowsPerPage * cols;
    }

    private void buildPage() {
        this.clearWidgets();

        int start = page * itemsPerPage();
        int end = Math.min(allRows.size(), start + itemsPerPage());

        int colStep = labelW + boxW + 10;

        for (int i = start; i < end; i++) {
            int idx = i - start;
            int col = idx % cols;
            int rowIdx = idx / cols;
            int x = px + col * colStep + labelW;
            int y = py + rowIdx * rowH;
            allRows.get(i).build(this, x, y);
        }

        /* ---- 底部按钮：两行，全部保证在屏幕内 ---- */
        int cx = this.width / 2;

        // 第一行：翻页 + 恢复默认
        int bw = Math.max(40, Math.min(72, (this.width - 32) / 3));
        int gap = 6;
        int totalBW = bw * 3 + gap * 2;
        int bx0 = cx - totalBW / 2;

        // 从屏幕底部往上排，确保不出界
        int byClose = this.height - 24;          // 关闭按钮所在行
        int byMain = byClose - 24;               // 翻页那行

        addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
            if (page > 0) { page--; buildPage(); }
        }).bounds(bx0, byMain, bw, 20)
                .tooltip(Tooltip.create(Component.literal("上一页")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("恢复默认"), b -> {
            resetToDefaults();
            buildPage();
        }).bounds(bx0 + bw + gap, byMain, bw, 20)
                .tooltip(Tooltip.create(Component.literal("恢复所有全局配置为默认值")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            if (page + 1 < pageCount) { page++; buildPage(); }
        }).bounds(bx0 + (bw + gap) * 2, byMain, bw, 20)
                .tooltip(Tooltip.create(Component.literal("下一页")))
                .build());

        // 第二行：保存并返回 / 取消
        int bw2 = Math.max(52, Math.min(88, (this.width - 32) / 2));
        int gap2 = 8;
        int bx2 = cx - (bw2 * 2 + gap2) / 2;

        addRenderableWidget(Button.builder(Component.literal("保存并返回"), b -> {
            saveConfig();
            minecraft.setScreen(parent);
        }).bounds(bx2, byClose, bw2, 20)
                .tooltip(Tooltip.create(Component.literal("保存所有修改并返回上一级")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b -> {
            minecraft.setScreen(parent);
        }).bounds(bx2 + bw2 + gap2, byClose, bw2, 20)
                .tooltip(Tooltip.create(Component.literal("不保存，直接返回")))
                .build());
    }

    /* ================= 工具 ================= */

    private static boolean safeGetB(Supplier<Boolean> s) {
        try { return s.get(); } catch (Throwable t) { return false; }
    }
    private static int safeGetI(Supplier<Integer> s) {
        try { return s.get(); } catch (Throwable t) { return 0; }
    }
    private static double safeGetD(Supplier<Double> s) {
        try { return s.get(); } catch (Throwable t) { return 0; }
    }

    private static Component onOff(boolean on) {
        return Component.literal(on ? "§a开" : "§c关");
    }

    private void saveConfig() {
        try {
            java.lang.reflect.Method m = ModConfig.SPEC.getClass().getMethod("save");
            m.invoke(ModConfig.SPEC);
        } catch (Throwable ignored) {
        }
    }

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

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);
        super.render(gg, mouseX, mouseY, partialTick);

        // 标题（在最上方）
        gg.drawCenteredString(this.font, "全局设置", this.width / 2, 6, 0xFFD700);

        // 页码信息
        String pg = "第 " + (page + 1) + " / " + pageCount + " 页";
        gg.drawCenteredString(this.font, pg, this.width / 2, 18, 0xAAAAAA);

        // 标签
        int start = page * itemsPerPage();
        int end = Math.min(allRows.size(), start + itemsPerPage());
        int colStep = labelW + boxW + 10;

        for (int i = start; i < end; i++) {
            int idx = i - start;
            int col = idx % cols;
            int rowIdx = idx / cols;
            int lx = px + col * colStep;
            int ly = py + rowIdx * rowH;
            // 垂直居中对齐控件
            int textY = ly + (boxH - 8) / 2;
            gg.drawString(this.font, allRows.get(i).label(), lx, textY, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dy > 0 && page > 0) {
            page--;
            buildPage();
            return true;
        } else if (dy < 0 && page + 1 < pageCount) {
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