package zone.moddev.mc.technomagy.spell;

import java.util.List;
import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.skysleylines.api.PlayerPowerService;

import zone.moddev.mc.technomagy.mana.ManaType;

/**
 * Server-owned personal pool display data (D75), in ManaType.FLAVORS order, never including Raw.
 * Inspired by BattleMageDemo's ManaSnapshot at commit 0952f35707b55e4f15544400b473fad2fe4349fc. No
 * client state or client classes are referenced here; this immutable packet grants no power.
 *
 * @param bars
 *            exactly six validated elemental entries
 */
public record ManaPoolSnapshot(List<Bar> bars) implements CustomPacketPayload {
    /** The personal-pool HUD's server-to-client packet type. */
    public static final Type<ManaPoolSnapshot> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("technomagy", "mana_pool_snapshot"));

    /** Fixed six-entry codec, bounded independently of untrusted packet lengths. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ManaPoolSnapshot> CODEC = StreamCodec
        .of(
            (buffer, packet) -> {
                for (var bar : packet.bars()) {
                    buffer.writeVarLong(bar.balance());
                    buffer.writeVarLong(bar.capacity());
                    buffer.writeBoolean(bar.configured());
                    buffer.writeBoolean(bar.active());
                }
            }, buffer -> {
                var entries = new java.util.ArrayList<Bar>(ManaType.FLAVORS.size());
                for (var element : ManaType.FLAVORS) {
                    entries.add(new Bar(buffer.readVarLong(), buffer.readVarLong(),
                        buffer.readBoolean(), buffer.readBoolean()));
                }
                return new ManaPoolSnapshot(entries);
            });

    /** Makes the six-entry list immutable and rejects missing or excess entries. */
    public ManaPoolSnapshot {
        bars = List.copyOf(bars);
        if (bars.size() != ManaType.FLAVORS.size()) {
            throw new IllegalArgumentException("Personal mana HUD requires exactly six elements");
        }
    }

    /**
     * One pool's whole currency units. Balances above capacity are retained for truthful display.
     *
     * @param balance
     *            nonnegative stored units
     * @param capacity
     *            nonnegative configured maximum
     * @param configured
     *            whether the player has saved data for this currency
     * @param active
     *            whether LeyLines currently permits use of the pool
     */
    public record Bar(long balance, long capacity, boolean configured, boolean active) {
        /** Rejects malformed quantities and contradictory absent-pool data. */
        public Bar {
            if (balance < 0 || capacity < 0
                || (!configured && (balance != 0 || capacity != 0 || active))) {
                throw new IllegalArgumentException("Invalid personal mana HUD entry");
            }
        }
    }

    /**
     * Projects only Technomagy's six elements from a server-thread LeyLines snapshot.
     *
     * @param snapshot
     *            authoritative per-player pools
     * @return immutable display data; absent pools are marked unconfigured
     */
    public static ManaPoolSnapshot from(PlayerPowerService.Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        return new ManaPoolSnapshot(ManaType.FLAVORS.stream().map(element -> {
            var id = ResourceLocation.fromNamespaceAndPath("technomagy",
                element.getSerializedName());
            var pool = snapshot.pools().get(id);
            return pool == null
                ? new Bar(0, 0, false, false)
                : new Bar(pool.balance(), pool.settings().capacity(), true, pool.active());
        }).toList());
    }

    /** Returns the registered HUD payload identity. */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
