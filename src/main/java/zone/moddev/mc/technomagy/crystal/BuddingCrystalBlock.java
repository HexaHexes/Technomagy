package zone.moddev.mc.technomagy.crystal;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;

import org.jetbrains.annotations.Nullable;

import zone.moddev.mc.lib.api.BlockEntityBinding;

/**
 * Grows crystal clusters of its own flavor on its faces, the way budding amethyst does (D4, D7,
 * D8). The timing lives in {@link BuddingCrystalBlockEntity}; this block decides what grows where.
 *
 * <p>
 * Like budding amethyst it breaks when pushed by a piston and drops nothing, even with Silk Touch
 * (its loot table is empty).
 */
public class BuddingCrystalBlock extends AmethystBlock implements EntityBlock {
    private static final Direction[] DIRECTIONS = Direction.values();

    /** MMDLib's handle on this block's block entity type, factory and ticker. */
    private final BlockEntityBinding<BuddingCrystalBlockEntity> binding;

    /**
     * The cluster block this one grows. Looked up lazily because the cluster is registered after
     * this block (see {@link CrystalContent}).
     */
    private final Supplier<CrystalClusterBlock> cluster;

    private final Flavor flavor;

    /**
     * Creates the budding crystal of one flavor.
     *
     * @param properties
     *            the block properties MMDLib built from the module template; piston push reaction
     *            is set to destroy here
     * @param binding
     *            the block entity binding MMDLib created from the module's {@code block_entity}
     *            part
     * @param cluster
     *            supplies the same flavor's cluster block once it is registered
     * @param flavor
     *            this crystal's flavor
     */
    public BuddingCrystalBlock(
        Properties properties,
        BlockEntityBinding<BuddingCrystalBlockEntity> binding,
        Supplier<CrystalClusterBlock> cluster,
        Flavor flavor) {
        super(properties.pushReaction(PushReaction.DESTROY));
        this.binding = binding;
        this.cluster = cluster;
        this.flavor = flavor;
    }

    /**
     * This budding crystal's flavor, which sets its growth rate (D7, D8).
     *
     * @return the flavor
     */
    public Flavor flavor() {
        return flavor;
    }

    /**
     * Picks a random face and grows there, as budding amethyst does: in air or a water source it
     * starts a new bud (waterlogged in water); on this flavor's own cluster pointing away from this
     * face it advances the cluster one stage. Anything else on that face blocks growth for this
     * attempt.
     *
     * @param level
     *            the server level
     * @param pos
     *            this block's position
     * @param random
     *            the source for the face choice
     */
    public void grow(ServerLevel level, BlockPos pos, RandomSource random) {
        Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos target = pos.relative(direction);
        BlockState current = level.getBlockState(target);
        CrystalClusterBlock block = cluster.get();
        BlockState grown = null;
        if (current.isAir()
            || current.is(Blocks.WATER) && current.getFluidState().getAmount() == 8) {
            grown = block.bud(direction, current.getFluidState().getType() == Fluids.WATER);
        } else if (current.is(block)
            && current.getValue(CrystalClusterBlock.FACING) == direction
            && current.getValue(CrystalClusterBlock.STAGE) < CrystalClusterBlock.MAX_STAGE) {
            grown = current.setValue(
                CrystalClusterBlock.STAGE,
                current.getValue(CrystalClusterBlock.STAGE) + 1);
        }
        if (grown != null) {
            level.setBlockAndUpdate(target, grown);
        }
    }

    /**
     * Creates the growth-driving block entity through MMDLib's binding, which supplies the module's
     * {@code growth_interval}.
     *
     * @param pos
     *            the block's position
     * @param state
     *            the block's state
     * @return a new {@link BuddingCrystalBlockEntity}
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return binding.prepared().create(binding.type().get(), pos, state);
    }

    /**
     * The block entity ticker, through MMDLib's binding:
     * {@link BuddingCrystalBlockEntity#serverTick} on the server, none on the client.
     *
     * @param level
     *            the level asking
     * @param state
     *            the block's state
     * @param type
     *            the block entity type being ticked
     * @param <T>
     *            the block entity class
     * @return the ticker, or null when none applies
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level, BlockState state, BlockEntityType<T> type) {
        return binding.prepared().ticker(level, type, binding.type().get());
    }
}
