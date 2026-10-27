package com.torchcampfiredamage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 墙上的守卫火把。
 * <p>
 * 完全继承原版 WallTorchBlock —— 碰撞箱、朝向、附着检测、掉落全部使用原版实现，
 * 不覆写任何形状逻辑。唯一额外的是挂载 BlockEntity 存参数。
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