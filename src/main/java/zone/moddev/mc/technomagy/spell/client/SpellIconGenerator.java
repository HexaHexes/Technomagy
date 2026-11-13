package zone.moddev.mc.technomagy.spell.client;

import java.awt.image.BufferedImage;

import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;

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

import org.slf4j.Logger;

/**
 * Render-time icon generation for the sixteen spells (D82): composes a duo, trio or quadra icon
 * from the element glyphs the common side resolved, laid out on a transparent 32x32 canvas at the
 * count's layout - diagonal, triangle, X - so a resource pack that reskins an element symbol flows
 * into the generated icon. Client-state only: reads the live resource stack, registers one dynamic
 * texture per ability, and drops the whole cache when resources reload so a pack swap regenerates.
 * Nothing throws to the caller - an unreadable source answers null and the documented resolution
 * order falls to the definition's static icon.
 *
 * <p>
 * The baked static icons are this same composition: the {@code runSpellIconChecks} Gradle check
 * (class {@code SpellIconChecks}) composes every spell from the committed glyph files through
 * {@link #composeCanvas} and asserts pixel equality against the committed PNGs, so the generated
 * texture and the fall-back file cannot drift apart - and running that check with its write flag
 * regenerates the baked files from this very code, with no second copy of the math anywhere.
 * </p>
 */
