package com.mcmoddev.technomagy.init;

import com.mcmoddev.technomagy.init.ItemGroups;
import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

public class TMBlocks extends com.mcmoddev.lib.init.Blocks {
	public static boolean initDone = false;
	

	protected static Block createCustomBlock(MMDMaterial material, String odPrefix) {
		if (material == null) {
			return null;
		}

		if ((material.block == null)) {
			material.block = addBlock( new Block(net.minecraft.block.material.Material.WOOD) { 
				@Override
				public boolean isFullCube(IBlockState state) {
					return false;
				}
				
				@Override
				public boolean isOpaqueCube(IBlockState state) {
					return false;
				}
			}, odPrefix, material, ItemGroups.blocksTab);
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
		createCustomBlock(TMMaterials.alchTable, "item");
		
		initDone = true;
	}
}
