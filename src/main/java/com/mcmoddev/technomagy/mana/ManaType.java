package com.mcmoddev.technomagy.mana;

import com.mcmoddev.technomagy.crystal.Flavor;
import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;

/**
 * The seven types of mana: one per {@link Flavor}, plus {@link #RAW}. Raw is its own type, but is
 * treated as an even mix of the six flavors.
 */
public enum ManaType implements StringRepresentable {
    FIRE, EARTH, AIR, WATER, LIGHT, DARK, RAW;

    public static final Codec<ManaType> CODEC = StringRepresentable.fromEnum(ManaType::values);
    private static final ManaType[] VALUES = values();
    /** The six flavor types, in {@link Flavor} order. */
    public static final ManaType[] FLAVORS = {FIRE, EARTH, AIR, WATER, LIGHT, DARK};

    public static ManaType of(Flavor flavor) {
        return FLAVORS[flavor.ordinal()];
    }

    public static ManaType byIndex(int index) {
        return VALUES[index];
    }

    /** The flavor this type carries; empty for Raw. */
    public Optional<Flavor> flavor() {
        return this == RAW ? Optional.empty() : Optional.of(Flavor.values()[ordinal()]);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
