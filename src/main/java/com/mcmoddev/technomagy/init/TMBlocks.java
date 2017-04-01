package com.mcmoddev.technomagy.init;

import com.mcmoddev.technomagy.blocks.BlockElementalOre;

import java.util.ArrayList;

import com.mcmoddev.lib.block.BlockDoubleMetalSlab;
import com.mcmoddev.lib.init.ItemGroups;
import com.mcmoddev.lib.material.MetalMaterial;
import com.mcmoddev.lib.registry.IOreDictionaryEntry;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockSlab;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;
import scala.reflect.internal.Trees.Super;

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
	
	protected static Block createCustomBlock(MetalMaterial material, String odPrefix) {
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
		createCustomBlock(TMMaterials.alchBooks, "item");
		createCustomBlock(TMMaterials.alchTable, "item");
		
		initDone = true;
	}
}
