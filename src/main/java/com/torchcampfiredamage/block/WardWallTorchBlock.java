package com.torchcampfiredamage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 墙上的守卫火把。
 * <p>
 * 继承原版 WallTorchBlock，并用与原版完全一致的「按朝向变化的碰撞箱」。
 */
public class WardWallTorchBlock extends WallTorchBlock implements EntityBlock {

    /** 原版 WallTorchBlock 的朝向属性 */
    public static final DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    /** 各个朝向的碰撞箱（贴近墙面一侧） */
    private static final VoxelShape NORTH_SHAPE = Shapes.box(5.5, 3.0, 11.0, 10.5, 16.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = Shapes.box(5.5, 3.0, 0.0, 10.5, 16.0, 5.0);
    private static final VoxelShape WEST_SHAPE  = Shapes.box(11.0, 3.0, 5.5, 16.0, 16.0, 10.5);
    private static final VoxelShape EAST_SHAPE  = Shapes.box(0.0, 3.0, 5.5, 5.0, 16.0, 10.5);

    public WardWallTorchBlock(Properties props) {
        super(ParticleTypes.FLAME, props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WardSourceBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction facing = state.getValue(FACING);
        return switch (facing) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> Shapes.empty();
        };
    }
}