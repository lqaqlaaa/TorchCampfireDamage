package com.torchcampfiredamage.client;

import com.torchcampfiredamage.block.WardSourceBlockEntity;
import com.torchcampfiredamage.config.ModConfig;
import com.torchcampfiredamage.network.WardMobTargetsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

import net.minecraft.world.level.block.entity.BlockEntity;

/** 单根方块火把攻击范围内的生物名单。 */
public class WardMobTargetsScreen extends Screen {
    private int rowsPerPage;
    private final Screen parent;
    private final BlockPos pos;
    private final Map<ResourceLocation, Boolean> targets = new TreeMap<>();
    private EditBox search;
    private int scroll;
    private final List<Button> rowButtons = new ArrayList<>();
    private final Map<ResourceLocation, Mob> previews = new HashMap<>();

    private record MobEntry(ResourceLocation id, String name, boolean enabled, boolean configured) {}

    public WardMobTargetsScreen(Screen parent, WardSourceBlockEntity ward) {
        super(Component.literal("攻击生物名单"));
        this.parent = parent;
        this.pos = ward.getBlockPos();
        this.targets.putAll(ward.getMobTargets());
    }

    private List<MobEntry> filteredEntries() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return List.of();
        BlockEntity raw = mc.level.getBlockEntity(pos);
        if (!(raw instanceof WardSourceBlockEntity ward)) return List.of();

        AABB area;
        if (ward.isChunkMode()) {
            int r = ward.getChunkRadius();
            int cx = SectionPos.blockToSectionCoord(pos.getX());
            int cz = SectionPos.blockToSectionCoord(pos.getZ());
            area = new AABB((double)(cx-r)*16, pos.getY()-64, (double)(cz-r)*16,
                    (double)(cx+r+1)*16, pos.getY()+64, (double)(cz+r+1)*16);
        } else {
            double r = ward.getBlockRadius() + 0.5;
            double y = Math.min(r, 64);
            area = AABB.ofSize(Vec3.atCenterOf(pos), r*2, y*2, r*2);
        }

