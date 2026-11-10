package com.torchcampfiredamage.client;

import imgui.ImDrawData;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.gl3.ImGuiImplGl3;
import imgui.type.ImBoolean;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * First Dear ImGui integration pass.
 *
 * The screen deliberately owns only the presentation layer. Existing Minecraft
 * settings screens remain available as a compatibility fallback while the
 * remaining pages are migrated.
 */
public final class ImGuiSettingsScreen extends Screen {
    private static final ImGuiImplGl3 RENDERER = new ImGuiImplGl3();
    private static boolean contextCreated;
    private static boolean rendererReady;

    private final Screen parent;
    private final ImBoolean demoToggle = new ImBoolean(true);
    private final float[] demoSlider = {6.0f};
    private int selectedPage;

    public ImGuiSettingsScreen(Screen parent) {
        super(Component.literal("守卫火把"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        if (!contextCreated) {
            ImGui.createContext();
            ImGui.styleColorsDark();
            ImGui.getIO().IniFilename = null;
            contextCreated = true;
        }
        if (!rendererReady) {
            RENDERER.init("#version 150");
            rendererReady = true;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xD9090D16);

        ImGuiIO io = ImGui.getIO();
        io.setDisplaySize(this.width, this.height);
        io.setDisplayFramebufferScale(
                (float) minecraft.getWindow().getWidth() / Math.max(1, this.width),
                (float) minecraft.getWindow().getHeight() / Math.max(1, this.height));
        io.setDeltaTime(Math.max(0.001f, partialTick / 20.0f));
        io.addMousePosEvent(mouseX, mouseY);

        ImGui.newFrame();
        drawWorkspace();
        ImGui.render();

        ImDrawData drawData = ImGui.getDrawData();
        RENDERER.renderDrawData(drawData);
    }

    private void drawWorkspace() {
        ImGui.setNextWindowPos(24.0f, 24.0f);
        ImGui.setNextWindowSize(Math.max(520.0f, this.width - 48.0f), Math.max(360.0f, this.height - 48.0f));
        int flags = 1 | 2 | 4 | 8 | 32 | 64 | 128;
        if (ImGui.begin("守卫火把##workspace", flags)) {
            ImGui.text("GUARD TORCH");
            ImGui.sameLine();
            ImGui.textDisabled("  /  CLIENT CONFIGURATION");
            ImGui.separator();

            ImGui.beginChild("sidebar", 176.0f, 0.0f, true);
            ImGui.text("WORKSPACE");
            ImGui.separator();
            String[] pages = {"总览", "通用设置", "副手火把", "放置火把", "生物列表", "界面预览"};
            for (int i = 0; i < pages.length; i++) {
                if (ImGui.selectable(pages[i], selectedPage == i, 0, 0.0f, 30.0f)) {
                    selectedPage = i;
                }
            }
            ImGui.separator();
            ImGui.textDisabled("NEOFORGE 1.21.1");
            ImGui.endChild();

            ImGui.sameLine();
            ImGui.beginChild("content", 0.0f, 0.0f, false);
            ImGui.text("守卫火把");
            ImGui.textDisabled("Dear ImGui Java 渲染验证界面");
            ImGui.separator();

            switch (selectedPage) {
                case 0 -> {
                    ImGui.text("欢迎使用守卫火把");
                    ImGui.textWrapped("这是新的游戏内界面渲染层。接下来会逐页迁移现有设置，并保留原有配置与同步逻辑。");
                    ImGui.spacing();
                    ImGui.text("当前阶段：渲染 / 输入 / 尺寸适配验证");
                    ImGui.textDisabled("此页面中的演示控件暂不写入模组配置。");
                }
                case 1 -> {
                    ImGui.text("通用设置");
                    ImGui.checkbox("演示开关（不写入配置）", demoToggle);
                    ImGui.sliderFloat("演示数值", demoSlider, 0.0f, 20.0f, "%.1f");
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
                    ImGui.checkbox("复选框", demoToggle);
                    ImGui.sliderFloat("滑块", demoSlider, 0.0f, 20.0f);
                }
            }

            ImGui.spacing();
            ImGui.separator();
            if (ImGui.button("返回旧版设置界面")) {
                if (minecraft != null) {
                    minecraft.setScreen(new GlobalSettingsScreen(parent));
                }
            }
            ImGui.endChild();
        }
        ImGui.end();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ImGui.getIO().addMouseButtonEvent(button, true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        ImGui.getIO().AddMouseButtonEvent(button, false);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        ImGui.getIO().addMousePosEvent((float) mouseX, (float) mouseY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ImGui.getIO().addMouseWheelEvent((float) scrollX, (float) scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        ImGui.getIO().addInputCharacter((int) codePoint);
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        // Keep the context and GL renderer alive between settings screens.
        // They are released by the client process when Minecraft exits.
        super.removed();
    }
}
