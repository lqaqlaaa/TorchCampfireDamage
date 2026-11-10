package com.torchcampfiredamage.client;

import com.torchcampfiredamage.network.WardEnchantmentsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Set;
import java.util.TreeSet;

/** 放置火把的特殊伤害类型选择界面。 */
public final class WardDamageTypesScreen extends Screen {
    private final Screen parent;
    private final BlockPos pos;
    private final Set<String> selected = new TreeSet<>();
    private static final String[][] TYPES = {
            {"fire", "火焰伤害", "直接造成火焰类型伤害，受火焰免疫影响"},
            {"burn", "持续燃烧", "点燃目标，后续由原版燃烧机制持续造成伤害"},
            {"magic", "魔法伤害", "使用原版魔法伤害来源"},
            {"lightning", "闪电伤害", "使用原版闪电伤害来源，不生成真实雷击"},
            {"wither", "凋零伤害", "使用原版凋零伤害来源"}
    };

    public WardDamageTypesScreen(Screen parent, BlockPos pos, Set<String> current) {
        super(Component.literal("守卫火把 · 特殊伤害"));
        this.parent = parent;
        this.pos = pos;
        if (current != null) selected.addAll(current);
    }

    @Override protected void init() {
        clearWidgets();
        int panelWidth = Math.min(440, width - 20);
        int left = (width - panelWidth) / 2;
        int top = Math.max(38, (height - 260) / 2);
        int rowHeight = Math.max(40, Math.min(48, (height - top - 48) / TYPES.length));
        for (int i = 0; i < TYPES.length; i++) {
            final String id = TYPES[i][0];
            int y = top + i * rowHeight;
            addRenderableWidget(Button.builder(Component.literal((selected.contains(id) ? "✓  " : "＋  ") + TYPES[i][1]), b -> {
                boolean enable = !selected.contains(id);
                if (enable) selected.add(id); else selected.remove(id);
                PacketDistributor.sendToServer(new WardEnchantmentsPayload(pos, 2, id, enable ? 1 : 0));
                rebuildButtons();
            }).bounds(left, y, panelWidth, 22).build());
        }
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> Minecraft.getInstance().setScreen(parent))
                .bounds(left, top + TYPES.length * rowHeight + 6, panelWidth, 20).build());
    }

    private void rebuildButtons() {
        // Reinitializing keeps button labels synchronized with the selected set.
        init();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        GuiLayout.drawFrame(g, width, height);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, "特殊伤害方式（可多选）", width / 2, 14, GuiLayout.TEXT);
        int panelWidth = Math.min(440, width - 20);
        int left = (width - panelWidth) / 2;
        int top = Math.max(38, (height - 260) / 2);
        int rowHeight = Math.max(40, Math.min(48, (height - top - 48) / TYPES.length));
        for (int i = 0; i < TYPES.length; i++) {
            g.drawString(font, TYPES[i][2], left + 5, top + i * rowHeight + 24, 0xFFB8B8B8);
        }
        g.drawCenteredString(font, "未选择时使用默认魔法伤害；多选时所有已选伤害方式都会完整触发", width / 2,
                Math.min(height - 12, top + TYPES.length * rowHeight + 32), 0xFFAAAAAA);
    }

    @Override public boolean isPauseScreen() { return false; }
}
