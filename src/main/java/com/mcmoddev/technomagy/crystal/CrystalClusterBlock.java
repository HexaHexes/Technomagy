package com.mcmoddev.technomagy.crystal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A crystal growing out of a budding crystal block. Vanilla uses a separate block for each
 * amethyst bud size; this is one block whose {@link #STAGE} runs from a small bud (0) to a full
 * cluster (3), with the matching size, light and sound at each stage.
 */
public class CrystalClusterBlock extends AmethystBlock implements SimpleWaterloggedBlock {
    public static final int MAX_STAGE = 3;
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, MAX_STAGE);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    // Per stage, matching the vanilla small, medium and large buds and the full cluster.
    private static final int[] HEIGHT = {3, 4, 5, 7};
    private static final int[] INSET = {4, 3, 3, 3};
    private static final int[] LIGHT = {1, 2, 4, 5};
    private static final SoundType[] SOUND = {
            SoundType.SMALL_AMETHYST_BUD, SoundType.MEDIUM_AMETHYST_BUD, SoundType.LARGE_AMETHYST_BUD, SoundType.AMETHYST_CLUSTER
    };
    private static final VoxelShape[][] SHAPES = new VoxelShape[MAX_STAGE + 1][];

    static {
        for (int stage = 0; stage <= MAX_STAGE; stage++) {
            double h = HEIGHT[stage], i = INSET[stage], o = 16 - i;
            SHAPES[stage] = new VoxelShape[Direction.values().length];
            SHAPES[stage][Direction.UP.ordinal()] = Block.box(i, 0, i, o, h, o);
            SHAPES[stage][Direction.DOWN.ordinal()] = Block.box(i, 16 - h, i, o, 16, o);
            SHAPES[stage][Direction.NORTH.ordinal()] = Block.box(i, i, 16 - h, o, o, 16);
            SHAPES[stage][Direction.SOUTH.ordinal()] = Block.box(i, i, 0, o, o, h);
            SHAPES[stage][Direction.EAST.ordinal()] = Block.box(0, i, i, h, o, o);
            SHAPES[stage][Direction.WEST.ordinal()] = Block.box(16 - h, i, i, 16, o, o);
        }
    }

    public CrystalClusterBlock(Properties properties) {
        super(properties
                .forceSolidOn()
                .noOcclusion()
                .pushReaction(PushReaction.DESTROY)
                .lightLevel(state -> LIGHT[state.getValue(STAGE)]));
        registerDefaultState(defaultBlockState()
                .setValue(STAGE, MAX_STAGE)
                .setValue(FACING, Direction.UP)
                .setValue(WATERLOGGED, false));
    }

    /** A freshly started bud, pointing away from the face it grows on. */
    public BlockState bud(Direction facing, boolean waterlogged) {
        return defaultBlockState().setValue(STAGE, 0).setValue(FACING, facing).setValue(WATERLOGGED, waterlogged);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(STAGE)][state.getValue(FACING).ordinal()];
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return SOUND[state.getValue(STAGE)];
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER)
                .setValue(FACING, context.getClickedFace());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, FACING, WATERLOGGED);
    }
}
