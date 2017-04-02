package com.mcmoddev.technomagy.init;

public class TMItems extends com.mcmoddev.lib.init.Items {
	public static boolean initDone = false;

	public static void init() {
		if( initDone ) {
			return;
		}

		createCrystal(TMMaterials.air);
		createCrystal(TMMaterials.earth);
		createCrystal(TMMaterials.fire);
		createCrystal(TMMaterials.water);
		createCrystal(TMMaterials.light);
		createCrystal(TMMaterials.dark);
		createCrystal(TMMaterials.spirit);

		initDone = true;
	}
}