@EventBusSubscriber(modid = "technomagy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SpellIconGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The box each element glyph is drawn into, matching the baked static icons (D82). */
    private static final int GLYPH_SIZE = 15;

    /**
     * The icon canvas edge in pixels (D82): baked files and generated textures are both square on
     * it, in this source set and in the baked-icon check.
     */
    public static final int CANVAS_SIZE = 32;

    /**
     * Layout slots per glyph count (D82): each 15x15 box packed one pixel from the canvas edge, so
     * a duo reads as a diagonal, a trio as a triangle and a quadra as a diagonal X - no two boxes
     * overlap at the larger size (a plus-sign cross would force its four arms to collide in the
     * middle). The glyphs fill these slots in the spell's own stream order.
     */
    private static final Map<Integer, int[][]> LAYOUTS = Map.of(
        2, new int[][]{{1, 1}, {16, 16}},
        3, new int[][]{{8, 1}, {1, 16}, {16, 16}},
        4, new int[][]{{1, 1}, {16, 1}, {1, 16}, {16, 16}});

    /**
     * Generated texture ids by ability (render thread only - {@link #compose} refuses any other
     * thread), dropped on resource reload so a pack swap regenerates. A refusal is cached as an
     * empty optional beside a registered id, so an unreadable or rejected glyph is diagnosed once
     * per reload instead of every frame the hotbar draw chain asks the provider.
     */
    private static final Map<ResourceLocation, Optional<ResourceLocation>> icons = new HashMap<>();

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
                    icons.values().stream().flatMap(Optional::stream)
                        .forEach(minecraft.getTextureManager()::release);
                }
                icons.clear();
            }
        });
    }

    /**
     * Composes and registers one ability's icon, or answers null when it cannot - the resolution
     * order then falls to the definition's static icon. Every answer, including a refusal, is
     * cached against the ability id until resources reload, so a repeated ask is a map lookup
     * rather than a PNG decode, a box scale and a texture upload. The cache and the texture manager
     * belong to the render thread, so any other thread (an integrated-server ask, say) answers null
     * without touching either and the render thread answers on the next frame.
     *
     * @param abilityId
     *            the ability being drawn (names the generated texture)
     * @param glyphs
     *            the element glyph textures in the spell's stream order
     * @return the registered dynamic texture's id, or null for no answer, including an answer
     *         requested off the render thread
     */
    public static ResourceLocation compose(ResourceLocation abilityId,
        List<ResourceLocation> glyphs) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null; // no client yet - there is nothing to cache an answer against
        }
        if (!minecraft.isSameThread()) {
            return null; // the render thread owns the cache and the texture manager
        }
        Optional<ResourceLocation> cached = icons.get(abilityId);
        if (cached != null) {
            return cached.orElse(null);
        }
        ResourceLocation id = composeAndRegister(minecraft, abilityId, glyphs);
        icons.put(abilityId, Optional.ofNullable(id));
        return id;
    }

    /**
     * Whether a glyph count has a layout slot set (D82): the one executable statement of the
     * system's glyph-count range, read by the provider, the generator itself and the baked-icon
     * check so they cannot drift - a future one-glyph layout for single-element casts would be
     * added to {@code LAYOUTS} and nowhere else.
     *
     * @param glyphCount
     *            how many glyphs the caller proposes to compose
     * @return true when the layout table holds slots for that count
     */
    public static boolean hasLayout(int glyphCount) {
        return LAYOUTS.containsKey(glyphCount);
    }

    /**
     * The uncached half of {@link #compose}: decodes the glyphs from the live resource stack,
     * composes them and registers the result, leaving the caller to store the answer.
     *
     * @param minecraft
     *            the live client whose resource stack and texture manager are used
     * @param abilityId
     *            the ability being drawn (names the generated texture)
     * @param glyphs
     *            the element glyph textures in the spell's stream order
     * @return the registered dynamic texture's id, or null when the count has no layout or a glyph
     *         is unreadable or not square
     */
    private static ResourceLocation composeAndRegister(Minecraft minecraft,
        ResourceLocation abilityId, List<ResourceLocation> glyphs) {
        if (!hasLayout(glyphs.size())) {
            return null; // a single-element spell wears its entry's icon; deeper counts abstain
        }
        List<BufferedImage> decoded = new ArrayList<>(glyphs.size());
        for (ResourceLocation glyph : glyphs) {
            BufferedImage image = read(minecraft, glyph);
            if (image == null) {
                return null;
            }
            decoded.add(image);
        }
        BufferedImage composed = composeCanvas(decoded);
        if (composed == null) {
            // composeCanvas has already reported the reason (a non-square glyph); name the caller
            LOGGER.warn("The icon for {} could not be composed; it falls back to its static file",
                abilityId);
            return null;
        }
        NativeImage pixels = new NativeImage(CANVAS_SIZE, CANVAS_SIZE, true);
        for (int y = 0; y < CANVAS_SIZE; y++) {
            for (int x = 0; x < CANVAS_SIZE; x++) {
                pixels.setPixelRGBA(x, y, toAbgr(composed.getRGB(x, y)));
            }
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("technomagy",
            "generated/" + abilityId.getPath());
        minecraft.getTextureManager().register(id, new DynamicTexture(pixels));
        return id;
    }

    /**
     * Composes the 32x32 icon canvas from already-decoded glyphs: each glyph box-averaged into a
     * 15x15 box and written at its count's layout slot, in list order (the spell's stream order).
     * Pure integer math with no client state - it touches neither the resource stack nor the
     * texture manager - so the baked-icon check runs the very code the game runs. Glyphs of any
     * square size compose (small ones are nearest-sampled up, large ones box-averaged down); a
     * non-square source - an animated strip - is refused here, the one place both callers share,
     * and reported, so no caller can let a strip into the box scale.
     *
     * @param glyphs
     *            the decoded element glyphs in the spell's stream order
     * @return the composed canvas, or null when the glyph count has no layout or any glyph is not
     *         square
     */
    public static BufferedImage composeCanvas(List<BufferedImage> glyphs) {
        if (!hasLayout(glyphs.size())) {
            return null;
        }
        for (int i = 0; i < glyphs.size(); i++) {
            BufferedImage glyph = glyphs.get(i);
            if (glyph.getWidth() != glyph.getHeight()) {
                LOGGER.warn("Glyph #{} is {}x{} rather than square - an animated strip cannot be "
                    + "composed", i, glyph.getWidth(), glyph.getHeight());
                return null;
            }
        }
        int[][] slots = LAYOUTS.get(glyphs.size());
        BufferedImage composed = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE,
            BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < glyphs.size(); i++) {
            BufferedImage small = boxScale(glyphs.get(i), GLYPH_SIZE, GLYPH_SIZE);
            for (int dy = 0; dy < GLYPH_SIZE; dy++) {
                for (int dx = 0; dx < GLYPH_SIZE; dx++) {
                    composite(composed, slots[i][0] + dx, slots[i][1] + dy, small.getRGB(dx, dy));
                }
            }
        }
        return composed;
    }

    /**
     * Premultiplied box average into a dw x dh image: each destination pixel averages the source
     * block that maps to it, so thin glyph features (a symbol's top bar, the dot inside the Gold
     * ring) survive the downscale instead of being sampled away by nearest-neighbour. A source
     * smaller than the box (a pack's 8x8 reskin) would map some destination pixels to an empty
     * block, so the block is widened to at least one source pixel and that pixel is
     * nearest-sampled: any square size composes, the way the old drawImage path allowed, instead of
     * striping. Integer-only and renderer-independent, so the baked static icons regenerate pixel
     * for pixel from this same code (verified by the baked-icon check).
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
            int y1 = Math.max((dy + 1) * src.getHeight() / dh, y0 + 1);
            for (int dx = 0; dx < dw; dx++) {
                int x0 = dx * src.getWidth() / dw;
                int x1 = Math.max((dx + 1) * src.getWidth() / dw, x0 + 1);
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
     * Writes one downscaled glyph pixel at (x, y) unless it is fully transparent. The canvas starts
     * transparent and every layout slot is disjoint (the three layouts pack the 15x15 boxes edge to
     * edge without shared pixels), so each canvas pixel is written at most once: source-over onto
     * transparency reduces to a plain write, which also keeps the packed channels clear of the
     * overflow that separately-floored blend terms could reach. A future overlapping layout would
     * overwrite rather than blend.
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
        if ((argb >>> 24) == 0) {
            return;
        }
        target.setRGB(x, y, argb);
    }

    /** BufferedImage hands out ARGB; NativeImage's setPixelRGBA stores ABGR - swap red and blue. */
    private static int toAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >>> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    /**
     * Decodes one glyph from the live resource stack, reporting a malformed pack texture instead of
     * failing silently.
     *
     * @param minecraft
     *            the client whose resource stack is read
     * @param id
     *            the glyph texture id
     * @return the decoded image, or null when the resource is absent (the documented resolution
     *         order falls through) or undecodable (logged)
     */
    private static BufferedImage read(Minecraft minecraft, ResourceLocation id) {
        try {
            Optional<Resource> resource = minecraft.getResourceManager().getResource(id);
            if (resource.isEmpty()) {
                return null;
            }
            try (InputStream in = resource.get().open()) {
                BufferedImage image = ImageIO.read(in);
                if (image == null) {
                    LOGGER.warn("Glyph texture {} is not a decodable image; the icon falls back "
                        + "to the static file", id);
                }
                return image;
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Unreadable glyph texture {}; the icon falls back to the static file",
                id, e);
            return null; // an unreadable source answers no icon; the order falls through
        }
    }
}
