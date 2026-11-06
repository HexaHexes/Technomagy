package zone.moddev.mc.technomagy.mana;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable ordered construction tree for D37, D52, D54, and D57.
 *
 * <p>
 * The tree retains grouped additions rather than flattening quantities. It is safe to share between
 * threads. Traversal uses an explicit stack so long prism chains do not exhaust the Java call
 * stack. Persistent interpretation histories avoid quadratic copying as chains grow; each output
 * sequence is flattened only once. Chemistry and world execution are deliberately separate from
 * this construction.
 *
 * @param <E>
 *            element enum supplied by the owning mana model
 */
public final class SpellStream<E extends Enum<E>> {
    private final E element;
    private final int units;
    private final SpellStream<E> primary;
    private final SpellStream<E> secondary;

    /** Creates a validated leaf or merge; only the factories select its shape. */
    private SpellStream(E element, int units, SpellStream<E> primary, SpellStream<E> secondary) {
        this.element = element;
        this.units = units;
        this.primary = primary;
        this.secondary = secondary;
    }

    /**
     * Creates one grouped elemental input.
     *
     * @param <E>
     *            caller's element enum
     * @param element
     *            non-null flavor, not an already interpreted compound
     * @param units
     *            positive whole units supplied together
     * @return immutable input
     * @throws IllegalArgumentException
     *             if units are not positive
     * @throws NullPointerException
     *             if element is null
     */
    public static <E extends Enum<E>> SpellStream<E> element(E element, int units) {
        Objects.requireNonNull(element, "element");
        if (units <= 0) {
            throw new IllegalArgumentException("Stream input must be positive: " + units);
        }
        return new SpellStream<>(element, units, null, null);
    }

    /**
     * Creates an ordered prism merge, preserving both immutable input trees (D52).
     *
     * @param primary
     *            stream executed first
     * @param secondary
     *            stream executed afterward
     * @param <E>
     *            element enum shared by both inputs
     * @return a new merge retaining the original inputs by identity
     * @throws NullPointerException
     *             if either input is null
     */
    public static <E extends Enum<E>> SpellStream<E> merge(
        SpellStream<E> primary, SpellStream<E> secondary) {
        return new SpellStream<>(
            null, 0, Objects.requireNonNull(primary), Objects.requireNonNull(secondary));
    }

    /**
     * Identifies grouped elemental inputs.
     *
     * @return true for a leaf, false for a prism merge
     */
    public boolean isElement() {
        return element != null;
    }

    /**
     * Reads a grouped input's flavor.
     *
     * @return leaf flavor
     * @throws IllegalStateException
     *             if this is a merge
     */
    public E element() {
        requireElement();
        return element;
    }

    /**
     * Reads a grouped input's quantity.
     *
     * @return positive whole mana units, using D47's Raw convention
     * @throws IllegalStateException
     *             if this is a merge
     */
    public int units() {
        requireElement();
        return units;
    }

    /**
     * Exposes the most recent merge's exact inputs (D26, D37).
     *
     * @return immutable primary/secondary inputs in slot order
     * @throws IllegalStateException
     *             if this leaf has no merge history
     */
    public List<SpellStream<E>> split() {
        if (isElement()) {
            throw new IllegalStateException("Element input has no merge to undo");
        }
        return List.of(primary, secondary);
    }

    /**
     * Interprets a construction bottom-up with caller-owned pair meanings (D54, D57).
     *
     * <p>
     * Same-element inputs reinforce a pure stream without establishing a spell (D32). Distinct
     * elemental inputs establish a base, passed to the supplied rule. A spell plus an elemental
     * input gains one grouped addition without creating another spell. Two spell-bearing streams
     * concatenate primary first. Individual modifiers must be applied before spells are joined
     * (D62). Utility pairs such as life and death contribute no spell to the execution sequence.
     *
     * @param pairs
     *            rule resolving commutative base pairs; never returns null
     * @return immutable ordered spells, or empty for a pure elemental stream
     * @throws ArithmeticException
     *             if reinforced pure units overflow
     * @throws IllegalStateException
     *             for an undefined modifier target
     */
    List<Spell<E>> interpret(PairRule<E> pairs) {
        Objects.requireNonNull(pairs, "pairs");
        var pending = new ArrayDeque<Visit<E>>();
        var values = new ArrayDeque<Meaning<E>>();
        pending.push(new Visit<>(this, false));
        while (!pending.isEmpty()) {
            Visit<E> visit = pending.pop();
            SpellStream<E> node = visit.node();
            if (node.isElement()) {
                values.push(new Meaning<>(node, null));
            } else if (!visit.expanded()) {
                pending.push(new Visit<>(node, true));
                pending.push(new Visit<>(node.secondary, false));
                pending.push(new Visit<>(node.primary, false));
            } else {
                Meaning<E> right = values.pop();
                Meaning<E> left = values.pop();
                if (left.pure() != null && right.pure() != null) {
                    SpellStream<E> firstInput = left.pure();
                    SpellStream<E> secondInput = right.pure();
                    E first = firstInput.element;
                    E second = secondInput.element;
                    if (first == second) {
                        values.push(
                            new Meaning<>(
                                element(
                                    first,
                                    Math.addExact(firstInput.units, secondInput.units)),
                                null));
                        continue;
                    }
                    if (first.compareTo(second) > 0) {
                        E swap = first;
                        first = second;
                        second = swap;
                    }
                    if (pairs.isUtility(first, second)) {
                        values.push(new Meaning<>(null, null));
                        continue;
                    }
                    pairs.validate(first, second);
                    values.push(
                        new Meaning<>(
                            null,
                            singleton(new Building<>(firstInput, secondInput, null))));
                } else if (left.pure() != null || right.pure() != null) {
                    Sequence<E> spells = left.pure() != null ? right.spells() : left.spells();
                    SpellStream<E> addition = left.pure() != null ? left.pure() : right.pure();
                    if (spells == null) {
                        // A utility pair stabilizes a pure carrier without establishing a spell.
                        values.push(new Meaning<>(addition, null));
                        continue;
                    }
                    if (spells.count() != 1) {
                        throw new IllegalStateException("Element addition needs one target spell");
                    }
                    Building<E> spell = spells.single();
                    values.push(
                        new Meaning<>(
                            null,
                            singleton(
                                new Building<>(
                                    spell.first(),
                                    spell.second(),
                                    new AdditionLink<>(
                                        addition, spell.additions())))));
                } else {
                    values.push(new Meaning<>(null, join(left.spells(), right.spells())));
                }
            }
        }
        return flatten(values.pop().spells());
    }

