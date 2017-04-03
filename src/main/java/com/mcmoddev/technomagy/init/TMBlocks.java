package com.mcmoddev.technomagy.init;

import com.mcmoddev.technomagy.blocks.BlockAlchemyTable;
import com.mcmoddev.technomagy.blocks.BlockAlchemyChair;
import com.mcmoddev.technomagy.init.ItemGroups;
import com.mcmoddev.basemetals.init.Materials;
import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraft.block.Block;

public class TMBlocks extends com.mcmoddev.lib.init.Blocks {
	public static boolean initDone = false;
	

	protected static Block createAlchemyTable(MMDMaterial material) {
		if (material == null) {
			return null;
		}

		if ((material.block == null)) {
			material.block = addBlock( new BlockAlchemyTable(Materials.getMaterialByName("vanilla_wood")), "table", material, ItemGroups.blocksTab);
		}

		return material.block;
	}

	protected static Block createAlchemyChair(MMDMaterial material) {
		if (material == null) {
			return null;
		}

		if ((material.block == null)) {
			material.block = addBlock( new BlockAlchemyChair(Materials.getMaterialByName("vanilla_wood")), "chair", material, ItemGroups.blocksTab);
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
		createBookshelf(TMMaterials.alchBooks, false);
		createAlchemyTable(TMMaterials.alchTable);
//		createAlchemyChair(TMMaterials.alchChair);
		
		initDone = true;
	}
}
