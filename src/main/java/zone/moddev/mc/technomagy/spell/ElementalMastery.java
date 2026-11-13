package zone.moddev.mc.technomagy.spell;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.AbilityPresentation;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.MasteryAxis;
import org.bitwisemadness.abilitycore.api.MasteryDeclarations;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.bitwisemadness.abilitycore.api.PresentationField;
import org.bitwisemadness.abilitycore.api.PresentationProviders;
import org.bitwisemadness.abilityhotbar.UnlockRule;
import org.bitwisemadness.abilityhotbar.compat.CoreMasteryCompat;
import org.bitwisemadness.abilityhotbar.registry.MenuTabs;

import zone.moddev.mc.technomagy.mana.ManaType;

/**
 * The four count menu tabs and the six elemental mastery entries (D82): organization only - tabs by
 * element count, an element tag per component element on every ability, and one non-castable entry
 * per element presenting its bucket's level. No balance lives here; the boosts the entries describe
 * are D80's aggregate math.
 */
final class ElementalMastery {

    /** The Single tab: exactly one element tag (the six entries today). */
    static final ResourceLocation SINGLE_TAB = ResourceLocation.fromNamespaceAndPath("technomagy",
        "single");

    /** The Duo tab: exactly two element tags (the fifteen pair spells). */
    static final ResourceLocation DUO_TAB = ResourceLocation.fromNamespaceAndPath("technomagy",
        "duo");

    /** The Trio tab: exactly three element tags (Growth today). */
    static final ResourceLocation TRIO_TAB = ResourceLocation.fromNamespaceAndPath("technomagy",
        "trio");

    /**
     * The Quadra tab: exactly four element tags. Registered with no members today - no spell draws
     * on four elements yet - so hotbar's empty-tab rule keeps it hidden until one arrives (D82).
     */
    static final ResourceLocation QUADRA_TAB = ResourceLocation.fromNamespaceAndPath("technomagy",
        "quadra");

    /** The meter label key (D81's presentation seam). */
    private static final String METER_LABEL = "technomagy.mastery.progress";

    /** The meter's value text key: the localised level inside the bar. */
    private static final String LEVEL_VALUE = "technomagy.mastery.level_value";

    /** The element chips' declared order under every tab (D82). */
    private static final List<String> ELEMENT_ORDER = ManaType.FLAVORS.stream()
        .map(ElementalMastery::tagName).toList();

    private ElementalMastery() {
    }

    /**
     * Declares the four count tabs with their alchemical category icons, element-chip order, their
     * learned-only live content (D83), the six entry presentation providers, each entry's
     * pane-display mastery spec (D85), and no snapshot listener of its own - the client reads
     * hotbar's shared snapshot store instead. Runs once at mod construction beside the D79/D80
     * declarations (D82, D85).
     */
    static void declare() {
        MenuTabs.register(SINGLE_TAB, "tab.technomagy.single", tabIcon("single"),
            null, (id, definition) -> elementTagCount(definition) == 1, content(1), false);
        MenuTabs.register(DUO_TAB, "tab.technomagy.duo", tabIcon("duo"),
            null, (id, definition) -> elementTagCount(definition) == 2, content(2), false);
        MenuTabs.register(TRIO_TAB, "tab.technomagy.trio", tabIcon("trio"),
            null, (id, definition) -> elementTagCount(definition) == 3, content(3), false);
        MenuTabs.register(QUADRA_TAB, "tab.technomagy.quadra", null,
            null, (id, definition) -> elementTagCount(definition) == 4, content(4), false);
        MenuTabs.subCategoryOrder(SINGLE_TAB, ELEMENT_ORDER);
        MenuTabs.subCategoryOrder(DUO_TAB, ELEMENT_ORDER);
        MenuTabs.subCategoryOrder(TRIO_TAB, ELEMENT_ORDER);
        MenuTabs.subCategoryOrder(QUADRA_TAB, ELEMENT_ORDER);
        for (ManaType element : ManaType.FLAVORS) {
            MenuTabs.subCategoryIcon(tagName(element), chipIcon(element), null);
            PresentationProviders.declare(id(element), ElementalMastery::present);
            // D85's display contract: the entry never casts, so these curves never apply -
            // they exist so the pane's Mastery bonuses section reads POWER and DURATION at the
            // element's own level (the ISS school-mastery pattern). No has() guard: a second
            // declaration must trip core's duplicate-owner error, never silently win.
            MasteryDeclarations.declare(id(element), MasterySpec.gained(0.0)
                .withEffect(MasteryAxis.POWER)
                .withEffect(MasteryAxis.DURATION));
        }
    }

