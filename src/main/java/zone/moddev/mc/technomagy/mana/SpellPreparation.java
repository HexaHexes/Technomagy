package zone.moddev.mc.technomagy.mana;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Produces fully priced, range-resolved plans without applying effects or spending mana (D66).
 *
 * <p>
 * Every uncancelled effect requires explicit tuning. Invalid or missing tuning fails before any
 * result is published. Output costs remain individual and ordered; they are not silently combined
 * into a reservation or transaction policy. Executors must validate and commit payment using their
 * own agreed activation contract.
 */
final class SpellPreparation<E extends Enum<E>> {
    private final SpellElements<E> elements;
    private final SpellHeuristics calculations;
    private final SpellInterpreter<E, SpellCatalogue.Kind, SpellCatalogue.Modifier> interpreter;

    /** Binds the same flavor roles and numeric calculator used for evaluation. */
    SpellPreparation(
        SpellElements<E> elements,
        SpellHeuristics calculations,
        SpellInterpreter<E, SpellCatalogue.Kind, SpellCatalogue.Modifier> interpreter) {
        this.elements = Objects.requireNonNull(elements);
        this.calculations = Objects.requireNonNull(calculations);
        this.interpreter = Objects.requireNonNull(interpreter);
    }

    /** Resolves required execution inputs; cancelled spells never acquire costs or ranges. */
    Result<E> prepare(SpellEvaluation.Result<E> evaluation, SpellTuning<E> tuning) {
        Objects.requireNonNull(evaluation);
        Objects.requireNonNull(tuning);
        var prepared = new ArrayList<Effect<E>>();
        for (var effect : evaluation.effects()) {
            if (effect.cancelled()) {
                continue;
            }
            var parameters = tuning.spells().get(effect.kind());
            if (parameters == null) {
                throw new IllegalArgumentException("Missing execution tuning for " + effect.kind());
            }
            var source = effect.source();
            var base = new EnumMap<E, Integer>(elements.type());
            var remaining = new EnumMap<E, Integer>(elements.type());
            remaining.putAll(effect.effectivePowers());
            for (var input : List.of(source.construction().first(),
                source.construction().second())) {
                int available = Math.min(input.units(), remaining.getOrDefault(input.element(), 0));
                base.put(input.element(), available);
                remaining.put(
                    input.element(), remaining.getOrDefault(input.element(), 0) - available);
            }
            var effectivePlan = new SpellInterpreter.Plan<>(
                source.identity(),
                effect.effectivePowers(),
                effect.activeAdditions(),
                source.construction());
            int reach = interpreter.range(
                effectivePlan,
                parameters.initialReach().applyAsInt(Map.copyOf(base)),
                SpellCatalogue.Modifier.RANGE_UP,
                SpellCatalogue.Modifier.RANGE_DOWN,
                calculations,
                parameters.basePowerRange());
            Map<E, Integer> cost = Map.copyOf(parameters.manaCost().apply(effect));
            for (var entry : cost.entrySet()) {
                if (!elements.contains(entry.getKey())) {
                    throw new IllegalArgumentException("Unknown mana cost role: " + entry.getKey());
                }
                if (entry.getValue() < 0) {
                    throw new IllegalArgumentException("Negative mana cost for " + entry.getKey());
                }
            }
            double mortality = 0;
            if (effect.growth().isPresent() && !effect.growth().orElseThrow().balanced()) {
                mortality = probability(tuning.plantDeathProbability().applyAsDouble(effect));
            }
            prepared.add(new Effect<>(effect, reach, cost, mortality));
        }
        double overload = evaluation.excessSpellCount() == 0
            ? 0
            : probability(tuning.overloadProbability().applyAsDouble(evaluation));
        return new Result<>(evaluation, prepared, overload);
    }

    /** Rejects non-finite or out-of-range policy outputs. */
    private double probability(double probability) {
        if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Probability outside [0, 1]: " + probability);
        }
        return probability;
    }

    /** One prepared action, preserving its source, whole-block reach, cost and mortality risk. */
    record Effect<E extends Enum<E>>(
        SpellEvaluation.Effect<E> source,
        int reach,
        Map<E, Integer> manaCost,
        double plantDeathProbability) {
        /** Publishes costs as immutable data. */
        Effect {
            Objects.requireNonNull(source);
            manaCost = Map.copyOf(manaCost);
        }
    }

    /** Original evaluation plus ordered, uncancelled, fully tuned actions. */
    record Result<E extends Enum<E>>(
        SpellEvaluation.Result<E> evaluation,
        List<Effect<E>> effects,
        double overloadProbability) {
        /** Publishes actions as immutable data. */
        Result {
            Objects.requireNonNull(evaluation);
            effects = List.copyOf(effects);
        }
    }
}
