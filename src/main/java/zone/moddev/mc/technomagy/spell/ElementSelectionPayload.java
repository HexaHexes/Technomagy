package zone.moddev.mc.technomagy.spell;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client selection intent; the server checks the actual held focus before modifying its data.
 *
 * @param element
 *            stable mana identity, including Raw (D73)
 * @param offhand
 *            whether the selected focus is held in the offhand
 */
public record ElementSelectionPayload(ResourceLocation element, boolean offhand)
    implements
        CustomPacketPayload {
    /** Rejects an absent identity before encoding or handling. */
    public ElementSelectionPayload {
        java.util.Objects.requireNonNull(element);
    }

    /**
     * Typed identity for element selection, separate from activation and never a world-effect
     * packet.
     */
    public static final Type<ElementSelectionPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("technomagy", "select_element"));

    /** Wire codec sends the stable mana ID and hand, never a trusted item stack or target. */
    public static final StreamCodec<ByteBuf, ElementSelectionPayload> CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC,
        ElementSelectionPayload::element,
        ByteBufCodecs.BOOL,
        ElementSelectionPayload::offhand,
        ElementSelectionPayload::new);

    /** Identifies this payload to NeoForge's channel registry. */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Enqueues validation and mutation on the owning server thread. */
    static void handle(ElementSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(
            () -> {
                if (!(context.player() instanceof ServerPlayer player)) {
                    return;
                }
                var element = java.util.Arrays
                    .stream(zone.moddev.mc.technomagy.mana.ManaType.values())
                    .filter(type -> payload.element().equals(ResourceLocation.fromNamespaceAndPath(
                        "technomagy", type.getSerializedName())))
                    .findFirst();
                InteractionHand hand = payload.offhand()
                    ? InteractionHand.OFF_HAND
                    : InteractionHand.MAIN_HAND;
                var stack = player.getItemInHand(hand);
                if (element.isPresent() && stack.is(SpellContent.FOCUS.get())) {
                    SpellFocusItem.selectElement(stack, element.orElseThrow());
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "mana.technomagy." + element.orElseThrow().getSerializedName()), true);
                }
            });
    }
}
