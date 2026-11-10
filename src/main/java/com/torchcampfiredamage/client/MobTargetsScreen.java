package com.torchcampfiredamage.client;

import com.torchcampfiredamage.config.ModConfig;
import com.torchcampfiredamage.network.PlayerMobTargetsRequestPayload;
import com.torchcampfiredamage.network.PlayerMobTargetsSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Locale;
import net.minecraft.world.entity.Entity;

/** 副手火把/护符的附近生物名单。 */
public final class MobTargetsScreen extends Screen {
    private static final int ACTION_LIST = 0;
    private static final int ACTION_SET = 1;
    private static final int ACTION_REMOVE = 2;
    private static MobTargetsScreen current;

    private final Screen parent;
    /** 服务端保存的显式覆盖；列表本身只显示当前攻击范围内出现的生物。 */
    private List<PlayerMobTargetsSyncPayload.Entry> targets = new ArrayList<>();
    private List<PlayerMobTargetsSyncPayload.Entry> visible = new ArrayList<>();
    private int page;
    private int rowsPerPage;
    private boolean requestSent;
    private EditBox search;
    private String searchText = "";
    private final Map<ResourceLocation, Mob> previews = new HashMap<>();

    public MobTargetsScreen(Screen parent) {
        super(Component.literal("副手火把生物列表"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        current = this;
        rowsPerPage = Math.max(1, Math.min(7, (height - 166) / 44));
        visible = scanNearby();
        int pageCount = pageCount();
        if (page >= pageCount) page = pageCount - 1;
        clearWidgets();

        int panelWidth = Math.min(420, width - 16);
        int left = Math.max(8, (width - panelWidth) / 2);
        search = new EditBox(font, left, 44, panelWidth, 20, Component.literal("搜索生物"));
        search.setHint(Component.literal("输入生物名称、实体 ID 或模组 ID"));
        search.setValue(searchText);
        search.setResponder(value -> { searchText = value; page = 0; init(); });
        addRenderableWidget(search);
        List<PlayerMobTargetsSyncPayload.Entry> filtered = filteredEntries();
        int start = page * rowsPerPage;
        int end = Math.min(filtered.size(), start + rowsPerPage);
        int rowHeight = 44;
        int listTop = 72;
        int listWidth = panelWidth;

        for (int i = start; i < end; i++) {
            PlayerMobTargetsSyncPayload.Entry entry = filtered.get(i);
            int y = listTop + (i - start) * rowHeight + 18;
            int right = left + listWidth;
            boolean configured = entry.configured();
            int toggleW = 54;
            int resetW = 42;
            int toggleX = right - resetW - toggleW - 8;
            int resetX = right - resetW;

            addRenderableWidget(Button.builder(
                    Component.literal(entry.enabled() ? "§a开启" : "§c关闭"),
                    button -> send(ACTION_SET, entry.entityId(), !entry.enabled()))
                    .bounds(toggleX, y + 2, toggleW, 20).build());
            if (!configured) {
                addRenderableWidget(Button.builder(Component.literal("删除"),
                        button -> send(3, entry.entityId(), false))
                        .bounds(resetX, y + 2, resetW, 20).build());
            } else {
                addRenderableWidget(Button.builder(Component.literal("默认"),
                        button -> send(2, entry.entityId(), false))
                        .bounds(resetX, y + 2, resetW, 20).build());
            }
        }

        int controlsWidth = 56 + 8 + 24 + 8 + 24 + 8 + 56;
        int controlsLeft = (width - controlsWidth) / 2;
        int controlsY = height - 52;
        addRenderableWidget(Button.builder(Component.literal("刷新"), b -> {
            requestSent = true;
            requestList();
            visible = scanNearby();
            page = 0;
            init();
        }).bounds(controlsLeft, controlsY, 56, 20).build());
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
            if (page > 0) { page--; init(); }
        }).bounds(controlsLeft + 64, controlsY, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            if (page + 1 < pageCount()) { page++; init(); }
        }).bounds(controlsLeft + 96, controlsY, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> minecraft.setScreen(parent))
                .bounds(controlsLeft + 128, controlsY, 56, 20).build());

        if (!requestSent) { requestSent = true; requestList(); }
    }

    private List<PlayerMobTargetsSyncPayload.Entry> scanNearby() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return List.of();
        int radius = 1;
        try { radius = Math.max(0, ModConfig.OFFHAND_CHUNK_RADIUS.get()); } catch (Throwable ignored) {}
        int cx = SectionPos.blockToSectionCoord(mc.player.getBlockX());
        int cz = SectionPos.blockToSectionCoord(mc.player.getBlockZ());
        AABB area = new AABB(
                (double) (cx - radius) * 16, mc.player.getY() - 32,
                (double) (cz - radius) * 16,
                (double) (cx + radius + 1) * 16, mc.player.getY() + 32,
                (double) (cz + radius + 1) * 16);
        Map<ResourceLocation, Mob> nearby = new TreeMap<>();
        for (Mob mob : mc.level.getEntitiesOfClass(Mob.class, area)) {
            if (!mob.isAlive()) continue;
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            if (id != null) nearby.putIfAbsent(id, mob);
        }

        List<PlayerMobTargetsSyncPayload.Entry> result = new ArrayList<>();
        for (var entry : nearby.entrySet()) {
            boolean defaultEnabled = isHostile(entry.getValue());
            PlayerMobTargetsSyncPayload.Entry override = findConfigured(entry.getKey().toString());
            PacketDistributor.sendToServer(new PlayerMobTargetsRequestPayload(4, entry.getKey().toString(), defaultEnabled));
            result.add(new PlayerMobTargetsSyncPayload.Entry(entry.getKey().toString(),
                    override == null ? defaultEnabled : override.enabled(), override != null));
        }
        for (PlayerMobTargetsSyncPayload.Entry saved : targets) {
            if (result.stream().noneMatch(e -> e.entityId().equals(saved.entityId()))) result.add(saved);
        }
        sortEntries(result);
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

    private PlayerMobTargetsSyncPayload.Entry findConfigured(String id) {
        return targets.stream().filter(e -> e.entityId().equals(id)).findFirst().orElse(null);
    }

    private boolean isConfigured(String id) {
        return findConfigured(id) != null;
    }

    private List<PlayerMobTargetsSyncPayload.Entry> filteredEntries() {
        String query = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return visible;
        return visible.stream().filter(entry -> {
            String id = entry.entityId().toLowerCase(Locale.ROOT);
            String namespace = namespaceOf(entry.entityId()).toLowerCase(Locale.ROOT);
            String name = displayName(entry.entityId()).toLowerCase(Locale.ROOT);
            return id.contains(query) || namespace.contains(query) || name.contains(query);
        }).toList();
    }

    private int pageCount() {
        return Math.max(1, (filteredEntries().size() + Math.max(1, rowsPerPage) - 1) / Math.max(1, rowsPerPage));
    }

    private void requestList() {
        PacketDistributor.sendToServer(new PlayerMobTargetsRequestPayload(ACTION_LIST, "", false));
    }

    private void send(int action, String id, boolean enabled) {
        PacketDistributor.sendToServer(new PlayerMobTargetsRequestPayload(action, id, enabled));
        if (action == ACTION_SET) {
            targets.removeIf(e -> e.entityId().equals(id));
            targets.add(new PlayerMobTargetsSyncPayload.Entry(id, enabled, true));
        } else if (action == 2) {
            targets.removeIf(e -> e.entityId().equals(id));
            targets.add(new PlayerMobTargetsSyncPayload.Entry(id, false, false));
        } else if (action == 3) {
            targets.removeIf(e -> e.entityId().equals(id));
        }
        init();
    }

    public static void receive(List<PlayerMobTargetsSyncPayload.Entry> entries) {
        if (current == null) return;
        current.targets = new ArrayList<>(entries);
        current.requestSent = true;
        current.init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        GuiLayout.drawFrame(graphics, this.width, this.height);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "副手火把 / 护符 · 附近生物", width / 2, 10, 0xFFD700);
        graphics.drawCenteredString(font, "扫描副手攻击范围；敌对/仇恨生物默认开启，其他生物默认关闭",
                width / 2, 24, 0xAAAAAA);
        List<PlayerMobTargetsSyncPayload.Entry> filtered = filteredEntries();
        graphics.drawCenteredString(font, "第 " + (page + 1) + " / " + pageCount()
                + " 页    搜索结果 " + filtered.size() + " 种 / 范围内 " + visible.size() + " 种", width / 2, 36, 0xFFFFFF);

        int rowHeight = 44;
        int listTop = 72;
        int left = Math.max(8, (width - Math.min(420, width - 16)) / 2);
        int listWidth = Math.min(420, width - 16);
        int start = page * rowsPerPage;
        int end = Math.min(filtered.size(), start + rowsPerPage);
        if (filtered.isEmpty()) {
            graphics.drawCenteredString(font, "攻击范围内暂时没有生物，点击刷新重新扫描。",
                    width / 2, height / 2 - 8, 0xBBBBBB);
        }
        ResourceLocation hoveredId = null;
        for (int i = start; i < end; i++) {
            PlayerMobTargetsSyncPayload.Entry entry = filtered.get(i);
            int y = listTop + (i - start) * rowHeight;
            if (mouseX >= left + 4 && mouseX <= left + 32 && mouseY >= y + 16 && mouseY <= y + 42) {
                hoveredId = ResourceLocation.tryParse(entry.entityId());
            }
            graphics.fill(left, y + 15, left + listWidth, y + 43, 0xFF172438);
            graphics.fill(left, y + 15, left + 2, y + 43, GuiLayout.ACCENT);
            graphics.renderOutline(left, y + 15, listWidth, 28, GuiLayout.BORDER);
            ResourceLocation id = ResourceLocation.tryParse(entry.entityId());
            String name = id == null ? entry.entityId()
                    : BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                    .map(type -> type.getDescription().getString()).orElse(entry.entityId());
            String state = entry.enabled() ? "开启" : "关闭";
            if (!entry.configured()) state += "（默认）";
            String namespace = id == null ? "unknown" : id.getNamespace();
            if (i == start || !namespace.equals(namespaceOf(filtered.get(i - 1).entityId()))) {
                String group = namespace.equals("minecraft") ? "Minecraft 原版" : namespace;
                graphics.fill(left, y + 13, left + listWidth, y + 14, 0xFF777777);
                graphics.drawString(font, group, left + 4, y + 3, 0xFFFFD700);
            }
            // 预览区域严格限制在本行内；巨型实体即使模型超出默认尺寸，也不会覆盖相邻条目。
            graphics.enableScissor(left + 4, y + 16, left + 32, y + 42);
            renderPreview(graphics, id, left + 18, y + 38);
            graphics.disableScissor();
            graphics.drawString(font, font.plainSubstrByWidth(name + " · " + state, listWidth - 154),
                    left + 38, y + 23, entry.enabled() ? 0xFFFFFF : 0xAAAAAA);
            graphics.drawString(font, entry.entityId(), left + 38, y + 33, 0x888888);
        }
        // 点击头像进入独立预览页，不再显示遮挡列表的悬浮窗。
    }

    private void renderHoverPreview(GuiGraphics graphics, ResourceLocation id, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Mob preview = previews.computeIfAbsent(id, key -> {
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
        String previewName = BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(type -> type.getDescription().getString()).orElse(id.toString());
        graphics.drawCenteredString(font, font.plainSubstrByWidth(previewName, boxW - 12), x + boxW / 2, y + 7, 0xFFFFFFFF);
        try {
            preview.setNoAi(true);
            InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, x + 8, y + 24,
                    x + boxW - 8, y + boxH - 8, Math.max(1, Math.round(scale)), 0.0F, 0.0F, 0.0F, preview);
        } catch (Throwable ignored) { }
    }


    private String namespaceOf(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        return id == null ? "unknown" : id.getNamespace();
    }

    private String displayName(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        return id == null ? rawId : BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                .map(type -> type.getDescription().getString()).orElse(rawId);
    }

    private void sortEntries(List<PlayerMobTargetsSyncPayload.Entry> entries) {
        entries.sort(Comparator
                .comparingInt((PlayerMobTargetsSyncPayload.Entry e) -> namespaceOf(e.entityId()).equals("minecraft") ? 0 : 1)
                .thenComparing(e -> namespaceOf(e.entityId()).toLowerCase(Locale.ROOT))
                .thenComparing(e -> displayName(e.entityId()).toLowerCase(Locale.ROOT))
                .thenComparing(PlayerMobTargetsSyncPayload.Entry::entityId));
    }

    private void renderPreview(GuiGraphics graphics, ResourceLocation id, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (id == null || mc.level == null) return;
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int left = Math.max(8, (width - Math.min(420, width - 16)) / 2);
            int start = page * rowsPerPage;
            List<PlayerMobTargetsSyncPayload.Entry> filtered = filteredEntries();
            for (int i = start; i < Math.min(filtered.size(), start + rowsPerPage); i++) {
                int y = 72 + (i - start) * 44;
                if (mouseX >= left + 4 && mouseX <= left + 34 && mouseY >= y + 15 && mouseY <= y + 43) {
                    ResourceLocation id = ResourceLocation.tryParse(filtered.get(i).entityId());
                    if (id != null) { minecraft.setScreen(new MobModelPreviewScreen(this, id)); return true; }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY < 0 && page + 1 < pageCount()) { page++; init(); return true; }
        if (deltaY > 0 && page > 0) { page--; init(); return true; }
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void removed() { if (current == this) current = null; }

    @Override
    public boolean isPauseScreen() { return false; }
}
