package zone.moddev.mc.technomagy.mana;

import java.util.Map;

/** Dependency-free checks of typed pair resolution and base/modifier interpretation. */
final class SpellInterpreterChecks {
    /** Fixture elements for structural evaluation independent of the game. */
    private enum Element {
        EARTH, AIR, FIRE, WATER, LIGHT
    }

    /** Fixture spell identities. */
    private enum Spell {
        ERODE, FORGE
    }

    /** Fixture action identities. */
    private enum Action {
        MAGNET, RANGE_DOWN, RANGE_UP
    }

    /** Executes ordered interpretation, commutativity, provenance, and overflow checks. */
    public static void main(String[] args) {
        var rules = new SpellInterpreter<>(
            Element.class,
            Map.of(
                new SpellInterpreter.Pair<>(Element.EARTH, Element.AIR),
                Spell.ERODE,
                new SpellInterpreter.Pair<>(Element.FIRE, Element.EARTH),
                Spell.FORGE),
            Map.of(Element.WATER, Action.MAGNET, Element.AIR, Action.RANGE_DOWN));
        var earth = SpellStream.element(Element.EARTH, 1);
        var air = SpellStream.element(Element.AIR, 1);
        var erode = SpellStream.merge(earth, air);
        var modified = SpellStream.merge(
            SpellStream.merge(erode, earth), SpellStream.element(Element.WATER, 3));
        var plan = rules.interpret(modified).getFirst();
        require(plan.identity() == Spell.ERODE);
        require(plan.powers().get(Element.EARTH) == 2);
        require(plan.additions().getFirst().basePower());
        require(plan.additions().get(1).action() == Action.MAGNET);
        require(plan.additions().get(1).units() == 3);
        require(
            rules.interpret(SpellStream.merge(air, earth)).getFirst().identity() == Spell.ERODE);
        var forge = SpellStream.merge(SpellStream.element(Element.FIRE, 1), earth);
        var ordered = rules.interpret(SpellStream.merge(modified, forge));
        require(ordered.size() == 2 && ordered.get(1).identity() == Spell.FORGE);
        var ranged = rules.interpret(SpellStream.merge(forge, air)).getFirst();
        require(!ranged.additions().getFirst().basePower());
        require(ranged.additions().getFirst().action() == Action.RANGE_DOWN);
        try {
            rules.interpret(
                SpellStream.merge(
                    erode, SpellStream.element(Element.EARTH, Integer.MAX_VALUE)));
            throw new AssertionError("Overflow accepted");
        } catch (ArithmeticException expected) {
            // Overflow must not silently wrap spell power.
        }
        try {
            rules.interpret(SpellStream.merge(erode, SpellStream.element(Element.FIRE, 1)));
            throw new AssertionError("Missing modifier accepted");
        } catch (IllegalArgumentException expected) {
            // Missing rules must be explicit rather than invented defaults.
        }
        var rangeRules = new SpellInterpreter<>(
            Element.class,
            Map.of(
                new SpellInterpreter.Pair<>(Element.FIRE, Element.LIGHT),
                Spell.FORGE),
            Map.of(Element.EARTH, Action.RANGE_UP, Element.AIR, Action.RANGE_DOWN));
        var base = SpellStream.merge(
            SpellStream.element(Element.FIRE, 1),
            SpellStream.element(Element.LIGHT, 1));
        var sequentialRange = base;
        for (int i = 0; i < 3; i++) {
            sequentialRange = SpellStream.merge(sequentialRange, earth);
        }
        var calculator = new SpellHeuristics(0.15, 3, 2, 0.25, 0.85);
        require(
            rangeRules.range(
                rangeRules.interpret(sequentialRange).getFirst(),
                5,
                Action.RANGE_UP,
                Action.RANGE_DOWN,
                calculator,
                Math::addExact) == 8);
        var groupedRange = SpellStream.merge(base, SpellStream.element(Element.EARTH, 3));
        require(
            rangeRules.range(
                rangeRules.interpret(groupedRange).getFirst(),
                5,
                Action.RANGE_UP,
                Action.RANGE_DOWN,
                calculator,
                Math::addExact) == 7);
        var reducedRange = SpellStream.merge(base, SpellStream.element(Element.AIR, 3));
        require(
            rangeRules.range(
                rangeRules.interpret(reducedRange).getFirst(),
                5,
                Action.RANGE_UP,
                Action.RANGE_DOWN,
                calculator,
                Math::addExact) == 3);
        var mixedRange = SpellStream.merge(groupedRange, air);
        require(
            rangeRules.range(
                rangeRules.interpret(mixedRange).getFirst(),
                5,
                Action.RANGE_UP,
                Action.RANGE_DOWN,
                calculator,
                Math::addExact) == 6);
        System.out.println("Spell interpreter checks passed");
    }

    /** Checks an invariant even when Java assertions are disabled. */
    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Interpreter invariant failed");
        }
    }
}
