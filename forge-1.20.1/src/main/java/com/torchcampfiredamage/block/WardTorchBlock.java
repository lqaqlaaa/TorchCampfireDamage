package com.torchcampfiredamage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 守卫火把 —— 独立放置的方块（可穿透）。
 */
public class WardTorchBlock extends Block implements EntityBlock {

    /** 瞄准框：完整 1×1×1（只用于射线检测） */
    private static final VoxelShape AIM_SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    public WardTorchBlock(Properties props) {
        super(props);
    }

    /** 瞄准/选取框 */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return AIM_SHAPE;
    }

    /** 不遮挡视线 */
    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    /** 不阻挡光照传播 */
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /** 不吸收光照 */
    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WardSourceBlockEntity(pos, state);
    }

    /** 放置时用配置默认值初始化 */
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