package zone.moddev.mc.technomagy.mana;

/**
 * Pure calculations for spell interpretation (D45, D49, D50, D59, D60).
 *
 * <p>
 * This immutable, thread-safe calculator neither consumes mana nor changes the world. Callers
 * supply balance parameters explicitly so tuning is separate from interpretation. It does not
 * decide spell eligibility, stabilizer chemistry, or overload-dropout probability.
 */
final class SpellHeuristics {
    private final double rangeFraction;
    private final int earthCap;
    private final int minimumReach;
    private final double dropoutPerPoint;
    private final double dropoutCap;
    private final double plantBase;
    private final double plantScale;
    private final int crystalBase;
    private final int crystalThreshold;

    /**
     * Creates a calculator with explicit tuning values.
     *
     * @param rangeFraction
     *            fractional range adjustment per added unit
     * @param earthCap
     *            maximum added blocks per Earth unit
     * @param minimumReach
     *            minimum spell reach in blocks
     * @param dropoutPerPoint
     *            dropout probability per excess elemental power unit
     * @param dropoutCap
     *            maximum elemental dropout probability
     * @throws IllegalArgumentException
     *             if values are non-finite or outside their valid bounds
     */
    SpellHeuristics(
        double rangeFraction,
        int earthCap,
        int minimumReach,
        double dropoutPerPoint,
        double dropoutCap) {
        this(rangeFraction, earthCap, minimumReach, dropoutPerPoint, dropoutCap, 1, 0.5, 1, 2);
    }

    /** Creates a fully tuned calculator including Growth and crystal scaling (D45, D50). */
    SpellHeuristics(
        double rangeFraction,
        int earthCap,
        int minimumReach,
        double dropoutPerPoint,
        double dropoutCap,
        double plantBase,
        double plantScale,
        int crystalBase,
        int crystalThreshold) {
        probability(rangeFraction);
        probability(dropoutPerPoint);
        probability(dropoutCap);
        positive(earthCap);
        if (minimumReach < 2) {
            throw new IllegalArgumentException("Spell reach floor must be at least two blocks");
        }
        this.rangeFraction = rangeFraction;
        this.earthCap = earthCap;
        this.minimumReach = minimumReach;
        this.dropoutPerPoint = dropoutPerPoint;
        this.dropoutCap = dropoutCap;
        if (!Double.isFinite(plantBase)
            || plantBase < 1
            || !Double.isFinite(plantScale)
            || plantScale < 0) {
            throw new IllegalArgumentException("Invalid plant growth scaling");
        }
        positive(crystalBase);
        nonnegative(crystalThreshold);
        this.plantBase = plantBase;
        this.plantScale = plantScale;
        this.crystalBase = crystalBase;
        this.crystalThreshold = crystalThreshold;
    }

    /**
     * Applies one grouped Earth addition (D59), using nearest rounding with half rounded up.
     *
     * <p>
     * Each construction event calls this once, rather than flattening sequential additions. Exact
     * half-block increments round upward (D65).
     *
     * @param reach
     *            current reach in whole blocks
     * @param units
     *            positive Earth units added in this event
     * @return updated reach in whole blocks
     * @throws IllegalArgumentException
     *             if inputs are invalid
     * @throws ArithmeticException
     *             if the updated reach cannot fit in an int
     */
    int addEarth(int reach, int units) {
        validateReach(reach);
        positive(units);
        double exact = reach * rangeFraction * units;
        double rounded = Math.floor(exact + 0.5);
        long increment = Math.max(1L, Math.min((long) earthCap * units, (long) rounded));
        return Math.toIntExact(reach + increment);
    }

    /**
     * Applies one grouped Air addition, rounding reduction down and preserving the reach floor.
     *
     * @param reach
     *            current whole-block reach
     * @param units
     *            positive Air units added together
     * @return remaining whole-block reach (D59)
     * @throws IllegalArgumentException
     *             if inputs are invalid
     */
    int addAir(int reach, int units) {
        validateReach(reach);
        positive(units);
        double reduction = Math.max(1, Math.floor(reach * rangeFraction * units));
        return (int) Math.max(minimumReach, reach - reduction);
    }

