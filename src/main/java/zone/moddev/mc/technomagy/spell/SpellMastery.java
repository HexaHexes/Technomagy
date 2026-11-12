package zone.moddev.mc.technomagy.spell;

import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.bitwisemadness.abilitycore.api.AbilityPresentation;
import org.bitwisemadness.abilitycore.api.MasteryAwards;
import org.bitwisemadness.abilitycore.api.MasteryAxis;
import org.bitwisemadness.abilitycore.api.MasteryDeclarations;
import org.bitwisemadness.abilitycore.api.MasteryEffects;
import org.bitwisemadness.abilitycore.api.MasteryGainHandlers;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.bitwisemadness.abilitycore.api.MasteryTarget;
import org.bitwisemadness.abilitycore.api.MasteryTrigger;
import org.bitwisemadness.abilitycore.api.MasteryView;
import org.bitwisemadness.abilitycore.api.PresentationField;
import org.bitwisemadness.abilitycore.api.PresentationProviders;
import org.bitwisemadness.abilityhotbar.compat.CoreMasteryCompat;

/**
 * Spell mastery (D79, D80, D81): declares every player spell's Ability Core mastery contract and
 * owns every award site, every effect read and the menu presentation Technomagy performs.
 *
 * <p>
 * Each spec routes grants through the D80 share list - the spell's elements plus its one role
 * school, with the self remainder kept on the spell itself - takes its gain from
 * {@link #EFFORT_HANDLER}, which prices effort linearly from the server-configured cast cost and
 * multiplies it by the spell's own-level effort surge, and declares the {@code POWER} and
 * {@code DURATION} axes whose multipliers this class reads back for the effect sites in
 * {@link SpellRuntime}. The static base gain is the loud fallback core applies if the handler name
 * ever fails to resolve.
 * </p>
 *
 * <p>
 * Declaration runs at mod construction from {@link SpellContent#register}, beside the pool resource
 * pairs (D78), before Ability Core's registry events fire; core rejects a duplicate declaration
 * loudly, so this class is the only owner of the sixteen spell specs and of the handler name.
 * </p>
 */
final class SpellMastery {

    /** Named gain handler: configured cost total times the trigger weight (D79). */
    private static final String EFFORT_HANDLER = "technomagy:spell_effort";

    /**
     * Effort priced for a spell with no configured positive cost, and the spec's static base gain
     * (D79). A misconfigured spell still progresses rather than silently granting nothing.
     */
    private static final double BASE_EFFORT = 2.0;

    /** One cast is worth one unit of effort (D79, initial value, to be tuned). */
    private static final double ACTIVATION_WEIGHT = 1.0;

    /** A landed strike is worth half a cast (D79, initial value, to be tuned). */
    private static final double HIT_WEIGHT = 0.5;

    /** A killing strike is worth a full cast (D79, initial value, to be tuned). */
    private static final double KILL_WEIGHT = 1.0;

    /** Fallback weight for a non-raw CUSTOM grant (D79, initial value, to be tuned). */
    private static final double CUSTOM_WEIGHT = 1.0;

    /**
     * Share of every grant routed to the spell's element buckets, split evenly across them (D80,
     * initial value, to be tuned). The self remainder keeps the rest.
     */
    private static final double ELEMENT_SHARE = 0.4;

    /** Share of every grant routed to the spell's discipline bucket (D80, to be tuned). */
    private static final double DISCIPLINE_SHARE = 0.15;

    /** Effort surge at own level 2 (D80, initial value, to be tuned). */
    private static final double SURGE_LEVEL_2 = 1.1;

    /** Effort surge at own level 3 (D80, initial value, to be tuned). */
    private static final double SURGE_LEVEL_3 = 1.25;

    /**
     * Effort surge at own level 4 and beyond (D80, initial value, to be tuned): the shipped level
     * table caps at 4, and holding this value for any deeper level keeps a future longer table from
     * ever dipping below a surge a player already reached.
     */
    private static final double SURGE_LEVEL_4 = 1.5;

    /** The menu meter's provider-owned label key (D81); hotbar localises with its own fallback. */
    private static final String METER_LABEL = "technomagy.mastery.progress";

    /** The meter's value text key: the localised own-level text drawn inside the bar (D81). */
    private static final String LEVEL_VALUE = "technomagy.mastery.level_value";

    /** The five role schools every spell belongs to exactly once (D80), as bucket targets. */
    private static final MasteryTarget SHAPING = school("shaping");

