package com.torchcampfiredamage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 守卫篝火 —— 完整继承原版篝火特性（点火/熄灭/烹饪/水中熄灭/伤害），
 * 额外挂载 BlockEntity 保存独立的伤害参数。
 * <p>
 * 注意：原版 CampfireBlock 的烹饪依赖 CampfireBlockEntity，
 * 这里我们用自定义 BE（继承自原版）来同时承载参数与烹饪。
 */
public class WardCampfireBlock extends CampfireBlock {

    public WardCampfireBlock(Properties props) {
        // spawnParticles = true（火焰粒子），fireDamage = 1（原版篝火灼烧伤害）
        super(true, 1, props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WardSourceBlockEntity(ModBlocks.WARD_SOURCE_BE.get(), pos, state);
    }
}