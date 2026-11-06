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
     * exhausted pool or disabling recharge later unlocked by an apparatus. Raw is deliberately
     * excluded. Null automatic rate disables recharge until that progression is implemented.
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
        var consumer = new PowerRequest.Consumer(
            ResourceLocation.fromNamespaceAndPath(Technomagy.MODID, "player_pools"),
            Optional.of(player));
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
