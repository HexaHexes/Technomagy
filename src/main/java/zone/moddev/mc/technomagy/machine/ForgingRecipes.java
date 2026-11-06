package zone.moddev.mc.technomagy.machine;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Resolves Forge's smelting results through the live vanilla recipe manager (D51).
 *
 * <p>
 * This is the recipe-backed part of Forge, not a complete spell executor. No speculative
 * replacement block, fallback output, extra yield, or world mutation is introduced by lookup.
 */
final class ForgingRecipes {
    private ForgingRecipes() {
    }

    /**
     * Looks up one input item's smelting result, preserving its recipe-defined output count.
     *
     * @param level
     *            owning server level
     * @param offered
     *            input stack; never modified
     * @return fresh output or empty if no smelting recipe matches
     * @throws NullPointerException
     *             if either argument is null
     * @throws IllegalStateException
     *             if called outside the server thread
     */
    static Optional<ItemStack> findOutput(ServerLevel level, ItemStack offered) {
        return RitualProcessingRecipes.findOutput(level, offered, RecipeType.SMELTING);
    }
}
