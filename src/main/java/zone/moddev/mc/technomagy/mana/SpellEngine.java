package zone.moddev.mc.technomagy.mana;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Public, world-independent entry point for heuristic spell evaluation (D35-D66).
 *
 * <p>
 * Build immutable {@link SpellStream} trees, evaluate their chemistry and ordered effects, then
 * supply explicit execution tuning to resolve ranges, costs, and risk. Evaluation never spends
 * mana, changes blocks, or advances time. All quantities use D47's whole units: one Raw is six
 * elemental power units. Keep the original stream for exact prism splitting; effect plans are
 * derived views and cannot reconstruct all construction history.
 *
 * <p>
 * Instances are immutable and thread-safe. Execution-policy callbacks must themselves be pure and
 * thread-safe. Minecraft adapters may use {@link ManaType} as their element enum; the engine also
 * supports independent enum identities for testing and integrations.
 *
 * @param <E>
 *            caller's mana type enum
 */
public final class SpellEngine<E extends Enum<E>> {
    private final SpellElements<E> elements;
    private final SpellHeuristics calculations;
    private final SpellEvaluation<E> evaluation;
    private final SpellPreparation<E> preparation;

    /**
     * Creates an engine with the default pair catalogue and an explicit numerical balance snapshot.
     *
     * @param elements
     *            seven distinct flavor roles
     * @param numbers
     *            validated numerical tuning, including growth and range rules
     * @throws NullPointerException
     *             if either argument is null
     * @throws IllegalArgumentException
     *             if numerical tuning is invalid
     */
    public SpellEngine(SpellElements<E> elements, Numbers numbers) {
        this(elements, numbers, defaultCatalogue(elements));
    }

    /**
     * Creates an engine with a complete, explicitly supplied pair/modifier catalogue.
     *
     * @param elements
     *            seven distinct flavor roles
     * @param numbers
     *            numerical tuning snapshot
     * @param catalogue
     *            immutable typed meanings; life-and-death remains a utility, not a spell
     * @throws IllegalArgumentException
     *             if coverage differs from the fourteen non-utility pairs
     */
    public SpellEngine(SpellElements<E> elements, Numbers numbers, Catalogue<E> catalogue) {
        this.elements = Objects.requireNonNull(elements);
        Objects.requireNonNull(numbers);
        Objects.requireNonNull(catalogue);
        if (catalogue.pairs().containsValue(SpellCatalogue.Kind.GROWTH)) {
            throw new IllegalArgumentException("Growth is an emergent handler, not a base pair");
        }
        Catalogue<E> defaults = defaultCatalogue(elements);
        if (!catalogue.pairs().keySet().equals(defaults.pairs().keySet())
            || !catalogue.modifiers().keySet().equals(defaults.modifiers().keySet())) {
            throw new IllegalArgumentException(
                "Catalogue must cover fourteen spell pairs and six modifiers");
        }
        calculations = new SpellHeuristics(
            numbers.rangeFraction(),
            numbers.earthCap(),
            numbers.minimumReach(),
            numbers.dropoutPerPoint(),
            numbers.dropoutCap(),
            numbers.plantBase(),
            numbers.plantScale(),
            numbers.crystalBase(),
            numbers.crystalThreshold());
        var pairMeanings = new HashMap<SpellInterpreter.Pair<E>, SpellCatalogue.Kind>();
        catalogue
            .pairs()
            .forEach(
                (pair, kind) -> pairMeanings.put(
                    new SpellInterpreter.Pair<>(pair.first(), pair.second()),
                    kind));
        var interpreter = new SpellInterpreter<>(
            elements.type(),
            pairMeanings,
            catalogue.modifiers(),
            Set.of(new SpellInterpreter.Pair<>(elements.light(), elements.dark())));
        evaluation = new SpellEvaluation<>(elements, calculations, interpreter);
        preparation = new SpellPreparation<>(elements, calculations, interpreter);
    }

    /**
     * Returns the agreed catalogue as immutable typed data, ready for explicit customization.
     *
     * @param elements
     *            caller's seven mana roles
     * @param <E>
     *            caller enum type
     * @return fourteen spell pairs and six non-base modifier actions
     */
    public static <E extends Enum<E>> Catalogue<E> defaultCatalogue(SpellElements<E> elements) {
        var interpreter = SpellCatalogue.create(Objects.requireNonNull(elements));
        var pairs = new HashMap<ElementPair<E>, SpellCatalogue.Kind>();
        interpreter
            .pairs()
            .forEach(
                (pair, kind) -> pairs.put(new ElementPair<>(pair.first(), pair.second()), kind));
        return new Catalogue<>(pairs, interpreter.modifiers());
    }

