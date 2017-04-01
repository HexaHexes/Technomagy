package com.mcmoddev.technomagy.init;

import com.mcmoddev.lib.material.MetalMaterial;
import com.mcmoddev.lib.material.MetalMaterial.MaterialType;

public class TMMaterials extends com.mcmoddev.lib.init.Materials {
	public static MetalMaterial earth;
	public static MetalMaterial air;
	public static MetalMaterial fire;
	public static MetalMaterial water;
	public static MetalMaterial light;
	public static MetalMaterial dark;
	public static MetalMaterial spirit;
	
	public static boolean initDone = false;
	
	protected TMMaterials() {
		throw new IllegalAccessError("Not an instantiable class!");
	}
	
	public static void init() {
		if( initDone ) {
			return;
		}
		light  = createRareMaterial("light", MaterialType.GEM, 8, 8, 15,   0xffbdedce);
		dark   = createRareMaterial("dark",  MaterialType.GEM, 8, 8, 15,   0xff333634);
		water  = createRareMaterial("water", MaterialType.GEM, 8, 8, 12.5, 0xff0448ff);
		air    = createRareMaterial("air",   MaterialType.GEM, 8, 8, 12.5, 0xffe8eae9);
		earth  = createRareMaterial("earth", MaterialType.GEM, 8, 8, 12.5, 0xff42d639);
		fire   = createRareMaterial("fire",  MaterialType.GEM, 8, 8, 12.5, 0xfff80000);
        spirit = createOrelessRareMaterial("spirit", MaterialType.GEM, 8, 8, 10, 0xff888888);
		
		initDone = true;
	}
}
