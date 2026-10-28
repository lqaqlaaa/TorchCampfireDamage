package com.torchcampfiredamage.events;

import com.torchcampfiredamage.block.WardSourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 右键守卫火把 → 由客户端 ClientEvents 打开 GUI，服务端这里阻止默认动作。
 * <p>
 * 注意：只有「空手」时才拦截，否则会妨碍用方块/物品正常交互（比如往墙上贴东西）。
 */
public class InteractionHandler {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof WardSourceBlockEntity)) return;

        // 空手右键才打开 GUI（避免影响正常放置/使用物品）
        boolean emptyHand = player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()
                || player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.AIR);

        if (emptyHand) {
            event.setCanceled(true);
        }
    }
}