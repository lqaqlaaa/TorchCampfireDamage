package com.torchcampfiredamage.client;

import com.torchcampfiredamage.network.WardEnchantmentsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** 当前单根放置火把的附魔管理界面；候选附魔只从玩家手持的附魔书读取。 */
public final class WardEnchantmentsScreen extends Screen {
    private static final int ROWS = 6;
    private static final int ROW_HEIGHT = 36;
    private final Screen parent;
    private final ResourceLocation ignored;
    private final net.minecraft.core.BlockPos pos;
    private final Map<ResourceLocation, Integer> configured = new TreeMap<>();
    private final Map<ResourceLocation, Integer> candidates = new LinkedHashMap<>();
    private final Map<ResourceLocation, EditBox> rowLevels = new TreeMap<>();
    private final List<net.minecraft.client.gui.components.AbstractWidget> rowWidgets = new ArrayList<>();
    private EditBox search;
    private int page;
    private String status = "手持附魔书后，点击“读取手持附魔书”导入候选附魔";

    public WardEnchantmentsScreen(Screen parent, net.minecraft.core.BlockPos pos, Map<ResourceLocation, Integer> current) {
        super(Component.literal("放置火把攻击附魔"));
        this.parent = parent;
        this.pos = pos;
        if (current != null) configured.putAll(current);
        this.ignored = null;
    }

