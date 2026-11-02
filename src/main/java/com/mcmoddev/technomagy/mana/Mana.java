package com.mcmoddev.technomagy.mana;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * An immutable quantity of mana: an amount of each {@link ManaType}. A combined stream keeps its
 * proportions, so this records exactly which types a stream holds and how much of each.
 *
 * <p>Every operation is lossless: amounts move between quantities or between Raw and the
 * flavors, but are never rounded away.
 */
public final class Mana {
    /**
     * The Raw mana that splits into one of each flavor, and that one of each flavor recombines
     * into. Six keeps the total amount the same both ways.
     */
    public static final int RAW_PER_SET = 6;

    private static final int TYPES = ManaType.values().length;
    public static final Mana EMPTY = new Mana(new int[TYPES]);

    public static final Codec<Mana> CODEC = Codec.unboundedMap(ManaType.CODEC, Codec.intRange(0, Integer.MAX_VALUE))
            .xmap(Mana::fromMap, Mana::toMap);
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

    private final int[] amounts;
    private final int total;

    private Mana(int[] amounts) {
        this.amounts = amounts;
        long sum = 0;
        for (int amount : amounts) {
            sum += amount;
        }
        this.total = (int) Math.min(sum, Integer.MAX_VALUE);
    }

    public static Mana of(ManaType type, int amount) {
        int[] amounts = new int[TYPES];
        amounts[type.ordinal()] = checked(amount);
        return of(amounts);
    }

    public static Mana raw(int amount) {
        return of(ManaType.RAW, amount);
    }

    private static Mana of(int[] amounts) {
        for (int amount : amounts) {
            if (amount != 0) {
                return new Mana(amounts);
            }
        }
        return EMPTY;
    }

    private static int checked(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Mana amounts cannot be negative: " + amount);
        }
        return amount;
    }

    public int get(ManaType type) {
        return amounts[type.ordinal()];
    }

    public int total() {
        return total;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    /** This quantity with one type's amount replaced. */
    public Mana with(ManaType type, int amount) {
        int[] result = amounts.clone();
        result[type.ordinal()] = checked(amount);
        return of(result);
    }

    /** Both quantities merged. Merging is how a prism combines streams; proportions are kept. */
    public Mana plus(Mana other) {
        int[] result = new int[TYPES];
        for (int i = 0; i < TYPES; i++) {
            result[i] = Math.addExact(amounts[i], other.amounts[i]);
        }
        return of(result);
    }

    /** This quantity with {@code other} removed; {@code other} must be {@linkplain #contains contained}. */
    public Mana minus(Mana other) {
        int[] result = new int[TYPES];
        for (int i = 0; i < TYPES; i++) {
            result[i] = checked(amounts[i] - other.amounts[i]);
        }
        return of(result);
    }

    /** The smaller amount of each type: what can be taken from this toward {@code other}. */
    public Mana min(Mana other) {
        int[] result = new int[TYPES];
        for (int i = 0; i < TYPES; i++) {
            result[i] = Math.min(amounts[i], other.amounts[i]);
        }
        return of(result);
    }

    /** Whether this holds at least as much of every type as {@code other}. */
    public boolean contains(Mana other) {
        for (int i = 0; i < TYPES; i++) {
            if (amounts[i] < other.amounts[i]) {
                return false;
            }
        }
        return true;
    }

    /** Only the given types; everything else dropped. */
    public Mana only(ManaType... types) {
        int[] result = new int[TYPES];
        for (ManaType type : types) {
            result[type.ordinal()] = amounts[type.ordinal()];
        }
        return of(result);
    }

    /**
     * At most {@code max} in total, keeping the proportions as closely as whole amounts allow. A
     * link moving this stream at a limited rate sends this much each tick.
     */
    public Mana limit(int max) {
        if (total <= max) {
            return this;
        }
        if (max <= 0) {
            return EMPTY;
        }
        // Each type gets its floored share; the units lost to flooring go to the largest remainders.
        int[] result = new int[TYPES];
        long[] remainders = new long[TYPES];
        int given = 0;
        for (int i = 0; i < TYPES; i++) {
            long scaled = (long) amounts[i] * max;
            result[i] = (int) (scaled / total);
            remainders[i] = scaled % total;
            given += result[i];
        }
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
     * The Raw part split into the six flavors, {@link #RAW_PER_SET} Raw to one of each. Raw that
     * doesn't make a whole set stays Raw.
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

    /** Every whole set of one of each flavor recombined into Raw; the reverse of {@link #splitRaw}. */
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
        result[ManaType.RAW.ordinal()] = Math.addExact(result[ManaType.RAW.ordinal()], Math.multiplyExact(sets, RAW_PER_SET));
        return of(result);
    }

    /**
     * The fraction of this quantity that is the given type, from 0 to 1. Raw counts as an even
     * mix, so it adds a sixth of itself to each flavor's share. For {@link ManaType#RAW} this is
     * just the Raw fraction.
     */
    public double share(ManaType type) {
        if (total == 0) {
            return 0;
        }
        double amount = get(type);
        if (type != ManaType.RAW) {
            amount += get(ManaType.RAW) / (double) ManaType.FLAVORS.length;
        }
        return amount / total;
    }

    private static Mana fromMap(Map<ManaType, Integer> map) {
        int[] amounts = new int[TYPES];
        map.forEach((type, amount) -> amounts[type.ordinal()] = amount);
        return of(amounts);
    }

    private Map<ManaType, Integer> toMap() {
        Map<ManaType, Integer> map = new EnumMap<>(ManaType.class);
        for (int i = 0; i < TYPES; i++) {
            if (amounts[i] != 0) {
                map.put(ManaType.byIndex(i), amounts[i]);
            }
        }
        return map;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Mana other && Arrays.equals(amounts, other.amounts);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(amounts);
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "Mana[", "]");
        toMap().forEach((type, amount) -> joiner.add(type.getSerializedName() + "=" + amount));
        return joiner.toString();
    }
}
