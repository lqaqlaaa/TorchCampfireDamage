package com.torchcampfiredamage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 守卫火把（墙上）。
 * 完全继承原版 WallTorchBlock（碰撞箱/朝向/附着检测/掉落），
 * 额外挂 BlockEntity 存参数，放置时按配置文件初始化。
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

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;

        if (level.getBlockEntity(pos) instanceof WardSourceBlockEntity ward) {
            ward.applyDefaults();
        }
    }
}