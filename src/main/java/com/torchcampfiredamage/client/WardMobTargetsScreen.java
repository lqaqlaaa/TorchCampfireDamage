package com.torchcampfiredamage.client;

import com.torchcampfiredamage.block.WardSourceBlockEntity;
import com.torchcampfiredamage.network.WardMobTargetsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/** 单根方块火把的攻击生物名单。 */
public class WardMobTargetsScreen extends Screen {
    private static final int ROWS = 8;
    private final Screen parent;
    private final net.minecraft.core.BlockPos pos;
    private final Map<ResourceLocation, Boolean> targets = new TreeMap<>();
    private EditBox search;
    private int scroll;
    private final List<Button> rowButtons = new ArrayList<>();

    private record MobEntry(ResourceLocation id, String name, boolean installed) {}

    public WardMobTargetsScreen(Screen parent, WardSourceBlockEntity ward) {
        super(Component.literal("攻击生物名单"));
        this.parent = parent;
        this.pos = ward.getBlockPos();
        this.targets.putAll(ward.getMobTargets());
    }

    private List<MobEntry> filteredEntries() {
        Map<ResourceLocation, MobEntry> all = new TreeMap<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type.getCategory() == MobCategory.MISC) continue;
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null) continue;
            all.put(id, new MobEntry(id, type.getDescription().getString(), true));
        }
        // 不认识的 ID 也显示出来，绝不因为模组缺失而丢掉存档数据。
        for (ResourceLocation id : targets.keySet()) {
            all.putIfAbsent(id, new MobEntry(id, id.toString(), false));
        }
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        return all.values().stream()
                .filter(e -> q.isEmpty() || e.id().toString().toLowerCase(Locale.ROOT).contains(q)
                        || e.name().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(390, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        search = new EditBox(this.font, left, 28, panelWidth, 20, Component.literal("搜索生物名称或 ID"));
        search.setHint(Component.literal("搜索生物名称或 modid:entity"));
        search.setResponder(value -> {
            scroll = 0;
            rebuildRows();
        });
        addRenderableWidget(search);
        rebuildRows();
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> Minecraft.getInstance().setScreen(parent))
                .bounds(this.width / 2 - 42, this.height - 28, 84, 20).build());
    }

    private void rebuildRows() {
        // 只重建列表操作按钮，搜索框和返回按钮保持不动。
        for (Button button : rowButtons) removeWidget(button);
        rowButtons.clear();

        List<MobEntry> entries = filteredEntries();
        int maxScroll = Math.max(0, entries.size() - ROWS);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int panelWidth = Math.min(390, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        int top = 58;
        int rowHeight = 25;
        for (int row = 0; row < ROWS && row + scroll < entries.size(); row++) {
            MobEntry entry = entries.get(row + scroll);
            int y = top + row * rowHeight;
            Boolean enabled = targets.get(entry.id());
            int labelWidth = panelWidth - 116;
            if (enabled == null) {
                rowButtons.add(addRenderableWidget(Button.builder(Component.literal("添加"), b -> change(entry.id(), true))
                        .bounds(left + labelWidth + 4, y, 52, 20).build()));
            } else {
                rowButtons.add(addRenderableWidget(Button.builder(Component.literal(enabled ? "停用" : "启用"),
                                b -> change(entry.id(), !enabled))
                        .bounds(left + labelWidth + 4, y, 52, 20).build()));
                rowButtons.add(addRenderableWidget(Button.builder(Component.literal("移除"), b -> remove(entry.id()))
                        .bounds(left + labelWidth + 60, y, 52, 20).build()));
            }
        }
    }

    private void change(ResourceLocation id, boolean enabled) {
        targets.put(id, enabled);
        PacketDistributor.sendToServer(new WardMobTargetsPayload(pos, 0, id.toString(), enabled));
        rebuildRows();
    }

    private void remove(ResourceLocation id) {
        targets.remove(id);
        PacketDistributor.sendToServer(new WardMobTargetsPayload(pos, 1, id.toString(), false));
        rebuildRows();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, filteredEntries().size() - ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY) * 3));
        rebuildRows();
        return true;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);
        super.render(gg, mouseX, mouseY, partialTick);
        gg.drawCenteredString(font, "攻击生物名单", width / 2, 10, 0xFFFFFF);
        gg.drawString(font, "点击添加；已添加的生物可启用、停用或移除", 12, height - 42, 0xAAAAAA);
        gg.drawString(font, "空名单时沿用默认敌对目标；配置后仅攻击已启用的生物", 12, height - 54, 0xAAAAAA);
        List<MobEntry> entries = filteredEntries();
        gg.drawString(font, "生物类型: " + entries.size() + "    滚轮翻页", 12, 53, 0x888888);
        int panelWidth = Math.min(390, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        for (int row = 0; row < ROWS && row + scroll < entries.size(); row++) {
            MobEntry entry = entries.get(row + scroll);
            Boolean enabled = targets.get(entry.id());
            String state = enabled == null ? "未添加" : (enabled ? "已启用" : "已停用");
            String suffix = entry.installed() ? state : "未安装";
            String label = font.plainSubstrByWidth(entry.name() + " [" + entry.id() + "] · " + suffix,
                    panelWidth - 122);
            gg.drawString(font, label, left, 64 + row * 25 + 6, entry.installed() ? 0xFFFFFF : 0xFF7777);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
