package com.torchcampfiredamage.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品选择器：网格展示游戏内所有物品（含非方块道具），支持搜索、翻页。
 * 供「环上旋转物品」使用。
 */
public class ItemPickerScreen extends Screen {

    private static final int COLS = 9;
    private static final int ROWS = 5;
    private static final int CELL = 20;

    private final Screen parent;
    /** 要设置的槽位 0~3 */
    private final int slot;

    private EditBox searchBox;
    private final List<Item> filtered = new ArrayList<>();
    private int scrollRow = 0;

    private int gridX, gridY;

    public ItemPickerScreen(Screen parent, int slot) {
        super(Component.literal("选择物品"));
        this.parent = parent;
        this.slot = slot;
    }

    @Override
    protected void init() {
        rebuildList("");

        int cx = this.width / 2;
        int cy = this.height / 2;

        gridX = cx - (COLS * CELL) / 2;
        gridY = cy - (ROWS * CELL) / 2 - 10;

        searchBox = new EditBox(this.font, cx - 150, gridY - 28, 300, 18,
                Component.literal("搜索"));
        searchBox.setHint(Component.literal("输入物品名搜索…"));
        searchBox.setResponder(this::rebuildList);
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.literal("▲ 上一页"), b -> {
            scrollRow = Math.max(0, scrollRow - ROWS);
        }).bounds(cx - 150, gridY + ROWS * CELL + 6, 70, 18).build());

        addRenderableWidget(Button.builder(Component.literal("▼ 下一页"), b -> {
            int maxRow = Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS);
            scrollRow = Math.min(maxRow, scrollRow + ROWS);
        }).bounds(cx - 74, gridY + ROWS * CELL + 6, 70, 18).build());

        addRenderableWidget(Button.builder(Component.literal("清空"), b -> {
            if (slot == 99) {
                DisplaySettingsScreen.centerItem = ItemStack.EMPTY;
            } else if (slot >= 0 && slot < RingSettingsScreen.RINGS.size()) {
                RingSettingsScreen.RINGS.get(slot).item = ItemStack.EMPTY;
            }
            minecraft.setScreen(parent);
        }).bounds(cx + 2, gridY + ROWS * CELL + 6, 50, 18).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b ->
                minecraft.setScreen(parent))
                .bounds(cx + 58, gridY + ROWS * CELL + 6, 50, 18).build());
    }

    private void rebuildList(String query) {
        filtered.clear();
        String q = query == null ? "" : query.trim().toLowerCase();

        for (Item it : BuiltInRegistries.ITEM) {
            if (it == Items.AIR) continue;

            ResourceLocation id = BuiltInRegistries.ITEM.getKey(it);
            if (id == null) continue;

            ItemStack stack = new ItemStack(it);
            if (stack.isEmpty()) continue;

            if (q.isEmpty()
                    || id.toString().contains(q)
                    || stack.getHoverName().getString().toLowerCase().contains(q)) {
                filtered.add(it);
            }
        }
        scrollRow = 0;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scrollRow + r) * COLS + c;
                if (idx >= filtered.size()) break;

                int x = gridX + c * CELL;
                int y = gridY + r * CELL;
                if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) {
                    Item chosen = filtered.get(idx);
                    if (slot == 99) {
                        DisplaySettingsScreen.centerItem = new ItemStack(chosen);
                    } else if (slot >= 0 && slot < RingSettingsScreen.RINGS.size()) {
                        RingSettingsScreen.RINGS.get(slot).item = new ItemStack(chosen);
                    }
                    minecraft.setScreen(parent);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int maxRow = Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS);
        if (dy > 0) scrollRow = Math.max(0, scrollRow - 1);
        else if (dy < 0) scrollRow = Math.min(maxRow, scrollRow + 1);
        return true;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);
        super.render(gg, mouseX, mouseY, partialTick);

        String title = (slot == 99)
                ? "选择中心物品"
                : ("选择旋转物品（第 " + (slot + 1) + " 环）");
        gg.drawCenteredString(this.font, title,
                this.width / 2, gridY - 42, 0xFFD700);

        gg.fill(gridX - 2, gridY - 2,
                gridX + COLS * CELL + 2, gridY + ROWS * CELL + 2, 0xC0000000);

        Item hovered = null;
        int hx = 0, hy = 0;

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scrollRow + r) * COLS + c;
                if (idx >= filtered.size()) break;

                Item it = filtered.get(idx);
                int x = gridX + c * CELL;
                int y = gridY + r * CELL;

                gg.renderItem(new ItemStack(it), x + 2, y + 2);

                if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                    gg.fill(x, y, x + CELL, y + CELL, 0x60FFFFFF);
                    hovered = it;
                    hx = mouseX;
                    hy = mouseY;
                }

                // 当前已选中的物品高亮
                ItemStack cur;
                if (slot == 99) {
                    cur = DisplaySettingsScreen.centerItem;
                } else if (slot >= 0 && slot < RingSettingsScreen.RINGS.size()) {
                    cur = RingSettingsScreen.RINGS.get(slot).item;
                } else {
                    cur = ItemStack.EMPTY;
                }
                if (!cur.isEmpty() && cur.getItem() == it) {
                    gg.fill(x, y, x + CELL, y + 1, 0xFF00FF00);
                    gg.fill(x, y + CELL - 1, x + CELL, y + CELL, 0xFF00FF00);
                    gg.fill(x, y, x + 1, y + CELL, 0xFF00FF00);
                    gg.fill(x + CELL - 1, y, x + CELL, y + CELL, 0xFF00FF00);
                }
            }
        }

        if (hovered != null) {
            gg.renderTooltip(this.font, new ItemStack(hovered), hx, hy);
        }

        int totalRows = Math.max(1, (filtered.size() + COLS - 1) / COLS);
        String page = "第 " + (scrollRow / ROWS + 1) + " / "
                + Math.max(1, (totalRows + ROWS - 1) / ROWS) + " 页  共 "
                + filtered.size() + " 个物品";
        gg.drawString(this.font, page, this.width / 2 - 150,
                gridY + ROWS * CELL + 28, 0xAAAAAA);

        gg.drawCenteredString(this.font, "滚轮翻页，点击选择",
                this.width / 2, gridY + ROWS * CELL + 40, 0x888888);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}