package zone.moddev.mc.technomagy.machine;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import zone.moddev.mc.technomagy.Technomagy;
import zone.moddev.mc.technomagy.mana.IManaHandler;
import zone.moddev.mc.technomagy.mana.InfiniteManaSource;
import zone.moddev.mc.technomagy.mana.ManaCapabilities;
import zone.moddev.mc.technomagy.mana.ManaType;

/**
 * Registers the machine pieces that rituals are built from (D30), their items and creative tab
 * entries, and their mana capabilities.
 *
 * <p>
 * Unlike the crystals and stones, machine pieces are ordinary blocks, not MMDLib materials: there
 * is one of each, with no per-flavor variants.
 *
 * <p>
 * Not thread-safe: {@link #register} runs once on the mod-loading thread.
 */
public final class MachineContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister
        .createBlocks(Technomagy.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister
        .createItems(Technomagy.MODID);

    /** The node director, which directs mana streams (D21). */
    public static final DeferredBlock<NodeDirectorBlock> NODE_DIRECTOR = BLOCKS.register(
        "node_director",
        () -> new NodeDirectorBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(1.5f, 6.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE)));

    /** The node director's item. */
    public static final DeferredItem<BlockItem> NODE_DIRECTOR_ITEM = ITEMS
        .registerSimpleBlockItem(NODE_DIRECTOR);

    /** The prism, which splits or combines mana (D20). */
    public static final DeferredBlock<PrismBlock> PRISM = BLOCKS.register(
        "prism",
        () -> new PrismBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(1.5f, 6.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE)));

    /** The prism's item. */
    public static final DeferredItem<BlockItem> PRISM_ITEM = ITEMS.registerSimpleBlockItem(PRISM);

    /**
     * An endless supply of Raw mana, for testing mana links. It has no recipe, so it is only
     * available in creative mode. It always renders fully lit and gives off light level 15.
     */
    public static final DeferredBlock<Block> RAW_MANA_SOURCE = BLOCKS.registerSimpleBlock(
        "raw_mana_source",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_BLUE)
            .strength(1.5f, 6.0f)
            .sound(SoundType.AMETHYST)
            .lightLevel(state -> 15)
            .emissiveRendering((state, level, pos) -> true));

    /** The raw mana source's item. */
    public static final DeferredItem<BlockItem> RAW_MANA_SOURCE_ITEM = ITEMS
        .registerSimpleBlockItem(RAW_MANA_SOURCE);

    /** The raw mana source's handler. Stateless, so one instance serves every source block. */
    private static final IManaHandler RAW_SUPPLY = new InfiniteManaSource(ManaType.RAW);

    private MachineContent() {
    }

    /**
     * Registers the blocks, items, creative tab entries and capabilities. Called once from the mod
     * constructor.
     *
     * @param modBus
     *            the mod event bus
     */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(MachineContent::addToTabs);
        modBus.addListener(MachineContent::registerCapabilities);
    }

    /** Gives the raw mana source its mana capability (D24); it needs no block entity for it. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(
            ManaCapabilities.BLOCK,
            (level, pos, state, blockEntity, side) -> RAW_SUPPLY,
            RAW_MANA_SOURCE.get());
    }

    /** Lists the machine pieces in the vanilla Functional Blocks tab. */
    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(NODE_DIRECTOR_ITEM);
            event.accept(PRISM_ITEM);
            event.accept(RAW_MANA_SOURCE_ITEM);
        }
    }
}
