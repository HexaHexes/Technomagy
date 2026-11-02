package com.mcmoddev.technomagy.machine;

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

import java.util.Locale;

/**
 * Splits Raw mana into its flavors, or combines flavors into one stream, depending on its
 * {@link Mode}. The glass bar lies along its {@link #FACING}; in combine mode a copper collar
 * clamps the middle of the bar. Using it with an empty hand switches modes.
 */
public class PrismBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);

    // The model is symmetric end to end, so north/south and east/west share a shape.
    private static final VoxelShape BASE = Shapes.or(
            Block.box(0, 0, 0, 3, 3, 3),
            Block.box(13, 0, 0, 16, 3, 3),
            Block.box(0, 0, 13, 3, 3, 16),
            Block.box(13, 0, 13, 16, 3, 16),
            Block.box(0, 3, 0, 16, 4, 16));
    private static final VoxelShape ALONG_Z = Shapes.or(BASE,
            Block.box(6, 4, 3, 10, 7, 5),
            Block.box(6, 4, 11, 10, 7, 13),
            Block.box(4, 7, 2, 12, 15, 14));
    private static final VoxelShape ALONG_X = Shapes.or(BASE,
            Block.box(3, 4, 6, 5, 7, 10),
            Block.box(11, 4, 6, 13, 7, 10),
            Block.box(2, 7, 4, 14, 15, 12));

    public PrismBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(MODE, Mode.SPLIT));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? ALONG_Z : ALONG_X;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide) {
            Mode mode = state.getValue(MODE).other();
            level.setBlockAndUpdate(pos, state.setValue(MODE, mode));
            level.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 1.0f, 1.0f);
            player.displayClientMessage(Component.translatable("block.technomagy.prism.mode." + mode.getSerializedName()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE);
    }

    public enum Mode implements StringRepresentable {
        SPLIT, COMBINE;

        public Mode other() {
            return this == SPLIT ? COMBINE : SPLIT;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
