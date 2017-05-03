package com.mcmoddev.technomagy.blocks;

import com.mcmoddev.lib.material.IMMDObject;
import com.mcmoddev.lib.material.MMDMaterial;
import com.mcmoddev.lib.registry.IOreDictionaryEntry;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;

public class BlockAlchemyTable extends Block implements IMMDObject, IOreDictionaryEntry {

	protected MMDMaterial material;
	protected String oreDictSuffix = "table";
	
	public BlockAlchemyTable( MMDMaterial material ) {
		super(Material.WOOD);
		this.material = material;
	}

	@Override
	public String getOreDictionaryName() {
		return "tableAlchemy";
	}

	@Override
	public MMDMaterial getMMDMaterial() {
		return this.material;
	}
	
	@Override
	public boolean isFullCube(IBlockState state) {
		return false;
	}
	
	@Override
	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}
}
