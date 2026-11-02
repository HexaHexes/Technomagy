package com.mcmoddev.technomagy.crystal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Drives a budding crystal's growth. Each tick it tries to grow with probability
 * {@code randomTickSpeed / growthInterval}; the default interval of 20480 (4096 * 5) matches
 * budding amethyst, which gets {@code randomTickSpeed} random ticks per 4096 blocks and grows on
 * one in five of them. Flavors that don't occur naturally take {@link Flavor#unpromptedSlowdown()}
 * times longer.
 */
public class BuddingCrystalBlockEntity extends BlockEntity {
    private final int growthInterval;

    public BuddingCrystalBlockEntity(BlockEntityType<BuddingCrystalBlockEntity> type, BlockPos pos, BlockState state,
                                     int growthInterval) {
        super(type, pos, state);
        this.growthInterval = growthInterval;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BuddingCrystalBlockEntity entity) {
        if (!(state.getBlock() instanceof BuddingCrystalBlock budding)) {
            return;
        }
        int speed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        int interval = entity.growthInterval * budding.flavor().unpromptedSlowdown();
        if (level.random.nextInt(interval) < speed) {
            budding.grow((ServerLevel) level, pos, level.random);
        }
    }
}
