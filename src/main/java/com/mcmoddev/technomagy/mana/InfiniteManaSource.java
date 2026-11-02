package com.mcmoddev.technomagy.mana;

/**
 * Supplies endless mana of one type and accepts nothing. Stateless, so one instance can serve
 * every block of a kind.
 */
public final class InfiniteManaSource implements IManaHandler {
    private final ManaType type;

    public InfiniteManaSource(ManaType type) {
        this.type = type;
    }

    @Override
    public Mana getStored() {
        return Mana.of(type, Integer.MAX_VALUE);
    }

    @Override
    public int getCapacity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Mana receive(Mana offered, boolean simulate) {
        return Mana.EMPTY;
    }

    @Override
    public Mana extract(Mana wanted, boolean simulate) {
        return wanted.only(type);
    }
}
