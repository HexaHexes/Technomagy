package zone.moddev.mc.technomagy.spell;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.ChargingModel;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;

/** Registers the approved focus, temporary blocks, and Ability Core provider (D69-D70). */
public final class SpellContent {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM,
        "technomagy");
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK,
        "technomagy");

    /** Non-stackable casting device; Ability Hotbar owns spell selection (D74). */
    public static final DeferredHolder<Item, Item> FOCUS = ITEMS.register(
        "spell_focus", () -> new SpellFocusItem(new Item.Properties().stacksTo(1)));

    static final DeferredHolder<Block, Block> EARTH = BLOCKS.register(
        "temporary_earth",
        () -> new TemporarySpellBlock(
            BlockBehaviour.Properties.of().strength(0.5F).noLootTable(),
            false));
    static final DeferredHolder<Block, Block> LIGHT = BLOCKS.register(
        "spell_light",
        () -> new TemporarySpellBlock(
            BlockBehaviour.Properties.of()
                .replaceable()
                .noCollission()
                .noOcclusion()
                .noLootTable()
                .lightLevel(
                    state -> state.getValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL)),
            true));
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister
        .create(Registries.MOB_EFFECT, "technomagy");
    static final DeferredHolder<MobEffect, MobEffect> SHROUD = EFFECTS.register(
        "shroud", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x303040) {
        });
    static final DeferredHolder<MobEffect, MobEffect> LEACH = EFFECTS.register("leach",
        LeachEffect::new);
    private static final SpellRuntime RUNTIME = new SpellRuntime();

    private SpellContent() {
    }

    /**
     * Wires registries, server configuration, selection networking, and runtime lifecycle.
     *
     * @param bus
     *            mod event bus
     * @param container
     *            owning mod container
     */
    public static void register(IEventBus bus, ModContainer container) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        EFFECTS.register(bus);
        bus.addListener(SpellContent::abilities);
        bus.addListener(SpellContent::setup);
        bus.addListener(SpellContent::payloads);
        bus.addListener(SpellContent::creative);
        container.registerConfig(
            ModConfig.Type.SERVER, SpellConfig.SPEC, "technomagy-spells-server.toml");
        NeoForge.EVENT_BUS.register(RUNTIME);
        NeoForge.EVENT_BUS.addListener(PlayerManaPools::joined);
    }

    /**
     * Registers all sixteen explicit abilities, keeping the heuristic utility distinction intact.
     */
    private static void abilities(RegisterEvent event) {
        if (!event.getRegistryKey().equals(AbilityRegistries.ABILITY_REGISTRY_KEY)) {
            return;
        }
        event.register(
            AbilityRegistries.ABILITY_REGISTRY_KEY,
            helper -> {
                for (PlayerSpell spell : PlayerSpell.values()) {
                    helper.register(
                        spell.id(),
                        new AbilityDefinition(
                            "spell.technomagy." + spell.id().getPath(),
                            ResourceLocation.fromNamespaceAndPath(
                                "minecraft", "textures/item/blaze_rod.png"),
                            ResourceLocation.fromNamespaceAndPath(
                                "technomagy", "spells"),
                            ActivationType.INSTANT,
                            List.of(),
                            "all",
                            "static",
                            Set.of("spell"),
                            Map.of()));
                }
            });
    }

    /**
     * Publishes native handlers after construction; the reserve/charging integration is deferred.
     */
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(
            () -> {
                for (PlayerSpell spell : PlayerSpell.values()) {
                    AbilityCore.HANDLERS.register(
                        spell.id(),
                        (request, player) -> RUNTIME.cast(spell, request, player),
                        ChargingModel.NATIVE);
                }
            });
    }

    /** Registers selection intent without transmitting trusted targets or effect parameters. */
    private static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar("technomagy")
            .versioned("1")
            .playToServer(ElementSelectionPayload.TYPE, ElementSelectionPayload.CODEC,
                ElementSelectionPayload::handle);
    }

    /** Makes the focus available for runtime testing and ordinary creative play. */
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(FOCUS.get());
        }
    }

    /** Runs the selected spell through Ability Core, rather than bypassing its eligibility path. */
    static void castSelected(ServerPlayer player, InteractionHand hand) {
        if (!player.getItemInHand(hand).is(FOCUS.get())) {
            return;
        }
        var bar = player.getData(BarStateAttachments.BAR_STATE);
        var assignment = bar.assignment(bar.selectedSlot());
        var selected = assignment == null || assignment.abilityId() == null
            ? java.util.Optional.<PlayerSpell>empty()
            : PlayerSpell.find(assignment.abilityId());
        if (selected.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("message.technomagy.select_spell"), true);
            return;
        }
        var request = ActivationRequest.of(selected.orElseThrow().id(),
            ActivationSource.NATIVE_ADAPTER);
        var result = AbilityCore.PIPELINE.run(request, player);
        if (result.isRefusal()) {
            player.displayClientMessage(
                Component.translatable("message.technomagy.invalid_target"), true);
        }
    }
}
