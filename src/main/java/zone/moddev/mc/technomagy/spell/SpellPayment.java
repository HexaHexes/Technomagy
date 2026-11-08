package zone.moddev.mc.technomagy.spell;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.fml.event.config.ModConfigEvent;

import org.bitwisemadness.abilitycore.api.ActivationOutcome;

import org.slf4j.Logger;

import zone.moddev.mc.skysleylines.api.LeyLinesApi;
import zone.moddev.mc.skysleylines.api.PlayerPowerService;
import zone.moddev.mc.skysleylines.api.PowerRequest;

import zone.moddev.mc.technomagy.mana.ManaType;

/**
 * Charges player casts from the caster's six personal elemental pools (D70, D76) and returns the
 * charge when a validated cast produces no effect.
 *
 * <h2>Why a Technomagy-side transaction</h2>
 *
 * <p>
 * A spell costs several currencies at once (Erode is one Earth and one Air), but Sky's Ley Lines
 * 0.5.6 only spends one currency per call, and each spend posts a cancellable
 * {@code PlayerPowerEvent.Pre} that any mod may veto. Until Ley Lines offers an atomic
 * multi-currency spend, D76 approximates one:
 *
 * <ol>
 * <li><b>Preflight</b> every pool from one {@link PlayerPowerService#inspect} snapshot: each costed
 * pool must exist, be active, and hold the full cost. Ordinary shortfalls refuse here without
 * touching any balance.</li>
 * <li><b>Spend</b> each costed pool in {@link ManaType#FLAVORS} order.</li>
 * <li>If any spend fails (typically a third-party veto), <b>roll back</b> every spend already
 * committed by adding its units back with {@link PlayerPowerService#adjust}, newest first.</li>
 * </ol>
 *
 * <p>
 * The rollback is itself an ordinary Ley Lines mutation, so it can also be vetoed, and Ley Lines
 * refuses to raise a balance above capacity even to restore a previously over-capacity balance.
 * Such a failure cannot be repaired here; it is reported as an error naming the player, pool,
 * units, and status so that an operator can restore the balance. Every other pool is still rolled
 * back.
 *
 * <p>
 * The same return path {@linkplain #refund refunds} a completed charge when the effect, already
 * validated, still does nothing because a decision made only at application time (a cancelled
 * damage or heal event, a refused block or entity, a zero-tick Growth roll) prevented it. No effect
 * is paid for without happening.
 *
 * <p>
 * <b>Threading:</b> every method requires the owning server thread, as Ley Lines does. The class
 * holds no mutable state; configuration is read through {@link SpellConfig} at each cast.
 */
final class SpellPayment {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SpellPayment() {
    }

