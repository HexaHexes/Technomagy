package zone.moddev.mc.technomagy.mana;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Composes pair interpretation, chemistry, and Growth's special handler into one pure evaluation.
 *
 * <p>
 * This internal entry point produces effect descriptions rather than changing blocks. It keeps the
 * original tree for splitting and reports chemical releases separately. Raw conversion runs before
 * spell interpretation and cancels the entire execution sequence (D62). Growth replaces Mend's
 * action after Earth is supplied, without increasing spell count (D63).
 */
final class SpellEvaluation<E extends Enum<E>> {
    private final SpellElements<E> elements;
    private final SpellHeuristics calculations;
    private final SpellInterpreter<E, SpellCatalogue.Kind, SpellCatalogue.Modifier> interpreter;
    private final StreamChemistry<E> chemistry;

    /** Binds an explicit catalogue, flavor roles, and numerical tuning without global state. */
    SpellEvaluation(
        SpellElements<E> elements,
        SpellHeuristics calculations,
        SpellInterpreter<E, SpellCatalogue.Kind, SpellCatalogue.Modifier> interpreter) {
        this.elements = Objects.requireNonNull(elements);
        this.calculations = Objects.requireNonNull(calculations);
        this.interpreter = Objects.requireNonNull(interpreter);
        this.chemistry = new StreamChemistry<>(elements, calculations);
    }

    /**
     * Evaluates one directed stream, retaining effect order and grouped modifier provenance.
     *
     * @param stream
     *            original immutable construction tree
     * @return chemical state and ordered effect plans; no plans survive Raw conversion
     * @throws IllegalStateException
     *             if individual modification occurs after spells are joined
     * @throws IllegalArgumentException
     *             for unsupported chemistry or unknown pair definitions
     * @throws ArithmeticException
     *             if stored units overflow
     */
    Result<E> evaluate(SpellStream<E> stream) {
        StreamChemistry.Result<E> chemical = chemistry.evaluate(stream);
        if (chemical.spellsCancelled()
            || (stream.isElement() && stream.element() == elements.raw())) {
            return new Result<>(chemical, List.of());
        }
        var effects = new ArrayList<Effect<E>>();
        int lightRemaining = chemical.active().getOrDefault(elements.light(), 0);
        int darkRemaining = chemical.active().getOrDefault(elements.dark(), 0);
        for (var plan : interpreter.interpret(stream)) {
            var source = plan.construction();
            var localTree = SpellStream.merge(source.first(), source.second());
            for (var addition : source.additions()) {
                localTree = SpellStream.merge(localTree, addition);
            }
            var local = chemistry.evaluate(localTree);
            var powers = new EnumMap<E, Integer>(elements.type());
            powers.putAll(local.active());
            int light = Math.min(lightRemaining, powers.getOrDefault(elements.light(), 0));
            int dark = Math.min(darkRemaining, powers.getOrDefault(elements.dark(), 0));
            lightRemaining -= light;
            darkRemaining -= dark;
            powers.put(elements.light(), light);
            powers.put(elements.dark(), dark);
            boolean cancelled = (source.isBase(elements.light()) && light == 0)
                || (source.isBase(elements.dark()) && dark == 0);
            Optional<Growth> growth = Optional.empty();
            SpellCatalogue.Kind kind = plan.identity();
            if (!cancelled
                && kind == SpellCatalogue.Kind.MEND
                && powers.getOrDefault(elements.earth(), 0) > 0) {
                kind = SpellCatalogue.Kind.GROWTH;
                int water = powers.get(elements.water());
                int earth = powers.get(elements.earth());
                growth = Optional.of(
                    new Growth(
                        calculations.plantMultiplier(water, earth),
                        calculations.balancedGrowth(water, earth)));
            }
            effects.add(
                new Effect<>(
                    kind, plan, powers, activeAdditions(plan, powers), growth, cancelled));
        }
        return new Result<>(chemical, effects);
    }

    /** Allocates surviving quantities to original addition events after reserving base inputs. */
    private List<SpellInterpreter.Addition<E, SpellCatalogue.Modifier>> activeAdditions(
        SpellInterpreter.Plan<E, SpellCatalogue.Kind, SpellCatalogue.Modifier> plan,
        Map<E, Integer> powers) {
        var remaining = new EnumMap<E, Integer>(elements.type());
        remaining.putAll(powers);
        for (var input : List.of(plan.construction().first(), plan.construction().second())) {
            int available = remaining.getOrDefault(input.element(), 0);
            remaining.put(input.element(), Math.max(0, available - input.units()));
        }
        var active = new ArrayList<SpellInterpreter.Addition<E, SpellCatalogue.Modifier>>();
        for (var addition : plan.additions()) {
            int available = Math.min(addition.units(),
                remaining.getOrDefault(addition.element(), 0));
            if (available > 0) {
                active.add(
                    new SpellInterpreter.Addition<>(
                        addition.element(),
                        available,
                        addition.basePower(),
                        addition.action()));
                remaining.put(addition.element(), remaining.get(addition.element()) - available);
            }
        }
        return List.copyOf(active);
    }

    /**
     * Plant-growth properties; an imbalanced stream still works but requires a mortality policy.
     */
    record Growth(double tickMultiplier, boolean balanced) {
    }

    /** One executable action identity and its original grouped modifier plan. */
    record Effect<E extends Enum<E>>(
        SpellCatalogue.Kind kind,
        SpellInterpreter.Plan<E, SpellCatalogue.Kind, SpellCatalogue.Modifier> source,
        Map<E, Integer> effectivePowers,
        List<SpellInterpreter.Addition<E, SpellCatalogue.Modifier>> activeAdditions,
        Optional<Growth> growth,
        boolean cancelled) {
        /**
         * Rejects absent components; an absent growth property is represented by Optional.empty.
         */
        Effect {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(source);
            Objects.requireNonNull(growth);
            effectivePowers = Map.copyOf(effectivePowers);
            activeAdditions = List.copyOf(activeAdditions);
        }
    }

    /** Chemical releases and ordered effects; returned collections are immutable. */
    record Result<E extends Enum<E>>(StreamChemistry.Result<E> chemistry, List<Effect<E>> effects) {
        /** Defensively copies effects and rejects a missing chemistry result. */
        Result {
            Objects.requireNonNull(chemistry);
            effects = List.copyOf(effects);
        }

        /** Reports how many spells exceed the two-spell safe count, not a guessed probability. */
        int excessSpellCount() {
            return Math.max(
                0, (int) effects.stream().filter(effect -> !effect.cancelled()).count() - 2);
        }
    }
}