    /**
     * Returns immutable chemical state and ordered spell descriptions without world effects.
     *
     * @param stream
     *            original construction; direct Light/Dark merges represent prism pairing
     * @return resolved chemistry, effects, and excess active spell count
     * @throws IllegalStateException
     *             if an individual modifier is applied after joining spells
     * @throws ArithmeticException
     *             if elemental unit accounting overflows
     */
    public Evaluation<E> evaluate(SpellStream<E> stream) {
        return view(evaluation.evaluate(stream));
    }

    /**
     * Prices and resolves the range of every active spell using required caller-supplied policies.
     *
     * @param stream
     *            original construction
     * @param tuning
     *            immutable spell settings and pure cost/risk policies
     * @return ordered prepared effects; cancelled spells have no cost
     * @throws IllegalArgumentException
     *             if required tuning is absent or returns invalid values
     * @throws ArithmeticException
     *             if reach or power overflows
     */
    public Prepared<E> prepare(SpellStream<E> stream, ExecutionTuning<E> tuning) {
        Objects.requireNonNull(tuning);
        var parameters = new java.util.EnumMap<SpellCatalogue.Kind, SpellTuning.Parameters<E>>(
            SpellCatalogue.Kind.class);
        tuning.spells()
            .forEach(
                (kind, settings) -> parameters.put(
                    kind,
                    new SpellTuning.Parameters<>(
                        settings.initialReach(),
                        settings.basePowerRange(),
                        effect -> settings.manaCost().apply(view(effect)))));
        var tuned = new SpellTuning<>(
            parameters,
            result -> tuning.overloadProbability().applyAsDouble(view(result)),
            effect -> tuning.plantDeathProbability().applyAsDouble(view(effect)));
        var result = preparation.prepare(evaluation.evaluate(stream), tuned);
        return new Prepared<>(
            view(result.evaluation()),
            result.effects().stream()
                .map(
                    effect -> new PreparedEffect<>(
                        view(effect.source()),
                        effect.reach(),
                        effect.manaCost(),
                        effect.plantDeathProbability()))
                .toList(),
            result.overloadProbability());
    }

    /**
     * Evaluates outside-prism contact without forming new life-and-death pairs.
     *
     * @param first
     *            first existing stream
     * @param second
     *            second existing stream
     * @return chemistry only; unpaired polar contact releases stabilization energy
     */
    public Chemistry<E> contact(SpellStream<E> first, SpellStream<E> second) {
        return view(new StreamChemistry<>(elements, calculations).contact(first, second));
    }

    /**
     * Returns the tuned forcing multiplier; ordinary growth is unchanged below the threshold.
     *
     * @param elementalPower
     *            nonnegative power of the crystal's matching flavor
     * @return tick opportunity multiplier, not an instruction to tick arbitrary blocks
     * @throws IllegalArgumentException
     *             if power is negative
     * @throws ArithmeticException
     *             if the multiplier overflows
     */
    public int crystalMultiplier(int elementalPower) {
        return calculations.crystalMultiplier(elementalPower);
    }

    /**
     * Returns elemental dropout chance per five-tick span; overload probability is separate.
     *
     * @param excessPower
     *            nonnegative unstabilized excess elemental units
     * @return probability between zero and the configured cap
     * @throws IllegalArgumentException
     *             if excess is negative
     */
    public double dropoutProbability(int excessPower) {
        return calculations.dropoutProbability(excessPower);
    }

    /**
     * Returns D60's magic damage, bypassing armor, with the first Raw unit excluded.
     *
     * @param rawUnits
     *            nonnegative Raw stream strength, not elemental-equivalent power
     * @return damage points; no entity is damaged by this calculation
     * @throws IllegalArgumentException
     *             if strength is negative
     */
    public int rawDamage(int rawUnits) {
        return calculations.rawDamage(rawUnits);
    }

    /**
     * Describes elemental dropout risk without producing particles or changing blocks (D67).
     *
     * @param result
     *            evaluated stream snapshot
     * @return one entry per active flavor exceeding its stabilization level
     */
    public List<Dropout<E>> dropout(Evaluation<E> result) {
        Objects.requireNonNull(result);
        return result.chemistry().active().entrySet().stream()
            .filter(entry -> entry.getValue() > result.chemistry().stabilizationLevel())
            .map(
                entry -> {
                    int excess = entry.getValue() - result.chemistry().stabilizationLevel();
                    Severity severity = excess == 1
                        ? Severity.PARTICLES
                        : excess == 2
                            ? Severity.DROPLETS
                            : Severity.WORLD_EFFECTS;
                    return new Dropout<>(
                        entry.getKey(),
                        excess,
                        severity,
                        calculations.dropoutProbability(excess));
                })
            .sorted(java.util.Comparator.comparing(Dropout::element))
            .toList();
    }

