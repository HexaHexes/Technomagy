package zone.moddev.mc.technomagy.spell.client;

import java.util.List;
import java.util.Objects;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
 * Client-thread-owned D75 display of opposing elemental pools. Each chamber animates towards the
 * last server reading. Labels show the authoritative values as soon as a snapshot arrives.
 */
@EventBusSubscriber(modid = "technomagy", value = Dist.CLIENT)
public final class PersonalManaHud {
    private static final int WIDTH = 160;
    private static final int VANILLA_HOTBAR_HALF_WIDTH = 91;
    private static final int MARGIN = 8;
    private static final int MIN_ROW_WIDTH = 126;
    private static final int MIN_STACK_WIDTH = 48;
    private static final int STACK_ORB_SIZE = 36;
    private static final int STACK_ROW_HEIGHT = 39;
    private static final int TEXT_LINE_HEIGHT = 9;
    private static final int POOL_LABEL_HEIGHT = 2 * TEXT_LINE_HEIGHT;
    private static final int MIN_PAIRED_LABEL_WIDTH = 54;
    private static final int MAX_ORB_SIZE = 48;
    private static final int ANIMATION_MILLIS = 240;
    // The atlas stacks four square tiles. Glass bounds exclude the outer metal ring.
    private static final int TILE = 96;
    private static final int ATLAS_HEIGHT = TILE * 4;
    private static final int FRAME_TILE = 0;
    private static final int LEFT_LIQUID_TILE = 1;
    private static final int RIGHT_LIQUID_TILE = 2;
    private static final int GLASS_TILE = 3;
    private static final int GLASS_TOP = 11;
    private static final int GLASS_BOTTOM = 85;
    private static final ResourceLocation ORB_TEXTURE = ResourceLocation
        .fromNamespaceAndPath("technomagy", "textures/gui/mana_orb.png");
    private static final List<Opposites> PAIRS = List.of(
        new Opposites(ManaType.EARTH, ManaType.AIR),
        new Opposites(ManaType.FIRE, ManaType.WATER),
        new Opposites(ManaType.LIGHT, ManaType.DARK));
    private static final float[] startFills = new float[ManaType.FLAVORS.size()];
    private static ManaPoolSnapshot current;
    private static long animationStarted;

    /** Two independent chambers in one orb, ordered from left to right (D14, D75). */
    private record Opposites(ManaType left, ManaType right) {
    }

    /** One element name and its exact balance, laid out on separate lines. */
    private record PoolLabel(Component name, Component balance, int nameColor, int balanceColor) {
    }

    private PersonalManaHud() {
    }

    /** Accepts a validated server snapshot on the client thread (D75). */
    public static void receive(ManaPoolSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        long now = Util.getMillis();
        float progress = animationProgress(now);
        for (var element : ManaType.FLAVORS) {
            startFills[element.ordinal()] = current == null
                ? fraction(bar(snapshot, element))
                : displayedFill(current, element, progress);
        }
        current = snapshot;
        animationStarted = now;
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
        draw(gui, snapshot, animationProgress(Util.getMillis()));
    }

    /**
     * Draws a settled snapshot for the disposable client layout check. Uses the same renderer as
     * gameplay, with no client animation state required.
     */
    static void draw(GuiGraphics gui, ManaPoolSnapshot snapshot) {
        draw(gui, snapshot, 1);
    }

