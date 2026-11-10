package com.torchcampfiredamage.client;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Minimal screen used only to verify ImGuiMC can display one ImGui window. */
public final class ImGuiSettingsScreen extends Screen {
    private static volatile ImGuiSettingsScreen activeScreen;
    private static boolean firstDrawLogged;
    private final Screen parent;

    public ImGuiSettingsScreen(Screen parent) {
        super(Component.literal("守卫火把 - ImGui 测试"));
        this.parent = parent;
        ClientDebugLog.info("Minimal ImGui test screen constructed.");
    }

    @Override
    protected void init() {
        super.init();
        activeScreen = this;
        firstDrawLogged = false;
        ClientDebugLog.info("Minimal ImGui test screen initialized; size=" + width + "x" + height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // ImGuiMC draws its independent overlay from the POST frame callback.
    }

    public static void renderMinimalWindow() {
        ImGuiSettingsScreen screen = activeScreen;
        if (screen == null || Minecraft.getInstance().screen != screen) {
            return;
        }

        ImGui.setNextWindowPos(40.0f, 40.0f, ImGuiCond.Once);
        ImGui.setNextWindowSize(420.0f, 180.0f, ImGuiCond.Once);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings;
        boolean visible = ImGui.begin("守卫火把 - ImGuiMC 窗口测试", flags);
        if (visible) {
            ImGui.text("守卫火把菜单");
            ImGui.separator();
            ImGui.text("如果你能看到这个窗口，说明 ImGuiMC 渲染链路已经正常工作。");
            ImGui.text("当前状态：最小窗口测试");
            ImGui.text("请使用 ESC 关闭窗口。");
        }
        ImGui.end();

        if (!firstDrawLogged) {
            firstDrawLogged = true;
            ClientDebugLog.info("Minimal ImGui window draw call executed; ImGui.begin returned " + visible);
        }
    }

    @Override
    public void onClose() {
        activeScreen = null;
        ClientDebugLog.info("Minimal ImGui test screen closed.");
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        if (activeScreen == this) {
            activeScreen = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
