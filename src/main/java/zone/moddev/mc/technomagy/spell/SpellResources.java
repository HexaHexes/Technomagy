package zone.moddev.mc.technomagy.spell;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * charge site, with a refund half for callers that ask the owner. Both halves treat a disabled
 * {@code payment.enabled} or a creative caster as free, so the pair can never disagree about who
 * pays.
 *
 * <p>
 * A zero price is trivially affordable and charges nothing, mirroring D76's rule that unconfigured
 * flavors are never checked or spent. Only strictly positive prices touch Ley Lines, whose balances
 * are whole units; the resolver prices from integer server config, so pre-mastery snapshot amounts
 * are integral. A fractional amount - possible only once a mastery discount scales prices (Phase 3
 * of the mastery work) - is refused rather than rounded, because a rounding rule would then be a
 * design decision of its own.
 *
 * <h2>Per-request pricing</h2>
 *
 * <p>
 * Every spell definition declares all six flavors with its default stream as the nominal cost, and
 * names the {@code technomagy:spell_cost} resolver: at registration time the per-world server
 * config is not loaded yet, so the declared amounts are display defaults and the resolver is
 * authoritative for each cast (D78). The resolver mirrors the declared resource ids in order and
 * re-prices each entry from {@link SpellConfig#COSTS}; an entry whose id is not one of the six pool
 * currencies cannot be priced honestly and fails the cast closed.
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
                (abilityId, player, amount) -> check(element, amount, player), LEY_LINES_MOD_ID);
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
     * creative caster always pays nothing, so the matching charge is a no-op; a zero price never
     * inspects the pool, and otherwise the pool must exist, be active, and hold the whole price
     * (D76's preflight).
     *
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
    private static Affordability check(ManaType element, double amount, ServerPlayer player) {
        String id = resourceId(element);
        if (!SpellConfig.PAYMENT_ENABLED.get() || player.isCreative() || amount <= 0) {
            return Affordability.sufficient(id, amount);
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
     * (but never retrying) a failed spend. A failed spend is a restriction by another mod or a race
     * against the preflight balance, not a defect here, so it is reported as a warning: the cast
     * stands and was not paid for that pool (D78), and the GameTest log gate treats warnings as
     * ordinary operational records.
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
                    LOGGER.warn(
                        "Ley Lines did not charge {} {} from player {} ({}) for {}: {}. The cast"
                            + " stands free of that pool (D78).",
                        units,
                        resourceId(element),
                        player.getGameProfile().getName(),
                        player.getUUID(),
                        abilityId,
                        result.status());
                }
            }

            @Override
            public void refund(ResourceLocation abilityId, ServerPlayer player,
                Affordability snapshot) {
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
     * Converts one validated snapshot into whole units, refusing fractional prices.
     *
     * @param player
     *            the paying player
     * @param snapshot
     *            the validated affordability; its amount is charged verbatim
     * @return whole units to move, or 0 for a free cast (payment disabled, creative, or zero price)
     * @throws IllegalStateException
     *             if called off the server thread
     */
    private static long units(ServerPlayer player, Affordability snapshot) {
        requireServerThread(player);
        if (!SpellConfig.PAYMENT_ENABLED.get() || player.isCreative()) {
            return 0;
        }
        if (snapshot.amount() != Math.floor(snapshot.amount())) {
            LOGGER.error(
                "Snapshot for {} prices {} units, but pool balances are whole; charging nothing.",
                snapshot.resourceId(),
                snapshot.amount());
            return 0;
        }
        return (long) snapshot.amount();
    }

    /**
     * Prices one cast from the server config, mirroring the declared entries in order (D78).
     *
     * <p>
     * A foreign ability whose datapack overlay names {@code technomagy:spell_cost} is priced at its
     * declared amounts: only Technomagy spells have configured elemental costs, and the declared
     * list is then the honest price. An entry that does not name one of the six pools cannot be
     * priced and throws, which Ability Core turns into a fail-closed refusal.
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
     */
    private static List<ResourceCost> price(List<ResourceCost> declared, ActivationRequest request,
        ServerPlayer player, Object resolvedTarget) {
        Optional<PlayerSpell> spell = PlayerSpell.find(request.abilityId());
        if (spell.isEmpty()) {
            return List.copyOf(declared);
        }
        List<ResourceCost> priced = new ArrayList<>(declared.size());
        for (ResourceCost entry : declared) {
            priced.add(
                new ResourceCost(
                    entry.resourceId(),
                    SpellConfig.configured(spell.orElseThrow(), elementOf(entry.resourceId()))));
        }
        return priced;
    }

    /**
     * Snapshots every personal pool's live state.
     *
     * @param player
     *            the pool owner, on the server thread
     * @return the owner's pools by currency id
     * @throws IllegalStateException
     *             if called off the server thread
     */
    private static Map<ResourceLocation, PlayerPowerService.Pool> pools(ServerPlayer player) {
        return LeyLinesApi.playerPower(player.server).inspect(player.getUUID()).pools();
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
