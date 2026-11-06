# Elemental Magic System — Design Notes

Nov 3, 2026 · @Dan

## Overview

Spells are built from six elements arranged in three opposing pairs. Opposed elements don't cancel: combining them creates a new effect. Mana itself is like white light — raw mana is an even blend of all six, and a prism splits it into elements or recombines them.

| Pair | Opposition | Combined result |
| --- | --- | --- |
| Light ↔ Dark | Life vs death | A stable **power stream** (pure energy) |
| Earth ↔ Air | Stability vs impermanence | **Erode** (the mining spell) |
| Fire ↔ Water | Heat vs motion | **Steam** (heat turned into motion) |

## The elements

Each element has a core meaning that decides what it does in any spell.

| Element | Core meaning | As a modifier |
| --- | --- | --- |
| Light (life) | Preservation of form and identity | Keeps things whole — e.g. silk touch on Erode |
| Dark (death) | Destruction, harm, breaking down | Breaks things down — e.g. voids or converts drops |
| Earth (stability) | "What is, stays" — time and presence held fixed | Full strength, fixed in place, lasting |
| Air (impermanence) | "Nothing stays" — change, transience | Spikes, moves, then fades |
| Fire | Heat, energy, intensity | More power and consumption |
| Water | Motion and flow | Effects follow paths, surfaces and channels |

Two distinctions keep elements from overlapping:

- **Light vs Earth:** both "preserve", but Earth keeps a thing *existing* while Light keeps it *whole*.
- **Water vs Air:** both involve motion, but Water is *flow* (continuous, following a path, carrying things along) while Air is *freedom* (untethered, jumping, scattering). A water projectile follows terrain; an air projectile arcs freely or chains between targets.

## Earth and Air as modifiers

Earth and Air have six sub-effects each, and nearly every one has a direct opposite on the other side.

| Axis | Earth (stability) | Air (impermanence) |
| --- | --- | --- |
| Time | **Persistence** — effects last longer | **Acceleration** — faster activation, shorter duration |
| Position | **Anchoring** — effects stay fixed in place | **Mobility** — effects move or follow targets |
| Concentration | **Reinforcement** — stronger shields, barriers, constructs | **Diffusion** — larger area, weaker effect |
| Predictability | **Consistency** — less randomness in outcomes | **Volatility** — more power, less predictable |
| Physicality | **Solidification** — temporary constructs become real blocks | **Phasing** — spells pass through blocks or obstacles |
| Boundary | **Resistance** — harder to dispel or interrupt | **Propagation** — effects spread or jump between entities |

The boundary row mirrors less cleanly: Resistance keeps outside forces *out*, while Propagation lets the effect *out*. An option is to give Earth a **Containment** entry (the effect stays on its original target and never spreads) as Propagation's true opposite, folding Resistance into Reinforcement or Persistence.

## Combinations

Opposed pairs generate the system's foundational effects; other pairs create more specific spells.

### Opposed pairs

- **Light + Dark → Power stream.** Life and death in perfect balance release pure energy, like matter meeting antimatter. The stream is stable and simply delivers energy, which makes it a natural mana carrier and a clean bridge to Forge Energy for tech mods. Tipping the balance could be how spells fail or get disrupted.
- **Earth + Air → Erode.** Impermanence wears the block away while stability preserves the drop, like real erosion carrying material off. Erode can't take Earth or Air as modifiers, since they're its parents. It could act gradually, with softer blocks (dirt, gravel) wearing away before stone and stone before ores.
- **Fire + Water → Steam.** Heat plus motion is a steam engine: heat converted into motion. Natural effects are pressure, pushing, launching, propulsion, or driving machinery.

### Other pairs (brainstorm)

|  | Light | Dark | Fire | Earth | Air |
| --- | --- | --- | --- | --- | --- |
| **Fire** | Mage-light | Blight (lingering harm) |  |  |  |
| **Earth** | Ward (barrier) | Entomb / bury | Forge (smelt in place) |  |  |
| **Air** | Reveal (sight, ore sense) | Shroud (invisibility, silence) | Spark (lightning) | — |  |
| **Water** | Mend (heal, cleanse) | Drown (poison, suffocation) | — | Grow (crops, maybe fortune) | Mist (fog, weather) |

## Modifier heuristic

The base spell is moderate and steady; Earth makes it full strength and fixed; Air makes it spike, move, or spread, then fade. Earth must not be a strict upgrade, so Air always gets something Earth can't do.

| Spell | Base | + Earth | + Air |
| --- | --- | --- | --- |
| Light | Light level 7 | Level 15, fixed in place, lasting (Reinforcement) | Level 3 over a wide radius (Diffusion), or follows the caster, or flashes to 15 then fades |
| Damage | Steady hit | Sustained burn | Big hit that fades |
| Shield | Normal barrier | Durable barrier | Absorbs one spike, then collapses |

Light and Dark work the same way on integrity:

| Spell | + Light | + Dark |
| --- | --- | --- |
| Erode | Silk touch — the block keeps its identity | Voids the drops, ideally converting them to XP, mana or essence |

Which sub-effect applies is set by the base spell: each spell declares the axes it has (light has strength and area; a projectile has movement), and the modifier acts on those. This is easy to express in data.

## Mana

Mana is gathered raw, refracted into elements, then recombined into spells.

1. **Gather** raw mana — an even blend of all six elements, the form most mana takes naturally.
2. **Refract** it through a prism into the six elements.
3. **Recombine** them in the prism's combining mode: Light + Dark into a power stream, Earth + Air into Erode, and so on.

### Regional imbalance

The local blend reflects what is physically there (water, stone, heat) and what is happening there (life, decay, motion).

| Place | Dominant elements |
| --- | --- |
| Oceans | Water |
| Warm oceans | Water + Light (abundant life) |
| Rivers | Water + Earth (water carving earth) |
| Lava lakes | Fire |
| Deep dark | Dark, with Earth |
| Deserts / badlands | Fire + Earth / mostly Earth |
| Mountains | Air + Earth |
| Lush caves, jungles | Light + Water |
| Swamps | Water + Dark, some Light |
| Frozen biomes | Water + Earth, little Fire |
| Nether | Fire; soul sand valleys lean Dark |
| The End | Air + Dark, or unusually pure raw mana |

A general rule could give Earth more weight with depth, so the deep dark's Earth comes naturally.

### Implementation

- **Biome baseline** set through biome tags in data, so modded biomes join automatically via common tags (`c:is_ocean`, `c:is_hot`) and pack makers can tune it.
- **Local influence** from nearby blocks — lava, water, crops and leaves, sculk — sampled by collectors. A lava lake in plains still reads as fire-heavy, and players can build around a collector to bias it.
- **Sculk catalysts** feed on nearby deaths, making them a natural Dark source; a mob farm over one becomes a Dark generator.
- Optional: small losses when refracting and recombining, so using local imbalance pays off; raw mana usable directly as a weak, unfocused early tier.

## Open questions

- [ ] Is the power stream the fuel for every spell, or a separate utility for moving energy?
- [ ] Do non-opposed pairs always make new spells (like mage-light), or can one element act as base and the other as modifier? If both, what decides which?
- [ ] Which element, if any, gives fortune on Erode — Earth + Water growth, or something else?
- [ ] Keep Resistance in Earth, or replace it with Containment to mirror Propagation?
- [ ] Final effect for Steam: propulsion, a concealing mist, or quenching/tempering?
