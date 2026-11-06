package zone.moddev.mc.technomagy.spell.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

import org.lwjgl.glfw.GLFW;

import zone.moddev.mc.technomagy.spell.SpellContent;

/** Client-only D73 element selector binding, configurable through Minecraft Controls. */
@EventBusSubscriber(modid = "technomagy", value = Dist.CLIENT)
public final class ElementSelectionKeys {
    private static final KeyMapping SELECT = new KeyMapping("key.technomagy.select_element",
        KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD,
        "key.categories.technomagy");

    private ElementSelectionKeys() {
    }

    /** Consumes the binding only during play and opens selection for an actually held focus. */
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        while (SELECT.consumeClick()) {
            if (minecraft.player == null || minecraft.screen != null) {
                continue;
            }
            for (var hand : InteractionHand.values()) {
                if (minecraft.player.getItemInHand(hand).is(SpellContent.FOCUS.get())) {
                    minecraft.setScreen(new ElementSelectionScreen(hand));
                    break;
                }
            }
        }
    }

    /** Registers the binding on the client mod bus without loading client classes on servers. */
    @EventBusSubscriber(modid = "technomagy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        /** Adds the element selector to Minecraft's rebindable Controls list. */
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(SELECT);
        }
    }
}
