package com.torchcampfiredamage.client;

import com.torchcampfiredamage.block.WardSourceBlockEntity;
import com.torchcampfiredamage.network.UpdateWardPayload;
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
 */
public class WardConfigScreen extends Screen {
    private final BlockPos pos;
    private final WardSourceBlockEntity be;

    private EditBox radiusBox;
    private EditBox damageBox;
    private EditBox intervalBox;
    private EditBox ampBox;

    private boolean announce;
    private boolean glowing;
    private boolean slow;
    private boolean enabled;

    public WardConfigScreen(BlockPos pos, WardSourceBlockEntity be) {
        super(Component.literal("守卫火源设置"));
        this.pos = pos;
        this.be = be;
        this.announce = be.isAnnounce();
        this.glowing = be.isGlowing();
        this.slow = be.isSlow();
        this.enabled = be.isEnabled();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = this.height / 2 - 100;

        // 区块范围
        radiusBox = new EditBox(this.font, cx - 110, top, 70, 20, Component.literal("区块范围"));
        radiusBox.setValue(String.valueOf(be.getChunkRadius()));
        radiusBox.setTooltip(Tooltip.create(
                Component.literal("0 = 1x1区块\n1 = 3x3区块\n2 = 5x5区块\n3 = 7x7区块\n4 = 9x9区块")));
        addRenderableWidget(radiusBox);

        // 伤害
        damageBox = new EditBox(this.font, cx - 110, top + 24, 70, 20, Component.literal("伤害"));
        damageBox.setValue(String.valueOf(be.getDamage()));
        damageBox.setTooltip(Tooltip.create(
                Component.literal("填 0 = 只发光/减速，不造成伤害\n无上限")));
        addRenderableWidget(damageBox);

        // 间隔
        intervalBox = new EditBox(this.font, cx - 110, top + 48, 70, 20, Component.literal("间隔"));
        intervalBox.setValue(String.valueOf(be.getInterval()));
        intervalBox.setTooltip(Tooltip.create(
                Component.literal("单位 tick\n20 = 1秒\n1 = 每tick")));
        addRenderableWidget(intervalBox);

        // 减速等级
        ampBox = new EditBox(this.font, cx - 110, top + 72, 70, 20, Component.literal("减速等级"));
        ampBox.setValue(String.valueOf(be.getSlowAmplifier()));
        ampBox.setTooltip(Tooltip.create(
                Component.literal("0 = 缓慢I\n254 = 缓慢255")));
        addRenderableWidget(ampBox);

        // 开关
        addRenderableWidget(Button.builder(announceText(), b -> {
            announce = !announce;
            b.setMessage(announceText());
        }).bounds(cx + 10, top, 100, 20).build());

        addRenderableWidget(Button.builder(glowText(), b -> {
            glowing = !glowing;
            b.setMessage(glowText());
        }).bounds(cx + 10, top + 24, 100, 20).build());

        addRenderableWidget(Button.builder(slowText(), b -> {
            slow = !slow;
            b.setMessage(slowText());
        }).bounds(cx + 10, top + 48, 100, 20).build());

        addRenderableWidget(Button.builder(enabledText(), b -> {
            enabled = !enabled;
            b.setMessage(enabledText());
        }).bounds(cx + 10, top + 72, 100, 20).build());

        // 保存 / 取消
        addRenderableWidget(Button.builder(Component.literal("✔ 保存"), b -> save())
                .bounds(cx - 110, top + 108, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("✘ 取消"), b -> onClose())
                .bounds(cx + 10, top + 108, 100, 20).build());
    }

    private Component announceText() { return Component.literal("播报击杀: " + (announce ? "§a开" : "§c关")); }
    private Component glowText()    { return Component.literal("目标发光: " + (glowing ? "§a开" : "§c关")); }
    private Component slowText()    { return Component.literal("目标减速: " + (slow ? "§a开" : "§c关")); }
    private Component enabledText() { return Component.literal("整体生效: " + (enabled ? "§a开" : "§c关")); }

    private void save() {
        int radius = parseInt(radiusBox.getValue(), 1, 0, 4);
        double damage = parseDouble(damageBox.getValue(), 2.0);
        int interval = parseInt(intervalBox.getValue(), 20, 1, Integer.MAX_VALUE);
        int amp = parseInt(ampBox.getValue(), 254, 0, 254);

        PacketDistributor.sendToServer(new UpdateWardPayload(
                pos, radius, damage, interval, announce, glowing, slow, amp, enabled));

        // 本地立即更新，界面即刻反映
        be.applyAll(radius, damage, interval, announce, glowing, slow, amp, enabled);
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

        int cx = this.width / 2;
        int top = this.height / 2 - 100;

        gg.drawCenteredString(this.font, "§6✦ 守卫火源设置 ✦", cx, top - 26, 0xFFFFFF);
        gg.drawString(this.font, "§7坐标: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(),
                cx - 110, top - 12, 0xAAAAAA);

        gg.drawString(this.font, "§f区块范围", cx - 190, top + 6, 0xFFFFFF);
        gg.drawString(this.font, "§f伤害", cx - 190, top + 30, 0xFFFFFF);
        gg.drawString(this.font, "§f间隔(tick)", cx - 190, top + 54, 0xFFFFFF);
        gg.drawString(this.font, "§f减速等级", cx - 190, top + 78, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}