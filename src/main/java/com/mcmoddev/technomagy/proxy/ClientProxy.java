package com.mcmoddev.technomagy.proxy;

import com.mcmoddev.technomagy.Technomagy;
import com.mcmoddev.technomagy.init.TMItems;
import com.mcmoddev.technomagy.init.TMBlocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockSlab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemModelMesher;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

	@Override
	public void init(FMLInitializationEvent event) {
		super.init(event);

		for (final String name : TMItems.getItemRegistry().keySet()) {
			registerRenderOuter(TMItems.getItemByName(name));
		}

		for (final String name : TMBlocks.getBlockRegistry().keySet()) {
			registerRenderOuter(TMBlocks.getBlockByName(name));
		}
	}

	public void registerRenderOuter(Item item) {
		if (item != null) {
			registerRender(item, TMItems.getNameOfItem(item));
		}
	}

	public void registerRenderOuter(Block block) {
		if (block != null) {
			registerRender(Item.getItemFromBlock(block), TMBlocks.getNameOfBlock(block));
		}
	}

	public void registerRender(Item item, String name) {
		final ItemModelMesher itemModelMesher = Minecraft.getMinecraft().getRenderItem().getItemModelMesher();
		if( item == null ) {
			return;
		}
		
		if (!item.getRegistryName().getResourceDomain().equals(Technomagy.MODID)) {
			return;
		}

		itemModelMesher.register(item, 0, new ModelResourceLocation(new ResourceLocation(item.getRegistryName().getResourceDomain(), name), "inventory"));
	}

}
