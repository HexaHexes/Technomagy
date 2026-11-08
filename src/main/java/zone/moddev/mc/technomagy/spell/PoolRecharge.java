package zone.moddev.mc.technomagy.spell;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import org.slf4j.Logger;

import zone.moddev.mc.skysleylines.api.LeyLinesApi;
import zone.moddev.mc.skysleylines.api.PlayerPowerService;

import zone.moddev.mc.technomagy.mana.ManaType;

/**
 * Recharges personal elemental pools (D70) once a player has had a casting focus (D77).
 *
 * <p>
 * <b>Unlock.</b> The first time a server pulse finds a {@code technomagy:spell_focus} anywhere in a
 * player's inventory (including armor and offhand slots) or on their menu cursor, the player's
 * {@link #UNLOCKED} attachment is set. It is saved with the player and copied on death, so recharge
 * stays unlocked after the focus is lost. Checking at each pulse, instead of hooking crafting,
 * pickup, and commands separately, catches every way a focus can arrive.
 *
 * <p>
 * <b>Pulse.</b> Every {@link SpellConfig#RECHARGE_INTERVAL} server ticks, each online, living,
 * non-spectating unlocked player gains {@link SpellConfig#RECHARGE_UNITS} units in one pool chosen
 * uniformly from the pools that are active and below capacity, clamped to the remaining room. When
 * every pool is full or inactive, nothing happens, so no pulse is wasted on a full pool. Ley Lines'
 * own fixed per-pool recharge ({@code Settings.autoRate}) stays disabled: it cannot pick a random
 * element. Offline, dead, and spectating players neither recharge nor unlock.
 *
 * <p>
 * <b>Threading:</b> server thread only, driven by {@link ServerTickEvent.Post}. Holds no mutable
 * state of its own; the phase comes from the server tick count.
 */
final class PoolRecharge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister
        .create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, "technomagy");

    /** Whether the player has ever had a casting focus; saved, and kept through death (D77). */
    static final Supplier<AttachmentType<Boolean>> UNLOCKED = ATTACHMENTS.register(
        "recharge_unlocked",
        () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    private PoolRecharge() {
    }

    /**
     * Registers the unlock attachment on the mod bus.
     *
     * @param bus
     *            the mod event bus
     */
    static void register(net.neoforged.bus.api.IEventBus bus) {
        ATTACHMENTS.register(bus);
    }

    /**
     * Runs one recharge pulse for every online player when the configured interval elapses.
     *
     * @param event
     *            the end of a server tick
     */
    static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % SpellConfig.RECHARGE_INTERVAL.get() != 0) {
            return;
        }
        PlayerPowerService power = LeyLinesApi.playerPower(server);
        int units = SpellConfig.RECHARGE_UNITS.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            recharge(power, player, units);
        }
    }

    /**
     * Applies one pulse to a single player: skipped unless the player is alive and not spectating,
     * then unlocks if they have a focus, then recharges if unlocked (D77).
     *
     * @param power
     *            the server's player power service
     * @param player
     *            an online player
     * @param units
     *            positive units per pulse
     * @return the currency recharged, or null when nothing was recharged
     */
    static ResourceLocation recharge(PlayerPowerService power, ServerPlayer player, int units) {
        if (!player.isAlive() || player.isSpectator() || !unlock(player)) {
            return null;
        }
        return pulse(power, player, player.getRandom(), units);
    }

    /**
     * Unlocks recharge if the player now has a focus, and reports whether it is unlocked.
     *
     * @param player
     *            an online player
     * @return true once the player has ever had a focus
     */
    static boolean unlock(ServerPlayer player) {
        if (player.getData(UNLOCKED)) {
            return true;
        }
        boolean hasFocus = player.getInventory()
            .hasAnyMatching(stack -> stack.is(SpellContent.FOCUS.get()))
            || player.containerMenu.getCarried().is(SpellContent.FOCUS.get());
        if (hasFocus) {
            player.setData(UNLOCKED, true);
        }
        return hasFocus;
    }

    /**
     * Adds units to one randomly chosen active, non-full pool.
     *
     * @param power
     *            the server's player power service
     * @param player
     *            the recharging player
     * @param random
     *            source for the pool choice
     * @param units
     *            positive units per pulse; clamped to the chosen pool's remaining room
     * @return the currency recharged, or null when every pool was full, inactive, or the change was
     *         refused
     */
    static ResourceLocation pulse(
        PlayerPowerService power, ServerPlayer player, RandomSource random, int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("Recharge units must be positive: " + units);
        }
        var pools = power.inspect(player.getUUID()).pools();
        List<ResourceLocation> candidates = new ArrayList<>(ManaType.FLAVORS.size());
        for (ManaType element : ManaType.FLAVORS) {
            ResourceLocation currency = PlayerManaPools.currency(element);
            var pool = pools.get(currency);
            if (pool != null && pool.active() && pool.balance() < pool.settings().capacity()) {
                candidates.add(currency);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        ResourceLocation chosen = candidates.get(random.nextInt(candidates.size()));
        var pool = pools.get(chosen);
        long added = Math.min(units, pool.settings().capacity() - pool.balance());
        var result = power.adjust(
            player.getUUID(), chosen, added, PlayerManaPools.consumer(player.getUUID()));
        if (result.succeeded()) {
            return chosen;
        }
        if (result.status() == PlayerPowerService.Status.CANCELLED) {
            // Another mod vetoed this change; that is its decision, not a fault.
            LOGGER.debug("Recharge of {} for {} was cancelled", chosen,
                player.getGameProfile().getName());
        } else {
            LOGGER.error("Ley Lines could not recharge {} by {} for player {} ({}): {}",
                chosen, added, player.getGameProfile().getName(), player.getUUID(),
                result.status());
        }
        return null;
    }
}
