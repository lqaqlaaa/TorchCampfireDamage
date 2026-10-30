package com.torchcampfiredamage.block;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 守卫护符 —— 只能手持生效的物品形态。
 * <p>
 * 放在副手时会触发范围伤害（由 OffhandTorchHandler 处理）。
 * 右键可以变回守卫火把方块。
 * <p>
 * 合成方式：守卫火把 → 守卫护符（见 recipe/ward_charm.json）
 */
public class WardCharmItem extends Item {

    public WardCharmItem(Properties props) {
        super(props);
    }

    /** 右键：变回守卫火把 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {
            // 变回守卫火把
            ItemStack torch = new ItemStack(ModBlocks.WARD_TORCH_ITEM.get(), stack.getCount());
            stack.shrink(stack.getCount());

            if (!player.getInventory().add(torch)) {
                player.drop(torch, false);
            }

            level.playSound(null, player.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
            player.displayClientMessage(
                    Component.literal("§d守卫护符 §7→ §e守卫火把"), true);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7手持时自动攻击周围敌对生物").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7放在副手或饰品栏即可生效").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("§8可与守卫火把互相合成").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }
}