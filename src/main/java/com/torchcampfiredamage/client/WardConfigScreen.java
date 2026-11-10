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
    private int leftColumnX, rightColumnX, rowsTop;

    @Override
    protected void init() {
        activeInstance = this;
        sideW = Math.max(76, Math.min(104, width / 4));
        contentX = 12 + sideW + 8;
        contentY = 42;
        contentW = Math.max(120, width - contentX - 12);
        contentH = Math.max(48, height - contentY - 36);

        columns = contentW >= 430 ? 2 : 1;
        columnGap = columns == 2 ? 14 : 0;
        int colW = Math.max(110, (contentW - columnGap) / columns);
        labelW = Math.min(112, Math.max(58, colW / 3));
        controlW = Math.max(48, colW - labelW - 16);
        controlH = Math.max(14, Math.min(20, GuiLayout.px(20, width, height, 14)));
        rowH = Math.max(controlH + 7, Math.min(34, contentH / 7));
        rowsTop = contentY + 24;
        leftColumnX = contentX;
        rightColumnX = contentX + (columns == 2 ? colW + columnGap : 0);

        int cx = width / 2;
        // 左侧导航沿用 O 键菜单的入口组织方式
        String[] nav = {"参数设置", "生物列表", "攻击附魔", "显示设置", "星环设置", "全局设置"};
        int navY = 48;
        int navH = Math.max(20, Math.min(24, controlH + 4));
        int navGap = 5;
        for (int i = 0; i < nav.length; i++) {
            final int item = i;
            addRenderableWidget(Button.builder(Component.literal(nav[i]), b -> {
                switch (item) {
                    case 1 -> Minecraft.getInstance().setScreen(new WardMobTargetsScreen(this, be));
                    case 2 -> Minecraft.getInstance().setScreen(new WardEnchantmentsScreen(this, pos, be.getAttackEnchantments()));
                    case 3 -> {
                        DisplaySettingsScreen.loadFrom(be);
                        Minecraft.getInstance().setScreen(new DisplaySettingsScreen(this));
                    }
                    case 4 -> {
                        RingSettingsScreen.loadFrom(be);
                        Minecraft.getInstance().setScreen(new RingSettingsScreen(this));
                    }
                    case 5 -> Minecraft.getInstance().setScreen(new GlobalSettingsScreen(this));
                    default -> { }
                }
            }).bounds(12, navY + i * (navH + navGap), sideW, navH).build());
        }

        // 左列六项
        int lx = leftColumnX + labelW + 8;
        int y = rowsTop;
        addRenderableWidget(Button.builder(onOffText(enabled), b -> {
            enabled = !enabled; b.setMessage(onOffText(enabled));
        }).bounds(lx, y, controlW, controlH).build());
        y += rowH;
        addRenderableWidget(Button.builder(rangeTypeText(), b -> {
            chunkMode = !chunkMode; b.setMessage(rangeTypeText());
            rangeText = String.valueOf(convertRange()); rangeBox.setValue(rangeText);
        }).bounds(lx, y, controlW, controlH).build());
        y += rowH;
        rangeBox = new EditBox(font, lx, y, controlW, controlH, Component.literal("伤害范围"));
        rangeBox.setValue(rangeText);
        rangeBox.setResponder(t -> rangeText = t);
        rangeBox.setTooltip(Tooltip.create(Component.literal("区块模式：0=1x1区块，1=3x3，2=5x5；方块模式：格数半径")));
        addRenderableWidget(rangeBox);
        y += rowH;
        damageBox = new EditBox(font, lx, y, controlW, controlH, Component.literal("伤害"));
        damageBox.setValue(damageText); damageBox.setResponder(t -> damageText = t);
        damageBox.setTooltip(Tooltip.create(Component.literal("填 0 = 只发光/减速，不造成伤害")));
        addRenderableWidget(damageBox);
        y += rowH;
        intervalBox = new EditBox(font, lx, y, controlW, controlH, Component.literal("伤害间隔"));
        intervalBox.setValue(intervalText); intervalBox.setResponder(t -> intervalText = t);
        intervalBox.setTooltip(Tooltip.create(Component.literal("单位 tick；20 = 1 秒")));
        addRenderableWidget(intervalBox);
        y += rowH;
        addRenderableWidget(Button.builder(Component.literal("攻击生物名单  ›"), b ->
                Minecraft.getInstance().setScreen(new WardMobTargetsScreen(this, be)))
                .bounds(lx, y, controlW, controlH).build());

        // 右列七项；窄屏时放到第二组列位置，避免两列挤压
        int rx = (columns == 2 ? rightColumnX : contentX) + labelW + 8;
        int ry = rowsTop;
        addToggle(rx, ry, () -> glowing, v -> glowing = v); ry += rowH;
        addToggle(rx, ry, () -> slow, v -> slow = v); ry += rowH;
        addToggle(rx, ry, () -> announce, v -> announce = v); ry += rowH;
        addToggle(rx, ry, () -> dropTeleport, v -> dropTeleport = v); ry += rowH;
        addToggle(rx, ry, () -> xpTeleport, v -> xpTeleport = v); ry += rowH;
        addToggle(rx, ry, () -> ignoreNamed, v -> ignoreNamed = v); ry += rowH;
        addRenderableWidget(Button.builder(Component.literal("攻击附魔  ›"), b ->
                Minecraft.getInstance().setScreen(new WardEnchantmentsScreen(this, pos, be.getAttackEnchantments())))
                .bounds(rx, ry, controlW, controlH).build());

        int footerY = height - 25;
        int footerW = Math.max(54, Math.min(88, (contentW - 8) / 2));
        int footerGap = 8;
        int footerX = contentX + (contentW - footerW * 2 - footerGap) / 2;
        addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .bounds(footerX, footerY, footerW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(footerX + footerW + footerGap, footerY, footerW, 20).build());
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

        int range = parseInt(rangeBox.getValue(), chunkMode ? 1 : 16,
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
        int range = parseInt(rangeBox.getValue(), chunkMode ? 1 : 16,
                chunkMode ? 0 : 0, chunkMode ? 32 : 512);
        double damage = parseDouble(damageBox.getValue(), 2.0);
        int interval = parseInt(intervalBox.getValue(), 20, 1, Integer.MAX_VALUE);

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

        // 与 O 键菜单相同的导航栏、工作区和行卡片结构
        gg.fill(12, 40, 12 + sideW, height - 12, 0xC9101B2B);
        gg.fill(contentX - 2, 40, width - 12, height - 12, 0x66121F32);
        gg.renderOutline(12, 40, sideW, height - 52, 0xFF263F60);
        gg.renderOutline(contentX - 2, 40, width - contentX - 10, height - 52, 0xFF263F60);
        super.render(gg, mouseX, mouseY, partialTick);

        gg.fill(16, 29, 82, 31, GuiLayout.ACCENT);
        gg.drawString(font, "守卫火把", 16, 12, 0xFF70AEFF, false);
        gg.drawString(font, "放置火把 · 参数设置", contentX + 8, 12, GuiLayout.TEXT, false);
        gg.drawString(font, "坐标: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(),
                contentX + 8, 27, GuiLayout.MUTED, false);

        String[] leftLabels = {"开关", "范围类型", "伤害范围", "伤害", "伤害间隔", "攻击生物"};
        String[] rightLabels = {"目标发光", "目标减速", "击杀播报", "传送掉落物", "传送经验", "忽略命名生物", "攻击附魔"};
        int colW = columns == 2 ? (contentW - columnGap) / 2 : contentW;
        for (int i = 0; i < leftLabels.length; i++) {
            int y = rowsTop + i * rowH;
            int x = leftColumnX;
            gg.fill(x + 2, y - 3, x + colW - 2, y + controlH + 4, 0x66304765);
            gg.fill(x + 2, y - 3, x + 4, y + controlH + 4, GuiLayout.ACCENT);
            gg.drawString(font, leftLabels[i], x + 8, y + Math.max(2, (controlH - 8) / 2), GuiLayout.TEXT, false);
        }
        int rightX = columns == 2 ? rightColumnX : contentX;
        for (int i = 0; i < rightLabels.length; i++) {
            int y = rowsTop + i * rowH;
            gg.fill(rightX + 2, y - 3, rightX + colW - 2, y + controlH + 4, 0x66304765);
            gg.fill(rightX + 2, y - 3, rightX + 4, y + controlH + 4, GuiLayout.ACCENT);
            gg.drawString(font, rightLabels[i], rightX + 8, y + Math.max(2, (controlH - 8) / 2), GuiLayout.TEXT, false);
        }
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