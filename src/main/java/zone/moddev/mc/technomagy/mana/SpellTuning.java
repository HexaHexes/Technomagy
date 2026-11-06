package zone.moddev.mc.technomagy.mana;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Required execution-tuning inputs for D66; no unspecified gameplay defaults are invented.
 *
 * <p>
 * The immutable map fixes the tuning snapshot. Functions must be pure, thread-safe, and stable for
 * that snapshot; their outputs are validated when preparing a spell. They calculate costs and
 * probabilities only, never consume resources or mutate the world.
 */
record SpellTuning<E extends Enum<E>>(
    Map<SpellCatalogue.Kind, Parameters<E>> spells,
    ToDoubleFunction<SpellEvaluation.Result<E>> overloadProbability,
    ToDoubleFunction<SpellEvaluation.Effect<E>> plantDeathProbability) {
    /** Rejects absent policy inputs and defensively copies spell parameters. */
    SpellTuning {
        spells = Map.copyOf(spells);
        Objects.requireNonNull(overloadProbability);
        Objects.requireNonNull(plantDeathProbability);
    }

    /**
     * Spell-specific reach and resource requirements, all required from the tuning owner.
     *
     * @param initialReach
     *            whole-block reach for the actual base input powers
     * @param basePowerRange
     *            updates reach for one later grouped base-power addition
     * @param manaCost
     *            complete per-flavor cost for one activation
     */
    record Parameters<E extends Enum<E>>(
        ToIntFunction<Map<E, Integer>> initialReach,
        IntBinaryOperator basePowerRange,
        Function<SpellEvaluation.Effect<E>, Map<E, Integer>> manaCost) {
        /** Rejects absent tuning functions. */
        Parameters {
            Objects.requireNonNull(initialReach);
            Objects.requireNonNull(basePowerRange);
            Objects.requireNonNull(manaCost);
        }
    }
}
