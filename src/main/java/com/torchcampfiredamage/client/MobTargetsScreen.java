package com.torchcampfiredamage.client;

import com.torchcampfiredamage.config.ModConfig;
import com.torchcampfiredamage.network.PlayerMobTargetsRequestPayload;
import com.torchcampfiredamage.network.PlayerMobTargetsSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
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

    public MobTargetsScreen(Screen parent) {
        super(Component.literal("副手火把生物列表"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        current = this;
        rowsPerPage = Math.max(1, Math.min(8, (height - 112) / 28));
        visible = scanNearby();
        int pageCount = pageCount();
        if (page >= pageCount) page = pageCount - 1;
        clearWidgets();

        int start = page * rowsPerPage;
        int end = Math.min(visible.size(), start + rowsPerPage);
        int rowHeight = 26;
        int listTop = 48;
        int left = Math.max(8, (width - Math.min(420, width - 16)) / 2);
        int listWidth = Math.min(420, width - 16);

        for (int i = start; i < end; i++) {
            PlayerMobTargetsSyncPayload.Entry entry = visible.get(i);
            int y = listTop + (i - start) * rowHeight;
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

    private int pageCount() {
        return Math.max(1, (visible.size() + Math.max(1, rowsPerPage) - 1) / Math.max(1, rowsPerPage));
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
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "副手火把 / 护符 · 附近生物", width / 2, 10, 0xFFD700);
        graphics.drawCenteredString(font, "扫描副手攻击范围；敌对/仇恨生物默认开启，其他生物默认关闭",
                width / 2, 24, 0xAAAAAA);
        graphics.drawCenteredString(font, "第 " + (page + 1) + " / " + pageCount()
                + " 页    当前范围内 " + visible.size() + " 种", width / 2, 36, 0xFFFFFF);

        int rowHeight = 26;
        int listTop = 48;
        int left = Math.max(8, (width - Math.min(420, width - 16)) / 2);
        int listWidth = Math.min(420, width - 16);
        int start = page * rowsPerPage;
        int end = Math.min(visible.size(), start + rowsPerPage);
        if (visible.isEmpty()) {
            graphics.drawCenteredString(font, "攻击范围内暂时没有生物，点击刷新重新扫描。",
                    width / 2, height / 2 - 8, 0xBBBBBB);
        }
        for (int i = start; i < end; i++) {
            PlayerMobTargetsSyncPayload.Entry entry = visible.get(i);
            int y = listTop + (i - start) * rowHeight;
            graphics.fill(left, y, left + listWidth, y + 24, 0x66333333);
            ResourceLocation id = ResourceLocation.tryParse(entry.entityId());
            String name = id == null ? entry.entityId()
                    : BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                    .map(type -> type.getDescription().getString()).orElse(entry.entityId());
            String state = entry.enabled() ? "开启" : "关闭";
            if (!entry.configured()) state += "（默认）";
            graphics.drawString(font, font.plainSubstrByWidth(name + " · " + state, listWidth - 120),
                    left + 8, y + 7, entry.enabled() ? 0xFFFFFF : 0xAAAAAA);
            graphics.drawString(font, entry.entityId(), left + 8, y + 16, 0x888888);
        }
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
