# First player spell runtime

Status: accepted first player-cast scope (D69). This completes the
execution details needed beyond the pure heuristic engine. Existing decisions
remain authoritative. All numeric values below would be server-configurable.

## Casting and integration

- `technomagy:spell_focus` is a non-stackable casting device. Under D74,
  right-click casts the active Ability Hotbar slot; its management menu owns
  spell selection. Hotbar activation keys also work while holding a focus.
- Ability Hotbar 1.0.1-alpha.03 is required on both sides, pinned to its public GitHub
  release artifact alongside Core 1.1.0-alpha.02. The server validates the held item,
  target and range; each cast is one spell, with timed effects allowed to overlap.
  Clients send intent only.
- Register the fifteen executable spell actions plus life-and-death in Ability
  Core, with matching `technomagy:<lowercase_action>` identifiers. Life-and-death
  remains a utility in the heuristic catalogue; its explicit cast invokes the
  energy-release handler rather than establishing another pair spell.
- Sky's Ley Lines is a hard dependency, pinned to Maven artifact
  `zone.moddev.mc.skysleylines:SkysLeyLines:0.5.6.121012` from moddev.zone.
  It is required on both client and server.
- Sky's Ley Lines supplies actual power. Casts charge the caster's personal
  pools (see Payment below). Do not invent collection rules. D70 now specifies automatic per-player
  pool setup on join: six elements, capacity 10 each, initially 3, no Raw pool.
  Existing pools are preserved. Having a focus unlocks recharge permanently
  (D77).
- Use Ability Core 1.1.0-alpha.02. No GitHub Packages credentials are configured here;
  approved fallback is its exact public GitHub release artifact, resolved through
  a pinned repository declaration rather than committing a binary into this repo.
- Put runtime handlers in `spell`, client UI in `spell.client`, and configuration
  beside the handlers. Proposed config: `technomagy-spells-server.toml`.
- One spell per cast; previously applied timed effects may overlap.
- Casts initially use one unit of each base element; Growth adds one Earth.
  Arbitrary player-designed stream editing is separate from spell selection.
- Initial reach: 5 blocks. Initial base-power scaling: +1 block per added base
  unit. Apply agreed Earth/Air modifiers after that. Self-targeting is allowed
  for protective/healing effects; other spells require an appropriate target.

## Proposed first executable behavior

| Effect | Behavior | Initial tuning / boundary |
| --- | --- | --- |
| Erode | Mine a targeted block using MMDLib crushing when available, otherwise ordinary tool-aware drops. Preservation uses silk-touch drops; Heat smelts outputs; Water collects them. | One block per cast; obey break events, unbreakable blocks, and block-entity restrictions. Do not discard block-entity inventories. |
| Forge | Use live smelting recipes on the targeted block's material or a dropped item. Replace a block only if the result is a single placeable block; otherwise remove it and emit recipe outputs. | One recipe application per cast; no recipe means refusal with no changes. |
| Lightning | Strike a targeted entity/position with magical lightning damage and visual feedback. | 4 damage; no automatic fire or vanilla lightning side effects. |
| Steam | Push a targeted living entity away from the caster. | Velocity impulse 0.6; no teleportation. |
| Mist | Create a temporary mist field that visually obscures and reduces visibility for occupants. | Radius 2 blocks, 200 ticks; use vanilla blindness for actual obstruction, with mist particles. |
| Constraint | Temporarily impede a living target's movement. | Slowness II for 200 ticks. |
| Ward | Shield a living target and resist knockback. | Absorb 8 damage points for up to 200 ticks; do not erase existing health or potion effects. |
| Entomb | Place a temporary, breakable shell around a living target without replacing occupied blocks. | One-block-thick shell around the target bounding box; 100 ticks; no suffocation. Temporary blocks produce no duplication drops. |
| Reveal | Reveal nearby invisible living entities. | Radius 5, glowing for 100 ticks. Selected-resource scanning requires a later resource selection interface. |
| Shroud | Reduce hostile detection and mute footsteps while active; attacking ends it. | 200 ticks; does not clear existing attackers or hide buildings. |
| Radiance | Create a temporary light source near the target, supporting light/warmth conditions without invoking Growth tick acceleration. | Light level 15 for 200 ticks. Ordinary plant rules benefit from lighting; do not tick arbitrary blocks. |
| Blight | Apply a lingering harmful effect to living targets. | Wither I for 100 ticks. Organic-item processing requires explicitly supplied recipes; no invented ash items. |
| Mend | Heal a living target. | Restore 4 health points. Equipment repair is withheld until material costs are specified; no free repair economy. |
| Leach | Temporarily weaken a living target's armor. | -4 armor for 200 ticks. Ore extraction requires explicit reagent/residue recipes; do not invent outputs. |
| Growth | Apply the agreed multiplier to eligible plants in a 9x9x1 layer. | Plant layer at effector/cast target Y; probabilistically schedule fractional extra opportunities; imbalanced mixes have configurable mortality, 10% per affected plant per activation. |
| Life & death | Deliver energy to a targeted block exposing an FE receiver. | 100 FE per paired unit. Reject non-receivers; no explosion or generic block damage. |

