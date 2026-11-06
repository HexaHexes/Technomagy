package zone.moddev.mc.technomagy.mana;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Default elemental spell meanings and modifier action identities (D51, D58-D59, D62-D63).
 *
 * <p>
 * Names describe interpreted actions, not registry IDs or world executors. Alternative catalogues
 * can be supplied to the interpreter without changing stream construction or numerical tuning.
 * Life-and-death is a utility pair and does not add an executable spell to overload counting.
 */
public final class SpellCatalogue {
    private SpellCatalogue() {
    }

    /** Pair-established spells plus the explicit emergent Growth action. */
    public enum Kind {
        /** Earth/Air mining and crushing. */
        ERODE,
        /** Fire/Earth thermal processing. */
        FORGE,
        /** Fire/Air energized air. */
        LIGHTNING,
        /** Fire/Water steam or motion. */
        STEAM,
        /** Air/Water mist or diffusion. */
        MIST,
        /** Earth/Water constraint. */
        CONSTRAINT,
        /** Earth/Light integrity protection. */
        WARD,
        /** Earth/Dark burial or backfilling. */
        ENTOMB,
        /** Air/Light detection. */
        REVEAL,
        /** Air/Dark concealment. */
        SHROUD,
        /** Fire/Light life-supporting illumination and warmth. */
        RADIANCE,
        /** Fire/Dark decomposition. */
        BLIGHT,
        /** Water/Light restoration; Earth can establish Growth. */
        MEND,
        /** Water/Dark extraction through dissolution. */
        LEACH,
        /** Special Water/Light/Earth plant acceleration, not an extra spell. */
        GROWTH
    }

    /** Non-base action categories; execution depends on the spell's supported outputs. */
    public enum Modifier {
        /** Earth range increase. */
        RANGE_UP,
        /** Air range decrease. */
        RANGE_DOWN,
        /** Fire thermal processing. */
        HEAT,
        /** Water collection or magnetic attraction. */
        COLLECT,
        /** Light preservation of form or identity. */
        PRESERVE,
        /** Dark breakdown or destruction. */
        DESTROY
    }

    /** Constructs the complete distinct-pair catalogue and the six non-base action categories. */
    static <E extends Enum<E>> SpellInterpreter<E, Kind, Modifier> create(SpellElements<E> e) {
        var pairs = new HashMap<SpellInterpreter.Pair<E>, Kind>();
        pairs.put(new SpellInterpreter.Pair<>(e.earth(), e.air()), Kind.ERODE);
        pairs.put(new SpellInterpreter.Pair<>(e.earth(), e.fire()), Kind.FORGE);
        pairs.put(new SpellInterpreter.Pair<>(e.fire(), e.air()), Kind.LIGHTNING);
        pairs.put(new SpellInterpreter.Pair<>(e.fire(), e.water()), Kind.STEAM);
        pairs.put(new SpellInterpreter.Pair<>(e.air(), e.water()), Kind.MIST);
        pairs.put(new SpellInterpreter.Pair<>(e.earth(), e.water()), Kind.CONSTRAINT);
        pairs.put(new SpellInterpreter.Pair<>(e.earth(), e.light()), Kind.WARD);
        pairs.put(new SpellInterpreter.Pair<>(e.earth(), e.dark()), Kind.ENTOMB);
        pairs.put(new SpellInterpreter.Pair<>(e.air(), e.light()), Kind.REVEAL);
        pairs.put(new SpellInterpreter.Pair<>(e.air(), e.dark()), Kind.SHROUD);
        pairs.put(new SpellInterpreter.Pair<>(e.fire(), e.light()), Kind.RADIANCE);
        pairs.put(new SpellInterpreter.Pair<>(e.fire(), e.dark()), Kind.BLIGHT);
        pairs.put(new SpellInterpreter.Pair<>(e.water(), e.light()), Kind.MEND);
        pairs.put(new SpellInterpreter.Pair<>(e.water(), e.dark()), Kind.LEACH);
        return new SpellInterpreter<>(
            e.type(),
            pairs,
            Map.of(
                e.earth(),
                Modifier.RANGE_UP,
                e.air(),
                Modifier.RANGE_DOWN,
                e.fire(),
                Modifier.HEAT,
                e.water(),
                Modifier.COLLECT,
                e.light(),
                Modifier.PRESERVE,
                e.dark(),
                Modifier.DESTROY),
            Set.of(new SpellInterpreter.Pair<>(e.light(), e.dark())));
    }
}
