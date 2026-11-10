package com.torchcampfiredamage.client;

import com.torchcampfiredamage.block.WardSourceBlockEntity;
import com.torchcampfiredamage.network.UpdateWardPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 守卫火源参数设置界面。
 * 字段：开关 / 范围类型 / 伤害范围 / 伤害 / 伤害间隔 /
 *      目标发光 / 目标减速 / 击杀播报 / 传送掉落物 / 传送经验
 */
public class WardConfigScreen extends Screen {
    private final BlockPos pos;
    private final WardSourceBlockEntity be;

    private EditBox rangeBox;
    private EditBox damageBox;
    private EditBox intervalBox;

    private boolean enabled;
    private boolean chunkMode;   // true = 区块, false = 方块
    private boolean glowing;
    private boolean slow;
    private boolean announce;
    private boolean dropTeleport;
    private boolean xpTeleport;
    /** 本火把是否忽略被命名的生物（白名单） */
    private boolean ignoreNamed;
    private boolean showRange;
    /** 输入框的「工作值」：init() 重建时以它为准，避免从子界面返回被 BE 覆盖 */
    private String rangeText;
    private String damageText;
    private String intervalText;

    // 布局
    private int ROW_H = 22;
    private int LABEL_W = 56;
    private int BOX_W = 78;
    private int BTN_W = 78;
    private int CONTROL_H = 20;
    private int bottomButtonW = 88;
    private int layoutGap = 6;

    /** 最近一次打开的主界面所属方块，供子界面访问 */
    public static WardSourceBlockEntity lastBE;

    public WardConfigScreen(BlockPos pos, WardSourceBlockEntity be) {
        super(Component.literal("守卫火源设置"));
        this.pos = pos;
        this.be = be;
        lastBE = be;
        this.enabled = be.isEnabled();
        this.chunkMode = be.isChunkMode();
        this.glowing = be.isGlowing();
        this.slow = be.isSlow();
        this.announce = be.isAnnounce();
        this.dropTeleport = be.isTeleportDrops();
        this.xpTeleport = be.isTeleportXp();
        this.ignoreNamed = be.isIgnoreNamed();
        this.showRange = (Minecraft.getInstance().level != null)
                && RangeOverlay.has(Minecraft.getInstance().level.dimension(), pos);
        this.rangeText = String.valueOf(be.isChunkMode() ? be.getChunkRadius() : be.getBlockRadius());
        this.damageText = String.valueOf(be.getDamage());
        this.intervalText = String.valueOf(be.getInterval());
    }

    // O 键主界面同款：左侧导航 + 右侧内容工作区 + 底部固定操作区
    private int sideW, contentX, contentY, contentW, contentH;
    private int rowH, controlH, labelW, controlW, columnGap, columns;
    private int leftColumnX, rightColumnX, rowsTop, rowsPerColumn, page, pageCount, itemsPerPage;

    private static final String[] SETTING_LABELS = {
            "开关", "范围类型", "伤害范围", "伤害", "伤害间隔", "攻击生物",
            "目标发光", "目标减速", "击杀播报", "传送掉落物", "传送经验", "忽略命名生物"
    };

