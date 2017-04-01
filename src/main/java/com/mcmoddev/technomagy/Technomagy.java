package com.mcmoddev.technomagy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mcmoddev.technomagy.proxy.CommonProxy;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Mod entry point
 *
 * 
 * @author Daniel Hazelton &lt;dshadowwolf@gmail.com&gt;
 */

@Mod( modid = Technomagy.MODID,
name = Technomagy.NAME,
version = Technomagy.VERSION,
dependencies = "required-after:Forge@[12.18.3.2185,);required-after:basemetals;",
acceptedMinecraftVersions = "[1.10.2,)" )

public class Technomagy {
	@Instance
	public static Technomagy instance;

	/** ID of this Mod */
	public static final String MODID = "technomagy";
	/** Display name of this Mod */
	public static final String NAME = "Technomagy";
	/** Version Number, in SemVer format */
	public static final String VERSION = "0.0.1-alpha1";

	/** base format for naming the proxies */
	public static final String PROXY_BASE = "com.mcmoddev."+MODID+".proxy.";

	@SidedProxy( clientSide = PROXY_BASE + "ClientProxy", serverSide = PROXY_BASE + "ServerProxy" )
	public static CommonProxy proxy;

	public static Logger logger = LogManager.getFormatterLogger(MODID);


	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		proxy.preInit(event);
	}

	@EventHandler
	public void init(FMLInitializationEvent event) {
		proxy.init(event);
	}

	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		proxy.postInit(event);
	}

	public static boolean hasMMDLib() {
		return Loader.isModLoaded("mmdlib");
	}
}
