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
 * 客户端：检测潜行右键守卫火源 → 打开设置界面。
 */
@EventBusSubscriber(modid = "torchcampfiredamage", value = Dist.CLIENT)
public class ClientEvents {

    private static boolean wasUsePressed = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            wasUsePressed = false;
            return;
        }

        boolean pressed = mc.options.keyUse.isDown();
        boolean justPressed = pressed && !wasUsePressed;
        wasUsePressed = pressed;

        if (!justPressed) {
            return;
        }

        if (!player.isShiftKeyDown()) {
            return;
        }

        // 是否瞄准守卫方块
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockEntity be = mc.level.getBlockEntity(hit.getBlockPos());
        if (be instanceof WardSourceBlockEntity ward) {
            mc.setScreen(new WardConfigScreen(hit.getBlockPos(), ward));
        }
    }
}