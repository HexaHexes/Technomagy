package com.mcmoddev.technomagy.crystal;

import com.google.common.base.Suppliers;
import com.mcmoddev.technomagy.Technomagy;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.api.BlockContext;
import zone.moddev.mc.lib.api.BlockImplementation;
import zone.moddev.mc.lib.api.ItemContext;
import zone.moddev.mc.lib.api.ItemImplementation;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.factories.BlockEntityFactory;
import zone.moddev.mc.lib.common.runtime.RuntimeContentRegistrar;
import zone.moddev.mc.lib.common.types.MMDBlock;
import zone.moddev.mc.lib.common.types.MMDBlockEntity;
import zone.moddev.mc.lib.common.types.MMDItem;
import zone.moddev.mc.lib.common.types.MMDMaterial;

import java.util.function.Supplier;

import static zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES;

/**
 * Wires the crystal forms into MMDLib.
 *
 * <p>The forms are data modules under {@code mmdlib/item} and {@code mmdlib/block}, and each
 * flavor is a material under {@code mmdlib/material} selecting them. This class supplies the Java
 * behind the modules' {@code factory} IDs, then queues the loaded materials on this mod's own
 * {@link RuntimeContentRegistrar}, which creates the items, blocks and block entities.
 */
public final class CrystalContent {
    public static final ResourceLocation CRYSTAL = id("crystal");
    public static final ResourceLocation CRYSTAL_BLOCK = id("crystal_block");
    public static final ResourceLocation BUDDING_CRYSTAL = id("budding_crystal");
    public static final ResourceLocation CRYSTAL_CLUSTER = id("crystal_cluster");

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Technomagy.MODID);

    private static RuntimeContentRegistrar registrar;

    private CrystalContent() {
    }

    /** Called from the mod constructor, before MMDLib decodes any materials. */
    public static void register(IEventBus modBus) {
        var api = MaterialExtensions.GLOBAL;
        api.registerItemImplementation(CRYSTAL, new ItemImplementation<CrystalItem>() {
            @Override
            public Class<CrystalItem> itemClass() {
                return CrystalItem.class;
            }

            @Override
            public CrystalItem create(MMDMaterial material, MMDItem definition) {
                throw new IllegalStateException("needs a context");
            }

            @Override
            public CrystalItem create(ItemContext context) {
                return new CrystalItem(context.properties(), context.material());
            }
        });
        api.registerBlockImplementation(CRYSTAL_BLOCK, new BlockImplementation<AmethystBlock>() {
            @Override
            public Class<AmethystBlock> blockClass() {
                return AmethystBlock.class;
            }

            @Override
            public AmethystBlock create(MMDMaterial material, MMDBlock definition) {
                throw new IllegalStateException("needs a context");
            }

            @Override
            public AmethystBlock create(BlockContext context) {
                return new AmethystBlock(context.properties());
            }
        });
        api.registerBlockImplementation(CRYSTAL_CLUSTER, new BlockImplementation<CrystalClusterBlock>() {
            @Override
            public Class<CrystalClusterBlock> blockClass() {
                return CrystalClusterBlock.class;
            }

            @Override
            public CrystalClusterBlock create(MMDMaterial material, MMDBlock definition) {
                throw new IllegalStateException("needs a context");
            }

            @Override
            public CrystalClusterBlock create(BlockContext context) {
                return new CrystalClusterBlock(context.properties());
            }
        });
        api.registerBlockImplementation(BUDDING_CRYSTAL, new BlockImplementation<BuddingCrystalBlock>() {
            @Override
            public Class<BuddingCrystalBlock> blockClass() {
                return BuddingCrystalBlock.class;
            }

            @Override
            public BuddingCrystalBlock create(MMDMaterial material, MMDBlock definition) {
                throw new IllegalStateException("needs a context");
            }

            @Override
            public BuddingCrystalBlock create(BlockContext context) {
                var binding = context.blockEntity().orElseThrow().as(BuddingCrystalBlockEntity.class);
                return new BuddingCrystalBlock(context.properties(), binding, clusterOf(context.material()),
                        Flavor.of(context.material()));
            }
        });
        BLOCK_ENTITY_FACTORIES.register(BUDDING_CRYSTAL, new BlockEntityFactory<BuddingCrystalBlockEntity, Integer>() {
            @Override
            public Class<BuddingCrystalBlockEntity> blockEntityClass() {
                return BuddingCrystalBlockEntity.class;
            }

            @Override
            public Codec<Integer> settingsCodec() {
                return Codec.intRange(1, Integer.MAX_VALUE).fieldOf("growth_interval").codec();
            }

            @Override
            public BuddingCrystalBlockEntity create(BlockEntityType<BuddingCrystalBlockEntity> type, BlockPos pos,
                                                    BlockState state, MMDBlockEntity definition, Integer growthInterval) {
                return new BuddingCrystalBlockEntity(type, pos, state, growthInterval);
            }

            @Override
            public BlockEntityTicker<BuddingCrystalBlockEntity> ticker(Level level, MMDBlockEntity definition,
                                                                       Integer growthInterval) {
                return level.isClientSide ? null : BuddingCrystalBlockEntity::serverTick;
            }
        });

        BLOCK_ENTITY_TYPES.register(modBus);
        registrar = new RuntimeContentRegistrar(Technomagy.MODID, api, modBus);
        modBus.addListener(CrystalContent::queue);
    }

    /**
     * MMDLib loads definitions in deferred construction work, and the order of different mods'
     * deferred work is not guaranteed. NewRegistryEvent fires after all of it has run and before
     * any RegisterEvent, so the materials are loaded and the registers still accept entries.
     * This queues every Technomagy material, including the stones, which use only MMDLib's
     * built-in forms.
     */
    private static void queue(NewRegistryEvent event) {
        var materials = MMDLib.loadedMaterials().values().stream()
                .filter(result -> result.material().name().orElseThrow().getNamespace().equals(Technomagy.MODID))
                .toList();
        if (materials.isEmpty()) {
            throw new IllegalStateException("MMDLib loaded no " + Technomagy.MODID + " materials; check mmdlib/material/");
        }
        materials.forEach(registrar::queue);
        registrar.blockEntityHandles().values().forEach(handle -> handle.register(BLOCK_ENTITY_TYPES));
    }

    /** The cluster block of a material, named by MMDLib's {@code <material>_<module>} rule. */
    private static Supplier<CrystalClusterBlock> clusterOf(MMDMaterial material) {
        var name = material.name().orElseThrow();
        var cluster = name.withPath(name.getPath() + "_" + CRYSTAL_CLUSTER.getPath());
        return Suppliers.memoize(() -> (CrystalClusterBlock) BuiltInRegistries.BLOCK.get(cluster));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Technomagy.MODID, path);
    }
}
