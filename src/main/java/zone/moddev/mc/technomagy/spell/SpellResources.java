package zone.moddev.mc.technomagy.spell;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.Affordability;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.RefundableConsumer;
import org.bitwisemadness.abilitycore.api.ResourceConsumers;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.bitwisemadness.abilitycore.api.ResourceProviders;

import org.slf4j.Logger;

import zone.moddev.mc.skysleylines.api.LeyLinesApi;
import zone.moddev.mc.skysleylines.api.PlayerPowerService;
import zone.moddev.mc.skysleylines.api.PowerRequest;

import zone.moddev.mc.technomagy.mana.ManaType;

/**
 * Presents the six personal elemental pools (D70) to Ability Core as resource pairs and prices
 * spell costs per cast (D78).
 *
 * <h2>One pair per pool</h2>
 *
 * <p>
 * Each pool's Ley Lines currency (D70) becomes one Ability Core resource whose id is the currency
 * id, so a definition's cost entry names the pool it is paid from, for example
 * {@code "technomagy:water"}. The read half answers affordability for the priced amount it is
 * given; the write half spends the validated snapshot post-activation through Ability Core's single
 * charge site, with a refund half for callers that ask the owner. A disabled
 * {@code payment.enabled} or a creative caster answers a zero snapshot, so the charge half reads
 * its decision from the snapshot itself and never re-derives who pays.
 *
 * <p>
 * A zero price is trivially affordable and charges nothing, mirroring D76's rule that unconfigured
 * flavors are never checked or spent. Only strictly positive prices touch Ley Lines, whose balances
 * are whole units; the resolver prices from integer server config, so pre-mastery snapshot amounts
 * are integral. A fractional amount - possible only once a mastery discount scales prices - is
 * refused fail-closed at the affordability pre-check (UNKNOWN), because a rounding rule would then
 * be a design decision of its own; the charge half treats a fractional snapshot as a contract
 * violation it logs rather than a price to round.
 *
 * <h2>Per-request pricing</h2>
 *
 * <p>
 * Every spell definition declares all six flavors with its default stream as the nominal cost, and
 * names the {@code technomagy:spell_cost} resolver: at registration time the per-world server
 * config is not loaded yet, so the declared amounts are display defaults and the resolver is
 * authoritative for each cast (D78). The resolver validates the declared list - exactly the six
 * pool ids, each once - and re-prices every entry from {@link SpellConfig#COSTS}, so an overlay
 * that drops, duplicates or renames a pool id fails the cast closed instead of quietly charging
 * less. The resolver prices Technomagy spells only; a foreign ability that names it fails closed
 * too (D78).
 *
 * <p>
 * <b>Threading:</b> Ley Lines requires its owning server thread; the halves assert it explicitly,
 * because Ability Core's charge site runs on whichever thread activated the ability. Declaration
 * runs once at mod construction, before Ability Core's registry events fire, so definition-time
 * cost decisions are order-safe.
 */
