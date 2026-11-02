# Technomagy Design

## Core Premise

Technomagy treats magic as a science. Magic follows rules that can be studied,
learned, and engineered.

## Magic Flavors

Magic has six flavors:

- **Earth**
- **Air**
- **Fire**
- **Water**
- **Light**
- **Dark**

The first four are the classic elements. Light and Dark are the other two.

## World Generation

The world generates crystal geodes that work like vanilla amethyst geodes.
Each geode is made of either **Light** crystal blocks or **Dark** crystal blocks.

Each geode also contains **budding crystal** blocks, which work like vanilla
budding amethyst. A geode's budding blocks are the same flavor as the rest of
the geode: a Light geode has budding Light crystal, and a Dark geode has budding
Dark crystal.

Only Light and Dark generate naturally. No other flavor does. Each geode is a
single flavor, and crystal geodes as a whole turn up slightly more often than
amethyst geodes (about 5% more).

### Crystal Locator

Geodes are world features, not structures, so structure-finding tools such as
`/locate` and Explorer's Compass cannot find them. Technomagy will provide its
own **crystal locator**: an item that senses the resonance of nearby Light or
Dark crystals and points players toward geodes. Details are still to be
designed.

## Crystal Growth

Light and Dark budding crystals grow on their own, at the same rate as vanilla
budding amethyst.

Fire, Earth, Air, and Water budding crystals need **prompting** from the mod's
**mana** power system to grow properly. Without prompting they still grow, but
at about a quarter of amethyst's rate.

Every budding crystal, Light and Dark included, can be accelerated by supplying
enough mana of the matching type.

Beyond that, crystal growth can be **forced** even faster by feeding a budding
crystal a **supersaturated** stream: a compounded stream that carries more of
its own flavor than plain mana can. This is separate from the general growth
combination (Light + Water + Earth), which does not affect crystals.

For example, a Water crystal can be forced with Water + Light + Water. One
prism combines Water and Light into a new sub-element, and a second prism
combines more Water into that, supersaturating the stream with Water (see
[Combinations](#combinations)).

The middle flavor can be any other flavor; Light is only an example. Flavors
opposed to the crystal's own weaken the stream instead of boosting it. The
opposing pairs are:

- Light and Dark
- Earth and Air
- Fire and Water

So a Water crystal is boosted by Water + Earth + Water but weakened by
Water + Fire + Water.

## Progression

Players learn how to convert budding crystals into the other elemental forms
(Earth, Air, Fire, Water).

## Mana

Mana is the mod's power system. It is **wireless**: it moves without cables
or pipes.

### Types

Mana comes in seven types:

- one for each flavor of magic: **Earth**, **Air**, **Fire**, **Water**,
  **Light**, and **Dark**
- **Raw** mana, the seventh type

Raw mana contains all six flavors in an even mix that has not been
differentiated or split into its parts. Because of that even balance, Raw mana
counts as a **pure** form, even though it has six components.

Raw mana is the form players work with most during play. Players can **split**
Raw mana into its flavors to power the individual parts of their machines
(see [Machines](#machines)).

### Sources

Mana can be **generated** by players.

Mana also arises **naturally** from the world. Each natural source gives off
the type or types that match it. For example:

- bodies of water produce Water mana
- burning fires produce Fire mana
- lakes of lava produce Fire mana

A source can produce more than one type. Lava produces both Fire and Earth
mana.

### Combinations

Combining flavors of mana, or recombining flavors split from Raw mana, produces
effects. A combination behaves the way those forces would interact in the
physical world. Flavors that seem opposed do not cancel out; they react and
make something new.

Mana is combined in **prisms**. A prism merges incoming streams into a new
sub-element. Prisms can be chained: the output of one prism can feed another,
which combines more mana into it. Chaining builds compounded streams, and adding
the same flavor again supersaturates the stream with that flavor.

Examples:

- **Fire + Air:** a hotter fire. Likely used as a stage in a smelting process.
- **Fire + Water:** clouds. The water does not lower the temperature.
- **Fire + Water + Earth:** can trigger rain.
- **Light + Water + Earth:** accelerates growth, speeding up growth ticks for
  things nearby. It does not affect budding crystals (see
  [Crystal Growth](#crystal-growth)).

### Direction

Mana can be **directed**, in a way similar to ley lines: players channel it
along paths from where it arises to where it is needed.

Directed mana is shown in the world as a rendered **line** between the points
it travels between, so players can see where their mana is flowing.

Players configure the links between node directors and prisms with a **wand**,
a wrench-like tool. Details are still to be designed.

### Storage and Transfer

Mana is handled through a capability, the same way Forge Energy (FE) is, so
any block can store, accept, or supply it. Unlike FE, which tracks only an
amount, mana tracks three things:

- **type**: which mana it is
- **amount**: how much is stored or moved
- **rate**: how fast it moves

Different machine components need different types of mana, and consume it at
different rates.

A quantity of mana records how much of each of the seven types it holds: the
six flavors plus Raw. Raw is technically its own seventh type, but it is
treated as an even mix of the six flavors. Combinations keep their
proportions, so a stream records exactly which flavors it contains and in what
amounts.

Splitting and combining are **lossless**: a prism in split mode separates a
stream into its parts, a prism in combine mode merges streams, and no mana is
lost either way. Combining and then splitting gives back exactly what went in.

Splitting Raw keeps the total amount: 6 Raw splits into 1 of each flavor, and
1 of each flavor recombines into 6 Raw.

Rate is a property of each **link**, not of the blocks at either end: each
connection between two points has its own transfer rate.

## Technology

All of the mod's technology is built on working with these crystals.

### Machines

Machines, also called **rituals**, are distributed, free-form multiblocks built
from **program components**. Each program component does one specific sub-task
of the machine's overall job. Players build a machine by combining components
into a structure of their choosing, not by following a fixed template.

Machines run on mana. Players split Raw mana into the flavors that each
component needs and direct it to those components.
