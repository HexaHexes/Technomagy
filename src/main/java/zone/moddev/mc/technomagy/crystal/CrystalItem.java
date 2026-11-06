package zone.moddev.mc.technomagy.crystal;

import net.minecraft.world.item.Item;

import zone.moddev.mc.lib.common.runtime.MaterialBacked;
import zone.moddev.mc.lib.common.types.MMDMaterial;

/**
 * A harvested crystal of one flavor of magic: the item a full crystal cluster drops. MMDLib creates
 * one per flavor material through the {@code technomagy:crystal} item module.
 */
public class CrystalItem extends Item implements MaterialBacked {
    private final MMDMaterial material;
    private final Flavor flavor;

    /**
     * Creates the crystal of one flavor.
     *
     * @param properties
     *            the item properties MMDLib built from the module template
     * @param material
     *            the flavor's material
     * @throws IllegalArgumentException
     *             if the material is not a flavor material
     */
    public CrystalItem(Properties properties, MMDMaterial material) {
        super(properties);
        this.material = material;
        this.flavor = Flavor.of(material);
    }

    /**
     * The MMDLib material this crystal was made from.
     *
     * @return the flavor's material
     */
    @Override
    public MMDMaterial material() {
        return material;
    }

    /**
     * This crystal's flavor of magic.
     *
     * @return the flavor
     */
    public Flavor flavor() {
        return flavor;
    }
}
