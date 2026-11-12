package zone.moddev.mc.technomagy.spell;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.common.NeoForge;

import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.AbilityPresentation;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.PresentationField;
import org.bitwisemadness.abilitycore.api.PresentationProviders;
import org.bitwisemadness.abilitycore.sync.MasteryQueryAnsweredEvent;
import org.bitwisemadness.abilitycore.sync.MasteryQueryResponse;
import org.bitwisemadness.abilityhotbar.registry.MenuTabs;

import zone.moddev.mc.technomagy.mana.ManaType;

/**
 * The three menu tabs and the six elemental mastery entries (D82): organization only - tabs by
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

    /** The meter label key (D81's presentation seam). */
    private static final String METER_LABEL = "technomagy.mastery.progress";

    /** The meter's value text key: the localised level inside the bar. */
    private static final String LEVEL_VALUE = "technomagy.mastery.level_value";

    /** The element chips' declared order under every tab (D82). */
    private static final List<String> ELEMENT_ORDER = ManaType.FLAVORS.stream()
        .map(ElementalMastery::tagName).toList();

    /**
     * The answered snapshot's element buckets (D82): client-side only, written when core posts the
     * answer and read by the menu's render, so it stays on the client thread like hotbar's own
     * snapshot store.
     */
    private static boolean answered;

    private static final Map<ManaType, MasteryQueryResponse.Entry> buckets = new EnumMap<>(
        ManaType.class);

    private ElementalMastery() {
    }

    /**
     * Declares the three tabs with their element-chip order and icons, the six entry presentation
     * providers, and the snapshot listener. Runs once at mod construction beside the D79/D80
     * declarations (D82).
     */
    static void declare() {
        MenuTabs.register(SINGLE_TAB, "tab.technomagy.single",
            (id, definition) -> elementTagCount(definition) == 1);
        MenuTabs.register(DUO_TAB, "tab.technomagy.duo",
            (id, definition) -> elementTagCount(definition) == 2);
        MenuTabs.register(TRIO_TAB, "tab.technomagy.trio",
            (id, definition) -> elementTagCount(definition) == 3);
        MenuTabs.subCategoryOrder(SINGLE_TAB, ELEMENT_ORDER);
        MenuTabs.subCategoryOrder(DUO_TAB, ELEMENT_ORDER);
        MenuTabs.subCategoryOrder(TRIO_TAB, ELEMENT_ORDER);
        for (ManaType element : ManaType.FLAVORS) {
            MenuTabs.subCategoryIcon(tagName(element), chipIcon(element), null);
            PresentationProviders.declare(id(element), ElementalMastery::present);
        }
        NeoForge.EVENT_BUS.addListener(ElementalMastery::onAnswered);
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
     * {@code light}, ...), the same word the personal pool and the D80 bucket use.
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
     * {@code UNKNOWN} activation is refused fail-closed, the empty cost list and {@code static}
     * resolver mean no pricing can ever run - carrying its one element tag and the boost
     * description.
     *
     * @param element
     *            the entry's element
     * @return the registry entry for {@code <element>_mastery}
     */
    static AbilityDefinition definition(ManaType element) {
        String name = tagName(element);
        return new AbilityDefinition(
            "mastery.technomagy." + name,
            ResourceLocation.fromNamespaceAndPath("technomagy",
                "textures/item/" + name + "_mastery.png"),
            ResourceLocation.fromNamespaceAndPath("technomagy", "mastery"),
            ActivationType.UNKNOWN,
            List.of(),
            "all",
            "static",
            Set.of(name),
            Map.of("description", "mastery.technomagy." + name + ".description"));
    }

    /**
     * Client: keeps the element-bucket entries of core's answered mastery snapshot (D82) - the
     * public hotbar reads expose only {@code ABILITY} entries, so the six meters and the client
     * unlock verdicts read this store instead. Before the first answer nothing is stored, and both
     * consumers abstain rather than guess.
     *
     * @param event
     *            the answered response, posted on the client
     */
    static void onAnswered(MasteryQueryAnsweredEvent event) {
        buckets.clear();
        for (MasteryQueryResponse.Entry entry : event.response().entries()) {
            if (!"SUBCATEGORY".equals(entry.kind())) {
                continue;
            }
            for (ManaType element : ManaType.FLAVORS) {
                if (PlayerManaPools.currency(element).toString().equals(entry.id())) {
                    buckets.put(element, entry);
                }
            }
        }
        answered = true;
    }

    /**
     * The client's level for one element bucket, or empty before the snapshot answers (D82's
     * never-a-guessed-zero rule; an answered snapshot without the bucket honestly reads level 0 -
     * the element has simply never earned).
     *
     * @param element
     *            the element being asked about
     * @return the stored level, or empty when nothing has answered yet
     */
    static Optional<Integer> clientLevel(ManaType element) {
        if (!answered) {
            return Optional.empty();
        }
        MasteryQueryResponse.Entry entry = buckets.get(element);
        return Optional.of(entry == null ? 0 : entry.level());
    }

    /**
     * The menu presentation for one entry (D82 through D81's seam): the element's level text and
     * in-level fill once the snapshot has answered, an empty presentation before it - never a
     * guessed zero. Toggle stays null because nothing here is castable.
     *
     * @param player
     *            the player whose menu is rendering (unused: the store already names them)
     * @param abilityId
     *            the mastery entry being presented
     * @return the presentation; never null
     */
    static AbilityPresentation present(Player player, ResourceLocation abilityId) {
        if (!answered) {
            return new AbilityPresentation(null, List.of());
        }
        ManaType element = elementOf(abilityId);
        if (element == null) {
            return new AbilityPresentation(null, List.of());
        }
        MasteryQueryResponse.Entry entry = buckets.get(element);
        int level = entry == null ? 0 : entry.level();
        double fill = entry == null ? 0.0 : entry.progress();
        PresentationField meter = PresentationField.progress(METER_LABEL, (float) fill,
            Component.translatable(LEVEL_VALUE, level).getString());
        return new AbilityPresentation(null, List.of(meter));
    }

    private static ManaType elementOf(ResourceLocation abilityId) {
        for (ManaType element : ManaType.FLAVORS) {
            if (id(element).equals(abilityId)) {
                return element;
            }
        }
        return null;
    }
}
