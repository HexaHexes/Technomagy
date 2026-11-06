package zone.moddev.mc.technomagy.mana;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure bottom-up stream chemistry for D35-D38, D47, D55-D56, and D62.
 *
 * <p>
 * Original trees are never rewritten: reversible prism construction and active chemistry are
 * distinct. Paired life-and-death is kept separate from unpaired Light/Dark. Annihilation moves
 * polar power into an explicit energy ledger and retains its stabilization contribution. Raw
 * conversion cancels spells and routes all excess to an explicit dropout ledger. All arithmetic is
 * checked; no released or converted power silently disappears. Instances are thread-safe.
 */
final class StreamChemistry<E extends Enum<E>> {
    private final SpellElements<E> elements;
    private final SpellHeuristics rules;

    /** Creates chemistry using explicit flavor roles and calculation rules. */
    StreamChemistry(SpellElements<E> elements, SpellHeuristics rules) {
        this.elements = Objects.requireNonNull(elements);
        this.rules = Objects.requireNonNull(rules);
    }

    /**
     * Evaluates a tree iteratively without world access or recursive Java calls.
     *
     * @param stream
     *            non-null original input tree
     * @return immutable active quantities, releases, and stabilization state
     * @throws ArithmeticException
     *             if a per-type amount overflows
     */
    Result<E> evaluate(SpellStream<E> stream) {
        return publish(evaluateState(stream));
    }

    /** Evaluates contact outside a prism: polar flavors annihilate instead of pairing. */
    Result<E> contact(SpellStream<E> first, SpellStream<E> second) {
        return publish(combine(evaluateState(first), evaluateState(second), false));
    }

    /** Resolves a prism construction into exclusively owned scratch state. */
    private State evaluateState(SpellStream<E> stream) {
        Objects.requireNonNull(stream);
        var pending = new ArrayDeque<Visit<E>>();
        var results = new ArrayDeque<State>();
        pending.push(new Visit<>(stream, false));
        while (!pending.isEmpty()) {
            Visit<E> visit = pending.pop();
            if (visit.node().isElement()) {
                State leaf = new State();
                E type = visit.node().element();
                if (!elements.contains(type)) {
                    throw new IllegalArgumentException("Unconfigured mana role: " + type);
                }
                int amount = visit.node().units();
                if (type == elements.raw()) {
                    leaf.raw = amount;
                } else {
                    leaf.active.put(type, amount);
                    leaf.pure = type;
                    leaf.lightSeen = type == elements.light();
                    leaf.darkSeen = type == elements.dark();
                }
                results.push(leaf);
            } else if (!visit.expanded()) {
                List<SpellStream<E>> inputs = visit.node().split();
                pending.push(new Visit<>(visit.node(), true));
                pending.push(new Visit<>(inputs.get(1), false));
                pending.push(new Visit<>(inputs.get(0), false));
            } else {
                State secondary = results.pop();
                State primary = results.pop();
                results.push(combine(primary, secondary, true));
            }
        }
        return results.pop();
    }

    /** Publishes an immutable view including stabilization retained after annihilation. */
    private Result<E> publish(State result) {
        int stabilized = result.paired > 0
            ? 6
            : result.lightSeen && result.darkSeen
                ? 5
                : result.lightSeen || result.darkSeen ? 3 : 2;
        return new Result<>(
            result.active,
            result.paired,
            result.raw,
            result.dropout,
            result.energy,
            stabilized,
            result.converted);
    }

