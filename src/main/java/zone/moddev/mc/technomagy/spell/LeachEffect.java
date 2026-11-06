package zone.moddev.mc.technomagy.spell;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Applies configurable armor loss only when active, after server configuration loads (D69). */
final class LeachEffect extends MobEffect {
    private static final ResourceLocation MODIFIER = ResourceLocation
        .fromNamespaceAndPath("technomagy", "leach");

    /** Registry construction must never evaluate a world-specific config value. */
    LeachEffect() {
        super(MobEffectCategory.HARMFUL, 0x4d8d65);
    }

    /** Adds one transient modifier; refreshing the effect does not stack duplicate penalties. */
    @Override
    public void addAttributeModifiers(AttributeMap attributes, int amplifier) {
        var armor = attributes.getInstance(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(MODIFIER);
            armor.addTransientModifier(
                new AttributeModifier(
                    MODIFIER,
                    -SpellConfig.ARMOR_REDUCTION.get(),
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** Removes only this spell's modifier, preserving other armor and equipment changes. */
    @Override
    public void removeAttributeModifiers(AttributeMap attributes) {
        var armor = attributes.getInstance(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(MODIFIER);
        }
    }
}
