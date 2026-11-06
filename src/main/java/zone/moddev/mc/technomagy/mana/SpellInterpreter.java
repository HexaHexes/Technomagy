package zone.moddev.mc.technomagy.mana;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntBinaryOperator;

/**
 * Resolves structural spell streams into typed, immutable effect plans (D54-D59).
 *
 * <p>
 * The owning catalogue supplies spell identities and modifier actions. No missing mapping is
 * guessed and no gameplay defaults are embedded here. Plans preserve grouped events, including
 * repeated non-base elements, so later execution can apply construction-sensitive range rules. This
 * thread-safe interpreter owns defensive copies of its catalogue and never accesses a world.
 *
 * @param <E>
 *            element enum
 * @param <S>
 *            spell identity enum
 * @param <A>
 *            modifier action enum
 */
final class SpellInterpreter<E extends Enum<E>, S extends Enum<S>, A extends Enum<A>> {
    private final Class<E> elements;
    private final Map<Pair<E>, S> pairs;
    private final Map<E, A> modifiers;
    private final Set<Pair<E>> utilities;

    /**
     * Creates an interpreter from explicit catalogue entries.
     *
     * @param elements
     *            element enum class, used for typed power accounting
     * @param pairs
     *            commutative base pairs mapped to spell identities
     * @param modifiers
     *            non-base elements mapped to their modifier action
     * @throws NullPointerException
     *             if any argument, key, or value is null
     */
    SpellInterpreter(Class<E> elements, Map<Pair<E>, S> pairs, Map<E, A> modifiers) {
        this(elements, pairs, modifiers, Set.of());
    }

    /** Creates a catalogue with explicit non-spell utility pairs, such as life and death. */
    SpellInterpreter(
        Class<E> elements, Map<Pair<E>, S> pairs, Map<E, A> modifiers, Set<Pair<E>> utilities) {
        this.elements = Objects.requireNonNull(elements);
        this.pairs = Map.copyOf(pairs);
        this.modifiers = Map.copyOf(modifiers);
        this.utilities = Set.copyOf(utilities);
        for (Pair<E> utility : utilities) {
            if (pairs.containsKey(utility)) {
                throw new IllegalArgumentException(
                    "Pair cannot be both spell and utility: " + utility);
            }
        }
    }

    /** Returns immutable pair definitions for projecting a tunable catalogue snapshot. */
    Map<Pair<E>, S> pairs() {
        return pairs;
    }

    /** Returns immutable modifier definitions for projecting a tunable catalogue snapshot. */
    Map<E, A> modifiers() {
        return modifiers;
    }

    /**
     * Resolves spells in execution order without performing chemistry or world effects.
     *
     * @param stream
     *            original construction tree
     * @return immutable ordered plans with per-element power and grouped modifier events
     * @throws IllegalArgumentException
     *             if a pair or modifier has no declared meaning
     * @throws ArithmeticException
     *             if per-element power exceeds signed-int storage
     * @throws IllegalStateException
     *             if the stream contains an undefined modifier target
     */
    List<Plan<E, S, A>> interpret(SpellStream<E> stream) {
        Objects.requireNonNull(stream);
        List<SpellStream.Spell<E>> spells = stream.interpret(
            new SpellStream.PairRule<>() {
                @Override
                public void validate(E first, E second) {
                    if (!pairs.containsKey(new Pair<>(first, second))) {
                        throw new IllegalArgumentException(
                            "No spell for pair " + first + " + " + second);
                    }
                }

                @Override
                public boolean isUtility(E first, E second) {
                    return utilities.contains(new Pair<>(first, second));
                }
            });
        var result = new ArrayList<Plan<E, S, A>>();
        for (SpellStream.Spell<E> spell : spells) {
            S identity = pairs.get(new Pair<>(spell.first().element(), spell.second().element()));
            var powers = new EnumMap<E, Integer>(elements);
            add(powers, spell.first());
            add(powers, spell.second());
            var events = new ArrayList<Addition<E, A>>();
            for (SpellStream<E> input : spell.additions()) {
                add(powers, input);
                if (spell.isBase(input.element())) {
                    events.add(new Addition<>(input.element(), input.units(), true, null));
                } else {
                    A action = modifiers.get(input.element());
                    if (action == null) {
                        throw new IllegalArgumentException(
                            "No modifier action for " + input.element());
                    }
                    events.add(new Addition<>(input.element(), input.units(), false, action));
                }
            }
            result.add(new Plan<>(identity, powers, events, spell));
        }
        return List.copyOf(result);
    }

