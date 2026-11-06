package zone.moddev.mc.technomagy.spell;

import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import zone.moddev.mc.technomagy.mana.ManaType;

/** Non-stackable casting device; Ability Hotbar owns spell selection (D74). */
final class SpellFocusItem extends Item {

    /** Creates the focus with ordinary item properties. */
    SpellFocusItem(Properties properties) {
        super(properties);
    }

    /** Casts the authoritative active hotbar slot through Ability Core on the server (D74). */
    @Override
    public InteractionResultHolder<ItemStack> use(
        Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            SpellContent.castSelected(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** Casts before a clicked block opens its own interface; protection events still run first. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        return use(context.getLevel(), player, context.getHand()).getResult();
    }

    /** Reads the independent D73 machinery element selection; unset data has no default. */
    static Optional<ManaType> selectedElement(ItemStack stack) {
        String value = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag().getString("technomagy_element");
        return java.util.Arrays.stream(ManaType.values())
            .filter(type -> type.getSerializedName().equals(value)).findFirst();
    }

    /** Saves the validated D73 machinery selection independently of hotbar spell selection. */
    static void selectElement(ItemStack stack, ManaType element) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
            tag -> tag.putString("technomagy_element", element.getSerializedName()));
    }

}
