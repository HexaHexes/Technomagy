package com.mcmoddev.technomagy.init;

import com.mcmoddev.technomagy.blocks.BlockAlchemyTable;
import com.mcmoddev.technomagy.blocks.BlockAlchemicBookshelf;
import com.mcmoddev.technomagy.blocks.BlockAlchemicFurnace;
import com.mcmoddev.technomagy.blocks.BlockAlchemyChair;
import com.mcmoddev.technomagy.init.ItemGroups;
import com.mcmoddev.basemetals.init.Materials;
import com.mcmoddev.lib.data.Names;
import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraft.block.Block;

public class TMBlocks extends com.mcmoddev.lib.init.Blocks {
	public static boolean initDone = false;
	
	protected static Block createAlchemicBookshelf(MMDMaterial material) {
		if (material == null) {
			return null;
		}

		if ((material.getBlock("bookshelf") == null)) {
			material.addNewBlock( "bookshelf", addBlock( new BlockAlchemicBookshelf(Materials.getMaterialByName("wood")), "bookshelf", material, ItemGroups.blocksTab));
			((BlockAlchemicBookshelf)material.getBlock("bookshelf")).setFullBlock(false);
		}

		return material.getBlock("bookshelf");
	}
	
	protected static Block createAlchemyTable(MMDMaterial material) {
		if (material == null) {
			return null;
		}

		if ((material.getBlock("table") == null)) {
			material.addNewBlock("table", addBlock( new BlockAlchemyTable(Materials.getMaterialByName("wood")), "table", material, ItemGroups.blocksTab));
		}

		return material.getBlock("table");
	}

	protected static Block createAlchemyChair(MMDMaterial material) {
		if (material == null) {
			return null;
		}

		if ((material.getBlock("chair") == null)) {
			material.addNewBlock("chair", addBlock( new BlockAlchemyChair(Materials.getMaterialByName("wood")), "chair", material, ItemGroups.blocksTab));
		}

		return material.getBlock("chair");
	}
	
	protected static Block createAlchemicFurnace(MMDMaterial material) {
		if( material == null ) {
			return null;
		}
		
		if( (material.getBlock("furnace") == null) ) {
			material.addNewBlock("furnace", addBlock( new BlockAlchemicFurnace( Materials.getMaterialByName("iron") ), "furnace", material, ItemGroups.blocksTab));
		}
		
		return material.getBlock("furnace");
	}

	public static void init() {
		TMMaterials.init();
		ItemGroups.init();
		
		createOre(TMMaterials.air, ItemGroups.blocksTab);
		createOre(TMMaterials.earth, ItemGroups.blocksTab);
		createOre(TMMaterials.fire, ItemGroups.blocksTab);
		createOre(TMMaterials.water, ItemGroups.blocksTab);
		createOre(TMMaterials.light, ItemGroups.blocksTab);
		createOre(TMMaterials.dark, ItemGroups.blocksTab);
		
		createBlock(TMMaterials.air, ItemGroups.blocksTab);
		createBlock(TMMaterials.earth, ItemGroups.blocksTab);
		createBlock(TMMaterials.fire, ItemGroups.blocksTab);
		createBlock(TMMaterials.water, ItemGroups.blocksTab);
		createBlock(TMMaterials.light, ItemGroups.blocksTab);
		createBlock(TMMaterials.dark, ItemGroups.blocksTab);
		createBlock(TMMaterials.spirit, true, ItemGroups.blocksTab);
		createAlchemicBookshelf(TMMaterials.alchBooks);
		createAlchemyTable(TMMaterials.alchTable);
		createAlchemicFurnace(TMMaterials.alchFurnace);
		
//		createAlchemyChair(TMMaterials.alchChair);
		
		initDone = true;
	}
}
