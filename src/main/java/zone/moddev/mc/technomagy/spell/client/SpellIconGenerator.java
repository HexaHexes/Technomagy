package zone.moddev.mc.technomagy.spell.client;

import java.awt.image.BufferedImage;

import java.io.IOException;
import java.io.InputStream;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/**
 * Render-time icon generation for the sixteen spells (D82): composes a duo, trio or quadra icon
 * from the element glyphs the common side resolved, laid out on a transparent 32x32 canvas at the
 * count's layout - diagonal, triangle, cross - so a resource pack that reskins an element symbol
 * flows into the generated icon. Client-state only: reads the live resource stack, registers one
 * dynamic texture per ability, and drops the whole cache when resources reload so a pack swap
 * regenerates. Nothing throws to the caller - an unreadable source answers null and the documented
 * resolution order falls to the definition's static icon.
 *
 * <p>
 * The baked static icons are the same composition produced by the same integer math (the D82 icon
 * regeneration pass mirrors {@link #boxScale} and {@link #composite} line for line): identical
 * glyph box, layout slots and downscale, so the generated texture and the fall-back file agree
 * pixel for pixel.
 * </p>
 */
@EventBusSubscriber(modid = "technomagy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SpellIconGenerator {

    /** The box each element glyph is drawn into, matching the baked static icons (D82). */
    private static final int GLYPH_SIZE = 10;

    /** Layout slots per glyph count, in the spell's own stream order (D82). */
    private static final Map<Integer, int[][]> LAYOUTS = Map.of(
        2, new int[][]{{2, 2}, {20, 20}},
        3, new int[][]{{11, 1}, {1, 20}, {20, 20}},
        4, new int[][]{{11, 1}, {20, 11}, {11, 20}, {1, 11}});

    /** Generated texture ids by ability, dropped on resource reload (client thread only). */
    private static final Map<ResourceLocation, ResourceLocation> generated = new HashMap<>();

    private SpellIconGenerator() {
    }

    /** Invalidates the cache after every resource reload so packs regenerate the icons. */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void prepared, ResourceManager manager, ProfilerFiller profiler) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft != null) {
                    generated.values().forEach(minecraft.getTextureManager()::release);
                }
                generated.clear();
            }
        });
    }

    /**
     * Composes and registers one ability's icon, or answers null when it cannot - the resolution
     * order then falls to the definition's static icon.
     *
     * @param abilityId
     *            the ability being drawn (names the generated texture)
     * @param glyphIcons
     *            the element glyph textures in the spell's stream order (2..4 of them)
     * @return the registered dynamic texture's id, or null for no answer
     */
    public static ResourceLocation compose(ResourceLocation abilityId,
        List<ResourceLocation> glyphs) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        int[][] slots = LAYOUTS.get(glyphs.size());
        if (slots == null) {
            return null; // a single-element spell wears its entry's icon; deeper counts abstain
        }
        BufferedImage composed = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < glyphs.size(); i++) {
            BufferedImage glyph = read(minecraft, glyphs.get(i));
            if (glyph == null) {
                return null;
            }
            BufferedImage small = boxScale(glyph, GLYPH_SIZE, GLYPH_SIZE);
            for (int dy = 0; dy < GLYPH_SIZE; dy++) {
                for (int dx = 0; dx < GLYPH_SIZE; dx++) {
                    composite(composed, slots[i][0] + dx, slots[i][1] + dy, small.getRGB(dx, dy));
                }
            }
        }
        NativeImage pixels = new NativeImage(32, 32, true);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                pixels.setPixelRGBA(x, y, toAbgr(composed.getRGB(x, y)));
            }
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("technomagy",
            "generated/" + abilityId.getPath());
        minecraft.getTextureManager().register(id, new DynamicTexture(pixels));
        return id;
    }

    /**
     * Premultiplied box average into a dw x dh image: each destination pixel averages the source
     * block that maps to it, so thin glyph features (a symbol's top bar, the dot inside the Gold
     * ring) survive the downscale instead of being sampled away by nearest-neighbour. Integer-only
     * and renderer-independent, so the baked static icons regenerate byte-identically from this
     * same code (the D82 icon regeneration pass mirrors it line for line).
     *
     * @param src
     *            the glyph texture at its native size
     * @param dw
     *            destination width in pixels
     * @param dh
     *            destination height in pixels
     * @return the downscaled glyph; fully transparent source blocks stay transparent
     */
    private static BufferedImage boxScale(BufferedImage src, int dw, int dh) {
        BufferedImage out = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
        for (int dy = 0; dy < dh; dy++) {
            int y0 = dy * src.getHeight() / dh;
            int y1 = (dy + 1) * src.getHeight() / dh;
            for (int dx = 0; dx < dw; dx++) {
                int x0 = dx * src.getWidth() / dw;
                int x1 = (dx + 1) * src.getWidth() / dw;
                long sumA = 0;
                long sumR = 0;
                long sumG = 0;
                long sumB = 0;
                int count = (y1 - y0) * (x1 - x0);
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        int p = src.getRGB(x, y);
                        int a = (p >>> 24) & 0xFF;
                        sumA += a;
                        sumR += ((p >>> 16) & 0xFF) * a;
                        sumG += ((p >>> 8) & 0xFF) * a;
                        sumB += (p & 0xFF) * a;
                    }
                }
                if (sumA == 0) {
                    continue;
                }
                int a = (int) (sumA / count);
                int r = (int) (sumR / sumA);
                int g = (int) (sumG / sumA);
                int b = (int) (sumB / sumA);
                out.setRGB(dx, dy, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    /**
     * Sources one downscaled glyph pixel over the canvas pixel at (x, y) - the canvas starts fully
     * transparent and glyph boxes may touch at a corner, so overlap blends instead of replacing.
     * Mirrored line for line by the baked-icon regeneration pass.
     *
     * @param target
     *            the 32x32 composition being built
     * @param x
     *            destination column
     * @param y
     *            destination row
     * @param argb
     *            the glyph pixel to draw
     */
    private static void composite(BufferedImage target, int x, int y, int argb) {
        int sa = (argb >>> 24) & 0xFF;
        if (sa == 0) {
            return;
        }
        int dst = target.getRGB(x, y);
        int da = (dst >>> 24) & 0xFF;
        int outA = sa + da * (255 - sa) / 255;
        if (outA == 0) {
            return;
        }
        int r = (((argb >>> 16) & 0xFF) * sa + ((dst >>> 16) & 0xFF) * da * (255 - sa) / 255)
            / outA;
        int g = (((argb >>> 8) & 0xFF) * sa + ((dst >>> 8) & 0xFF) * da * (255 - sa) / 255)
            / outA;
        int b = ((argb & 0xFF) * sa + (dst & 0xFF) * da * (255 - sa) / 255) / outA;
        target.setRGB(x, y, (outA << 24) | (r << 16) | (g << 8) | b);
    }

    /** BufferedImage hands out ARGB; NativeImage's setPixelRGBA stores ABGR - swap red and blue. */
    private static int toAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >>> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    private static BufferedImage read(Minecraft minecraft, ResourceLocation id) {
        try {
            Optional<Resource> resource = minecraft.getResourceManager().getResource(id);
            if (resource.isEmpty()) {
                return null;
            }
            try (InputStream in = resource.get().open()) {
                return ImageIO.read(in);
            }
        } catch (IOException | RuntimeException e) {
            return null; // an unreadable source answers no icon; the order falls through
        }
    }
}
