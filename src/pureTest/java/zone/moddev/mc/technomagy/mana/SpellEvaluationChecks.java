package zone.moddev.mc.technomagy.mana;

/** End-to-end checks of the default catalogue, Growth replacement, cancellation, and utilities. */
final class SpellEvaluationChecks {
    /** Pure flavor fixture. */
    private enum Element {
        EARTH, AIR, FIRE, WATER, LIGHT, DARK, RAW
    }

    /**
     * Runs all-pair coverage and compound interpretation against the concrete default catalogue.
     */
    public static void main(String[] args) {
        var roles = new SpellElements<>(
            Element.EARTH,
            Element.AIR,
            Element.FIRE,
            Element.WATER,
            Element.LIGHT,
            Element.DARK,
            Element.RAW);
        var numbers = new SpellHeuristics(0.15, 3, 2, 0.25, 0.85);
        var catalogue = SpellCatalogue.create(roles);
        var evaluate = new SpellEvaluation<>(roles, numbers, catalogue);
        for (Element first : Element.values()) {
            for (Element second : Element.values()) {
                if (first == Element.RAW || second == Element.RAW || first == second) {
                    continue;
                }
                var plans = catalogue.interpret(pair(first, second));
                require(
                    plans.size() == ((first == Element.LIGHT && second == Element.DARK)
                        || (first == Element.DARK
                            && second == Element.LIGHT)
                                ? 0
                                : 1));
            }
        }
        var mend = pair(Element.LIGHT, Element.WATER);
        require(evaluate.evaluate(mend).effects().getFirst().kind() == SpellCatalogue.Kind.MEND);
        var growth = SpellStream.merge(mend, input(Element.EARTH, 1));
        var grown = evaluate.evaluate(growth).effects().getFirst();
        require(grown.kind() == SpellCatalogue.Kind.GROWTH && !grown.cancelled());
        require(grown.growth().orElseThrow().tickMultiplier() == 2);
        var boosted = evaluate
            .evaluate(SpellStream.merge(growth, pair(Element.LIGHT, Element.DARK)));
        require(boosted.effects().size() == 1 && boosted.chemistry().stabilizationLevel() == 6);
        var unbalanced = evaluate.evaluate(SpellStream.merge(growth, input(Element.WATER, 2)));
        require(!unbalanced.effects().getFirst().growth().orElseThrow().balanced());
        var erode = pair(Element.EARTH, Element.AIR);
        var forge = pair(Element.EARTH, Element.FIRE);
        var raw = evaluate.evaluate(
            SpellStream.merge(
                SpellStream.merge(erode, pair(Element.FIRE, Element.WATER)),
                pair(Element.LIGHT, Element.DARK)));
        require(raw.chemistry().rawUnits() == 1 && raw.effects().isEmpty());
        var lightSpell = pair(Element.EARTH, Element.LIGHT);
        var darkSpell = pair(Element.WATER, Element.DARK);
        var annihilated = evaluate.evaluate(SpellStream.merge(lightSpell, darkSpell));
        require(
            annihilated.effects().get(0).cancelled()
                && annihilated.effects().get(1).cancelled());
        require(annihilated.chemistry().stabilizationLevel() == 5);
        var twoLight = SpellStream.merge(lightSpell, pair(Element.AIR, Element.LIGHT));
        var partial = evaluate.evaluate(SpellStream.merge(twoLight, darkSpell));
        require(!partial.effects().get(0).cancelled());
        require(partial.effects().get(1).cancelled() && partial.effects().get(2).cancelled());
        var ordered = evaluate.evaluate(SpellStream.merge(erode, forge));
        require(ordered.effects().get(0).kind() == SpellCatalogue.Kind.ERODE);
        require(ordered.effects().get(1).kind() == SpellCatalogue.Kind.FORGE);
        try {
            evaluate.evaluate(
                SpellStream.merge(SpellStream.merge(erode, forge), input(Element.WATER, 1)));
            throw new AssertionError("Post-join modification accepted");
        } catch (IllegalStateException expected) {
            // D62 requires individual modifiers before joining spells.
        }
        var forcedWater = evaluate.evaluate(
            SpellStream.merge(
                input(Element.WATER, 4), pair(Element.LIGHT, Element.DARK)));
        require(
            forcedWater.effects().isEmpty()
                && forcedWater.chemistry().stabilizationLevel() == 6);
        require(forcedWater.chemistry().active().get(Element.WATER) == 4);
        System.out.println("Spell evaluation checks passed");
    }

    /** Builds a one-unit base pair. */
    private static SpellStream<Element> pair(Element first, Element second) {
        return SpellStream.merge(input(first, 1), input(second, 1));
    }

    /** Builds a grouped elemental input. */
    private static SpellStream<Element> input(Element element, int units) {
        return SpellStream.element(element, units);
    }

    /** Checks results even when assertions are disabled. */
    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Evaluation invariant failed");
        }
    }
}
