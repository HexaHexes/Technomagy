# Spell heuristic API

`zone.moddev.mc.technomagy.mana.SpellEngine` is a pure Java 21 evaluator. It
implements the interpretation rules in D35–D68 and returns immutable data.
It does not cast spells, transfer inventory, spend mana, mutate blocks, or
render beams. Those operations consume its output through gameplay adapters.

## Construction and evaluation

The caller supplies seven distinct enum identities through `SpellElements`.
Minecraft adapters can use the existing `ManaType`; dependency-free clients
can use their own enum. `SpellEngine.Numbers.standard()` selects the exact
numeric starting rules agreed in the design. Construct a different `Numbers`
snapshot to tune range, growth, crystal forcing, or elemental dropout.

```java
var roles = new SpellElements<>(ManaType.EARTH, ManaType.AIR, ManaType.FIRE,
        ManaType.WATER, ManaType.LIGHT, ManaType.DARK, ManaType.RAW);
var engine = new SpellEngine<>(roles, SpellEngine.Numbers.standard());
var earth = SpellStream.element(ManaType.EARTH, 1);
var air = SpellStream.element(ManaType.AIR, 1);
var fire = SpellStream.element(ManaType.FIRE, 1);
var erode = SpellStream.merge(earth, air);
var forge = SpellStream.merge(earth, fire);
var ritual = SpellStream.merge(erode, forge);
var result = engine.evaluate(ritual); // Erode, then Forge
```

Spell creation is commutative; joining established spells is ordered. Pure
same-element merges reinforce a carrier. Grouped modifiers retain their unit
counts, while separate additions remain separate events. Individual modifiers
must precede multi-spell joining; a paired life-and-death utility stream can
still be supplied afterward for balance without increasing spell count.

`split()` returns the original primary and secondary inputs by identity. The
engine never rewrites that construction tree when chemistry changes active
quantities. A direct Raw leaf has no merge history: splitting it into flavors
is the separate D27 quantity operation, not reversal of a prior prism merge.

## Chemistry and cancellation

One life-and-death unit contains one Light and one Dark. One Raw unit contains
six elemental power units. Do not confuse this convention with the legacy
flat `Mana` capability, which has not yet been migrated from its old units.

Direct prism Light/Dark inputs pair one-for-one, retaining unmatched excess.
`engine.contact(first, second)` instead models outside-prism contact and
annihilates unpaired polar inputs. Annihilated power remains in the released
energy ledger and contributes stabilization. Paired inputs are never silently
unpaired to satisfy another reaction.

Every complete Earth/Air/Fire/Water/life-and-death set converts to one Raw unit.
Conversion cancels all spells; unconverted excess is placed in the dropout
ledger. Within a non-Raw sequence, surviving polar units are allocated to
spells primary-first. A spell with an exhausted polar base is marked cancelled.

`Chemistry` reports active elemental amounts, paired units, Raw units, pending
dropout, released stabilization power, and the supported stabilization level.
The pending dropout ledger is not discarded or automatically released.

## Effects and preparation

`evaluate()` reports ordered action identities, effective elemental power,
original modifier events, surviving modifier events, cancellation, and Growth properties. Light/Water
creates Mend; Earth establishes Growth as its replacement action. Growth
reports its multiplier and balance state. It does not tick plants.

`prepare(stream, executionTuning)` requires explicit settings for every active
spell kind. Each setting supplies:

- Initial reach from the effective base input amounts.
- Base-power reach scaling for later grouped reinforcement.
- Per-flavor mana cost for that evaluated effect.

The tuning also supplies overload probability and imbalanced-Growth mortality
policies. World executors must use surviving modifier events, not replay additions
that annihilation consumed. These callbacks must be pure, thread-safe, and stable for the tuning
snapshot. No production defaults are invented. Probabilities must be finite
and within `[0, 1]`; costs must be nonnegative integer quantities. Cancelled
spells are not priced. Missing settings fail before a prepared result is returned.

Earth/Air range events execute in recorded addition order. Earth rounds to
nearest with halves up, minimum +1 per addition event, and maximum +3 per unit.
Air rounds reductions down with minimum -1 per event; remaining reach is at
least two blocks under the standard tuning. Consumed polar modifier quantities
are not reapplied during preparation.

`dropout(evaluation)` reports per-flavor excess power, chance per five-tick
span, and severity: particles at one excess unit, droplets at two, and material
world effects above two. This is distinct from the pending release ledger and
from multi-spell overload risk. Spatial falloff and actual manifestations are
executor responsibilities, not silently chosen by the evaluator.

## Tunable catalogues

`SpellEngine.defaultCatalogue(roles)` returns immutable typed pair and modifier
maps. Copy and change those maps, then pass a new `SpellEngine.Catalogue` to the
three-argument constructor. Coverage must remain complete: fourteen distinct
spell pairs and six non-base modifiers. Life-and-death is always a utility pair.
Range interpretation follows the configured action category, not hard-coded
checks of an addition's element name.

The initial catalogue is typed Java data. Datapack schema, reload integration,
and serialization/versioning are intentionally separate, as agreed in D68.

## Verification and remaining integration

Run `./gradlew pureTest` or `bash scripts/check_spell_heuristics.sh` for the six
pure check suites. `check` depends on these suites. They exercise all pair
orders, grouped/sequential range, required tuning, invalid inputs, overflow,
long chains, pairing/contact differences, energy conservation, Raw cancellation,
primary-first polar allocation, Growth replacement, and immutable public results.

The evaluator is not proof of in-game effects. The first player-cast integration now connects it to Ability Core and the sixteen
approved effect handlers; see [SPELL_RUNTIME_PLAN.md](SPELL_RUNTIME_PLAN.md).
Prism/director block entities, legacy capability migration, arbitrary stream
editing, and stream save/render integration remain separate work. World-effect
costs, limits, timing, permissions, and transaction behavior must be supplied by
those callers rather than inferred from an effect's name.
