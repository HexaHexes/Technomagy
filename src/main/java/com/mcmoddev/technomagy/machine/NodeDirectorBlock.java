package com.mcmoddev.technomagy.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A machine piece that directs a mana stream through the glass director at its tip. It stands on
 * a floor, or hangs upside down when placed against a ceiling.
 */
public class NodeDirectorBlock extends Block {
    public static final BooleanProperty HANGING = BlockStateProperties.HANGING;
    public static final int LIGHT = 7;

    private static final VoxelShape STANDING = Shapes.or(
            Block.box(0, 0, 0, 3, 3, 3),
            Block.box(13, 0, 0, 16, 3, 3),
            Block.box(0, 0, 13, 3, 3, 16),
            Block.box(13, 0, 13, 16, 3, 16),
            Block.box(0, 3, 0, 16, 4, 16),
            Block.box(6, 4, 6, 10, 8, 10),
            Block.box(7, 8, 7, 9, 16, 9));
    private static final VoxelShape HANGING_SHAPE = Shapes.or(
            Block.box(0, 13, 0, 3, 16, 3),
            Block.box(13, 13, 0, 16, 16, 3),
            Block.box(0, 13, 13, 3, 16, 16),
            Block.box(13, 13, 13, 16, 16, 16),
            Block.box(0, 12, 0, 16, 13, 16),
            Block.box(6, 8, 6, 10, 12, 10),
            Block.box(7, 0, 7, 9, 8, 9));

    public NodeDirectorBlock(Properties properties) {
        super(properties.noOcclusion().lightLevel(state -> LIGHT));
        registerDefaultState(defaultBlockState().setValue(HANGING, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HANGING) ? HANGING_SHAPE : STANDING;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HANGING, context.getClickedFace() == Direction.DOWN);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HANGING);
    }
}
