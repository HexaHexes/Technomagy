package zone.moddev.mc.technomagy.crystal;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

import zone.moddev.mc.lib.common.types.MMDMaterial;

/**
 * The six flavors of magic (D2). Each one is an MMDLib material whose ID path is the flavor's name,
 * e.g. {@code technomagy:fire}, and its crystal forms are named after it ({@code fire_crystal},
 * {@code fire_budding_crystal}, ...).
 *
 * <p>
 * Only Light and Dark are <em>natural</em>: they generate in the world (D5) and their budding
 * crystals grow unaided at budding amethyst's rate (D7). The four classic elements grow
 * {@value #UNPROMPTED_SLOWDOWN} times slower without mana prompting (D8).
 */
public enum Flavor implements StringRepresentable {
    /** Fire, a classic element. Opposes Water (D11). */
    FIRE(false),
    /** Earth, a classic element. Opposes Air (D11). */
    EARTH(false),
    /** Air, a classic element. Opposes Earth (D11). */
    AIR(false),
    /** Water, a classic element. Opposes Fire (D11). */
    WATER(false),
    /** Light, natural. Opposes Dark (D11). */
    LIGHT(true),
    /** Dark, natural. Opposes Light (D11). */
    DARK(true);

    /**
     * How many times slower than budding amethyst a non-natural budding crystal grows without mana
     * prompting: "about a quarter of amethyst's rate" (D8).
     */
    private static final int UNPROMPTED_SLOWDOWN = 4;

    private final boolean natural;

    Flavor(boolean natural) {
        this.natural = natural;
    }

    /**
     * Whether this flavor occurs naturally (D5) and its budding crystal grows unaided (D7).
     *
     * @return true for Light and Dark
     */
    public boolean natural() {
        return natural;
    }

    /**
     * The factor by which this flavor's budding crystal grows slower than amethyst when unprompted
     * (D7, D8).
     *
     * @return 1 for natural flavors, {@value #UNPROMPTED_SLOWDOWN} otherwise
     */
    public int unpromptedSlowdown() {
        return natural ? 1 : UNPROMPTED_SLOWDOWN;
    }

    /**
     * The flavor's name in IDs and data: its constant name in lower case.
     *
     * @return {@code fire}, {@code earth}, ...
     */
    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The flavor an MMDLib material stands for, matched on the material ID's path.
     *
     * @param material
     *            a crystal material loaded from {@code mmdlib/material/}
     * @return the flavor whose serialized name equals the material ID's path
     * @throws IllegalArgumentException
     *             if the material is not one of the six flavor materials
     * @throws java.util.NoSuchElementException
     *             if the material has no ID
     */
    public static Flavor of(MMDMaterial material) {
        var id = material.name().orElseThrow();
        for (Flavor flavor : values()) {
            if (flavor.getSerializedName().equals(id.getPath())) {
                return flavor;
            }
        }
        throw new IllegalArgumentException("Material " + id + " is not a flavor of magic");
    }
}
