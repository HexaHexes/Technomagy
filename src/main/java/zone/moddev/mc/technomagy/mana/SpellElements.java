package zone.moddev.mc.technomagy.mana;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Typed flavor roles shared by pure interpretation and Minecraft adapters.
 *
 * @param <E>
 *            caller enum containing the seven mana types
 * @param earth
 *            Earth identity
 * @param air
 *            Air identity
 * @param fire
 *            Fire identity
 * @param water
 *            Water identity
 * @param light
 *            Light identity
 * @param dark
 *            Dark identity
 * @param raw
 *            Raw identity, worth six elemental power units
 */
public record SpellElements<E extends Enum<E>>(
    E earth, E air, E fire, E water, E light, E dark, E raw) {
    /** Rejects null, repeated, or mixed-enum roles instead of silently aliasing ingredients. */
    public SpellElements {
        var roles = List.of(earth, air, fire, water, light, dark, raw);
        if (new HashSet<>(roles).size() != 7) {
            throw new IllegalArgumentException("Seven distinct mana roles are required");
        }
        Class<E> type = earth.getDeclaringClass();
        for (E role : roles) {
            Objects.requireNonNull(role);
            if (role.getDeclaringClass() != type) {
                throw new IllegalArgumentException("Mana roles must share an enum class");
            }
        }
    }

    /** Returns whether a caller enum constant has a configured mana role. */
    boolean contains(E element) {
        return element == earth
            || element == air
            || element == fire
            || element == water
            || element == light
            || element == dark
            || element == raw;
    }

    /** Returns the enum class for exact typed quantity maps. */
    Class<E> type() {
        return earth.getDeclaringClass();
    }
}
