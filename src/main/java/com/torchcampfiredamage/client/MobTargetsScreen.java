package com.torchcampfiredamage.client;

import com.torchcampfiredamage.network.PlayerMobTargetsRequestPayload;
import com.torchcampfiredamage.network.PlayerMobTargetsSyncPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Player-owned mob target rules, opened from the global settings screen. */
public final class MobTargetsScreen extends Screen {
    private static final int ACTION_LIST = 0;
    private static final int ACTION_SET = 1;
    private static final int ACTION_REMOVE = 2;
    private static MobTargetsScreen current;

    private final Screen parent;
    private List<PlayerMobTargetsSyncPayload.Entry> targets = new ArrayList<>();
    private int page;
    private int rowsPerPage;
    private boolean requested;

    public MobTargetsScreen(Screen parent) {
        super(Component.literal("生物列表"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        current = this;
        rowsPerPage = Math.max(1, Math.min(8, (height - 112) / 28));
        int pageCount = pageCount();
        if (page >= pageCount) page = pageCount - 1;
        clearWidgets();
        if (!requested) { requested = true; requestList(); }

        int start = page * rowsPerPage;
        int end = Math.min(targets.size(), start + rowsPerPage);
        int rowHeight = 26;
        int listTop = 48;
        int left = Math.max(8, (width - Math.min(420, width - 16)) / 2);
        int listWidth = Math.min(420, width - 16);

        for (int i = start; i < end; i++) {
            PlayerMobTargetsSyncPayload.Entry entry = targets.get(i);
            int y = listTop + (i - start) * rowHeight;
            int right = left + listWidth;
            int toggleW = 54;
            int deleteW = 42;
            int toggleX = right - deleteW - toggleW - 8;
            int deleteX = right - deleteW;

            addRenderableWidget(Button.builder(
                    Component.literal(entry.enabled() ? "§a开启" : "§c关闭"),
                    button -> send(ACTION_SET, entry.entityId(), !entry.enabled()))
                    .bounds(toggleX, y + 2, toggleW, 20).build());
            addRenderableWidget(Button.builder(Component.literal("删除"),
                    button -> send(ACTION_REMOVE, entry.entityId(), false))
                    .bounds(deleteX, y + 2, deleteW, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("刷新"), b -> requestList())
                .bounds(width / 2 - 92, height - 52, 56, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> minecraft.setScreen(parent))
                .bounds(width / 2 + 36, height - 52, 56, 20).build());
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
            if (page > 0) { page--; init(); }
        }).bounds(width / 2 - 64, height - 52, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            if (page + 1 < pageCount()) { page++; init(); }
        }).bounds(width / 2 + 8, height - 52, 24, 20).build());
    }

    private int pageCount() {
        return Math.max(1, (targets.size() + Math.max(1, rowsPerPage) - 1) / Math.max(1, rowsPerPage));
    }

    private void requestList() {
        PacketDistributor.sendToServer(new PlayerMobTargetsRequestPayload(ACTION_LIST, "", false));
    }

    private void send(int action, String id, boolean enabled) {
        PacketDistributor.sendToServer(new PlayerMobTargetsRequestPayload(action, id, enabled));
    }

    public static void receive(List<PlayerMobTargetsSyncPayload.Entry> entries) {
        if (current == null) return;
        current.targets = new ArrayList<>(entries);
        current.page = Math.min(current.page, current.pageCount() - 1);
        current.init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, "玩家生物列表", width / 2, 10, 0xFFD700);
        graphics.drawCenteredString(font, "攻击生物后自动收录；开关会同时影响方块火把和副手/护符",
                width / 2, 24, 0xAAAAAA);

        int pageCount = pageCount();
        graphics.drawCenteredString(font, "第 " + (page + 1) + " / " + pageCount
                + " 页    共 " + targets.size() + " 种", width / 2, 36, 0xFFFFFF);

        int rowHeight = 26;
        int listTop = 48;
        int left = Math.max(8, (width - Math.min(420, width - 16)) / 2);
        int listWidth = Math.min(420, width - 16);
        int start = page * rowsPerPage;
        int end = Math.min(targets.size(), start + rowsPerPage);

        if (targets.isEmpty()) {
            graphics.drawCenteredString(font, "列表为空。主动攻击生物并造成伤害后，它会自动出现在这里。",
                    width / 2, height / 2 - 8, 0xBBBBBB);
        }

        for (int i = start; i < end; i++) {
            PlayerMobTargetsSyncPayload.Entry entry = targets.get(i);
            int y = listTop + (i - start) * rowHeight;
            graphics.fill(left, y, left + listWidth, y + 24, 0x66333333);

            ResourceLocation id = ResourceLocation.tryParse(entry.entityId());
            EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            String displayName = type == null ? entry.entityId() + "（未安装）" : type.getDescription().getString();
            int nameMax = Math.max(20, listWidth - 150);
            graphics.drawString(font, font.plainSubstrByWidth(displayName, nameMax), left + 8, y + 5,
                    type == null ? 0xFF7777 : 0xFFFFFF);
            graphics.drawString(font, entry.entityId(), left + 8, y + 15, 0x888888);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY < 0 && page + 1 < pageCount()) { page++; init(); return true; }
        if (deltaY > 0 && page > 0) { page--; init(); return true; }
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        if (current == this) current = null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
