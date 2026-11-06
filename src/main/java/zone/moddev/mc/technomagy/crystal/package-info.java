/**
 * The six flavors of magic and their crystals (D2-D12).
 *
 * <p>
 * Each flavor is an MMDLib material ({@code mmdlib/material/<flavor>.json}) with four forms: the
 * harvested {@link zone.moddev.mc.technomagy.crystal.CrystalItem crystal}, a storage block, a
 * {@link zone.moddev.mc.technomagy.crystal.BuddingCrystalBlock budding crystal} and a growing
 * {@link zone.moddev.mc.technomagy.crystal.CrystalClusterBlock cluster}. The forms are MMDLib data
 * modules; {@link zone.moddev.mc.technomagy.crystal.CrystalContent} supplies their Java factories
 * and registers every Technomagy material, the stones included.
 *
 * <p>
 * Growth works like budding amethyst (D4), at amethyst's rate for the natural flavors, Light and
 * Dark (D7), and a quarter of it for the others (D8). Light and Dark geodes are data only ({@code
 * data/technomagy/worldgen}, D3, D5). Static assets and data for these forms come from {@code
 * scripts/generate_crystal_*.py}.
 *
 * <p>
 * This package must not depend on {@code machine}. Mana prompting and acceleration of growth
 * (D9-D11) will make it depend on {@code mana}.
 */
package zone.moddev.mc.technomagy.crystal;
