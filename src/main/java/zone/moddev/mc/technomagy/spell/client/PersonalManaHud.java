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
    private static final int SEGMENT_WIDTH = 96;
    private static final java.util.List<ManaType> CLASSICAL = java.util.List.of(
        ManaType.EARTH, ManaType.AIR, ManaType.FIRE, ManaType.WATER);
    private static final java.util.List<ManaType> POLAR = java.util.List.of(
        ManaType.LIGHT, ManaType.DARK);
    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 17;
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
     * balances are never summed. At narrow GUI sizes, move above the vanilla status area rather
     * than covering its hotbar, health, or armor. Snapshot order remains independent of row order.
     */
    static void draw(GuiGraphics gui, ManaPoolSnapshot snapshot) {
        int right = gui.guiWidth() - MARGIN;
        int width = Math.min(SEGMENT_WIDTH * CLASSICAL.size(), Math.max(0, right - MARGIN));
        int left = right - width;
        int bottomMargin = left < gui.guiWidth() / 2 + 91 + MARGIN ? 60 : MARGIN;
        int top = gui.guiHeight() - bottomMargin - ROW_HEIGHT * 2;
        drawRow(gui, snapshot, CLASSICAL, left, top, width);
        drawRow(gui, snapshot, POLAR, left, top + ROW_HEIGHT, width);
    }

    /** Draws independently filled segments with a one-pixel gap and labels fitted to each cell. */
    private static void drawRow(GuiGraphics gui, ManaPoolSnapshot snapshot,
        java.util.List<ManaType> elements, int left, int y, int width) {
        var font = Minecraft.getInstance().font;
        for (int index = 0; index < elements.size(); index++) {
            var element = elements.get(index);
            var bar = snapshot.bars().get(ManaType.FLAVORS.indexOf(element));
            int x = left + index * width / elements.size();
            int right = left + (index + 1) * width / elements.size() - 1;
            int segmentWidth = Math.max(0, right - x);
            var label = Component.translatable("hud.technomagy.mana_pool",
                Component.translatable("mana.technomagy." + element.getSerializedName()),
                bar.balance(), bar.capacity());
            if (!bar.active()) {
                label.append(Component.translatable("hud.technomagy.mana_pool_disabled"));
            }
            int color = bar.active() ? color(element) : 0xffaaaaaa;
            // Fit localized names and large numbers without spilling into adjacent elements.
            float scale = Math.min(1.0f, (float) segmentWidth / Math.max(1, font.width(label)));
            if (scale > 0) {
                gui.pose().pushPose();
                gui.pose().translate(x, y, 0);
                gui.pose().scale(scale, scale, 1);
                gui.drawString(font, label, 0, 0, color);
                gui.pose().popPose();
            }
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
