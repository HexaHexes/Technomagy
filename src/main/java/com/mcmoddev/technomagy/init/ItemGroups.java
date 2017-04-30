package com.mcmoddev.technomagy.init;

import com.mcmoddev.lib.data.Names;
import com.mcmoddev.lib.init.MMDCreativeTab;
import com.mcmoddev.lib.init.Materials;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

public class ItemGroups extends com.mcmoddev.lib.init.ItemGroups {

	public static boolean initDone = false;
	private static final int blocksTabId  = addTab("blocks", true );
	private static final int itemsTabId  = addTab("items", true );
	public static final MMDCreativeTab blocksTab = getTab(blocksTabId);
	public static final MMDCreativeTab itemsTab = getTab(itemsTabId);

	private ItemGroups() {
		throw new IllegalAccessError("Not a instantiable class");
	}

	public static void init() {
		if (initDone) {
			return;
		}

		initDone = true;
	}

	public static void setupIcons() {
		Block temp;
		Item blocksTabIconItem;
		Item itemsTabIconItem;
		
		if (TMMaterials.alchBooks.getBlock("bookshelf") != null) {
			temp = TMMaterials.alchBooks.getBlock("bookshelf");
		} else {
			temp = net.minecraft.init.Blocks.BOOKSHELF;
		}
		blocksTabIconItem = Item.getItemFromBlock(temp);

		if( TMMaterials.air.getItem(Names.CRYSTAL) != null ) {
			itemsTabIconItem = TMMaterials.air.getItem(Names.CRYSTAL);
		} else {
			itemsTabIconItem = net.minecraft.init.Items.DIAMOND;
		}
		
		blocksTab.setTabIconItem(blocksTabIconItem);
		itemsTab.setTabIconItem(itemsTabIconItem);
	}
}