## Shared execution boundaries

- Respect server permissions and protection events before block changes. Refusal
  means no resources consumed and no world effects applied.
- Never force-load chunks for targeting. Target hints must be rechecked server-side.
- Timed effects are owned and cleaned up per server/level lifecycle; unloading or
  server restart must not leave permanent attribute changes or temporary blocks.
- A transient effect expiring is distinct from successful casting: do not replay
  a spell because client rendering or an observer fails.
- Effects that lack a specified item-processing recipe decline that operation;
  the entity-targeted spell remains implemented. Ritual-specific continuous
  effects and their totem/inventory wiring are separate from single player casts.

This accepted first-cast scope deliberately identifies narrower behaviors such as Mend's
healing and Leach's armor weakening. These are not claims that every ritual
variant from the team notes is implemented. Further ritual variants require their own execution rules.

## Focus crafting

Use three sticks diagonally, with any of the six elemental crystals directly
above and below the center stick. Vanilla shaped-recipe mirroring supports both
diagonals:

```
S C .       . C S
. S .   or  . S .
. C S       S C .
```

The crystals may be different colors. The output is one spell focus.
Temporary blocks use `technomagy:temporary_earth` and `technomagy:spell_light`;
timed effects use `technomagy:shroud` and `technomagy:leach`; plant exclusions
use the `technomagy:growth_excluded` block tag.

## Implementation and verification

The focus and all sixteen first-cast handlers are implemented. Casts go through
Ability Core's registered pipeline, validate an actually held focus,
compute targets on the server, and evaluate initial spell streams with the
shared heuristic engine. Ability Hotbar owns spell selection; empty or foreign slots
refuse focus casting rather than guessing a spell. Block-use is handled before a
block's own interface, so energy receivers and other interactable targets do
not swallow the cast.

Server tuning is stored per world in
`serverconfig/technomagy-spells-server.toml`. It covers reach, durations, mist
and Reveal radii, damage, impulse, Ward capacity, healing, armor reduction,
FE delivery, status amplifiers, Radiance light level, Growth multiplier terms,
and imbalanced Growth mortality.

Protection preflight rejects unloaded/out-of-height/protected blocks,
unbreakable blocks, and block entities. Erode/Forge never discard inventories.
Hostile entity effects obey PvP/team restrictions. Ward and mist state is
server/level-owned and cleared on lifecycle exit. Leach uses a removable
transient attribute modifier; Shroud uses an ordinary synced timed status
rather than writing a persistent silence flag. Temporary blocks have no drops
and expire through saved scheduled ticks.

### Payment

Server option `payment.enabled` (default `true`, D76) makes each cast charge the
caster's personal elemental pools. Creative-mode casters are never charged.
Costs are whole units configured per spell
under `[payment.costs.<spell>]`, one value per flavor (0 to 64). They default to
the spell's input stream: one unit of each base element (Erode is one Earth and
one Air), Growth one Water, Light, and Earth, and Life & death one Light and one
Dark. A spell whose six costs are all zero is reported as an error when the
config loads and is refused while payment is enabled. It is never cast for free.

Charging runs through Ability Core's EXTERNAL model (D78). `SpellResources`
declares one resource pair per pool, `technomagy:<flavor>`, backed by Ley Lines'
`PlayerPowerService`, and every spell definition declares all six pools at its
input-stream amounts - display defaults, because the per-world server config is
not loaded when definitions register. On each cast Ability Core's
`technomagy:spell_cost` amount resolver re-prices those entries from the server
config, then validates every selected entry fail-closed before the handler
runs: the pool must exist, be active, and hold the price. An invalid target or
an unaffordable cast is refused before the handler runs and before any pool is
touched.

