package com.mcmoddev.technomagy.mana;

import com.mcmoddev.technomagy.Technomagy;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/**
 * The mana capability, looked up like NeoForge's energy capability:
 * {@code level.getCapability(ManaCapabilities.BLOCK, pos, side)}. Blocks register their handlers
 * in {@code RegisterCapabilitiesEvent}.
 */
public final class ManaCapabilities {
    public static final BlockCapability<IManaHandler, @Nullable Direction> BLOCK = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath(Technomagy.MODID, "mana_handler"), IManaHandler.class);

    private ManaCapabilities() {
    }
}
