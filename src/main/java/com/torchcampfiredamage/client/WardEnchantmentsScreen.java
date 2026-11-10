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

/** 当前单根放置火把的附魔管理界面；打开时自动扫描玩家背包中的全部附魔书。 */
public final class WardEnchantmentsScreen extends Screen {
    private int rowsPerPage = 1;
    private int rowHeight = 30;
    private int listTop = 82;
    private int panelWidth;
    private int panelLeft;
    private int footerY;
    private int statusY;
    private final Screen parent;
    private final ResourceLocation ignored;
    private final net.minecraft.core.BlockPos pos;
    private final Map<ResourceLocation, Integer> configured = new TreeMap<>();
    private final Map<ResourceLocation, Integer> candidates = new LinkedHashMap<>();
    private final Map<ResourceLocation, EditBox> rowLevels = new TreeMap<>();
    private final List<net.minecraft.client.gui.components.AbstractWidget> rowWidgets = new ArrayList<>();
    private EditBox search;
    private int page;
    private String status = "打开界面时会自动扫描背包中的所有附魔书";

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
        panelWidth = Math.max(80, Math.min(500, width - 16));
        panelLeft = (width - panelWidth) / 2;
        footerY = height - 24;
        boolean compact = panelWidth < 300;
        listTop = compact ? 108 : 82;
        statusY = compact ? 80 : 54;
        int availableHeight = Math.max(28, footerY - listTop - 8);
        rowsPerPage = Math.max(1, Math.min(6, availableHeight / 32));
        rowHeight = Math.max(28, Math.min(36, availableHeight / rowsPerPage));

        int gap = 4;
        int scanY = compact ? 54 : 30;
        int searchW = compact ? panelWidth : Math.max(40, panelWidth - 190);
        search = new EditBox(font, panelLeft, 30, searchW, 20, Component.literal("搜索已导入附魔"));
        search.setHint(Component.literal("搜索附魔名称"));
        search.setResponder(value -> { page = 0; rebuildRows(); });
        addRenderableWidget(search);
        int scanX = compact ? panelLeft : panelLeft + searchW + gap;
        int scanW = compact ? Math.max(32, (panelWidth - gap) / 2) : 82;
        int bulkX = compact ? panelLeft + scanW + gap : scanX + scanW + gap;
        int bulkW = compact ? Math.max(32, panelWidth - scanW - gap)
                : Math.max(40, panelWidth - searchW - scanW - gap * 2);
        addRenderableWidget(Button.builder(Component.literal("重新扫描"), b -> scanInventoryBooks())
                .bounds(scanX, scanY, scanW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("全部添加"), b -> addAllCandidates())
                .bounds(bulkX, scanY, bulkW, 20).build());

