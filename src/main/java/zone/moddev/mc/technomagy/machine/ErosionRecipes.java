package zone.moddev.mc.technomagy.machine;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.common.recipes.CrushingRecipe;

/**
 * Resolves Erode's processing outputs through MMDLib's live crushing recipes (D61).
 *
 * <p>
 * This is recipe resolution only: it neither mines blocks nor consumes inventory or mana. Callers
 * must validate the full operation and output space before committing those changes. Access is
 * restricted to the owning server thread. No recipe cache is retained, so datapack reloads are
 * observed on the next lookup without duplicating MMDLib's recipe infrastructure.
 */
final class ErosionRecipes {
    private ErosionRecipes() {
    }

    /**
     * Resolves one input item's recipe-defined crushing output.
     *
     * @param level
     *            server level whose current recipe manager is authoritative
     * @param offered
     *            input stack; neither its count nor its components are modified
     * @return a fresh output stack, or empty if no recipe matches or the input is empty
     * @throws NullPointerException
     *             if either argument is null
     * @throws IllegalStateException
     *             if called outside the owning server thread
     */
    static Optional<ItemStack> findOutput(ServerLevel level, ItemStack offered) {
        return RitualProcessingRecipes.findOutput(level, offered, CrushingRecipe.TYPE.get());
    }
}