    /** Anchors three orbs within the right gutter and stacks them when the gutter narrows. */
    private static void draw(GuiGraphics gui, ManaPoolSnapshot snapshot, float progress) {
        int right = gui.guiWidth() - MARGIN;
        int gutter = right - (gui.guiWidth() / 2 + VANILLA_HOTBAR_HALF_WIDTH + MARGIN);
        int width = Math.min(WIDTH, gutter);
        int bottom = gui.guiHeight() - MARGIN;
        boolean wide = width >= MIN_ROW_WIDTH;
        if (width < MIN_STACK_WIDTH
            || (!wide && bottom < STACK_ROW_HEIGHT * PAIRS.size() + MARGIN)) {
            return;
        }
        int left = right - width;
        var font = Minecraft.getInstance().font;
        var labels = ManaType.FLAVORS.stream().map(element -> label(snapshot, element)).toList();
        if (wide) {
            int cell = width / PAIRS.size();
            int size = Math.min(MAX_ORB_SIZE, cell - 4);
            int textTop = bottom - POOL_LABEL_HEIGHT;
            float scale = labelScale(font, labels, cell / 2 - 2);
            for (int i = 0; i < PAIRS.size(); i++) {
                var pair = PAIRS.get(i);
                int x = left + i * cell;
                drawOrb(gui, snapshot, pair, x + (cell - size) / 2, textTop - size - 2,
                    size, progress);
                drawLabels(gui, font, labels, pair, x, textTop, cell, true, scale);
            }
        } else {
            int size = Math.min(STACK_ORB_SIZE, width / 2);
            int top = bottom - STACK_ROW_HEIGHT * PAIRS.size();
            int textLeft = left + size + 4;
            int textWidth = right - textLeft;
            boolean sideBySide = textWidth >= MIN_PAIRED_LABEL_WIDTH;
            int captionWidth = sideBySide ? textWidth / 2 : textWidth;
            int captionHeight = sideBySide ? POOL_LABEL_HEIGHT : 2 * POOL_LABEL_HEIGHT;
            float scale = labelScale(font, labels, captionWidth - 2);
            int textOffset = (STACK_ROW_HEIGHT - captionHeight) / 2;
            for (int i = 0; i < PAIRS.size(); i++) {
                var pair = PAIRS.get(i);
                int y = top + i * STACK_ROW_HEIGHT;
                drawOrb(gui, snapshot, pair, left, y + (STACK_ROW_HEIGHT - size) / 2,
                    size, progress);
                drawLabels(gui, font, labels, pair, textLeft, y + textOffset, textWidth,
                    sideBySide, scale);
            }
        }
    }

