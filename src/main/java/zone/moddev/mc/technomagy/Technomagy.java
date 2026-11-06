package zone.moddev.mc.technomagy;

import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.resources.ResourceLocation;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import zone.moddev.mc.skysleylines.api.LeyLinesApi;
import zone.moddev.mc.skysleylines.api.MagicAmount;
import zone.moddev.mc.skysleylines.api.Paradigm;

import zone.moddev.mc.technomagy.crystal.CrystalContent;
import zone.moddev.mc.technomagy.machine.MachineContent;
import zone.moddev.mc.technomagy.spell.SpellContent;

/**
 * The mod's entry point, constructed once by NeoForge while mods load.
 *
 * <p>
 * The constructor wires each content area onto the mod event bus: the elemental crystals and the
 * stones, which come from MMDLib data (see {@link CrystalContent}), and the machine pieces (see
 * {@link MachineContent}). Each area owns its own registration; this class only calls them.
 *
 * <p>
 * The setup, IMC and server-starting handlers are the NeoForge MDK template's examples. They only
 * log, and do nothing for the mod.
 */
@Mod(Technomagy.MODID)
public class Technomagy {

    /** The mod id. Must match {@code modId} in {@code META-INF/neoforge.mods.toml}. */
    public static final String MODID = "technomagy";

    /** The mod's logger. */
    public static final Logger LOGGER = LogManager.getLogger();

    /** The mod's power system */
    /** Unaspected/Raw */
    public static final ResourceLocation RAW = id("raw");

    /** Light Aspected */
    public static final ResourceLocation LIGHT = id("light");

    /** Dark Aspected */
    public static final ResourceLocation DARK = id("dark");

    /** Earth Aspected */
    public static final ResourceLocation EARTH = id("earth");

    /** Air Aspected */
    public static final ResourceLocation AIR = id("air");

    /** Fire Aspected */
    public static final ResourceLocation FIRE = id("fire");

    /** Water Aspected */
    public static final ResourceLocation WATER = id("water");

    /**
     * Registers every content area and the event handlers.
     *
     * @param modEventBus
     *            the mod's event bus, where registration and lifecycle events fire
     * @param container
     *            the mod's container; unused
     */
    public Technomagy(IEventBus modEventBus, ModContainer container) {
        // Register the setup method for modloading
        modEventBus.addListener(this::setup);
        // Register the enqueueIMC method for modloading
        modEventBus.addListener(this::enqueueIMC);
        // Register the processIMC method for modloading
        modEventBus.addListener(this::processIMC);

        // Register ourselves for server and other game events we are interested in
        NeoForge.EVENT_BUS.register(this);

        CrystalContent.register(modEventBus);
        MachineContent.register(modEventBus);
        SpellContent.register(modEventBus, container);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> LeyLinesApi.paradigms().register(
            new Paradigm<>(id("technomagy"), null,
                List.of(new Paradigm.Currency(RAW, () -> MagicAmount.of(6000)),
                    new Paradigm.Currency(LIGHT, () -> MagicAmount.of(1000)),
                    new Paradigm.Currency(DARK, () -> MagicAmount.of(1000)),
                    new Paradigm.Currency(EARTH, () -> MagicAmount.of(1000)),
                    new Paradigm.Currency(AIR, () -> MagicAmount.of(1000)),
                    new Paradigm.Currency(FIRE, () -> MagicAmount.of(1000)),
                    new Paradigm.Currency(WATER, () -> MagicAmount.of(1000))))));
    }

    private void enqueueIMC(final InterModEnqueueEvent event) {
        // reserved for possible future use
    }

    private void processIMC(final InterModProcessEvent event) {
        // reserved for possible future use
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // reserved for possible future use
    }

    /** Borrowed from Sky's demo mod */
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