    private List<ResourceLocation> filtered() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<ResourceLocation> ids = new ArrayList<>();
        for (ResourceLocation id : candidates.keySet()) if (!ids.contains(id)) ids.add(id);
        for (ResourceLocation id : configured.keySet()) if (!ids.contains(id)) ids.add(id);
        ids.sort(Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ResourceLocation::toString));
        if (!query.isEmpty()) ids.removeIf(id ->
                !displayName(id).toLowerCase(Locale.ROOT).contains(query)
                        && !id.toString().toLowerCase(Locale.ROOT).contains(query));
        return ids;
    }

    private String displayName(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return id.toString();
        var registry = mc.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        return registry.getHolder(ResourceKey.create(Registries.ENCHANTMENT, id))
                .map(holder -> Enchantment.getFullname(holder, Math.max(1, candidates.getOrDefault(id, configured.getOrDefault(id, 1)))).getString())
                .orElse(id.toString());
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(500, width - 16);
        int left = (width - panelWidth) / 2;
        search = new EditBox(font, left, 30, panelWidth - 112, 20, Component.literal("搜索已导入附魔"));
        search.setHint(Component.literal("搜索附魔名称"));
        search.setResponder(value -> { page = 0; rebuildRows(); });
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("读取手持附魔书"), b -> importHeldBooks())
                .bounds(left + panelWidth - 106, 30, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("上一页"), b -> { if (page > 0) { page--; rebuildRows(); } })
                .bounds(width / 2 - 106, height - 30, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("下一页"), b -> {
            if ((page + 1) * ROWS < filtered().size()) { page++; rebuildRows(); }
        }).bounds(width / 2 - 36, height - 30, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> Minecraft.getInstance().setScreen(parent))
                .bounds(width / 2 + 34, height - 30, 64, 20).build());
        rebuildRows();
    }

    private void importHeldBooks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int before = candidates.size();
        int found = 0;
        found += readBook(mc.player.getMainHandItem());
        found += readBook(mc.player.getOffhandItem());
        status = found == 0
                ? "主手和副手都没有带附魔的附魔书"
                : "已读取 " + found + " 条附魔；新增/更新候选 " + (candidates.size() - before) + " 种（不会消耗附魔书）";
        page = 0;
        rebuildRows();
    }

    private int readBook(ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK)) return 0;
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return 0;
        int count = 0;
        for (var entry : stored.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            holder.unwrapKey().ifPresent(key -> candidates.put(key.location(), entry.getIntValue()));
            count++;
        }
        return count;
    }

    private void rebuildRows() {
        for (var widget : rowWidgets) removeWidget(widget);
        rowWidgets.clear();
        rowLevels.clear();
        List<ResourceLocation> ids = filtered();
        int panelWidth = Math.min(500, width - 16);
        int left = (width - panelWidth) / 2;
        int top = 78;
        for (int row = 0; row < ROWS && page * ROWS + row < ids.size(); row++) {
            ResourceLocation id = ids.get(page * ROWS + row);
            int y = top + row * ROW_HEIGHT;
            boolean imported = candidates.containsKey(id);
            int initialLevel = configured.getOrDefault(id, candidates.getOrDefault(id, 1));
            EditBox level = new EditBox(font, left + panelWidth - 184, y + 5, 48, 20, Component.literal("等级"));
            level.setValue(Integer.toString(initialLevel));
            level.setFilter(value -> value.isEmpty() || value.matches("[0-9]{1,10}"));
            rowLevels.put(id, level);
            rowWidgets.add(addRenderableWidget(level));
            rowWidgets.add(addRenderableWidget(Button.builder(Component.literal("添加/更新"), b -> set(id))
                    .bounds(left + panelWidth - 130, y + 5, 72, 20).build()));
            if (configured.containsKey(id)) {
                rowWidgets.add(addRenderableWidget(Button.builder(Component.literal("移除"), b -> remove(id))
                        .bounds(left + panelWidth - 52, y + 5, 48, 20).build()));
            }
        }
    }

    private int selectedLevel(ResourceLocation id) {
        EditBox box = rowLevels.get(id);
        try {
            long value = Long.parseLong(box == null ? "1" : box.getValue());
            return (int)Math.max(1L, Math.min((long)Integer.MAX_VALUE, value));
        } catch (Exception ignored) { return 1; }
    }

    private void set(ResourceLocation id) {
        int level = selectedLevel(id);
        configured.put(id, level);
        PacketDistributor.sendToServer(new WardEnchantmentsPayload(pos, 0, id.toString(), level));
        status = displayName(id) + " 已添加/更新为等级 " + level + "（仅当前火把）";
        rebuildRows();
    }

    private void remove(ResourceLocation id) {
        configured.remove(id);
        PacketDistributor.sendToServer(new WardEnchantmentsPayload(pos, 1, id.toString(), 0));
        status = displayName(id) + " 已从当前火把移除";
        rebuildRows();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        GuiLayout.drawFrame(graphics, width, height);
        super.render(graphics, mouseX, mouseY, partialTick);
        int panelWidth = Math.min(500, width - 16);
        int left = (width - panelWidth) / 2;
        graphics.drawCenteredString(font, "放置火把 · 从附魔书导入独立攻击附魔", width / 2, 10, 0xFFD700);
        graphics.drawString(font, "可读取主手/副手附魔书；一本书含多个附魔时会全部导入。读取和添加均不会消耗书。", left, 53, 0xCCCCCC);
        graphics.drawString(font, status, left, 65, 0x88CCFF);
        List<ResourceLocation> ids = filtered();
        graphics.drawString(font, "候选 " + candidates.size() + " 种 · 当前火把已配置 " + configured.size()
                + " 种 · 第 " + (page + 1) + " / " + Math.max(1, (ids.size() + ROWS - 1) / ROWS) + " 页",
                left, 42, 0xFFFFFF);
        for (int row = 0; row < ROWS && page * ROWS + row < ids.size(); row++) {
            ResourceLocation id = ids.get(page * ROWS + row);
            int y = 78 + row * ROW_HEIGHT;
            Integer bookLevel = candidates.get(id);
            Integer current = configured.get(id);
            graphics.fill(left, y, left + panelWidth, y + 31, (row & 1) == 0 ? 0x55333333 : 0x44222222);
            graphics.drawString(font, font.plainSubstrByWidth(displayName(id), panelWidth - 195),
                    left + 5, y + 4, current == null ? 0xFFFFFF : 0x55FF77);
            String detail = bookLevel == null ? "已配置 · 当前等级 " + current
                    : "附魔书等级 " + bookLevel + (current == null ? " · 尚未添加" : " · 当前火把等级 " + current);
            graphics.drawString(font, font.plainSubstrByWidth(detail, panelWidth - 195),
                    left + 5, y + 17, 0xAAAAAA);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