    /** Composites the frame, independently clipped liquid panes, and a glass reflection. */
    private static void drawOrb(GuiGraphics gui, ManaPoolSnapshot snapshot, Opposites pair,
        int x, int y, int size, float progress) {
        var pose = gui.pose();
        pose.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            pose.translate(x, y, 0);
            pose.scale((float) size / TILE, (float) size / TILE, 1);
            blitTile(gui, FRAME_TILE, 0, TILE);
            drawLiquid(gui, pair.left(), LEFT_LIQUID_TILE,
                displayedFill(snapshot, pair.left(), progress));
            drawLiquid(gui, pair.right(), RIGHT_LIQUID_TILE,
                displayedFill(snapshot, pair.right(), progress));
            blitTile(gui, GLASS_TILE, 0, TILE);
        } finally {
            RenderSystem.disableBlend();
            pose.popPose();
        }
    }

    /**
     * Crops one pane vertically. The atlas already masks the sphere and curved divider, so the fill
     * never reaches the other chamber. A narrow highlight marks a partial liquid surface.
     */
    private static void drawLiquid(GuiGraphics gui, ManaType element, int tile, float fraction) {
        if (fraction <= 0) {
            return;
        }
        int top = fraction >= 1
            ? 0
            : GLASS_BOTTOM - Math.max(1, Math.round((GLASS_BOTTOM - GLASS_TOP) * fraction));
        int color = color(element);
        float red = ((color >> 16) & 255) / 255f;
        float green = ((color >> 8) & 255) / 255f;
        float blue = (color & 255) / 255f;
        try {
            gui.setColor(red, green, blue, 1);
            blitTile(gui, tile, top, TILE - top);
            if (top != 0) {
                gui.setColor(red * 0.7f + 0.3f, green * 0.7f + 0.3f, blue * 0.7f + 0.3f, 0.85f);
                blitTile(gui, tile, top, 2);
            }
        } finally {
            gui.setColor(1, 1, 1, 1);
        }
    }

    private static void blitTile(GuiGraphics gui, int tile, int top, int height) {
        gui.blit(ORB_TEXTURE, 0, top, 0, tile * TILE + top, TILE, height, TILE, ATLAS_HEIGHT);
    }

    /** Builds separate name and balance lines. Muted text marks inactive chambers. */
    private static PoolLabel label(ManaPoolSnapshot snapshot, ManaType element) {
        var bar = bar(snapshot, element);
        var name = Component.translatable("mana.technomagy." + element.getSerializedName());
        var balance = Component.translatable("hud.technomagy.mana_pool_amount",
            bar.balance(), bar.capacity());
        int muted = 0xffaaaaaa;
        return new PoolLabel(name, balance, bar.active() ? color(element) : muted,
            bar.active() ? 0xffe9e4d8 : muted);
    }

    /** Fits every line to one shared scale, including localized names and large balances. */
    private static float labelScale(Font font, List<PoolLabel> labels, int width) {
        int widest = 1;
        for (var label : labels) {
            widest = Math.max(widest,
                Math.max(font.width(label.name()), font.width(label.balance())));
        }
        return Math.min(1f, (float) width / widest);
    }

    /** Aligns each caption with its chamber, with a vertical fallback for narrow captions. */
    private static void drawLabels(GuiGraphics gui, Font font, List<PoolLabel> labels,
        Opposites pair, int x, int y, int width, boolean sideBySide, float scale) {
        int cell = sideBySide ? width / 2 : width;
        drawLabel(gui, font, labels.get(pair.left().ordinal()), x, y, cell, scale);
        drawLabel(gui, font, labels.get(pair.right().ordinal()),
            sideBySide ? x + cell : x, sideBySide ? y : y + POOL_LABEL_HEIGHT, cell, scale);
    }

    private static void drawLabel(GuiGraphics gui, Font font, PoolLabel label,
        int x, int y, int width, float scale) {
        drawText(gui, font, label.name(), x, y, width, scale, label.nameColor());
        drawText(gui, font, label.balance(), x, y + TEXT_LINE_HEIGHT, width, scale,
            label.balanceColor());
    }

    private static void drawText(GuiGraphics gui, Font font, Component text,
        int x, int y, int width, float scale, int color) {
        gui.pose().pushPose();
        gui.pose().translate(Math.round(x + (width - font.width(text) * scale) / 2), y, 0);
        gui.pose().scale(scale, scale, 1);
        gui.drawString(font, text, 0, 0, color);
        gui.pose().popPose();
    }

    private static ManaPoolSnapshot.Bar bar(ManaPoolSnapshot snapshot, ManaType element) {
        return snapshot.bars().get(element.ordinal());
    }

    private static float fraction(ManaPoolSnapshot.Bar bar) {
        return bar.active() && bar.capacity() > 0
            ? (float) Math.min(1, (double) bar.balance() / bar.capacity())
            : 0;
    }

    /** Smoothstep eases towards the latest amount and comes to rest at the end of the animation. */
    private static float animationProgress(long now) {
        float elapsed = Math.min(1f, (float) (now - animationStarted) / ANIMATION_MILLIS);
        return elapsed * elapsed * (3 - 2 * elapsed);
    }

    private static float displayedFill(ManaPoolSnapshot snapshot, ManaType element,
        float progress) {
        var bar = bar(snapshot, element);
        if (!bar.active() || progress >= 1) {
            return fraction(bar);
        }
        float start = startFills[element.ordinal()];
        return start + (fraction(bar) - start) * progress;
    }

    /** Returns each elemental hue, with violet charcoal to keep Dark legible against the glass. */
    private static int color(ManaType element) {
        return switch (element) {
            case FIRE -> 0xfff3764d;
            case EARTH -> 0xff9bc66a;
            case AIR -> 0xffb7dbdc;
            case WATER -> 0xff4ba4f4;
            case LIGHT -> 0xfff0ead6;
            case DARK -> 0xffa88ac9;
            case RAW -> throw new IllegalArgumentException("Raw has no personal mana HUD chamber");
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
