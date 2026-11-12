package zone.moddev.mc.technomagy.spell;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.RegisterEvent;

import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.api.Affordability;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.MasteryAwards;
import org.bitwisemadness.abilitycore.api.MasteryTarget;
import org.bitwisemadness.abilitycore.api.MasteryTrigger;
import org.bitwisemadness.abilitycore.api.MasteryView;
import org.bitwisemadness.abilitycore.api.PresentationField;
import org.bitwisemadness.abilitycore.api.PresentationProviders;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.bitwisemadness.abilitycore.api.ResourceProviders;
import org.bitwisemadness.abilityhotbar.compat.CoreMasteryCompat;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;

import zone.moddev.mc.skysleylines.api.LeyLinesApi;
import zone.moddev.mc.skysleylines.api.PlayerPowerEvent;
import zone.moddev.mc.skysleylines.api.PlayerPowerService;
import zone.moddev.mc.skysleylines.api.PowerRequest;

import zone.moddev.mc.technomagy.Technomagy;
import zone.moddev.mc.technomagy.mana.ManaType;

/**
 * Dedicated-server, actual-pipeline checks for each of the sixteen approved effects, their payment
 * (D76-D78), and their mastery attribution, shares and effects (D79-D80).
 */
@GameTestHolder("technomagy")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "technomagy", bus = EventBusSubscriber.Bus.MOD)
public final class SpellGameTests {
    private static Block receiver;
    private static final AtomicInteger RECEIVED = new AtomicInteger();

    private SpellGameTests() {
    }

    /**
     * Checks independent reserves, Raw exclusion, and reconnects after spending or configuration.
     */
    @GameTest(template = "empty")
    public static void personalManaPools(GameTestHelper helper) {
        var power = LeyLinesApi.playerPower(helper.getLevel().getServer());
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        PlayerManaPools.initialize(power, first);
        PlayerManaPools.initialize(power, second);
        var initial = power.inspect(first).pools();
        helper.assertTrue(initial.size() == 6 && !initial.containsKey(Technomagy.RAW),
            "Exactly six elemental pools must be created without Raw");
        for (var pool : initial.values()) {
            helper.assertTrue(pool.balance() == 3 && pool.settings().capacity() == 10
                && pool.settings().enabled() && pool.settings().autoRate() == null,
                "Each elemental reserve must start at 3/10 with recharge disabled");
        }
        var consumer = new PowerRequest.Consumer(Technomagy.FIRE);
        helper.assertTrue(power.spend(first, Technomagy.FIRE, 3, consumer).succeeded(),
            "Initial elemental balance must be spendable");
        helper.assertTrue(power.configure(first, Technomagy.WATER,
            new PlayerPowerService.Settings(false, 20, null), consumer).succeeded(),
            "Existing pool settings must be configurable");
        var before = power.inspect(first);
        PlayerManaPools.initialize(power, first);
        helper.assertTrue(before.equals(power.inspect(first)),
            "Rejoining must preserve empty balances and existing settings");
        helper.assertTrue(power.inspect(second).pools().equals(initial),
            "Spending and configuration must not affect another player");
        helper.succeed();
    }

    /** Registers one test-only energy receiver; it is excluded from production JARs. */
    @SubscribeEvent
    public static void block(RegisterEvent event) {
        if (!Boolean.getBoolean("neoforge.gameTestServer")) {
            return;
        }
        event.register(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("technomagy", "test_energy_receiver"),
            () -> {
                receiver = new Block(BlockBehaviour.Properties.of());
                return receiver;
            });
    }

    /** Provides a bounded receiver solely to verify life-and-death energy delivery. */
    @SubscribeEvent
    public static void capability(RegisterCapabilitiesEvent event) {
        if (receiver == null) {
            return;
        }
        event.registerBlock(
            Capabilities.EnergyStorage.BLOCK,
            (level, pos, state, entity, side) -> new IEnergyStorage() {
                @Override
                public int receiveEnergy(int maxReceive, boolean simulate) {
                    int accepted = Math.min(Math.max(0, maxReceive), 1000 - RECEIVED.get());
                    if (!simulate) {
                        RECEIVED.addAndGet(accepted);
                    }
                    return accepted;
                }

                @Override
                public int extractEnergy(int maxExtract, boolean simulate) {
                    return 0;
                }

                @Override
                public int getEnergyStored() {
                    return RECEIVED.get();
                }

                @Override
                public int getMaxEnergyStored() {
                    return 1000;
                }

                @Override
                public boolean canExtract() {
                    return false;
                }

                @Override
                public boolean canReceive() {
                    return true;
                }
            },
            receiver);
    }