    /** Copies quantity maps while rejecting negative or null input. */
    private static <T> Map<T, Integer> checkedAmounts(Map<T, Integer> amounts) {
        Map<T, Integer> copy = Map.copyOf(amounts);
        if (copy.values().stream().anyMatch(amount -> amount < 0)) {
            throw new IllegalArgumentException("Negative mana amount");
        }
        return copy;
    }

    /** Tests whether a probability is finite and within its closed unit interval. */
    private static boolean validProbability(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }

    /** Adapts a complete internal result without exposing implementation-only classes. */
    private Evaluation<E> view(SpellEvaluation.Result<E> result) {
        return new Evaluation<>(
            view(result.chemistry()),
            result.effects().stream().map(this::view).toList(),
            result.excessSpellCount());
    }

    /** Adapts immutable chemistry ledgers. */
    private Chemistry<E> view(StreamChemistry.Result<E> result) {
        return new Chemistry<>(
            result.active(),
            result.pairedUnits(),
            result.rawUnits(),
            result.dropout(),
            result.releasedPower(),
            result.stabilizationLevel(),
            result.spellsCancelled());
    }

    /** Adapts ordered modifier provenance and the special Growth properties. */
    private Effect<E> view(SpellEvaluation.Effect<E> effect) {
        return new Effect<>(
            effect.kind(),
            effect.effectivePowers(),
            additions(effect.source().additions()),
            additions(effect.activeAdditions()),
            effect.growth()
                .map(growth -> new Growth(growth.tickMultiplier(), growth.balanced())),
            effect.cancelled());
    }

    /** Projects original or surviving addition events without exposing internal types. */
    private List<Addition<E>> additions(
        List<SpellInterpreter.Addition<E, SpellCatalogue.Modifier>> additions) {
        return additions.stream()
            .map(
                addition -> new Addition<>(
                    addition.element(),
                    addition.units(),
                    addition.basePower(),
                    Optional.ofNullable(addition.action())))
            .toList();
    }

    /**
     * Commutative catalogue key; never an ordered execution sequence.
     *
     * @param first
     *            first distinct flavor
     * @param second
     *            second distinct flavor
     * @param <E>
     *            caller enum type
     */
    public record ElementPair<E extends Enum<E>>(E first, E second) {
        /** Canonicalizes enum order and rejects null or same-element spell pairs. */
        public ElementPair {
            Objects.requireNonNull(first);
            Objects.requireNonNull(second);
            if (first == second) {
                throw new IllegalArgumentException(
                    "Same-element reinforcement is not a spell pair");
            }
            if (first.compareTo(second) > 0) {
                E swap = first;
                first = second;
                second = swap;
            }
        }
    }

    /**
     * Immutable typed catalogue snapshot; datapack schema/loading is a separate integration.
     *
     * @param pairs
     *            complete non-utility spell-pair meanings
     * @param modifiers
     *            complete per-flavor non-base action meanings
     * @param <E>
     *            caller enum type
     */
    public record Catalogue<E extends Enum<E>>(
        Map<ElementPair<E>, SpellCatalogue.Kind> pairs,
        Map<E, SpellCatalogue.Modifier> modifiers) {
        /** Rejects null keys/values and copies both maps. */
        public Catalogue {
            pairs = Map.copyOf(pairs);
            modifiers = Map.copyOf(modifiers);
        }
    }

    /** Environmental severity, distinct from the probability of an elemental dropout event. */
    public enum Severity {
        /** One excess unit: visual particles only. */
        PARTICLES,
        /** Two excess units: short-lived droplets/embers with rapid vertical falloff. */
        DROPLETS,
        /** More than two excess units: material world effects with increasing spatial extent. */
        WORLD_EFFECTS
    }

    /**
     * Unstabilized elemental release risk; executors supply spatial rules and actual effects.
     *
     * @param element
     *            excess flavor
     * @param excessPower
     *            unstabilized units above the supported level
     * @param severity
     *            visual/material tier
     * @param probability
     *            event chance per five-tick span
     * @param <E>
     *            caller element type
     */
    public record Dropout<E>(E element, int excessPower, Severity severity, double probability) {
        /** Rejects invalid release descriptions. */
        public Dropout {
            Objects.requireNonNull(element);
            Objects.requireNonNull(severity);
            if (excessPower <= 0
                || !Double.isFinite(probability)
                || probability < 0
                || probability > 1) {
                throw new IllegalArgumentException("Invalid dropout description");
            }
        }
    }