    /** See {@link #SHAPING}. */
    private static final MasteryTarget STRIKING = school("striking");

    /** See {@link #SHAPING}. */
    private static final MasteryTarget WARDING = school("warding");

    /** See {@link #SHAPING}. */
    private static final MasteryTarget VEILING = school("veiling");

    /** See {@link #SHAPING}. */
    private static final MasteryTarget VITALITY = school("vitality");

    private SpellMastery() {
    }

    /**
     * Declares the effort gain handler and all sixteen spell specs: the D80 share list (elements
     * then discipline), the D79 trigger set - Lightning adds {@code HIT}/{@code KILL}, Ward adds
     * {@code CUSTOM}, every spell the {@code ACTIVATION} core fires itself - and the D80 effect
     * axes. An award for an undeclared trigger is ignored by core (D79); the declared axes let
     * core's default curve scale the spell's magnitudes and durations through the aggregate level.
     * The D81 menu presentation registers beside them, one provider function shared by all sixteen
     * ids (core enforces one owner per id).
     */
    static void declare() {
        MasteryGainHandlers.declare(EFFORT_HANDLER, SpellMastery::gain);
        for (PlayerSpell spell : PlayerSpell.values()) {
            MasterySpec spec = MasterySpec.gained(BASE_EFFORT).withGainHandler(EFFORT_HANDLER);
            List<MasteryTarget> elements = elements(spell);
            if (elements.isEmpty()) {
                // A spell with no elements has no elemental mastery to route to; failing here
                // names the spell instead of letting a share validation misreport the problem.
                throw new IllegalStateException(
                    "Spell " + spell.id() + " declares no elements for its D80 shares");
            }
            double elementShare = ELEMENT_SHARE / elements.size();
            for (MasteryTarget element : elements) {
                spec = spec.withShare(element, elementShare);
            }
            spec = spec.withShare(discipline(spell), DISCIPLINE_SHARE);
            spec = switch (spell) {
                case LIGHTNING -> spec.withTriggers(MasteryTrigger.HIT, MasteryTrigger.KILL)
                    .withEffect(MasteryAxis.POWER);
                case WARD -> spec.withTriggers(MasteryTrigger.CUSTOM)
                    .withEffect(MasteryAxis.POWER)
                    .withEffect(MasteryAxis.DURATION);
                case MIST, CONSTRAINT, ENTOMB, REVEAL, SHROUD, RADIANCE, BLIGHT, LEACH ->
                    spec.withEffect(MasteryAxis.DURATION);
                case STEAM, MEND, GROWTH, LIFE_AND_DEATH -> spec.withEffect(MasteryAxis.POWER);
                case ERODE, FORGE -> spec;
            };
            MasteryDeclarations.declare(spell.id(), spec);
            PresentationProviders.declare(spell.id(), SpellMastery::present);
        }
    }

    /**
     * The menu presentation for one spell (D81), answering core's provider contract: hotbar's
     * answered mastery snapshot decides, so an unanswered exchange or a spell core never declared
     * yields an empty presentation - no meter, no rows, never a guessed zero - and an answered one
     * yields the mastery meter. The snapshot reads ({@link CoreMasteryCompat}) are the client's
     * stored copy of the server's answer, so this stays side-disciplined: synced values only, and
     * the player argument core passes is unused because the snapshot already names the local
     * player's state.
     *
     * @param player
     *            the player whose menu is rendering (the client's local player in production)
     * @param abilityId
     *            the spell being presented
     * @return the presentation; never null, so core never logs a contract violation for it
     */
    static AbilityPresentation present(Player player, ResourceLocation abilityId) {
        return presentation(
            CoreMasteryCompat.answered() && CoreMasteryCompat.declared(abilityId),
            CoreMasteryCompat.levelOf(abilityId), CoreMasteryCompat.progressOf(abilityId));
    }

    /**
     * The pure half of {@link #present}: the answered snapshot's numbers in, one presentation out,
     * with no client state touched - the seam the GameTests drive with fabricated answers (D81).
     * Unanswered reads an empty presentation; answered reads the meter with the localised level
     * text inside and the in-level fill on the bar. Toggle is always null because no spell is
     * togglable, and there are no text fields because nothing else is both live and client-known.
     *
     * @param answered
     *            whether hotbar's snapshot has answered for this spell
     * @param level
     *            the snapshot's own-target level (read only when answered)
     * @param progress
     *            the snapshot's in-level fill in {@code [0, 1]} (read only when answered)
     * @return the presentation; never null
     */
    static AbilityPresentation presentation(boolean answered, int level, double progress) {
        if (!answered) {
            return new AbilityPresentation(null, List.of());
        }
        PresentationField meter = PresentationField.progress(METER_LABEL, (float) progress,
            Component.translatable(LEVEL_VALUE, level).getString());
        return new AbilityPresentation(null, List.of(meter));
    }