The handler only prepares and applies the effect. Ability Core charges the
validated snapshots at its single charge site after the effect reports ACTED, so
outcomes decided only at application time - a cancelled damage or heal event, a
refused block or entity, a Growth roll with no extra ticks - never cost
anything: no effect is paid for without happening, and no refund is needed. A
spend vetoed or refused after the effect has acted cannot be rolled back: the
cast stands, that pool alone goes unpaid, and every failure names the player,
pool, units, and status: a warning for a veto, a balance race, or an inactive
pool, an error for every other status, so the build's ERROR log gate still
catches real defects. The refund half of each pair still
returns units to a caller that asks (`ResourceConsumers.refund`); Ley Lines can
refuse a return, and it will not raise a balance above capacity, so a failed
return is an error naming the player, pool, and units.

### Recharge

The first time a player has a focus anywhere in their inventory, recharge
unlocks permanently (D77). The unlock is saved in the
`technomagy:recharge_unlocked` attachment and kept through death. Every
`recharge.intervalTicks` (default 10) server ticks, each online unlocked player
who is alive and not spectating
gains `recharge.unitsPerPulse` (default 1) units in one random pool that is
active and below capacity. The gain is clamped to the remaining room, and nothing
happens when every pool is full.

### Mastery

Layer-one spell mastery (D79) declares every spell's Ability Core mastery
contract from `SpellMastery.declare()` at construction, beside the pool
resource pairs. Grants land on the spell's own ability target, priced by
the `technomagy:spell_effort` gain handler: the spell's configured cost
total (read whether or not payment is enabled) times the trigger's
weight - `ACTIVATION` 1.0, `HIT` 0.5, `KILL` 1.0 - with a base effort of
2.0 when no positive cost is configured. All weights are initial and to
be tuned. Ability Core fires `ACTIVATION` itself after every successful
cast; `CUSTOM` grants bypass the handler with a raw amount.

Technomagy fires the rest itself, through `SpellMastery`'s award sites:
Lightning's bolt grants `HIT` when it damages its target and `KILL` when
it brings that target to zero health (one strike, one award), and each
Ward absorption grants `CUSTOM` with the absorbed damage as a raw amount
to the ward's caster - the `Ward` record carries the casting player's
UUID, preserved across refreshes and owned by the last caster to refresh,
resolved to the online player at absorption time. An award for a trigger
the spell's spec did not declare is ignored by core.

Mastery layers two and three (D80) extend the same declaration. Every
grant - the effort handler's output and raw awards alike - routes 40%
evenly across the spell's element buckets (`SUBCATEGORY`
`technomagy:<flavor>`, one per element of its stream), 15% to its one
role-school bucket (`shaping` / `striking` / `warding` / `veiling` /
`vitality`), and keeps the 45% remainder on the spell itself. Fourteen
spells declare `POWER` (Lightning damage, Steam impulse, Mend heal,
Ward shield, Growth tick multiplier, Life & Death energy) or
`DURATION` (Constraint, Shroud, Leach, Mist, Ward, Radiance at
`DURATION` ticks; Blight, Entomb, Reveal at `SHORT_DURATION` ticks;
Ward takes both; Erode and Forge declare neither), read back through
core's default curve
(`1 + 0.5 × (1 - e^(-0.01 × aggregateLevel))`) at each effect site - integer
sinks rounded nearest half-up, float magnitudes unrounded. The
`technomagy:spell_effort` handler multiplies its total by the spell's
own-level effort surge before routing (level 0-1 x1.0, level 2 x1.1,
level 3 x1.25, level 4+ x1.5, initial and to be tuned); raw awards
bypass the handler and are never surged. The mod ships
`data/technomagy/mastery/levels.json` - one hundred cumulative
thresholds: `[50, 125, 250, 425]` hand-set, then x1.15 per level to
the table's length, which is the maximum level 100 (core's default
soft cap) - overriding core's config curve for every target.

Menu presentation (D81) rides hotbar `1.0.1-alpha.06`: each spell
declares a `PresentationProvider` whose single `PROGRESS` field is the
menu's mastery meter - localised level text inside, in-level fill on
the bar - read from hotbar's answered mastery snapshot, with an empty
presentation (no meter, no rows, never a guessed zero) before the
answer arrives and a null toggle because no spell is togglable. The
slot overlay's availability provider and the icon providers stay
undeclared: no verdict can trust display-default costs against synced
pools, and no spell art exists to iconify.

