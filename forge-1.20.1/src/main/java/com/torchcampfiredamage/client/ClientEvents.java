package com.torchcampfiredamage.client;

import com.torchcampfiredamage.block.WardSourceBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端：右键守卫火把直接打开设置界面。
 */
@Mod.EventBusSubscriber(modid = "torchcampfiredamage", value = Dist.CLIENT)
public class ClientEvents {

    private static boolean wasUsePressed = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
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

        if (!justPressed) {
            return;
        }

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