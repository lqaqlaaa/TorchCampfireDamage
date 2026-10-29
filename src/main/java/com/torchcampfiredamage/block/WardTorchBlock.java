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
 * 守卫火把 —— 独立放置的方块。
 * <p>
 * 特性：
 * <ul>
 *   <li>不再依附于其他方块，可放在任意位置</li>
 *   <li>无碰撞（生物可以穿过）</li>
 *   <li>选取/碰撞箱为完整 1×1×1 方块范围（方便点击和瞄准）</li>
 *   <li>自带光源</li>
 *   <li>外观完全由 BlockEntityRenderer 绘制（中心物品 + 星环）</li>
 * </ul>
 */
public class WardTorchBlock extends Block implements EntityBlock {

    /** 1×1×1 完整方块形状 */
    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    public WardTorchBlock(Properties props) {
        super(props);
    }

    /** 碰撞箱 / 选取箱：完整 1×1×1 */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** 光线穿透：不遮挡视线（像玻璃那样不阻挡光，但可见） */
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