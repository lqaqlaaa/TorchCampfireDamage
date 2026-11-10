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
    private int sideW;
    private int contentX, contentY, contentW, contentH;

    /* ===== 数据 ===== */
    private interface Row {
        void build(GlobalSettingsScreen s, int x, int y);
        String label();
    }

    private final List<Row> allRows = new ArrayList<>();
    private int page = 0;
    private int pageCount = 1;
    // 0 = 全局规则，1 = Curios 护符，2 = 新放置火把默认值
    private int activeCategory = 0;

    private List<Row> visibleRows() {
        int from = activeCategory == 0 ? 0 : activeCategory == 1 ? 10 : 21;
        int to = activeCategory == 0 ? 10 : activeCategory == 1 ? 21 : allRows.size();
        return allRows.subList(Math.min(from, allRows.size()), Math.min(to, allRows.size()));
    }

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
        allRows.add(boolRow("忽略命名生物", "已命名的生物不会被伤害（白名单），Curios 护符同样生效",
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

        // --- Curios 护符 ---
        allRows.add(boolRow("启用护符", "Curios 饰品栏装备护符时是否生效",
                () -> ModConfig.OFFHAND_ENABLED.get(), v -> ModConfig.OFFHAND_ENABLED.set(v)));
        allRows.add(intRow("护符区块半径", "伤害范围（区块），1 = 3x3 区块",
                () -> ModConfig.OFFHAND_CHUNK_RADIUS.get(), v -> ModConfig.OFFHAND_CHUNK_RADIUS.set(v)));
        allRows.add(intRow("护符攻击间隔", "tick 数，1 = 每 tick，20 = 1 秒",
                () -> ModConfig.OFFHAND_INTERVAL.get(), v -> ModConfig.OFFHAND_INTERVAL.set(v)));
        allRows.add(boolRow("护符固定伤害模式", "关闭：使用玩家攻击力；开启：使用下方固定伤害",
                () -> ModConfig.OFFHAND_FIXED_DAMAGE_MODE.get(), v -> ModConfig.OFFHAND_FIXED_DAMAGE_MODE.set(v)));
        allRows.add(dblRow("护符固定伤害", "仅固定伤害模式生效，2.0 = 1 颗心",
                () -> ModConfig.OFFHAND_FIXED_DAMAGE.get(), v -> ModConfig.OFFHAND_FIXED_DAMAGE.set(Math.max(0.0, Math.min(2048.0, v)))));
        allRows.add(boolRow("护符击杀播报", "护符击杀时是否播报",
                () -> ModConfig.OFFHAND_ANNOUNCE.get(), v -> ModConfig.OFFHAND_ANNOUNCE.set(v)));
        allRows.add(boolRow("护符发光", "被攻击目标获得发光效果",
                () -> ModConfig.OFFHAND_GLOWING.get(), v -> ModConfig.OFFHAND_GLOWING.set(v)));
        allRows.add(boolRow("护符减速", "被攻击目标获得减速效果",
                () -> ModConfig.OFFHAND_SLOW.get(), v -> ModConfig.OFFHAND_SLOW.set(v)));
        allRows.add(boolRow("护符传掉落", "护符击杀掉落物传送到玩家",
                () -> ModConfig.OFFHAND_TELEPORT_DROPS.get(), v -> ModConfig.OFFHAND_TELEPORT_DROPS.set(v)));
        allRows.add(boolRow("护符传经验", "护符击杀经验球传送到玩家",
                () -> ModConfig.OFFHAND_TELEPORT_XP.get(), v -> ModConfig.OFFHAND_TELEPORT_XP.set(v)));
        allRows.add(boolRow("护符忽略命名生物", "护符不会攻击带有自定义名称的生物",
                () -> ModConfig.OFFHAND_IGNORE_NAMED_MOBS.get(), v -> ModConfig.OFFHAND_IGNORE_NAMED_MOBS.set(v)));

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
    private static final int RESERVED_TOP = 50;
    private static final int RESERVED_BOTTOM = 56;

    @Override
    protected void init() {
        // 左侧导航与右侧内容区始终按逻辑分辨率计算，适配 GUI 缩放。
        sideW = Math.max(76, Math.min(104, this.width / 4));
        contentX = 12 + sideW + 8;
        contentY = 42;
        contentW = Math.max(120, this.width - contentX - 12);
        contentH = Math.max(48, this.height - contentY - 36);

        cols = contentW >= 430 ? 2 : 1;
        int gap = cols == 2 ? 14 : 0;
        int colW = (contentW - gap) / cols;
        labelW = Math.min(112, Math.max(64, colW / 2));
        boxW = Math.max(48, colW - labelW - 12);
        boxH = 20;
        rowH = 30;
        rowsPerPage = Math.max(1, Math.min(8, contentH / rowH));

        List<Row> rows = visibleRows();
        int perPage = rowsPerPage * cols;
        pageCount = Math.max(1, (rows.size() + perPage - 1) / perPage);
        if (page >= pageCount) page = pageCount - 1;
        buildPage();
    }

    /** 当前页对应的行切片 */
    private int itemsPerPage() {
        return rowsPerPage * cols;
    }

    private void buildPage() {
        this.clearWidgets();

        // 左侧分类导航
        int navX = 12;
        int navY = 48;
        int navButtonH = 24;
        int navGap = 5;
        String[] tabs = {"通用设置", "饰品栏护符", "放置火把", "生物列表"};
        String[] icons = {"⚙", "⚔", "✦", "♟"};
        for (int i = 0; i < tabs.length; i++) {
            final int category = i;
            String label = icons[i] + " " + tabs[i];
            Button button = Button.builder(Component.literal(label), b -> {
                if (category == 3) {
                    minecraft.setScreen(new MobTargetsScreen(this));
                    return;
                }
                activeCategory = category;
                page = 0;
                init();
            }).bounds(navX, navY + i * (navButtonH + navGap), sideW, navButtonH).build();
            addRenderableWidget(button);
        }

        // 当前分类的设置项
        int start = page * itemsPerPage();
        List<Row> rows = visibleRows();
        int end = Math.min(rows.size(), start + itemsPerPage());
        int gap = cols == 2 ? 14 : 0;
        int colW = (contentW - gap) / cols;
        int top = contentY + 25;
        for (int i = start; i < end; i++) {
            int idx = i - start;
            int col = idx % cols;
            int rowIdx = idx / cols;
            int baseX = contentX + col * (colW + gap);
            int y = top + rowIdx * rowH;
            rows.get(i).build(this, baseX + labelW + 8, y);
        }

        // 分页与操作按钮
        int footerY = this.height - 25;
        int smallW = Math.max(38, Math.min(58, (contentW - 12) / 3));
        int footerGap = 5;
        int total = smallW * 3 + footerGap * 2;
        int footerX = contentX + Math.max(0, (contentW - total) / 2);
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
            if (page > 0) { page--; buildPage(); }
        }).bounds(footerX, footerY, smallW, 20).tooltip(Tooltip.create(Component.literal("上一页"))).build());
        addRenderableWidget(Button.builder(Component.literal("基础默认"), b -> {
            resetToDefaults();
            buildPage();
        }).bounds(footerX + smallW + footerGap, footerY, smallW, 20)
                .tooltip(Tooltip.create(Component.literal("恢复通用规则中的默认值"))).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            if (page + 1 < pageCount) { page++; buildPage(); }
        }).bounds(footerX + (smallW + footerGap) * 2, footerY, smallW, 20)
                .tooltip(Tooltip.create(Component.literal("下一页"))).build());

        int backW = Math.min(76, Math.max(52, sideW - 4));
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> {
            saveConfig();
            minecraft.setScreen(parent);
        }).bounds(navX, this.height - 25, backW, 20).build());
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

            saveConfig();
        } catch (Throwable ignored) {
        }
    }

    /* ================= 渲染 ================= */

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);

        // Shared dark workspace and blue brand accents.
        GuiLayout.drawFrame(gg, this.width, this.height);
        gg.fill(12, 40, 12 + sideW, this.height - 12, 0xC9101B2B);
        gg.fill(contentX - 2, 40, this.width - 12, this.height - 12, 0x66121F32);
        gg.renderOutline(12, 40, sideW, this.height - 52, 0xFF263F60);
        gg.renderOutline(contentX - 2, 40, this.width - contentX - 10, this.height - 52, 0xFF263F60);

        super.render(gg, mouseX, mouseY, partialTick);

        String categoryName = activeCategory == 0 ? "通用设置" : activeCategory == 1 ? "副手火把设置" : "放置火把默认设置";
        gg.fill(16, 29, 82, 31, 0xFF398BFF);
        gg.drawString(font, "守卫火把", 16, 12, 0xFF70AEFF, false);
        gg.drawString(font, categoryName, contentX + 8, 12, 0xFFFFFFFF, false);
        gg.drawString(font, "第 " + (page + 1) + " / " + pageCount + " 页", this.width - 70, 12, 0xFFB8B8B8, false);

        int start = page * itemsPerPage();
        List<Row> rows = visibleRows();
        int end = Math.min(rows.size(), start + itemsPerPage());
        int gap = cols == 2 ? 14 : 0;
        int colW = (contentW - gap) / cols;
        int top = contentY + 25;
        for (int i = start; i < end; i++) {
            int idx = i - start;
            int col = idx % cols;
            int rowIdx = idx / cols;
            int baseX = contentX + col * (colW + gap);
            int y = top + rowIdx * rowH;
            gg.fill(baseX + 2, y - 3, baseX + colW - 2, y + 24, 0x66304765);
            gg.fill(baseX + 2, y - 3, baseX + 4, y + 24, 0xFF398BFF);
            gg.drawString(font, rows.get(i).label(), baseX + 7, y + 6, 0xFFE6E6E6, false);
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
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}