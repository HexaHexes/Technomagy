package com.mcmoddev.technomagy.init;

import com.mcmoddev.technomagy.blocks.BlockElementalOre;

import java.util.ArrayList;

import com.mcmoddev.technomagy.init.ItemGroups;
import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraft.block.Block;

public class TMBlocks extends com.mcmoddev.lib.init.Blocks {
	public static boolean initDone = false;
	

	protected static Block createCustomBlock(MMDMaterial material, String odPrefix) {
		if (material == null) {
			return null;
		}

		if ((material.ore == null)) {
			material.block = addBlock(new BlockElementalOre(material), odPrefix, material, ItemGroups.blocksTab);
		}

		return material.block;
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
		createBookshelf(TMMaterials.alchBooks);
		createCustomBlock(TMMaterials.alchTable, "item");
		
		initDone = true;
	}
}
