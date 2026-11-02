package com.mcmoddev.technomagy.machine;

import com.mcmoddev.technomagy.Technomagy;
import com.mcmoddev.technomagy.mana.IManaHandler;
import com.mcmoddev.technomagy.mana.InfiniteManaSource;
import com.mcmoddev.technomagy.mana.ManaCapabilities;
import com.mcmoddev.technomagy.mana.ManaType;
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

/** The machine pieces that rituals are built from. */
public final class MachineContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Technomagy.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Technomagy.MODID);

    public static final DeferredBlock<NodeDirectorBlock> NODE_DIRECTOR = BLOCKS.register("node_director",
            () -> new NodeDirectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));
    public static final DeferredItem<BlockItem> NODE_DIRECTOR_ITEM = ITEMS.registerSimpleBlockItem(NODE_DIRECTOR);
    public static final DeferredBlock<PrismBlock> PRISM = BLOCKS.register("prism",
            () -> new PrismBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));
    public static final DeferredItem<BlockItem> PRISM_ITEM = ITEMS.registerSimpleBlockItem(PRISM);
    /** An endless supply of Raw mana, for testing mana links. Not obtainable in survival. */
    public static final DeferredBlock<Block> RAW_MANA_SOURCE = BLOCKS.registerSimpleBlock("raw_mana_source",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 15)
                    .emissiveRendering((state, level, pos) -> true));
    public static final DeferredItem<BlockItem> RAW_MANA_SOURCE_ITEM = ITEMS.registerSimpleBlockItem(RAW_MANA_SOURCE);

    private static final IManaHandler RAW_SUPPLY = new InfiniteManaSource(ManaType.RAW);

    private MachineContent() {
    }

    /** Called from the mod constructor. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(MachineContent::addToTabs);
        modBus.addListener(MachineContent::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(ManaCapabilities.BLOCK, (level, pos, state, blockEntity, side) -> RAW_SUPPLY,
                RAW_MANA_SOURCE.get());
    }

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(NODE_DIRECTOR_ITEM);
            event.accept(PRISM_ITEM);
            event.accept(RAW_MANA_SOURCE_ITEM);
        }
    }
}
