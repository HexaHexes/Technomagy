# Technomagy Design

Each design decision is numbered (**D1**, **D2**, ...). Code that implements a
decision cites its number, so the code and this document can be cross-checked.
Numbers are permanent: a changed decision keeps its number, and a dropped one
is marked as dropped rather than reused.

## Core Premise

**D1.** Technomagy treats magic as a science. Magic follows rules that can be studied,
learned, and engineered.

## Magic Flavors

**D2.** Magic has six flavors:

- **Earth**
- **Air**
- **Fire**
- **Water**
- **Light**
- **Dark**

The first four are the classic elements. Light and Dark are the other two.

## World Generation

**D3.** The world generates crystal geodes that work like vanilla amethyst geodes.
Each geode is made of either **Light** crystal blocks or **Dark** crystal blocks.

**D4.** Each geode also contains **budding crystal** blocks, which work like vanilla
budding amethyst. A geode's budding blocks are the same flavor as the rest of
the geode: a Light geode has budding Light crystal, and a Dark geode has budding
Dark crystal.

**D5.** Only Light and Dark generate naturally. No other flavor does. Each geode is a
single flavor, and crystal geodes as a whole turn up slightly more often than
amethyst geodes (about 5% more).

### Crystal Locator

**D6.** Geodes are world features, not structures, so structure-finding tools such as
`/locate` and Explorer's Compass cannot find them. Technomagy will provide its
own **crystal locator**: an item that senses the resonance of nearby Light or
Dark crystals and points players toward geodes. Details are still to be
designed.

## Crystal Growth

**D7.** Light and Dark budding crystals grow on their own, at the same rate as vanilla
budding amethyst.

**D8.** Fire, Earth, Air, and Water budding crystals need **prompting** from the mod's
**mana** power system to grow properly. Without prompting they still grow, but
at about a quarter of amethyst's rate.

**D9.** Every budding crystal, Light and Dark included, can be accelerated by supplying
enough mana of the matching type.

**D10.** Beyond that, crystal growth can be **forced** even faster by feeding a budding
crystal a **supersaturated** stream: a compounded stream that carries more of
its own flavor than plain mana can. This is separate from the general growth
combination (Light + Water + Earth), which does not affect crystals.