Menu organization (D82) and availability (D83) extend the same layer:
four tabs - Single, Duo, Trio, Quadra - classify every ability by its
element tag count (`fire`, `earth`, `air`, `water`, `light`, `dark`, one
tag per component element, replacing the old `spell` tag), with the six
non-castable elemental mastery entries as Single's population, each
element wearing its alchemical symbol as chip and row icon (Gold for
Light, Silver for Dark), and Single/Duo/Trio wearing the philosopher's
stone, the amalgam and the amalgam (alternate) as tab icons. Quadra
holds four-element abilities (none exists yet, so hotbar hides the
empty tab) and stays text-only until a symbol is chosen. The spells'
per-spell icons are generated at render time by an IconProvider from
the element symbols (pack-aware: the glyphs composite onto a
transparent 32x32, each box-averaged into a 15-pixel box by integer
math so thin features survive, with no placeholder underneath; duo on
the top-left to bottom-right diagonal, trio in a triangle, quadra in a
diagonal X across the quadrants; the baked files regenerate by the
same math and remain the static fallback) and the entries read the element buckets D80 routes
into and present each bucket's level as a meter once the snapshot
answers.
Single is learned from the start (a future item-tag
craft gate is recorded as intent); Duo unlocks when every element of a
spell's stream reaches `mastery.unlockDuoLevel` (default 10) and Trio
at `mastery.unlockTrioLevel` (default 25) - server-config thresholds
synced to the client at join beside the pool snapshot, verdicts through
core's unlock providers, and each tab's live content listing only
proven-learned abilities (unknown lists - never a guessed lockout).

### Verification

D74 verification: `build` and all 26 dedicated-server GameTests passed with
Core 1.1.0-alpha.02 and Hotbar 1.0.1-alpha.03 installed. Coverage includes focus casting
from server-owned hotbar state, empty/foreign-slot refusal, offhand use,
direct-slot activation through Core's payload-to-request conversion, and
refusal without a held focus. The mock connection declares Hotbar's snapshot
channel before login; this tests the real login sync sender but not client
packet consumption. The client smoke check passed under a virtual display
with software rendering and captured the seven-element selector. In-world
hotbar assignment, slot rendering, and physical activation keys remain manual
verification. Ordinary display startup crashed in native OpenGL before mod
initialization; the virtual-display run completed successfully.

D80/D81 verification: `spotlessApply build` and all 46 dedicated-server
GameTests passed with Core 1.1.0-alpha.06 and Hotbar 1.0.1-alpha.06
installed, zero ERROR/FATAL records. The shipped level table loads
through core's fixed datapack loader, the POWER/DURATION effect math
matches core's asymptotic curve, and the D81 presentation checks cover
the pre-answer empty presentation and the answered meter's field. The
menu meter, the pane rows and the slot overlay are client render:
build green plus the user's in-game check is their honest scope.

Run `./gradlew runGameTestServer` for the 50 runtime checks, including every
effect, expiration, recipe mirroring/color combinations, protection vetoes,
Ward/Steam interaction, and the actual focus air/block-use paths. Six payment
checks cover the default and configured costs, refusing a shortfall or invalid
target without a charge, a spend vetoed after the effect has acted leaving the
cast standing with only that pool unpaid, an effect cancelled at application
time never being charged, refusing an all-zero cost, and exempting creative
casters. Fifteen mastery checks cover activation gain keeping its 45%
self-share, Lightning's HIT then KILL awards, Ward absorption crediting the
ward's caster and routing raw grants, core ignoring an undeclared trigger,
the 40/15/45 share routing into element and discipline buckets, POWER-scaled
Mend healing, DURATION-scaled Constraint slowdown, the shipped level-table
curve, the level-2 effort surge, the D81 presentation registering for every
spell with an empty pre-answer meter, the answered meter carrying its
level text and in-level fill, the D82 three-tab organization with
per-element tags and the entry meters from the answered snapshot, the D83
unlock verdicts by element level, and the tab content listing only
proven-learned abilities. Two recharge checks
cover the permanent focus unlock, skipping dead players, and pool selection
with clamping. The task
requires the exact pass count and rejects ERROR/FATAL log records. Runtime
fixtures and UI instrumentation are excluded from the production JAR.

An opt-in client check is available with
`./gradlew runClient -PspellClientCheck=true`. It uses a disposable directory,
opens the machinery element selection menu after reaching the title screen, verifies all
seven element buttons, captures `run-spell-clientcheck/focus-selection-check.png`,
and exits. The check was launched through `xvfb-run` with software rendering,
but the user observed a real desktop window: display isolation was not achieved
or verified. WSLg/Wayland routing is a possible explanation, not an established
cause. No further client launches are planned as part of this task. The captured
frame proves menu rendering, not isolation, manual multiplayer play, or
shader-pack compatibility.

The accepted first-cast scope remains distinct from later ritual variants:
material-backed equipment repair, organic decomposition recipes, reagent-based
ore leaching, selected-resource scanning, ongoing totem execution, player stream
editing, and charging from Ley Lines nexus (ambient) power are not implemented
by these first handlers.