final class SpellResources {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The mod id backing pool state, for the mod-gated declaration (issue #92). */
    private static final String LEY_LINES_MOD_ID = "skysleylines";

    /** The registered Ability Core amount resolver, named in every spell definition (D78). */
    static final String COST_RESOLVER = "technomagy:spell_cost";

    /**
     * The same-tick pool memo backing {@link #pools}: server-thread confined, cleared by every
     * spend and refund so no later cast reads a pre-write balance.
     */
    private static CachedPools cachedPools;

    private SpellResources() {
    }

    /**
     * Declares the six pool pairs and the cost resolver. The provider half must be declared before
     * its consumer (Ability Core's pairing rule), and duplicates fail loudly, so this must run
     * exactly once per load, at mod construction (D78).
     */
    static void declare() {
        for (ManaType element : ManaType.FLAVORS) {
            String id = resourceId(element);
            ResourceProviders.declare(id,
                (abilityId, player, amount) -> check(abilityId, element, amount, player),
                LEY_LINES_MOD_ID);
            ResourceConsumers.declare(id, refundableConsumer(element), LEY_LINES_MOD_ID);
        }
        AmountResolvers.declare(COST_RESOLVER, SpellResources::price);
    }

    /**
     * Returns the Ability Core resource id naming one personal pool: its Ley Lines currency (D70).
     *
     * @param element
     *            one of {@link ManaType#FLAVORS}
     * @return the {@code technomagy:<flavor>} currency as a cost-entry resource id
     */
    static String resourceId(ManaType element) {
        return PlayerManaPools.currency(element).toString();
    }

    /**
     * Resolves the flavor a cost entry's resource id pays from.
     *
     * @param resource
     *            a pool resource id, nominally from {@link #resourceId}
     * @return the matching flavor
     * @throws IllegalArgumentException
     *             for an id that is not one of the six pool currencies; the resolver turns this
     *             into a fail-closed refusal rather than guessing a price
     */
    private static ManaType elementOf(String resource) {
        for (ManaType element : ManaType.FLAVORS) {
            if (resourceId(element).equals(resource)) {
                return element;
            }
        }
        throw new IllegalArgumentException(
            "Not a Technomagy pool resource: " + resource + "; cannot price it");
    }

    /**
     * Answers affordability for one pool at the priced amount. A disabled payment option or a
     * creative caster pays nothing, so the snapshot itself carries a zero amount and the charge
     * half needs no second opinion; a zero price likewise answers zero without touching the pool. A
     * fractional price cannot be charged in whole units and fails closed as UNKNOWN rather than
     * rounding. Otherwise the pool must exist, be active, and hold the whole price (D76's
     * preflight), read from one same-tick snapshot shared with the other five entries.
     *
     * @param abilityId
     *            the ability being cast, for the fractional-price diagnostic
     * @param element
     *            the pool being consulted
     * @param amount
     *            this request's priced cost, already resolved by {@link #price}
     * @param player
     *            the casting player
     * @return the affordability verdict Ability Core validates fail-closed
     * @throws IllegalStateException
     *             if called off the server thread
     */
    private static Affordability check(ResourceLocation abilityId, ManaType element, double amount,
        ServerPlayer player) {
        String id = resourceId(element);
        if (!SpellConfig.PAYMENT_ENABLED.get() || player.isCreative() || amount <= 0) {
            return Affordability.sufficient(id, 0);
        }
        if (amount != Math.floor(amount)) {
            // WARN not ERROR: refusing is this path's designed outcome, and the log gate
            // reserves ERROR for defects (the unreachable units() guard keeps ERROR).
            LOGGER.warn(
                "Pool {} was asked about a fractional price {} for {}; refusing fail-closed"
                    + " instead of rounding (D78).",
                id, amount, abilityId);
            return Affordability.unknown(id);
        }
        requireServerThread(player);
        PlayerPowerService.Pool pool = pools(player).get(PlayerManaPools.currency(element));
        if (pool == null) {
            LOGGER.error(
                "Player {} ({}) has no {} pool; refusing casts that price it. Personal pools are"
                    + " recreated on next login.",
                player.getGameProfile().getName(),
                player.getUUID(),
                id);
            return Affordability.unknown(id);
        }
        return pool.active() && pool.balance() >= amount
            ? Affordability.sufficient(id, amount)
            : Affordability.insufficient(id, amount);
    }

    /**
     * Builds the write half for one pool: spend the validated snapshot post-activation, reporting
     * (but never retrying) a failed spend. A failed spend is split by status: a veto, a race
     * against the preflight balance, or an inactive pool is a restriction by another mod or the
     * world, reported as a warning - the cast stands and was not paid for that pool (D78), and the
     * GameTest log gate treats warnings as ordinary operational records. Every other status is a
     * defect here and is reported as an error so that gate and server operators both catch it. Any
     * spend or refund clears the same-tick pool memo, so no later cast reads a pre-write balance.
     *
     * @param element
     *            the pool this consumer spends
     * @return the refundable consumer Ability Core charges with the validated snapshot
     * @throws IllegalStateException
     *             if charged off the server thread
     */
    private static RefundableConsumer refundableConsumer(ManaType element) {
        return new RefundableConsumer() {
            @Override
            public void charge(ResourceLocation abilityId, ServerPlayer player,
                Affordability snapshot) {
                cachedPools = null;
                long units = units(player, snapshot);
                if (units <= 0) {
                    LOGGER.debug("{} charged nothing from {}: no positive price", abilityId,
                        resourceId(element));
                    return;
                }
                PlayerPowerService.Result result = LeyLinesApi.playerPower(player.server).spend(
                    player.getUUID(), PlayerManaPools.currency(element), units,
                    receipt(abilityId, player));
                if (!result.succeeded()) {
                    if (isRestriction(result.status())) {
                        LOGGER.warn(
                            "Ley Lines did not charge {} {} from player {} ({}) for {}: {}. The"
                                + " cast stands free of that pool (D78).",
                            units,
                            resourceId(element),
                            player.getGameProfile().getName(),
                            player.getUUID(),
                            abilityId,
                            result.status());
                    } else {
                        LOGGER.error(
                            "Ley Lines could not charge {} {} from player {} ({}) for {}: {}. The"
                                + " cast stands free of that pool (D78); this status is a defect,"
                                + " not a restriction.",
                            units,
                            resourceId(element),
                            player.getGameProfile().getName(),
                            player.getUUID(),
                            abilityId,
                            result.status());
                    }
                }
            }

            @Override
            public void refund(ResourceLocation abilityId, ServerPlayer player,
                Affordability snapshot) {
                cachedPools = null;
                long units = units(player, snapshot);
                if (units <= 0) {
                    return;
                }
                PlayerPowerService.Result result = LeyLinesApi.playerPower(player.server).adjust(
                    player.getUUID(), PlayerManaPools.currency(element), units,
                    receipt(abilityId, player));
                if (!result.succeeded()) {
                    LOGGER.error(
                        "Could not return {} {} to player {} ({}) for {}: {}. Those units are lost"
                            + " and must be restored manually.",
                        units,
                        resourceId(element),
                        player.getGameProfile().getName(),
                        player.getUUID(),
                        abilityId,
                        result.status());
                }
            }
        };
    }

    /**
     * Converts one validated snapshot into whole units. A zero amount is the free decision the read
     * half already made (payment disabled, creative, or zero price) and charges nothing; a
     * fractional amount can only arrive if something bypassed {@link #check}, so it is reported as
     * an error and charges nothing rather than being rounded.
     *
     * @param player
     *            the paying player
     * @param snapshot
     *            the validated affordability; its amount is charged verbatim
     * @return whole units to move, or 0 for a free or invalid snapshot
     * @throws IllegalStateException
     *             if called off the server thread
     */
    private static long units(ServerPlayer player, Affordability snapshot) {
        requireServerThread(player);
        if (snapshot.amount() == 0) {
            return 0;
        }
        if (snapshot.amount() != Math.floor(snapshot.amount())) {
            LOGGER.error(
                "Snapshot for {} prices {} units, but pool balances are whole; check() should"
                    + " have refused this snapshot. Charging nothing.",
                snapshot.resourceId(),
                snapshot.amount());
            return 0;
        }
        return (long) snapshot.amount();
    }

    /**
     * Whether a failed spend is a restriction or race rather than a defect: Ley Lines' veto, the
     * balance race, and an inactive pool are operational and warn; every other status - storage
     * failures, re-entrancy rejections, policy refusals - is a defect and errors, so the GameTest
     * log gate still catches it (D78).
     *
     * @param status
     *            the failed spend's Ley Lines status
     * @return true for the three operational statuses, false for everything else
     */
    private static boolean isRestriction(PlayerPowerService.Status status) {
        return status == PlayerPowerService.Status.CANCELLED
            || status == PlayerPowerService.Status.INSUFFICIENT_POWER
            || status == PlayerPowerService.Status.DORMANT;
    }

    /**
     * Prices one cast from the server config, mirroring the declared entries in order (D78).
     *
     * <p>
     * Only Technomagy spells have configured elemental costs, so a foreign ability that names
     * {@code technomagy:spell_cost} fails closed with a thrown resolution - Ability Core logs the
     * resolver failure and refuses the cast rather than inventing a price (D78's recorded
     * decision). A Technomagy spell's declared list must be exactly the six pool ids, each once:
     * dropping one would silently stop charging that pool, duplicating one would charge it twice,
     * and any other id is not a pool currency. Every surviving entry is re-priced from
     * {@link SpellConfig#COSTS}, which is authoritative over the display defaults.
     *
     * @param declared
     *            the definition's declared cost entries, in list order
     * @param request
     *            the activation; its ability id selects the configured spell
     * @param player
     *            the casting player, ignored: pricing is player-independent
     * @param resolvedTarget
     *            the server-resolved entity target, unused: spells cost flat configured amounts
     * @return the priced mirror of {@code declared}
     * @throws IllegalArgumentException
     *             for a foreign ability, a dropped or duplicated pool id, or an id that is not one
     *             of the six pools; Ability Core turns each into a logged fail-closed refusal
     */
    private static List<ResourceCost> price(List<ResourceCost> declared, ActivationRequest request,
        ServerPlayer player, Object resolvedTarget) {
        PlayerSpell spell = PlayerSpell.find(request.abilityId()).orElseThrow(
            () -> new IllegalArgumentException("Resolver " + COST_RESOLVER + " prices Technomagy"
                + " spells only; " + request.abilityId() + " must name its own resolver (D78)"));
        validateDeclared(declared);
        List<ResourceCost> priced = new ArrayList<>(declared.size());
        for (ResourceCost entry : declared) {
            priced.add(
                new ResourceCost(
                    entry.resourceId(),
                    SpellConfig.configured(spell, elementOf(entry.resourceId()))));
        }
        return priced;
    }

    /**
     * Rejects a declared list this resolver cannot price honestly (D78): exactly the six pool ids,
     * each once, with {@link #elementOf} rejecting any id outside the six. Any violation throws,
     * which Ability Core turns into a logged fail-closed refusal instead of a quietly cheaper cast.
     *
     * @param declared
     *            the definition's declared cost entries
     * @throws IllegalArgumentException
     *             for a missing, duplicated, or non-pool id
     */
    private static void validateDeclared(List<ResourceCost> declared) {
        if (declared.size() != ManaType.FLAVORS.size()) {
            throw new IllegalArgumentException("Declared costs must name all " + ManaType.FLAVORS
                .size() + " pools exactly once, got " + declared.size() + " entries");
        }
        Set<String> seen = new HashSet<>();
        for (ResourceCost entry : declared) {
            elementOf(entry.resourceId());
            if (!seen.add(entry.resourceId())) {
                throw new IllegalArgumentException(
                    "Duplicate pool id in declared costs: " + entry.resourceId());
            }
        }
    }

    /**
     * Snapshots every personal pool's live state, memoized for the rest of the server tick: the six
     * per-entry affordability consults of one cast share a single inspect, and every spend or
     * refund clears the memo so a later cast never reads a pre-write balance. Server-thread
     * confined (Ley Lines' own requirement), so the memo needs no synchronization; an external
     * write in the same tick can only leave it conservative or one race-class spend behind, which
     * the spend path already reports.
     *
     * @param player
     *            the pool owner, on the server thread
     * @return the owner's pools by currency id
     * @throws IllegalStateException
     *             if called off the server thread
     */
    private static Map<ResourceLocation, PlayerPowerService.Pool> pools(ServerPlayer player) {
        int tick = player.server.getTickCount();
        CachedPools hit = cachedPools;
        if (hit != null && hit.player().equals(player.getUUID()) && hit.tick() == tick) {
            return hit.pools();
        }
        Map<ResourceLocation, PlayerPowerService.Pool> fresh = LeyLinesApi
            .playerPower(player.server).inspect(player.getUUID()).pools();
        cachedPools = new CachedPools(player.getUUID(), tick, fresh);
        return fresh;
    }

    /** One inspect's result, valid for one player on one server tick (see {@link #pools}). */
    private record CachedPools(UUID player, int tick,
        Map<ResourceLocation, PlayerPowerService.Pool> pools) {
    }

    /**
     * Identifies spell payments and returns in Ley Lines receipts by ability and caster.
     *
     * @param abilityId
     *            the cast ability
     * @param player
     *            the paying player
     * @return the receipt consumer for one spell payment
     */
    private static PowerRequest.Consumer receipt(ResourceLocation abilityId, ServerPlayer player) {
        return new PowerRequest.Consumer(abilityId, Optional.of(player.getUUID()));
    }

    /** Enforces Ley Lines' owning-thread requirement with a clear failure. */
    private static void requireServerThread(ServerPlayer player) {
        if (!player.server.isSameThread()) {
            throw new IllegalStateException("Pool payment requires the server thread");
        }
    }
}
