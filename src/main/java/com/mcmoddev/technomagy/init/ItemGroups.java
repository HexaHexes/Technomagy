package com.mcmoddev.technomagy.init;

public class ItemGroups extends com.mcmoddev.lib.init.ItemGroups {

	public static boolean initDone = false;
	
	private ItemGroups() {
		throw new IllegalAccessError("Not a instantiable class");
	}

	public static void init() {
		if (initDone) {
			return;
		}

		initDone = true;
	}

}
