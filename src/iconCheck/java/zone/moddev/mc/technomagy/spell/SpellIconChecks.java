package zone.moddev.mc.technomagy.spell;

import java.awt.image.BufferedImage;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.technomagy.spell.client.SpellIconGenerator;

/**
 * Verifies the baked static spell icons (D82) against the code that composes them: for every spell
 * whose element count has a layout, it loads the committed element glyphs, runs them through
 * {@link SpellIconGenerator#composeCanvas} - the very method the render-time provider composes with
 * - and compares the result pixel for pixel with the committed PNG. The generated texture and the
 * static fallback therefore cannot drift apart, and nobody needs a second copy of the math to
 * regenerate the baked files: this check replaces the out-of-tree mirror that produced them.
 *
 * <p>
 * Runs as the {@code runSpellIconChecks} Gradle task, hooked into {@code check}. When the
 * composition itself changes, the separate {@code blessSpellIcons} Gradle task rewrites the
 * committed PNGs from this same code instead of comparing them - deliberately outside
 * {@code check}, so no build can rewrite resources by accident. Output files are named after the
 * ability id, lowercase - an uppercase name survives NTFS's case-insensitive overwrite, hides from
 * git, and breaks the jar's case-sensitive resource lookup as a missing texture in game, so every
 * file this check touches is required to exist under its exact name.
 * </p>
 *
 * <p>
 * A failure throws, so the task exits non-zero and the build fails. No Minecraft bootstrap is
 * needed: the touched classes are the spell enum, the glyph mapping and the pure integer
 * composition math, none of which read client or registry state.
 * </p>
 */
public final class SpellIconChecks {

    /** Where the mod's resources live; the Gradle task passes the absolute path. */
    private static final String RESOURCES_PROPERTY = "technomagy.resources";

    /** Set by the {@code blessSpellIcons} Gradle task to rewrite instead of compare. */
    private static final String BLESS_PROPERTY = "technomagy.blessSpellIcons";

    private SpellIconChecks() {
    }

    /**
     * Composes every spell's icon from the committed glyphs and compares it with the committed
     * baked PNG (or rewrites that PNG when the bless task runs it).
     *
     * @param args
     *            unused
     * @throws IOException
     *             if a committed PNG cannot be read or written
     * @throws IllegalStateException
     *             if the resources property is missing, a glyph source is missing, undecodable, not
     *             square or present only under a different case, a spell with a layout composes to
     *             nothing, no spell was checked at all, no PNG writer accepts a blessed file, or a
     *             committed icon differs from its composition
     */
    public static void main(String[] args) throws IOException {
        String resourcesPath = System.getProperty(RESOURCES_PROPERTY);
        if (resourcesPath == null) {
            throw new IllegalStateException("missing -D" + RESOURCES_PROPERTY + "; run this "
                + "through ./gradlew runSpellIconChecks or ./gradlew blessSpellIcons");
        }
        Path resources = Path.of(resourcesPath);
        boolean bless = Boolean.getBoolean(BLESS_PROPERTY);
        int verified = 0;
        for (PlayerSpell spell : PlayerSpell.values()) {
            List<ResourceLocation> glyphs = SpellIcons.glyphsFor(spell);
            if (!SpellIconGenerator.hasLayout(glyphs.size())) {
                continue; // the provider abstains for this count, so no icon is baked either
            }
            List<BufferedImage> decoded = new ArrayList<>(glyphs.size());
            for (ResourceLocation glyph : glyphs) {
                decoded.add(load(resources, glyph));
            }
            BufferedImage composed;
            try {
                composed = SpellIconGenerator.composeCanvas(decoded);
            } catch (SpellIconGenerator.GlyphRefusalException refusal) {
                throw new IllegalStateException(spell + ": glyph "
                    + glyphs.get(refusal.glyphIndex()) + " is " + refusal.width() + "x"
                    + refusal.height() + " rather than square; baked icons need square glyph "
                    + "sources", refusal);
            }
            if (composed == null) {
                throw new IllegalStateException(spell + ": composeCanvas answered nothing although "
                    + "hasLayout passed - a bug in the generator");
            }
            Path baked = bakedIcon(resources, spell);
            if (bless) {
                exactEntry(baked); // absent answers null (the write creates it); a case-only
                // clone of the name throws here instead of silently keeping the wrong case
                if (!ImageIO.write(composed, "PNG", baked.toFile())) {
                    throw new IllegalStateException("no PNG writer accepts " + baked);
                }
                System.out.println("regenerated " + baked.getFileName());
            } else {
                compare(spell, composed, baked);
            }
            verified++;
        }
        if (verified == 0) {
            throw new IllegalStateException("no spell icons were checked; the spell sources moved");
        }
        System.out.println("SpellIconChecks: " + verified + " baked spell icons "
            + (bless ? "regenerated from the generator" : "match the generator"));
    }

