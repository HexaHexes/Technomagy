package com.mcmoddev.technomagy.mana;

/**
 * Something that stores, accepts or supplies mana: the mana counterpart of NeoForge's
 * {@code IEnergyStorage}. Get one from a block with {@link ManaCapabilities#BLOCK}.
 *
 * <p>A handler has no transfer rate of its own. Rate belongs to the link moving mana between two
 * handlers, which limits each transfer (see {@link Mana#limit}). A handler only decides which
 * types it accepts or supplies, and how much fits.
 */
public interface IManaHandler {
    /** Everything currently stored. */
    Mana getStored();

    /** The most mana, of all types together, this can hold. */
    int getCapacity();

    /**
     * Offers mana to this handler.
     *
     * @param offered  the mana offered
     * @param simulate if true, only report what would be accepted
     * @return the part of {@code offered} accepted; empty if none
     */
    Mana receive(Mana offered, boolean simulate);

    /**
     * Takes mana from this handler.
     *
     * @param wanted   the most of each type to take
     * @param simulate if true, only report what would be taken
     * @return what was taken, never more of a type than {@code wanted} asked for; empty if none
     */
    Mana extract(Mana wanted, boolean simulate);
}
