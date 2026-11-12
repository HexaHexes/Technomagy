package zone.moddev.mc.technomagy.spell;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The two unlock thresholds, server config to client (D83): the menu decides hiding client-side and
 * server configs never reach the client, so the pair rides the technomagy channel at join, beside
 * {@link ManaPoolSnapshot}. The client store starts unset (-1) and abstains until this arrives -
 * unknown never locks anyone out (hotbar's {@code UnlockRule}).
 *
 * @param duoLevel
 *            element level every Duo spell's parts must reach (0..100)
 * @param trioLevel
 *            the Trio threshold (0..100)
 */
public record UnlockThresholdsPayload(int duoLevel, int trioLevel) implements CustomPacketPayload {

    /** The unlock-threshold payload's server-to-client packet type. */
    public static final Type<UnlockThresholdsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("technomagy", "unlock_thresholds"));

    /** Two bounded ints, validated by the compact constructor on read. */
    public static final StreamCodec<RegistryFriendlyByteBuf, UnlockThresholdsPayload> CODEC = StreamCodec
        .of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.duoLevel());
                buffer.writeVarInt(packet.trioLevel());
            },
            buffer -> new UnlockThresholdsPayload(buffer.readVarInt(), buffer.readVarInt()));

    /** Client store: -1 until the server's join payload applies (D83). */
    private static volatile int duo = -1;

    private static volatile int trio = -1;

    /** Rejects thresholds outside the config's own 0..100 range. */
    public UnlockThresholdsPayload {
        if (duoLevel < 0 || duoLevel > 100 || trioLevel < 0 || trioLevel > 100) {
            throw new IllegalArgumentException(
                "Unlock thresholds must stay in 0..100, got " + duoLevel + "/" + trioLevel);
        }
    }

    /**
     * Applies the server's thresholds to the client store; runs on the client after the payload
     * arrives.
     *
     * @param payload
     *            the received pair
     */
    public static void store(UnlockThresholdsPayload payload) {
        duo = payload.duoLevel();
        trio = payload.trioLevel();
    }

    /**
     * The client's threshold for one gate, or -1 while unknown (D83's abstain).
     *
     * @param trioGate
     *            true for the Trio threshold, false for Duo
     * @return the stored threshold, or -1 before the payload arrives
     */
    public static int threshold(boolean trioGate) {
        return trioGate ? trio : duo;
    }

    /** Returns the registered payload identity. */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
