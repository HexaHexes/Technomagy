/**
 * Personal elemental pool display and server snapshot synchronization implement D75.
 *
 * The focus also stores a separate server-validated machinery element selection (D73).
 *
 * Ability Hotbar owns spell selection and the focus casts its active slot (D74).
 *
 * Server-authoritative player spell execution and Ability Core integration (D69-D70).
 *
 * <p>
 * Effects consume the agreed first-cast runtime policy; payment is not connected yet. D70's
 * LeyLines reserves are created on login: six elemental pools, capacity ten, initially three,
 * without automatic recharge. Existing pools retain their balances and settings. This package may
 * depend on mana, Minecraft, LeyLines, and Ability Core, but must not load client classes on
 * dedicated servers. Timed state is owned by the server lifecycle. Recipe processing uses the live
 * recipe manager rather than maintaining private copies of crushing or smelting data.
 */
package zone.moddev.mc.technomagy.spell;
