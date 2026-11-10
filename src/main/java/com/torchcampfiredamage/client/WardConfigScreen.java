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

    @Override
    protected void init() {
        activeInstance = this;
        float scale = GuiLayout.scale(this.width, this.height);
        ROW_H = GuiLayout.px(22, width, height, 15);
        LABEL_W = GuiLayout.px(56, width, height, 34);
        BOX_W = GuiLayout.px(78, width, height, 58);
        BTN_W = BOX_W;
        CONTROL_H = GuiLayout.px(20, width, height, 14);
        bottomButtonW = GuiLayout.px(88, width, height, 58);
        layoutGap = GuiLayout.px(6, width, height, 3);
        int cx = this.width / 2;
        int colGap = GuiLayout.px(24, width, height, 8);
        int left = cx - colGap / 2 - BOX_W;
        int right = cx + colGap / 2;
        // 内容总高约 6 行 + 2 行按钮
        int contentH = ROW_H * 7 + CONTROL_H * 2 + GuiLayout.px(24, width, height, 12);
        int top = Math.max(8, (this.height - contentH) / 2);
        int y = top;

        // ---- 左列：开关 / 范围类型 / 伤害范围 / 伤害 / 伤害间隔 ----
        // 开关
        addRenderableWidget(Button.builder(onOffText(enabled), b -> {
            enabled = !enabled;
            b.setMessage(onOffText(enabled));
        }).bounds(left, y, BTN_W, CONTROL_H).build());
        y += ROW_H;

        // 范围类型
        addRenderableWidget(Button.builder(rangeTypeText(), b -> {
            chunkMode = !chunkMode;
            b.setMessage(rangeTypeText());
            rangeText = String.valueOf(convertRange());
            rangeBox.setValue(rangeText);
        }).bounds(left, y, BTN_W, CONTROL_H).build());
        y += ROW_H;

        // 伤害范围（数值框）
        rangeBox = new EditBox(this.font, left, y, BTN_W, CONTROL_H, Component.literal("伤害范围"));
        rangeBox.setValue(rangeText);
        rangeBox.setResponder(t -> rangeText = t);
        rangeBox.setTooltip(Tooltip.create(Component.literal(
                "区块模式：0=1x1区块, 1=3x3, 2=5x5, 3=7x7, 4=9x9\n方块模式：格数半径")));
        addRenderableWidget(rangeBox);
        y += ROW_H;

        // 伤害
        damageBox = new EditBox(this.font, left, y, BTN_W, 20, Component.literal("伤害"));
        damageBox.setValue(damageText);
        damageBox.setResponder(t -> damageText = t);
        damageBox.setTooltip(Tooltip.create(Component.literal("填 0 = 只发光/减速，不造成伤害\n无上限")));
        addRenderableWidget(damageBox);
        y += ROW_H;

        // 伤害间隔
        intervalBox = new EditBox(this.font, left, y, BTN_W, 20, Component.literal("伤害间隔"));
        intervalBox.setValue(intervalText);
        intervalBox.setResponder(t -> intervalText = t);
        intervalBox.setTooltip(Tooltip.create(Component.literal("单位 tick\n20 = 1秒，1 = 每tick")));
        addRenderableWidget(intervalBox);
        y += ROW_H;

        // 当前方块火把独立的攻击生物名单。
        addRenderableWidget(Button.builder(Component.literal("攻击生物名单"), b ->
                Minecraft.getInstance().setScreen(new WardMobTargetsScreen(this, be)))
                .bounds(left, y, BTN_W, CONTROL_H)
                .tooltip(Tooltip.create(Component.literal("只影响坐标对应的这一根火把")))
                .build());

        // ---- 右列：目标发光 / 目标减速 / 击杀播报 / 传送掉落物 / 传送经验 ----
        int ry = top;
        addRenderableWidget(Button.builder(onOffText(glowing), b -> {
            glowing = !glowing;
            b.setMessage(onOffText(glowing));
        }).bounds(right, ry, BTN_W, CONTROL_H).build());
        ry += ROW_H;

        addRenderableWidget(Button.builder(onOffText(slow), b -> {
            slow = !slow;
            b.setMessage(onOffText(slow));
        }).bounds(right, ry, BTN_W, 20).build());
        ry += ROW_H;

        addRenderableWidget(Button.builder(onOffText(announce), b -> {
            announce = !announce;
            b.setMessage(onOffText(announce));
        }).bounds(right, ry, BTN_W, 20).build());
        ry += ROW_H;

        addRenderableWidget(Button.builder(onOffText(dropTeleport), b -> {
            dropTeleport = !dropTeleport;
            b.setMessage(onOffText(dropTeleport));
        }).bounds(right, ry, BTN_W, 20).build());
        ry += ROW_H;

        addRenderableWidget(Button.builder(onOffText(xpTeleport), b -> {
            xpTeleport = !xpTeleport;
            b.setMessage(onOffText(xpTeleport));
        }).bounds(right, ry, BTN_W, 20).build());
        ry += ROW_H;

        // 忽略命名生物（白名单）—— 本火把独立设置
        addRenderableWidget(Button.builder(onOffText(ignoreNamed), b -> {
            ignoreNamed = !ignoreNamed;
            b.setMessage(onOffText(ignoreNamed));
        }).bounds(right, ry, BTN_W, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "开 = 被命名的生物不会受伤\n只影响这一只火把")))
                .build());
        ry += ROW_H;

        addRenderableWidget(Button.builder(Component.literal("攻击附魔"), b ->
                Minecraft.getInstance().setScreen(new WardEnchantmentsScreen(this, pos, be.getAttackEnchantments())))
                .bounds(right, ry, BTN_W, 20)
                .tooltip(Tooltip.create(Component.literal("为当前这根放置火把配置独立攻击附魔；可添加多种附魔并设置等级")))
                .build());
        ry += ROW_H;

        // ---- 底部按钮区（两行三列，居中）----
        int bw = bottomButtonW;
        int gap = layoutGap;
        int totalW = bw * 3 + gap * 2;
        int bx0 = cx - totalW / 2;
        int by1 = top + ROW_H * 7 + GuiLayout.px(16, width, height, 8);
        int by2 = by1 + GuiLayout.px(24, width, height, 12);

        // 第一行：显示范围 / 显示设置 / 保存
        addRenderableWidget(Button.builder(showRangeText(), b -> {
            showRange = !showRange;
            b.setMessage(showRangeText());
            updateOverlay();
        }).bounds(bx0, by1, bw, CONTROL_H).build());

        addRenderableWidget(Button.builder(Component.literal("显示设置"), b -> {
            DisplaySettingsScreen.loadFrom(be);
            Minecraft.getInstance().setScreen(new DisplaySettingsScreen(this));
        }).bounds(bx0 + bw + gap, by1, bw, CONTROL_H).build());

        addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .bounds(bx0 + (bw + gap) * 2, by1, bw, CONTROL_H).build());

        // 第二行：星环设置 / 全局设置 / 取消
        addRenderableWidget(Button.builder(Component.literal("星环设置"), b -> {
            RingSettingsScreen.loadFrom(be);
            Minecraft.getInstance().setScreen(new RingSettingsScreen(this));
        }).bounds(bx0, by2, bw, CONTROL_H).build());

        addRenderableWidget(Button.builder(Component.literal("全局设置"), b ->
                Minecraft.getInstance().setScreen(new GlobalSettingsScreen(this)))
                .bounds(bx0 + bw + gap, by2, bw, CONTROL_H)
                .tooltip(Tooltip.create(Component.literal(
                        "全局配置：忽略命名生物、传送、播报、副手火把等")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(bx0 + (bw + gap) * 2, by2, bw, CONTROL_H).build());

        this._left = left;
        this._right = right;
        this._top = top;
    }

    private int _left;
    private int _right;
    private int _top;

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
        GuiLayout.drawFrame(gg, this.width, this.height);
        super.render(gg, mouseX, mouseY, partialTick);

        GuiLayout.drawTitle(gg, this.font, this.width, Math.max(4, _top - 24), "火把参数设置", "当前放置火把 · 独立配置");
        gg.drawString(this.font,
                "坐标: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(),
                _left, _top - 10, GuiLayout.MUTED);

        // 左列标签
        String[] leftLabels = {"开关", "范围类型", "伤害范围", "伤害", "伤害间隔", "攻击生物"};
        for (int i = 0; i < leftLabels.length; i++) {
            gg.drawString(this.font, leftLabels[i], _left - LABEL_W, _top + i * ROW_H + 5, GuiLayout.TEXT);
        }
        String[] rightLabels = {"目标发光", "目标减速", "击杀播报", "传送掉落物", "传送经验", "忽略命名生物"};
        for (int i = 0; i < rightLabels.length; i++) {
            gg.drawString(this.font, rightLabels[i], _right - LABEL_W, _top + i * ROW_H + 5, GuiLayout.TEXT);
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