    /**
     * Evaluates range events for one resolved spell without flattening grouped additions (D59).
     *
     * <p>
     * Base-power reach scaling is spell-specific and supplied explicitly. Non-range modifiers leave
     * reach unchanged. Earth and Air apply in recorded addition order (D65).
     *
     * @param plan
     *            interpreted spell plan
     * @param initialReach
     *            spell-specific starting reach in blocks
     * @param rangeIncrease
     *            action identifying range additions
     * @param rangeDecrease
     *            action identifying range reductions
     * @param calculations
     *            explicitly tuned numerical rules
     * @param basePowerRange
     *            function from current reach and added base units to updated reach
     * @return whole-block final reach
     * @throws IllegalArgumentException
     *             if input reach or supplied base scaling violates the floor
     */
    int range(
        Plan<E, S, A> plan,
        int initialReach,
        A rangeIncrease,
        A rangeDecrease,
        SpellHeuristics calculations,
        IntBinaryOperator basePowerRange) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(rangeIncrease);
        Objects.requireNonNull(rangeDecrease);
        Objects.requireNonNull(calculations);
        Objects.requireNonNull(basePowerRange);
        if (rangeIncrease == rangeDecrease) {
            throw new IllegalArgumentException("Range action identities must be distinct");
        }
        calculations.validateReach(initialReach);
        int reach = initialReach;
        for (Addition<E, A> addition : plan.additions()) {
            if (addition.basePower()) {
                reach = basePowerRange.applyAsInt(reach, addition.units());
                calculations.validateReach(reach);
            } else if (addition.action() == rangeIncrease) {
                reach = calculations.addEarth(reach, addition.units());
            } else if (addition.action() == rangeDecrease) {
                reach = calculations.addAir(reach, addition.units());
            }
        }
        return reach;
    }

    /** Accumulates exact per-element units without silent integer overflow. */
    private void add(EnumMap<E, Integer> powers, SpellStream<E> input) {
        powers.merge(input.element(), input.units(), Math::addExact);
    }

    /** Canonical, unordered base pair; same-element inputs are reinforcement, not new spells. */
    record Pair<E extends Enum<E>>(E first, E second) {
        /** Rejects null/same-element pairs and canonicalizes enum order (D57). */
        Pair {
            Objects.requireNonNull(first);
            Objects.requireNonNull(second);
            if (first == second) {
                throw new IllegalArgumentException("Pure reinforcement is not a spell pair");
            }
            if (first.compareTo(second) > 0) {
                E swap = first;
                first = second;
                second = swap;
            }
        }
    }

    /** One grouped construction event; base-power events carry no separate modifier action. */
    record Addition<E, A>(E element, int units, boolean basePower, A action) {
        /** Enforces the distinction between base reinforcement and non-base action. */
        Addition {
            Objects.requireNonNull(element);
            if (units <= 0 || (basePower ? action != null : action == null)) {
                throw new IllegalArgumentException("Invalid spell addition");
            }
        }
    }

    /** Resolved identity, whole-unit flavor powers, and ordered grouped additions. */
    record Plan<E extends Enum<E>, S, A>(
        S identity,
        Map<E, Integer> powers,
        List<Addition<E, A>> additions,
        SpellStream.Spell<E> construction) {
        /** Defensively copies collections so interpretation results are safe to share. */
        Plan {
            Objects.requireNonNull(identity);
            Objects.requireNonNull(construction);
            powers = Map.copyOf(powers);
            additions = List.copyOf(additions);
        }
    }
}
