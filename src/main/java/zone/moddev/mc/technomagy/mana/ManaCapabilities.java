package zone.moddev.mc.technomagy.mana;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.capabilities.BlockCapability;

import org.jetbrains.annotations.Nullable;

import zone.moddev.mc.technomagy.Technomagy;

/**
 * The mana capability, looked up like NeoForge's energy capability: {@code
 * level.getCapability(ManaCapabilities.BLOCK, pos, side)}. Blocks register their handlers in {@code
 * RegisterCapabilitiesEvent}.
 */
public final class ManaCapabilities {
    /**
     * The block mana capability (D24), {@code technomagy:mana_handler}. The context is the side
     * being accessed, or null for no particular side.
     */
    public static final BlockCapability<IManaHandler, @Nullable Direction> BLOCK = BlockCapability
        .createSided(
            ResourceLocation.fromNamespaceAndPath(Technomagy.MODID, "mana_handler"),
            IManaHandler.class);

    private ManaCapabilities() {
    }
}
