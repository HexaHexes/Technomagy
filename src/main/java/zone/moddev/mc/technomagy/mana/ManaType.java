package zone.moddev.mc.technomagy.mana;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

import zone.moddev.mc.technomagy.crystal.Flavor;

/**
 * The seven types of mana (D14): one per {@link Flavor}, plus {@link #RAW}. Raw is its own type,
 * but is treated as an even mix of the six flavors (D15, D25).
 *
 * <p>
 * The flavor constants are declared in {@link Flavor}'s order, so a flavor's ordinal is its mana
 * type's ordinal; {@link #of} and {@link #flavor} rely on that.
 */
public enum ManaType implements StringRepresentable {
    /** Fire mana. */
    FIRE,
    /** Earth mana. */
    EARTH,
    /** Air mana. */
    AIR,
    /** Water mana. */
    WATER,
    /** Light mana. */
    LIGHT,
    /** Dark mana. */
    DARK,
    /** Raw mana: undifferentiated, treated as an even mix of the six flavors (D15). */
    RAW;

    /** Encodes a type by its {@linkplain #getSerializedName() serialized name}. */
    public static final Codec<ManaType> CODEC = StringRepresentable.fromEnum(ManaType::values);

    private static final ManaType[] VALUES = values();

    /** The six flavor types, in {@link Flavor} order. Immutable. */
    public static final List<ManaType> FLAVORS = List.of(FIRE, EARTH, AIR, WATER, LIGHT, DARK);

    /**
     * The mana type of a flavor.
     *
     * @param flavor
     *            a flavor of magic
     * @return the matching flavor type; never {@link #RAW}
     */
    public static ManaType of(Flavor flavor) {
        return FLAVORS.get(flavor.ordinal());
    }

    /**
     * The type with the given ordinal, without the array copy {@link #values()} makes.
     *
     * @param index
     *            an ordinal, 0 to 6
     * @return the type
     * @throws ArrayIndexOutOfBoundsException
     *             if the index is out of range
     */
    public static ManaType byIndex(int index) {
        return VALUES[index];
    }

    /**
     * The flavor this type carries.
     *
     * @return the flavor, or empty for {@link #RAW}
     */
    public Optional<Flavor> flavor() {
        return this == RAW ? Optional.empty() : Optional.of(Flavor.values()[ordinal()]);
    }

    /**
     * The type's name in saved data: its constant name in lower case.
     *
     * @return {@code fire}, ..., {@code raw}
     */
    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