For example, a Water crystal can be forced with Water + Light + Water. One
prism combines Water and Light into a new sub-element, and a second prism
combines more Water into that, supersaturating the stream with Water (see
[Combinations](#combinations)).

**D11.** The middle flavor can be any other flavor; Light is only an example. Flavors
opposed to the crystal's own weaken the stream instead of boosting it. The
opposing pairs are:

- Light and Dark
- Earth and Air
- Fire and Water

So a Water crystal is boosted by Water + Earth + Water but weakened by
Water + Fire + Water.

Light and Dark are *polar* opposites rather than natural ones, and follow their
own rules when they meet (D34–D36).

## Progression

**D12.** Players learn how to convert budding crystals into the other elemental forms
(Earth, Air, Fire, Water).

## Mana

**D13.** Mana is the mod's power system. It is **wireless**: it moves without cables
or pipes.

### Types

**D14.** Mana comes in seven types:

- one for each flavor of magic: **Earth**, **Air**, **Fire**, **Water**,
  **Light**, and **Dark**
- **Raw** mana, the seventh type

**D15.** Raw mana contains all six flavors in an even mix that has not been
differentiated or split into its parts. Because of that even balance, Raw mana
counts as a **pure** form, even though it has six components.

**D16.** Raw mana is the form players work with most during play. Players can **split**
Raw mana into its flavors to power the individual parts of their machines
(see [Machines](#machines)).

### Sources

**D17.** Mana can be **generated** by players.

**D18.** Mana also arises **naturally** from the world. Each natural source gives off
the type or types that match it. For example:

- bodies of water produce Water mana
- burning fires produce Fire mana
- lakes of lava produce Fire mana

A source can produce more than one type. Lava produces both Fire and Earth
mana.

### Combinations

**D19.** Combining flavors of mana, or recombining flavors split from Raw mana, produces
effects. A combination behaves the way those forces would interact in the
physical world. Flavors that seem opposed do not cancel out; they react and
make something new. The one exception is Light and Dark (D34).

**D34.** There are two kinds of opposition:

- **Natural opposites**, Earth/Air and Fire/Water, react and make something
  new (D19).
- **Polar opposites**, Light/Dark, cancel each other instead (D35, D36).

**D35.** Light and Dark merged **directly with each other**, and with nothing
else, form a stable pair called **life & death**. With no other flavor in the
stream, the energy of cancelling has nowhere to go, so the pair holds together
until it strikes a block (D39).

**D39.** A life & death stream **releases its energy on impact with a block**:
it is a carrier of pure energy. A block built for it, such as a reactor, can
convert that energy into Forge Energy (FE). Merging into a prism is not an
impact, so life & death still serves as an ingredient of Raw (D27). Which
other blocks count as an impact, and what the release does to them, is not
designed yet.

**D36.** When Light or Dark is merged into a compound that holds the other one
**unpaired** (not inside a life & death pair), they **annihilate** one-for-one:
3 Light meeting 2 Dark leaves 1 Light. The annihilated energy goes into the
rest of the stream and raises its saturation; this is how D33 oversaturates.
Nothing is lost, so D26 still holds.

**D20.** Mana is combined in **prisms**. A prism in combine mode merges exactly
**two** incoming streams into one new sub-element. Prisms can be chained: the
output of one prism can feed another, which combines more mana into it. Chaining builds compounded streams, and adding
the same flavor again to a compounded stream supersaturates the stream with
that flavor (see D32).

**D32.** Combining pure streams of a single flavor does **not** supersaturate.
Water + Water is still pure Water, only stronger: it works like feeding extra
power into a laser. Supersaturation needs a compounded stream, one that already
carries another flavor, such as Water + Light + Water (D10): the third element
is necessary.

**D33.** A supersaturated stream can be pushed further, to **oversaturation**,
the highest saturation level, by combining in a fourth part that **balances**
it: the flavor opposing the stream's other flavor, together with more of the
main flavor. For example, Water + Light + Water combined with a Dark + Water
stream: the Dark **denatures** the Light, and the extra Water pushes the
saturation to its highest level. Denaturing is annihilation (D36): the Dark
arrives unpaired into a compound holding unpaired Light, and their energy goes
into the Water's saturation.

Examples:

- **Fire + Air:** a hotter fire. Likely used as a stage in a smelting process.
- **Fire + Water:** clouds. The water does not lower the temperature.
- **Fire + Water + Earth:** can trigger rain.
- **Light + Water + Earth:** accelerates growth, speeding up growth ticks for
  things nearby. It does not affect budding crystals (see
  [Crystal Growth](#crystal-growth)).

### Direction

**D21.** Mana can be **directed**, in a way similar to ley lines: players channel it
along paths from where it arises to where it is needed.

**D22.** Directed mana is shown in the world as a rendered **line** between the points
it travels between, so players can see where their mana is flowing.

**D23.** Players configure the links between node directors and prisms with a **wand**,
a wrench-like tool. Details are still to be designed.

### Storage and Transfer

**D24.** Mana is handled through a capability, the same way Forge Energy (FE) is, so
any block can store, accept, or supply it. Unlike FE, which tracks only an
amount, mana tracks three things:

- **type**: which mana it is
- **amount**: how much is stored or moved
- **rate**: how fast it moves

Different machine components need different types of mana, and consume it at
different rates.

**D25.** A quantity of mana records how much of each of the seven types it holds: the
six flavors plus Raw. Raw is technically its own seventh type, but it is
treated as an even mix of the six flavors. Combinations keep their
proportions, so a stream records exactly which flavors it contains and in what
amounts.

**D37.** A stream also records its **merge structure**: how it was built, as a
tree in which each prism merge is a node with its two inputs as children. The
amounts of D25 are the totals over that tree. A prism in split mode undoes the
**most recent** merge and outputs its two inputs. Raw is the exception: it
splits into its six flavors in one step (D27).

**D26.** Splitting and combining are **lossless**: a prism in split mode separates a
stream into its parts, a prism in combine mode merges streams, and no mana is
lost either way. Combining and then splitting gives back exactly what went in,
structure included (D37), except that Raw splits in one step (D27). The one
exception to losslessness is dropout from unbalanced mana
(D38), where the released mana is spent on a world effect.

**D27.** Building and splitting Raw are not symmetric. Raw **splits in a single
step**: one prism in split mode turns it into all six flavors. Because prisms
merge two streams at a time (D20), the following **five-prism construction**
is one way to build Raw, not an exclusive recipe. Light and Dark must enter
together as a pre-paired life & death stream (D35); the remaining merge
structure need not match this example:

```
1: Earth + Air    ─┐
2: Fire + Water   ─┴─ 3: → raw elemental ─┐
4: Light + Dark   → life & death ─────────┴─ 5: → Raw
```

The conversion condition is D56: adding paired life & death to a stream
containing at least one unit each of Earth, Air, Fire, and Water converts
one unit of each of those four elements plus one unit of life & death
into one Raw unit. Conversion repeats while complete sets remain (D56).

Elemental power is kept: 1 of each flavor builds 1 Raw unit, and 1 Raw
unit splits back into 1 of each (D47). Since the split doesn't depend on how Raw was built, Raw from a
source (D17, D18), which has no merge history, splits the same way.

**D38.** **Flavor imbalance and dropout are general stream mechanics**, not
limited to Raw. Unstabilized excess mana leaks out over time along the stream's
path and manifests as world effects matching its flavor: Water drops drip
from Water streams, Fire streams set fires, and other flavors have their own
manifestations. This is physical release of mana, not merely reduced effect
strength; released mana is spent on the world effect (D26).

For any single mana flavor, power up to **2 points** needs no Light/Dark
stabilization. Either Light or Dark alone stabilizes to **3 points**; adding
both individually stabilizes to **5 points**; adding pre-paired life & death
stabilizes to **6 points** (D55). Increasing the main
flavor alone does not provide this stabilization. These requirements refine
the saturation rules of D10–D11 and D32–D33: another flavor alone is not a
substitute for the required Light/Dark stabilization.

Raw has an additional balance requirement: it is an even mix of all six
flavors (D15). When the final merge of D27 is uneven, the balanced part forms
Raw and the excess undergoes dropout, also called a **decomposition release**.
For example, 2 Fire + 1 of each other flavor gives 1 Raw unit, and the 1 extra Fire
makes flames pop up along the beam.

Required stabilizer quantities,
Light/Dark stabilization of their own flavors, and the rates and manifestations
of dropout remain to be specified before implementation. Their interaction
with pairing and annihilation (D35–D36) must also be defined.

**D28.** Rate is a property of each **link**, not of the blocks at either end: each
connection between two points has its own transfer rate.

## Technology

**D29.** All of the mod's technology is built on working with these crystals.

### Machines

**D30.** Machines, also called **rituals**, are distributed, free-form multiblocks built
from **program components**. Each program component does one specific sub-task
of the machine's overall job. Players build a machine by combining components
into a structure of their choosing, not by following a fixed template.

**D31.** Machines run on mana. Players split Raw mana into the flavors that each
component needs and direct it to those components.

### Ritual totems

**D43.** Rituals use at least five component roles, represented by totem
blocks. These are roles available to a ritual, not a requirement to place
five blocks in every ritual:

| Role | Responsibility |
| --- | --- |
| Input item holder | Supplies item ingredients, materials, and reagents. |
| Input fluid holder | Supplies fluid ingredients and reagents. |
| Output item holder | Receives item products. |
| Output fluid holder | Receives fluid products. |
| Effector | Performs the ritual's operation on its target. |

Only the roles needed by an operation must be present. Components must be
within a bounded neighborhood rather than a fixed multiblock template (D30).
A **5 x 5 horizontal footprint with +/- 2 Y levels** is the tentative size,
not a finalized implementation constant.

Still to decide before implementation:

- The neighborhood's reference point and exact bounds, and whether the effect
  area can extend beyond the component neighborhood.
- Whether holders can be shared by effectors or explicitly assigned, how
  multiple qualifying holders are selected, and component multiplicity.
- How an effector's operation is selected from its incoming mana and/or
  configuration.
- What happens when outputs cannot fit, including when ingredients and mana
  are consumed relative to producing outputs and applying world effects.

### Emergent combination exceptions

**D44.** The combination heuristic must allow explicit special cases when its
general rules do not naturally produce the intended emergent effect. Larger
combinations need not be limited to an existing pair action with modifiers:
an explicitly defined compound reaction may establish an emergent action.
Light + Water + Earth producing Growth (D19) is an existing result that must
remain expressible even if the general heuristic cannot derive it naturally.

The heuristic remains the general mechanism; special cases supplement it
where needed. This allowance does not approve all of P1–P3 or settle individual
pair meanings. Matching by composition versus merge structure, precedence
between matching special cases, and whether a case replaces or adds to the
derived effect remain open and must be specified before implementation.

### Plant growth scaling

**D45.** The Light + Water + Earth plant-growth effect (D19) uses the following
random-tick opportunity multiplier for eligible plants in its area of effect:

```
multiplier = 1 + (waterPower + earthPower) / 2
```

The average uses real division, not integer truncation. Power means the total
power of each flavor, including its base contribution. Thus Water/Earth powers
of 1/1 give x2, 2/1 give x2.5, 2/2 give x3, and 3/3 give x4. This is an
average-based rule, not a minimum-of-the-two rule. Water and Earth must also
be balanced within one power point: `abs(waterPower - earthPower) <= 1`.
For example, 2 Water / 1 Earth is balanced, but 3 Water / 1 Earth is not.
An out-of-balance stream can still grow plants, but is unreliable and can kill
them. Overwatering or lack of nutrients are possible explanations; the mechanic
need not distinguish the cause. Death probability and its relation to imbalance
remain to be specified. The effect requires Light, Water, and Earth. The formula
is a tunable balance rule.

The multiplier applies locally to eligible plants' random-tick opportunities,
not to the world's `randomTickSpeed` gamerule or unrelated blocks and machines.
Fractional multipliers must be supported. Budding crystals are excluded (D10).
Eligibility and area follow D48; mana consumption and the scheduling mechanism
remain to be specified before implementation.

**D46.** The base Growth stream's Light stabilizes both its Water and Earth
contributions up to power 3 each (D38): two additional points of each can be
added to their base power of 1. Higher power requires additional balancing.
A separately paired Light + Dark stream (life & death, D35) can provide that
balancing without cancelling the unpaired Light that establishes Growth.
Adding Dark alone instead annihilates unpaired Light under D36 and can remove
the Light needed for Growth. Stabilization levels follow D55; the interaction
of individually added stabilizers with annihilation still needs specification.

### Power units and growth execution

**D47.** Power is measured in whole integer units of each stream type. One
unit of Raw contains one unit of each of the six flavors, so it carries six
units of elemental power. This revises D27's quantity convention: one Raw
unit splits into one of each flavor, and one of each forms one Raw unit.
Conservation is measured in elemental power, not the unweighted sum of stored
units. Existing code and API documentation using six stored Raw units per set
must be migrated before this convention is implemented.

**D48.** Growth eligibility uses an **exclusion tag**: blocks that must not be
accelerated belong in that tag. Budding crystals remain excluded from plant
Growth (D10). The tag's contents must enforce this plant-growth scope; absence
from the tag must not accidentally accelerate unrelated machines. Exact tag
identity and eligibility handling remain to be specified.

The Growth area is **9 x 9 x 1**, centered on an effector block. The vertical
placement of the plant layer relative to the effector remains to be specified.
Rituals must not physically occupy the same space, but their effect areas may
overlap. Each overlapping Growth ritual independently supplies its extra tick
opportunities; there is no separately imposed additive or multiplicative
stacking formula. Fractional scheduling remains open. The precise physical
space reserved by a ritual also remains to be defined.

**D49.** The initial tunable dropout probability is **25 percentage points per
unstabilized excess power point**, capped at **85%**, for a five-tick span:

```
probability = min(0.85, 0.25 * excessPower)
```

Dropout is randomized. How random-tick opportunities relate to five-game-tick
spans, where along a stream checks occur, whether checks are per flavor, and
how much mana each release spends remain to be specified.

**D50.** Saturated and oversaturated crystal forcing uses the initial tunable
tick-rate multiplier below for element power above 2:

```
multiplier = 1 + (elementPower - 2)
```

Thus element power 3 gives x2 and power 4 gives x3. This describes forcing
above the first two power units, not a slowdown at lower power. Stabilization
requirements still apply (D38). Ordinary crystal growth and acceleration remain
subject to D7–D9; their composition with this multiplier, mana costs, scheduling,
and Light/Dark crystals' stabilization remain to be specified.

**Growth construction.** Light (life) + Water is the base for Growth in either
input order; Earth supplies the stability needed for it to take hold (D45).
Primary/secondary slot selection does not change this spell-creation result
(D57).

**D51.** Fire + Earth produces **Forge**, expressing thermal processing of
solid material (smelting in place). Earth + Air produces **Erosion**, whose
breaking-down behavior also covers crumbling. Crumbling is not a separate
Fire + Earth action. This resolves the competing pair meanings in P1/P3
without approving their other unresolved proposals.

### Ordered ritual spells

**D52.** Each combining prism has a **primary** and a **secondary** input slot.
Streams carry execution ordering as part of their merge structure (D37).
The primary stream's spells trigger first, in their existing internal order,
followed by the secondary stream's spells, also in their existing internal
order. Nesting prisms preserves both sub-streams' ordering; it does not sort
spells by a global priority or interleave their actions.

For example, combining primary Erode with secondary Forge produces the order
`Erode -> Forge`. Feeding that stream into another prism's primary slot and
a Collect spell, if available, into its secondary slot produces
`Erode -> Forge -> Collect`. This example does not establish Collect as a
new spell. Action timing, intermediate world changes, and failure handling
between actions remain to be specified.

**D53.** Players can cast only one spell at a time. Rituals can combine multiple
spells through prisms, with execution ordered by D52. More than two spells in
the combined stream risks **overload dropout** effects along its path unless
balancing elements are added, such as a pre-paired Light + Dark stream. This
spell-count requirement is distinct from per-flavor power stabilization (D38).

Balancing is not guaranteed to preserve the spells: adding Light + Dark may
instead complete the composition needed to convert the stream to Raw (D27).
When the spell-bearing stream converts to Raw, its spells are cancelled; the
result carries Raw mana rather than executing the previous spell sequence.
This risk applies to multi-spell combinations generally, not only exactly
three spells.

Spell counting follows D54. Balancing quantities, overload dropout probability, and its
relationship to per-flavor dropout remain to be specified. D49's per-flavor
probability is not automatically an overload-dropout probability.

**D54.** A spell is established by combining a pair of elements. Adding extra
individual elements to that spell modifies or strengthens it; it does not
create another spell or increase its spell count, even if those additions
could form another pair when considered in isolation. Combining already
formed spells joins their sequences under D52 and counts them separately for
D53. Growth remains a special emergent effect under D44–D45, not an extra
spell automatically created by the addition of Earth.

**D55.** The stabilization power levels are:

| Stabilization | Supported power level |
| --- | --- |
| Neither Light nor Dark | 2 |
| Light alone or Dark alone | 3 |
| Light and Dark added individually | 5 |
| Light + Dark added together as paired life & death | 6 |

Individually added Light/Dark and a pre-paired life & death stream are distinct
constructions. These levels do not waive Raw formation and spell cancellation
(D27, D53). Individual stabilization survives annihilation as released energy
(D62). Required stabilizer quantities and the mapping of these power levels
to multi-spell overload balancing remain to be specified.

**D56.** When a stream contains at least **1 unit each of Earth, Air, Fire,
and Water**, adding a **life & death** stream converts that selection into
**1 unit of Raw**:

```
1 Earth + 1 Air + 1 Fire + 1 Water + 1 life & death = 1 Raw
```

One unit of life & death contains one paired unit each of Light and Dark;
together the selected inputs supply six elemental power units (D47). Individually
added, unpaired Light/Dark does not satisfy this paired-input condition.

This conversion takes precedence over using the selected elements for spell
execution or life & death stabilization. Spell cancellation follows D53;
excess unconverted mana is subject to D38. Conversion is recursive: after
each set converts, the remaining inputs are checked again, repeating while
another complete set is available. For example, two units of each classical
element plus two units of life & death produce two Raw units. The treatment
of remaining spell structure is settled by D62: cancel all spells and release
unconverted excess through dropout.

**D57.** Element combinations used to **create a spell are commutative**:
swapping a prism's primary and secondary inputs does not change which spell
the pair creates. For example, Water + Light (life) and Light (life) + Water
both qualify as the base for Growth. This does not make the inputs' distinct
pairing or construction history irrelevant to stabilization or Raw formation.

Commutative spell creation is distinct from sequencing **already-formed
spells**. When joining spell sequences, primary executes before secondary
and each sub-stream retains its internal order (D52). Thus Erode followed by
Forge is still a different execution sequence from Forge followed by Erode.

**D58.** An added element's role depends on whether it belongs to the spell's
base pair. Adding a base element increases the existing spell's **power**,
rather than applying that element's separate modifier action or creating a
new spell. An element outside the base pair instead applies its defined
**type of modifier action** to the existing spell (D54).

For Erode, whose base elements are Earth and Air, adding either Earth or Air
increases its power and lets it **reach further**. Adding Water instead adds
the **magnet** effect that draws produced drops toward the player. Repeated
base elements do not give Erode separate anchoring, diffusion, or other
Earth/Air modifier actions. Numerical power-to-reach scaling and the complete
non-base modifier action definitions remain to be specified and tunable.

**D59.** When used as non-base modifiers (D58), **Earth increases range** and
**Air decreases range**. Each added unit of Air power reduces range by an
initial tunable **15%**. Each added unit of Earth power increases range by an
initial tunable **15%**, but contributes at most **3 additional blocks**.
Earth additions use the stream's construction history (D37). Adding `x` Earth
units together applies a single `15% * x` boost to the reach immediately
before that addition, rounds the increment to whole blocks, and clamps it
between `1` and `3 * x` blocks: the minimum is per addition event, while the
maximum is per added unit. Adding
units separately instead recalculates from the updated reach at each step:

```
increment = clamp(round(currentReach * 0.15 * x), 1, 3 * x)
nextReach = currentReach + increment
```

For example, separate one-unit additions to reach 5 yield reach 6, then 7,
then 8: the unrounded increments are 0.75, 0.90, and 1.05. A grouped addition
uses its combined percentage once rather than replaying individual additions:
three units together give `round(5 * 0.45) = 2` extra blocks, for reach 7,
whereas three sequential units give reach 8. Grouped and sequential
construction can therefore produce different reach.
The merge history must preserve this distinction rather than reducing all
Earth modifiers to a single total. Thus `n` added Earth units increase reach
by at most `3 * n` blocks in total.

Spells must retain at least **2 blocks of reach from the casting position**;
range reduction cannot take them below that floor. Air follows the same
construction-sensitive grouping: `x` units added together apply one
`15% * x` reduction, while separate additions recalculate from the updated
reach. Its calculated reduction rounds **down**, with a minimum of **1 block
per addition event**:

```
reduction = max(1, floor(currentReach * 0.15 * x))
nextReach = max(2, currentReach - reduction)
```

The two-block reach floor takes precedence over the minimum reduction: an
already two-block spell stays at two blocks. For example, three sequential
one-unit Air additions to reach 5 give 4, then 3, then 2; three units together
remove `floor(5 * 0.45) = 2` blocks and give reach 3. Earth's three-block
per-unit cap limits increases; Air reductions are bounded by the remaining
reach above two blocks. D65 specifies Earth half-way rounding and mixed
Earth/Air adjustment order.

These modifier actions do not apply when Earth or Air belongs to the spell's
base pair. In particular, both are base elements of Erode: adding either
increases its power and reach, so neither provides a way to reduce Erode's
range. This decision replaces the earlier proposed Earth/Air modifier families
with generic range adjustment.

### Directed Raw stream contact

**D60.** Crossing a directed Raw mana stream stronger than **1 Raw unit**
deals **magic damage that bypasses armor**. The first Raw unit is safe and
does not contribute to damage:

```
damagePoints = max(0, rawUnits - 1)
```

Thus a 1-Raw stream deals no damage, a 2-Raw stream deals 1 damage point,
and a 3-Raw stream deals 2 damage points. Strength is measured in Raw units,
not their sixfold elemental power equivalent (D47). Repeated exposure timing,
beam contact bounds, and interaction with non-armor magical protections
remain to be specified before implementation.

**D61.** Erode uses MMDLib's crushing recipe registry as the basis for material
processing results. Use the public `mmdlib:crushing` recipe type and the live
server recipe manager rather than duplicating crushing recipes or maintaining
a private registry. Recipe outputs determine processing yields; Erode does
not apply an unconditional doubling multiplier. This selects the processing
recipe source, not the still-to-be-defined precedence between processing,
ordinary mining drops, and preservation modifiers.

**D62.** Individual modifiers must be added before spells are joined into a
multi-spell stream. Adding an individual element after that join is invalid;
this does not prohibit adding a paired life & death utility stream for balance.
If Raw conversion occurs, all spells are cancelled and all unconverted excess
undergoes dropout. Individually added Light/Dark that annihilates retains its
stabilization contribution as released energy, rather than losing that benefit.

**D63.** Light + Water establishes **Mend**. Adding Earth establishes the special
**Growth** action in place of Mend, without adding a second spell. The default
pair catalogue includes the team's Light/Dark pair definitions:

| Pair | Action |
| --- | --- |
| Earth + Light | Ward: preserve physical integrity. |
| Earth + Dark | Entomb: bury or enclose in solid material. |
| Air + Light | Reveal: detect concealed presence. |
| Air + Dark | Shroud: suppress signs of presence. |
| Fire + Light | Radiance: life-supporting light and warmth. |
| Fire + Dark | Blight: decompose living/organic material. |
| Water + Light | Mend: restore damaged things; Earth establishes Growth. |
| Water + Dark | Leach: dissolve and extract using explicit recipes. |

These are spell meanings, not blanket authorization for all world interactions;
recipe inputs, protections, costs, and effect-specific limits remain required.

**D64.** Direct Light/Dark combination in a prism pairs one-for-one into life &
death, retaining unmatched excess unpaired: 3 Light + 2 Dark produces 2 life &
death units and 1 unpaired Light. They do not form pairs outside a prism;
outside-prism contact instead annihilates unpaired Light/Dark and releases
stabilizing energy (D36, D62).

**D65.** Mixed Earth/Air range modifiers apply in recorded addition order.
Round Earth increments to the nearest whole block with exact halves upward,
then enforce D59's minimum and cap. Air retains its round-down rule and the
minimum two-block remaining reach.

**D66.** When annihilation removes a spell's base element completely, cancel
that spell. If too few polar units survive for all spells, primary-first spells
retain available units first, following D52. Required tuning inputs must be
supplied for unspecified costs, base ranges, overload probabilities, and
plant-death probabilities; implementation must not invent defaults for them.

**D67.** Elemental dropout severity scales with unstabilized excess power,
separately from the event probability of D49:

- 1 excess unit: particles only.
- 2 excess units: droplets/embers beneath and parallel to the beam in a narrow
  one-block area, with rapid falloff; roughly four blocks below, no particles
  should reach anything. This tier is visual rather than block-changing.
- More than 2 excess units: material effects along the path, with increasing
  range and slower vertical falloff as excess increases.

Water produces droplets and, at material severity, source blocks. Fire produces
embers and fires. Earth places dirt; Air crumbles existing blocks. Light emits
light and at high severity causes proximity-based bloom-like glare. Dark
produces darkness and proximity-based blindness. Exact spatial curves, effect
rates, Light/Dark proximity thresholds, and release costs require explicit tuning.

**D68.** The heuristic engine exposes a public mana-package evaluation API using
immutable streams and results. It does not mutate the world. The complete
pair/modifier catalogue is initially supplied as immutable typed Java data;
a datapack schema and reload adapter will be reviewed separately. Dependency-free
heuristic checks run under `pureTest` and are required by Gradle `check`.

**D69.** Implement the sixteen first player-cast effects, controls, identifiers,
and tunable defaults in [SPELL_RUNTIME_PLAN.md](SPELL_RUNTIME_PLAN.md). Players
use a spell-focus item with selection; each cast is one spell, but previously
applied timed effects can overlap. Ability Core alpha.42 is resolved from its
pinned public GitHub release while package credentials are unavailable. Runtime
code belongs in `spell` and `spell.client`.

**D70.** Actual casting power will come from Sky's Ley Lines. The integration
plan remains in progress. On joining a world, each player automatically receives
six elemental pools (Light, Dark, Earth, Air, Fire, Water), each with capacity
10 units and an initial balance of 3 units. There is no Raw player pool: Raw
cannot be used to cast. Existing pools retain their saved balances and settings
on subsequent joins. Recharge is disabled initially; it becomes available once
the player has a casting focus, as specified by D77. Casting charges these pools
as specified by D76.

**D71.** Craft one spell focus from three sticks on a diagonal, with one
crystal of any of the six flavors directly above and another directly below
the center stick. The two crystals may have different flavors. The recipe
supports horizontal mirroring. Temporary blocks are `technomagy:temporary_earth`
and `technomagy:spell_light`; timed status effects are `technomagy:shroud` and
`technomagy:leach`; plant exclusions use `technomagy:growth_excluded`.

**D72.** Each ritual has a distinct identity, an owner player UUID, and its own
mana reserve, persisted with its controller and owned by Technomagy (D40).
Directed mana feeds that ritual's reserve. Reserves are isolated per ritual,
including between rituals owned by the same player, and are separate from
personal casting pools (D70).

Ritual costs and any defined failure deductions are paid from that ritual's
reserve, with transfers, consumption, and failures attributed to its owner.
They do not debit the owner's personal casting pools, another player's pools,
or another ritual's reserve. Insufficient power stops the operation rather than
falling back to another account. Owner attribution does not itself authorize a
personal-pool deduction; such deductions require a separate explicit rule.

This decision establishes ownership and accounting isolation, not an
implementation. Reserve capacities, collection and transfer contracts with
Sky's Ley Lines, failure costs and effects, and ownership lifecycle rules
remain to be specified before implementation.

**D73.** The existing `technomagy:spell_focus` is the personal casting device
and the wand for machinery configuration (D23). A client element-selection GUI
opens while holding it using a rebindable Minecraft Controls key, default `.`.
It offers all six elements and Raw, since Raw must be directed to prisms.
The selected mana type is saved on each device independently of hotbar spell
selection (D74); changing one does not change the other. Selection is validated against
the actual held device on the server. Raw selection is for directing mana and
does not make Raw castable or create a Raw player pool (D70). This selector does
not itself implement director/prism linking or transfer.

**D74.** Ability Hotbar owns overall player spell selection. Require
`org.bitwisemadness.abilityhotbar:ability_hotbar:1.0.0-alpha.56` on client and
server, resolved through its exact public GitHub release artifact, alongside
Ability Core alpha.42. The hotbar's management menu replaces the focus's spell
picker and spell-selection item data. Existing item spell-selection data is
ignored; it does not override hotbar selection.

Right-clicking the held focus casts the Technomagy spell assigned to the active
hotbar's selected slot, read from Ability Hotbar's server-owned player state.
Empty or non-Technomagy assignments refuse without a fallback. Ability Hotbar's
activation controls also invoke Technomagy spells through Ability Core. Both
paths require a living, non-spectating player holding a focus in either hand;
the requested spell need not match legacy item selection or the currently
selected slot when activating a different slot directly. Right-click uses the
same server activation pipeline. The separate `.` machinery element selector
(D73), including Raw, remains; Raw still cannot be cast. Spell charging follows
D76.

**D75.** Show the player's six personal elemental pools (D70) as three split
glass orbs in the bottom-right HUD. Pair Earth with Air, Fire with Water, and
Light with Dark (D34). Each chamber fills independently from the bottom and
eases to its new level over 240 milliseconds. The active palette uses
BattleMageDemo's Fire `#F3764D`, Water `#4BA4F4`, Air `#B7DBDC`, and Earth
`#9BC66A`. Light is eggshell white `#F0EAD6` and Dark uses a violet highlight
`#A88AC9` for contrast against the glass. Show each element's exact
balance/capacity below its name. Use one shared text size across the HUD.
Show inactive pools with gray text and an empty chamber.
Clamp the visual fill at capacity while still displaying any excess balance.
This HUD displays personal elemental pools only.

Show the orbs during ordinary gameplay without requiring a held device. Hide
them for open screens, hidden GUI (F1), spectators, or incomplete pool data.
Anchor the group eight scaled pixels from the right and bottom edges. Use up
to 160 scaled pixels to the right of the vanilla hotbar, with an eight-pixel
gap. Place the orbs side by side with labels below when the gutter is wide
enough. Stack the orbs with labels beside them at larger GUI scales. Skip the
HUD if the available space cannot fit the stack.

The server sends a six-entry display snapshot only to its owning player,
after player ticks when displayed data changes, and initially for a new player
instance. Client display state clears on disconnect; server sync caches clear
on logout and server shutdown. This is read-only display synchronization,
not a new reserve or a client-side authority over power.

**D76.** Player casts charge the caster's personal elemental pools (D70).
Charging is controlled by the server option `payment.enabled`, which defaults
to **on** now that recharge exists (D77). Players in creative mode are never
charged.

Costs are whole units (D47), configured per spell in the spell server config as
one section per spell with one value per flavor (0 to 64). Each spell's default
is its input stream: one unit of each base element, so Growth costs one Water,
Light, and Earth, and Life & death one Light and one Dark. Raw is never charged.
A spell whose six costs are all zero is invalid: it is reported when the config
loads and refused while charging is enabled, never cast for free.

Each cast is **validated, then charged, then applied**. Validation checks the
target and every protection, placement, recipe, and receiver condition that can
be checked without changing the world; an invalid cast is refused without a
charge. Sky's Ley Lines 0.5.6 spends one currency per call and lets other mods
veto each spend, so Technomagy approximates an atomic multi-currency payment:
it checks every costed pool first, spends them in flavor order, and if a later
spend fails returns the earlier ones. If a validated effect still does nothing
when applied (an event cancels it, placement is refused, or a random roll has no
result), the charge is refunded and the cast is refused. No effect is paid for
without happening. Ley Lines may also refuse a return, including restoring a
balance above capacity; such a failure is reported as an error naming the
player, pool, and units. A true atomic multi-currency spend in Ley Lines would
supersede the rollback.

**D77.** Personal pool recharge (D70) unlocks **permanently** the first time a
player has a `technomagy:spell_focus` anywhere in their inventory, by any means
(crafted, picked up, given, creative). The unlock is saved with the player in the
`technomagy:recharge_unlocked` attachment and kept through death; losing the
focus does not relock it. While unlocked, online, alive, and not spectating, the
player gains **1 unit
every 10 game ticks** in one pool chosen uniformly at random from the pools that
are active and below capacity, clamped to the remaining room. When every pool is
full or inactive, the pulse does nothing. The interval and units per pulse are
server-configurable. Offline, dead, and spectating players neither recharge
nor unlock. Ley Lines' fixed per-pool
automatic rate stays disabled, since it cannot choose a random element.

## Integration foundations

**D40.** Sky's Ley Lines is a **required dependency on both client and server**
and the foundation for ambient, naturally distributed world power: power that is not directed by a player or machine. Technomagy
retains its own directed mana links, prisms, storage, and elemental combination
rules (D14–D28). Ley Lines' raw conversion reserve and Technomagy's balanced
Raw mana (D15) are distinct concepts; their exchange is not defined by this
dependency choice. The elemental mapping from natural sources (D18), collection
rules, and dimension coverage still need to be specified.

**D41.** Ability Core is the integration foundation for player spell activation.
Technomagy owns spell construction, elemental interpretation, mana accounting,
and world effects. Ability Core supplies the shared activation contracts and
hooks. This choice does not settle the remaining spell proposals (P3), dynamic
spell identity, or ownership of charging and cooldown state.

**D42.** Resolve Sky's Ley Lines from the moddev.zone Maven repository, pinned
at `zone.moddev.mc.skysleylines:SkysLeyLines:0.5.6.121012` (verified published
and platform-compatible). Prefer
GitHub Packages for Ability Core, using a personal access token (PAT) where
authentication is required. Keep credentials in user-level Gradle properties
or CI secrets, never in the repository. CurseMaven is an alternative when the
required Ability Core build is available there. Pin exact tested dependency
versions; verify future version bumps and alternate repository coordinates
before changing build integration.

Upstream references:

- [Sky's Ley Lines](https://github.com/MinecraftModDevelopmentMods/SkysLeyLines)
  and its [integration guide](https://github.com/MinecraftModDevelopmentMods/SkysLeyLines/blob/master-1.21.1-neo/docs/INTEGRATION.md).
- [Ability Core](https://github.com/HexaHexes/ability-core) and its
  [API and dependency policy](https://github.com/HexaHexes/ability-core/blob/main/docs/api-surface.md).

## Proposals

Proposals are suggested extensions or changes to the design that are **not
final**. Nothing here is a decision: code must not implement a proposal until
it is accepted, at which point it becomes one or more numbered decisions.
Proposals are numbered **P1**, **P2**, ... and, like decisions, numbers are
never reused.

### P1. Elemental principles

*Status: proposed, under discussion with the team. Combines two team
suggestions with additions for Light and Dark.*

**Idea.** Each flavor stands for a **principle**, and combinations follow from
the principles involved. Players who learn the principles can predict what a
combination will do instead of memorizing a list, which fits magic as a
science (D1). A flavor may carry more than one meaning.

| Flavor | Principle | Notes |
| --- | --- | --- |
| Fire | **Energy**, volatility | On its own it produces heat; combined with other flavors it becomes other forms of energy. It *releases*. |
| Water | **Fluidity**, absorption | It *takes in* and conforms: the counterpart of Fire's release (Fire/Water are opposed). |
| Earth | **Stability**, solidness | |
| Air | **Impermanence**, movement | The counterpart of Earth's stability (Earth/Air are opposed). |
| Light | **Vitality**, order | The living, growing flavor: it grows unaided and drives growth (Light + Water + Earth). |
| Dark | **Entropy**, decay | The opposing pull to Light; it *denatures* Light. (Light/Dark are opposed) |

**Combinations by principle:**

| Combination | Meaning | Reasoning |
| --- | --- | --- |
| Fire + Air | Lightning | Energized air: electric storms. |
| Fire + Water | Motion | Steam, as used in a steam engine: it pushes things. |
| Fire + Earth | Forge | Thermal processing of solid material; smelting in place (D51). |
| Air + Water | Diffusion, mist | Spreading something out over an area. |
| Air + Earth | Erosion | Impermanence acting on stability: weathering, crumbling, breaking down, sifting (D51). |
| Water + Earth | Constraint | Slowing things down, holding them in place (mud). |
| Water + (Fire + Air) | Storm | Water combined with Lightning, a compound of a compound. A natural example of prism chaining. |

**Principle and form.** A combination's *principle* is fixed, but the
**component that consumes it decides the form it takes** (D30). Fire + Air is
energized impermanence: in a smelting component it is a hotter flame, and in a
weather component it is a lightning strike. Fire + Water is clouds when released
into the open sky, and motion when contained in a piston-like component.

**Effect on existing decisions if accepted:**

- **D2:** each flavor gains a principle.
- **D19:** its examples stay valid as *forms* of the principled combinations.
  Fire + Air "a hotter fire" and Fire + Water "clouds" become the open-air or
  smelting forms of Lightning and Motion rather than competing definitions.
- **D30:** program components gain a role: choosing the form a combination
  takes.

**Open points:**

- Light/Dark pair meanings are settled by D63; effect-specific execution
  behavior and limits still need concrete definitions.
- How strength (D32) and saturation (D10, D33) scale a combination's effect.

### P2. Systemic effect derivation

*Status: proposed. Builds on P1; not usable without it.*

**Idea.** The team cannot author every combination a player might try, so
effects are **derived** from rules about principles instead of looked up in a
complete list. Roguelikes get their "the designers thought of everything" feel
the same way: things have properties, and rules act on the properties, so
cases nobody planned still behave sensibly.

**Pairs are authored, larger combinations are derived.** Six flavors give only
15 pairs, so every pair is defined by hand (P1 defines six of them so far).
Natural opposites react (D34), and so tend to give the *active* results:
Earth + Air is erosion, Fire + Water is motion. In a larger compound, the
first pair is the **action** and each later flavor is a **modifier** that
applies its principle to that action's result. This describes a single spell's
derivation; combining multiple ritual spells preserves their action sequences
under D52 rather than turning every secondary spell into a modifier.

**Modifier order.**

The following proposed modifier order concerns derivation within a spell, not
execution order between spells; ritual spell execution follows D52.

1. Light and Dark apply first. They act on the *nature* of the result,
   preserving or destroying it, so they settle that before anything shapes it.
2. The other flavors then apply in **mix order**: the order the prism chain
   added them, read from the stream's merge structure (D37).

If both Light and Dark are added as modifiers, unpaired, they annihilate
(D36) rather than both applying.

**Worked example: erosion.** Earth + Air is a mining effect that breaks blocks
and produces drops. Each modifier changes what happens to the result:

| Erosion + | Principle on the result | Effect |
| --- | --- | --- |
| Dark | entropy | drops voided |
| Light | order, preservation | block kept whole (silk touch) |
| Fire | energy | drops smelted |
| Water | absorption | mild **magnetic** pull: drops drift slowly toward the player |
| more Earth | base-element power | greater reach (D58) |
| more Air | base-element power | greater reach (D58) |

The Water row applies to any effect that produces drops, not only erosion:
absorption *takes in* the result.

The prisms are chained one modifier at a time:

```
prism 1: Earth + Air   → erosion
prism 2: (1) + Light   → silk-touch erosion
prism 3: (2) + Fire    → silk-touch, auto-smelting erosion
```

Mining stone with the result of prism 3 keeps the block whole and then smelts
it, giving smooth stone.

**Continuous parameters.** Beyond picking the action and modifiers, the stream's
proportions tune the effect: secondary flavors shift radius, duration, or
strength; strength (D32) and saturation (D10, D33) scale it. Every input maps
to *some* result, so nothing simply does nothing.

**Forms.** As in P1, the consuming component decides the form an effect takes
(D30). A decomposition release (D38) is an effect with no component: the world
decides the form, by the same principles (flames for Fire, mist for Water, and
so on).

**Target affinities.** World things carry flavor profiles, reusing the data
behind natural sources (D18: lava is Fire + Earth). Effects react with a
target's profile: Water on something Fire-affine quenches it, Air on something
Earth-affine erodes it. Behavior is then stream × target × component, which
multiplies cases without authoring each one.

**Named results.** Hand-authored combinations (such as Light + Water + Earth
for growth) are named points in the derived space, recorded when the player
discovers them. They may follow the general rules or use an explicit emergent
reaction special case where those rules do not produce the intended effect
(D44).

**Data-driven.** Pair meanings, flavor principles, target affinities, named
results, and emergent reaction special cases live in datapacks, so modpacks
and add-ons can fill gaps.

**Open points:**

- D52 settles execution order when combining spells. How are compound merges
  classified as multi-spell composition versus within-spell modification or
  stabilization (as in D33)?
- Is there a maximum chain depth? Each merge grows the stream's structure
  (D37), which is saved and sent over the network. Past a limit, does the merge
  fail, destabilize, or collapse?
- Can streams merge anywhere other than a prism, for example two links feeding
  one node director, or must they stay separate?
- Should every combination really produce something, or may some be inert?
- Do world things get reactive affinities (the "target affinities" above)?
- D44 allows special cases to depart from derived results. How do they match
  composition or merge structure, how are overlapping cases prioritized, and
  when do they replace versus supplement the derived effect?
- How D11's weakening applies to Light and Dark crystals, given the pairing
  (D35) and annihilation (D36) rules.

### P3. Elemental magic notes (spells, modifiers, regional mana)

*Status: proposed; team input, not yet reconciled. The full notes are in
[elemental-magic-notes.md](elemental-magic-notes.md).*

**Idea.** The notes add three things to the design:

- **Spells** cast by the player, alongside the rituals and apparatus the
  player builds (D30). The two are not opposed: spells come from the player,
  rituals from things the player sets up, and both follow the same elemental
  rules. A ritual is a large-scale machine, so its effects differ **subtly**
  from a spell's with the same principle, the way hitting something with a
  hammer differs from a machine running an autohammer: same action, but
  sustained, automated, and at scale. A **spell** is fire-and-effect: cast
  once, it happens once. A **ritual** continues its effect **as long as power
  is applied**.
- **Element meanings and modifier axes**, especially six paired axes for Earth
  (stability) and Air (impermanence): Persistence/Acceleration,
  Anchoring/Mobility, Reinforcement/Diffusion, Consistency/Volatility,
  Solidification/Phasing, and Resistance (or Containment)/Propagation.
- **Regional imbalance**: the local mana blend follows biome (set by biome
  tags) and nearby blocks sampled by collectors, with sculk catalysts as a
  Dark source. This extends D18.

**Already settled against the design:**

- The Light + Dark "power stream" is life & death (D35), releasing its energy
  on impact (D39).
- Refraction splits Raw in one step; recombining is two streams at a time
  (D20, D27).
- Natural sources follow D18 where the notes differ (lava gives Fire and
  Earth, not Fire only).
- The notes' optional loss when refracting and recombining is **not adopted**;
  D26 keeps both lossless. It stays in the notes as a suggestion.
- **Dark's meaning.** The notes' "destruction and harm" extends P1's entropy
  and decay rather than replacing it.
- **Water + Earth** stays Constraint (P1), and growth stays
  Light + Water + Earth (D19). The notes' Grow is not adopted.

**Conflicts still open:**

- **Water's meaning.** The notes make Water motion and flow; P1 gives it
  fluidity and absorption, from two team suggestions. Flow and fluidity agree;
  absorption is the extra meaning. The Water magnet on drops (P2) fits either
  reading.

**The notes' own open questions** (whether the power stream fuels every spell,
how non-opposed pairs choose between base and modifier, fortune on Erode,
Resistance vs Containment, and Steam's final effect) are listed in the notes
file.
