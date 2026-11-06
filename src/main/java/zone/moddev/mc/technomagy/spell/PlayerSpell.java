package zone.moddev.mc.technomagy.spell;

import java.util.Locale;
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

    /** Constructs a commutative base pair of one unit per element. */
    private static SpellStream<ManaType> pair(ManaType first, ManaType second) {
        return SpellStream.merge(SpellStream.element(first, 1), SpellStream.element(second, 1));
    }

    /** Returns the approved stable ability ID; enum order is never sent over the network. */
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath("technomagy", name().toLowerCase(Locale.ROOT));
    }

    /** Returns this spell's player-facing localized title. */
    public Component title() {
        return Component.translatable("spell.technomagy." + id().getPath());
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
