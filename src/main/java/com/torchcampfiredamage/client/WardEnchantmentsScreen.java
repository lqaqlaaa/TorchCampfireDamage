package com.torchcampfiredamage.client;

import com.torchcampfiredamage.network.WardEnchantmentsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 当前单根放置火把的附魔管理界面。 */
public final class WardEnchantmentsScreen extends Screen {
    private static final int ROWS = 7;
    private final Screen parent;
    private final BlockPos pos;
    private final Map<ResourceLocation, Integer> configured = new TreeMap<>();
    private EditBox search;
    private EditBox levelBox;
    private int page;
    private final List<Button> rowButtons = new ArrayList<>();

    public WardEnchantmentsScreen(Screen parent, BlockPos pos, Map<ResourceLocation, Integer> current) {
        super(Component.literal("放置火把攻击附魔"));
        this.parent = parent;
        this.pos = pos;
        if (current != null) configured.putAll(current);
    }

    private List<ResourceLocation> filtered() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        if (Minecraft.getInstance().level == null) return List.of();
        List<ResourceLocation> ids = new ArrayList<>(Minecraft.getInstance().level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).keySet());
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        if (!query.isEmpty()) ids.removeIf(id -> !id.toString().toLowerCase(java.util.Locale.ROOT).contains(query));
        return ids;
    }

    @Override protected void init() {
        int panelWidth = Math.min(430, width - 16);
        int left = (width - panelWidth) / 2;
        search = new EditBox(font, left, 30, panelWidth - 130, 20, Component.literal("搜索附魔"));
        search.setHint(Component.literal("输入附魔名称或命名空间"));
        search.setResponder(value -> { page = 0; rebuildRows(); });
        addRenderableWidget(search);
        levelBox = new EditBox(font, left + panelWidth - 122, 30, 122, 20, Component.literal("等级"));
        levelBox.setValue("1");
        levelBox.setFilter(value -> value.isEmpty() || value.matches("[0-9]{1,10}"));
        levelBox.setHint(Component.literal("等级"));
        addRenderableWidget(levelBox);
        addRenderableWidget(Button.builder(Component.literal("上一页"), b -> { if (page > 0) { page--; rebuildRows(); } })
                .bounds(width / 2 - 100, height - 30, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("下一页"), b -> {
            if ((page + 1) * ROWS < filtered().size()) { page++; rebuildRows(); }
        }).bounds(width / 2 - 30, height - 30, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> Minecraft.getInstance().setScreen(parent))
                .bounds(width / 2 + 40, height - 30, 60, 20).build());
        rebuildRows();
    }

    private void rebuildRows() {
        for (Button button : rowButtons) removeWidget(button);
        rowButtons.clear();
        List<ResourceLocation> ids = filtered();
        int panelWidth = Math.min(430, width - 16);
        int left = (width - panelWidth) / 2;
        int top = 62;
        for (int row = 0; row < ROWS && page * ROWS + row < ids.size(); row++) {
            ResourceLocation id = ids.get(page * ROWS + row);
            int y = top + row * 32;
            rowButtons.add(addRenderableWidget(Button.builder(Component.literal("添加/更新"), b -> set(id))
                    .bounds(left + panelWidth - 130, y, 64, 20).build()));
            rowButtons.add(addRenderableWidget(Button.builder(Component.literal("移除"), b -> remove(id))
                    .bounds(left + panelWidth - 62, y, 56, 20).build()));
        }
    }

    private int selectedLevel() {
        try {
            long value = Long.parseLong(levelBox.getValue());
            return (int)Math.max(1L, Math.min((long)Integer.MAX_VALUE, value));
        } catch (Exception ignored) { return 1; }
    }

    private void set(ResourceLocation id) {
        int level = selectedLevel();
        configured.put(id, level);
        PacketDistributor.sendToServer(new WardEnchantmentsPayload(pos, 0, id.toString(), level));
        rebuildRows();
    }

    private void remove(ResourceLocation id) {
        configured.remove(id);
        PacketDistributor.sendToServer(new WardEnchantmentsPayload(pos, 1, id.toString(), 0));
        rebuildRows();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        GuiLayout.drawFrame(graphics, this.width, this.height);
        super.render(graphics, mouseX, mouseY, partialTick);
        int panelWidth = Math.min(430, width - 16);
        int left = (width - panelWidth) / 2;
        graphics.drawCenteredString(font, "放置火把 · 独立攻击附魔", width / 2, 10, 0xFFD700);
        graphics.drawString(font, "游戏注册表中的附魔均可搜索添加；等级范围 1–2147483647", left, 53, 0xAAAAAA);
        List<ResourceLocation> ids = filtered();
        graphics.drawString(font, "已配置 " + configured.size() + " 种 · 第 " + (page + 1)
                + " / " + Math.max(1, (ids.size() + ROWS - 1) / ROWS) + " 页", left, 42, 0xFFFFFF);
        for (int row = 0; row < ROWS && page * ROWS + row < ids.size(); row++) {
            ResourceLocation id = ids.get(page * ROWS + row);
            int y = 62 + row * 32;
            Integer current = configured.get(id);
            graphics.drawString(font, id.toString(), left + 4, y + 6, 0xFFFFFF);
            graphics.drawString(font, current == null ? "未添加" : "当前等级: " + current,
                    left + panelWidth - 202, y + 6, current == null ? 0x888888 : 0x55FF55);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
