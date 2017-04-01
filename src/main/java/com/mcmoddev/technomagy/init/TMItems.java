package com.mcmoddev.technomagy.init;

import com.mcmoddev.basemetals.util.Config.Options;
import com.mcmoddev.lib.init.ItemGroups;
import com.mcmoddev.lib.item.ItemMetalIngot;
import com.mcmoddev.lib.material.MetalMaterial;

import net.minecraft.item.Item;

public class TMItems extends com.mcmoddev.lib.init.Items {
	public static boolean initDone = false;
	
	public static Item createCrystal( MetalMaterial mat ) {
		if (mat == null) {
			return null;
		}

		if ((mat.ingot == null)) {
			mat.ingot = addItem(new ItemMetalIngot(mat), "crystal", mat, ItemGroups.itemsTab);
		}

		return mat.ingot;
	}
	
	public static void init() {
		if( initDone ) {
			return;
		}
		
		TMMaterials.air.ingot = createIngot(TMMaterials.air);
		TMMaterials.earth.ingot = createIngot(TMMaterials.earth);
		TMMaterials.fire.ingot = createIngot(TMMaterials.fire);
		TMMaterials.water.ingot = createIngot(TMMaterials.water);
		TMMaterials.light.ingot = createIngot(TMMaterials.light);
		TMMaterials.dark.ingot = createIngot(TMMaterials.dark);
		TMMaterials.spirit.ingot = createIngot(TMMaterials.spirit);

		initDone = true;
	}
}
