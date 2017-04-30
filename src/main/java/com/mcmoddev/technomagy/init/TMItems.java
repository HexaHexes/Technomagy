package com.mcmoddev.technomagy.init;

public class TMItems extends com.mcmoddev.lib.init.Items {
	public static boolean initDone = false;

	public static void init() {
		if( initDone ) {
			return;
		}

		createCrystal(TMMaterials.air, ItemGroups.itemsTab);
		createCrystal(TMMaterials.earth, ItemGroups.itemsTab);
		createCrystal(TMMaterials.fire, ItemGroups.itemsTab);
		createCrystal(TMMaterials.water, ItemGroups.itemsTab);
		createCrystal(TMMaterials.light, ItemGroups.itemsTab);
		createCrystal(TMMaterials.dark, ItemGroups.itemsTab);
		createCrystal(TMMaterials.spirit, ItemGroups.itemsTab);

		initDone = true;
	}
}