    /**
     * Grants Lightning's strike result (D79): {@code HIT} when the bolt damaged its target,
     * {@code KILL} when it brought that target to zero health. One strike is one award, so a kill
     * never grants {@code HIT} as well. Inert for an undeclared trigger, a disabled system, or a
     * null player, as core defines.
     *
     * @param caster
     *            the player who cast the bolt
     * @param killed
     *            whether the strike left the target at zero health
     */
    static void awardStrike(ServerPlayer caster, boolean killed) {
        MasteryAwards.award(caster, PlayerSpell.LIGHTNING.id(),
            killed ? MasteryTrigger.KILL : MasteryTrigger.HIT);
    }

    /**
     * Grants Ward's absorption as a raw amount of absorbed damage (D79): the gain handler is
     * skipped for this grant - and with it the D80 effort surge, so absorption stays literally raw
     * - while share routing and the global gain multiplier still apply. An offline or removed
     * caster resolves to null and grants nothing.
     *
     * @param level
     *            the ward's level, used to resolve the caster's current player
     * @param caster
     *            the UUID of the player who cast the ward owning the pool
     * @param absorbed
     *            the health points this hit took from the pool, in points; zero or less grants
     *            nothing
     */
    static void awardAbsorption(ServerLevel level, UUID caster, double absorbed) {
        if (absorbed <= 0.0) {
            return;
        }
        MasteryAwards.award(level.getServer().getPlayerList().getPlayer(caster),
            PlayerSpell.WARD.id(), MasteryTrigger.CUSTOM, absorbed);
    }

    /**
     * Scales an int tick count by one spell's DURATION multiplier (D80), rounded to the nearest
     * half-up tick - the design's rounding rule for integer sinks, so a nominal duration never
     * changes when the multiplier is {@code 1.0}.
     *
     * @param player
     *            the caster whose mastery the duration scales with
     * @param spell
     *            the spell being cast
     * @param ticks
     *            the configured duration in game ticks
     * @return the scaled duration in game ticks
     */
    static int durationTicks(ServerPlayer player, PlayerSpell spell, int ticks) {
        return (int) Math.round(ticks * MasteryEffects.durationMultiplier(player, spell.id()));
    }

    /**
     * Scales a float magnitude by one spell's POWER multiplier (D80) - the shared read behind the
     * damage, impulse, shield and heal sites, so one curve change cannot leave them diverging.
     *
     * @param player
     *            the caster whose mastery the effect scales with
     * @param spell
     *            the spell being cast
     * @param magnitude
     *            the configured float magnitude
     * @return the scaled magnitude, unrounded (D80's float-magnitude rule)
     */
    static double power(ServerPlayer player, PlayerSpell spell, double magnitude) {
        return magnitude * MasteryEffects.powerMultiplier(player, spell.id());
    }

    /**
     * Scales an int sink by one spell's POWER multiplier (D80), rounded nearest half-up exactly
     * like {@link #durationTicks} - Life & Death's configured energy amount.
     *
     * @param player
     *            the caster whose mastery the effect scales with
     * @param spell
     *            the spell being cast
     * @param units
     *            the configured whole-unit amount
     * @return the scaled amount in whole units
     */
    static int powerUnits(ServerPlayer player, PlayerSpell spell, int units) {
        return (int) Math.round(units * MasteryEffects.powerMultiplier(player, spell.id()));
    }

