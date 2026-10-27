package com.torchcampfiredamage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 墙上的守卫火把 —— 继承原版 WallTorchBlock 特性（附着方向、掉落），
 * 挂载 BlockEntity 保存伤害参数。
 */
public class WardWallTorchBlock extends WallTorchBlock implements EntityBlock {

    public WardWallTorchBlock(Properties props) {
        super(ParticleTypes.FLAME, props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WardSourceBlockEntity(pos, state);
    }
}