package com.mcmoddev.technomagy.mana;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.EnumSet;
import java.util.Set;

/**
 * A plain mana store with a total capacity, accepting only the types it is given. Block entities
 * hold one and expose it through {@link ManaCapabilities#BLOCK}; override {@link #onChanged} to
 * mark the block entity dirty.
 */
public class ManaStorage implements IManaHandler, INBTSerializable<Tag> {
    protected final int capacity;
    protected final ManaType[] accepted;
    protected Mana stored = Mana.EMPTY;

    /** A store accepting every type. */
    public ManaStorage(int capacity) {
        this(capacity, EnumSet.allOf(ManaType.class));
    }

    public ManaStorage(int capacity, Set<ManaType> accepted) {
        this.capacity = capacity;
        this.accepted = accepted.toArray(ManaType[]::new);
    }

    @Override
    public Mana getStored() {
        return stored;
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public Mana receive(Mana offered, boolean simulate) {
        Mana accepting = offered.only(accepted).limit(capacity - stored.total());
        if (!simulate && !accepting.isEmpty()) {
            stored = stored.plus(accepting);
            onChanged();
        }
        return accepting;
    }

    @Override
    public Mana extract(Mana wanted, boolean simulate) {
        Mana taking = stored.min(wanted);
        if (!simulate && !taking.isEmpty()) {
            stored = stored.minus(taking);
            onChanged();
        }
        return taking;
    }

    /** Replaces the contents, for loading or syncing; does not check capacity. */
    public void setStored(Mana mana) {
        stored = mana;
    }

    /** Called after the stored mana changes through {@link #receive} or {@link #extract}. */
    protected void onChanged() {
    }

    @Override
    public Tag serializeNBT(HolderLookup.Provider provider) {
        return Mana.CODEC.encodeStart(NbtOps.INSTANCE, stored).getOrThrow();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, Tag nbt) {
        stored = Mana.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElse(Mana.EMPTY);
    }
}
