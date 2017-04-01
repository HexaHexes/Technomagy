package com.mcmoddev.technomagy.blocks;

import java.util.Random;

import com.mcmoddev.basemetals.init.Materials;
import com.mcmoddev.lib.material.IMetalObject;
import com.mcmoddev.lib.material.MetalMaterial;
import com.mcmoddev.lib.registry.IOreDictionaryEntry;
import com.mcmoddev.lib.util.Oredicts;

import net.minecraft.block.BlockOre;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class BlockElementalOre extends BlockOre implements IOreDictionaryEntry, IMetalObject {

	private final MetalMaterial material;
	private final String oreDict;

	/**
	 *
	 * @param material
	 *            The material the ore is made from
	 */
	public BlockElementalOre(MetalMaterial material) {
		super();
		// this.setSoundType(SoundType.STONE);
		this.material = material;
		this.blockHardness = Math.max(5f, material.getOreBlockHardness());
		this.blockResistance = Math.max(1.5f, material.getBlastResistance() * 0.75f);
		this.setHarvestLevel("pickaxe", material.getRequiredHarvestLevel());
		this.oreDict = Oredicts.ORE + material.getCapitalizedName();
	}
	
	public BlockElementalOre() {
		throw new IllegalAccessError("This class requires the use of the constructor that passes in a MetalMaterial");
	}

	public BlockElementalOre(MapColor color) {
		throw new IllegalAccessError("This class requires the use of the constructor that passes in a MetalMaterial");
	}

	@Override
	public MetalMaterial getMaterial() {
		return this.material;
	}

	@Override
	@Deprecated
	public MetalMaterial getMetalMaterial() {
		return this.material;
	}

	@Override
	public String getOreDictionaryName() {
		return this.oreDict;
	}

	@Override
	public int quantityDropped(IBlockState state, int fortune, Random random) {
		int most = 3;
		int least = 2;
		int total;
		total = ((most - least) + fortune) + 1;
		return least + random.nextInt(total);
	}

	@Override
	public Item getItemDropped(IBlockState state, Random random, int fortune) {
		return this.material.ingot;
	}

	@Override
	public int getExpDrop(final IBlockState bs, IBlockAccess w, final BlockPos coord, final int i) {
		return 0; // No XP for you! The crystals are power
	}

	@Override
	public boolean canEntityDestroy(IBlockState bs, IBlockAccess w, BlockPos coord, Entity entity) {
		if ((this == Materials.starsteel.ore) && (entity instanceof net.minecraft.entity.boss.EntityDragon))
			return false;
		return super.canEntityDestroy(bs, w, coord, entity);
	}

}
