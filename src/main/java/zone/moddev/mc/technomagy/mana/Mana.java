package zone.moddev.mc.technomagy.mana;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;

/**
 * An immutable quantity of mana: an amount of each {@link ManaType} (D25). A combined stream keeps
 * its proportions, so this records exactly which types a stream holds and how much of each.
 *
 * <p>
 * <b>Losslessness (D26, D27).</b> No operation destroys or creates mana. {@link #plus} and
 * {@link #minus} move whole amounts; {@link #splitRaw} and {@link #formRaw} convert exactly
 * {@value #RAW_PER_SET} Raw to one of each flavor and back, leaving any remainder as it was; and
 * {@link #limit} decides how much of a stream to move this time, so whatever it leaves out stays
 * where it was rather than being lost.
 *
 * <p>
 * Amounts are non-negative {@code int}s. Arithmetic that would overflow an amount throws
 * {@link ArithmeticException}; {@link #total()} saturates at {@link Integer#MAX_VALUE} instead,
 * since a full {@link InfiniteManaSource} reports {@code MAX_VALUE} of one type.
 *
 * <p>
 * Instances are immutable and thread-safe. All-zero quantities are always {@link #EMPTY}.
 */
public final class Mana {
    /**
     * The Raw mana that splits into one of each flavor, and that one of each flavor recombines
     * into. Six keeps the total amount the same both ways (D27).
     */
    public static final int RAW_PER_SET = 6;

    private static final int TYPES = ManaType.values().length;

    /** No mana. Every all-zero quantity is this instance. */
    public static final Mana EMPTY = new Mana(new int[TYPES]);

    /**
     * Saves a quantity as a map from type name to amount, leaving out zero amounts: {@code {water:
     * 4, light: 1}}. Rejects negative amounts and unknown type names.
     */
    public static final Codec<Mana> CODEC = Codec
        .unboundedMap(ManaType.CODEC, Codec.intRange(0, Integer.MAX_VALUE))
        .xmap(Mana::fromMap, Mana::toMap);

    /** Sends a quantity over the network as one VarInt per type, in {@link ManaType} order. */
    public static final StreamCodec<ByteBuf, Mana> STREAM_CODEC = StreamCodec.of(
        (buf, mana) -> {
            for (int amount : mana.amounts) {
                VarInt.write(buf, amount);
            }
        },
        buf -> {
            int[] amounts = new int[TYPES];
            for (int i = 0; i < TYPES; i++) {
                amounts[i] = VarInt.read(buf);
            }
            return of(amounts);
        });

    /** One amount per type, indexed by {@link ManaType#ordinal()}. Never exposed or modified. */
    private final int[] amounts;

    /** The exact sum of {@link #amounts}, which can exceed {@code int} range. */
    private final long exactTotal;

    private Mana(int[] amounts) {
        this.amounts = amounts;
        long sum = 0;
        for (int amount : amounts) {
            sum += amount;
        }
        this.exactTotal = sum;
    }

    /**
     * An amount of a single type.
     *
     * @param type
     *            the type
     * @param amount
     *            the amount, at least 0
     * @return the quantity; {@link #EMPTY} for 0
     * @throws IllegalArgumentException
     *             if {@code amount} is negative
     */
    public static Mana of(ManaType type, int amount) {
        int[] amounts = new int[TYPES];
        amounts[type.ordinal()] = checked(amount);
        return of(amounts);
    }

    /**
     * An amount of Raw mana.
     *
     * @param amount
     *            the amount, at least 0
     * @return the quantity; {@link #EMPTY} for 0
     * @throws IllegalArgumentException
     *             if {@code amount} is negative
     */
    public static Mana raw(int amount) {
        return of(ManaType.RAW, amount);
    }

    /** Wraps an amounts array this class owns, returning {@link #EMPTY} when it is all zero. */
    private static Mana of(int[] amounts) {
        for (int amount : amounts) {
            if (amount != 0) {
                return new Mana(amounts);
            }
        }
        return EMPTY;
    }

