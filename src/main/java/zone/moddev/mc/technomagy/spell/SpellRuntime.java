package zone.moddev.mc.technomagy.spell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;

import zone.moddev.mc.lib.common.recipes.CrushingRecipe;

import zone.moddev.mc.technomagy.machine.RitualProcessingRecipes;
import zone.moddev.mc.technomagy.mana.ManaType;
import zone.moddev.mc.technomagy.mana.SpellElements;
import zone.moddev.mc.technomagy.mana.SpellEngine;

/**
 * Server-thread executor for the sixteen approved first player-cast effects (D69-D70).
 *
 * <p>
 * All target coordinates are computed on the server. Clients cannot supply damage, duration,
 * radius, or arbitrary target positions. Power charging is intentionally deferred to the upcoming
 * Ley Lines contract. Timed wards/mist are owned by this runtime and cleared at server shutdown;
 * ordinary status effects and scheduled temporary blocks use Minecraft's own persistence.
 */
final class SpellRuntime {
    private static final TagKey<Block> GROWTH_EXCLUDED = TagKey.create(
        Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath("technomagy", "growth_excluded"));
    private final Map<UUID, Ward> wards = new HashMap<>();
    private final List<Mist> mists = new ArrayList<>();

    /**
     * Validates the held casting device (D74), computes targets, and dispatches one registered
     * ability.
     */
    ActivationResult cast(PlayerSpell spell, ActivationRequest request, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Spell execution requires the server thread");
        }
        boolean holdingFocus = java.util.stream.Stream
            .of(player.getMainHandItem(), player.getOffhandItem())
            .anyMatch(stack -> stack.is(SpellContent.FOCUS.get()));
        if (!holdingFocus
            || player.isSpectator()
            || !player.isAlive()
            || !request.abilityId().equals(spell.id())) {
            return ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED);
        }
        var engine = new SpellEngine<>(
            new SpellElements<>(
                ManaType.EARTH,
                ManaType.AIR,
                ManaType.FIRE,
                ManaType.WATER,
                ManaType.LIGHT,
                ManaType.DARK,
                ManaType.RAW),
            new SpellEngine.Numbers(
                0.15,
                3,
                2,
                0.25,
                0.85,
                SpellConfig.GROWTH_BASE.get(),
                SpellConfig.GROWTH_SCALE.get(),
                1,
                2));
        var evaluated = engine.evaluate(spell.stream());
        if (spell != PlayerSpell.LIFE_AND_DEATH
            && (evaluated.effects().size() != 1
                || evaluated.effects().getFirst().cancelled())) {
            return ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE);
        }
        Target target = target(player, SpellConfig.REACH.get());
        boolean harmful = switch (spell) {
            case LIGHTNING, STEAM, CONSTRAINT, ENTOMB, BLIGHT, LEACH -> true;
            default -> false;
        };
        if (harmful && target.living() != null && !mayHarm(player, target.living())) {
            return ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED);
        }
        boolean acted = switch (spell) {
            case ERODE -> processBlock(player, target.block(), false);
            case FORGE -> forge(player, target);
            case LIGHTNING -> lightning(player, target);
            case STEAM -> push(player, target);
            case MIST -> mist(player, target.position());
            case CONSTRAINT ->
                apply(
                    target.living(),
                    MobEffects.MOVEMENT_SLOWDOWN,
                    SpellConfig.DURATION.get(),
                    SpellConfig.CONSTRAINT_AMPLIFIER.get());
            case WARD -> ward(target.living() == null ? player : target.living());
            case ENTOMB -> entomb(player, target.living());
            case REVEAL -> reveal(player);
            case SHROUD ->
                (target.living() == null ? player : target.living())
                    .addEffect(
                        new MobEffectInstance(
                            SpellContent.SHROUD,
                            SpellConfig.DURATION.get()));
            case RADIANCE -> radiance(player, target);
            case BLIGHT ->
                apply(
                    target.living(),
                    MobEffects.WITHER,
                    SpellConfig.SHORT_DURATION.get(),
                    SpellConfig.BLIGHT_AMPLIFIER.get());
            case MEND -> heal(target.living() == null ? player : target.living());
            case LEACH ->
                target.living() != null
                    && target.living()
                        .addEffect(
                            new MobEffectInstance(
                                SpellContent.LEACH,
                                SpellConfig.DURATION.get()));
            case GROWTH ->
                grow(
                    player,
                    target.block(),
                    evaluated.effects().getFirst().growth().orElseThrow());
            case LIFE_AND_DEATH -> energy(level, target.block(), target.face());
        };
        return acted
            ? ActivationResult.ACTED
            : ActivationResult.refused(ActivationOutcome.REFUSED_INVALID_TARGET);
    }

    /** Raycasts blocks/fluids and pickable entities without looking through a blocking surface. */
    private Target target(ServerPlayer player, int reach) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(reach));
        BlockHitResult block = player.serverLevel()
            .clip(
                new ClipContext(
                    start,
                    end,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.ANY,
                    player));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(
            player,
            start,
            stop,
            player.getBoundingBox().expandTowards(stop.subtract(start)).inflate(1),
            candidate -> (candidate instanceof ItemEntity || candidate.isPickable())
                && !candidate.isSpectator(),
            start.distanceToSqr(stop));
        Entity hit = entity == null ? null : entity.getEntity();
        Vec3 position = entity == null ? stop : entity.getLocation();
        return new Target(
            block.getBlockPos(),
            block.getDirection(),
            position,
            hit instanceof LivingEntity living ? living : null,
            hit instanceof ItemEntity item ? item : null);
    }

    /** Preserves PvP and team-friendly-fire restrictions for hostile entity effects. */
    private boolean mayHarm(ServerPlayer caster, LivingEntity target) {
        return !(target instanceof ServerPlayer other)
            || other == caster
            || caster.serverLevel().getServer().isPvpAllowed() && caster.canHarmPlayer(other);
    }

    /** Applies a vanilla timed effect without replacing stronger/longer effects forcibly. */
    private boolean apply(
        LivingEntity entity,
        net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
        int duration,
        int amplifier) {
        return entity != null
            && entity.addEffect(new MobEffectInstance(effect, duration, amplifier));
    }

    /** Refuses protected, unbreakable, unloaded, or inventory-bearing blocks before breaking. */
    private boolean breakable(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)
            || !level.mayInteract(player, pos)
            || !player.mayBuild()
            || level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        return !NeoForge.EVENT_BUS
            .post(new BlockEvent.BreakEvent(level, pos, state, player))
            .isCanceled();
    }

    /**
     * Resolves one block through crushing/smelting, never applying a hard-coded yield multiplier.
     */
    private boolean processBlock(ServerPlayer player, BlockPos pos, boolean smelting) {
        ServerLevel level = player.serverLevel();
        if (!breakable(player, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        ItemStack offered = new ItemStack(state.getBlock().asItem());
        var result = smelting
            ? RitualProcessingRecipes.findOutput(level, offered, RecipeType.SMELTING)
            : RitualProcessingRecipes.findOutput(
                level, offered, CrushingRecipe.TYPE.get());
        if (smelting && result.isEmpty()) {
            return false;
        }
        List<ItemStack> drops = result.map(List::of)
            .orElseGet(
                () -> Block.getDrops(
                    state,
                    level,
                    pos,
                    null,
                    player,
                    player.getMainHandItem()));
        BlockState replacement = null;
        if (smelting
            && drops.size() == 1
            && drops.getFirst().getCount() == 1
            && drops.getFirst().getItem() instanceof BlockItem item) {
            replacement = item.getBlock().defaultBlockState();
            if (!placementAllowed(player, pos, replacement)) {
                return false;
            }
        }
        if (!level.removeBlock(pos, false)) {
            return false;
        }
        if (replacement == null || !level.setBlock(pos, replacement, Block.UPDATE_ALL)) {
            for (ItemStack drop : drops) {
                Block.popResource(level, pos, drop);
            }
        }
        level.levelEvent(2001, pos, Block.getId(state));
        return true;
    }

    /** Smelts one dropped item or one target block, retaining recipe-defined output quantities. */
    private boolean forge(ServerPlayer player, Target target) {
        if (target.item() == null) {
            return processBlock(player, target.block(), true);
        }
        ItemEntity entity = target.item();
        var output = RitualProcessingRecipes.findOutput(
            player.serverLevel(), entity.getItem(), RecipeType.SMELTING);
        if (output.isEmpty()) {
            return false;
        }
        ItemStack remainder = entity.getItem().copy();
        remainder.shrink(1);
        if (remainder.isEmpty()) {
            entity.setItem(output.orElseThrow());
        } else {
            ItemEntity product = new ItemEntity(
                player.serverLevel(),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                output.orElseThrow());
            if (!player.serverLevel().addFreshEntity(product)) {
                return false;
            }
            entity.setItem(remainder);
        }
        return true;
    }

    /** Magical strike without vanilla lightning's unrelated fire/weather/transformation effects. */
    private boolean lightning(ServerPlayer player, Target target) {
        if (target.living() != null
            && !target.living()
                .hurt(
                    player.damageSources().indirectMagic(player, player),
                    SpellConfig.DAMAGE.get().floatValue())) {
            return false;
        }
        var bolt = EntityType.LIGHTNING_BOLT.create(player.serverLevel());
        if (bolt == null) {
            throw new IllegalStateException("Vanilla lightning factory returned no entity");
        }
        bolt.moveTo(target.position());
        bolt.setVisualOnly(true);
        boolean visual = player.serverLevel().addFreshEntity(bolt);
        return target.living() != null || visual;
    }

    /** Applies an impulse instead of teleporting the entity or clearing its motion. */
    private boolean push(ServerPlayer player, Target target) {
        if (target.living() == null) {
            return false;
        }
        Vec3 direction = target.living().position().subtract(player.position());
        if (direction.lengthSqr() == 0) {
            direction = player.getLookAngle();
        }
        direction = direction.normalize();
        var knockback = CommonHooks.onLivingKnockBack(
            target.living(),
            SpellConfig.IMPULSE.get().floatValue(),
            direction.x,
            direction.z);
        if (knockback.isCanceled() || knockback.getStrength() <= 0) {
            return false;
        }
        Vec3 impulse = new Vec3(knockback.getRatioX(), direction.y, knockback.getRatioZ())
            .normalize()
            .scale(knockback.getStrength());
        target.living().push(impulse.x, impulse.y, impulse.z);
        target.living().hurtMarked = true;
        return true;
    }

    /** Creates a server-owned field; particle delivery failure never repeats the action. */
    private boolean mist(ServerPlayer player, Vec3 center) {
        ServerLevel level = player.serverLevel();
        mists.add(
            new Mist(
                level,
                player.getUUID(),
                center,
                SpellConfig.MIST_RADIUS.get(),
                level.getGameTime() + SpellConfig.DURATION.get()));
        return true;
    }

    /** Refreshes this runtime's shield without changing vanilla absorption or health. */
    private boolean ward(LivingEntity entity) {
        wards.put(
            entity.getUUID(),
            new Ward(
                (ServerLevel) entity.level(),
                entity.level().getGameTime() + SpellConfig.DURATION.get(),
                SpellConfig.SHIELD.get().floatValue()));
        return true;
    }

    /** Places only an empty-space shell, never placing a block inside an entity's body. */
    private boolean entomb(ServerPlayer player, LivingEntity target) {
        if (target == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        AABB body = target.getBoundingBox();
        BlockPos minimum = BlockPos.containing(body.minX, body.minY, body.minZ).offset(-1, 0, -1);
        BlockPos maximum = BlockPos.containing(body.maxX, body.maxY, body.maxZ).offset(1, 1, 1);
        List<BlockPos> positions = new ArrayList<>();
        BlockState shell = SpellContent.EARTH.get().defaultBlockState();
        for (BlockPos candidate : BlockPos.betweenClosed(minimum, maximum)) {
            boolean boundary = candidate.getX() == minimum.getX()
                || candidate.getX() == maximum.getX()
                || candidate.getZ() == minimum.getZ()
                || candidate.getZ() == maximum.getZ()
                || candidate.getY() == maximum.getY();
            if (boundary
                && !new AABB(candidate).intersects(body)
                && level.hasChunkAt(candidate)
                && level.isEmptyBlock(candidate)
                && level.getEntities(null, new AABB(candidate)).isEmpty()) {
                if (!placementAllowed(player, candidate, shell)) {
                    return false;
                }
                positions.add(candidate.immutable());
            }
        }
        for (BlockPos position : positions) {
            level.setBlock(position, shell, Block.UPDATE_ALL);
            level.scheduleTick(position, shell.getBlock(), SpellConfig.SHORT_DURATION.get());
        }
        return !positions.isEmpty();
    }

    /** Reveals entities, including invisible ones, using the approved finite-duration outline. */
    private boolean reveal(ServerPlayer player) {
        int radius = SpellConfig.REVEAL_RADIUS.get();
        boolean found = false;
        for (LivingEntity entity : player.serverLevel()
            .getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                entity -> entity != player
                    && entity.distanceToSqr(player) <= radius * radius)) {
            found |= entity.addEffect(
                new MobEffectInstance(
                    MobEffects.GLOWING, SpellConfig.SHORT_DURATION.get()));
        }
        return found;
    }

    /** Places an invisible no-drop light only in empty space, with saved scheduled expiry. */
    private boolean radiance(ServerPlayer player, Target target) {
        BlockPos pos = player.serverLevel().isEmptyBlock(target.block())
            ? target.block()
            : target.block().relative(target.face());
        BlockState light = SpellContent.LIGHT
            .get()
            .defaultBlockState()
            .setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL,
                SpellConfig.LIGHT_LEVEL.get());
        if (!player.serverLevel().hasChunkAt(pos)
            || !player.serverLevel().isEmptyBlock(pos)
            || !placementAllowed(player, pos, light)) {
            return false;
        }
        boolean placed = player.serverLevel().setBlock(pos, light, Block.UPDATE_ALL);
        if (placed) {
            player.serverLevel().scheduleTick(pos, light.getBlock(), SpellConfig.DURATION.get());
        }
        return placed;
    }

    /** Restores health only; equipment maintenance remains recipe/material-dependent. */
    private boolean heal(LivingEntity target) {
        if (target.getHealth() >= target.getMaxHealth()) {
            return false;
        }
        target.heal(SpellConfig.HEAL.get().floatValue());
        return true;
    }

    /**
     * Applies the standard base Growth's one extra tick opportunity to plants in its fixed layer.
     */
    private boolean grow(ServerPlayer player, BlockPos center, SpellEngine.Growth growth) {
        ServerLevel level = player.serverLevel();
        boolean affected = false;
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-4, 0, -4),
            center.offset(4, 0, 4))) {
            if (!level.hasChunkAt(position) || !level.mayInteract(player, position)) {
                continue;
            }
            BlockState state = level.getBlockState(position);
            Block block = state.getBlock();
            boolean plant = block instanceof BushBlock
                || block instanceof GrowingPlantBlock
                || block instanceof CactusBlock
                || block instanceof SugarCaneBlock
                || block instanceof BambooStalkBlock
                || block instanceof BambooSaplingBlock;
            if (plant
                && state.isRandomlyTicking()
                && !state.hasBlockEntity()
                && !state.is(GROWTH_EXCLUDED)) {
                if (!growth.balanced()
                    && level.random.nextDouble() < SpellConfig.PLANT_MORTALITY.get()) {
                    if (breakable(player, position)) {
                        level.removeBlock(position, false);
                        affected = true;
                    }
                    continue;
                }
                double extra = growth.tickMultiplier() - 1;
                int opportunities = (int) Math.floor(extra);
                if (level.random.nextDouble() < extra - opportunities) {
                    opportunities++;
                }
                for (int tick = 0; tick < opportunities; tick++) {
                    BlockState current = level.getBlockState(position);
                    if (!current.is(block) || !current.isRandomlyTicking()) {
                        break;
                    }
                    current.randomTick(level, position.immutable(), level.random);
                    affected = true;
                }
            }
        }
        return affected;
    }

    /** Delivers only what the FE receiver accepts, never spending energy on a refused target. */
    private boolean energy(ServerLevel level, BlockPos pos, Direction face) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        var receiver = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face);
        return receiver != null && receiver.receiveEnergy(SpellConfig.FE.get(), false) > 0;
    }

    /**
     * Preflights placement with the proposed block state rather than publishing a speculative
     * block.
     */
    private boolean placementAllowed(ServerPlayer player, BlockPos pos, BlockState proposed) {
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)
            || level.isOutsideBuildHeight(pos)
            || !level.mayInteract(player, pos)
            || !player.mayBuild()) {
            return false;
        }
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        return !NeoForge.EVENT_BUS
            .post(
                new ProposedPlacement(
                    snapshot, level.getBlockState(pos.below()), player, proposed))
            .isCanceled();
    }

    /** Updates only runtime-owned timed state; it never requests unloaded chunks. */
    @SubscribeEvent
    public void tick(ServerTickEvent.Post event) {
        wards.entrySet()
            .removeIf(
                entry -> entry.getValue().expires() <= entry.getValue().level().getGameTime());
        mists.removeIf(mist -> mist.expires() <= mist.level().getGameTime());
        for (Mist mist : mists) {
            double radius = mist.radius();
            ServerPlayer owner = mist.level().getServer().getPlayerList().getPlayer(mist.owner());
            for (LivingEntity entity : mist.level()
                .getEntitiesOfClass(
                    LivingEntity.class,
                    new AABB(mist.center(), mist.center()).inflate(radius),
                    entity -> entity.distanceToSqr(mist.center()) <= radius * radius
                        && (!(entity instanceof ServerPlayer)
                            || owner != null
                                && mayHarm(owner, entity)))) {
                entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 2, 0, false, false));
            }
            if (mist.level().getGameTime() % 5 == 0) {
                mist.level()
                    .sendParticles(
                        ParticleTypes.CLOUD,
                        mist.center().x,
                        mist.center().y,
                        mist.center().z,
                        12,
                        radius,
                        radius,
                        radius,
                        0);
            }
        }
    }

    /** Consumes this runtime's ward pool and breaks an attacker's Shroud on hostile action. */
    @SubscribeEvent
    public void damage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            attacker.removeEffect(SpellContent.SHROUD);
        }
        Ward ward = wards.get(event.getEntity().getUUID());
        if (ward != null && ward.expires() > event.getEntity().level().getGameTime()) {
            float absorbed = Math.min(ward.remaining(), event.getAmount());
            event.setAmount(event.getAmount() - absorbed);
            float remaining = ward.remaining() - absorbed;
            if (remaining <= 0) {
                wards.remove(event.getEntity().getUUID());
            } else {
                wards.put(
                    event.getEntity().getUUID(),
                    new Ward(ward.level(), ward.expires(), remaining));
            }
            if (event.getAmount() <= 0) {
                event.setCanceled(true);
            }
        }
    }

    /** Shields resist knockback only while their finite damage pool remains active. */
    @SubscribeEvent
    public void knockback(LivingKnockBackEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        Ward ward = wards.get(event.getEntity().getUUID());
        if (ward != null && ward.expires() > event.getEntity().level().getGameTime()) {
            event.setCanceled(true);
        }
    }

    /** Shroud prevents distant new targeting without erasing attackers already committed. */
    @SubscribeEvent
    public void targeting(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null
            && target.hasEffect(SpellContent.SHROUD)
            && event.getEntity() instanceof Mob mob
            && mob.getTarget() != target
            && !mob.isWithinMeleeAttackRange(target)) {
            event.setCanceled(true);
        }
    }

    /** An attempted player attack ends Shroud even if no damage is ultimately dealt. */
    @SubscribeEvent
    public void attack(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            event.getEntity().removeEffect(SpellContent.SHROUD);
        }
    }

    /**
     * Suppresses only footsteps, using the synced effect marker rather than persistent silence
     * flags.
     */
    @SubscribeEvent
    public void sound(PlayLevelSoundEvent.AtEntity event) {
        if (event.getEntity() instanceof LivingEntity living
            && living.hasEffect(SpellContent.SHROUD)
            && event.getSound() != null
            && event.getSound().value().getLocation().getPath().endsWith(".step")) {
            event.setCanceled(true);
        }
    }

    /** Releases level-owned references without accessing server maps from a client unload. */
    @SubscribeEvent
    public void unload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            wards.entrySet().removeIf(entry -> entry.getValue().level() == event.getLevel());
            mists.removeIf(mist -> mist.level() == event.getLevel());
        }
    }

    /** Drops transient per-server references; no state leaks into another server session. */
    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        wards.clear();
        mists.clear();
    }

    /** Resolved server target; client hints never populate this structure. */
    private record Target(
        BlockPos block, Direction face, Vec3 position, LivingEntity living, ItemEntity item) {
    }

    /** Runtime-owned ward capacity and expiry in its level's game ticks. */
    private record Ward(ServerLevel level, long expires, float remaining) {
    }

    /** Runtime-owned mist geometry and expiry in level game ticks. */
    private record Mist(ServerLevel level, UUID owner, Vec3 center, int radius, long expires) {
    }

    /**
     * Placement preflight with explicit proposed state, while retaining the real replaced snapshot.
     */
    private static final class ProposedPlacement extends BlockEvent.EntityPlaceEvent {
        private final BlockState proposed;

        /** Supplies the block that will be placed without modifying the world to ask permission. */
        private ProposedPlacement(
            BlockSnapshot snapshot,
            BlockState against,
            ServerPlayer player,
            BlockState proposed) {
            super(snapshot, against, player);
            this.proposed = proposed;
        }

        /** Returns the proposed block to ordinary placement-protection listeners. */
        @Override
        public BlockState getPlacedBlock() {
            return proposed;
        }

        /** Returns the proposed state rather than the unchanged preflight world state. */
        @Override
        public BlockState getState() {
            return proposed;
        }
    }
}
