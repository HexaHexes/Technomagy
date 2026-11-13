package zone.moddev.mc.technomagy.machine;

import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

/**
 * Splits a mana stream into its parts, or combines streams into one, depending on its {@link Mode}
 * (D20, D26). The glass bar lies along its {@link #FACING}; in combine mode a copper collar clamps
 * the middle of the bar.
 *
 * <p>
 * So far it is only the block: it has no block entity and moves no mana. Splitting and combining
 * will use {@link zone.moddev.mc.technomagy.mana.Mana}'s lossless operations when links exist.
 *
 * <p>
 * Using it with an empty hand switches modes. That is a stand-in until the wand (D23) exists.
 */
public class PrismBlock extends Block {
    /** The direction the glass bar points: away from the player who placed it. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Whether the prism splits or combines. */
    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);

    // The model is symmetric end to end, so north/south and east/west share a shape.
    private static final VoxelShape BASE = Shapes.or(
        Block.box(0, 0, 0, 3, 3, 3),
        Block.box(13, 0, 0, 16, 3, 3),
        Block.box(0, 0, 13, 3, 3, 16),
        Block.box(13, 0, 13, 16, 3, 16),
        Block.box(0, 3, 0, 16, 4, 16));
    private static final VoxelShape ALONG_Z = Shapes.or(
        BASE,
        Block.box(6, 4, 3, 10, 7, 5),
        Block.box(6, 4, 11, 10, 7, 13),
        Block.box(4, 7, 2, 12, 15, 14));
    private static final VoxelShape ALONG_X = Shapes.or(
        BASE,
        Block.box(3, 4, 6, 5, 7, 10),
        Block.box(11, 4, 6, 13, 7, 10),
        Block.box(2, 7, 4, 14, 15, 12));

    /**
     * Creates the prism block. Its default state is split mode, facing north.
     *
     * @param properties
     *            block properties; occlusion is turned off here
     */
    public PrismBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(
            defaultBlockState().setValue(FACING, Direction.NORTH).setValue(MODE, Mode.SPLIT));
    }

    /** The outline for the bar's axis. */
    @Override
    protected VoxelShape getShape(
        BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? ALONG_Z : ALONG_X;
    }

    @Nullable
    /** Faces away from the placing player, in split mode. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Switches between split and combine, with a click sound and an action bar message naming the
     * new mode. The stand-in for the wand (D23).
     */
    @Override
    protected InteractionResult useWithoutItem(
        BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            Mode mode = state.getValue(MODE).other();
            level.setBlockAndUpdate(pos, state.setValue(MODE, mode));
            level.playSound(
                null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 1.0f, 1.0f);
            player.displayClientMessage(
                Component.translatable(
                    "block.technomagy.prism.mode." + mode.getSerializedName()),
                true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Turns the facing with the structure being rotated. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Mirrors the facing with the structure being mirrored. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    /** Declares {@link #FACING} and {@link #MODE}. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE);
    }

    /** What a prism does with the mana passing through it (D20, D26). */
    public enum Mode implements StringRepresentable {
        /** Separates a stream into its parts. */
        SPLIT,
        /** Merges incoming streams into one, keeping their proportions. */
        COMBINE;

        /**
         * The mode a switch leads to.
         *
         * @return the other mode
         */
        public Mode other() {
            return this == SPLIT ? COMBINE : SPLIT;
        }

        /**
         * The mode's name in block states and messages.
         *
         * @return {@code split} or {@code combine}
         */
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
