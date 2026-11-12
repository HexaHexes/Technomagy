package zone.moddev.mc.technomagy.spell;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import org.slf4j.Logger;

import zone.moddev.mc.technomagy.mana.ManaType;

/**
 * Server-authoritative, bounded first-cast tuning approved in D69, plus the per-spell elemental
 * costs charged from personal pools through Ability Core's resource pairs (D70, D78).
 *
 * <p>
 * Payment is enabled by default now that carrying a focus unlocks recharge (D77). Costs are one
 * section per spell with one whole-unit value per elemental flavor, defaulting to
 * {@link PlayerSpell#inputUnits()}; the {@code technomagy:spell_cost} resolver prices each cast
 * from these values (D78). A spell whose six costs are all zero is invalid; {@link #configChanged}
 * reports it at load and the cast handler refuses that spell while payment is enabled rather than
 * letting it cast for free (D76).
 */
final class SpellConfig {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Largest configurable per-element cost of one cast, in whole pool units (D47). */
    static final int MAX_COST = 64;
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
    /** Whether casts charge the caster's personal elemental pools (D76). */
    static final ModConfigSpec.BooleanValue PAYMENT_ENABLED;
    /** Per-spell, per-flavor cast costs in whole units; never contains Raw. */
    static final Map<PlayerSpell, Map<ManaType, ModConfigSpec.IntValue>> COSTS;
    /** Game ticks between recharge pulses for unlocked players (D77). */
    static final ModConfigSpec.IntValue RECHARGE_INTERVAL;
    /** Whole units added to one random non-full pool per recharge pulse (D77). */
    static final ModConfigSpec.IntValue RECHARGE_UNITS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
            "First player-cast defaults (D69) and personal-pool payment (D76).");
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
        builder.push("payment");
        PAYMENT_ENABLED = builder
            .comment(
                "Charge each cast from the caster's personal elemental pools (D76).")
            .define("enabled", true);
        builder.comment(
            "Whole units charged per cast from each personal pool. Raw cannot be spent.",
            "Every spell must cost at least one unit in total.")
            .push("costs");
        Map<PlayerSpell, Map<ManaType, ModConfigSpec.IntValue>> costs = new EnumMap<>(
            PlayerSpell.class);
        for (PlayerSpell spell : PlayerSpell.values()) {
            builder.push(spell.id().getPath());
            Map<ManaType, Integer> defaults = spell.inputUnits();
            Map<ManaType, ModConfigSpec.IntValue> values = new EnumMap<>(ManaType.class);
            for (ManaType element : ManaType.FLAVORS) {
                values.put(element, builder.defineInRange(
                    element.getSerializedName(),
                    defaults.getOrDefault(element, 0),
                    0,
                    MAX_COST));
            }
            costs.put(spell, Collections.unmodifiableMap(values));
            builder.pop();
        }
        COSTS = Collections.unmodifiableMap(costs);
        builder.pop(2);
        builder.comment(
            "Personal pool recharge (D77). Unlocked permanently the first time a player has a",
            "casting focus in their inventory. Each pulse adds units to one randomly chosen",
            "active pool that is below capacity.")
            .push("recharge");
        RECHARGE_INTERVAL = builder.defineInRange("intervalTicks", 10, 1, 72000);
        RECHARGE_UNITS = builder.defineInRange("unitsPerPulse", 1, 1, MAX_COST);
        builder.pop();
        SPEC = builder.build();
    }

    private SpellConfig() {
    }

    /**
     * Reports every spell whose configured costs are all zero when the spell config loads or
     * reloads. Such a spell is refused while payment is enabled (see the cast handler), so a typo
     * can never make it free; this message tells the operator why.
     *
     * @param event
     *            a loading or reloading event; other mods' configs are ignored
     */
    static void configChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        for (PlayerSpell spell : PlayerSpell.values()) {
            if (cost(spell).isEmpty()) {
                LOGGER.error("Invalid spell config: every payment.costs.{} value is 0; {} will be "
                    + "refused while payment is enabled. Give it a cost of at least one unit.",
                    spell.id().getPath(), spell.id());
            }
        }
    }

    /**
     * Reads one spell's configured cost for one flavor, including a configured zero: zero-cost
     * flavors are priced at zero and never checked or spent (D76, D78).
     *
     * @param spell
     *            the spell being priced
     * @param element
     *            the flavor to read
     * @return the configured whole-unit cost
     */
    static int configured(PlayerSpell spell, ManaType element) {
        return COSTS.get(spell).get(element).get();
    }

    /**
     * Reads one spell's configured cost.
     *
     * @param spell
     *            the spell being priced
     * @return positive units by flavor, in {@link ManaType#FLAVORS} order; empty when every cost is
     *         zero, which is invalid configuration
     */
    static Map<ManaType, Integer> cost(PlayerSpell spell) {
        Map<ManaType, Integer> cost = new EnumMap<>(ManaType.class);
        COSTS.get(spell).forEach((element, value) -> {
            int units = value.get();
            if (units > 0) {
                cost.put(element, units);
            }
        });
        return cost;
    }
}