    @Override
    protected void init() {
        activeInstance = this;
        sideW = Math.max(64, Math.min(104, width / 4));
        contentX = 12 + sideW + 8;
        contentY = 40;
        contentW = Math.max(80, width - contentX - 12);
        contentH = Math.max(64, height - contentY - 34);

        columns = contentW >= 430 ? 2 : 1;
        columnGap = columns == 2 ? 14 : 0;
        int colW = Math.max(80, (contentW - columnGap) / columns);
        labelW = Math.min(112, Math.max(46, colW / 3));
        controlW = Math.max(34, colW - labelW - 16);
        controlH = Math.max(12, Math.min(20, (height - 70) / 9));
        rowsTop = contentY + 28;
        int footerReserve = 34;
        int availableRowsH = Math.max(controlH + 3, height - rowsTop - footerReserve - 8);
        rowH = Math.max(controlH + 3, Math.min(32, availableRowsH / 6));
        rowsPerColumn = Math.max(1, Math.min(6, availableRowsH / rowH));
        itemsPerPage = rowsPerColumn * columns;
        pageCount = Math.max(1, (SETTING_LABELS.length + itemsPerPage - 1) / itemsPerPage);
        page = Math.max(0, Math.min(page, pageCount - 1));

        leftColumnX = contentX;
        rightColumnX = contentX + (columns == 2 ? colW + columnGap : 0);
        clearWidgets();

        String[] nav = {"参数设置", "生物列表", "伤害类型", "显示设置", "星环设置", "全局设置"};
        int navY = 46;
        int navGap = 3;
        int navH = Math.max(15, Math.min(23, (height - 78 - navGap * 5) / 6));
        for (int i = 0; i < nav.length; i++) {
            final int item = i;
            addRenderableWidget(Button.builder(Component.literal(nav[i]), b -> {
                switch (item) {
                    case 1 -> Minecraft.getInstance().setScreen(new WardMobTargetsScreen(this, be));
                    case 2 -> Minecraft.getInstance().setScreen(new WardEnchantmentsScreen(this, pos, be.getSpecialDamageTypes()));
                    case 3 -> { DisplaySettingsScreen.loadFrom(be); Minecraft.getInstance().setScreen(new DisplaySettingsScreen(this)); }
                    case 4 -> { RingSettingsScreen.loadFrom(be); Minecraft.getInstance().setScreen(new RingSettingsScreen(this)); }
                    case 5 -> Minecraft.getInstance().setScreen(new GlobalSettingsScreen(this));
                    default -> { page = 0; init(); }
                }
            }).bounds(12, navY + i * (navH + navGap), sideW, navH).build());
        }

        int colWActual = (contentW - columnGap) / columns;
        int start = page * itemsPerPage;
        int end = Math.min(SETTING_LABELS.length, start + itemsPerPage);
        for (int index = start; index < end; index++) {
            int local = index - start;
            int col = local / rowsPerColumn;
            int row = local % rowsPerColumn;
            int baseX = contentX + col * (colWActual + columnGap);
            int x = baseX + labelW + 8;
            int y = rowsTop + row * rowH;
            addSettingWidget(index, x, y);
        }

        int footerY = height - 24;
        int footerW = Math.max(32, Math.min(66, (contentW - 20) / 4));
        int footerGap = 4;
        int totalW = footerW * 4 + footerGap * 3;
        int footerX = contentX + Math.max(0, (contentW - totalW) / 2);
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> { if (page > 0) { page--; init(); } })
                .bounds(footerX, footerY, footerW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("保存"), b -> save()).bounds(footerX + footerW + footerGap, footerY, footerW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose()).bounds(footerX + (footerW + footerGap) * 2, footerY, footerW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> { if (page + 1 < pageCount) { page++; init(); } })
                .bounds(footerX + (footerW + footerGap) * 3, footerY, footerW, 20).build());
    }

    private void addSettingWidget(int index, int x, int y) {
        switch (index) {
            case 0 -> addRenderableWidget(Button.builder(onOffText(enabled), b -> {
                enabled = !enabled; b.setMessage(onOffText(enabled));
            }).bounds(x, y, controlW, controlH).build());
            case 1 -> addRenderableWidget(Button.builder(rangeTypeText(), b -> {
                chunkMode = !chunkMode; b.setMessage(rangeTypeText());
                rangeText = String.valueOf(convertRange()); if (rangeBox != null) rangeBox.setValue(rangeText);
            }).bounds(x, y, controlW, controlH).build());
            case 2 -> {
                rangeBox = new EditBox(font, x, y, controlW, controlH, Component.literal("伤害范围"));
                rangeBox.setValue(rangeText); rangeBox.setResponder(t -> rangeText = t);
                rangeBox.setTooltip(Tooltip.create(Component.literal("区块模式：0=1x1区块，1=3x3，2=5x5；方块模式：格数半径")));
                addRenderableWidget(rangeBox);
            }
            case 3 -> {
                damageBox = new EditBox(font, x, y, controlW, controlH, Component.literal("伤害"));
                damageBox.setValue(damageText); damageBox.setResponder(t -> damageText = t);
                addRenderableWidget(damageBox);
            }
            case 4 -> {
                intervalBox = new EditBox(font, x, y, controlW, controlH, Component.literal("伤害间隔"));
                intervalBox.setValue(intervalText); intervalBox.setResponder(t -> intervalText = t);
                addRenderableWidget(intervalBox);
            }
            case 5 -> addRenderableWidget(Button.builder(Component.literal("打开生物列表 ›"),
                    b -> Minecraft.getInstance().setScreen(new WardMobTargetsScreen(this, be)))
                    .bounds(x, y, controlW, controlH).build());
            case 6 -> addToggle(x, y, () -> glowing, v -> glowing = v);
            case 7 -> addToggle(x, y, () -> slow, v -> slow = v);
            case 8 -> addToggle(x, y, () -> announce, v -> announce = v);
            case 9 -> addToggle(x, y, () -> dropTeleport, v -> dropTeleport = v);
            case 10 -> addToggle(x, y, () -> xpTeleport, v -> xpTeleport = v);
            case 11 -> addToggle(x, y, () -> ignoreNamed, v -> ignoreNamed = v);
        }
    }

    private void addToggle(int x, int y, java.util.function.Supplier<Boolean> getter,
                           java.util.function.Consumer<Boolean> setter) {
        addRenderableWidget(Button.builder(onOffText(getter.get()), button -> {
            boolean next = !getter.get();
            setter.accept(next);
            button.setMessage(onOffText(next));
        }).bounds(x, y, controlW, controlH).build());
    }

    private Component toggleText(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "开" : "关"));
    }

    private Component onOffText(boolean on) {
        return Component.literal(on ? "开" : "关");
    }

    private Component rangeTypeText() {
        return Component.literal("范围类型: " + (chunkMode ? "区块" : "方块"));
    }

    /** 在区块/方块之间切换时，尽量保持范围可比 */
    private int convertRange() {
        if (chunkMode) {
            // 从方块切到区块：格数 → 区块数（向上取整）
            int blocks = be.getBlockRadius();
            return Math.max(0, Math.min(4, (blocks + 8) / 16));
        } else {
            // 从区块切到方块：区块数 → 格数
            int chunks = be.getChunkRadius();
            return Math.max(0, Math.min(64, chunks * 16));
        }
    }

    private Component showRangeText() {
        return Component.literal("显示范围: " + (showRange ? "开" : "关"));
    }

    /** 根据当前 GUI 数值刷新范围显示 */
    private void updateOverlay() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (!showRange) {
            RangeOverlay.remove(mc.level.dimension(), pos);
            return;
        }

        int range = parseInt(rangeText, chunkMode ? 1 : 16,
                chunkMode ? 0 : 0, chunkMode ? 32 : 512);
        RangeOverlay.alpha = DisplaySettingsScreen.alpha;
        RangeOverlay.RangeBox box = RangeOverlay.makeBox(
                pos, chunkMode, range, range,
                DisplaySettingsScreen.blockA, DisplaySettingsScreen.blockB);
        RangeOverlay.set(mc.level.dimension(), pos, box);
    }

    /** 把星环当前配置发送给服务端（供子界面调用） */
    public static void sendOrbit(BlockPos pos) {
        var beanRings = RingSettingsScreen.toBeanRings();
        int ringCount = beanRings.size();
        var ringData = new com.torchcampfiredamage.network.UpdateOrbitPayload.RingData[ringCount];
        for (int i = 0; i < ringCount; i++) {
            var r = beanRings.get(i);
            ringData[i] = new com.torchcampfiredamage.network.UpdateOrbitPayload.RingData(
                    r.item, r.radius, r.speed, r.angle,
                    r.tiltX, r.tiltY, r.tiltZ,
                    r.color, r.alpha, r.width, r.style, r.spacing);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.torchcampfiredamage.network.UpdateOrbitPayload(
                        pos,
                        DisplaySettingsScreen.orbitVisible,
                        DisplaySettingsScreen.centerItem,
                        parseF(DisplaySettingsScreen.centerScale, 0.9f),
                        parseF(DisplaySettingsScreen.centerSpin, 1.5f),
                        ringData));
    }

    private void save() {
        saveNow();
        updateOverlay();
        onClose();
    }

    /**
     * 立即把当前界面 + 子界面（星环 / 显示）的所有设置保存到服务端。
     * <p>
     * 供子界面直接调用，这样在子界面里改完就能立刻保存，不必退回主界面。
     */
    public static void saveNow() {
        if (lastBE == null) return;
        WardConfigScreen inst = activeInstance;
        if (inst != null) {
            inst.saveInternal();
        } else {
            // 没有主界面实例时（理论上不会发生），只同步星环部分
            sendOrbit(lastBE.getBlockPos());
        }
    }

    /** 当前打开的主界面实例（供静态保存用） */
    private static WardConfigScreen activeInstance;

    private void saveInternal() {
        int range = parseInt(rangeText, chunkMode ? 1 : 16,
                chunkMode ? 0 : 0, chunkMode ? 32 : 512);
        double damage = parseDouble(damageText, 2.0);
        int interval = parseInt(intervalText, 20, 1, Integer.MAX_VALUE);

        PacketDistributor.sendToServer(new UpdateWardPayload(
                pos, enabled, chunkMode, range, damage, interval,
                glowing, slow, announce, dropTeleport, xpTeleport, ignoreNamed));

        be.applyAll(enabled, chunkMode, range, damage, interval,
                glowing, slow, announce, dropTeleport, xpTeleport, ignoreNamed);

        // 同步星环设置到服务端
        var beanRings = RingSettingsScreen.toBeanRings();
        int ringCount = beanRings.size();
        var ringData = new com.torchcampfiredamage.network.UpdateOrbitPayload.RingData[ringCount];
        for (int i = 0; i < ringCount; i++) {
            var r = beanRings.get(i);
            ringData[i] = new com.torchcampfiredamage.network.UpdateOrbitPayload.RingData(
                    r.item, r.radius, r.speed, r.angle,
                    r.tiltX, r.tiltY, r.tiltZ,
                    r.color, r.alpha, r.width, r.style, r.spacing);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.torchcampfiredamage.network.UpdateOrbitPayload(
                        pos,
                        DisplaySettingsScreen.orbitVisible,
                        DisplaySettingsScreen.centerItem,
                        parseF(DisplaySettingsScreen.centerScale, 0.9f),
                        parseF(DisplaySettingsScreen.centerSpin, 1.5f),
                        ringData));

        updateOverlay();
    }

    private static float parseF(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private int parseInt(String s, int def, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(s.trim())));
        } catch (Exception e) {
            return def;
        }
    }

    private double parseDouble(String s, double def) {
        try {
            return Math.max(0, Double.parseDouble(s.trim()));
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);
        GuiLayout.drawFrame(gg, width, height);
        gg.fill(12, 40, 12 + sideW, height - 12, 0xC9101B2B);
        gg.fill(contentX - 2, 40, width - 12, height - 12, 0x66121F32);
        gg.renderOutline(12, 40, sideW, height - 52, 0xFF263F60);
        gg.renderOutline(contentX - 2, 40, width - contentX - 10, height - 52, 0xFF263F60);
        super.render(gg, mouseX, mouseY, partialTick);

        gg.fill(16, 29, 82, 31, GuiLayout.ACCENT);
        gg.drawString(font, "守卫火把", 16, 12, 0xFF70AEFF, false);
        gg.drawString(font, "放置火把 · 参数设置", contentX + 8, 10, GuiLayout.TEXT, false);
        gg.drawString(font, "坐标: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(),
                contentX + 8, 24, GuiLayout.MUTED, false);

        int colW = (contentW - columnGap) / columns;
        int start = page * itemsPerPage;
        int end = Math.min(SETTING_LABELS.length, start + itemsPerPage);
        for (int index = start; index < end; index++) {
            int local = index - start;
            int col = local / rowsPerColumn;
            int row = local % rowsPerColumn;
            int x = contentX + col * (colW + columnGap);
            int y = rowsTop + row * rowH;
            gg.fill(x + 2, y - 2, x + colW - 2, y + controlH + 3, 0x66304765);
            gg.fill(x + 2, y - 2, x + 4, y + controlH + 3, GuiLayout.ACCENT);
            gg.drawString(font, SETTING_LABELS[index], x + 7,
                    y + Math.max(1, (controlH - 8) / 2), GuiLayout.TEXT, false);
        }
        gg.drawString(font, (page + 1) + " / " + pageCount, width - 44, 12, GuiLayout.MUTED, false);
    }

    @Override
    public void onClose() {
        if (!showRange) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) RangeOverlay.remove(mc.level.dimension(), pos);
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}