    /**
     * Returns Growth's multiplier, not its eligibility or plant-death outcome (D45).
     *
     * @param water
     *            positive Water power
     * @param earth
     *            positive Earth power
     * @return multiplier including the ordinary tick opportunity
     * @throws IllegalArgumentException
     *             if either component is absent
     */
    double plantMultiplier(int water, int earth) {
        positive(water);
        positive(earth);
        double multiplier = plantBase + ((double) water + earth) * plantScale;
        if (!Double.isFinite(multiplier)) {
            throw new ArithmeticException("Plant multiplier overflow");
        }
        return multiplier;
    }

    /** Returns whether positive Growth components are within one power unit (D45). */
    boolean balancedGrowth(int water, int earth) {
        positive(water);
        positive(earth);
        return Math.abs((long) water - earth) <= 1;
    }

    /** Returns the forcing multiplier above power two; ordinary growth remains unchanged (D50). */
    int crystalMultiplier(int power) {
        nonnegative(power);
        return Math.addExact(crystalBase, Math.max(0, power - crystalThreshold));
    }

    /** Returns per-five-tick elemental dropout probability, not spell-count overload (D49). */
    double dropoutProbability(int excessPower) {
        nonnegative(excessPower);
        return Math.min(dropoutCap, dropoutPerPoint * excessPower);
    }

    /** Returns armor-bypassing damage points, excluding the first safe Raw unit (D60). */
    int rawDamage(int rawUnits) {
        nonnegative(rawUnits);
        return Math.max(0, rawUnits - 1);
    }

    /**
     * Converts every complete classical-element/life-and-death set to Raw (D47, D56).
     *
     * <p>
     * The paired count represents life-and-death units, each containing one Light and one Dark.
     * Unpaired Light/Dark cannot enter this calculation. Taking the minimum is equivalent to
     * repeatedly converting single sets, without a loop proportional to the amount of mana.
     * Remainders are returned explicitly; the caller owns their D38 release and spell handling.
     *
     * @param input
     *            available whole units, including only already-paired life and death
     * @return Raw units formed and the exact unconverted remainder
     * @throws NullPointerException
     *             if input is null
     */
    RawConversion formRaw(RawIngredients input) {
        java.util.Objects.requireNonNull(input, "input");
        int sets = Math.min(
            Math.min(input.earth(), input.air()),
            Math.min(Math.min(input.fire(), input.water()), input.lifeAndDeath()));
        return new RawConversion(
            sets,
            new RawIngredients(
                input.earth() - sets,
                input.air() - sets,
                input.fire() - sets,
                input.water() - sets,
                input.lifeAndDeath() - sets));
    }

    /**
     * Whole-unit ingredients for D56; unpaired Light and Dark are intentionally not representable.
     */
    record RawIngredients(int earth, int air, int fire, int water, int lifeAndDeath) {
        /** Rejects negative ingredient amounts. */
        RawIngredients {
            nonnegative(earth);
            nonnegative(air);
            nonnegative(fire);
            nonnegative(water);
            nonnegative(lifeAndDeath);
        }

        /** Returns exact elemental power; one paired unit contributes two power units (D47). */
        long elementalPower() {
            return (long) earth + air + fire + water + 2L * lifeAndDeath;
        }
    }

    /**
     * Raw unit yield and remaining ingredients, without discarding excess or construction history.
     */
    record RawConversion(int rawUnits, RawIngredients remainder) {
        /** Rejects invalid externally constructed results. */
        RawConversion {
            nonnegative(rawUnits);
            java.util.Objects.requireNonNull(remainder, "remainder");
        }
    }

    /** Rejects reach below the configured minimum rather than silently repairing bad inputs. */
    void validateReach(int value) {
        if (value < minimumReach) {
            throw new IllegalArgumentException("Reach below minimum: " + value);
        }
    }

    /** Validates a positive whole-unit value. */
    private static void positive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Expected positive units: " + value);
        }
    }

    /** Validates a nonnegative whole-unit value. */
    private static void nonnegative(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Expected nonnegative units: " + value);
        }
    }

    /** Validates a finite fractional parameter. */
    private static void probability(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException("Expected finite fraction in [0, 1]: " + value);
        }
    }
}
