package zone.moddev.mc.technomagy.spell;

import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.bitwisemadness.abilitycore.api.AbilityUnlocks;
import org.bitwisemadness.abilitycore.api.MasteryTarget;
import org.bitwisemadness.abilitycore.mastery.MasteryAttachments;
import org.bitwisemadness.abilitycore.mastery.MasteryLevels;

import zone.moddev.mc.technomagy.mana.ManaType;

/**
 * The unlock verdicts for all twenty-two abilities (D83): Single (the six entries and any
 * one-element ability) is learned from the start; Duo needs every component element's mastery at
 * {@code mastery.unlockDuoLevel}; Trio at {@code mastery.unlockTrioLevel}. The server reads the
 * element buckets from the player's attachments, the client reads D82's answered snapshot plus the
 * threshold payload, and both abstain to unknown rather than guess - hotbar hides and refuses only
 * a proven {@code false} (ability-hotbar#135).
 */
final class SpellUnlocks {

    private SpellUnlocks() {
    }

    /**
     * Declares the unlock provider for every spell and every elemental mastery entry, once at mod
     * construction beside the other D82/D83 declarations (D83).
     */
    static void declare() {
        for (PlayerSpell spell : PlayerSpell.values()) {
            AbilityUnlocks.declare(spell.id(), SpellUnlocks::learnedFor);
        }
        for (ManaType element : ManaType.FLAVORS) {
            AbilityUnlocks.declare(ElementalMastery.id(element), SpellUnlocks::learnedFor);
        }
    }

    /**
     * Whether one player has one ability (D83). The six entries and single-element abilities are
     * always learned; a multi-element ability needs every element of its stream at the tab's
     * threshold. Unknown answers: a foreign id, the client before its threshold payload, or the
     * client before the answered snapshot.
     *
     * @param player
     *            the asking player (null = test context: the client path answers unknown)
     * @param abilityId
     *            the ability being asked about
     * @return true / false / empty for unknown, per core's unlock contract
     */
    static Optional<Boolean> learnedFor(Player player, ResourceLocation abilityId) {
        if (ElementalMastery.isMastery(abilityId)) {
            return Optional.of(true);
        }
        Optional<PlayerSpell> spell = PlayerSpell.find(abilityId);
        if (spell.isEmpty()) {
            return Optional.empty();
        }
        Set<ManaType> elements = spell.get().inputUnits().keySet();
        if (elements.size() <= 1) {
            return Optional.of(true);
        }
        boolean trioGate = elements.size() >= 3;
        int needed = threshold(player, trioGate);
        if (needed < 0) {
            return Optional.empty();
        }
        for (ManaType element : elements) {
            Optional<Integer> level = level(player, element);
            if (level.isEmpty()) {
                return Optional.empty();
            }
            if (level.get() < needed) {
                return Optional.of(false);
            }
        }
        return Optional.of(true);
    }

    /**
     * The threshold for one gate: server config on the server, the payload's store on the client.
     */
    private static int threshold(Player player, boolean trioGate) {
        if (player instanceof ServerPlayer) {
            return (trioGate ? SpellConfig.UNLOCK_TRIO_LEVEL : SpellConfig.UNLOCK_DUO_LEVEL).get();
        }
        return UnlockThresholdsPayload.threshold(trioGate);
    }

    /**
     * One element bucket's level for one player: the stored XP through the shipped curve on the
     * server, D82's answered snapshot on the client (empty before the answer).
     *
     * @param player
     *            the asking player (null = test context: unknown)
     * @param element
     *            the element whose mastery is read
     * @return the level, or empty when it cannot be answered honestly
     */
    private static Optional<Integer> level(Player player, ManaType element) {
        if (player instanceof ServerPlayer server) {
            double xp = MasteryAttachments.data(server)
                .xp(MasteryTarget.subcategory(PlayerManaPools.currency(element)).key());
            return Optional.of(MasteryLevels.level(xp));
        }
        return ElementalMastery.clientLevel(element);
    }

    /**
     * Sends the server's thresholds to the joining player (D83): one payload per login, before the
     * menu can ask; a config reload reaches clients on their next join (initial).
     *
     * @param event
     *            the login event
     */
    public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new UnlockThresholdsPayload(
            SpellConfig.UNLOCK_DUO_LEVEL.get(), SpellConfig.UNLOCK_TRIO_LEVEL.get()));
    }
}
