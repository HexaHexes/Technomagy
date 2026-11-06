package zone.moddev.mc.technomagy.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** No-drop temporary material whose scheduled expiration survives chunk saves (D69). */
final class TemporarySpellBlock extends Block {
    private final boolean invisible;

    /** Creates a physical shell block or an invisible light emitter. */
    TemporarySpellBlock(Properties properties, boolean invisible) {
        super(properties);
        this.invisible = invisible;
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.LEVEL, 15));
    }

    /** Stores light strength in block state so registration never reads server configuration. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.LEVEL);
    }

    /** Invisible emitters do not obstruct later targeting rays. */
    @Override
    protected VoxelShape getShape(
        BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return invisible ? Shapes.empty() : super.getShape(state, level, pos, context);
    }

    /** Expires only this scheduled block; replacing it cancels its effect through tick identity. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }

    /** Light emitters have no visible model; shell blocks use their block model. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return invisible ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }
}
