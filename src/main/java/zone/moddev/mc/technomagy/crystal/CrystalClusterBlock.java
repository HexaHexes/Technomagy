package zone.moddev.mc.technomagy.crystal;

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
 * A crystal growing out of a budding crystal block (D4). Vanilla uses a separate block for each
 * amethyst bud size; this is one block whose {@link #STAGE} runs from a small bud (0) to a full
 * cluster ({@value #MAX_STAGE}), with the matching size, light and sound at each stage.
 *
 * <p>
 * It points away from the block it grows on ({@link #FACING}), breaks when that support is removed
 * or when pushed by a piston, and can be waterlogged. The loot table drops crystals only at the
 * full stage; Silk Touch keeps the cluster at its stage.
 */
public class CrystalClusterBlock extends AmethystBlock implements SimpleWaterloggedBlock {
    /** The full-cluster stage; stages run from 0 (small bud) to this. */
    public static final int MAX_STAGE = 3;

    /** Growth stage: 0 small bud, 1 medium bud, 2 large bud, 3 full cluster. */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, MAX_STAGE);

    /** The direction the crystal points: away from the block it grows on. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /** Whether the cluster holds water, as it does when grown into a water source. */
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    // Per stage, matching the vanilla small, medium and large buds and the full cluster. Sizes are
    // in model pixels (1/16 block): height out from the support face, inset from each side.
    private static final int[] HEIGHT = {3, 4, 5, 7};
    private static final int[] INSET = {4, 3, 3, 3};
    private static final int[] LIGHT = {1, 2, 4, 5};
    private static final SoundType[] SOUND = {
        SoundType.SMALL_AMETHYST_BUD,
        SoundType.MEDIUM_AMETHYST_BUD,
        SoundType.LARGE_AMETHYST_BUD,
        SoundType.AMETHYST_CLUSTER
    };
    private static final VoxelShape[][] SHAPES = new VoxelShape[MAX_STAGE + 1][];

    static {
        for (int stage = 0; stage <= MAX_STAGE; stage++) {
            double h = HEIGHT[stage];
            double i = INSET[stage];
            double o = 16 - i;
            SHAPES[stage] = new VoxelShape[Direction.values().length];
            SHAPES[stage][Direction.UP.ordinal()] = Block.box(i, 0, i, o, h, o);
            SHAPES[stage][Direction.DOWN.ordinal()] = Block.box(i, 16 - h, i, o, 16, o);
            SHAPES[stage][Direction.NORTH.ordinal()] = Block.box(i, i, 16 - h, o, o, 16);
            SHAPES[stage][Direction.SOUTH.ordinal()] = Block.box(i, i, 0, o, o, h);
            SHAPES[stage][Direction.EAST.ordinal()] = Block.box(0, i, i, h, o, o);
            SHAPES[stage][Direction.WEST.ordinal()] = Block.box(16 - h, i, i, 16, o, o);
        }
    }

    /**
     * Creates the cluster block of one flavor. Its default state is a full cluster pointing up.
     *
     * @param properties
     *            the block properties MMDLib built from the module template; occlusion, piston
     *            reaction and per-stage light are set here
     */
    public CrystalClusterBlock(Properties properties) {
        super(
            properties
                .forceSolidOn()
                .noOcclusion()
                .pushReaction(PushReaction.DESTROY)
                .lightLevel(state -> LIGHT[state.getValue(STAGE)]));
        registerDefaultState(
            defaultBlockState()
                .setValue(STAGE, MAX_STAGE)
                .setValue(FACING, Direction.UP)
                .setValue(WATERLOGGED, false));
    }

    /**
     * A freshly started bud (stage 0).
     *
     * @param facing
     *            the direction it points: away from the face it grows on
     * @param waterlogged
     *            whether it starts in water
     * @return the bud's state
     */
    public BlockState bud(Direction facing, boolean waterlogged) {
        return defaultBlockState()
            .setValue(STAGE, 0)
            .setValue(FACING, facing)
            .setValue(WATERLOGGED, waterlogged);
    }

    /** The stage's precomputed outline, turned to the cluster's facing. */
    @Override
    protected VoxelShape getShape(
        BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(STAGE)][state.getValue(FACING).ordinal()];
    }

    /** The matching vanilla bud or cluster sound for the stage. */
    @Override
    public SoundType getSoundType(
        BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return SOUND[state.getValue(STAGE)];
    }

    /** A cluster needs a sturdy face on the block it grows from. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    /**
     * Keeps water flowing when waterlogged, and breaks the cluster when its support no longer holds
     * it.
     */
    @Override
    protected BlockState updateShape(
        BlockState state,
        Direction direction,
        BlockState neighborState,
        LevelAccessor level,
        BlockPos pos,
        BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /**
     * A placed cluster points away from the face it was placed against, and is waterlogged when
     * placed in water.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(
                WATERLOGGED,
                context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER)
            .setValue(FACING, context.getClickedFace());
    }

    /** Turns the facing with the structure being rotated. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Mirrors the facing with the structure being mirrored. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Water source when waterlogged, otherwise none. */
    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED)
            ? Fluids.WATER.getSource(false)
            : super.getFluidState(state);
    }

    /** Declares {@link #STAGE}, {@link #FACING} and {@link #WATERLOGGED}. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, FACING, WATERLOGGED);
    }
}