    /**
     * Reports every spell whose configured costs are all zero when the spell config loads or
     * reloads. Such a spell is refused while payment is enabled (see {@link #charge}), so a typo
     * can never make it free; this message tells the operator why.
     *
     * @param event
     *            a loading or reloading event; other mods' configs are ignored
     */
    static void configChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SpellConfig.SPEC) {
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
     * Reads one spell's configured cost.
     *
     * @param spell
     *            the spell being priced
     * @return positive units by flavor, in {@link ManaType#FLAVORS} order; empty when every cost is
     *         zero, which is invalid configuration
     */
    static Map<ManaType, Integer> cost(PlayerSpell spell) {
        Map<ManaType, Integer> cost = new EnumMap<>(ManaType.class);
        SpellConfig.COSTS.get(spell).forEach((element, value) -> {
            int units = value.get();
            if (units > 0) {
                cost.put(element, units);
            }
        });
        return cost;
    }

    /**
     * Charges one cast, all-or-nothing as far as Ley Lines allows (see the class description).
     *
     * @param player
     *            the caster, on the server thread
     * @param spell
     *            the validated spell about to be applied
     * @return the committed charge (empty when payment is disabled or the caster is in creative
     *         mode), or the refusal to report
     * @throws IllegalStateException
     *             if called off the server thread
     */
    static Outcome charge(ServerPlayer player, PlayerSpell spell) {
        requireServerThread(player);
        if (!SpellConfig.PAYMENT_ENABLED.get() || player.isCreative()) {
            // Creative players cast without paying (D76).
            return new Outcome.Paid(Charge.NONE);
        }
        Map<ManaType, Integer> cost = cost(spell);
        if (cost.isEmpty()) {
            // Already reported at config load; refusing keeps a misconfigured spell from being
            // free.
            LOGGER.debug("Refusing {} for {}: its configured cost is all zero", spell.id(),
                player.getGameProfile().getName());
            return new Outcome.Refused(ActivationOutcome.REFUSED_UNAVAILABLE);
        }
        PlayerPowerService power = LeyLinesApi.playerPower(player.server);
        UUID owner = player.getUUID();
        Map<ResourceLocation, PlayerPowerService.Pool> pools = power.inspect(owner).pools();
        for (var entry : cost.entrySet()) {
            ResourceLocation currency = PlayerManaPools.currency(entry.getKey());
            PlayerPowerService.Pool pool = pools.get(currency);
            if (pool == null) {
                // Pools are created at login (D70) and initialization throws on failure, so a
                // missing pool means the player's saved Ley Lines data was altered or lost.
                LOGGER.error("Player {} ({}) has no {} pool; refusing {}. Their personal pools "
                    + "are recreated on next login.",
                    player.getGameProfile().getName(), owner, currency, spell.id());
                return new Outcome.Refused(ActivationOutcome.REFUSED_UNAVAILABLE);
            }
            if (!pool.active() || pool.balance() < entry.getValue()) {
                return new Outcome.Refused(ActivationOutcome.REFUSED_UNAFFORDABLE);
            }
        }
        var consumer = consumer(spell, owner);
        List<Charge.Entry> paid = new ArrayList<>(cost.size());
        for (var entry : cost.entrySet()) {
            ResourceLocation currency = PlayerManaPools.currency(entry.getKey());
            long units = entry.getValue();
            PlayerPowerService.Result result = power.spend(owner, currency, units, consumer);
            if (!result.succeeded()) {
                returnUnits(power, player, spell, paid, "partial charge rollback");
                return new Outcome.Refused(refusal(result.status(), player, spell, currency));
            }
            paid.add(new Charge.Entry(currency, units));
        }
        return new Outcome.Paid(new Charge(owner, List.copyOf(paid)));
    }

    /**
     * Returns a committed charge after a validated effect turned out to do nothing. Returns of
     * individual pools that Ley Lines rejects are reported as errors; the rest still proceed.
     *
     * @param player
     *            the caster the charge was taken from
     * @param spell
     *            the spell that produced no effect
     * @param charge
     *            the charge returned by {@link #charge}; {@link Charge#NONE} does nothing
     * @throws IllegalArgumentException
     *             if the charge belongs to another player
     * @throws IllegalStateException
     *             if called off the server thread
     */
    static void refund(ServerPlayer player, PlayerSpell spell, Charge charge) {
        requireServerThread(player);
        if (charge.paid().isEmpty()) {
            return;
        }
        if (!charge.player().equals(player.getUUID())) {
            throw new IllegalArgumentException(
                "Charge for " + charge.player() + " refunded to " + player.getUUID());
        }
        returnUnits(LeyLinesApi.playerPower(player.server), player, spell, charge.paid(),
            "refund of an ineffective cast");
    }

    /**
     * Adds paid units back, newest first, reporting every pool that Ley Lines will not restore.
     *
     * @param reason
     *            why the units are returned, for the error report
     */
    private static void returnUnits(PlayerPowerService power, ServerPlayer player,
        PlayerSpell spell, List<Charge.Entry> paid, String reason) {
        var consumer = consumer(spell, player.getUUID());
        for (int index = paid.size() - 1; index >= 0; index--) {
            Charge.Entry entry = paid.get(index);
            PlayerPowerService.Result result = power.adjust(
                player.getUUID(), entry.currency(), entry.units(), consumer);
            if (!result.succeeded()) {
                LOGGER.error("Could not return {} {} to player {} ({}) during {} for {}: {}. "
                    + "Those units are lost and must be restored manually.",
                    entry.units(), entry.currency(), player.getGameProfile().getName(),
                    player.getUUID(), reason, spell.id(), result.status());
            }
        }
    }

    /**
     * Maps a failed spend, after preflight passed, to the refusal reported to Ability Core.
     *
     * <p>
     * A veto by another mod is a restriction. A balance or pool change between preflight and spend
     * is an ordinary shortfall. Anything else means Ley Lines could not perform a valid request
     * (for example a storage failure, or a cast attempted from inside a power-event listener, which
     * Ley Lines rejects as re-entrant) and is reported as an error.
     */
    private static ActivationOutcome refusal(PlayerPowerService.Status status, ServerPlayer player,
        PlayerSpell spell, ResourceLocation currency) {
        return switch (status) {
            case CANCELLED -> ActivationOutcome.REFUSED_RESTRICTED;
            case INSUFFICIENT_POWER, DORMANT -> ActivationOutcome.REFUSED_UNAFFORDABLE;
            default -> {
                LOGGER.error("Ley Lines could not charge {} from player {} ({}) for {}: {}",
                    currency, player.getGameProfile().getName(), player.getUUID(), spell.id(),
                    status);
                yield ActivationOutcome.REFUSED_UNAVAILABLE;
            }
        };
    }

    /** Identifies spell payments in Ley Lines receipts by spell and caster. */
    private static PowerRequest.Consumer consumer(PlayerSpell spell, UUID player) {
        return new PowerRequest.Consumer(spell.id(), Optional.of(player));
    }

    /** Enforces Ley Lines' owning-thread requirement with a clear failure. */
    private static void requireServerThread(ServerPlayer player) {
        if (!player.server.isSameThread()) {
            throw new IllegalStateException("Spell payment requires the server thread");
        }
    }

    /**
     * Units actually taken from one player for one cast.
     *
     * @param player
     *            the paying player's UUID; unused when {@code paid} is empty
     * @param paid
     *            committed spends, in spend order; empty when payment is disabled
     */
    record Charge(UUID player, List<Entry> paid) {
        /** The charge of a free cast (payment disabled, or creative caster): nothing to return. */
        static final Charge NONE = new Charge(new UUID(0, 0), List.of());

        /** Validates and freezes the entries. */
        Charge {
            Objects.requireNonNull(player);
            paid = List.copyOf(paid);
        }

        /**
         * One committed spend.
         *
         * @param currency
         *            the personal pool's Ley Lines currency
         * @param units
         *            positive whole units taken
         */
        record Entry(ResourceLocation currency, long units) {
            /** Rejects malformed entries. */
            Entry {
                Objects.requireNonNull(currency);
                if (units <= 0) {
                    throw new IllegalArgumentException("Charged units must be positive: " + units);
                }
            }
        }
    }

    /** The result of {@link #charge}: either a committed charge or the refusal to report. */
    sealed interface Outcome {
        /**
         * The cast is paid for.
         *
         * @param charge
         *            what to return if the effect does nothing
         */
        record Paid(Charge charge) implements Outcome {
        }

        /**
         * The cast is refused and no balance has changed (apart from reported rollback failures).
         *
         * @param outcome
         *            the Ability Core refusal
         */
        record Refused(ActivationOutcome outcome) implements Outcome {
        }
    }
}
