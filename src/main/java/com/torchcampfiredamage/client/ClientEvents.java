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
 * 客户端：右键守卫火把直接打开设置界面（不需要潜行）。
 * <p>
 * 注：潜行 + 右键的路径由服务端 InteractionHandler 拦截，
 * 这里处理普通右键（不潜行）的情况。
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

        // 有界面打开时不处理
        if (mc.screen != null) {
            wasUsePressed = mc.options.keyUse.isDown();
            return;
        }

        boolean pressed = mc.options.keyUse.isDown();
        boolean justPressed = pressed && !wasUsePressed;
        wasUsePressed = pressed;

        if (!justPressed) {
            return;
        }

        // 瞄准守卫方块
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