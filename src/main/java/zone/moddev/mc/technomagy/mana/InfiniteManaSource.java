package zone.moddev.mc.technomagy.mana;

/**
 * Supplies endless mana of one type and accepts nothing. Stateless, so one instance can serve every
 * block of a kind.
 */
public final class InfiniteManaSource implements IManaHandler {
    private final ManaType type;

    /**
     * Creates a source of one type.
     *
     * @param type
     *            the type it supplies
     */
    public InfiniteManaSource(ManaType type) {
        this.type = type;
    }

    /**
     * Reports an always-full store.
     *
     * @return {@link Integer#MAX_VALUE} of this source's type
     */
    @Override
    public Mana getStored() {
        return Mana.of(type, Integer.MAX_VALUE);
    }

    /**
     * Reports an unlimited capacity.
     *
     * @return {@link Integer#MAX_VALUE}
     */
    @Override
    public int getCapacity() {
        return Integer.MAX_VALUE;
    }

    /**
     * Accepts nothing.
     *
     * @return always {@link Mana#EMPTY}
     */
    @Override
    public Mana receive(Mana offered, boolean simulate) {
        return Mana.EMPTY;
    }

    /**
     * Supplies all of this source's type that was asked for, and none of any other type. The source
     * is never depleted, so {@code simulate} makes no difference.
     *
     * @return {@code wanted}, restricted to this source's type
     */
    @Override
    public Mana extract(Mana wanted, boolean simulate) {
        return wanted.only(type);
    }
}
