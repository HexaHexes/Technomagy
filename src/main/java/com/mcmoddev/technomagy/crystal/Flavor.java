package com.mcmoddev.technomagy.crystal;

import net.minecraft.util.StringRepresentable;
import zone.moddev.mc.lib.common.types.MMDMaterial;

import java.util.Locale;

/**
 * The six flavors of magic. Each one is an MMDLib material whose ID path is the flavor's
 * name, e.g. {@code technomagy:fire}.
 */
public enum Flavor implements StringRepresentable {
    FIRE(false), EARTH(false), AIR(false), WATER(false), LIGHT(true), DARK(true);

    /** How many times slower than budding amethyst a budding crystal grows without mana prompting. */
    private static final int UNPROMPTED_SLOWDOWN = 4;

    private final boolean natural;

    Flavor(boolean natural) {
        this.natural = natural;
    }

    /** Whether this flavor occurs in the world and its budding crystal grows unaided. */
    public boolean natural() {
        return natural;
    }

    /** The factor by which this flavor's budding crystal grows slower than amethyst when unprompted. */
    public int unpromptedSlowdown() {
        return natural ? 1 : UNPROMPTED_SLOWDOWN;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

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
