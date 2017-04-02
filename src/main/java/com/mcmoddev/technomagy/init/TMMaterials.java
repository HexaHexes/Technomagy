package com.mcmoddev.technomagy.init;

import com.mcmoddev.lib.material.MMDMaterial;
import com.mcmoddev.lib.material.MMDMaterial.MaterialType;

public class TMMaterials extends com.mcmoddev.lib.init.Materials {
	public static MMDMaterial earth;
	public static MMDMaterial air;
	public static MMDMaterial fire;
	public static MMDMaterial water;
	public static MMDMaterial light;
	public static MMDMaterial dark;
	public static MMDMaterial spirit;
	public static MMDMaterial alchBooks;
	public static MMDMaterial alchTable;
	
	public static boolean initDone = false;
	
	protected TMMaterials() {
		throw new IllegalAccessError("Not an instantiable class!");
	}
	
	public static void init() {
		if( initDone ) {
			return;
		}
		light  = createRareMaterial("light", MaterialType.CRYSTAL, 8, 8, 15,   0xffbdedce);
		dark   = createRareMaterial("dark",  MaterialType.CRYSTAL, 8, 8, 15,   0xff333634);
		water  = createRareMaterial("water", MaterialType.CRYSTAL, 8, 8, 12.5, 0xff0448ff);
		air    = createRareMaterial("air",   MaterialType.CRYSTAL, 8, 8, 12.5, 0xffe8eae9);
		earth  = createRareMaterial("earth", MaterialType.CRYSTAL, 8, 8, 12.5, 0xff42d639);
		fire   = createRareMaterial("fire",  MaterialType.CRYSTAL, 8, 8, 12.5, 0xfff80000);
        spirit = createOrelessRareMaterial("spirit", MaterialType.CRYSTAL, 8, 8, 10, 0xff888888);
		alchBooks = createOrelessMaterial("bookshelf", MaterialType.WOOD, 1, 1, 0, 0xffb4905a);
		alchTable = createOrelessMaterial("alchemy_table", MaterialType.WOOD, 1, 1, 0, 0xff9f844d);
		
		initDone = true;
	}
}
