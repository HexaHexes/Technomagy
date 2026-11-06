package zone.moddev.mc.technomagy.spell;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.RegisterEvent;

import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;

import zone.moddev.mc.skysleylines.api.LeyLinesApi;
import zone.moddev.mc.skysleylines.api.PlayerPowerService;
import zone.moddev.mc.skysleylines.api.PowerRequest;

import zone.moddev.mc.technomagy.Technomagy;
import zone.moddev.mc.technomagy.mana.ManaType;

/** Dedicated-server, actual-pipeline checks for each of the sixteen approved effects. */
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
    private static ServerPlayer connectedPlayer(GameTestHelper helper) {
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
                return true;
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

    /** Creates a real mock-server connection and an actually selected held focus. */
    private static ServerPlayer player(GameTestHelper helper, PlayerSpell spell) {
        ServerPlayer player = connectedPlayer(helper);
        Vec3 position = helper.absoluteVec(new Vec3(7.5, 1, 2.5));
        player.moveTo(position.x, position.y, position.z, 0, 0);
        ItemStack focus = new ItemStack(SpellContent.FOCUS.get());
        var bar = player.getData(BarStateAttachments.BAR_STATE);
        player.setData(BarStateAttachments.BAR_STATE,
            bar.withAssignment(bar.selectedSlot(), spell.id()));
        player.setItemInHand(InteractionHand.MAIN_HAND, focus);
        return player;
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
}
