package com.torchcampfiredamage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * 守卫篝火 —— 直接继承原版 CampfireBlock。
 * <p>
 * 完整获得原版篝火所有特性：
 * - 点燃/熄灭（铲子、水、打火石）
 * - 含水（waterlogged）
 * - 烹饪（依赖 BE 的容器逻辑）
 * - 火焰粒子、发声、烟雾信号
 * - 原版模型与贴图（blockstate 直接引用 minecraft:block/campfire）
 * <p>
 * 唯一差异：额外挂载参数，潜行右键打开 GUI。
 */
public class WardCampfireBlock extends CampfireBlock {

    public WardCampfireBlock(Properties props) {
        super(true, 1, props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WardSourceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        // 复刻原版 CampfireBlock 的 ticker（客户端只做粒子，服务端做烹饪）
        if (level.isClientSide) {
            return state.getValue(CampfireBlock.LIT)
                    ? (lvl, pos, st, be) -> {
                        if (lvl instanceof net.minecraft.world.level.Level realLevel) {
                            CampfireBlock.makeParticles(realLevel, pos, st.getValue(CampfireBlock.SIGNAL_FIRE), true);
                        }
                    }
                    : null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof net.minecraft.world.level.block.entity.CampfireBlockEntity cbe) {
                net.minecraft.world.level.block.entity.CampfireBlockEntity.cookTick(lvl, pos, st, cbe);
            }
        };
    }

    /** 放置时：如果在水中，直接熄灭（原版逻辑由 updateShape 处理，这里补上放置瞬间） */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null) return null;
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (level.getFluidState(pos).getType() == Fluids.WATER) {
            state = state.setValue(CampfireBlock.LIT, false);
        }
        return state;
    }
}