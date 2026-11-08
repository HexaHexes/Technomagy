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
 * Effects consume the agreed first-cast runtime policy. Unless the server disables payment, each
 * cast is validated, then charged its configured per-element cost from the caster's personal pools,
 * then applied; ineffective casts are refunded ({@code SpellPayment}, D76). D70's LeyLines reserves
 * are created on login: six elemental pools, capacity ten, initially three. Having a focus unlocks
 * recharge permanently: {@code PoolRecharge} adds a unit to a random non-full pool every pulse
 * (D77). Existing pools retain their balances and settings. This package may depend on mana,
 * Minecraft, LeyLines, and Ability Core, but must not load client classes on dedicated servers.
 * Timed state is owned by the server lifecycle. Recipe processing uses the live recipe manager
 * rather than maintaining private copies of crushing or smelting data.
 */
package zone.moddev.mc.technomagy.spell;
