/**
 * Personal elemental pool display and server snapshot synchronization implement D75.
 *
 * Client-only machinery element selection presentation (D73). Ability Hotbar owns spell selection
 * (D74). Selection is sent as intent; the server owns held-item checks, selected spell and mana
 * type state, targeting, and all world effects. This package must not be initialized by the
 * dedicated server or retain authoritative spell state.
 */
package zone.moddev.mc.technomagy.spell.client;
