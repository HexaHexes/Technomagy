package zone.moddev.mc.technomagy.spell;

import java.awt.image.BufferedImage;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;

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
 * Runs as the {@code runSpellIconChecks} Gradle task, hooked into {@code check}. Pass the Gradle
 * project property {@code -PblessSpellIcons} to rewrite the committed PNGs from this same code
 * instead of comparing them. Output files are named after the ability id, lowercase - an uppercase
 * name survives NTFS's case-insensitive overwrite, hides from git, and breaks the jar's
 * case-sensitive resource lookup as a missing texture in game.
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

    /** Set by the Gradle task under {@code -PblessSpellIcons} to rewrite instead of compare. */
    private static final String BLESS_PROPERTY = "technomagy.blessSpellIcons";

    private SpellIconChecks() {
    }

    /**
     * Composes every spell's icon from the committed glyphs and compares it with the committed
     * baked PNG (or rewrites that PNG when blessing).
     *
     * @param args
     *            unused
     * @throws IOException
     *             if a committed PNG cannot be read or written
     * @throws IllegalStateException
     *             if a glyph source is missing, undecodable or not square, a spell with a layout
     *             composes to nothing, no spell was checked at all, or a committed icon differs
     *             from its composition
     */
    public static void main(String[] args) throws IOException {
        Path resources = Path.of(System.getProperty(RESOURCES_PROPERTY, "src/main/resources"));
        boolean bless = Boolean.getBoolean(BLESS_PROPERTY);
        int verified = 0;
        for (PlayerSpell spell : PlayerSpell.values()) {
            List<ResourceLocation> glyphs = SpellIcons.glyphsFor(spell);
            if (glyphs.size() < 2 || glyphs.size() > 4) {
                continue; // the provider abstains for this count, so no icon is baked either
            }
            List<BufferedImage> decoded = new ArrayList<>(glyphs.size());
            for (ResourceLocation glyph : glyphs) {
                decoded.add(load(resources, glyph));
            }
            BufferedImage composed = SpellIconGenerator.composeCanvas(decoded);
            if (composed == null) {
                throw new IllegalStateException(spell + " has " + glyphs.size()
                    + " glyphs but no layout slot for that count");
            }
            Path baked = bakedIcon(resources, spell);
            if (bless) {
                ImageIO.write(composed, "PNG", baked.toFile());
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
     * Loads one committed glyph strictly: a missing, undecodable or non-square source must fail the
     * build here, where the game would only warn and fall back.
     *
     * @param resources
     *            the mod's {@code src/main/resources} root
     * @param id
     *            the glyph texture id ({@code <namespace>:textures/item/<name>.png})
     * @return the decoded glyph
     * @throws IOException
     *             if the file is missing or unreadable
     * @throws IllegalStateException
     *             if the file is not a decodable image or is not square (an animated strip)
     */
    private static BufferedImage load(Path resources, ResourceLocation id) throws IOException {
        Path file = resources.resolve("assets/" + id.getNamespace()).resolve(id.getPath());
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) {
            throw new IllegalStateException("glyph " + id + " is not a decodable image: " + file);
        }
        if (image.getWidth() != image.getHeight()) {
            throw new IllegalStateException("glyph " + id + " is " + image.getWidth() + "x"
                + image.getHeight() + " rather than square: " + file);
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
     *             if the committed file is missing, not a 32x32 image, or differs
     */
    private static void compare(PlayerSpell spell, BufferedImage composed, Path baked)
        throws IOException {
        if (!Files.exists(baked)) {
            throw new IllegalStateException("baked icon " + baked + " is missing; run "
                + ".\\gradlew.bat runSpellIconChecks -PblessSpellIcons to generate it");
        }
        BufferedImage committed = ImageIO.read(baked.toFile());
        if (committed == null) {
            throw new IllegalStateException("baked icon " + baked + " is not a decodable image");
        }
        if (committed.getWidth() != 32 || committed.getHeight() != 32) {
            throw new IllegalStateException("baked icon " + baked + " is " + committed.getWidth()
                + "x" + committed.getHeight() + " rather than 32x32");
        }
        int differing = 0;
        int firstX = -1;
        int firstY = -1;
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
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
                + firstX + ", " + firstY + "); run "
                + ".\\gradlew.bat runSpellIconChecks -PblessSpellIcons to regenerate it");
        }
    }
}
