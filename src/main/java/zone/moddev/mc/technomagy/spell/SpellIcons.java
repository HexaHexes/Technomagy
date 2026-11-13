package zone.moddev.mc.technomagy.spell;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import net.neoforged.fml.loading.FMLEnvironment;

import org.bitwisemadness.abilitycore.api.IconProviders;

import zone.moddev.mc.technomagy.mana.ManaType;
import zone.moddev.mc.technomagy.spell.client.SpellIconGenerator;

/**
 * Declares the sixteen spells' icon providers (D82): the render-time generator answers each duo,
 * trio or quadra icon composed from its element symbols, so resource packs flow into the icon. This
 * side owns the classification - which spell, which glyphs, which layout count - while a dedicated
 * server abstains before touching any client class, and an unanswerable spell falls through to the
 * definition's static icon - core's documented resolution order.
 */
final class SpellIcons {

    private SpellIcons() {
    }

    /** Declares one provider per spell; runs once at mod construction beside the D82 seams. */
    static void declare() {
        for (PlayerSpell spell : PlayerSpell.values()) {
            IconProviders.declare(spell.id(), SpellIcons::iconFor);
        }
    }

    private static ResourceLocation iconFor(Player player, ResourceLocation abilityId) {
        if (!FMLEnvironment.dist.isClient()) {
            return null;
        }
        Optional<PlayerSpell> spell = PlayerSpell.find(abilityId);
        if (spell.isEmpty()) {
            return null;
        }
        List<ManaType> elements = List.copyOf(spell.get().inputUnits().keySet());
        if (elements.size() < 2 || elements.size() > 4) {
            return null; // a single-element spell wears its entry's own icon
        }
        List<ResourceLocation> glyphs = new ArrayList<>(elements.size());
        for (ManaType element : elements) {
            glyphs.add(ElementalMastery.entryIcon(element));
        }
        return SpellIconGenerator.compose(abilityId, glyphs);
    }
}
