package com.torchcampfiredamage.client;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Guard Torch settings UI. ImGuiMC owns the ImGui context, frame lifecycle,
 * Minecraft input integration and OpenGL renderer; this class only draws UI.
 */
public final class ImGuiSettingsScreen extends Screen {
    private static final ImBoolean DEMO_TOGGLE = new ImBoolean(true);
    private static final float[] DEMO_SLIDER = {6.0f};
    private static volatile ImGuiSettingsScreen activeScreen;
    private static boolean firstDrawLogged;
    private static boolean vanillaRenderLogged;
    private static boolean firstWindowResultLogged;
    private final Screen parent;
    private int selectedPage;

    public ImGuiSettingsScreen(Screen parent) {
        super(Component.literal("守卫火把"));
        this.parent = parent;
        ClientDebugLog.info("ImGuiSettingsScreen constructed; parent="
                + (parent == null ? "<none>" : parent.getClass().getName()));
    }

    @Override
    protected void init() {
        super.init();
        activeScreen = this;
        firstDrawLogged = false;
        vanillaRenderLogged = false;
        firstWindowResultLogged = false;
        ClientDebugLog.info("ImGuiSettingsScreen.init completed; guiSize=" + width + "x" + height
                + ", activeScreenAssigned=true.");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // ImGuiMC renders this screen from its post-frame event.
        if (!vanillaRenderLogged) {
            vanillaRenderLogged = true;
            ClientDebugLog.info("Vanilla Screen.render callback reached; ImGuiMC event should draw on top.");
        }
    }

    public static void renderActiveImGui() {
        ImGuiSettingsScreen screen = activeScreen;
        if (screen == null) {
            return;
        }
        if (Minecraft.getInstance().screen != screen) {
            if (!firstDrawLogged) {
                ClientDebugLog.info("Active settings screen reference exists, but Minecraft.currentScreen differs: "
                        + (Minecraft.getInstance().screen == null ? "<none>" : Minecraft.getInstance().screen.getClass().getName()));
            }
            return;
        }
        if (!firstDrawLogged) {
            firstDrawLogged = true;
            ClientDebugLog.info("ImGui draw callback reached active settings screen; dimensions="
                    + screen.width + "x" + screen.height);
        }
        screen.drawWorkspace();
    }

    private void drawWorkspace() {
        float width = Math.max(520.0f, this.width - 48.0f);
        float height = Math.max(360.0f, this.height - 48.0f);
        ImGui.setNextWindowPos(24.0f, 24.0f, ImGuiCond.Once);
        ImGui.setNextWindowSize(width, height, ImGuiCond.Once);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoSavedSettings;
        boolean windowVisible = ImGui.begin("守卫火把##workspace", flags);
        if (!firstWindowResultLogged) {
            firstWindowResultLogged = true;
            ClientDebugLog.info("ImGui.begin returned " + windowVisible + "; window size request="
                    + width + "x" + height);
        }
        if (windowVisible) {
            ImGui.text("GUARD TORCH");
            ImGui.sameLine();
            ImGui.textDisabled("  /  CLIENT CONFIGURATION");
            ImGui.separator();

            ImGui.beginChild("sidebar", 176.0f, 0.0f, true);
            ImGui.text("WORKSPACE");
            ImGui.separator();
            String[] pages = {"总览", "通用设置", "副手火把", "放置火把", "生物列表", "界面预览"};
            for (int i = 0; i < pages.length; i++) {
                if (ImGui.selectable(pages[i], selectedPage == i)) {
                    selectedPage = i;
                }
            }
            ImGui.separator();
            ImGui.textDisabled("NEOFORGE 1.21.1");
            ImGui.endChild();

            ImGui.sameLine();
            ImGui.beginChild("content", 0.0f, 0.0f, false);
            ImGui.text("守卫火把");
            ImGui.textDisabled("由 ImGuiMC 提供游戏内渲染与输入支持");
            ImGui.separator();
            switch (selectedPage) {
                case 0 -> {
                    ImGui.text("欢迎使用守卫火把");
                    ImGui.textWrapped("新的界面渲染层已经交由 ImGuiMC 管理。接下来会逐页迁移现有设置，并保留原有配置与同步逻辑。");
                    ImGui.spacing();
                    ImGui.text("当前阶段：ImGuiMC 集成验证");
                    ImGui.textDisabled("此页面中的演示控件暂不写入模组配置。");
                }
                case 1 -> {
                    ImGui.text("通用设置");
                    ImGui.checkbox("演示开关（不写入配置）", DEMO_TOGGLE);
                    ImGui.sliderFloat("演示数值", DEMO_SLIDER, 0.0f, 20.0f, "%.1f");
                    ImGui.textDisabled("控件验证通过后，将绑定到现有 ModConfig。");
                }
                case 2 -> {
                    ImGui.text("副手火把");
                    ImGui.textWrapped("副手伤害模式、攻击力模式和按键设置将在后续迁移中接入现有配置。");
                }
                case 3 -> {
                    ImGui.text("放置火把");
                    ImGui.textWrapped("放置火把的伤害、范围、显示与附魔配置将在后续迁移中接入。");
                }
                case 4 -> {
                    ImGui.text("生物列表");
                    ImGui.textWrapped("保留自动收录、默认规则、显式覆盖和永久删除行为。");
                }
                default -> {
                    ImGui.text("控件预览");
                    ImGui.button("按钮");
                    ImGui.sameLine();
                    ImGui.checkbox("复选框", DEMO_TOGGLE);
                    ImGui.sliderFloat("滑块", DEMO_SLIDER, 0.0f, 20.0f);
                }
            }
            ImGui.spacing();
            ImGui.separator();
            if (ImGui.button("返回旧版设置界面")) {
                Minecraft.getInstance().setScreen(new GlobalSettingsScreen(parent));
            }
            ImGui.endChild();
        }
        ImGui.end();
    }

    @Override
    public void onClose() {
        ClientDebugLog.info("ImGuiSettingsScreen.onClose called.");
        activeScreen = null;
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        if (activeScreen == this) {
            activeScreen = null;
        }
        ClientDebugLog.info("ImGuiSettingsScreen.removed called; active reference cleared="
                + (activeScreen == null));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
