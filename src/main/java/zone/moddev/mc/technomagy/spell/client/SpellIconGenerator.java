package zone.moddev.mc.technomagy.spell.client;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 * from the shared placeholder and the element glyphs the common side resolved, at the count's
 * layout - diagonal, triangle, cross - so a resource pack that reskins an element symbol or the
 * placeholder flows into the generated icon. Client-state only: reads the live resource stack,
 * registers one dynamic texture per ability, and drops the whole cache when resources reload so a
 * pack swap regenerates. Nothing throws to the caller - an unreadable source answers null and the
 * documented resolution order falls to the definition's static icon.
 */
@EventBusSubscriber(modid = "technomagy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SpellIconGenerator {

    /** The shared placeholder each generated icon composes over (the old static spell icon). */
    private static final ResourceLocation PLACEHOLDER = ResourceLocation
        .fromNamespaceAndPath("minecraft", "textures/item/blaze_rod.png");

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
        BufferedImage placeholder = read(minecraft, PLACEHOLDER);
        if (placeholder == null) {
            return null;
        }
        BufferedImage composed = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = composed.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(placeholder, 0, 0, 32, 32, null);
            for (int i = 0; i < glyphs.size(); i++) {
                BufferedImage glyph = read(minecraft, glyphs.get(i));
                if (glyph == null) {
                    return null;
                }
                graphics.drawImage(glyph, slots[i][0], slots[i][1], null);
            }
        } finally {
            graphics.dispose();
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
