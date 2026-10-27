package com.torchcampfiredamage.events;

import com.torchcampfiredamage.block.WardSourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 潜行 + 右键守卫火源 → 打开参数设置界面（客户端监听，由打开界面处处理）。
 * 服务端这里只负责取消默认动作。
 */
public class InteractionHandler {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof WardSourceBlockEntity)) {
            return;
        }
        if (!player.isShiftKeyDown()) {
            return;
        }
        // 潜行右键 → 打开 GUI（客户端侧 ClientEvents 会处理），服务端阻止默认交互
        event.setCanceled(true);
    }
}