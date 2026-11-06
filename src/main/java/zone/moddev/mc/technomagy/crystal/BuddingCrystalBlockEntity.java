package zone.moddev.mc.technomagy.crystal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Drives a budding crystal's growth (D7, D8).
 *
 * <p>
 * Each server tick it tries to grow with probability {@code randomTickSpeed / interval}, where
 * {@code interval} is the module's {@code growth_interval} times the flavor's
 * {@link Flavor#unpromptedSlowdown() slowdown}.
 *
 * <p>
 * <b>Why 20480 matches vanilla.</b> Budding amethyst grows from random ticks: a chunk section of
 * 4096 blocks gets {@code randomTickSpeed} random ticks per game tick, so one block is picked with
 * probability {@code randomTickSpeed / 4096}, and then grows with probability 1/5. That is {@code
 * randomTickSpeed / 20480} per tick, so the default {@code growth_interval} of 20480 gives Light
 * and Dark exactly budding amethyst's rate. Using the gamerule keeps growth under the same control
 * as vanilla's: {@code randomTickSpeed 0} stops it.
 *
 * <p>
 * Ticking with a block entity rather than random ticks keeps the interval data-driven (set in
 * {@code mmdlib/block/budding_crystal.json}) and leaves room for mana prompting (D9), which will
 * need per-block state.
 */
public class BuddingCrystalBlockEntity extends BlockEntity {
    /** Mean ticks between growths at {@code randomTickSpeed} 1, before the flavor's slowdown. */
    private final int growthInterval;

    /**
     * Creates the block entity for one budding crystal.
     *
     * @param type
     *            the block entity type MMDLib registered for the budding crystal module
     * @param pos
     *            the block's position
     * @param state
     *            the block's state
     * @param growthInterval
     *            the module's {@code growth_interval}; at least 1
     */
    public BuddingCrystalBlockEntity(
        BlockEntityType<BuddingCrystalBlockEntity> type,
        BlockPos pos,
        BlockState state,
        int growthInterval) {
        super(type, pos, state);
        this.growthInterval = growthInterval;
    }

    /**
     * The server ticker: rolls for growth and, on success, has the budding block grow a bud or
     * advance one. Runs on the server thread only; clients get no ticker.
     *
     * @param level
     *            the level, always a {@link ServerLevel}
     * @param pos
     *            the budding block's position
     * @param state
     *            the budding block's state
     * @param entity
     *            this block entity
     * @throws IllegalStateException
     *             if the block is not a budding crystal. That can't happen in normal play: only
     *             {@link BuddingCrystalBlock#getTicker} hands out this ticker, and the level only
     *             ticks a block entity while its type is valid for the block at its position.
     *             Reaching it means something placed this block entity on another block, which is a
     *             bug to surface, not to skip silently.
     */
    public static void serverTick(
        Level level, BlockPos pos, BlockState state, BuddingCrystalBlockEntity entity) {
        if (!(state.getBlock() instanceof BuddingCrystalBlock budding)) {
            throw new IllegalStateException(
                "Budding crystal block entity at "
                    + pos
                    + " is ticking on "
                    + state
                    + ", which is not a budding crystal");
        }
        int speed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        int interval = entity.growthInterval * budding.flavor().unpromptedSlowdown();
        if (level.random.nextInt(interval) < speed) {
            budding.grow((ServerLevel) level, pos, level.random);
        }
    }
}
