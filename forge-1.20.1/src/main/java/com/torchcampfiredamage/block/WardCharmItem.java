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
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * 守卫护符 —— 手持生效，也可以装备到 Curios 饰品栏。
 * <p>
 * 放在副手或饰品栏时触发范围伤害（由 OffhandTorchHandler 处理）。
 * 右键可以变回守卫火把方块。
 * <p>
 * Curios 1.20.1 通过实现 {@link ICurioItem} 接口注册饰品。
 */
public class WardCharmItem extends Item implements ICurioItem {

    public WardCharmItem(Properties props) {
        super(props);
    }

    /** 右键：变回守卫火把 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {
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

    /* ================= Curios 饰品接口 ================= */

    /** 允许装备到饰品栏 */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    /** 允许通过右键直接装备 */
    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7手持时自动攻击周围敌对生物").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7放在副手或饰品栏即可生效").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("§8右键变回守卫火把").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}