package com.mcmoddev.technomagy.proxy;

import com.mcmoddev.technomagy.init.ItemGroups;
import com.mcmoddev.technomagy.init.TMBlocks;
import com.mcmoddev.technomagy.init.TMItems;
import com.mcmoddev.technomagy.init.TMMaterials;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

	public void preInit(FMLPreInitializationEvent event) {
		TMMaterials.init();
		ItemGroups.init();
		TMBlocks.init();
		TMItems.init();
		ItemGroups.setupIcons();
	}

	public void init(FMLInitializationEvent event) {
		// TODO Auto-generated method stub
		
	}

	public void postInit(FMLPostInitializationEvent event) {
		// TODO Auto-generated method stub
		
	}

}