    /** Combines two independent state ledgers; no child state is mutated. */
    private State combine(State first, State second, boolean prism) {
        State result = new State();
        copyInto(result.active, first.active);
        copyInto(result.active, second.active);
        copyInto(result.dropout, first.dropout);
        copyInto(result.dropout, second.dropout);
        result.raw = Math.addExact(first.raw, second.raw);
        result.paired = Math.addExact(first.paired, second.paired);
        result.energy = Math.addExact(first.energy, second.energy);
        result.lightSeen = first.lightSeen || second.lightSeen;
        result.darkSeen = first.darkSeen || second.darkSeen;
        result.converted = first.converted || second.converted;
        boolean polarPair = (first.pure == elements.light() && second.pure == elements.dark())
            || (first.pure == elements.dark() && second.pure == elements.light());
        if (prism && polarPair) {
            int light = result.amount(elements.light());
            int dark = result.amount(elements.dark());
            int paired = Math.min(light, dark);
            result.paired = Math.addExact(result.paired, paired);
            result.subtract(elements.light(), paired);
            result.subtract(elements.dark(), paired);
            result.lightSeen = light > paired;
            result.darkSeen = dark > paired;
        } else {
            int annihilated = Math.min(result.amount(elements.light()),
                result.amount(elements.dark()));
            result.subtract(elements.light(), annihilated);
            result.subtract(elements.dark(), annihilated);
            result.energy = Math.addExact(result.energy, 2L * annihilated);
        }
        var raw = rules.formRaw(
            new SpellHeuristics.RawIngredients(
                result.amount(elements.earth()),
                result.amount(elements.air()),
                result.amount(elements.fire()),
                result.amount(elements.water()),
                result.paired));
        if (prism && raw.rawUnits() > 0) {
            result.raw = Math.addExact(result.raw, raw.rawUnits());
            result.subtract(elements.earth(), raw.rawUnits());
            result.subtract(elements.air(), raw.rawUnits());
            result.subtract(elements.fire(), raw.rawUnits());
            result.subtract(elements.water(), raw.rawUnits());
            result.paired -= raw.rawUnits();
            result.converted = true;
        }
        if (result.converted) {
            copyInto(result.dropout, result.active);
            result.active.clear();
            if (result.paired > 0) {
                result.dropout.merge(elements.light(), result.paired, Math::addExact);
                result.dropout.merge(elements.dark(), result.paired, Math::addExact);
                result.paired = 0;
            }
        }
        if (!result.converted && first.pure != null && first.pure == second.pure) {
            result.pure = first.pure;
        }
        return result;
    }

    /** Merges checked units without aliasing a child map. */
    private void copyInto(EnumMap<E, Integer> target, Map<E, Integer> source) {
        source.forEach((type, amount) -> target.merge(type, amount, Math::addExact));
    }

    /**
     * Immutable chemistry result; energy uses elemental power units and Raw uses sixfold units.
     * Active/dropout maps contain only unpaired elemental quantities, never Raw entries.
     */
    record Result<E>(
        Map<E, Integer> active,
        int pairedUnits,
        int rawUnits,
        Map<E, Integer> dropout,
        long releasedPower,
        int stabilizationLevel,
        boolean spellsCancelled) {
        /** Copies mutable evaluation ledgers before publishing results. */
        Result {
            active = Map.copyOf(active);
            dropout = Map.copyOf(dropout);
        }

        /** Returns accounted elemental power, including all released and converted quantities. */
        long elementalPower() {
            long total = 2L * pairedUnits + 6L * rawUnits + releasedPower;
            for (int amount : active.values()) {
                total = Math.addExact(total, amount);
            }
            for (int amount : dropout.values()) {
                total = Math.addExact(total, amount);
            }
            return total;
        }
    }

    /** Mutable, exclusively owned scratch state for one evaluated subtree. */
    private final class State {
        private final EnumMap<E, Integer> active = new EnumMap<>(elements.type());
        private final EnumMap<E, Integer> dropout = new EnumMap<>(elements.type());
        private E pure;
        private int paired;
        private int raw;
        private long energy;
        private boolean lightSeen;
        private boolean darkSeen;
        private boolean converted;

        /** Reads absent elemental quantities as zero. */
        private int amount(E type) {
            return active.getOrDefault(type, 0);
        }

        /** Removes exact units and omits zero entries. */
        private void subtract(E type, int amount) {
            int remainder = Math.subtractExact(amount(type), amount);
            if (remainder < 0) {
                throw new IllegalStateException("Chemistry over-consumed " + type);
            }
            if (remainder == 0) {
                active.remove(type);
            } else {
                active.put(type, remainder);
            }
        }
    }

    /** One postorder evaluation frame. */
    private record Visit<E extends Enum<E>>(SpellStream<E> node, boolean expanded) {
    }
}