    /** Rejects access to leaf-only data on a merge. */
    private void requireElement() {
        if (!isElement()) {
            throw new IllegalStateException("Expected elemental input");
        }
    }

    /** Caller-owned validation of a canonical enum-ordered pair; may reject unknown reactions. */
    @FunctionalInterface
    interface PairRule<E extends Enum<E>> {
        /** Validates or rejects the base pair without mutating stream state. */
        void validate(E first, E second);

        /** Returns whether this pair carries utility energy rather than an executable spell. */
        default boolean isUtility(E first, E second) {
            return false;
        }
    }

    /**
     * One established base spell and its ordered grouped additions (D54, D58).
     *
     * @param first
     *            first original base input
     * @param second
     *            second original base input
     * @param additions
     *            immutable construction-order elemental additions
     */
    record Spell<E extends Enum<E>>(
        SpellStream<E> first, SpellStream<E> second, List<SpellStream<E>> additions) {
        /** Defensively copies additions and rejects null components. */
        Spell {
            Objects.requireNonNull(first);
            Objects.requireNonNull(second);
            additions = List.copyOf(additions);
        }

        /**
         * Returns whether an added element increases base power rather than modifying the spell.
         */
        boolean isBase(E element) {
            Objects.requireNonNull(element);
            return first.element() == element || second.element() == element;
        }
    }

    /** Internal interpretation state: a pure carrier or a persistent ordered sequence. */
    private record Meaning<E extends Enum<E>>(SpellStream<E> pure, Sequence<E> spells) {
    }

    /** One spell under construction, with constant-time persistent addition history. */
    private record Building<E extends Enum<E>>(
        SpellStream<E> first, SpellStream<E> second, AdditionLink<E> additions) {
    }

    /** Latest-first persistent addition chain, reversed once at publication. */
    private record AdditionLink<E extends Enum<E>>(
        SpellStream<E> input, AdditionLink<E> previous) {
    }

    /** Persistent concatenation tree, avoiding repeated copying of long ritual sequences. */
    private record Sequence<E extends Enum<E>>(
        Building<E> single, Sequence<E> primary, Sequence<E> secondary, int count) {
    }

    /** Creates a one-spell sequence. */
    private Sequence<E> singleton(Building<E> spell) {
        return new Sequence<>(spell, null, null, 1);
    }

    /** Joins sequences in constant time; utility-only streams contribute no spells. */
    private Sequence<E> join(Sequence<E> primary, Sequence<E> secondary) {
        if (primary == null) {
            return secondary;
        }
        if (secondary == null) {
            return primary;
        }
        return new Sequence<>(
            null, primary, secondary, Math.addExact(primary.count(), secondary.count()));
    }

    /** Publishes ordered spells and additions once, using iterative linear-time traversal. */
    private List<Spell<E>> flatten(Sequence<E> sequence) {
        if (sequence == null) {
            return List.of();
        }
        var pending = new ArrayDeque<Sequence<E>>();
        var result = new ArrayList<Spell<E>>();
        pending.push(sequence);
        while (!pending.isEmpty()) {
            Sequence<E> current = pending.pop();
            if (current.single() == null) {
                pending.push(current.secondary());
                pending.push(current.primary());
            } else {
                Building<E> spell = current.single();
                var additions = new ArrayDeque<SpellStream<E>>();
                for (AdditionLink<E> link = spell.additions(); link != null; link = link
                    .previous()) {
                    additions.push(link.input());
                }
                result.add(new Spell<>(spell.first(), spell.second(), List.copyOf(additions)));
            }
        }
        return List.copyOf(result);
    }

    /** One iterative postorder traversal frame. */
    private record Visit<E extends Enum<E>>(SpellStream<E> node, boolean expanded) {
    }
}
