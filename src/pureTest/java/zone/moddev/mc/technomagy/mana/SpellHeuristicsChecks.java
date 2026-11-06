package zone.moddev.mc.technomagy.mana;

/**
 * Standalone Java 21 checks of the agreed examples, requiring no game runtime or test dependency.
 */
final class SpellHeuristicsChecks {
    /** Runs calculation checks; throws AssertionError on a mismatch. */
    public static void main(String[] args) {
        SpellHeuristics rules = new SpellHeuristics(0.15, 3, 2, 0.25, 0.85);
        int sequential = 5;
        for (int i = 0; i < 3; i++) {
            sequential = rules.addEarth(sequential, 1);
        }
        equal(8, sequential);
        equal(7, rules.addEarth(5, 3));
        equal(103, rules.addEarth(100, 1));
        equal(109, rules.addEarth(100, 3));
        equal(3, rules.addAir(5, 3));
        equal(2, rules.addAir(rules.addAir(rules.addAir(5, 1), 1), 1));
        equal(2, rules.addAir(2, Integer.MAX_VALUE));
        equal(2.5, rules.plantMultiplier(2, 1));
        equal(4, rules.plantMultiplier(3, 3));
        if (!rules.balancedGrowth(2, 1) || rules.balancedGrowth(3, 1)) {
            throw new AssertionError("Growth balance");
        }
        equal(1, rules.crystalMultiplier(2));
        equal(2, rules.crystalMultiplier(3));
        equal(3, rules.crystalMultiplier(4));
        equal(0, rules.dropoutProbability(0));
        equal(0.25, rules.dropoutProbability(1));
        equal(0.85, rules.dropoutProbability(4));
        equal(0, rules.rawDamage(1));
        equal(1, rules.rawDamage(2));
        rejects(() -> rules.addEarth(5, 0));
        rejects(() -> rules.dropoutProbability(-1));
        rejects(() -> new SpellHeuristics(Double.NaN, 3, 2, 0.25, 0.85));
        rejects(() -> new SpellHeuristics(0.15, 3, 1, 0.25, 0.85));
        var ingredients = new SpellHeuristics.RawIngredients(3, 2, 4, 2, 3);
        var raw = rules.formRaw(ingredients);
        equal(2, raw.rawUnits());
        equal(1, raw.remainder().earth());
        equal(2, raw.remainder().fire());
        equal(1, raw.remainder().lifeAndDeath());
        equal(ingredients.elementalPower(), 6L * raw.rawUnits() + raw.remainder().elementalPower());
        equal(0, rules.formRaw(raw.remainder()).rawUnits());
        equal(0, rules.formRaw(new SpellHeuristics.RawIngredients(1, 1, 1, 1, 0)).rawUnits());
        equal(
            Integer.MAX_VALUE,
            rules.formRaw(
                new SpellHeuristics.RawIngredients(
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE))
                .rawUnits());
        rejects(() -> new SpellHeuristics.RawIngredients(1, 1, 1, 1, -1));
        System.out.println("Spell heuristic checks passed");
    }

    /** Checks exact values for these representable test cases. */
    private static void equal(double expected, double actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    /** Checks rejection of invalid arguments. */
    private static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid argument accepted");
    }
}