    /**
     * One tab's alchemical category icon (D82): the philosopher's stone for Single, the amalgam for
     * Duo, the amalgam (alternate) for Trio, from symbols.com.
     *
     * @param name
     *            the tab's id path: {@code single}, {@code duo} or {@code trio}
     * @return the 16px texture id
     */
    private static ResourceLocation tabIcon(String name) {
        return ResourceLocation.fromNamespaceAndPath("technomagy",
            "textures/gui/tab/" + name + ".png");
    }

    /**
     * One tab's live content (D83): only PROVEN-learned abilities list, so a Duo or Trio spell
     * absent from its category until every element of its stream reaches the threshold. Unknown
     * lists - hotbar's never-guess rule holds on this side too, so a player whose snapshot or
     * threshold payload has not answered yet sees the full tab rather than a wrongly emptied one.
     *
     * @param elementCount
     *            the tab's element count: 1 (the entries and any future single-element ability), 2,
     *            or 3
     * @return the content handler hotbar queries per menu frame
     */
    private static MenuTabs.TabContent content(int elementCount) {
        return player -> {
            List<ResourceLocation> ids = new ArrayList<>();
            if (elementCount == 1) {
                for (ManaType element : ManaType.FLAVORS) {
                    ResourceLocation entry = id(element);
                    if (listed(player, entry)) {
                        ids.add(entry);
                    }
                }
            }
            for (PlayerSpell spell : PlayerSpell.values()) {
                if (spell.inputUnits().size() == elementCount && listed(player, spell.id())) {
                    ids.add(spell.id());
                }
            }
            return ids;
        };
    }

    /** Whether one id lists under a tab: everything except a proven-unlearned answer. */
    private static boolean listed(Player player, ResourceLocation abilityId) {
        return !UnlockRule.provenUnlearned(SpellUnlocks.learnedFor(player, abilityId));
    }

    /**
     * The mastery entry's ability id: {@code technomagy:<element>_mastery}.
     *
     * @param element
     *            one of the six personal-pool flavors
     * @return the entry's stable id
     */
    static ResourceLocation id(ManaType element) {
        return ResourceLocation.fromNamespaceAndPath("technomagy",
            tagName(element) + "_mastery");
    }

