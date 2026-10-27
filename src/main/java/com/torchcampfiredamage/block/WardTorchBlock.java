package com.torchcampfiredamage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 守卫火把 —— 完整继承原版火把特性（照明、粒子、放置/掉落逻辑），
 * 额外挂载 BlockEntity 保存伤害参数。
 */
public class WardTorchBlock extends TorchBlock implements EntityBlock {

    public WardTorchBlock(Properties props) {
        super(ParticleTypes.FLAME, props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WardSourceBlockEntity(ModBlocks.WARD_SOURCE_BE.get(), pos, state);
    }
}