package zone.moddev.mc.technomagy.spell.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import zone.moddev.mc.technomagy.mana.ManaType;
import zone.moddev.mc.technomagy.spell.ManaPoolSnapshot;

/**
 * Client-thread-owned D75 bottom-right pool display, adapted from BattleMageDemo's MageClient at
 * commit 0952f35707b55e4f15544400b473fad2fe4349fc. It displays server snapshots only and never
 * predicts balances, grants power, or performs gameplay operations.
 */
@EventBusSubscriber(modid = "technomagy", value = Dist.CLIENT)
public final class PersonalManaHud {
    private static final int WIDTH = 160;
    private static final int VANILLA_HOTBAR_HALF_WIDTH = 91;
    private static final java.util.List<ManaType> CLASSICAL = java.util.List.of(
        ManaType.EARTH, ManaType.AIR, ManaType.FIRE, ManaType.WATER);
    private static final java.util.List<ManaType> POLAR = java.util.List.of(
        ManaType.LIGHT, ManaType.DARK);
    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 27;
    private static ManaPoolSnapshot current;

    private PersonalManaHud() {
    }

    /** Accepts a validated server snapshot on the client thread (D75). */
    public static void receive(ManaPoolSnapshot snapshot) {
        current = java.util.Objects.requireNonNull(snapshot);
    }

    /** Clears balances before another server or world can display stale personal data. */
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        current = null;
    }

    /** Renders localized labels and bounded fills, anchored to the current scaled screen size. */
    private static void render(GuiGraphics gui, DeltaTracker time) {
        var minecraft = Minecraft.getInstance();
        var snapshot = current;
        if (snapshot == null || minecraft.player == null || minecraft.options.hideGui
            || minecraft.player.isSpectator() || minecraft.screen != null
            || snapshot.bars().stream().anyMatch(bar -> !bar.configured())) {
            return;
        }
        draw(gui, snapshot);
    }

    /**
     * Draws two equal-width segmented rows (D75). Each segment normalizes only its own pool;
     * balances are never summed. The whole HUD fits in the gutter to the right of the vanilla
     * hotbar, rather than moving toward the screen center at larger GUI scales. Names and amounts
     * occupy separate lines to stay legible in compact segments. Snapshot order is independent of
     * row order.
     */
    static void draw(GuiGraphics gui, ManaPoolSnapshot snapshot) {
        int right = gui.guiWidth() - MARGIN;
        int gutter = right - (gui.guiWidth() / 2 + VANILLA_HOTBAR_HALF_WIDTH + MARGIN);
        int width = Math.min(WIDTH, Math.max(0, gutter));
        if (width == 0) {
            return;
        }
        int left = right - width;
        int top = gui.guiHeight() - MARGIN - ROW_HEIGHT * 2;
        drawRow(gui, snapshot, CLASSICAL, left, top, width);
        drawRow(gui, snapshot, POLAR, left, top + ROW_HEIGHT, width);
    }

    /** Draws independently filled segments with a one-pixel gap and labels fitted to each cell. */
    private static void drawRow(GuiGraphics gui, ManaPoolSnapshot snapshot,
        java.util.List<ManaType> elements, int left, int y, int width) {
        for (int index = 0; index < elements.size(); index++) {
            var element = elements.get(index);
            var bar = snapshot.bars().get(ManaType.FLAVORS.indexOf(element));
            int x = left + index * width / elements.size();
            int right = left + (index + 1) * width / elements.size() - 1;
            int segmentWidth = Math.max(0, right - x);
            var label = Component.translatable("mana.technomagy." + element.getSerializedName());
            var amount = Component.translatable("hud.technomagy.mana_pool_amount",
                bar.balance(), bar.capacity());
            if (!bar.active()) {
                label.append(Component.translatable("hud.technomagy.mana_pool_disabled"));
            }
            int color = bar.active() ? color(element) : 0xffaaaaaa;
            drawLabel(gui, label, x, y, segmentWidth, color);
            drawLabel(gui, amount, x, y + 16, segmentWidth, color);
            gui.fill(x, y + 10, right, y + 14, 0xcc191a20);
            int fill = bar.capacity() == 0
                ? 0
                : (int) Math.round(
                    segmentWidth * Math.min(1.0, (double) bar.balance() / bar.capacity()));
            if (bar.active() && fill > 0) {
                gui.fill(x, y + 10, x + fill, y + 14, color);
            }
        }
    }

    /** Fits a localized label into its segment without spilling into its neighbors. */
    private static void drawLabel(GuiGraphics gui, Component label, int x, int y,
        int width, int color) {
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1.0f, (float) width / Math.max(1, font.width(label)));
        if (scale <= 0) {
            return;
        }
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);
        gui.pose().scale(scale, scale, 1);
        gui.drawString(font, label, 0, 0, color);
        gui.pose().popPose();
    }

    /** Returns each elemental hue; Raw has no personal pool and is rejected. */
    private static int color(ManaType element) {
        return switch (element) {
            case FIRE -> 0xfff3764d;
            case EARTH -> 0xff9bc66a;
            case AIR -> 0xffb7dbdc;
            case WATER -> 0xff4ba4f4;
            case LIGHT -> 0xfff0ead6;
            case DARK -> 0xff333333;
            case RAW -> throw new IllegalArgumentException("Raw has no personal mana HUD bar");
        };
    }

    /** Client mod-bus registration keeps the receiver and display cache off dedicated servers. */
    @EventBusSubscriber(modid = "technomagy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        /** Registers the HUD above vanilla layers. */
        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath("technomagy", "personal_mana"),
                PersonalManaHud::render);
        }

    }
}
