package zone.moddev.mc.technomagy.spell.client;

import java.io.IOException;

import java.nio.file.Path;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.InteractionHand;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import zone.moddev.mc.technomagy.Technomagy;

/**
 * Opt-in client smoke check, excluded from the production mod, for the real rendered focus menu.
 */
@EventBusSubscriber(modid = "technomagy", value = Dist.CLIENT)
public final class SpellClientGameCheck {
    private static boolean opened;
    private static boolean captured;

    private SpellClientGameCheck() {
    }

    /** Opens the menu only after successful client/resource startup, never during ordinary play. */
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (Boolean.getBoolean("technomagy.testFocusScreen")
            && !opened
            && Minecraft.getInstance().screen instanceof TitleScreen) {
            opened = true;
            Minecraft.getInstance()
                .setScreen(new ElementSelectionScreen(InteractionHand.MAIN_HAND));
        }
    }

    /**
     * Verifies all seven real buttons, captures the rendered frame, and cleanly stops the client.
     */
    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post event) {
        if (!Boolean.getBoolean("technomagy.testFocusScreen")
            || captured
            || !(event.getScreen() instanceof ElementSelectionScreen screen)) {
            return;
        }
        long buttons = screen.children().stream().filter(child -> child instanceof Button).count();
        if (buttons != 7) {
            throw new IllegalStateException("Expected seven focus buttons, got " + buttons);
        }
        try (NativeImage image = Screenshot
            .takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of("focus-selection-check.png"));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not capture focus selection screen", exception);
        }
        // Exercise the production orb renderer with zero, full, inactive, and over-capacity
        // fixtures. This proves layout only; the server tests cover actual pool projection/codec.
        var rows = java.util.List.of(
            new zone.moddev.mc.technomagy.spell.ManaPoolSnapshot.Bar(3, 10, true, true),
            new zone.moddev.mc.technomagy.spell.ManaPoolSnapshot.Bar(0, 10, true, true),
            new zone.moddev.mc.technomagy.spell.ManaPoolSnapshot.Bar(10, 10, true, true),
            new zone.moddev.mc.technomagy.spell.ManaPoolSnapshot.Bar(7, 10, true, true),
            new zone.moddev.mc.technomagy.spell.ManaPoolSnapshot.Bar(3, 0, true, false),
            new zone.moddev.mc.technomagy.spell.ManaPoolSnapshot.Bar(12, 10, true, true));
        PersonalManaHud.draw(event.getGuiGraphics(),
            new zone.moddev.mc.technomagy.spell.ManaPoolSnapshot(rows));
        event.getGuiGraphics().flush();
        try (NativeImage image = Screenshot
            .takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of("mana-hud-check.png"));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not capture personal mana HUD", exception);
        }
        captured = true;
        Technomagy.LOGGER.info(
            "SPELL_CLIENT_CHECK: seven focus buttons rendered; screenshot captured");
        Minecraft.getInstance().stop();
    }
}