    /**
     * Numerical tuning snapshot. Defaults are the explicitly agreed design values.
     *
     * @param rangeFraction
     *            range fraction per Earth/Air unit
     * @param earthCap
     *            maximum blocks gained per Earth unit
     * @param minimumReach
     *            minimum remaining spell reach in blocks
     * @param dropoutPerPoint
     *            elemental dropout chance per excess unit
     * @param dropoutCap
     *            maximum elemental dropout chance
     * @param plantBase
     *            baseline plant tick multiplier
     * @param plantScale
     *            multiplier contribution per Water/Earth unit
     * @param crystalBase
     *            baseline crystal multiplier
     * @param crystalThreshold
     *            elemental power before forcing contributes
     */
    public record Numbers(
        double rangeFraction,
        int earthCap,
        int minimumReach,
        double dropoutPerPoint,
        double dropoutCap,
        double plantBase,
        double plantScale,
        int crystalBase,
        int crystalThreshold) {
        /** Validates all numeric fields immediately. */
        public Numbers {
            new SpellHeuristics(
                rangeFraction,
                earthCap,
                minimumReach,
                dropoutPerPoint,
                dropoutCap,
                plantBase,
                plantScale,
                crystalBase,
                crystalThreshold);
        }

        /** Returns D45/D49/D50/D59's agreed numerical starting values. */
        public static Numbers standard() {
            return new Numbers(0.15, 3, 2, 0.25, 0.85, 1, 0.5, 1, 2);
        }
    }

    /**
     * Immutable chemical quantities, separate from original construction history.
     *
     * @param active
     *            active unpaired elemental units
     * @param pairedUnits
     *            life-and-death units, each worth two elemental units
     * @param rawUnits
     *            Raw units, each worth six elemental units
     * @param dropout
     *            quantities awaiting environmental release
     * @param releasedPower
     *            annihilation energy retained for stabilization
     * @param stabilizationLevel
     *            supported per-flavor power
     * @param spellsCancelled
     *            whether Raw conversion cancelled the sequence
     * @param <E>
     *            element enum
     */
    public record Chemistry<E>(
        Map<E, Integer> active,
        int pairedUnits,
        int rawUnits,
        Map<E, Integer> dropout,
        long releasedPower,
        int stabilizationLevel,
        boolean spellsCancelled) {
        /** Copies quantity maps before publication. */
        public Chemistry {
            active = checkedAmounts(active);
            dropout = checkedAmounts(dropout);
            if (pairedUnits < 0
                || rawUnits < 0
                || releasedPower < 0
                || (stabilizationLevel != 2
                    && stabilizationLevel != 3
                    && stabilizationLevel != 5
                    && stabilizationLevel != 6)) {
                throw new IllegalArgumentException("Invalid chemistry snapshot");
            }
        }
    }

    /**
     * One grouped addition in original construction order.
     *
     * @param element
     *            added flavor
     * @param units
     *            whole units added together
     * @param basePower
     *            whether this reinforces the base instead of applying a modifier
     * @param action
     *            non-base action, absent for reinforcement
     * @param <E>
     *            element enum
     */
    public record Addition<E>(
        E element, int units, boolean basePower, Optional<SpellCatalogue.Modifier> action) {
        /** Validates positive units and the distinction between reinforcement and modifiers. */
        public Addition {
            Objects.requireNonNull(element);
            Objects.requireNonNull(action);
            if (units <= 0 || basePower == action.isPresent()) {
                throw new IllegalArgumentException("Invalid addition description");
            }
        }
    }

    /**
     * Plant-growth properties; imbalanced growth needs the supplied mortality policy.
     *
     * @param tickMultiplier
     *            local plant tick opportunity multiplier
     * @param balanced
     *            whether Water/Earth differ by at most one unit
     */
    public record Growth(double tickMultiplier, boolean balanced) {
        /** Requires a finite multiplier that does not slow ordinary plant growth. */
        public Growth {
            if (!Double.isFinite(tickMultiplier) || tickMultiplier < 1) {
                throw new IllegalArgumentException("Invalid growth multiplier");
            }
        }
    }

