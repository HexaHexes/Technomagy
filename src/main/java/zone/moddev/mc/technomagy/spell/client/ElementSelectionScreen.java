package zone.moddev.mc.technomagy.spell.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

import net.neoforged.neoforge.network.PacketDistributor;

import zone.moddev.mc.technomagy.mana.ManaType;
import zone.moddev.mc.technomagy.spell.ElementSelectionPayload;

/** Client-only selection menu, containing the seven directed mana types (D73). */
final class ElementSelectionScreen extends Screen {
    private final InteractionHand hand;

    /** Remembers which hand the server must revalidate when selection is submitted. */
    ElementSelectionScreen(InteractionHand hand) {
        super(Component.translatable("screen.technomagy.element_selection"));
        this.hand = hand;
    }

    /** Builds localized, keyboard-navigable buttons without predicting server selection state. */
    @Override
    protected void init() {
        ManaType[] elements = ManaType.values();
        int buttonWidth = Math.min(140, (width - 30) / 2);
        int rowHeight = Math.min(22, Math.max(12, (height - 45) / 4));
        int top = Math.max(25, (height - rowHeight * 4) / 2);
        for (int index = 0; index < elements.length; index++) {
            ManaType element = elements[index];
            int x = width / 2 + (index % 2 == 0 ? -buttonWidth - 3 : 3);
            int y = top + index / 2 * rowHeight;
            addRenderableWidget(
                Button.builder(
                    Component.translatable("mana.technomagy." + element.getSerializedName()),
                    button -> {
                        PacketDistributor.sendToServer(
                            new ElementSelectionPayload(
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    "technomagy", element.getSerializedName()),
                                hand == InteractionHand.OFF_HAND));
                        onClose();
                    })
                    .bounds(x, y, buttonWidth, rowHeight - 1)
                    .build());
        }
    }

    /** Draws the standard screen and its title. */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xffffff);
    }

    /** Selection never pauses multiplayer or changes the server's tick ownership. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