    /** Returns the amount, or throws {@link IllegalArgumentException} if it is negative. */
    private static int checked(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Mana amounts cannot be negative: " + amount);
        }
        return amount;
    }

    /**
     * The amount of one type.
     *
     * @param type
     *            the type
     * @return its amount, at least 0
     */
    public int get(ManaType type) {
        return amounts[type.ordinal()];
    }

    /**
     * The amount of all types together, saturating at {@link Integer#MAX_VALUE}.
     *
     * @return the total
     */
    public int total() {
        return (int) Math.min(exactTotal, Integer.MAX_VALUE);
    }

    /**
     * Whether this holds no mana at all.
     *
     * @return true for {@link #EMPTY}
     */
    public boolean isEmpty() {
        return this == EMPTY;
    }

    /**
     * This quantity with one type's amount replaced.
     *
     * @param type
     *            the type to change
     * @param amount
     *            its new amount, at least 0
     * @return the new quantity
     * @throws IllegalArgumentException
     *             if {@code amount} is negative
     */
    public Mana with(ManaType type, int amount) {
        int[] result = amounts.clone();
        result[type.ordinal()] = checked(amount);
        return of(result);
    }

    /**
     * Both quantities merged, type by type. Merging is how a prism combines streams (D20);
     * proportions are kept.
     *
     * @param other
     *            the quantity to add
     * @return the sum
     * @throws ArithmeticException
     *             if any type's amount would overflow
     */
    public Mana plus(Mana other) {
        int[] result = new int[TYPES];
        for (int i = 0; i < TYPES; i++) {
            result[i] = Math.addExact(amounts[i], other.amounts[i]);
        }
        return of(result);
    }

    /**
     * This quantity with {@code other} removed, type by type.
     *
     * @param other
     *            the quantity to remove; must be {@linkplain #contains contained} in this one
     * @return the difference
     * @throws IllegalArgumentException
     *             if {@code other} holds more of any type than this
     */
    public Mana minus(Mana other) {
        int[] result = new int[TYPES];
        for (int i = 0; i < TYPES; i++) {
            result[i] = checked(amounts[i] - other.amounts[i]);
        }
        return of(result);
    }

    /**
     * The smaller amount of each type: what can be taken from this toward {@code other}.
     *
     * @param other
     *            the wanted amounts
     * @return the per-type minimum
     */
    public Mana min(Mana other) {
        int[] result = new int[TYPES];
        for (int i = 0; i < TYPES; i++) {
            result[i] = Math.min(amounts[i], other.amounts[i]);
        }
        return of(result);
    }

    /**
     * Whether this holds at least as much of every type as {@code other}.
     *
     * @param other
     *            the amounts to compare with
     * @return true if {@code other} can be taken from this in full
     */
    public boolean contains(Mana other) {
        for (int i = 0; i < TYPES; i++) {
            if (amounts[i] < other.amounts[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Only the given types; every other type's amount is dropped from the result.
     *
     * @param types
     *            the types to keep
     * @return the restricted quantity
     */
    public Mana only(ManaType... types) {
        int[] result = new int[TYPES];
        for (ManaType type : types) {
            result[type.ordinal()] = amounts[type.ordinal()];
        }
        return of(result);
    }

    /**
     * At most {@code max} in total, keeping the proportions as closely as whole amounts allow. A
     * link moving this stream at a limited rate (D28) sends this much each tick.
     *
     * <p>
     * <b>How.</b> Exact proportional shares are usually fractions, and amounts are whole, so this
     * uses the largest-remainder method: each type first gets its share rounded down, then the
     * units lost to rounding go one each to the types with the largest fractional parts. The result
     * totals exactly {@code max}, and no type gets more than one unit away from its exact share.
     *
     * <p>
     * Example: Fire 7, Air 3, Water 1 (total 11) limited to 5. Exact shares are 35/11 = 3.18, 15/11
     * = 1.36 and 5/11 = 0.45. Rounded down they are 3, 1 and 0, which is one short; Water has the
     * largest remainder (0.45), so the result is Fire 3, Air 1, Water 1. This also means a small
     * share still gets a unit rather than never moving.
     *
     * @param max
     *            the most to return in total; 0 or less gives {@link #EMPTY}
     * @return this quantity if it is within {@code max}, otherwise a proportional part totalling
     *         exactly {@code max}
     */
    public Mana limit(int max) {
        if (exactTotal <= max) {
            return this;
        }
        if (max <= 0) {
            return EMPTY;
        }
        // Each type gets its floored share; the units lost to flooring go to the largest
        // remainders. amounts[i] * max fits a long (both are below 2^31), and so does every share.
        int[] result = new int[TYPES];
        long[] remainders = new long[TYPES];
        int given = 0;
        for (int i = 0; i < TYPES; i++) {
            long scaled = (long) amounts[i] * max;
            result[i] = (int) (scaled / exactTotal);
            remainders[i] = scaled % exactTotal;
            given += result[i];
        }
        // At most TYPES - 1 units are short, one per type, so each type is topped up at most once;
        // a topped-up remainder is set to -1 so it isn't picked again.
        for (; given < max; given++) {
            int best = 0;
            for (int i = 1; i < TYPES; i++) {
                if (remainders[i] > remainders[best]) {
                    best = i;
                }
            }
            result[best]++;
            remainders[best] = -1;
        }
        return of(result);
    }

    /**
     * The Raw part split into the six flavors, {@value #RAW_PER_SET} Raw to one of each (D27). Raw
     * that doesn't make a whole set stays Raw, so the total is unchanged. This is how a prism in
     * split mode turns Raw into flavors (D16, D26).
     *
     * <p>
     * Example: Raw 20 splits into 3 of each flavor (18 Raw) with Raw 2 left over.
     *
     * @return the split quantity; this one if there is less than one set of Raw
     * @throws ArithmeticException
     *             if a flavor's amount would overflow
     */
    public Mana splitRaw() {
        int sets = get(ManaType.RAW) / RAW_PER_SET;
        if (sets == 0) {
            return this;
        }
        int[] result = amounts.clone();
        result[ManaType.RAW.ordinal()] -= sets * RAW_PER_SET;
        for (ManaType flavor : ManaType.FLAVORS) {
            result[flavor.ordinal()] = Math.addExact(result[flavor.ordinal()], sets);
        }
        return of(result);
    }

    /**
     * Every whole set of one of each flavor recombined into Raw, {@value #RAW_PER_SET} Raw per set
     * (D27); the exact reverse of {@link #splitRaw}. Flavor amounts beyond the smallest flavor's
     * stay as they are, so the total is unchanged.
     *
     * @return the recombined quantity; this one if any flavor is missing
     * @throws ArithmeticException
     *             if the Raw amount would overflow
     */
    public Mana formRaw() {
        int sets = Integer.MAX_VALUE;
        for (ManaType flavor : ManaType.FLAVORS) {
            sets = Math.min(sets, get(flavor));
        }
        if (sets == 0) {
            return this;
        }
        int[] result = amounts.clone();
        for (ManaType flavor : ManaType.FLAVORS) {
            result[flavor.ordinal()] -= sets;
        }
        result[ManaType.RAW.ordinal()] = Math.addExact(
            result[ManaType.RAW.ordinal()], Math.multiplyExact(sets, RAW_PER_SET));
        return of(result);
    }

    /**
     * The fraction of this quantity that is the given type, from 0 to 1. Raw counts as an even mix
     * (D15), so it adds a sixth of itself to each flavor's share. For {@link ManaType#RAW} this is
     * just the Raw fraction. Components that need "a stream that is mostly Water" check this.
     *
     * <p>
     * Example: Water 4, Light 1 has a Water share of 0.8; Raw 6 has a Fire share of 1/6.
     *
     * @param type
     *            the type to measure
     * @return its share, 0 for an empty quantity
     */
    public double share(ManaType type) {
        if (exactTotal == 0) {
            return 0;
        }
        double amount = get(type);
        if (type != ManaType.RAW) {
            amount += get(ManaType.RAW) / (double) ManaType.FLAVORS.size();
        }
        return amount / exactTotal;
    }

    /** Decodes {@link #CODEC}'s map form. */
    private static Mana fromMap(Map<ManaType, Integer> map) {
        int[] amounts = new int[TYPES];
        map.forEach((type, amount) -> amounts[type.ordinal()] = amount);
        return of(amounts);
    }

    /** The non-zero amounts, in {@link ManaType} order: {@link #CODEC}'s map form. */
    private Map<ManaType, Integer> toMap() {
        Map<ManaType, Integer> map = new EnumMap<>(ManaType.class);
        for (int i = 0; i < TYPES; i++) {
            if (amounts[i] != 0) {
                map.put(ManaType.byIndex(i), amounts[i]);
            }
        }
        return map;
    }

    /** Equal when every type's amount is equal. */
    @Override
    public boolean equals(Object o) {
        return o instanceof Mana other && Arrays.equals(amounts, other.amounts);
    }

    /** Consistent with {@link #equals}. */
    @Override
    public int hashCode() {
        return Arrays.hashCode(amounts);
    }

    /** Lists the non-zero amounts, e.g. {@code Mana[water=4, light=1]}. */
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "Mana[", "]");
        toMap().forEach((type, amount) -> joiner.add(type.getSerializedName() + "=" + amount));
        return joiner.toString();
    }
}
