package zone.moddev.mc.technomagy.spell;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import zone.moddev.mc.skysleylines.api.LeyLinesApi;
import zone.moddev.mc.skysleylines.api.PlayerPowerService;
import zone.moddev.mc.skysleylines.api.PowerRequest;

import zone.moddev.mc.technomagy.Technomagy;
import zone.moddev.mc.technomagy.mana.ManaType;

/**
 * Creates the six personal elemental reserves on login under D70's approved pool policy. All access
 * runs on the owning server thread; LeyLines owns persistence across player lifetimes.
 */
final class PlayerManaPools {
    private static final List<ResourceLocation> ELEMENTS = List.of(
        Technomagy.LIGHT, Technomagy.DARK, Technomagy.EARTH,
        Technomagy.AIR, Technomagy.FIRE, Technomagy.WATER);
    private static final long CAPACITY = 10;
    private static final long INITIAL_BALANCE = 3;

    private PlayerManaPools() {
    }

    /**
     * Returns the LeyLines currency backing an elemental flavor's personal pool.
     *
     * @param element
     *            one of {@link ManaType#FLAVORS}
     * @return the registered {@code technomagy:<flavor>} currency
     * @throws IllegalArgumentException
     *             for {@link ManaType#RAW}, which has no player pool (D70)
     */
    static ResourceLocation currency(ManaType element) {
        if (element == ManaType.RAW) {
            throw new IllegalArgumentException("Raw has no personal pool (D70)");
        }
        return ResourceLocation.fromNamespaceAndPath(Technomagy.MODID, element.getSerializedName());
    }

    /**
     * Identifies pool setup and recharge changes in Ley Lines receipts.
     *
     * @param player
     *            the pool owner
     * @return the {@code technomagy:player_pools} consumer acting for that player
     */
    static PowerRequest.Consumer consumer(UUID player) {
        return new PowerRequest.Consumer(
            ResourceLocation.fromNamespaceAndPath(Technomagy.MODID, "player_pools"),
            Optional.of(player));
    }

    /** Creates missing pools for a joining server player, including players in existing worlds. */
    static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            initialize(LeyLinesApi.playerPower(player.server), player.getUUID());
        }
    }

    /**
     * Configures missing pools and seeds them once, without changing any existing reserve.
     *
     * <p>
     * Inspecting persisted entries, rather than balance, prevents reconnects from refilling an
     * exhausted pool or overwriting settings changed since. Raw is deliberately excluded. Ley
     * Lines' automatic rate stays null: {@link PoolRecharge} recharges a random pool instead (D77).
     * LeyLines configuration and seeding are separate commits: a rejected operation is reported
     * explicitly rather than bypassing protection hooks or claiming initialization succeeded.
     *
     * @param power
     *            owning server's player power service
     * @param player
     *            persistent player UUID
     * @throws IllegalStateException
     *             if LeyLines rejects configuration or the initial balance
     */
    static void initialize(PlayerPowerService power, UUID player) {
        var existing = power.inspect(player).pools();
        var consumer = consumer(player);
        for (var element : ELEMENTS) {
            if (existing.containsKey(element)) {
                continue;
            }
            requireSuccess(power.configure(player, element,
                new PlayerPowerService.Settings(true, CAPACITY, null), consumer), player, element);
            requireSuccess(power.setBalance(player, element, INITIAL_BALANCE, consumer),
                player, element);
        }
    }

    /** Reports rejected operations with the player, currency, and upstream status for diagnosis. */
    private static void requireSuccess(
        PlayerPowerService.Result result, UUID player, ResourceLocation element) {
        if (!result.succeeded()) {
            throw new IllegalStateException("Cannot initialize mana pool " + element
                + " for player " + player + ": " + result.status());
        }
    }
}
