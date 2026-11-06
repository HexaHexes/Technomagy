/**
 * The mana power system's data and capability (D13-D28).
 *
 * <p>
 * {@link zone.moddev.mc.technomagy.mana.ManaType} names the seven types: the six flavors and Raw.
 * {@link zone.moddev.mc.technomagy.mana.Mana} is an immutable quantity holding an amount of each,
 * with the lossless operations streams need: merging, splitting Raw into flavors and back, and
 * proportional rate limiting. {@link zone.moddev.mc.technomagy.mana.IManaHandler} is the capability
 * interface, looked up with {@link zone.moddev.mc.technomagy.mana.ManaCapabilities#BLOCK} like
 * NeoForge's energy capability, and {@link zone.moddev.mc.technomagy.mana.ManaStorage} and
 * {@link zone.moddev.mc.technomagy.mana.InfiniteManaSource} are ready-made handlers.
 *
 * <p>
 * {@code docs/MANA.md} describes the system as a whole, with usage examples. Internal spell
 * calculations implement D45, D49, D50, D59, and D60 without game-world access. These calculations
 * must not perform world effects or infer unresolved stabilization rules. Internal stream
 * construction preserves prism ordering and grouped additions (D37, D52-D58) without depending on
 * machine or client classes. Interpretation resolves caller-supplied pair and modifier meanings
 * into immutable effect plans, distinguishing base power from modifier actions (D54-D59). The
 * public SpellEngine API adds chemistry, cancellation, typed catalogue tuning, and required
 * execution policies (D62-D68); it does not perform world mutation. The legacy flat Mana units must
 * be migrated before bridging them to D47's stream quantities.
 *
 * <p>
 * Transfer rate is not part of a handler: it belongs to the link between two handlers (D28), which
 * this package does not implement yet.
 *
 * <p>
 * This package depends on {@code crystal} only for
 * {@link zone.moddev.mc.technomagy.crystal.Flavor}. It must not depend on {@code machine}; machines
 * use mana, not the other way round.
 */
package zone.moddev.mc.technomagy.mana;