    /**
     * Creates Minecraft's mock connection with the hotbar snapshot channel declared before login.
     * The stock helper skips mod channel negotiation, which makes Hotbar's real login sync fail.
     * This test-only setup retains the login event and snapshot sender rather than disabling them;
     * it does not simulate a client consuming or rendering those packets.
     */
    private static ServerPlayer connectedPlayer(GameTestHelper helper, boolean creative) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
            new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
            cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return creative;
            }
        };
        var connection = new net.minecraft.network.Connection(
            net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.ChannelAttributes
            .getOrCreateCommonChannels(connection, net.minecraft.network.ConnectionProtocol.PLAY)
            .add(ResourceLocation.fromNamespaceAndPath("ability_hotbar", "bar_state_snapshot"));
        net.neoforged.neoforge.network.registration.ChannelAttributes
            .getOrCreateCommonChannels(connection, net.minecraft.network.ConnectionProtocol.PLAY)
            .add(ManaPoolSnapshot.TYPE.id());
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    /** Creates a creative caster with a real mock-server connection and a selected held focus. */
    private static ServerPlayer player(GameTestHelper helper, PlayerSpell spell) {
        return player(helper, spell, true);
    }

    /**
     * Creates a caster with a real mock-server connection and an actually selected held focus.
     * Creative casters are never charged (D76), so payment checks use survival casters.
     */
    private static ServerPlayer player(GameTestHelper helper, PlayerSpell spell, boolean creative) {
        ServerPlayer player = connectedPlayer(helper, creative);
        Vec3 position = helper.absoluteVec(new Vec3(7.5, 1, 2.5));
        player.moveTo(position.x, position.y, position.z, 0, 0);
        ItemStack focus = new ItemStack(SpellContent.FOCUS.get());
        var bar = player.getData(BarStateAttachments.BAR_STATE);
        player.setData(BarStateAttachments.BAR_STATE,
            bar.withAssignment(bar.selectedSlot(), spell.id()));
        player.setItemInHand(InteractionHand.MAIN_HAND, focus);
        fillPools(helper, player, 10);
        return player;
    }

    /**
     * Sets all six personal pools to one balance. Effect tests start full so they pass whether or
     * not the server enables payment (D76).
     */
    private static void fillPools(GameTestHelper helper, ServerPlayer player, long units) {
        var power = LeyLinesApi.playerPower(helper.getLevel().getServer());
        PlayerManaPools.initialize(power, player.getUUID());
        for (ManaType element : ManaType.FLAVORS) {
            helper.assertTrue(power.setBalance(player.getUUID(), PlayerManaPools.currency(element),
                units, PlayerManaPools.consumer(player.getUUID())).succeeded(),
                "Could not set the test caster's " + element + " pool");
        }
    }

    /** Executes the registered Ability Core pipeline and rejects any reported refusal. */
    private static void cast(GameTestHelper helper, ServerPlayer player, PlayerSpell spell) {
        var result = AbilityCore.PIPELINE.run(
            ActivationRequest.of(spell.id(), ActivationSource.NATIVE_ADAPTER), player);
        helper.assertTrue(result.isSuccess(), "Cast refused: " + spell + " / " + result);
    }

    /** Creates a stationary living target at the caster's server-computed eye ray. */
    private static Husk target(GameTestHelper helper) {
        Husk entity = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(7.5, 1, 5.5));
        entity.setInvulnerable(false);
        return entity;
    }

    /** Erode consults the installed MMDLib cobblestone crushing recipe. */
    @GameTest(template = "empty")
    public static void erode(GameTestHelper h) {
        h.setBlock(7, 2, 5, Blocks.COBBLESTONE);
        cast(h, player(h, PlayerSpell.ERODE), PlayerSpell.ERODE);
        h.assertBlockNotPresent(Blocks.COBBLESTONE, 7, 2, 5);
        h.assertItemEntityPresent(net.minecraft.world.item.Items.GRAVEL, new BlockPos(7, 2, 5), 2);
        h.succeed();
    }

    /** Forge replaces sand with its live smelting result, glass. */
    @GameTest(template = "empty")
    public static void forge(GameTestHelper h) {
        h.setBlock(7, 1, 5, Blocks.STONE);
        h.setBlock(7, 2, 5, Blocks.SAND);
        cast(h, player(h, PlayerSpell.FORGE), PlayerSpell.FORGE);
        h.assertBlockPresent(Blocks.GLASS, 7, 2, 5);
        h.succeed();
    }

    /** Lightning deals actual magical damage rather than only producing particles. */
    @GameTest(template = "empty")
    public static void lightning(GameTestHelper h) {
        Husk entity = target(h);
        float before = entity.getHealth();
        cast(h, player(h, PlayerSpell.LIGHTNING), PlayerSpell.LIGHTNING);
        h.assertTrue(entity.getHealth() < before, "Lightning did no damage");
        h.succeed();
    }

    /** Steam changes velocity without teleporting the target. */
    @GameTest(template = "empty")
    public static void steam(GameTestHelper h) {
        Husk entity = target(h);
        cast(h, player(h, PlayerSpell.STEAM), PlayerSpell.STEAM);
        h.assertTrue(entity.getDeltaMovement().lengthSqr() > 0, "Steam produced no impulse");
        h.succeed();
    }

    /** Mist applies an actual obscuring effect to field occupants. */
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void mist(GameTestHelper h) {
        Husk entity = target(h);
        cast(h, player(h, PlayerSpell.MIST), PlayerSpell.MIST);
        h.succeedWhen(
            () -> h.assertTrue(
                entity.hasEffect(MobEffects.BLINDNESS),
                "Mist did not obscure occupant"));
    }

    /** Constraint applies its approved movement penalty. */
    @GameTest(template = "empty")
    public static void constraint(GameTestHelper h) {
        Husk entity = target(h);
        cast(h, player(h, PlayerSpell.CONSTRAINT), PlayerSpell.CONSTRAINT);
        h.assertTrue(entity.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Constraint missing");
        h.succeed();
    }

    /** Ward absorbs incoming damage without altering the target's health pool or armor. */
    @GameTest(template = "empty")
    public static void ward(GameTestHelper h) {
        Husk entity = target(h);
        cast(h, player(h, PlayerSpell.WARD), PlayerSpell.WARD);
        float health = entity.getHealth();
        entity.hurt(h.getLevel().damageSources().magic(), 4);
        h.assertTrue(entity.getHealth() == health, "Ward did not absorb damage");
        h.succeed();
    }

    /** Entomb places a shell without suffocating or replacing the target's occupied cell. */
    @GameTest(template = "empty", timeoutTicks = 130)
    public static void entomb(GameTestHelper h) {
        Husk entity = target(h);
        cast(h, player(h, PlayerSpell.ENTOMB), PlayerSpell.ENTOMB);
        BlockPos wall = new BlockPos(6, 1, 5);
        h.assertBlockPresent(SpellContent.EARTH.get(), wall);
        h.assertTrue(
            !h.getLevel().getBlockState(entity.blockPosition()).is(SpellContent.EARTH.get()),
            "Shell intersects target body");
        h.runAfterDelay(
            110,
            () -> {
                h.assertBlockNotPresent(SpellContent.EARTH.get(), wall);
                h.succeed();
            });
    }

    /** Reveal outlines an invisible entity rather than depending on client visibility. */
    @GameTest(template = "empty")
    public static void reveal(GameTestHelper h) {
        Husk entity = target(h);
        entity.setInvisible(true);
        cast(h, player(h, PlayerSpell.REVEAL), PlayerSpell.REVEAL);
        h.assertTrue(entity.hasEffect(MobEffects.GLOWING), "Reveal missing");
        h.succeed();
    }

    /** Shroud is applied and breaks on attacking. */
    @GameTest(template = "empty")
    public static void shroud(GameTestHelper h) {
        Husk entity = target(h);
        cast(h, player(h, PlayerSpell.SHROUD), PlayerSpell.SHROUD);
        h.assertTrue(entity.hasEffect(SpellContent.SHROUD), "Shroud missing");
        entity.hurt(h.getLevel().damageSources().mobAttack(entity), 1);
        h.assertTrue(!entity.hasEffect(SpellContent.SHROUD), "Attack did not break Shroud");
        h.succeed();
    }

    /** Radiance emits real block light and its scheduled expiry removes the temporary block. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void radiance(GameTestHelper h) {
        h.setBlock(7, 2, 5, Blocks.STONE);
        cast(h, player(h, PlayerSpell.RADIANCE), PlayerSpell.RADIANCE);
        h.assertBlockPresent(SpellContent.LIGHT.get(), 7, 2, 4);
        h.runAfterDelay(
            210,
            () -> {
                h.assertBlockNotPresent(SpellContent.LIGHT.get(), 7, 2, 4);
                h.succeed();
            });
    }

    /** Blight applies the approved finite withering effect. */
    @GameTest(template = "empty")
    public static void blight(GameTestHelper h) {
        Husk entity = target(h);
        cast(h, player(h, PlayerSpell.BLIGHT), PlayerSpell.BLIGHT);
        h.assertTrue(entity.hasEffect(MobEffects.WITHER), "Blight missing");
        h.succeed();
    }

    /** Mend restores actual health and does not invent free tool repair. */
    @GameTest(template = "empty")
    public static void mend(GameTestHelper h) {
        Husk entity = target(h);
        entity.setHealth(10);
        cast(h, player(h, PlayerSpell.MEND), PlayerSpell.MEND);
        h.assertTrue(entity.getHealth() == 14, "Mend health mismatch");
        h.succeed();
    }

    /** Leach's attribute modifier reduces armor and is removed with the status effect. */
    @GameTest(template = "empty")
    public static void leach(GameTestHelper h) {
        Husk entity = target(h);
        double armor = entity
            .getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        cast(h, player(h, PlayerSpell.LEACH), PlayerSpell.LEACH);
        h.assertTrue(
            entity.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) < armor,
            "Leach did not reduce armor");
        entity.removeEffect(SpellContent.LEACH);
        h.assertTrue(
            entity.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) == armor,
            "Leach left a permanent attribute modifier");
        h.succeed();
    }

    /** Growth advances a planted, lit field through the shared heuristic's multiplier. */
    @GameTest(template = "empty")
    public static void growth(GameTestHelper h) {
        for (BlockPos pos : BlockPos.betweenClosed(3, 1, 3, 11, 1, 11)) {
            h.setBlock(
                pos.below(),
                Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            h.setBlock(pos, Blocks.WHEAT);
        }
        ServerPlayer player = player(h, PlayerSpell.GROWTH);
        Vec3 position = h.absoluteVec(new Vec3(7.5, 1, 2.5));
        player.moveTo(position.x, position.y, position.z, 0, 35);
        h.getLevel().random.setSeed(12345);
        cast(h, player, PlayerSpell.GROWTH);
        boolean advanced = false;
        for (BlockPos pos : BlockPos.betweenClosed(3, 1, 3, 11, 1, 11)) {
            advanced |= h.getBlockState(pos).getValue(CropBlock.AGE) > 0;
        }
        h.assertTrue(advanced, "Growth advanced no plants");
        h.succeed();
    }

    /** Life-and-death delivers energy through an actual sided FE capability. */
    @GameTest(template = "empty")
    public static void lifeAndDeath(GameTestHelper h) {
        RECEIVED.set(0);
        h.setBlock(7, 2, 5, receiver);
        cast(h, player(h, PlayerSpell.LIFE_AND_DEATH), PlayerSpell.LIFE_AND_DEATH);
        h.assertTrue(RECEIVED.get() == 100, "Energy delivery mismatch");
        h.succeed();
    }

    /** A chest is refused rather than losing its inventory to processing. */
    @GameTest(template = "empty")
    public static void inventoryProtected(GameTestHelper h) {
        h.setBlock(7, 2, 5, Blocks.CHEST);
        ServerPlayer player = player(h, PlayerSpell.ERODE);
        var result = AbilityCore.PIPELINE.run(
            ActivationRequest.of(
                PlayerSpell.ERODE.id(), ActivationSource.NATIVE_ADAPTER),
            player);
        h.assertTrue(result.isRefusal(), "Inventory-bearing block was not refused");
        h.assertBlockPresent(Blocks.CHEST, 7, 2, 5);
        h.succeed();
    }

    /** A request without an actually held focus cannot execute an ability (D74). */
    @GameTest(template = "empty")
    public static void selectionValidated(GameTestHelper h) {
        ServerPlayer player = player(h, PlayerSpell.ERODE);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var result = AbilityCore.PIPELINE.run(
            ActivationRequest.of(
                PlayerSpell.FORGE.id(), ActivationSource.NATIVE_ADAPTER),
            player);
        h.assertTrue(result.isRefusal(), "Spell without a held focus executed");
        h.succeed();
    }

    /** Right-click uses saved hotbar selection and refuses empty or foreign slots. */
    @GameTest(template = "empty")
    public static void hotbarFocusCasting(GameTestHelper h) {
        var player = player(h, PlayerSpell.MEND);
        player.setHealth(10);
        SpellContent.castSelected(player, InteractionHand.MAIN_HAND);
        h.assertTrue(player.getHealth() > 10, "Focus did not cast hotbar-selected Mend");
        var bar = player.getData(BarStateAttachments.BAR_STATE);
        player.setData(BarStateAttachments.BAR_STATE, bar.withAssignment(bar.selectedSlot(), null));
        player.setHealth(10);
        SpellContent.castSelected(player, InteractionHand.MAIN_HAND);
        h.assertTrue(player.getHealth() == 10, "Empty slot reused a previous spell");
        player.setData(BarStateAttachments.BAR_STATE,
            bar.withAssignment(bar.selectedSlot(), Technomagy.RAW));
        SpellContent.castSelected(player, InteractionHand.MAIN_HAND);
        h.assertTrue(player.getHealth() == 10, "Unknown slot reused a previous spell");
        var directBar = bar.withAssignment(1, PlayerSpell.MEND.id());
        player.setData(BarStateAttachments.BAR_STATE, directBar);
        var payload = new org.bitwisemadness.abilitycore.sync.ActivateAbilityPayload(
            PlayerSpell.MEND.id().toString(), ActivationSource.DIRECT_SLOT.wireName(),
            1, 0L, "", "");
        var direct = AbilityCore.PIPELINE.run(
            org.bitwisemadness.abilitycore.sync.ActivateAbilityPayload.toRequest(payload), player);
        h.assertTrue(direct.isSuccess() && player.getHealth() > 10,
            "Hotbar direct-slot activation did not execute through Core");
        player.setHealth(10);
        player.setData(BarStateAttachments.BAR_STATE, bar);
        var focus = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, focus);
        SpellContent.castSelected(player, InteractionHand.OFF_HAND);
        h.assertTrue(player.getHealth() > 10, "Offhand focus did not cast the hotbar selection");
        h.succeed();
    }

    /** Checks bounded HUD packet round trips and truthful zero/over-capacity pool display data. */
    @GameTest(template = "empty")
    public static void personalPoolSnapshot(GameTestHelper h) {
        var power = LeyLinesApi.playerPower(h.getLevel().getServer());
        var owner = UUID.randomUUID();
        PlayerManaPools.initialize(power, owner);
        var initial = ManaPoolSnapshot.from(power.inspect(owner));
        h.assertTrue(initial.bars().size() == 6 && initial.bars().stream()
            .allMatch(bar -> bar.balance() == 3 && bar.capacity() == 10 && bar.active()),
            "HUD did not project six personal elemental reserves");
        var consumer = new PowerRequest.Consumer(Technomagy.FIRE);
        h.assertTrue(power.configure(owner, Technomagy.FIRE,
            new PlayerPowerService.Settings(false, 0, null), consumer).succeeded(),
            "Could not disable the test pool");
        var snapshot = ManaPoolSnapshot.from(power.inspect(owner));
        var fire = snapshot.bars().get(ManaType.FLAVORS.indexOf(ManaType.FIRE));
        h.assertTrue(!fire.active() && fire.capacity() == 0 && fire.balance() == 3,
            "HUD must preserve over-capacity balances for disabled pools");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ManaPoolSnapshot.CODEC.encode(buffer, snapshot);
            h.assertTrue(ManaPoolSnapshot.CODEC.decode(buffer).equals(snapshot),
                "HUD snapshot changed during network round trip");
        } finally {
            buffer.release();
        }
        h.assertTrue(ManaPoolSnapshot.from(power.inspect(UUID.randomUUID())).bars().stream()
            .noneMatch(ManaPoolSnapshot.Bar::configured), "Missing pools appeared configured");
        h.succeed();
    }

    /** Focus data is per-stack and supports a deliberately empty initial selection. */
    @GameTest(template = "empty")
    public static void focusSelection(GameTestHelper h) {
        ItemStack first = new ItemStack(SpellContent.FOCUS.get());
        ItemStack second = first.copy();
        h.assertTrue(SpellFocusItem.selectedElement(first).isEmpty(), "New focus guessed a spell");
        SpellFocusItem.selectElement(first, ManaType.RAW);
        h.assertTrue(
            SpellFocusItem.selectedElement(first).orElseThrow() == ManaType.RAW,
            "Selection did not persist");
        h.assertTrue(SpellFocusItem.selectedElement(second).isEmpty(),
            "Selections leaked between stacks");
        h.succeed();
    }

    /** The actual datapack recipe accepts every crystal color in both mirrored orientations. */
    @GameTest(template = "empty")
    public static void focusRecipe(GameTestHelper h) {
        String[] colors = {"earth", "air", "fire", "water", "light", "dark"};
        for (int color = 0; color < colors.length; color++) {
            var first = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(
                    "technomagy", colors[color] + "_crystal"));
            var second = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(
                    "technomagy",
                    colors[(color + 1) % colors.length] + "_crystal"));
            for (boolean mirrored : new boolean[]{false, true}) {
                var slots = net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY);
                slots.set(mirrored ? 2 : 0, new ItemStack(net.minecraft.world.item.Items.STICK));
                slots.set(4, new ItemStack(net.minecraft.world.item.Items.STICK));
                slots.set(mirrored ? 6 : 8, new ItemStack(net.minecraft.world.item.Items.STICK));
                slots.set(1, new ItemStack(first));
                slots.set(7, new ItemStack(second));
                var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, slots);
                var recipe = h.getLevel()
                    .getRecipeManager()
                    .getRecipeFor(
                        net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                        input,
                        h.getLevel());
                h.assertTrue(
                    recipe.isPresent(),
                    "Focus recipe missing for " + colors[color] + " mirror=" + mirrored);
                h.assertTrue(
                    recipe.orElseThrow()
                        .value()
                        .assemble(input, h.getLevel().registryAccess())
                        .is(SpellContent.FOCUS.get()),
                    "Wrong focus recipe output");
            }
        }
        h.succeed();
    }

    /** Steam follows the knockback protection path and cannot push through an active Ward. */
    @GameTest(template = "empty")
    public static void wardStopsSteam(GameTestHelper h) {
        Husk entity = target(h);
        ServerPlayer player = player(h, PlayerSpell.WARD);
        cast(h, player, PlayerSpell.WARD);
        var bar = player.getData(BarStateAttachments.BAR_STATE);
        player.setData(BarStateAttachments.BAR_STATE,
            bar.withAssignment(bar.selectedSlot(), PlayerSpell.STEAM.id()));
        Vec3 before = entity.getDeltaMovement();
        var result = AbilityCore.PIPELINE.run(
            ActivationRequest.of(
                PlayerSpell.STEAM.id(), ActivationSource.NATIVE_ADAPTER),
            player);
        h.assertTrue(result.isRefusal(), "Steam bypassed the Ward");
        h.assertTrue(entity.getDeltaMovement().equals(before), "Ward allowed a knockback impulse");
        h.succeed();
    }

    /**
     * Ordinary break-protection listeners can veto processing without losing the block or drops.
     */
    @GameTest(template = "empty")
    public static void protectionVeto(GameTestHelper h) {
        h.setBlock(7, 2, 5, Blocks.COBBLESTONE);
        BlockPos absolute = h.absolutePos(new BlockPos(7, 2, 5));
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> listener = event -> {
            if (event.getLevel() == h.getLevel()
                && event.getPos().equals(absolute)) {
                event.setCanceled(true);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            net.neoforged.neoforge.event.level.BlockEvent.BreakEvent.class, listener);
        try {
            ServerPlayer player = player(h, PlayerSpell.ERODE);
            var result = AbilityCore.PIPELINE.run(
                ActivationRequest.of(
                    PlayerSpell.ERODE.id(), ActivationSource.NATIVE_ADAPTER),
                player);
            h.assertTrue(result.isRefusal(), "Protection veto ignored");
            h.assertBlockPresent(Blocks.COBBLESTONE, 7, 2, 5);
            h.assertItemEntityNotPresent(net.minecraft.world.item.Items.GRAVEL);
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
        }
        h.succeed();
    }

    /**
     * A normal focus block-use call reaches Forge through Ability Core before block interaction.
     */
    @GameTest(template = "empty")
    public static void focusUseOn(GameTestHelper h) {
        h.setBlock(7, 1, 5, Blocks.STONE);
        h.setBlock(7, 2, 5, Blocks.SAND);
        ServerPlayer player = player(h, PlayerSpell.FORGE);
        var context = new net.minecraft.world.item.context.UseOnContext(
            player,
            InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(
                h.absoluteVec(new Vec3(7.5, 2.5, 5)),
                net.minecraft.core.Direction.NORTH,
                h.absolutePos(new BlockPos(7, 2, 5)),
                false));
        var result = SpellContent.FOCUS.get().onItemUseFirst(player.getMainHandItem(), context);
        h.assertTrue(result.consumesAction(), "Focus allowed an unrelated block interaction");
        h.assertBlockPresent(Blocks.GLASS, 7, 2, 5);
        h.succeed();
    }

    /** Air-use reaches the native activation pipeline and applies a self-targeted Mend. */
    @GameTest(template = "empty")
    public static void focusAirUse(GameTestHelper h) {
        ServerPlayer player = player(h, PlayerSpell.MEND);
        player.setHealth(10);
        SpellContent.FOCUS.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(player.getHealth() == 14, "Focus air-use did not cast Mend");
        h.succeed();
    }

    /** Payment charges Mend's stream-derived default, then a configured override (D70). */
    @GameTest(template = "empty")
    public static void paymentChargesConfiguredCost(GameTestHelper h) {
        ServerPlayer player = paidPlayer(h);
        withPayment(() -> {
            player.setHealth(10);
            cast(h, player, PlayerSpell.MEND);
            assertBalances(h, player, "Default Mend cost must be one Water and one Light",
                3, 3, 3, 2, 2, 3);
            var earth = SpellConfig.COSTS.get(PlayerSpell.MEND).get(ManaType.EARTH);
            int previous = earth.get();
            earth.set(2);
            try {
                player.setHealth(10);
                cast(h, player, PlayerSpell.MEND);
            } finally {
                earth.set(previous);
            }
            assertBalances(h, player, "Configured Earth cost was not charged",
                3, 1, 3, 1, 1, 3);
        });
        h.succeed();
    }

    /** Shortfalls, fractional prices, and invalid targets are refused before any pool changes. */
    @GameTest(template = "empty")
    public static void paymentRefusesShortfall(GameTestHelper h) {
        ServerPlayer player = paidPlayer(h);
        var power = LeyLinesApi.playerPower(h.getLevel().getServer());
        h.assertTrue(power.setBalance(player.getUUID(), Technomagy.LIGHT, 0,
            new PowerRequest.Consumer(Technomagy.LIGHT)).succeeded(), "Could not drain Light");
        withPayment(() -> {
            player.setHealth(10);
            var result = run(player, PlayerSpell.MEND);
            h.assertTrue(result.outcome() == ActivationOutcome.REFUSED_UNAFFORDABLE,
                "Unaffordable Mend was not refused as unaffordable: " + result);
            h.assertTrue(player.getHealth() == 10, "Unaffordable Mend still healed");
            assertBalances(h, player, "A refused shortfall changed a pool", 3, 3, 3, 3, 0, 3);
            // A fractional price cannot be charged in whole units: the provider must fail it
            // closed as UNKNOWN before the handler, never approve it for units() to zero out.
            var fractional = ResourceProviders.get(SpellResources.resourceId(ManaType.WATER))
                .orElseThrow()
                .check(PlayerSpell.MEND.id(), player, 0.5);
            h.assertTrue(fractional.state() == Affordability.State.UNKNOWN,
                "A fractional price must fail closed as UNKNOWN, got " + fractional.state());
            player.setHealth(player.getMaxHealth());
            var invalid = run(player, PlayerSpell.SHROUD);
            h.assertTrue(invalid.isSuccess(), "Affordable Shroud was refused: " + invalid);
            // Restore Light so the next cast is affordable: Ability Core's RESOURCE phase answers
            // before the handler runs (D78), so a drained pool would otherwise mask the handler's
            // own invalid-target refusal with REFUSED_UNAFFORDABLE.
            h.assertTrue(power.setBalance(player.getUUID(), Technomagy.LIGHT, 3,
                new PowerRequest.Consumer(Technomagy.LIGHT)).succeeded(),
                "Could not restore Light");
            var full = run(player, PlayerSpell.MEND);
            h.assertTrue(full.outcome() == ActivationOutcome.REFUSED_INVALID_TARGET,
                "Mend at full health was not an invalid target: " + full);
            assertBalances(h, player, "Only the successful Shroud may be charged",
                3, 3, 2, 3, 3, 2);
        });
        h.succeed();
    }

    /**
     * A spend vetoed after the effect has acted cannot be rolled back: the cast stands and only the
     * vetoed pool goes unpaid (D78's accepted post-activation charge semantics).
     */
    @GameTest(template = "empty")
    public static void paymentCastStandsWhenSpendVetoed(GameTestHelper h) {
        ServerPlayer player = paidPlayer(h);
        withPayment(() -> {
            player.setHealth(10);
            PaymentVetoes.LIGHT_SPEND.set(player.getUUID());
            ActivationResultHolder holder = new ActivationResultHolder();
            try {
                holder.result = run(player, PlayerSpell.MEND);
            } finally {
                PaymentVetoes.LIGHT_SPEND.set(null);
            }
            h.assertTrue(holder.result.isSuccess(),
                "Vetoed payment refused a cast that had already acted: " + holder.result);
            h.assertTrue(player.getHealth() > 10, "Vetoed payment undid the heal");
            assertBalances(h, player, "Water must be charged and the vetoed Light left alone",
                3, 3, 3, 2, 3, 3);
        });
        h.succeed();
    }

    /**
     * A validated effect that does nothing at application time never reaches Ability Core's
     * post-activation charge, so no pool changes (D78).
     */
    @GameTest(template = "empty")
    public static void paymentRefundsIneffectiveCast(GameTestHelper h) {
        ServerPlayer player = paidPlayer(h);
        withPayment(() -> {
            player.setHealth(10);
            PaymentVetoes.HEAL.set(player.getUUID());
            ActivationResultHolder holder = new ActivationResultHolder();
            try {
                holder.result = run(player, PlayerSpell.MEND);
            } finally {
                PaymentVetoes.HEAL.set(null);
            }
            h.assertTrue(holder.result.outcome() == ActivationOutcome.REFUSED_INVALID_TARGET,
                "Cancelled heal was not refused: " + holder.result);
            h.assertTrue(player.getHealth() == 10, "Cancelled heal changed health");
            assertBalances(h, player, "An ineffective Mend changed a pool", 3, 3, 3, 3, 3, 3);
        });
        h.succeed();
    }

    /**
     * A spell configured to cost nothing is refused rather than cast for free; a declared list that
     * drops or duplicates a pool id, or a foreign ability naming the resolver, is rejected
     * fail-closed instead of priced at a quietly different list (D78).
     */
    @GameTest(template = "empty")
    public static void paymentRefusesZeroCost(GameTestHelper h) {
        ServerPlayer player = paidPlayer(h);
        var costs = SpellConfig.COSTS.get(PlayerSpell.MEND);
        var previous = new java.util.EnumMap<ManaType, Integer>(ManaType.class);
        costs.forEach((element, value) -> previous.put(element, value.get()));
        withPayment(() -> {
            costs.values().forEach(value -> value.set(0));
            ActivationResultHolder holder = new ActivationResultHolder();
            try {
                player.setHealth(10);
                holder.result = run(player, PlayerSpell.MEND);
            } finally {
                previous.forEach((element, units) -> costs.get(element).set(units));
            }
            h.assertTrue(holder.result.outcome() == ActivationOutcome.REFUSED_UNAVAILABLE,
                "Zero-cost Mend was not refused: " + holder.result);
            h.assertTrue(player.getHealth() == 10, "Zero-cost Mend still healed");
            assertBalances(h, player, "Zero-cost refusal changed a pool", 3, 3, 3, 3, 3, 3);
        });
        var resolver = AmountResolvers.get(SpellResources.COST_RESOLVER).orElseThrow();
        var request = ActivationRequest.of(PlayerSpell.MEND.id(), ActivationSource.DIRECT_SLOT);
        var water = new ResourceCost(SpellResources.resourceId(ManaType.WATER), 1.0);
        var light = new ResourceCost(SpellResources.resourceId(ManaType.LIGHT), 1.0);
        assertResolverRejected(h, resolver, List.of(water, light), request, player,
            "A declared list missing pool ids must be rejected");
        var duplicated = new java.util.ArrayList<ResourceCost>();
        duplicated.add(water);
        duplicated.add(water);
        for (ManaType flavor : ManaType.FLAVORS) {
            if (flavor != ManaType.WATER && flavor != ManaType.DARK) {
                duplicated.add(new ResourceCost(SpellResources.resourceId(flavor), 1.0));
            }
        }
        assertResolverRejected(h, resolver, duplicated, request, player,
            "A duplicated pool id must be rejected");
        var foreign = ActivationRequest.of(
            ResourceLocation.fromNamespaceAndPath("othermod", "spell"),
            ActivationSource.DIRECT_SLOT);
        assertResolverRejected(h, resolver, List.of(water, light), foreign, player,
            "A foreign ability naming the resolver must be rejected");
        var six = new java.util.ArrayList<ResourceCost>();
        for (ManaType flavor : ManaType.FLAVORS) {
            six.add(new ResourceCost(SpellResources.resourceId(flavor), 1.0));
        }
        h.assertTrue(resolver.resolve(six, request, player, null).size() == six.size(),
            "A complete six-pool list must price");
        h.succeed();
    }

    /**
     * Asserts one resolver input is rejected fail-closed with an {@link IllegalArgumentException}.
     */
    private static void assertResolverRejected(GameTestHelper h,
        AmountResolvers.AmountResolver resolver, List<ResourceCost> declared,
        ActivationRequest request, ServerPlayer player, String message) {
        boolean threw = false;
        try {
            resolver.resolve(declared, request, player, null);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        h.assertTrue(threw, message);
    }

    /** Creative casters cast without being charged (D76). */
    @GameTest(template = "empty")
    public static void paymentSkipsCreative(GameTestHelper h) {
        ServerPlayer player = player(h, PlayerSpell.MEND, true);
        fillPools(h, player, 3);
        withPayment(() -> {
            player.setHealth(10);
            cast(h, player, PlayerSpell.MEND);
            h.assertTrue(player.getHealth() > 10, "Creative Mend did not heal");
            assertBalances(h, player, "A creative caster was charged", 3, 3, 3, 3, 3, 3);
        });
        h.succeed();
    }

    /**
     * A successful cast grants effort priced from the spell's configured cost (D79), split by the
     * D80 shares so the spell's own target keeps the 45% remainder.
     */
    @GameTest(template = "empty")
    public static void masteryActivationGain(GameTestHelper h) {
        ServerPlayer caster = player(h, PlayerSpell.MEND);
        caster.setHealth(10);
        cast(h, caster, PlayerSpell.MEND);
        assertOwnXp(h, caster, PlayerSpell.MEND,
            "Mend's 2-unit activation must keep its 45% self-share", 0.9);
        h.succeed();
    }

    /** Lightning's bolt grants HIT, and KILL when it brings the target down (D79). */
    @GameTest(template = "empty")
    public static void masteryLightningHitAndKill(GameTestHelper h) {
        Husk struck = target(h);
        ServerPlayer caster = player(h, PlayerSpell.LIGHTNING);
        cast(h, caster, PlayerSpell.LIGHTNING);
        assertOwnXp(h, caster, PlayerSpell.LIGHTNING,
            "A damaging strike must keep 45% of activation (2) plus HIT (1)", 1.35);
        // A freshly hurt target is inside vanilla's damage cooldown, so the killing strike
        // lands on a new one-HP husk instead of the same-tick second hit being refused.
        struck.discard();
        Husk dying = target(h);
        dying.setHealth(1.0F);
        cast(h, caster, PlayerSpell.LIGHTNING);
        assertOwnXp(h, caster, PlayerSpell.LIGHTNING,
            "A killing strike must keep 45% of activation (2) plus KILL (2)", 3.15);
        h.succeed();
    }

    /**
     * A ward's absorbed damage credits the casting player's mastery even though the ward protects
     * another entity (D79's caster ownership) - raw absorption routes like any other grant (D80) -
     * and only for damage that applies: a hit vanilla discards inside its invulnerability frames
     * drains the pool without any credit, and the ward's own caster earns nothing from hitting what
     * they warded (review of D79).
     */
    @GameTest(template = "empty")
    public static void masteryWardAbsorption(GameTestHelper h) {
        Husk husk = target(h);
        ServerPlayer caster = player(h, PlayerSpell.WARD);
        // A landed hit puts the husk into vanilla's invulnerability frames (lastHurt 4).
        h.assertTrue(husk.hurt(h.getLevel().damageSources().magic(), 4), "First hit must land");
        float health = husk.getHealth();
        cast(h, caster, PlayerSpell.WARD);
        assertOwnXp(h, caster, PlayerSpell.WARD,
            "Ward's 2-unit activation must keep its 45% self-share", 0.9);
        // A same-size hit inside those frames is discarded by vanilla: the ward's pool is spent
        // on nothing, so no mastery may be credited for it.
        h.assertTrue(!husk.hurt(h.getLevel().damageSources().magic(), 4),
            "A same hit inside the frames must be discarded");
        h.assertTrue(husk.getHealth() == health, "The discarded hit changed health");
        assertOwnXp(h, caster, PlayerSpell.WARD,
            "Damage vanilla discarded must not be credited", 0.9);
        // Frames over: the same hit lands and the ward absorbs it whole (the discarded hit above
        // already spent half the pool, so this one empties it).
        husk.invulnerableTime = 0;
        h.assertTrue(!husk.hurt(h.getLevel().damageSources().magic(), 4),
            "Ward did not absorb damage");
        h.assertTrue(husk.getHealth() == health, "Absorbed damage changed health");
        assertOwnXp(h, caster, PlayerSpell.WARD,
            "Absorbing 4 damage must add its 45% share to the ward's caster", 2.7);
        // The ward's own caster farming their warded target earns nothing: a fresh ward (the
        // probes above spent the first pool) takes the hit without any credit.
        cast(h, caster, PlayerSpell.WARD);
        double beforeSelf = MasteryView.of(caster, PlayerSpell.WARD.id()).ownXp();
        h.assertTrue(!husk.hurt(h.getLevel().damageSources().playerAttack(caster), 4),
            "The caster's own hit must still be absorbed");
        assertOwnXp(h, caster, PlayerSpell.WARD,
            "The ward's own caster must not earn from their warded target", beforeSelf);
        h.succeed();
    }

    /** Core ignores an award for a trigger the spell's spec did not declare (D79). */
    @GameTest(template = "empty")
    public static void masteryIgnoresUndeclaredTrigger(GameTestHelper h) {
        ServerPlayer caster = player(h, PlayerSpell.MEND);
        MasteryAwards.award(caster, PlayerSpell.MEND.id(), MasteryTrigger.KILL);
        h.assertTrue(MasteryView.of(caster, PlayerSpell.MEND.id()).ownXp() == 0.0,
            "An undeclared KILL trigger granted XP");
        MasteryAwards.award(caster, PlayerSpell.MEND.id(), MasteryTrigger.ACTIVATION, 5.0);
        assertOwnXp(h, caster, PlayerSpell.MEND,
            "A declared raw ACTIVATION must keep its 45% self-share", 2.25);
        h.succeed();
    }

    /**
     * Every grant splits 40% across the spell's element buckets, 15% to its discipline bucket and
     * the remainder to the spell - at the declared fractions and the shipped level curve (D80).
     */
    @GameTest(template = "empty")
    public static void masteryShareRouting(GameTestHelper h) {
        ServerPlayer caster = player(h, PlayerSpell.LIGHTNING);
        MasteryAwards.award(caster, PlayerSpell.LIGHTNING.id(), MasteryTrigger.ACTIVATION, 500.0);
        assertOwnXp(h, caster, PlayerSpell.LIGHTNING, "Self must keep the 45% remainder", 225.0);
        MasteryView lightning = MasteryView.of(caster, PlayerSpell.LIGHTNING.id());
        h.assertTrue(lightning.ownLevel() == 2,
            "225 self XP must reach level 2 on the shipped table, got " + lightning.ownLevel());
        assertShare(h, lightning, "SUBCATEGORY|technomagy:fire", 0.2, 1);
        assertShare(h, lightning, "SUBCATEGORY|technomagy:air", 0.2, 1);
        assertShare(h, lightning, "SUBCATEGORY|technomagy:striking", 0.15, 1);
        MasteryAwards.award(caster, PlayerSpell.GROWTH.id(), MasteryTrigger.ACTIVATION, 300.0);
        assertOwnXp(h, caster, PlayerSpell.GROWTH, "Growth self must keep 45% of 300", 135.0);
        MasteryView growth = MasteryView.of(caster, PlayerSpell.GROWTH.id());
        double third = 0.4 / 3;
        assertShare(h, growth, "SUBCATEGORY|technomagy:water", third, 0);
        assertShare(h, growth, "SUBCATEGORY|technomagy:light", third, 0);
        assertShare(h, growth, "SUBCATEGORY|technomagy:earth", third, 0);
        assertShare(h, growth, "SUBCATEGORY|technomagy:shaping", 0.15, 0);
        h.succeed();
    }

    /**
     * Mend's heal scales with the POWER multiplier over the aggregate level on core's default curve
     * - asymptotic toward +50%, x1.030055 at the pinned aggregate 6.2 (D80).
     */
    @GameTest(template = "empty")
    public static void masteryPowerScalesHeal(GameTestHelper h) {
        ServerPlayer caster = player(h, PlayerSpell.MEND);
        capMastery(caster, PlayerSpell.MEND);
        caster.setHealth(10);
        cast(h, caster, PlayerSpell.MEND);
        double aggregate = 4 + 0.2 * 4 + 0.2 * 4 + 0.15 * 4;
        double expected = 10
            + SpellConfig.HEAL.get() * (1 + 0.5 * (1 - Math.exp(-0.01 * aggregate)));
        h.assertTrue(Math.abs(caster.getHealth() - expected) < 1.0e-3,
            "Mend must heal to " + expected + " at aggregate " + aggregate + ", got "
                + caster.getHealth());
        h.succeed();
    }

    /**
     * Constraint's slowdown scales its duration by the DURATION multiplier over the aggregate
     * level, rounded to the nearest half-up tick - D80's integer-sink rule.
     */
    @GameTest(template = "empty")
    public static void masteryDurationScalesEffect(GameTestHelper h) {
        Husk husk = target(h);
        ServerPlayer caster = player(h, PlayerSpell.CONSTRAINT);
        capMastery(caster, PlayerSpell.CONSTRAINT);
        cast(h, caster, PlayerSpell.CONSTRAINT);
        var effect = husk.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        h.assertTrue(effect != null, "Constraint did not slow the target");
        double aggregate = 4 + 0.2 * 4 + 0.2 * 4 + 0.15 * 4;
        int expected = (int) Math.round(
            SpellConfig.DURATION.get()
                * (1 + 0.5 * (1 - Math.exp(-0.01 * aggregate))));
        h.assertTrue(effect.getDuration() == expected,
            "Slowdown must last " + expected + " scaled ticks, got " + effect.getDuration());
        h.succeed();
    }

    /**
     * The shipped level table owns the curve: cumulative thresholds 50/125/250/425 with a cap at
     * level 4, overriding core's config formula for every target (D80).
     */
    @GameTest(template = "empty")
    public static void masteryLevelsCurve(GameTestHelper h) {
        ServerPlayer caster = player(h, PlayerSpell.MEND);
        MasteryTarget self = MasteryTarget.ability(PlayerSpell.MEND.id());
        MasteryAwards.awardTo(caster, self, 49.0);
        assertOwnLevel(h, caster, PlayerSpell.MEND, 0, "Below 50 XP must stay level 0");
        MasteryAwards.awardTo(caster, self, 1.0);
        assertOwnLevel(h, caster, PlayerSpell.MEND, 1, "50 XP must reach level 1");
        MasteryAwards.awardTo(caster, self, 75.0);
        assertOwnLevel(h, caster, PlayerSpell.MEND, 2, "125 XP must reach level 2");
        MasteryAwards.awardTo(caster, self, 125.0);
        assertOwnLevel(h, caster, PlayerSpell.MEND, 3, "250 XP must reach level 3");
        MasteryAwards.awardTo(caster, self, 175.0);
        assertOwnLevel(h, caster, PlayerSpell.MEND, 4, "425 XP must reach level 4");
        MasteryAwards.awardTo(caster, self, 5000.0);
        assertOwnLevel(h, caster, PlayerSpell.MEND, 4, "The shipped table must cap at level 4");
        h.succeed();
    }

    /**
     * Crossing a level threshold multiplies the spell's effort gain - the D80 effort-surge perk,
     * read at grant time before routing: level 1 grants no surge, level 2 grants x1.1.
     */
    @GameTest(template = "empty")
    public static void masteryEffortSurge(GameTestHelper h) {
        ServerPlayer caster = player(h, PlayerSpell.MEND);
        MasteryTarget self = MasteryTarget.ability(PlayerSpell.MEND.id());
        caster.setHealth(10);
        MasteryAwards.awardTo(caster, self, 50.0);
        cast(h, caster, PlayerSpell.MEND);
        assertOwnXp(h, caster, PlayerSpell.MEND, "Level 1 must not surge Mend's effort", 50.9);
        MasteryAwards.awardTo(caster, self, 75.0);
        cast(h, caster, PlayerSpell.MEND);
        assertOwnXp(h, caster, PlayerSpell.MEND, "Level 2 must surge Mend's effort by x1.1",
            126.89);
        h.succeed();
    }

    /**
     * The D81 menu presentation registers for all sixteen spells and, with hotbar's snapshot
     * unanswered (a headless run never opens the menu), answers an empty presentation - no meter,
     * no rows, never a guessed zero.
     */
    @GameTest(template = "empty")
    public static void masteryPresentationWaitsForTheSnapshot(GameTestHelper h) {
        for (PlayerSpell spell : PlayerSpell.values()) {
            h.assertTrue(PresentationProviders.has(spell.id()),
                "Every spell must declare its D81 presentation, missing " + spell.id());
        }
        h.assertTrue(!CoreMasteryCompat.answered(),
            "A headless run never opens the menu, so the snapshot stays unanswered");
        var presentation = PresentationProviders.present(player(h, PlayerSpell.MEND),
            PlayerSpell.MEND.id());
        h.assertTrue(presentation.isPresent(), "A declared provider must answer a presentation");
        h.assertTrue(presentation.get().fields().isEmpty(),
            "An unanswered snapshot must present no meter, got " + presentation.get().fields());
        h.assertTrue(presentation.get().toggle() == null,
            "No spell is togglable, so no Enable/Disable control presents");
        h.succeed();
    }

    /**
     * The answered half of the D81 meter (its pure seam): one PROGRESS field carrying the in-level
     * fill and the localised level text, built from fabricated snapshot numbers.
     */
    @GameTest(template = "empty")
    public static void masteryPresentationMeterCarriesLevelAndFill(GameTestHelper h) {
        var answered = SpellMastery.presentation(true, 4, 0.25);
        h.assertTrue(answered.toggle() == null, "No spell is togglable");
        h.assertTrue(answered.fields().size() == 1,
            "The answered meter is exactly one field, got " + answered.fields());
        var meter = answered.fields().get(0);
        h.assertTrue(meter.kind() == PresentationField.Kind.PROGRESS,
            "The presentation field must render as a meter, got " + meter.kind());
        h.assertTrue(Math.abs(meter.progress() - 0.25f) < 1.0e-6,
            "The meter must carry the in-level fill, got " + meter.progress());
        h.assertTrue(meter.value().endsWith("4"),
            "The meter's value must name level 4, got '" + meter.value() + "'");
        h.assertTrue(SpellMastery.presentation(false, 4, 0.25).fields().isEmpty(),
            "An unanswered snapshot must present no meter");
        h.succeed();
    }

    /** Asserts one spell's self-target XP, allowing double error from share routing (D80). */
    private static void assertOwnXp(GameTestHelper h, ServerPlayer caster, PlayerSpell spell,
        String message, double expected) {
        double xp = MasteryView.of(caster, spell.id()).ownXp();
        h.assertTrue(Math.abs(xp - expected) < 1.0e-9, message + ", got " + xp);
    }

    /** Asserts one spell's self-target level on the shipped curve (D80). */
    private static void assertOwnLevel(GameTestHelper h, ServerPlayer caster, PlayerSpell spell,
        int level, String message) {
        int actual = MasteryView.of(caster, spell.id()).ownLevel();
        h.assertTrue(actual == level, message + ", got level " + actual);
    }

    /** Asserts one declared share target's fraction and curve level inside a view (D80). */
    private static void assertShare(GameTestHelper h, MasteryView view, String key, double share,
        int level) {
        MasteryView.TargetLevel match = null;
        for (MasteryView.TargetLevel target : view.targets()) {
            if (target.target().key().equals(key)) {
                match = target;
                break;
            }
        }
        h.assertTrue(match != null, "No share target " + key + " in " + view.targets());
        h.assertTrue(Math.abs(match.share() - share) < 1.0e-9,
            "Share for " + key + " must be " + share + ", got " + match.share());
        h.assertTrue(match.level() == level,
            "Level for " + key + " must be " + level + ", got " + match.level());
    }

    /**
     * Puts a spell and every one of its share targets at the shipped cap (425 XP each), so the
     * aggregate level is a known 6.2 for the effect-math assertions (D80).
     */
    private static void capMastery(ServerPlayer caster, PlayerSpell spell) {
        MasteryAwards.awardTo(caster, MasteryTarget.ability(spell.id()), 425.0);
        for (MasteryView.TargetLevel target : MasteryView.of(caster, spell.id()).targets()) {
            MasteryAwards.awardTo(caster, target.target(), 425.0);
        }
    }

    /** Creates a survival, focus-holding caster with 3/10 pools and no target in view. */
    private static ServerPlayer paidPlayer(GameTestHelper h) {
        ServerPlayer player = player(h, PlayerSpell.MEND, false);
        fillPools(h, player, 3);
        return player;
    }

    /**
     * Enables payment for one synchronous test body and restores the previous setting. GameTests
     * share the server config, so the body must not schedule casts for later ticks.
     */
    private static void withPayment(Runnable body) {
        boolean previous = SpellConfig.PAYMENT_ENABLED.get();
        SpellConfig.PAYMENT_ENABLED.set(true);
        try {
            body.run();
        } finally {
            SpellConfig.PAYMENT_ENABLED.set(previous);
        }
    }

    /** Runs one cast through Ability Core's real pipeline and returns its result. */
    private static ActivationResult run(ServerPlayer player, PlayerSpell spell) {
        return AbilityCore.PIPELINE.run(
            ActivationRequest.of(spell.id(), ActivationSource.NATIVE_ADAPTER), player);
    }

    /** Asserts the player's six balances, given in {@link ManaType#FLAVORS} order. */
    private static void assertBalances(GameTestHelper h, ServerPlayer player, String message,
        long fire, long earth, long air, long water, long light, long dark) {
        var pools = LeyLinesApi.playerPower(h.getLevel().getServer())
            .inspect(player.getUUID()).pools();
        long[] expected = {fire, earth, air, water, light, dark};
        for (int index = 0; index < expected.length; index++) {
            var element = ManaType.FLAVORS.get(index);
            long actual = pools.get(PlayerManaPools.currency(element)).balance();
            h.assertTrue(actual == expected[index],
                message + ": " + element + " is " + actual + ", expected " + expected[index]);
        }
    }

    /** Captures a result assigned inside try/finally for assertion afterward. */
    private static final class ActivationResultHolder {
        private ActivationResult result;
    }

    /**
     * Test-only third-party vetoes. Each targets one player UUID and is cleared by the test that
     * set it, so other GameTests are unaffected.
     */
    @EventBusSubscriber(modid = "technomagy")
    public static final class PaymentVetoes {
        static final AtomicReference<UUID> LIGHT_SPEND = new AtomicReference<>();
        static final AtomicReference<UUID> HEAL = new AtomicReference<>();

        private PaymentVetoes() {
        }

        /** Cancels the flagged player's Light spend, after Water has already been spent. */
        @SubscribeEvent
        public static void spend(PlayerPowerEvent.Pre event) {
            UUID player = LIGHT_SPEND.get();
            var receipt = event.receipt();
            if (player != null
                && receipt.operation() == PlayerPowerService.Operation.SPEND
                && receipt.currency().equals(Technomagy.LIGHT)
                && receipt.changes().containsKey(player)) {
                event.setCanceled(true);
            }
        }

        /** Cancels healing of the flagged entity at application time. */
        @SubscribeEvent
        public static void heal(LivingHealEvent event) {
            UUID entity = HEAL.get();
            if (entity != null && event.getEntity().getUUID().equals(entity)) {
                event.setCanceled(true);
            }
        }
    }

    /** Having a focus unlocks recharge permanently; each pulse adds one unit (D77). */
    @GameTest(template = "empty")
    public static void rechargeUnlocksWithFocus(GameTestHelper h) {
        var power = LeyLinesApi.playerPower(h.getLevel().getServer());
        ServerPlayer player = connectedPlayer(h, false);
        player.getInventory().clearContent();
        player.setData(PoolRecharge.UNLOCKED, false);
        fillPools(h, player, 3);
        h.assertTrue(!PoolRecharge.unlock(player) && !player.getData(PoolRecharge.UNLOCKED),
            "Recharge unlocked without a focus");
        player.getInventory().add(new ItemStack(SpellContent.FOCUS.get()));
        h.assertTrue(PoolRecharge.unlock(player) && player.getData(PoolRecharge.UNLOCKED),
            "A focus in the inventory did not unlock recharge");
        player.getInventory().clearContent();
        h.assertTrue(PoolRecharge.unlock(player), "Losing the focus relocked recharge");
        player.setHealth(0);
        h.assertTrue(PoolRecharge.recharge(power, player, 1) == null,
            "A dead player recharged");
        player.setHealth(player.getMaxHealth());
        var chosen = PoolRecharge.recharge(power, player, 1);
        h.assertTrue(chosen != null, "A pulse with room in every pool recharged nothing");
        long total = power.inspect(player.getUUID()).pools().values().stream()
            .mapToLong(PlayerPowerService.Pool::balance).sum();
        h.assertTrue(total == 19, "A pulse must add exactly one unit, total is " + total);
        h.assertTrue(power.inspect(player.getUUID()).pools().get(chosen).balance() == 4,
            "The reported pool was not the one recharged");
        h.succeed();
    }

    /** Pulses pick only active, non-full pools and clamp to the remaining room (D77). */
    @GameTest(template = "empty")
    public static void rechargeSkipsFullPools(GameTestHelper h) {
        var power = LeyLinesApi.playerPower(h.getLevel().getServer());
        ServerPlayer player = connectedPlayer(h, false);
        fillPools(h, player, 10);
        var consumer = PlayerManaPools.consumer(player.getUUID());
        h.assertTrue(power.setBalance(player.getUUID(), Technomagy.WATER, 9, consumer).succeeded()
            && power.setBalance(player.getUUID(), Technomagy.LIGHT, 0, consumer).succeeded()
            && power.configure(player.getUUID(), Technomagy.LIGHT,
                new PlayerPowerService.Settings(false, 10, null), consumer).succeeded(),
            "Could not prepare the test pools");
        var random = RandomSource.create(7);
        for (int pulse = 0; pulse < 8; pulse++) {
            var chosen = PoolRecharge.pulse(power, player, random, 5);
            h.assertTrue(pulse == 0 ? Technomagy.WATER.equals(chosen) : chosen == null,
                "Pulse " + pulse + " chose " + chosen);
        }
        assertBalances(h, player, "Recharge overfilled or touched an inactive pool",
            10, 10, 10, 10, 0, 10);
        h.succeed();
    }
}
