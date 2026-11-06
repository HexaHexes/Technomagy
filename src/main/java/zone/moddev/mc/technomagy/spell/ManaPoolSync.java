package zone.moddev.mc.technomagy.spell;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import zone.moddev.mc.skysleylines.api.LeyLinesApi;

/**
 * Server-thread-only D75 display synchronization. Samples after player ticks, coalescing all pool
 * changes in that tick into one snapshot and sending only changed data. Player identity changes
 * force a refresh after respawn; logout and shutdown release all retained state. Polling observes
 * automatic recharge and policy changes as well as explicit spending without assuming event order.
 */
final class ManaPoolSync {
    private final Map<UUID, Sent> sent = new HashMap<>();

    /** Last recipient instance and immutable display data; never authoritative storage. */
    private record Sent(ServerPlayer player, ManaPoolSnapshot snapshot) {
    }

    /** Sends first and changed snapshots only to the player whose balances were inspected. */
    @SubscribeEvent
    public void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var snapshot = ManaPoolSnapshot.from(
            LeyLinesApi.playerPower(player.server).inspect(player.getUUID()));
        var previous = sent.get(player.getUUID());
        if (previous == null || previous.player() != player
            || !previous.snapshot().equals(snapshot)) {
            PacketDistributor.sendToPlayer(player, snapshot);
            sent.put(player.getUUID(), new Sent(player, snapshot));
        }
    }

    /**
     * Drops cached display state on logout; a later connection always receives a fresh snapshot.
     */
    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        sent.remove(event.getEntity().getUUID());
    }

    /** Releases references across integrated-server sessions. */
    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        sent.clear();
    }
}
