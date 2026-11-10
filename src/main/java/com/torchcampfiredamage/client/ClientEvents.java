package com.torchcampfiredamage.client;

import com.torchcampfiredamage.block.WardSourceBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 原版 Screen 入口：O 键打开全局设置，普通右键守卫火把打开该方块设置。
 */
@EventBusSubscriber(modid = "torchcampfiredamage", value = Dist.CLIENT)
public class ClientEvents {
    private static boolean wasUsePressed;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (ModKeyMappings.OPEN_SETTINGS.consumeClick()) {
            if (player != null && mc.level != null && mc.screen == null) {
                mc.setScreen(new GlobalSettingsScreen(null));
            }
            return;
        }

        if (player == null || mc.level == null) {
            wasUsePressed = false;
            return;
        }

        if (mc.screen != null) {
            wasUsePressed = mc.options.keyUse.isDown();
            return;
        }

        boolean pressed = mc.options.keyUse.isDown();
        boolean justPressed = pressed && !wasUsePressed;
        wasUsePressed = pressed;
        if (!justPressed || mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockEntity be = mc.level.getBlockEntity(hit.getBlockPos());
        if (be instanceof WardSourceBlockEntity ward) {
            mc.setScreen(new WardConfigScreen(hit.getBlockPos(), ward));
        }
    }
}