        Map<ResourceLocation, Mob> nearby = new TreeMap<>();
        for (Mob mob : mc.level.getEntitiesOfClass(Mob.class, area)) {
            if (!mob.isAlive()) continue;
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            if (id != null) {
                nearby.putIfAbsent(id, mob);
                PacketDistributor.sendToServer(new WardMobTargetsPayload(pos, 3, id.toString(), isHostile(mob)));
            }
        }

        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<MobEntry> result = new ArrayList<>();
        Map<ResourceLocation, Boolean> allIds = new TreeMap<>(ward.getDiscoveredMobTargetDefaults());
        for (ResourceLocation id : nearby.keySet()) allIds.put(id, isHostile(nearby.get(id)));
        for (ResourceLocation id : allIds.keySet()) {
            Mob mob = nearby.get(id);
            boolean configured = targets.containsKey(id);
            boolean defaultEnabled = allIds.getOrDefault(id, false);
            boolean enabled = configured ? targets.get(id) : defaultEnabled;
            String name = mob == null
                    ? BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(t -> t.getDescription().getString()).orElse(id.toString())
                    : mob.getType().getDescription().getString();
            if (q.isEmpty() || id.toString().toLowerCase(Locale.ROOT).contains(q)
                    || name.toLowerCase(Locale.ROOT).contains(q)) {
                result.add(new MobEntry(id, name, enabled, configured));
            }
        }
        result.sort(Comparator
                .comparingInt((MobEntry e) -> e.id().getNamespace().equals("minecraft") ? 0 : 1)
                .thenComparing(e -> e.id().getNamespace().toLowerCase(Locale.ROOT))
                .thenComparing(e -> e.name().toLowerCase(Locale.ROOT))
                .thenComparing(e -> e.id().toString()));
        return result;
    }

    private boolean isHostile(Mob mob) {
        if (mob instanceof Enemy) return true;
        boolean provoked = false;
        try { provoked = ModConfig.TREAT_PROVOKED_AS_HOSTILE.get(); } catch (Throwable ignored) {}
        if (!provoked) return false;
        LivingEntity target = mob.getTarget();
        if (target instanceof Player) return true;
        return target instanceof TamableAnimal tameable && tameable.getOwner() != null;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(390, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        search = new EditBox(this.font, left, 28, panelWidth, 20, Component.literal("搜索当前范围内的生物"));
        search.setHint(Component.literal("搜索名称或 modid:entity"));
        search.setResponder(value -> { scroll = 0; rebuildRows(); });
        addRenderableWidget(search);
        rebuildRows();
        addRenderableWidget(Button.builder(Component.literal("刷新"), b -> {
            scroll = 0;
            rebuildRows();
        }).bounds(this.width / 2 - 88, this.height - 28, 56, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> Minecraft.getInstance().setScreen(parent))
                .bounds(this.width / 2 + 8, this.height - 28, 80, 20).build());
    }

    private void rebuildRows() {
        for (Button button : rowButtons) removeWidget(button);
        rowButtons.clear();

        List<MobEntry> entries = filteredEntries();
        int panelWidth = Math.min(390, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        int top = 58;
        int rowHeight = 44;
        rowsPerPage = Math.max(1, Math.min(7, (this.height - 112) / rowHeight));
        int maxScroll = Math.max(0, entries.size() - rowsPerPage);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        for (int row = 0; row < rowsPerPage && row + scroll < entries.size(); row++) {
            MobEntry entry = entries.get(row + scroll);
            int y = top + row * rowHeight + 18;
            int toggleX = left + panelWidth - 108;
            int resetX = left + panelWidth - 52;
            rowButtons.add(addRenderableWidget(Button.builder(
                            Component.literal(entry.enabled() ? "§a开启" : "§c关闭"),
                            b -> change(entry.id(), !entry.enabled()))
                    .bounds(toggleX, y, 52, 20).build()));
            if (entry.configured()) {
                rowButtons.add(addRenderableWidget(Button.builder(Component.literal("默认"),
                                b -> reset(entry.id()))
                        .bounds(resetX, y, 48, 20).build()));
            } else {
                rowButtons.add(addRenderableWidget(Button.builder(Component.literal("删除"),
                                b -> delete(entry.id()))
                        .bounds(resetX, y, 48, 20).build()));
            }
        }
    }

    private void change(ResourceLocation id, boolean enabled) {
        targets.put(id, enabled);
        PacketDistributor.sendToServer(new WardMobTargetsPayload(pos, 0, id.toString(), enabled));
        rebuildRows();
    }

    private void reset(ResourceLocation id) {
        targets.remove(id);
        PacketDistributor.sendToServer(new WardMobTargetsPayload(pos, 1, id.toString(), false));
        rebuildRows();
    }

    private void delete(ResourceLocation id) {
        targets.remove(id);
        PacketDistributor.sendToServer(new WardMobTargetsPayload(pos, 2, id.toString(), false));
        rebuildRows();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, filteredEntries().size() - rowsPerPage);
        scroll = Math.max(0, Math.min(max, scroll - (int)Math.signum(scrollY) * 2));
        rebuildRows();
        return true;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);
        GuiLayout.drawFrame(gg, this.width, this.height);
        super.render(gg, mouseX, mouseY, partialTick);
        gg.drawCenteredString(font, "地下火把 · 攻击范围内的生物", width / 2, 10, 0xFFFFFF);
        gg.drawCenteredString(font, "敌对/仇恨生物默认开启，其他生物默认关闭；可单独切换",
                width / 2, 20, 0xAAAAAA);
        List<MobEntry> entries = filteredEntries();
        gg.drawString(font, "当前范围内 " + entries.size() + " 种生物 · 滚轮翻页", 12, 53, 0x888888);
        int panelWidth = Math.min(390, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        if (entries.isEmpty()) {
            gg.drawCenteredString(font, "攻击范围内暂时没有生物，点击刷新重新扫描。",
                    width / 2, height / 2, 0xBBBBBB);
        }
        MobEntry hoveredEntry = null;
        for (int row = 0; row < rowsPerPage && row + scroll < entries.size(); row++) {
            MobEntry entry = entries.get(row + scroll);
            int y = 58 + row * 44;
            if (mouseX >= left + 4 && mouseX <= left + 32 && mouseY >= y + 16 && mouseY <= y + 42) {
                hoveredEntry = entry;
            }
            String state = entry.enabled() ? "开启" : "关闭";
            if (!entry.configured()) state += "（默认）";
            String label = font.plainSubstrByWidth(entry.name() + " · " + state, panelWidth - 122);
            String namespace = entry.id().getNamespace();
            if (row == 0 || !namespace.equals(entries.get(row + scroll - 1).id().getNamespace())) {
                String group = namespace.equals("minecraft") ? "Minecraft 原版" : namespace;
                gg.fill(left, y + 13, left + panelWidth, y + 14, 0xFF777777);
                gg.drawString(font, group, left + 4, y + 3, 0xFFFFD700);
            }
            // 裁剪模型预览，防止末影龙、巨人等大型模型绘制到其他条目上。
            gg.enableScissor(left + 4, y + 16, left + 32, y + 42);
            renderPreview(gg, entry.id(), left + 18, y + 38);
            gg.disableScissor();
            gg.drawString(font, label, left + 38, y + 23, entry.enabled() ? 0xFFFFFF : 0xAAAAAA);
            gg.drawString(font, entry.id().toString(), left + 38, y + 33, 0x888888);
        }
        if (hoveredEntry != null) renderHoverPreview(gg, hoveredEntry, mouseX, mouseY);
    }

    private void renderHoverPreview(GuiGraphics graphics, MobEntry entry, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Mob preview = previews.computeIfAbsent(entry.id(), key -> {
            try { return BuiltInRegistries.ENTITY_TYPE.getOptional(key).map(type -> (Mob) type.create(mc.level)).orElse(null); }
            catch (Throwable ignored) { return null; }
        });
        if (preview == null) return;
        // Size the hover panel and entity render scale from the actual mob bounds.
        // The enlarged preview deliberately has no scissor: only the tiny row icon is clipped.
        float entityW = Math.max(0.25F, preview.getBbWidth());
        float entityH = Math.max(0.25F, preview.getBbHeight());
        int maxW = Math.max(96, Math.min(300, width - 8));
        int maxH = Math.max(96, Math.min(300, height - 44));
        float scale = Math.min(48.0F, Math.min((maxW - 24) / (entityW * 2.2F),
                (maxH - 38) / (entityH * 2.2F)));
        scale = Math.max(1.0F, scale);
        int boxW = Math.min(maxW, Math.max(116, (int)(entityW * scale * 2.2F) + 24));
        int boxH = Math.min(maxH, Math.max(126, (int)(entityH * scale * 2.2F) + 38));
        int x = Math.max(4, Math.min(width - boxW - 4, mouseX + 14));
        int y = Math.max(36, Math.min(height - boxH - 4, mouseY + 12));
        graphics.fill(x, y, x + boxW, y + boxH, 0xF0101724);
        graphics.fill(x, y, x + boxW, y + 2, 0xFF398BFF);
        graphics.renderOutline(x, y, boxW, boxH, 0xFF42658E);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(entry.name(), boxW - 12), x + boxW / 2, y + 7, 0xFFFFFFFF);
        try {
            preview.setNoAi(true);
            InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, x + 8, y + 24,
                    x + boxW - 8, y + boxH - 8, scale, 0.0F, 0.0F, 0.0F, preview);
        } catch (Throwable ignored) { }
    }


    private void renderPreview(GuiGraphics graphics, ResourceLocation id, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Mob preview = previews.computeIfAbsent(id, key -> {
            try {
                return BuiltInRegistries.ENTITY_TYPE.getOptional(key)
                        .map(type -> (Mob) type.create(mc.level)).orElse(null);
            } catch (Throwable ignored) { return null; }
        });
        if (preview != null) {
            try {
                preview.setNoAi(true);
                InventoryScreen.renderEntityInInventoryFollowsAngle(
                        graphics, x - 9, y - 9, x + 9, y + 9, 12, 0.0F, 0.0F, 0.0F, preview);
            } catch (Throwable ignored) { }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