    /**
     * Loads one committed glyph strictly: a missing or undecodable source must fail the build here,
     * where the game would only warn and fall back, and a case-only clone of the name is refused
     * (the jar's lookup is case-sensitive where this filesystem is not). Squareness is not checked
     * here - the shared {@link SpellIconGenerator#composeCanvas} enforces it for every caller.
     *
     * @param resources
     *            the mod's {@code src/main/resources} root
     * @param id
     *            the glyph texture id ({@code <namespace>:textures/item/<name>.png})
     * @return the decoded glyph
     * @throws IOException
     *             if the file is missing or unreadable
     * @throws IllegalStateException
     *             if the file is not a decodable image, or exists only under a different case
     */
    private static BufferedImage load(Path resources, ResourceLocation id) throws IOException {
        Path file = resources.resolve("assets/" + id.getNamespace()).resolve(id.getPath());
        exactEntry(file);
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) {
            throw new IllegalStateException("glyph " + id + " is not a decodable image: " + file);
        }
        return image;
    }

    /**
     * The committed baked icon for one spell, beside the glyphs it is composed from.
     *
     * @param resources
     *            the mod's {@code src/main/resources} root
     * @param spell
     *            the spell being verified
     * @return the {@code textures/item/<ability id>.png} path, lowercase like the ability id
     */
    private static Path bakedIcon(Path resources, PlayerSpell spell) {
        return resources.resolve("assets/" + spell.id().getNamespace())
            .resolve("textures/item/" + spell.id().getPath() + ".png");
    }

    /**
     * Finds a directory entry with exactly the requested file name, refusing a case-only clone.
     * NTFS and macOS resolve names case-insensitively, so {@code Forge.png} would satisfy a local
     * exists/read (and a bless write would keep that name) while the jar's case-sensitive lookup
     * for {@code forge.png} fails in game - the very failure this check exists to prevent.
     *
     * @param file
     *            the exact path required
     * @return the exactly-named entry, or null when the directory holds no such name and no
     *         case-variant of it
     * @throws IOException
     *             if the directory cannot be listed
     * @throws IllegalStateException
     *             when only a differently-cased name exists
     */
    private static Path exactEntry(Path file) throws IOException {
        String wanted = file.getFileName().toString();
        String clash = null;
        try (Stream<Path> entries = Files.list(file.getParent())) {
            for (Path entry : (Iterable<Path>) entries::iterator) {
                String name = entry.getFileName().toString();
                if (name.equals(wanted)) {
                    return entry;
                }
                if (name.equalsIgnoreCase(wanted)) {
                    clash = name;
                }
            }
        }
        if (clash != null) {
            throw new IllegalStateException("file " + wanted + " is committed only as " + clash
                + "; the jar's case-sensitive lookup needs the exact name - rename it");
        }
        return null;
    }

    /**
     * Asserts the committed PNG equals the composition, pixel for pixel.
     *
     * @param spell
     *            the spell being verified (names the failure)
     * @param composed
     *            the canvas this code composes
     * @param baked
     *            the committed file to compare against
     * @throws IOException
     *             if the committed file cannot be read
     * @throws IllegalStateException
     *             if the committed file is missing, exists only under a different case, is not a
     *             {@link SpellIconGenerator#CANVAS_SIZE} square image, or differs
     */
    private static void compare(PlayerSpell spell, BufferedImage composed, Path baked)
        throws IOException {
        if (exactEntry(baked) == null) {
            throw new IllegalStateException("baked icon " + baked + " is missing; run "
                + "./gradlew blessSpellIcons to generate it");
        }
        BufferedImage committed = ImageIO.read(baked.toFile());
        if (committed == null) {
            throw new IllegalStateException("baked icon " + baked + " is not a decodable image");
        }
        int size = SpellIconGenerator.CANVAS_SIZE;
        if (committed.getWidth() != size || committed.getHeight() != size) {
            throw new IllegalStateException("baked icon " + baked + " is " + committed.getWidth()
                + "x" + committed.getHeight() + " rather than " + size + "x" + size);
        }
        int differing = 0;
        int firstX = -1;
        int firstY = -1;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (committed.getRGB(x, y) != composed.getRGB(x, y)) {
                    if (differing == 0) {
                        firstX = x;
                        firstY = y;
                    }
                    differing++;
                }
            }
        }
        if (differing > 0) {
            throw new IllegalStateException("baked icon " + baked.getFileName() + " for " + spell
                + " differs from the composed icon in " + differing + " pixel(s), first at ("
                + firstX + ", " + firstY + "); run ./gradlew blessSpellIcons to regenerate it");
        }
    }
}