    /**
     * Ordered spell description, not a committed action.
     *
     * @param kind
     *            resolved spell action
     * @param powers
     *            effective flavor units after polar allocation
     * @param additions
     *            original grouped modifier/reinforcement provenance
     * @param activeAdditions
     *            surviving grouped events; annihilated modifiers are omitted
     * @param growth
     *            present only for an active Growth action
     * @param cancelled
     *            whether annihilation exhausted a base component
     * @param <E>
     *            element enum
     */
    public record Effect<E>(
        SpellCatalogue.Kind kind,
        Map<E, Integer> powers,
        List<Addition<E>> additions,
        List<Addition<E>> activeAdditions,
        Optional<Growth> growth,
        boolean cancelled) {
        /** Defensively copies collections. */
        public Effect {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(growth);
            powers = checkedAmounts(powers);
            additions = List.copyOf(additions);
            activeAdditions = List.copyOf(activeAdditions);
        }
    }

    /**
     * Complete evaluated stream snapshot.
     *
     * @param chemistry
     *            chemical ledgers
     * @param effects
     *            primary-first spell descriptions, including cancelled entries
     * @param excessSpellCount
     *            active spell count beyond the safe first two
     * @param <E>
     *            element enum
     */
    public record Evaluation<E>(
        Chemistry<E> chemistry, List<Effect<E>> effects, int excessSpellCount) {
        /** Copies effects and rejects missing chemistry. */
        public Evaluation {
            Objects.requireNonNull(chemistry);
            effects = List.copyOf(effects);
            if (excessSpellCount < 0) {
                throw new IllegalArgumentException("Negative excess spell count");
            }
        }
    }

    /**
     * Required per-spell range and payment policy; callbacks must be pure and thread-safe.
     *
     * @param initialReach
     *            reach from the two actual base input quantities
     * @param basePowerRange
     *            updated reach for current reach and later added base units
     * @param manaCost
     *            per-flavor activation cost; must return nonnegative whole units
     * @param <E>
     *            element enum
     */
    public record SpellSettings<E>(
        ToIntFunction<Map<E, Integer>> initialReach,
        IntBinaryOperator basePowerRange,
        Function<Effect<E>, Map<E, Integer>> manaCost) {
        /** Rejects absent required policies. */
        public SpellSettings {
            Objects.requireNonNull(initialReach);
            Objects.requireNonNull(basePowerRange);
            Objects.requireNonNull(manaCost);
        }
    }

    /**
     * Required execution tuning; no cost, range, overload, or mortality defaults are implied.
     *
     * @param spells
     *            settings for every active action that may be prepared
     * @param overloadProbability
     *            risk policy for streams exceeding two active spells
     * @param plantDeathProbability
     *            risk policy for imbalanced Growth
     * @param <E>
     *            element enum
     */
    public record ExecutionTuning<E>(
        Map<SpellCatalogue.Kind, SpellSettings<E>> spells,
        ToDoubleFunction<Evaluation<E>> overloadProbability,
        ToDoubleFunction<Effect<E>> plantDeathProbability) {
        /** Copies spell settings and rejects absent probability policies. */
        public ExecutionTuning {
            spells = Map.copyOf(spells);
            Objects.requireNonNull(overloadProbability);
            Objects.requireNonNull(plantDeathProbability);
        }
    }

    /**
     * One ready-to-validate action. Payment and world changes are still executor responsibilities.
     *
     * @param effect
     *            interpreted action
     * @param reach
     *            whole-block reach
     * @param manaCost
     *            complete per-flavor activation cost
     * @param plantDeathProbability
     *            supplied mortality risk for this activation
     * @param <E>
     *            element enum
     */
    public record PreparedEffect<E>(
        Effect<E> effect, int reach, Map<E, Integer> manaCost, double plantDeathProbability) {
        /** Copies cost quantities. */
        public PreparedEffect {
            Objects.requireNonNull(effect);
            manaCost = checkedAmounts(manaCost);
            if (reach < 1 || !validProbability(plantDeathProbability)) {
                throw new IllegalArgumentException("Invalid prepared action");
            }
        }
    }

    /**
     * Prepared, ordered effects with explicit risk policies resolved.
     *
     * @param evaluation
     *            original evaluation snapshot
     * @param effects
     *            uncancelled actions with ranges and costs
     * @param overloadProbability
     *            supplied overload risk
     * @param <E>
     *            element enum
     */
    public record Prepared<E>(
        Evaluation<E> evaluation, List<PreparedEffect<E>> effects, double overloadProbability) {
        /** Copies ordered actions. */
        public Prepared {
            Objects.requireNonNull(evaluation);
            effects = List.copyOf(effects);
            if (!validProbability(overloadProbability)) {
                throw new IllegalArgumentException("Invalid overload probability");
            }
        }
    }
}
