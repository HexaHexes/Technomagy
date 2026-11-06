/**
 * Technomagy, a NeoForge mod where magic is studied and engineered like a science (D1).
 *
 * <p>
 * The base package holds only the mod's entry point, {@link zone.moddev.mc.technomagy.Technomagy},
 * which hands registration to each content area's own package:
 *
 * <ul>
 * <li>{@link zone.moddev.mc.technomagy.crystal}: the six flavors of magic and their crystals,
 * budding crystals and growth (D2-D12);
 * <li>{@link zone.moddev.mc.technomagy.mana}: the mana power system's types, quantities and
 * capability (D13-D28);
 * <li>{@link zone.moddev.mc.technomagy.machine}: the machine pieces that rituals are built from
 * (D20, D29-D31).
 * </ul>
 *
 * <p>
 * Classes in this package must not hold content or game logic of their own; anything new belongs in
 * the package of its area.
 */
package zone.moddev.mc.technomagy;
