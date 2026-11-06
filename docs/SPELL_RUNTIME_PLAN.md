# First player spell runtime

Status: accepted first player-cast scope (D69). This completes the
execution details needed beyond the pure heuristic engine. Existing decisions
remain authoritative. All numeric values below would be server-configurable.

## Casting and integration

- `technomagy:spell_focus` is a non-stackable casting device. Under D74,
  right-click casts the active Ability Hotbar slot; its management menu owns
  spell selection. Hotbar activation keys also work while holding a focus.
- Ability Hotbar alpha.56 is required on both sides, pinned to its public GitHub
  release artifact alongside Core alpha.42. The server validates the held item,
  target and range; each cast is one spell, with timed effects allowed to overlap.
  Clients send intent only.
- Register the fifteen executable spell actions plus life-and-death in Ability
  Core, with matching `technomagy:<lowercase_action>` identifiers. Life-and-death
  remains a utility in the heuristic catalogue; its explicit cast invokes the
  energy-release handler rather than establishing another pair spell.
- Sky's Ley Lines is a hard dependency, pinned to Maven artifact
  `zone.moddev.mc.skysleylines:SkysLeyLines:0.5.6.121012` from moddev.zone.
  It is required on both client and server.
- Sky's Ley Lines will supply actual power; its integration plan is still in
  progress. No power charging in this first casting implementation. Do not
  invent charging or collection rules. D70 now specifies automatic per-player
  pool setup on join: six elements, capacity 10 each, initially 3, no Raw pool.
  Existing pools are preserved. Automatic recharge remains disabled until the
  first casting apparatus; the unlock implementation and recharge rate are pending.
- Use Ability Core alpha.42. No GitHub Packages credentials are configured here;
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
and imbalanced Growth mortality. No power is consumed in this implementation;
Sky's Ley Lines payment remains deferred by D70.

Protection preflight rejects unloaded/out-of-height/protected blocks,
unbreakable blocks, and block entities. Erode/Forge never discard inventories.
Hostile entity effects obey PvP/team restrictions. Ward and mist state is
server/level-owned and cleared on lifecycle exit. Leach uses a removable
transient attribute modifier; Shroud uses an ordinary synced timed status
rather than writing a persistent silence flag. Temporary blocks have no drops
and expire through saved scheduled ticks.

D74 verification: `build` and all 26 dedicated-server GameTests passed with
Core alpha.42 and Hotbar alpha.56 installed. Coverage includes focus casting
from server-owned hotbar state, empty/foreign-slot refusal, offhand use,
direct-slot activation through Core's payload-to-request conversion, and
refusal without a held focus. The mock connection declares Hotbar's snapshot
channel before login; this tests the real login sync sender but not client
packet consumption. The client smoke check passed under a virtual display
with software rendering and captured the seven-element selector. In-world
hotbar assignment, slot rendering, and physical activation keys remain manual
verification. Ordinary display startup crashed in native OpenGL before mod
initialization; the virtual-display run completed successfully.

Run `./gradlew runGameTestServer` for the 27 runtime checks, including every
effect, expiration, recipe mirroring/color combinations, protection vetoes,
Ward/Steam interaction, and the actual focus air/block-use paths. The task
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
editing, and Ley Lines charging are not implemented by these first handlers.
