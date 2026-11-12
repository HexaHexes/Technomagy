package zone.moddev.mc.technomagy.spell;

import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.bitwisemadness.abilitycore.api.MasteryAwards;
import org.bitwisemadness.abilitycore.api.MasteryDeclarations;
import org.bitwisemadness.abilitycore.api.MasteryGainHandlers;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.bitwisemadness.abilitycore.api.MasteryTrigger;

/**
 * Layer-one spell mastery (D79): declares every player spell's Ability Core mastery contract and
 * owns every award site Technomagy can observe.
 *
 * <p>
 * Each spell's spec routes grants to the spell itself (no share list, no effect axes - those are
 * later layers) and takes its gain from {@link #EFFORT_HANDLER}, which prices effort linearly from
 * the server-configured cast cost: configured cost total times the trigger's weight. The static
 * base gain is the loud fallback core applies if the handler name ever fails to resolve.
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

    private SpellMastery() {
    }

    /**
     * Declares the effort gain handler and all sixteen spell specs. Lightning additionally declares
     * {@code HIT}/{@code KILL}, Ward additionally declares {@code CUSTOM}, and every spell declares
     * the {@code ACTIVATION} core fires itself after a successful cast; an award for an undeclared
     * trigger is ignored by core (D79).
     */
    static void declare() {
        MasteryGainHandlers.declare(EFFORT_HANDLER, SpellMastery::gain);
        for (PlayerSpell spell : PlayerSpell.values()) {
            MasterySpec spec = MasterySpec.gained(BASE_EFFORT).withGainHandler(EFFORT_HANDLER);
            spec = switch (spell) {
                case LIGHTNING -> spec.withTriggers(MasteryTrigger.HIT, MasteryTrigger.KILL);
                case WARD -> spec.withTriggers(MasteryTrigger.CUSTOM);
                default -> spec;
            };
            MasteryDeclarations.declare(spell.id(), spec);
        }
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
     * skipped for this grant while share routing and the global gain multiplier still apply. An
     * offline or removed caster resolves to null and grants nothing.
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
     * Prices one grant linearly from the spell's configured cost total times the trigger's weight
     * (D79), read whether or not payment is enabled - the cost table is the spell's intrinsic price
     * tag. A spell with no positive configured cost prices at {@link #BASE_EFFORT} rather than
     * granting nothing; an id this class never declared throws, which core logs loudly (D79).
     *
     * @param player
     *            the granting player; effort comes from configuration, not player state
     * @param source
     *            the ability id granting
     * @param trigger
     *            the trigger granting
     * @return XP for this grant, before routing and the global gain multiplier
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
        return effort(source) * weight;
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
}
