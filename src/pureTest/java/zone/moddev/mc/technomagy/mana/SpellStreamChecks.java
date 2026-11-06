package zone.moddev.mc.technomagy.mana;

/** Standalone checks for ordering, exact split history, and grouped modifier provenance. */
final class SpellStreamChecks {
    /** Test-only elements keep these structural checks independent of Minecraft initialization. */
    private enum Element {
        EARTH, AIR, FIRE, WATER, LIGHT
    }

    /** Runs the agreed structural examples without dependencies. */
    public static void main(String[] args) {
        var earth = SpellStream.element(Element.EARTH, 1);
        var air = SpellStream.element(Element.AIR, 1);
        var fire = SpellStream.element(Element.FIRE, 1);
        var water = SpellStream.element(Element.WATER, 1);
        var erode = SpellStream.merge(earth, air);
        var forge = SpellStream.merge(fire, earth);
        var ordered = SpellStream.merge(erode, forge);
        var sequence = ordered.interpret(SpellStreamChecks::pair);
        require(sequence.size() == 2);
        require(sequence.get(0).isBase(Element.AIR));
        require(sequence.get(1).isBase(Element.FIRE));
        var reversed = SpellStream.merge(forge, erode).interpret(SpellStreamChecks::pair);
        require(reversed.get(0).isBase(Element.FIRE));
        require(ordered.split().get(0) == erode);
        require(ordered.split().get(1) == forge);
        require(
            SpellStream.merge(air, earth)
                .interpret(SpellStreamChecks::pair)
                .getFirst()
                .isBase(Element.EARTH));
        var modified = SpellStream.merge(erode, water).interpret(SpellStreamChecks::pair);
        require(modified.size() == 1);
        require(!modified.getFirst().isBase(Element.WATER));
        require(modified.getFirst().additions().getFirst() == water);
        var grouped = SpellStream.merge(forge, SpellStream.element(Element.AIR, 3));
        require(
            grouped.interpret(SpellStreamChecks::pair).getFirst().additions().getFirst()
                .units() == 3);
        var nested = SpellStream.merge(ordered, SpellStream.merge(water, fire));
        require(nested.interpret(SpellStreamChecks::pair).size() == 3);
        require(nested.interpret(SpellStreamChecks::pair).get(2).isBase(Element.WATER));
        try {
            sequence.clear();
            throw new AssertionError("Mutable interpretation");
        } catch (UnsupportedOperationException expected) {
            // Results must be safe to share.
        }
        var deep = forge;
        for (int i = 0; i < 20000; i++) {
            deep = SpellStream.merge(deep, air);
        }
        require(deep.interpret(SpellStreamChecks::pair).getFirst().additions().size() == 20000);
        var pure = SpellStream.merge(earth, SpellStream.element(Element.EARTH, 2));
        require(pure.interpret(SpellStreamChecks::pair).isEmpty());
        require(pure.split().getFirst() == earth);
        var reinforced = SpellStream.merge(pure, air).interpret(SpellStreamChecks::pair).getFirst();
        require(reinforced.first().units() == 3);
        var groupedPure = SpellStream.merge(forge, SpellStream.merge(air, air))
            .interpret(SpellStreamChecks::pair)
            .getFirst();
        require(groupedPure.additions().size() == 1);
        require(groupedPure.additions().getFirst().units() == 2);
        var longSequence = erode;
        for (int i = 0; i < 5000; i++) {
            longSequence = SpellStream.merge(longSequence, forge);
        }
        require(longSequence.interpret(SpellStreamChecks::pair).size() == 5001);
        System.out.println("Spell stream checks passed");
    }

    /** Accepts distinct test pairs; pure reinforcement must not be mistaken for a new spell. */
    private static void pair(Element first, Element second) {
        if (first == second) {
            throw new IllegalArgumentException("Pure reinforcement is not a spell");
        }
    }

    /** Fails even when Java assertions are disabled. */
    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Stream invariant failed");
        }
    }
}
