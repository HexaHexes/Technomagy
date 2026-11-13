package zone.moddev.mc.technomagy.spell;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.technomagy.mana.ManaType;
import zone.moddev.mc.technomagy.mana.SpellStream;

/** The sixteen approved first player-cast effects, including the life-and-death utility (D69). */
public enum PlayerSpell {
    /** Earth/Air mining and crushing. */
    ERODE,
    /** Earth/Fire smelting. */
    FORGE,
    /** Fire/Air magical lightning. */
    LIGHTNING,
    /** Fire/Water propulsion. */
    STEAM,
    /** Air/Water obscuring mist. */
    MIST,
    /** Earth/Water impeded movement. */
    CONSTRAINT,
    /** Earth/Light protection. */
    WARD,
    /** Earth/Dark temporary enclosure. */
    ENTOMB,
    /** Air/Light detection. */
    REVEAL,
    /** Air/Dark concealment. */
    SHROUD,
    /** Fire/Light illumination. */
    RADIANCE,
    /** Fire/Dark withering. */
    BLIGHT,
    /** Water/Light healing. */
    MEND,
    /** Water/Dark armor weakening. */
    LEACH,
    /** Light/Water/Earth plant acceleration. */
    GROWTH,
    /** Paired Light/Dark energy delivery. */
    LIFE_AND_DEATH;

    /** Returns the initial unit-power construction interpreted by the shared heuristic engine. */
    public SpellStream<ManaType> stream() {
        return switch (this) {
            case ERODE -> pair(ManaType.EARTH, ManaType.AIR);
            case FORGE -> pair(ManaType.EARTH, ManaType.FIRE);
            case LIGHTNING -> pair(ManaType.FIRE, ManaType.AIR);
            case STEAM -> pair(ManaType.FIRE, ManaType.WATER);
            case MIST -> pair(ManaType.AIR, ManaType.WATER);
            case CONSTRAINT -> pair(ManaType.EARTH, ManaType.WATER);
            case WARD -> pair(ManaType.EARTH, ManaType.LIGHT);
            case ENTOMB -> pair(ManaType.EARTH, ManaType.DARK);
            case REVEAL -> pair(ManaType.AIR, ManaType.LIGHT);
            case SHROUD -> pair(ManaType.AIR, ManaType.DARK);
            case RADIANCE -> pair(ManaType.FIRE, ManaType.LIGHT);
            case BLIGHT -> pair(ManaType.FIRE, ManaType.DARK);
            case MEND -> pair(ManaType.WATER, ManaType.LIGHT);
            case LEACH -> pair(ManaType.WATER, ManaType.DARK);
            case GROWTH ->
                SpellStream.merge(
                    pair(ManaType.WATER, ManaType.LIGHT),
                    SpellStream.element(ManaType.EARTH, 1));
            case LIFE_AND_DEATH -> pair(ManaType.LIGHT, ManaType.DARK);
        };
    }

    /**
     * Totals the elemental units this spell's {@linkplain #stream() construction} consumes. These
     * are the default per-cast costs charged from the caster's personal pools (D76) when payment is
     * enabled; server configuration may override them per spell.
     *
     * <p>
     * Every leaf of the construction tree is one grouped input of whole units (D47), so summing the
     * leaves by element gives exactly the mana the player supplies. For example, Erode is one Earth
     * and one Air; Growth is one Water, one Light, and one Earth. Raw never appears because no
     * player spell is constructed from Raw.
     *
     * @return unmodifiable positive units by element, containing only elements the spell uses
     */
    public Map<ManaType, Integer> inputUnits() {
        Map<ManaType, Integer> units = new EnumMap<>(ManaType.class);
        for (Leaf leaf : leaves()) {
            units.merge(leaf.element(), leaf.units(), Math::addExact);
        }
        return Collections.unmodifiableMap(units);
    }

    /**
     * Lists this spell's elemental inputs in stream order (D82's icon layout): the primary-first
     * leaf walk of the {@linkplain #stream() construction}, so Forge reads Earth then Fire and
     * Growth reads Water, Light then Earth - the order DESIGN.md pins the generated icon's layout
     * slots to. Distinct elements appear once, at first occurrence, because a spell's element count
     * (D82's tab membership) counts distinct tags: a future construction that repeats an element
     * still wears one glyph for it.
     *
     * <p>
     * Unlike {@link #inputUnits()}, which answers an EnumMap in ManaType order - right for totals,
     * wrong for layout - this keeps the construction's own presentation order. The walk itself is
     * {@link #leaves()}, shared with {@link #inputUnits()} so the two cannot drift.
     *
     * @return unmodifiable distinct elements in primary-first stream order (2..4 today)
     */
    List<ManaType> streamOrder() {
        List<ManaType> order = new ArrayList<>();
        for (Leaf leaf : leaves()) {
            if (!order.contains(leaf.element())) {
                order.add(leaf.element());
            }
        }
        return List.copyOf(order);
    }

    /**
     * One elemental input of the construction tree: a leaf's flavor and its grouped whole-unit
     * amount (D47).
     *
     * @param element
     *            the leaf's flavor
     * @param units
     *            the grouped units this leaf contributes
     */
    private record Leaf(ManaType element, int units) {
    }

    /**
     * The {@linkplain #stream() construction's} elemental leaves in primary-first slot order: an
     * explicit-stack walk matching {@link SpellStream}'s own traversal style, pushing each merge's
     * secondary input behind its primary so the primary leaf pops next ({@link SpellStream#split()}
     * answers in slot order). This is the single traversal behind {@link #inputUnits()}, which sums
     * it, and {@link #streamOrder()}, which dedups it, so the two stay in step when
     * {@link SpellStream} gains node kinds.
     *
     * @return the leaves in construction order, repeated elements included
     */
    private List<Leaf> leaves() {
        List<Leaf> order = new ArrayList<>();
        var pending = new ArrayDeque<SpellStream<ManaType>>();
        pending.push(stream());
        while (!pending.isEmpty()) {
            var node = pending.pop();
            if (node.isElement()) {
                order.add(new Leaf(node.element(), node.units()));
            } else {
                var inputs = node.split();
                pending.push(inputs.get(1)); // secondary goes in behind...
                pending.push(inputs.get(0)); // ...so the primary leaf pops next
            }
        }
        return order;
    }

    /** Constructs a commutative base pair of one unit per element. */
    private static SpellStream<ManaType> pair(ManaType first, ManaType second) {
        return SpellStream.merge(SpellStream.element(first, 1), SpellStream.element(second, 1));
    }

    /** Returns the approved stable ability ID; enum order is never sent over the network. */
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath("technomagy", name().toLowerCase(Locale.ROOT));
    }

    /**
     * This spell's translation-key stem: the display-name key every spell string derives from, so
     * the name and its {@code .description} suffix can never drift apart (D84's
     * {@code <display name key>.description} convention).
     *
     * @return the key without any suffix, e.g. {@code spell.technomagy.mend}
     */
    public String nameKey() {
        return "spell.technomagy." + id().getPath();
    }

    /** Returns this spell's player-facing localized title. */
    public Component title() {
        return Component.translatable(nameKey());
    }

    /**
     * Resolves an untrusted selection without selecting a guessed fallback.
     *
     * @param id
     *            submitted ability identity
     * @return known spell, or empty for an unknown identity
     */
    public static Optional<PlayerSpell> find(ResourceLocation id) {
        for (PlayerSpell spell : values()) {
            if (spell.id().equals(id)) {
                return Optional.of(spell);
            }
        }
        return Optional.empty();
    }
}
