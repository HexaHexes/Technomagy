package com.mcmoddev.technomagy.crystal;

import net.minecraft.world.item.Item;
import zone.moddev.mc.lib.common.runtime.MaterialBacked;
import zone.moddev.mc.lib.common.types.MMDMaterial;

/** A harvested crystal of one flavor of magic. */
public class CrystalItem extends Item implements MaterialBacked {
    private final MMDMaterial material;
    private final Flavor flavor;

    public CrystalItem(Properties properties, MMDMaterial material) {
        super(properties);
        this.material = material;
        this.flavor = Flavor.of(material);
    }

    @Override
    public MMDMaterial material() {
        return material;
    }

    public Flavor flavor() {
        return flavor;
    }
}
