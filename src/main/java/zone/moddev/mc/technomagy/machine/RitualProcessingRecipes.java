package zone.moddev.mc.technomagy.machine;

import java.util.Objects;
import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * Shared live-recipe lookup for Erode and Forge processing (D51, D61).
 *
 * <p>
 * Lookup resolves one item without consuming its stack or changing the world. Recipes are read
 * afresh from the server manager so reloads require no private cache. Actual block conversion,
 * payment, and inventory commitment remain the invoking operation's responsibility.
 */
public final class RitualProcessingRecipes {
    private RitualProcessingRecipes() {
    }

    /**
     * Assembles a fresh recipe output for a single offered item.
     *
     * @param level
     *            owning server level
     * @param offered
     *            input stack, never mutated
     * @param type
     *            recipe type whose single-item processing rules apply
     * @param <R>
     *            recipe implementation
     * @return fresh output, or empty for no matching recipe or an empty input
     * @throws NullPointerException
     *             if an argument is null
     * @throws IllegalStateException
     *             if called away from the server thread
     */
    public static <R extends Recipe<SingleRecipeInput>> Optional<ItemStack> findOutput(
        ServerLevel level, ItemStack offered, RecipeType<R> type) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(offered, "offered");
        Objects.requireNonNull(type, "type");
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Ritual recipe lookup requires the server thread");
        }
        if (offered.isEmpty()) {
            return Optional.empty();
        }
        SingleRecipeInput input = new SingleRecipeInput(offered.copyWithCount(1));
        return level.getRecipeManager()
            .getRecipeFor(type, input, level)
            .map(holder -> holder.value().assemble(input, level.registryAccess()));
    }
}
