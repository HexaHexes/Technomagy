package com.mcmoddev.technomagy.crystal;

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

import java.util.function.Supplier;

/**
 * Grows crystal clusters of its own flavor on its faces, the way budding amethyst does. The
 * timing lives in {@link BuddingCrystalBlockEntity}; this block decides what grows where.
 */
public class BuddingCrystalBlock extends AmethystBlock implements EntityBlock {
    private static final Direction[] DIRECTIONS = Direction.values();

    private final BlockEntityBinding<BuddingCrystalBlockEntity> binding;
    private final Supplier<CrystalClusterBlock> cluster;
    private final Flavor flavor;

    public BuddingCrystalBlock(Properties properties, BlockEntityBinding<BuddingCrystalBlockEntity> binding,
                               Supplier<CrystalClusterBlock> cluster, Flavor flavor) {
        super(properties.pushReaction(PushReaction.DESTROY));
        this.binding = binding;
        this.cluster = cluster;
        this.flavor = flavor;
    }

    public Flavor flavor() {
        return flavor;
    }

    /** Starts a bud on a random face, or grows the bud already on that face by one stage. */
    public void grow(ServerLevel level, BlockPos pos, RandomSource random) {
        Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos target = pos.relative(direction);
        BlockState current = level.getBlockState(target);
        CrystalClusterBlock block = cluster.get();
        BlockState grown = null;
        if (current.isAir() || current.is(Blocks.WATER) && current.getFluidState().getAmount() == 8) {
            grown = block.bud(direction, current.getFluidState().getType() == Fluids.WATER);
        } else if (current.is(block) && current.getValue(CrystalClusterBlock.FACING) == direction
                && current.getValue(CrystalClusterBlock.STAGE) < CrystalClusterBlock.MAX_STAGE) {
            grown = current.setValue(CrystalClusterBlock.STAGE, current.getValue(CrystalClusterBlock.STAGE) + 1);
        }
        if (grown != null) {
            level.setBlockAndUpdate(target, grown);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return binding.prepared().create(binding.type().get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return binding.prepared().ticker(level, type, binding.type().get());
    }
}
