package zone.moddev.mc.technomagy.mana;

import java.util.HashMap;
import java.util.Map;

/** Exercises only the public evaluation API, including required tuning and immutable snapshots. */
final class SpellEngineChecks {
    /** API client flavor identities independent of Minecraft. */
    private enum Element {
        EARTH, AIR, FIRE, WATER, LIGHT, DARK, RAW
    }

    /** Runs complete evaluation/preparation and invalid-tuning checks. */
    public static void main(String[] args) {
        var roles = new SpellElements<>(
            Element.EARTH,
            Element.AIR,
            Element.FIRE,
            Element.WATER,
            Element.LIGHT,
            Element.DARK,
            Element.RAW);
        var engine = new SpellEngine<>(roles, SpellEngine.Numbers.standard());
        var base = pair(Element.FIRE, Element.LIGHT);
        var sequential = base;
        for (int i = 0; i < 3; i++) {
            sequential = SpellStream.merge(sequential, input(Element.EARTH, 1));
        }
        var settings = new SpellEngine.SpellSettings<Element>(
            powers -> 5, Math::addExact, effect -> Map.of(Element.FIRE, 2));
        var tuning = new SpellEngine.ExecutionTuning<>(
            Map.of(SpellCatalogue.Kind.RADIANCE, settings),
            result -> 0.4,
            effect -> 0.2);
        var prepared = engine.prepare(sequential, tuning);
        require(prepared.effects().getFirst().reach() == 8);
        require(prepared.effects().getFirst().manaCost().get(Element.FIRE) == 2);
        require(prepared.overloadProbability() == 0);
        var grouped = SpellStream.merge(base, input(Element.EARTH, 3));
        require(engine.prepare(grouped, tuning).effects().getFirst().reach() == 7);
        require(
            engine.prepare(SpellStream.merge(grouped, input(Element.AIR, 1)), tuning)
                .effects()
                .getFirst()
                .reach() == 6);
        var raw = SpellStream.merge(
            SpellStream.merge(
                pair(Element.EARTH, Element.AIR),
                pair(Element.FIRE, Element.WATER)),
            pair(Element.LIGHT, Element.DARK));
        var emptyTuning = new SpellEngine.ExecutionTuning<Element>(Map.of(), result -> 0,
            effect -> 0);
        require(engine.prepare(raw, emptyTuning).effects().isEmpty());
        var growth = SpellStream.merge(pair(Element.LIGHT, Element.WATER), input(Element.EARTH, 1));
        var growthSettings = new SpellEngine.SpellSettings<Element>(
            powers -> 4, Math::addExact, effect -> Map.of(Element.WATER, 1));
        var growthTuning = new SpellEngine.ExecutionTuning<>(
            Map.of(SpellCatalogue.Kind.GROWTH, growthSettings),
            result -> 0,
            effect -> 0.3);
        require(
            engine.prepare(growth, growthTuning).effects().getFirst().plantDeathProbability() == 0);
        var imbalanced = SpellStream.merge(growth, input(Element.WATER, 2));
        require(
            engine.prepare(imbalanced, growthTuning)
                .effects()
                .getFirst()
                .plantDeathProbability() == 0.3);
        var failureTuning = new SpellEngine.ExecutionTuning<>(
            Map.of(SpellCatalogue.Kind.GROWTH, growthSettings),
            result -> 0,
            effect -> Double.NaN);
        rejects(() -> engine.prepare(imbalanced, failureTuning));
        rejects(() -> engine.prepare(base, emptyTuning));
        require(engine.rawDamage(2) == 1 && engine.crystalMultiplier(4) == 3);
        var dropout = engine.dropout(engine.evaluate(input(Element.WATER, 5))).getFirst();
        require(
            dropout.excessPower() == 3
                && dropout.severity() == SpellEngine.Severity.WORLD_EFFECTS);
        require(dropout.probability() == 0.75);
        require(
            engine.dropout(engine.evaluate(input(Element.WATER, 3))).getFirst()
                .severity() == SpellEngine.Severity.PARTICLES);
        require(
            engine.dropout(engine.evaluate(input(Element.WATER, 4))).getFirst()
                .severity() == SpellEngine.Severity.DROPLETS);
        var contact = engine.contact(input(Element.LIGHT, 3), input(Element.DARK, 2));
        require(contact.releasedPower() == 4 && contact.pairedUnits() == 0);
        var defaults = SpellEngine.defaultCatalogue(roles);
        var customized = new HashMap<>(defaults.modifiers());
        customized.put(Element.EARTH, SpellCatalogue.Modifier.COLLECT);
        var customEngine = new SpellEngine<>(
            roles,
            SpellEngine.Numbers.standard(),
            new SpellEngine.Catalogue<>(defaults.pairs(), customized));
        require(customEngine.prepare(grouped, tuning).effects().getFirst().reach() == 5);
        var tunedEngine = new SpellEngine<>(roles,
            new SpellEngine.Numbers(0.2, 3, 2, 0.1, 0.7, 1, 1, 1, 2));
        require(
            tunedEngine
                .evaluate(growth)
                .effects()
                .getFirst()
                .growth()
                .orElseThrow()
                .tickMultiplier() == 3);
        try {
            prepared.effects().getFirst().manaCost().clear();
            throw new AssertionError("Mutable public snapshot");
        } catch (UnsupportedOperationException expected) {
            // Public plans must not expose mutable state.
        }
        var reinforcedWard = SpellStream.merge(input(Element.EARTH, 1), input(Element.LIGHT, 3));
        var enemyDark = SpellStream.merge(input(Element.WATER, 1), input(Element.DARK, 2));
        var wardSettings = new SpellEngine.SpellSettings<Element>(
            powers -> powers.get(Element.LIGHT) + 2,
            Math::addExact,
            effect -> Map.of(Element.LIGHT, effect.powers().get(Element.LIGHT)));
        var wardTuning = new SpellEngine.ExecutionTuning<>(
            Map.of(SpellCatalogue.Kind.WARD, wardSettings), result -> 0, effect -> 0);
        var remainingWard = engine.prepare(SpellStream.merge(reinforcedWard, enemyDark),
            wardTuning);
        require(remainingWard.effects().size() == 1);
        require(remainingWard.effects().getFirst().reach() == 3);
        require(remainingWard.effects().getFirst().manaCost().get(Element.LIGHT) == 1);
        var halfReach = new SpellEngine.SpellSettings<Element>(
            powers -> 10, Math::addExact, effect -> Map.of());
        var halfTuning = new SpellEngine.ExecutionTuning<>(
            Map.of(SpellCatalogue.Kind.RADIANCE, halfReach), result -> 0, effect -> 0);
        require(
            engine.prepare(SpellStream.merge(base, input(Element.EARTH, 1)), halfTuning)
                .effects()
                .getFirst()
                .reach() == 12);
        var erode = pair(Element.EARTH, Element.AIR);
        var denatured = engine.evaluate(
            SpellStream.merge(
                SpellStream.merge(erode, input(Element.LIGHT, 1)),
                input(Element.DARK, 1)))
            .effects()
            .getFirst();
        require(!denatured.cancelled() && denatured.additions().size() == 2);
        require(denatured.activeAdditions().isEmpty());
        var mend = pair(Element.WATER, Element.LIGHT);
        var triple = SpellStream.merge(SpellStream.merge(mend, mend), mend);
        var mendSettings = new SpellEngine.SpellSettings<Element>(
            powers -> 5, Math::addExact, effect -> Map.of(Element.WATER, 1));
        var overloadTuning = new SpellEngine.ExecutionTuning<>(
            Map.of(SpellCatalogue.Kind.MEND, mendSettings),
            result -> result.chemistry().pairedUnits() > 0 ? 0 : 0.4,
            effect -> 0);
        require(engine.prepare(triple, overloadTuning).overloadProbability() == 0.4);
        require(
            engine.prepare(
                SpellStream.merge(
                    triple, pair(Element.LIGHT, Element.DARK)),
                overloadTuning)
                .overloadProbability() == 0);
        System.out.println("Spell engine API checks passed");
    }

    /** Builds one base pair. */
    private static SpellStream<Element> pair(Element first, Element second) {
        return SpellStream.merge(input(first, 1), input(second, 1));
    }

    /** Builds one grouped input. */
    private static SpellStream<Element> input(Element type, int amount) {
        return SpellStream.element(type, amount);
    }

    /** Checks an invariant independently of JVM assertions. */
    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Public engine invariant failed");
        }
    }

    /** Checks rejection of invalid required tuning. */
    private static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid tuning accepted");
    }
}
