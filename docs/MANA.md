# Mana: Power System and Capability

Mana is Technomagy's power system: wireless, typed power that moves between
blocks the way Forge Energy (FE) does, but that also records *which kind* of
power it is (D13, D24). This document covers how mana is modelled in code,
the capability that lets blocks store and exchange it, and how to use both.

The design decisions it implements are in [DESIGN.md](DESIGN.md) and are cited
by number (D14, D24, ...). The code is in
`src/main/java/zone/moddev/mc/technomagy/mana/`.

## Contents

- [Status](#status)
- [Mana types](#mana-types)
- [Quantities: `Mana`](#quantities-mana)
- [The capability: `IManaHandler`](#the-capability-imanahandler)
- [Ready-made handlers](#ready-made-handlers)
- [Using mana in a block](#using-mana-in-a-block)
- [Moving mana between blocks](#moving-mana-between-blocks)
- [Saving and syncing](#saving-and-syncing)
- [Saturation](#saturation)
- [Open questions](#open-questions)

## Status

The public `SpellEngine` evaluates immutable `SpellStream` trees without world
mutations. It resolves all fourteen spell pairs and the life-and-death utility
pair, ordered composition, grouped additions, pure reinforcement, polar
annihilation, stabilization energy, recursive Raw conversion, spell cancellation,
and the Mend-to-Growth special handler. See [SPELL_HEURISTICS.md](SPELL_HEURISTICS.md)
for the API, tuning inputs, and boundaries.

Execution preparation resolves range, costs, overload risk, and imbalanced
Growth mortality using required caller-supplied policies. Numerical growth,
range, crystal forcing, and elemental dropout rules are tunable. Dropout
severity is reported separately from probability. No executor, payment,
particle rendering, or world mutation is invoked by evaluation/preparation.
The separate `spell` package now supplies sixteen approved first player-cast
handlers through Ability Core; see [SPELL_RUNTIME_PLAN.md](SPELL_RUNTIME_PLAN.md).
Casts charge the caster's personal pools unless the server disables payment.

D70's personal reserves are now initialized automatically on login through
LeyLines: six elemental pools, each with capacity 10 and starting balance 3.
Raw has no player pool. Existing pools retain saved balances and settings;
reconnecting does not refill them. Having a casting focus unlocks recharge
permanently (D77): one unit every 10 ticks goes to a random active pool that
is not full. Spell payment is on by default (`payment.enabled` in
`technomagy-spells-server.toml`). It charges per-spell configurable costs,
validating before charging and refunding casts that turn out ineffective. See [SPELL_RUNTIME_PLAN.md](SPELL_RUNTIME_PLAN.md#payment).

Six dependency-free suites run with `./gradlew pureTest`, are required by
`./gradlew check`, and can also run with `bash scripts/check_spell_heuristics.sh`.
They compile the pure engine without Minecraft/NeoForge on the classpath.

**Legacy quantity boundary:** the existing flat `Mana` capability below still
uses six stored Raw units per split set, while the new heuristic API follows
D47 (one Raw unit contains six elemental units). Do not pass legacy amounts
into the engine as if these conventions were interchangeable. Capability/save
migration and connecting the engine to prism block entities are separate work.

| Part | State |
| --- | --- |
| Mana types and quantities (`ManaType`, `Mana`) | Implemented |
| Capability (`IManaHandler`, `ManaCapabilities.BLOCK`) | Implemented |
| `ManaStorage`, `InfiniteManaSource` | Implemented |
| Raw Mana Source block (creative-only, endless Raw) | Implemented |
| Links between blocks, per-link rate (D21, D28) | Not yet |
| Rendered mana lines (D22), wand linking (D23) | Not yet |
| Node director and prism behavior (D20, D26) | Blocks only; they move no mana yet |
| Saturation levels (D10, D32, D33) | Design only |
| Merge structure and Raw conversion (D27, D37, D56) | Implemented in the pure stream engine; flat `Mana` remains legacy and is not connected |
| Life & death energy release on impact, FE conversion (D39) | Design only |
| Light/Dark pairing and annihilation (D34–D36) | Pure interpretation implemented; world effects not connected |
| General flavor imbalance and dropout (D38) | Ledgers, probability, and severity implemented; environmental execution pending |
| Natural and generated sources (D17, D18) | Not yet |
| Item capability (mana in items) | Not yet |

## Personal pool HUD

D75 displays six personal elemental pools as three split glass orbs at the
bottom right: **Earth/Air**, **Fire/Water**, and **Light/Dark**. Each orb has two
independent chambers that fill from the bottom, easing to new levels over
240 milliseconds. Labels show each element's name and exact balance/capacity
immediately; inactive pools have gray text and empty chambers. Visual fill is
clamped at capacity without hiding excess balances in the labels.

The group occupies at most 160 scaled pixels in the gutter beside the vanilla
hotbar, anchored eight pixels from the right and bottom edges. With enough
space, the orbs sit side by side with labels below; narrower gutters stack
them vertically with labels beside them. If the stack cannot fit, the HUD is
hidden rather than overlapping the hotbar. All captions share one text scale.
Light is eggshell white and Dark uses the accepted violet highlight for
contrast against the glass. There is no Raw orb and no requirement to hold a
focus. Open screens, F1, spectators, and incomplete pool data hide the HUD.

`ManaPoolSnapshot` carries six fixed entries in `ManaType.FLAVORS` order.
`ManaPoolSync` samples server-owned LeyLines pools after player ticks and sends
only first/changed snapshots to their owner. Respawn creates a fresh recipient
instance; logout/server shutdown release sync caches. `PersonalManaHud` holds
only client display state, cleared on disconnect. This display does not consume,
regenerate, or transfer power. The implementation follows the supplied
[BattleMageDemo HUD](https://github.com/MinecraftModDevelopmentMods/BattleMageDemo/blob/0952f35707b55e4f15544400b473fad2fe4349fc/src/main/java/zone/moddev/mc/battlemagedemo/MageClient.java)
and its companion snapshot packet.

## Mana types

`ManaType` has seven values (D14):

| Type | Notes |
| --- | --- |
| `FIRE`, `EARTH`, `AIR`, `WATER` | The classic elements. |
| `LIGHT`, `DARK` | The two natural flavors. |
| `RAW` | Undifferentiated mana: its own type, but treated as an even mix of the six flavors (D15). |

The six flavor types match `crystal.Flavor` one to one and in the same order:
`ManaType.of(flavor)` and `type.flavor()` convert between them. `RAW` has no
flavor. `ManaType.FLAVORS` is an immutable list of the six flavor types.

Opposing pairs (D11): Light/Dark, Earth/Air, Fire/Water.

## Quantities: `Mana`

A `Mana` is an **immutable amount of each of the seven types** (D25). A stream
of mana is one `Mana`, so a combined stream keeps its proportions: 2 Water +
1 Light + 2 Water is `water=4, light=1`, not a new named type.

```java
Mana water = Mana.of(ManaType.WATER, 10);
Mana raw = Mana.raw(60);
Mana mix = water.plus(Mana.of(ManaType.LIGHT, 2)); // water=10, light=2
mix.get(ManaType.WATER);  // 10
mix.total();              // 12
Mana.EMPTY.isEmpty();     // true; every all-zero quantity is EMPTY
```

Amounts are non-negative `int`s. Creating a negative amount throws
`IllegalArgumentException`; arithmetic that would overflow a type's amount
throws `ArithmeticException`. `total()` instead saturates at
`Integer.MAX_VALUE`, because an infinite source reports `MAX_VALUE` of a type.
Instances are thread-safe.

### Operations

| Method | Result |
| --- | --- |
| `plus(other)` | Both merged, type by type. This is combining (D20). |
| `minus(other)` | `other` removed. Throws if `other` isn't fully contained. |
| `min(other)` | The smaller amount of each type: what can be taken toward `other`. |
| `contains(other)` | Whether `other` could be taken in full. |
| `only(types...)` | Only the given types kept. |
| `with(type, amount)` | One type's amount replaced. |
| `limit(max)` | At most `max` in total, proportions kept. Used for rate limits. |
| `splitRaw()` | Raw split into flavors, 6 Raw to 1 of each (D27). |
| `formRaw()` | Whole sets of one of each flavor recombined into Raw; the reverse. |
| `share(type)` | Fraction of the stream that is `type`, counting Raw as an even mix. |

### Lossless by construction (D26, D27)

No operation creates or destroys mana:

- `plus` and `minus` move whole amounts.
- `splitRaw` and `formRaw` convert exactly `Mana.RAW_PER_SET` (6) Raw into one
  of each flavor and back. Whatever doesn't make a whole set stays as it was:
  Raw 20 splits into 3 of each flavor plus Raw 2. Splitting then recombining
  returns the original exactly.
- `limit` decides how much of a stream moves this time; the rest stays where it
  was.

`RAW_PER_SET` is 6 so that totals are conserved both ways.

### Rate limiting with `limit`

Proportional shares are usually fractions, and amounts are whole numbers, so
`limit` uses the **largest-remainder method**. Each type first gets its exact
share rounded down. Then the units lost to rounding go one each to the types
with the largest fractional parts. The result totals exactly `max`, and every
type is within one unit of its exact share.

Example: Fire 7, Air 3, Water 1 (total 11) limited to 5.

| Type | Exact share | Rounded down | Final |
| --- | --- | --- | --- |
| Fire | 35/11 = 3.18 | 3 | 3 |
| Air | 15/11 = 1.36 | 1 | 1 |
| Water | 5/11 = 0.45 | 0 | 1 (largest remainder) |

A small share still moves rather than starving behind larger ones.

### Measuring a stream with `share`

`share(type)` returns the fraction of the stream that is `type`, from 0 to 1.
Raw counts as an even mix, so it adds a sixth of itself to each flavor's
share. A component that needs "a stream that is at least 60% Water" checks
`stream.share(ManaType.WATER) >= 0.6`.

```java
Mana.of(ManaType.WATER, 4).with(ManaType.LIGHT, 1).share(ManaType.WATER); // 0.8
Mana.raw(6).share(ManaType.FIRE);                                         // 1/6
```

## The capability: `IManaHandler`

`IManaHandler` is the mana counterpart of NeoForge's `IEnergyStorage` (D24).
Anything that stores, accepts or supplies mana exposes one.

```java
public interface IManaHandler {
    Mana getStored();                              // everything stored
    int getCapacity();                             // all types together
    Mana receive(Mana offered, boolean simulate);  // returns the part accepted
    Mana extract(Mana wanted, boolean simulate);   // returns what was taken
}
```

- `receive` and `extract` follow FE's convention: with `simulate` true they
  only report what *would* happen.
- `extract` never returns more of a type than was asked for. To ask for
  "anything", pass `getStored()`.
- **A handler has no rate.** Rate belongs to the link between two handlers
  (D28); the link limits each transfer with `Mana.limit`. A handler only
  decides which types it takes or gives and how much fits.

### Looking it up

The capability is `ManaCapabilities.BLOCK` (`technomagy:mana_handler`), a
sided block capability like `Capabilities.EnergyStorage.BLOCK`:

```java
IManaHandler handler = level.getCapability(ManaCapabilities.BLOCK, pos, side);
if (handler != null) {
    // the block at pos handles mana
}
```

`side` is the face being accessed, or `null` for no particular side.

### Registering it

Register handlers in `RegisterCapabilitiesEvent` on the mod event bus. For a
block entity:

```java
event.registerBlockEntity(ManaCapabilities.BLOCK, MY_BLOCK_ENTITY_TYPE,
        (blockEntity, side) -> blockEntity.mana());
```

A block whose handler needs no state can register without a block entity.
This is how the Raw Mana Source works (see `machine.MachineContent`):

```java
event.registerBlock(ManaCapabilities.BLOCK,
        (level, pos, state, blockEntity, side) -> RAW_SUPPLY, RAW_MANA_SOURCE.get());
```

## Ready-made handlers

### `ManaStorage`

A plain store with a total capacity and a set of accepted types.

```java
new ManaStorage(1000);                                    // accepts every type
new ManaStorage(1000, EnumSet.of(ManaType.FIRE));         // a Fire-only component
```

- Capacity counts all types together. A negative capacity throws.
- `receive` refuses types it doesn't accept and takes as much of the rest as
  fits. If an offer doesn't fully fit, the accepted part keeps the offer's
  proportions.
- `extract` takes as much of each wanted type as is stored.
- `onChanged()` runs after a real (not simulated) change. Override it to mark
  the owning block entity changed.
- `setStored(mana)` replaces the contents for loading or client syncing. It
  doesn't check capacity or call `onChanged`.
- Not thread-safe: use it from the thread that owns its block entity.

### `InfiniteManaSource`

Endless mana of one type, accepting nothing. It is stateless, so one instance
can serve every block of a kind. `extract` returns everything asked for of its
type and nothing else; `getStored()` and `getCapacity()` report
`Integer.MAX_VALUE`.

The **Raw Mana Source** block (`technomagy:raw_mana_source`) is an
`InfiniteManaSource(ManaType.RAW)`. It is creative-only, for testing links.

## Using mana in a block

A component that runs on Fire mana, consuming 5 per tick while it works:

```java
public class FireComponentBlockEntity extends BlockEntity {
    private static final int FIRE_PER_TICK = 5;

    private final ManaStorage mana = new ManaStorage(1000, EnumSet.of(ManaType.FIRE)) {
        @Override
        protected void onChanged() {
            setChanged(); // so the world saves the new contents
        }
    };

    public IManaHandler mana() {
        return mana;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            FireComponentBlockEntity be) {
        Mana needed = Mana.of(ManaType.FIRE, FIRE_PER_TICK);
        if (be.mana.extract(needed, true).equals(needed)) { // enough for a full tick?
            be.mana.extract(needed, false);
            // ... do the component's work ...
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("mana", mana.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("mana")) {
            mana.deserializeNBT(registries, tag.get("mana"));
        }
    }
}
```

Different components need different types and consume at different rates
(D24): that's the store's accepted types and the amount taken per tick.

## Moving mana between blocks

Links (D21) and their rates (D28) aren't implemented yet. When they are, a link
moving mana from one handler to another each tick will follow this pattern:
simulate both sides first, then move only what both agree on.

```java
Mana offered = source.extract(source.getStored(), true).limit(rate); // what can leave
Mana accepted = target.receive(offered, true);                       // what fits
Mana moved = source.extract(accepted, false);
target.receive(moved, false);
```

Because `limit` keeps proportions, a mixed stream arrives with the same mix it
left with.

## Saving and syncing

- **`Mana.CODEC`** saves a quantity as a map from type name to amount, leaving
  out zeros: `{water: 4, light: 1}`. It rejects negative amounts and unknown
  type names.
- **`Mana.STREAM_CODEC`** sends a quantity over the network as one VarInt per
  type, in `ManaType` order.
- **`ManaStorage`** implements `INBTSerializable<Tag>` with `CODEC`.

**Corrupt saved data** is reported and fixed, never silently ignored. If a
store's saved mana doesn't decode (an unknown type, a negative amount, the
wrong tag type), `ManaStorage.deserializeNBT` logs an error naming the data and
the problem, then resets the store to empty. The next save writes the empty
contents, so the save is repaired, and the world still loads:

```
[ERROR] Unreadable stored mana {plasma:5}: Unknown element name:plasma ... Resetting this store to empty.
```

## Saturation

The heuristic engine implements D38/D55's stabilization levels: 2 without
polar stabilization, 3 with Light or Dark, 5 with individually added Light and
Dark, and 6 with pre-paired life and death. Annihilation retains stabilization
energy (D62), and paired input may instead cause Raw conversion (D56).

`SpellEngine.crystalMultiplier` implements D50's tuned forcing multiplier.
The engine reports chemical state and excess-power dropout but does not apply
crystal growth ticks. The older D10/D11/D33 examples are refined by these later
decisions; arbitrary nonpolar additions do not replace polar stabilization.

The flat `Mana` capability still lacks this construction/chemistry model.

## Open questions

These are undecided, so the code doesn't implement them yet:

1. **Power vs. link rate (D28, D32).** If merging pure streams adds power like
   a laser, can the merged stream exceed one link's rate, or does the rate
   still cap it, with the power showing only where the stream is used?
2. **Chain depth (D37).** The merge structure grows with each prism. Is there a
   maximum depth, and what happens past it? (Also listed in P2.)

Answered since this document was first written: denaturing is the polar
annihilation of Light and Dark (D34–D36), not a D19 reaction, and its energy
goes into saturation, so D26 still holds.
