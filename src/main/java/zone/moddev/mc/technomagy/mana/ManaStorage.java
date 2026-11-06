package zone.moddev.mc.technomagy.mana;

import java.util.EnumSet;
import java.util.Set;

import com.mojang.logging.LogUtils;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import net.neoforged.neoforge.common.util.INBTSerializable;

import org.slf4j.Logger;

/**
 * A plain mana store with a total capacity, accepting only the types it is given (D24, D25). Block
 * entities hold one and expose it through {@link ManaCapabilities#BLOCK}; override
 * {@link #onChanged} to mark the block entity dirty.
 *
 * <p>
 * Capacity counts all types together. When an offer is more than fits, the accepted part keeps the
 * offer's proportions (see {@link Mana#limit}).
 *
 * <p>
 * Not thread-safe; use it from the thread that owns its block entity (the server thread).
 */
public class ManaStorage implements IManaHandler, INBTSerializable<Tag> {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The most mana, of all types together, this store holds. */
    protected final int capacity;

    /** The types this store accepts; offers of other types are refused. */
    protected final ManaType[] accepted;

    /** The current contents. */
    protected Mana stored = Mana.EMPTY;

    /**
     * A store accepting every type.
     *
     * @param capacity
     *            the most mana it holds, at least 0
     * @throws IllegalArgumentException
     *             if {@code capacity} is negative
     */
    public ManaStorage(int capacity) {
        this(capacity, EnumSet.allOf(ManaType.class));
    }

    /**
     * A store accepting only some types, such as a component that needs one flavor.
     *
     * @param capacity
     *            the most mana it holds, at least 0
     * @param accepted
     *            the types it accepts
     * @throws IllegalArgumentException
     *             if {@code capacity} is negative
     */
    public ManaStorage(int capacity, Set<ManaType> accepted) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Mana capacity cannot be negative: " + capacity);
        }
        this.capacity = capacity;
        this.accepted = accepted.toArray(ManaType[]::new);
    }

    /** {@inheritDoc} */
    @Override
    public Mana getStored() {
        return stored;
    }

    /** {@inheritDoc} */
    @Override
    public int getCapacity() {
        return capacity;
    }

    /**
     * Accepts the offered types this store takes, up to its free space, keeping the offer's
     * proportions when it doesn't all fit.
     *
     * @param offered
     *            the mana offered
     * @param simulate
     *            if true, only report what would be accepted
     * @return the part accepted
     */
    @Override
    public Mana receive(Mana offered, boolean simulate) {
        Mana accepting = offered.only(accepted).limit(capacity - stored.total());
        if (!simulate && !accepting.isEmpty()) {
            stored = stored.plus(accepting);
            onChanged();
        }
        return accepting;
    }

    /**
     * Takes as much of each wanted type as is stored.
     *
     * @param wanted
     *            the most of each type to take
     * @param simulate
     *            if true, only report what would be taken
     * @return what was taken
     */
    @Override
    public Mana extract(Mana wanted, boolean simulate) {
        Mana taking = stored.min(wanted);
        if (!simulate && !taking.isEmpty()) {
            stored = stored.minus(taking);
            onChanged();
        }
        return taking;
    }

    /**
     * Replaces the contents, for loading or client syncing. Does not check capacity or accepted
     * types, and does not call {@link #onChanged}.
     *
     * @param mana
     *            the new contents
     */
    public void setStored(Mana mana) {
        stored = mana;
    }

    /**
     * Called after the stored mana changes through {@link #receive} or {@link #extract}, but not
     * when simulating. Does nothing by default; block entities override it to mark themselves
     * changed.
     */
    protected void onChanged() {
    }

    /**
     * Saves the contents with {@link Mana#CODEC}.
     *
     * @param provider
     *            registry access; unused
     * @return the encoded contents
     */
    @Override
    public Tag serializeNBT(HolderLookup.Provider provider) {
        return Mana.CODEC.encodeStart(NbtOps.INSTANCE, stored).getOrThrow();
    }

    /**
     * Loads contents saved by {@link #serializeNBT}.
     *
     * <p>
     * Saved data that doesn't decode (an unknown type name, a negative amount, the wrong tag type)
     * is reported as an error naming the data and the decoding problem, and the store is reset to
     * {@link Mana#EMPTY}. Resetting fixes the save: the next save writes the empty contents. It
     * does not stop the world loading, since one corrupt store shouldn't make a whole world
     * unplayable.
     *
     * @param provider
     *            registry access; unused
     * @param nbt
     *            the saved contents
     */
    @Override
    public void deserializeNBT(HolderLookup.Provider provider, Tag nbt) {
        var decoded = Mana.CODEC.parse(NbtOps.INSTANCE, nbt);
        decoded.ifError(
            error -> LOGGER.error(
                "Unreadable stored mana {}: {}. Resetting this store to empty.",
                nbt,
                error.message()));
        stored = decoded.result().orElse(Mana.EMPTY);
    }
}
