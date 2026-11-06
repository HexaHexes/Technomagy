package zone.moddev.mc.technomagy.mana;

/** Dependency-free chemistry checks of pairing, annihilation, Raw conversion, and conservation. */
final class StreamChemistryChecks {
    /** Seven explicit flavor roles for checking chemistry without Minecraft initialization. */
    private enum Element {
        EARTH, AIR, FIRE, WATER, LIGHT, DARK, RAW
    }

    /** Runs chemistry against the agreed D35, D36, D55, D56 and D62 constructions. */
    public static void main(String[] args) {
        var roles = new SpellElements<>(
            Element.EARTH,
            Element.AIR,
            Element.FIRE,
            Element.WATER,
            Element.LIGHT,
            Element.DARK,
            Element.RAW);
        var chemistry = new StreamChemistry<>(roles, new SpellHeuristics(0.15, 3, 2, 0.25, 0.85));
        var pair = merge(input(Element.LIGHT, 1), input(Element.DARK, 1));
        var paired = chemistry.evaluate(pair);
        require(paired.pairedUnits() == 1 && paired.releasedPower() == 0);
        require(paired.stabilizationLevel() == 6 && paired.elementalPower() == 2);
        var annihilated = chemistry.evaluate(
            merge(
                merge(input(Element.WATER, 1), input(Element.LIGHT, 3)),
                input(Element.DARK, 2)));
        require(annihilated.active().get(Element.LIGHT) == 1);
        require(!annihilated.active().containsKey(Element.DARK));
        require(annihilated.releasedPower() == 4 && annihilated.stabilizationLevel() == 5);
        require(annihilated.elementalPower() == 6);
        var classical = merge(
            merge(input(Element.EARTH, 2), input(Element.AIR, 2)),
            merge(input(Element.FIRE, 3), input(Element.WATER, 2)));
        var raw = chemistry.evaluate(
            merge(classical, merge(input(Element.LIGHT, 2), input(Element.DARK, 2))));
        require(raw.rawUnits() == 2 && raw.spellsCancelled());
        require(raw.dropout().get(Element.FIRE) == 1 && raw.active().isEmpty());
        require(raw.elementalPower() == 13);
        var noPair = chemistry.evaluate(
            merge(merge(classical, input(Element.LIGHT, 2)), input(Element.DARK, 2)));
        require(noPair.rawUnits() == 0 && !noPair.spellsCancelled());
        require(noPair.releasedPower() == 4 && noPair.elementalPower() == 13);
        var history = merge(classical, pair);
        require(history.split().get(0) == classical && history.split().get(1) == pair);
        var growth = merge(
            merge(input(Element.LIGHT, 1), input(Element.WATER, 3)),
            input(Element.EARTH, 3));
        var boosted = chemistry.evaluate(merge(growth, pair));
        require(boosted.active().get(Element.LIGHT) == 1);
        require(boosted.pairedUnits() == 1 && boosted.rawUnits() == 0);
        require(boosted.stabilizationLevel() == 6 && boosted.elementalPower() == 9);
        require(chemistry.evaluate(input(Element.RAW, 2)).elementalPower() == 12);
        try {
            raw.active().clear();
            throw new AssertionError("Mutable chemistry result");
        } catch (UnsupportedOperationException expected) {
            // Publication must not expose scratch state.
        }
        var unequal = chemistry.evaluate(merge(input(Element.LIGHT, 3), input(Element.DARK, 2)));
        require(unequal.pairedUnits() == 2 && unequal.active().get(Element.LIGHT) == 1);
        require(unequal.elementalPower() == 5);
        var contact = chemistry.contact(input(Element.LIGHT, 3), input(Element.DARK, 2));
        require(contact.pairedUnits() == 0 && contact.releasedPower() == 4);
        require(contact.active().get(Element.LIGHT) == 1 && contact.stabilizationLevel() == 5);
        require(contact.elementalPower() == 5);
        System.out.println("Stream chemistry checks passed");
    }

    /** Constructs one grouped leaf. */
    private static SpellStream<Element> input(Element type, int units) {
        return SpellStream.element(type, units);
    }

    /** Constructs one ordered merge. */
    private static SpellStream<Element> merge(
        SpellStream<Element> first, SpellStream<Element> second) {
        return SpellStream.merge(first, second);
    }

    /** Checks an invariant independently of JVM assertion settings. */
    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Chemistry invariant failed");
        }
    }
}