        int buttonW = Math.max(34, Math.min(64, (panelWidth - 12) / 4));
        int footerGap = 4;
        int total = buttonW * 4 + footerGap * 3;
        int startX = panelLeft + (panelWidth - total) / 2;
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> { if (page > 0) { page--; rebuildRows(); } })
                .bounds(startX, footerY, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> Minecraft.getInstance().setScreen(parent))
                .bounds(startX + buttonW + footerGap, footerY, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> {
            if ((page + 1) * rowsPerPage < filtered().size()) { page++; rebuildRows(); }
        }).bounds(startX + (buttonW + footerGap) * 2, footerY, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("清空搜索"), b -> {
            search.setValue(""); page = 0; rebuildRows();
        }).bounds(startX + (buttonW + footerGap) * 3, footerY, buttonW, 20).build());

        scanInventoryBooks();
        rebuildRows();
    }

    /** 扫描快捷栏、主背包和副手；同一种附魔保留发现的最高等级。 */
    private void scanInventoryBooks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int before = candidates.size();
        int found = 0;
        for (ItemStack stack : mc.player.getInventory().items) found += readBook(stack);
        for (ItemStack stack : mc.player.getInventory().offhand) found += readBook(stack);
        int added = candidates.size() - before;
        status = found == 0 ? "背包中没有找到附魔书（已配置附魔仍会保留）"
                : "扫描到 " + found + " 条附魔记录，发现 " + added + " 种新附魔；不会消耗书籍";
        page = 0;
        if (search != null) rebuildRows();
    }

    /** 一键把背包扫描到的全部候选附魔应用到当前火把。 */
    private void addAllCandidates() {
        if (candidates.isEmpty()) {
            status = "没有可添加的附魔，请先把附魔书放入背包";
            return;
        }
        int count = 0;
        for (Map.Entry<ResourceLocation, Integer> entry : candidates.entrySet()) {
            ResourceLocation id = entry.getKey();
            int level = Math.max(1, entry.getValue());
            configured.put(id, level);
            PacketDistributor.sendToServer(new WardEnchantmentsPayload(pos, 0, id.toString(), level));
            count++;
        }
        status = "已将 " + count + " 种背包附魔全部添加到当前火把";
        rebuildRows();
    }

    private int readBook(ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK)) return 0;
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return 0;
        int count = 0;
        for (var entry : stored.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            holder.unwrapKey().ifPresent(key -> candidates.merge(key.location(), entry.getIntValue(), Math::max));
            count++;
        }
        return count;
    }

    private void rebuildRows() {
        for (var widget : rowWidgets) removeWidget(widget);
        rowWidgets.clear();
        rowLevels.clear();
        List<ResourceLocation> ids = filtered();
        int maxPage = Math.max(0, (ids.size() - 1) / Math.max(1, rowsPerPage));
        page = Math.max(0, Math.min(page, maxPage));
        int start = page * rowsPerPage;
        for (int row = 0; row < rowsPerPage && start + row < ids.size(); row++) {
            ResourceLocation id = ids.get(start + row);
            int y = listTop + row * rowHeight;
            int controlY = y + Math.max(2, (rowHeight - 20) / 2);
            int levelX = panelLeft + panelWidth - 184;
            EditBox level = new EditBox(font, levelX, controlY, 48, 20, Component.literal("等级"));
            level.setValue(Integer.toString(configured.getOrDefault(id, candidates.getOrDefault(id, 1))));
            level.setFilter(value -> value.isEmpty() || value.matches("[0-9]{1,10}"));
            rowLevels.put(id, level);
            rowWidgets.add(addRenderableWidget(level));
            rowWidgets.add(addRenderableWidget(Button.builder(Component.literal("添加/更新"), b -> set(id))
                    .bounds(panelLeft + panelWidth - 130, controlY, 72, 20).build()));
            if (configured.containsKey(id)) {
                rowWidgets.add(addRenderableWidget(Button.builder(Component.literal("移除"), b -> remove(id))
                        .bounds(panelLeft + panelWidth - 52, controlY, 48, 20).build()));
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
        graphics.drawString(font, "守卫火把", 14, 10, 0xFF70AEFF, false);
        graphics.drawCenteredString(font, "放置火把 · 攻击附魔", width / 2, 10, GuiLayout.TEXT);
        graphics.drawString(font, "自动扫描背包附魔书 · 支持一次性全部添加 · 不消耗物品", panelLeft, statusY, 0xCCCCCC);
        graphics.drawString(font, status, panelLeft, statusY + 12, 0x88CCFF);
        List<ResourceLocation> ids = filtered();
        int pageCount = Math.max(1, (ids.size() + rowsPerPage - 1) / rowsPerPage);
        graphics.drawString(font, "候选 " + candidates.size() + " 种 · 当前火把已配置 " + configured.size()
                + " 种 · 第 " + (page + 1) + " / " + pageCount + " 页",
                panelLeft, statusY - 12, 0xFFFFFF);
        for (int row = 0; row < rowsPerPage && page * rowsPerPage + row < ids.size(); row++) {
            ResourceLocation id = ids.get(page * rowsPerPage + row);
            int y = listTop + row * rowHeight;
            graphics.fill(panelLeft, y, panelLeft + panelWidth, y + rowHeight - 2,
                    (row & 1) == 0 ? 0x99304765 : 0x88304765);
            graphics.fill(panelLeft, y, panelLeft + 2, y + rowHeight - 2, GuiLayout.ACCENT);
            Integer bookLevel = candidates.get(id);
            Integer current = configured.get(id);
            graphics.drawString(font, font.plainSubstrByWidth(displayName(id), Math.max(30, panelWidth - 195)),
                    panelLeft + 5, y + 3, current == null ? 0xFFFFFF : 0x55FF77);
            String detail = bookLevel == null ? "已配置 · 当前等级 " + current
                    : "附魔书等级 " + bookLevel + (current == null ? " · 尚未添加" : " · 当前火把等级 " + current);
            graphics.drawString(font, font.plainSubstrByWidth(detail, Math.max(30, panelWidth - 195)),
                    panelLeft + 5, y + 16, 0xFFCCCCCC);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
