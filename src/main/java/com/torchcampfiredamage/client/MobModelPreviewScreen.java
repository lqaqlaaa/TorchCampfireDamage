package com.torchcampfiredamage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Full-size model inspection; drag in any direction to rotate the entity. */
public final class MobModelPreviewScreen extends Screen {
    private final Screen parent;
    private final ResourceLocation entityId;
    private Mob entity;
    private float yaw;
    private float pitch;
    private EditBox yawInput;
    private EditBox pitchInput;
    private boolean dragging;
    private int left, top, right, bottom;

    public MobModelPreviewScreen(Screen parent, ResourceLocation entityId) {
        super(Component.literal("生物模型预览"));
        this.parent = parent;
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        if (minecraft.level != null) {
            entity = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId)
                    .map(type -> (Mob) type.create(minecraft.level)).orElse(null);
        }
        int inputY = Math.max(54, height - 66);
        int center = width / 2;
        yawInput = new EditBox(font, center - 116, inputY, 88, 18, Component.literal("水平角度"));
        yawInput.setValue(String.format(java.util.Locale.ROOT, "%.1f", yaw));
        yawInput.setHint(Component.literal("水平角度"));
        yawInput.setResponder(v -> { try { yaw = normalizeYaw(Float.parseFloat(v)); } catch (Exception ignored) {} });
        addRenderableWidget(yawInput);
        pitchInput = new EditBox(font, center + 28, inputY, 88, 18, Component.literal("垂直角度"));
        pitchInput.setValue(String.format(java.util.Locale.ROOT, "%.1f", pitch));
        pitchInput.setHint(Component.literal("垂直角度"));
        pitchInput.setResponder(v -> { try { pitch = clampPitch(Float.parseFloat(v)); } catch (Exception ignored) {} });
        addRenderableWidget(pitchInput);
        addRenderableWidget(Button.builder(Component.literal("水平 −"), b -> adjustYaw(-5))
                .bounds(center - 116, inputY + 20, 52, 18).build());
        addRenderableWidget(Button.builder(Component.literal("水平 +"), b -> adjustYaw(5))
                .bounds(center - 60, inputY + 20, 52, 18).build());
        addRenderableWidget(Button.builder(Component.literal("垂直 −"), b -> adjustPitch(-5))
                .bounds(center + 28, inputY + 20, 52, 18).build());
        addRenderableWidget(Button.builder(Component.literal("垂直 +"), b -> adjustPitch(5))
                .bounds(center + 84, inputY + 20, 52, 18).build());
        addRenderableWidget(Button.builder(Component.literal("重置视角"), b -> {
            yaw = 0.0F;
            pitch = 0.0F;
            syncAngleInputs();
        }).bounds(center - 88, height - 24, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> minecraft.setScreen(parent))
                .bounds(center + 8, height - 24, 80, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        GuiLayout.drawFrame(graphics, width, height);
        super.render(graphics, mouseX, mouseY, partialTick);
        String name = entity == null ? entityId.toString() : entity.getType().getDescription().getString();
        GuiLayout.drawTitle(graphics, font, width, 12, name, "按住鼠标左键上下左右拖动，旋转查看模型");
        left = Math.max(12, width / 2 - Math.min(150, width / 3));
        right = Math.min(width - 12, width / 2 + Math.min(150, width / 3));
        top = 48;
        bottom = Math.max(top + 48, height - 100);
        graphics.fill(left, top, right, bottom, 0xFF101A2A);
        graphics.renderOutline(left, top, right - left, bottom - top, GuiLayout.BORDER);
        if (entity != null) {
            entity.setNoAi(true);
            float entityW = Math.max(0.25F, entity.getBbWidth());
            float entityH = Math.max(0.25F, entity.getBbHeight());
            float fit = Math.min((right - left - 28) / (entityW * 2.4F),
                    (bottom - top - 34) / (entityH * 2.4F));
            int scale = Math.max(1, Math.min(80, Math.round(fit)));
            InventoryScreen.renderEntityInInventoryFollowsAngle(graphics,
                    left + 8, top + 8, right - 8, bottom - 8, scale,
                    0.0F, yaw, pitch, entity);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            yaw = normalizeYaw(yaw + (float) dragX * 0.22F);
            pitch = clampPitch(pitch + (float) dragY * 0.22F);
            syncAngleInputs();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private static float normalizeYaw(float value) {
        float wrapped = value % 360.0F;
        return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
    }

    private static float clampPitch(float value) {
        return Math.max(-89.0F, Math.min(89.0F, value));
    }

    private void adjustYaw(float delta) {
        yaw = normalizeYaw(yaw + delta);
        syncAngleInputs();
    }

    private void adjustPitch(float delta) {
        pitch = clampPitch(pitch + delta);
        syncAngleInputs();
    }

    private void syncAngleInputs() {
        if (yawInput != null) yawInput.setValue(String.format(java.util.Locale.ROOT, "%.1f", yaw));
        if (pitchInput != null) pitchInput.setValue(String.format(java.util.Locale.ROOT, "%.1f", pitch));
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
