package com.torchcampfiredamage.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;

/**
 * 守卫火把物品：自动处理「放上方 → 站立火把」「放侧面 → 墙火把」。
 * 这是原版火把使用的同一个类（StandingAndWallBlockItem）。
 */
public class WardTorchItem extends StandingAndWallBlockItem {

    public WardTorchItem(Block standingBlock, Block wallBlock, Properties props, Direction attachmentDirection) {
        super(standingBlock, wallBlock, props, attachmentDirection);
    }
}