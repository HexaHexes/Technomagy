/**
 * Personal elemental pool display and server snapshot synchronization implement D75.
 *
 * The focus also stores a separate server-validated machinery element selection (D73).
 *
 * Ability Hotbar owns spell selection and the focus casts its active slot (D74).
 *
 * Server-authoritative player spell execution and Ability Core integration (D69-D70).
 *
 * Layer-one spell mastery (D79): {@code SpellMastery} declares every spell's Ability Core mastery
 * contract at construction and owns the award sites for casts, Lightning's hits and kills, and
 * Ward's damage absorption, including the caster UUID a ward records.
 *
 * Mastery layers two and three (D80): the same class routes every grant 40% to the spell's element
 * buckets, 15% to its role-school bucket and the remainder to the spell itself, declares the
 * {@code POWER}/{@code DURATION} axes, reads their multipliers back for the effect sites in
 * {@code SpellRuntime} (nearest half-up on integer sinks), applies the own-level effort surge in
 * the gain handler, and ships {@code data/technomagy/mastery/levels.json}.
 *
 * Menu presentation (D81): the same class declares every spell's {@code PresentationProvider} - the
 * mastery meter built from Ability Hotbar's answered snapshot, an empty presentation before the
 * answer - and deliberately declares no availability or icon provider.
 *
 * <p>
 * Effects consume the agreed first-cast runtime policy, now served by Ability Core's external
 * charging: {@code SpellResources} presents the six personal pools as resource pairs and prices
 * each cast from server config through the {@code technomagy:spell_cost} resolver, Ability Core
 * refuses unaffordable casts before the handler runs and charges the validated snapshot after the
 * effect reports {@code ACTED} (D78). D70's LeyLines reserves are created on login: six elemental
 * pools, capacity ten, initially three. Having a focus unlocks recharge permanently:
 * {@code PoolRecharge} adds a unit to a random non-full pool every pulse (D77). Existing pools
 * retain their balances and settings. This package may depend on mana, Minecraft, LeyLines, and
 * Ability Core, but must not load client classes on dedicated servers. Timed state is owned by the
 * server lifecycle. Recipe processing uses the live recipe manager rather than maintaining private
 * copies of crushing or smelting data.
 */
package zone.moddev.mc.technomagy.spell;
