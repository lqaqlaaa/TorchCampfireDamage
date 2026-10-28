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
    private boolean showRange;

    // 布局
    private static final int ROW_H = 22;
    private static final int LABEL_W = 76;
    private static final int BOX_W = 88;
    private static final int BTN_W = 78;

    public WardConfigScreen(BlockPos pos, WardSourceBlockEntity be) {
        super(Component.literal("守卫火源设置"));
        this.pos = pos;
        this.be = be;
        this.enabled = be.isEnabled();
        this.chunkMode = be.isChunkMode();
        this.glowing = be.isGlowing();
        this.slow = be.isSlow();
        this.announce = be.isAnnounce();
        this.dropTeleport = be.isTeleportDrops();
        this.xpTeleport = be.isTeleportXp();
        this.showRange = (Minecraft.getInstance().level != null)
                && RangeOverlay.has(Minecraft.getInstance().level.dimension(), pos);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - 160;
        int right = cx + 10;
        int top = this.height / 2 - 88;
        int y = top;

        // ---- 左列：开关 / 范围类型 / 伤害范围 / 伤害 / 伤害间隔 ----
        // 开关
        addRenderableWidget(Button.builder(onOffText(enabled), b -> {
            enabled = !enabled;
            b.setMessage(onOffText(enabled));
        }).bounds(left, y, BTN_W, 20).build());
        y += ROW_H;

        // 范围类型
        addRenderableWidget(Button.builder(rangeTypeText(), b -> {
            chunkMode = !chunkMode;
            b.setMessage(rangeTypeText());
            rangeBox.setValue(String.valueOf(convertRange()));
        }).bounds(left, y, BTN_W, 20).build());
        y += ROW_H;

        // 伤害范围（数值框）
        rangeBox = new EditBox(this.font, left, y, BTN_W, 20, Component.literal("伤害范围"));
        rangeBox.setValue(String.valueOf(chunkMode ? be.getChunkRadius() : be.getBlockRadius()));
        rangeBox.setTooltip(Tooltip.create(Component.literal(
                "区块模式：0=1x1区块, 1=3x3, 2=5x5, 3=7x7, 4=9x9\n方块模式：格数半径")));
        addRenderableWidget(rangeBox);
        y += ROW_H;

        // 伤害
        damageBox = new EditBox(this.font, left, y, BTN_W, 20, Component.literal("伤害"));
        damageBox.setValue(String.valueOf(be.getDamage()));
        damageBox.setTooltip(Tooltip.create(Component.literal("填 0 = 只发光/减速，不造成伤害\n无上限")));
        addRenderableWidget(damageBox);
        y += ROW_H;

        // 伤害间隔
        intervalBox = new EditBox(this.font, left, y, BTN_W, 20, Component.literal("伤害间隔"));
        intervalBox.setValue(String.valueOf(be.getInterval()));
        intervalBox.setTooltip(Tooltip.create(Component.literal("单位 tick\n20 = 1秒，1 = 每tick")));
        addRenderableWidget(intervalBox);
        y += ROW_H;

        // ---- 右列：目标发光 / 目标减速 / 击杀播报 / 传送掉落物 / 传送经验 ----
        int ry = top;
        addRenderableWidget(Button.builder(onOffText(glowing), b -> {
            glowing = !glowing;
            b.setMessage(onOffText(glowing));
        }).bounds(right, ry, BTN_W, 20).build());
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

        // ---- 显示范围开关 ----
        int by = top + ROW_H * 5 + 12;
        addRenderableWidget(Button.builder(showRangeText(), b -> {
            showRange = !showRange;
            b.setMessage(showRangeText());
            updateOverlay();
        }).bounds(cx - 176, by, 86, 20).build());
        addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .bounds(cx + 2, by, 86, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(cx + 92, by, 86, 20).build());

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
        RangeOverlay.RangeBox box = RangeOverlay.makeBox(
                pos, chunkMode, range, range, 0x66CCFF);
        RangeOverlay.set(mc.level.dimension(), pos, box);
    }

    private void save() {
        int range = parseInt(rangeBox.getValue(), chunkMode ? 1 : 16,
                chunkMode ? 0 : 0, chunkMode ? 32 : 512);
        double damage = parseDouble(damageBox.getValue(), 2.0);
        int interval = parseInt(intervalBox.getValue(), 20, 1, Integer.MAX_VALUE);

        PacketDistributor.sendToServer(new UpdateWardPayload(
                pos, enabled, chunkMode, range, damage, interval,
                glowing, slow, announce, dropTeleport, xpTeleport));

        be.applyAll(enabled, chunkMode, range, damage, interval,
                glowing, slow, announce, dropTeleport, xpTeleport);
        updateOverlay();
        onClose();
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
        super.render(gg, mouseX, mouseY, partialTick);

        gg.drawCenteredString(this.font, "守卫火源设置", this.width / 2, _top - 24, 0xFFD700);
        gg.drawString(this.font,
                "坐标: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(),
                _left, _top - 10, 0xAAAAAA);

        // 左列标签
        String[] leftLabels = {"开关", "范围类型", "伤害范围", "伤害", "伤害间隔"};
        for (int i = 0; i < leftLabels.length; i++) {
            gg.drawString(this.font, leftLabels[i], _left - 56, _top + i * ROW_H + 6, 0xFFFFFF);
        }
        String[] rightLabels = {"目标发光", "目标减速", "击杀播报", "传送掉落物", "传送经验"};
        for (int i = 0; i < rightLabels.length; i++) {
            gg.drawString(this.font, rightLabels[i], _right - 56, _top + i * ROW_H + 6, 0xFFFFFF);
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