    /**
     * Whether an id names one of the six elemental mastery entries.
     *
     * @param abilityId
     *            the id being asked about
     * @return true only for the six entries' ids
     */
    static boolean isMastery(ResourceLocation abilityId) {
        for (ManaType element : ManaType.FLAVORS) {
            if (id(element).equals(abilityId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The element-tag vocabulary name for one flavor: its lowercase mana name ({@code fire},
     * {@code light}, ...), the same word the personal pool, the element tag and the
     * {@code <name>_mastery} entry id all derive from.
     *
     * @param element
     *            one of the six personal-pool flavors
     * @return the raw tag string
     */
    static String tagName(ManaType element) {
        return element.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The element tags for one spell: every component element of its stream (D82), so an ability
     * belongs to all of its parts' chips and classifies itself into a tab by count.
     *
     * @param spell
     *            the spell whose construction supplies the elements
     * @return the unmodifiable tag set
     */
    static Set<String> elementTags(PlayerSpell spell) {
        return spell.inputUnits().keySet().stream()
            .map(ElementalMastery::tagName)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * How many of a definition's tags are element tags (D82) - the tab membership count. Tags
     * outside the six-name vocabulary never count, so an unrelated tag cannot drag an ability into
     * the wrong tab.
     *
     * @param definition
     *            the registry entry being classified
     * @return 0..3
     */
    static int elementTagCount(AbilityDefinition definition) {
        int count = 0;
        for (String tag : definition.tags()) {
            if (ElementalMastery.isElementTag(tag)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isElementTag(String tag) {
        for (ManaType element : ManaType.FLAVORS) {
            if (tagName(element).equals(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The chip icon texture for one element (16px file; hotbar's chip draw resolves the full path
     * form).
     *
     * @param element
     *            one of the six personal-pool flavors
     * @return the texture id
     */
    static ResourceLocation chipIcon(ManaType element) {
        return ResourceLocation.fromNamespaceAndPath("technomagy",
            "textures/gui/element/" + tagName(element) + ".png");
    }

    /**
     * Builds one elemental mastery entry's definition (D82): non-castable by construction -
     * {@code NONE} activation declares no activation path at all, the empty cost list and
     * {@code static} resolver mean no pricing can ever run - carrying its one element tag and the
     * boost description.
     *
     * @param element
     *            the entry's element
     * @return the registry entry for {@code <element>_mastery}
     */
    static AbilityDefinition definition(ManaType element) {
        String name = tagName(element);
        return new AbilityDefinition(
            "mastery.technomagy." + name,
            entryIcon(element),
            ResourceLocation.fromNamespaceAndPath("technomagy", "mastery"),
            ActivationType.NONE,
            List.of(),
            "all",
            "static",
            Set.of(name),
            Map.of("description", "mastery.technomagy." + name + ".description"));
    }

    /**
     * The row icon texture for one element (D82): the element's alchemical symbol at 32 pixels -
     * the Single entry's icon and the source the spell-icon generator composes from.
     *
     * @param element
     *            one of the six personal-pool flavors
     * @return the texture id
     */
    static ResourceLocation entryIcon(ManaType element) {
        return ResourceLocation.fromNamespaceAndPath("technomagy",
            "textures/item/" + tagName(element) + "_mastery.png");
    }

    /**
     * The menu presentation for one entry (D82 through D81's seam): hotbar's stored copy of the
     * server's answer decides - the element's level text and in-level fill once it has answered, an
     * empty presentation before it - never a guessed zero. It is the same {@link CoreMasteryCompat}
     * read the spells' presentations use (fenced to the local player's exchange), so this class
     * keeps no client copy of its own. Toggle stays null because nothing here is castable.
     *
     * @param player
     *            the player whose menu is rendering (unused: the snapshot already names them)
     * @param abilityId
     *            the mastery entry being presented
     * @return the presentation; never null
     */
    static AbilityPresentation present(Player player, ResourceLocation abilityId) {
        if (!CoreMasteryCompat.answered()) {
            return new AbilityPresentation(null, List.of());
        }
        return presentation(abilityId, CoreMasteryCompat::levelOf, CoreMasteryCompat::progressOf);
    }

    /**
     * The pure half of {@link #present}: the id travels untransformed to the injected store readers
     * and their answers become the meter - no client state touched. The readers are parameters so
     * the GameTests can pin the exact id this method looks up: a transformed id (the old
     * pool-resource id, say) would silently answer level 0 and still build a meter.
     *
     * @param abilityId
     *            the entry id to look up, passed through unchanged
     * @param levelOf
     *            the store's own-target level reader
     * @param progressOf
     *            the store's in-level fill reader
     * @return the meter presentation; never null
     */
    static AbilityPresentation presentation(ResourceLocation abilityId,
        ToIntFunction<ResourceLocation> levelOf,
        ToDoubleFunction<ResourceLocation> progressOf) {
        int level = levelOf.applyAsInt(abilityId);
        double fill = progressOf.applyAsDouble(abilityId);
        PresentationField meter = PresentationField.progress(METER_LABEL, (float) fill,
            Component.translatable(LEVEL_VALUE, level).getString());
        return new AbilityPresentation(null, List.of(meter));
    }
}
