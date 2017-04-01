package com.mcmoddev.technomagy.init;

import com.mcmoddev.technomagy.blocks.BlockElementalOre;
import com.mcmoddev.lib.init.ItemGroups;
import com.mcmoddev.lib.material.MetalMaterial;

import net.minecraft.block.Block;

public class TMBlocks extends com.mcmoddev.lib.init.Blocks {
	public static boolean initDone = false;
	
	protected static Block createElementalOre(MetalMaterial material) {
		if (material == null) {
			return null;
		}

		if ((material.ore == null)) {
			material.ore = addBlock(new BlockElementalOre(material), "ore", material, ItemGroups.blocksTab);
		}

		return material.ore;
	}
	
	public static void init() {
		TMMaterials.init();
		ItemGroups.init();
		
		createOre(TMMaterials.air);
		createOre(TMMaterials.earth);
		createOre(TMMaterials.fire);
		createOre(TMMaterials.water);
		createOre(TMMaterials.light);
		createOre(TMMaterials.dark);
		
		createBlock(TMMaterials.air);
		createBlock(TMMaterials.earth);
		createBlock(TMMaterials.fire);
		createBlock(TMMaterials.water);
		createBlock(TMMaterials.light);
		createBlock(TMMaterials.dark);
		createBlock(TMMaterials.spirit, true);
		
		initDone = true;
	}
}
