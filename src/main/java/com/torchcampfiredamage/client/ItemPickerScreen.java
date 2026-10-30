package com.torchcampfiredamage.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
    /** 实际单元格边长（自适应，可能小于 CELL） */
    private int cell = CELL;

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

        /* ---- 自适应：格子尺寸与网格按屏幕压缩 ---- */
        int availW = this.width - 16;
        int availH = this.height - 70;

        cell = Math.min(CELL, Math.max(10, availW / COLS));
        if (ROWS * cell > availH) {
            cell = Math.max(8, availH / ROWS);
        }

        int gw = COLS * cell;
        int gh = ROWS * cell;

        gridX = cx - gw / 2;
        gridY = Math.max(46, (this.height - gh) / 2);

        int searchW = Math.max(100, Math.min(300, availW));
        searchBox = new EditBox(this.font, cx - searchW / 2, gridY - 28, searchW, 16,
                Component.literal("搜索"));
        searchBox.setHint(Component.literal("输入物品名搜索…"));
        searchBox.setResponder(this::rebuildList);
        addRenderableWidget(searchBox);

        /* ---- 底部按钮：四个横排，居中，按屏幕宽度压缩 ---- */
        int by = gridY + gh + 6;
        if (by > this.height - 22) by = this.height - 22;

        int bw = Math.max(40, Math.min(70, (this.width - 30) / 4));
        int gap = 4;
        int totalW = bw * 4 + gap * 3;
        int bx0 = cx - totalW / 2;

        addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
            scrollRow = Math.max(0, scrollRow - ROWS);
        }).bounds(bx0, by, bw, 18)
                .tooltip(Tooltip.create(Component.literal("上一页")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
            int maxRow = Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS);
            scrollRow = Math.min(maxRow, scrollRow + ROWS);
        }).bounds(bx0 + bw + gap, by, bw, 18)
                .tooltip(Tooltip.create(Component.literal("下一页")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("清空"), b -> {
            if (slot == 99) {
                DisplaySettingsScreen.centerItem = ItemStack.EMPTY;
            } else if (slot >= 0 && slot < RingSettingsScreen.RINGS.size()) {
                RingSettingsScreen.RINGS.get(slot).item = ItemStack.EMPTY;
            }
            minecraft.setScreen(parent);
        }).bounds(bx0 + (bw + gap) * 2, by, bw, 18).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b ->
                minecraft.setScreen(parent))
                .bounds(bx0 + (bw + gap) * 3, by, bw, 18).build());
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

                int x = gridX + c * cell;
                int y = gridY + r * cell;
                if (mx >= x && mx < x + cell && my >= y && my < y + cell) {
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
                gridX + COLS * cell + 2, gridY + ROWS * cell + 2, 0xC0000000);

        Item hovered = null;
        int hx = 0, hy = 0;

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scrollRow + r) * COLS + c;
                if (idx >= filtered.size()) break;

                Item it = filtered.get(idx);
                int x = gridX + c * cell;
                int y = gridY + r * cell;

                gg.renderItem(new ItemStack(it), x + 2, y + 2);

                if (mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + cell) {
                    gg.fill(x, y, x + cell, y + cell, 0x60FFFFFF);
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
                    gg.fill(x, y, x + cell, y + 1, 0xFF00FF00);
                    gg.fill(x, y + cell - 1, x + cell, y + cell, 0xFF00FF00);
                    gg.fill(x, y, x + 1, y + cell, 0xFF00FF00);
                    gg.fill(x + cell - 1, y, x + cell, y + cell, 0xFF00FF00);
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

        // 页码画在网格正上方、搜索框下方的空白处，避免超出屏幕
        gg.drawCenteredString(this.font, "§7" + page,
                this.width / 2, gridY - 14, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}