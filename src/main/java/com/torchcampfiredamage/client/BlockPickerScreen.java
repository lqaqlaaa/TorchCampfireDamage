package com.torchcampfiredamage.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * 方块选择器：网格展示所有方块，支持搜索、滚动。
 */
public class BlockPickerScreen extends Screen {

    private static final int COLS = 9;
    private static final int ROWS = 5;
    private static final int CELL = 20;
    /** 实际单元格边长（自适应） */
    private int cell = CELL;

    private final Screen parent;
    private final boolean forA;

    private EditBox searchBox;
    private final List<Block> filtered = new ArrayList<>();
    private int scrollRow = 0;

    private int gridX, gridY;

    public BlockPickerScreen(Screen parent, boolean forA) {
        super(Component.literal("选择方块"));
        this.parent = parent;
        this.forA = forA;
    }

    @Override
    protected void init() {
        rebuildList("");

int cx = this.width / 2;

        /* ---- 自适应 ---- */
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

        // 搜索框
        int searchW = Math.max(100, Math.min(300, availW));
        searchBox = new EditBox(this.font, cx - searchW / 2, gridY - 28, searchW, 16,
                Component.literal("搜索"));
        searchBox.setHint(Component.literal("输入方块名搜索…"));
        searchBox.setResponder(this::rebuildList);
        addRenderableWidget(searchBox);

        /* ---- 底部按钮：三个横排居中 ---- */
        int by = gridY + gh + 6;
        if (by > this.height - 22) by = this.height - 22;

        int bw = Math.max(40, Math.min(70, (this.width - 26) / 3));
        int gap = 4;
        int totalW = bw * 3 + gap * 2;
        int bx0 = cx - totalW / 2;

        addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
            scrollRow = Math.max(0, scrollRow - ROWS);
        }).bounds(bx0, by, bw, 18).build());

        addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
            int maxRow = Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS);
            scrollRow = Math.min(maxRow, scrollRow + ROWS);
        }).bounds(bx0 + bw + gap, by, bw, 18).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b ->
                minecraft.setScreen(parent))
                .bounds(bx0 + (bw + gap) * 2, by, bw, 18).build());
    }

    private void rebuildList(String query) {
        filtered.clear();
        String q = query == null ? "" : query.trim().toLowerCase();

        for (Block b : BuiltInRegistries.BLOCK) {
            if (b == Blocks.AIR) continue;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
            if (id == null) continue;

            // 过滤掉没有物品形态的方块（如某些技术方块）
            ItemStack stack = new ItemStack(b);
            if (stack.isEmpty()) continue;

            if (q.isEmpty()
                    || id.toString().contains(q)
                    || stack.getHoverName().getString().toLowerCase().contains(q)) {
                filtered.add(b);
            }
        }
        scrollRow = 0;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 判断是否点在网格里
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scrollRow + r) * COLS + c;
                if (idx >= filtered.size()) break;

                int x = gridX + c * cell;
                int y = gridY + r * cell;
                if (mx >= x && mx < x + cell && my >= y && my < y + cell) {
                    Block chosen = filtered.get(idx);
                    if (forA) DisplaySettingsScreen.blockA = chosen;
                    else DisplaySettingsScreen.blockB = chosen;
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

        // 标题
        gg.drawCenteredString(this.font,
                forA ? "选择方块 A（黑格）" : "选择方块 B（白格）",
                this.width / 2, gridY - 42, 0xFFD700);

        // 网格背景
        gg.fill(gridX - 2, gridY - 2,
                gridX + COLS * cell + 2, gridY + ROWS * cell + 2, 0xC0000000);

        Block hovered = null;
        int hoverX = 0, hoverY = 0;

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scrollRow + r) * COLS + c;
                if (idx >= filtered.size()) break;

                Block b = filtered.get(idx);
                int x = gridX + c * cell;
                int y = gridY + r * cell;

                ItemStack stack = new ItemStack(b);
                gg.renderItem(stack, x + 2, y + 2);

                // 悬停高亮
                if (mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + cell) {
                    gg.fill(x, y, x + cell, y + cell, 0x60FFFFFF);
                    hovered = b;
                    hoverX = mouseX;
                    hoverY = mouseY;
                }
                // 当前已选的标记
                Block current = forA ? DisplaySettingsScreen.blockA : DisplaySettingsScreen.blockB;
                if (b == current) {
                    gg.fill(x, y, x + cell, y + 1, 0xFF00FF00);
                    gg.fill(x, y + cell - 1, x + cell, y + cell, 0xFF00FF00);
                    gg.fill(x, y, x + 1, y + cell, 0xFF00FF00);
                    gg.fill(x + cell - 1, y, x + cell, y + cell, 0xFF00FF00);
                }
            }
        }

        // 悬停提示
        if (hovered != null) {
            ItemStack stack = new ItemStack(hovered);
            gg.renderTooltip(this.font, stack, hoverX, hoverY);
        }

        // 页码（画在网格上方，避免超出屏幕）
        int totalRows = Math.max(1, (filtered.size() + COLS - 1) / COLS);
        String page = "第 " + (scrollRow / ROWS + 1) + " / "
                + Math.max(1, (totalRows + ROWS - 1) / ROWS) + " 页  共 "
                + filtered.size() + " 个方块";
        gg.drawCenteredString(this.font, "§7" + page,
                this.width / 2, gridY - 14, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}