package zone.moddev.mc.technomagy.spell;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative, bounded first-cast tuning approved in D69; no power costs (D70). */
final class SpellConfig {
    static final ModConfigSpec SPEC;
    static final ModConfigSpec.IntValue REACH;
    static final ModConfigSpec.IntValue DURATION;
    static final ModConfigSpec.IntValue SHORT_DURATION;
    static final ModConfigSpec.IntValue MIST_RADIUS;
    static final ModConfigSpec.IntValue REVEAL_RADIUS;
    static final ModConfigSpec.DoubleValue DAMAGE;
    static final ModConfigSpec.DoubleValue IMPULSE;
    static final ModConfigSpec.DoubleValue SHIELD;
    static final ModConfigSpec.DoubleValue HEAL;
    static final ModConfigSpec.DoubleValue ARMOR_REDUCTION;
    static final ModConfigSpec.IntValue FE;
    static final ModConfigSpec.IntValue LIGHT_LEVEL;
    static final ModConfigSpec.IntValue CONSTRAINT_AMPLIFIER;
    static final ModConfigSpec.IntValue BLIGHT_AMPLIFIER;
    static final ModConfigSpec.DoubleValue GROWTH_BASE;
    static final ModConfigSpec.DoubleValue GROWTH_SCALE;
    static final ModConfigSpec.DoubleValue PLANT_MORTALITY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
            "First player-cast defaults. Power charging awaits the Ley Lines integration.");
        REACH = builder.defineInRange("reach", 5, 2, 64);
        DURATION = builder.defineInRange("durationTicks", 200, 1, 72000);
        SHORT_DURATION = builder.defineInRange("shortDurationTicks", 100, 1, 72000);
        MIST_RADIUS = builder.defineInRange("mistRadius", 2, 1, 16);
        REVEAL_RADIUS = builder.defineInRange("revealRadius", 5, 1, 32);
        DAMAGE = builder.defineInRange("lightningDamage", 4.0, 0.1, 1024.0);
        IMPULSE = builder.defineInRange("steamImpulse", 0.6, 0.0, 4.0);
        SHIELD = builder.defineInRange("wardDamagePool", 8.0, 0.1, 1024.0);
        HEAL = builder.defineInRange("mendHealth", 4.0, 0.1, 1024.0);
        ARMOR_REDUCTION = builder.defineInRange("leachArmorReduction", 4.0, 0.0, 1024.0);
        FE = builder.defineInRange("energyPerPairedUnit", 100, 1, Integer.MAX_VALUE);
        LIGHT_LEVEL = builder.defineInRange("radianceLightLevel", 15, 1, 15);
        CONSTRAINT_AMPLIFIER = builder.defineInRange("constraintAmplifier", 1, 0, 10);
        BLIGHT_AMPLIFIER = builder.defineInRange("blightAmplifier", 0, 0, 10);
        GROWTH_BASE = builder.defineInRange("growthBaseMultiplier", 1.0, 1.0, 64.0);
        GROWTH_SCALE = builder.defineInRange("growthPerElementMultiplier", 0.5, 0.0, 16.0);
        PLANT_MORTALITY = builder.defineInRange("imbalancedPlantMortality", 0.1, 0.0, 1.0);
        SPEC = builder.build();
    }

    private SpellConfig() {
    }
}