    /**
     * Prices one grant linearly from the spell's configured cost total times the trigger's weight
     * (D79), read whether or not payment is enabled - the cost table is the spell's intrinsic price
     * tag - and multiplies the result by the spell's own-level effort surge (D80). A spell with no
     * positive configured cost prices at {@link #BASE_EFFORT} rather than granting nothing; an id
     * this class never declared throws, which core logs loudly (D79).
     *
     * @param player
     *            the granting player; effort comes from configuration, the surge from their own
     *            mastery of {@code source}
     * @param source
     *            the ability id granting
     * @param trigger
     *            the trigger granting
     * @return XP for this grant, before routing and the global gain multiplier
     * @throws IllegalArgumentException
     *             for an id outside {@link PlayerSpell}
     */
    private static double gain(ServerPlayer player, ResourceLocation source,
        MasteryTrigger trigger) {
        double weight = switch (trigger) {
            case ACTIVATION -> ACTIVATION_WEIGHT;
            case HIT -> HIT_WEIGHT;
            case KILL -> KILL_WEIGHT;
            // Ward absorption grants raw absorbed damage and never reaches this handler; the
            // branch only answers a future non-raw CUSTOM award, priced like a cast.
            case CUSTOM -> CUSTOM_WEIGHT;
        };
        return effort(source) * weight * surge(player, source);
    }

    /**
     * The effort surge for {@code source}'s own level at grant time (D80): level 0-1 no surge, then
     * {@link #SURGE_LEVEL_2}, {@link #SURGE_LEVEL_3} and {@link #SURGE_LEVEL_4} from there. The
     * level is read before the grant is written, so one award is priced exactly once. A null player
     * yields an empty view (level 0) and no surge.
     *
     * @param player
     *            the granting player
     * @param source
     *            the ability id granting
     * @return a multiplier {@code >= 1} applied to the effort before routing
     */
    private static double surge(ServerPlayer player, ResourceLocation source) {
        return switch (MasteryView.of(player, source).ownLevel()) {
            case 0, 1 -> 1.0;
            case 2 -> SURGE_LEVEL_2;
            case 3 -> SURGE_LEVEL_3;
            default -> SURGE_LEVEL_4;
        };
    }

    /**
     * The configured cost total of {@code source}'s spell (D79), or {@link #BASE_EFFORT} when every
     * configured cost is zero, so a misconfigured spell still progresses rather than granting
     * nothing. An id that is not one of the sixteen spells throws instead: core logs the
     * misbehaving handler and falls back to the static base gain it owns, so a foreign spec
     * borrowing this handler is loud rather than silently priced at 2.0 (D79's recorded decision).
     *
     * @param source
     *            the ability id being priced
     * @return positive effort in cost units
     * @throws IllegalArgumentException
     *             for an id this class never declared
     */
    private static double effort(ResourceLocation source) {
        PlayerSpell spell = PlayerSpell.find(source)
            .orElseThrow(() -> new IllegalArgumentException("Gain handler " + EFFORT_HANDLER
                + " prices Technomagy spells only; " + source + " must name its own handler"));
        double total = SpellConfig.cost(spell).values().stream()
            .mapToDouble(Integer::doubleValue).sum();
        return total > 0.0 ? total : BASE_EFFORT;
    }

    /**
     * The element buckets one spell shares to (D80): one {@code SUBCATEGORY} target per element of
     * its construction, id {@code technomagy:<flavor>} - the same id as the personal pool that
     * element pays from (D70/D78), so elemental mastery and the pools name one element identically.
     *
     * @param spell
     *            the spell whose construction supplies the elements
     * @return the element targets in the spell's element enumeration order
     */
    private static List<MasteryTarget> elements(PlayerSpell spell) {
        return spell.inputUnits().keySet().stream()
            .map(element -> MasteryTarget.subcategory(PlayerManaPools.currency(element)))
            .toList();
    }

    /**
     * The discipline bucket one spell shares to (D80): its single fixed role school. Membership is
     * the exhaustive switch below - every spell belongs to exactly one school, so the discipline
     * share never splits.
     *
     * @param spell
     *            the spell whose school is looked up
     * @return the school's {@code SUBCATEGORY} target
     */
    private static MasteryTarget discipline(PlayerSpell spell) {
        return switch (spell) {
            case ERODE, FORGE, GROWTH -> SHAPING;
            case LIGHTNING, BLIGHT, STEAM -> STRIKING;
            case WARD, ENTOMB, CONSTRAINT -> WARDING;
            case MIST, SHROUD, REVEAL, RADIANCE -> VEILING;
            case MEND, LEACH, LIFE_AND_DEATH -> VITALITY;
        };
    }

    /**
     * The {@code SUBCATEGORY} target for one school id in the {@code technomagy} namespace.
     *
     * @param path
     *            the school's id path, e.g. {@code shaping}
     * @return the bucket target every spell of that school shares to
     */
    private static MasteryTarget school(String path) {
        return MasteryTarget.subcategory(ResourceLocation.fromNamespaceAndPath("technomagy", path));
    }
}
