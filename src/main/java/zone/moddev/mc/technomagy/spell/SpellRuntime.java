package zone.moddev.mc.technomagy.spell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * radius, or arbitrary target positions. Payment is Ability Core's: the pipeline prices the
 * declared pool costs, refuses unaffordable casts before this handler runs, and charges the
 * caster's personal pools at its single post-activation charge site (D78). Timed wards/mist are
 * owned by this runtime and cleared at server shutdown; ordinary status effects and scheduled
 * temporary blocks use Minecraft's own persistence.
 */
final class SpellRuntime {
    private static final TagKey<Block> GROWTH_EXCLUDED = TagKey.create(
        Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath("technomagy", "growth_excluded"));
    private final Map<UUID, Ward> wards = new HashMap<>();
    private final List<Mist> mists = new ArrayList<>();

    /**
     * Validates the held casting device (D74), computes targets, and applies one registered
     * ability. Ability Core charges the caster's personal pools afterwards (D78).
     *
     * <h2>Validate, apply, charge</h2>
     *
     * <p>
     * Every handler is split in two. {@code prepare*} methods check everything that can be checked
     * without changing the world (target presence, protection and placement vetoes, recipe outputs,
     * receiver acceptance) and return the {@link Effect} to apply, or empty when the cast has no
     * valid target. Ability Core prices the declared costs, refuses unaffordable casts before the
     * handler runs, and charges the validated snapshot only after this handler reports
     * {@link ActivationResult#ACTED}, so an invalid cast never costs anything.
     *
     * <p>
     * Some outcomes are decided only at application time: damage, healing, and status effects can
     * be cancelled by events or rejected by the target; block and entity placement can still be
     * refused; Growth's extra ticks are random. When the applied effect reports that it did
     * nothing, the cast is refused instead of acting, and the post-activation charge never runs -
     * no effect is ever paid for without happening (D78).
     *
     * @param spell
     *            the registered spell being activated
     * @param request
     *            Ability Core's request; its ability ID must match the spell
     * @param player
     *            the caster
     * @return {@link ActivationResult#ACTED} when the effect happened, triggering Ability Core's
     *         post-activation charge; otherwise the refusal
     * @throws IllegalStateException
     *             if called off the server thread
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
        if (SpellConfig.PAYMENT_ENABLED.get() && !player.isCreative()
            && SpellConfig.cost(spell).isEmpty()) {
            // Already reported when the config loaded; refusing keeps a misconfigured spell from
            // being free (D76).
            return ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE);
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
        Optional<Effect> effect = switch (spell) {
            case ERODE -> prepareProcessBlock(player, target.block(), false);
            case FORGE -> prepareForge(player, target);
            case LIGHTNING -> Optional.of(() -> lightning(player, target));
            case STEAM -> preparePush(player, target);
            case MIST -> Optional.of(() -> mist(player, target.position()));
            case CONSTRAINT ->
                prepareTimedEffect(
                    target.living(),
                    MobEffects.MOVEMENT_SLOWDOWN,
                    SpellConfig.DURATION.get(),
                    SpellConfig.CONSTRAINT_AMPLIFIER.get());
            case WARD -> {
                LivingEntity warded = target.living() == null ? player : target.living();
                yield Optional.of(() -> ward(warded));
            }
            case ENTOMB -> prepareEntomb(player, target.living());
            case REVEAL -> prepareReveal(player);
            case SHROUD ->
                prepareTimedEffect(
                    target.living() == null ? player : target.living(),
                    SpellContent.SHROUD,
                    SpellConfig.DURATION.get(),
                    0);
            case RADIANCE -> prepareRadiance(player, target);
            case BLIGHT ->
                prepareTimedEffect(
                    target.living(),
                    MobEffects.WITHER,
                    SpellConfig.SHORT_DURATION.get(),
                    SpellConfig.BLIGHT_AMPLIFIER.get());
            case MEND -> prepareHeal(target.living() == null ? player : target.living());
            case LEACH ->
                prepareTimedEffect(
                    target.living(),
                    SpellContent.LEACH,
                    SpellConfig.DURATION.get(),
                    0);
            case GROWTH ->
                prepareGrowth(
                    player,
                    target.block(),
                    evaluated.effects().getFirst().growth().orElseThrow());
            case LIFE_AND_DEATH -> prepareEnergy(level, target.block(), target.face());
        };
        if (effect.isEmpty()) {
            return ActivationResult.refused(ActivationOutcome.REFUSED_INVALID_TARGET);
        }
        if (!effect.get().apply()) {
            return ActivationResult.refused(ActivationOutcome.REFUSED_INVALID_TARGET);
        }
        return ActivationResult.ACTED;
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

    /**
     * Prepares a vanilla timed effect without replacing stronger/longer effects forcibly.
     *
     * <p>
     * Only the target's presence is validated. Whether the effect lands is decided by
     * {@link LivingEntity#addEffect}: it fires {@code MobEffectEvent.Applicable} and declines when
     * the target already has a stronger or longer instance. Checking that in advance would fire the
     * event twice or duplicate vanilla's update rules, so the applied result decides the refund.
     *
     * @return the effect, or empty without a living target
     */
    private Optional<Effect> prepareTimedEffect(
        LivingEntity entity,
        net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
        int duration,
        int amplifier) {
        if (entity == null) {
            return Optional.empty();
        }
        return Optional
            .of(() -> entity.addEffect(new MobEffectInstance(effect, duration, amplifier)));
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
     * Prepares crushing or smelting one block, never applying a hard-coded yield multiplier.
     *
     * <p>
     * Validation covers the break veto, the recipe output, and (for a smelted block that yields
     * exactly one block item) the placement veto for the replacement. The effect removes the block
     * and either places the replacement or drops the outputs.
     *
     * @return the effect, or empty when the block may not be processed
     */
    private Optional<Effect> prepareProcessBlock(
        ServerPlayer player, BlockPos pos, boolean smelting) {
        ServerLevel level = player.serverLevel();
        if (!breakable(player, pos)) {
            return Optional.empty();
        }
        BlockState state = level.getBlockState(pos);
        ItemStack offered = new ItemStack(state.getBlock().asItem());
        var result = smelting
            ? RitualProcessingRecipes.findOutput(level, offered, RecipeType.SMELTING)
            : RitualProcessingRecipes.findOutput(
                level, offered, CrushingRecipe.TYPE.get());
        if (smelting && result.isEmpty()) {
            return Optional.empty();
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
        BlockState replacement;
        if (smelting
            && drops.size() == 1
            && drops.getFirst().getCount() == 1
            && drops.getFirst().getItem() instanceof BlockItem item) {
            replacement = item.getBlock().defaultBlockState();
            if (!placementAllowed(player, pos, replacement)) {
                return Optional.empty();
            }
        } else {
            replacement = null;
        }
        return Optional.of(() -> {
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
        });
    }

    /**
     * Prepares smelting one dropped item or one target block, retaining recipe-defined output
     * quantities.
     *
     * @return the effect, or empty when nothing at the target smelts
     */
    private Optional<Effect> prepareForge(ServerPlayer player, Target target) {
        if (target.item() == null) {
            return prepareProcessBlock(player, target.block(), true);
        }
        ItemEntity entity = target.item();
        var output = RitualProcessingRecipes.findOutput(
            player.serverLevel(), entity.getItem(), RecipeType.SMELTING);
        if (output.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(() -> {
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
        });
    }

    /**
     * Magical strike without vanilla lightning's unrelated fire/weather/transformation effects.
     * Always valid: with no living target it is a visual strike at the aimed position.
     *
     * @return whether the target was damaged, or, without a target, whether the bolt appeared
     */
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

    /**
     * Prepares an impulse instead of teleporting the entity or clearing its motion. The NeoForge
     * knockback event is the permission check, so it is posted during validation and its adjusted
     * strength and direction are what the effect applies.
     *
     * @return the effect, or empty without a living target or when knockback is vetoed
     */
    private Optional<Effect> preparePush(ServerPlayer player, Target target) {
        LivingEntity living = target.living();
        if (living == null) {
            return Optional.empty();
        }
        Vec3 direction = living.position().subtract(player.position());
        if (direction.lengthSqr() == 0) {
            direction = player.getLookAngle();
        }
        direction = direction.normalize();
        var knockback = CommonHooks.onLivingKnockBack(
            living,
            SpellConfig.IMPULSE.get().floatValue(),
            direction.x,
            direction.z);
        if (knockback.isCanceled() || knockback.getStrength() <= 0) {
            return Optional.empty();
        }
        Vec3 impulse = new Vec3(knockback.getRatioX(), direction.y, knockback.getRatioZ())
            .normalize()
            .scale(knockback.getStrength());
        return Optional.of(() -> {
            living.push(impulse.x, impulse.y, impulse.z);
            living.hurtMarked = true;
            return true;
        });
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

    /**
     * Prepares an empty-space shell, never placing a block inside an entity's body. Every shell
     * position must pass the placement veto; one vetoed position refuses the whole cast.
     *
     * @return the effect, or empty without a target, without any empty shell position, or when a
     *         placement is vetoed; the effect reports whether any shell block was placed
     */
    private Optional<Effect> prepareEntomb(ServerPlayer player, LivingEntity target) {
        if (target == null) {
            return Optional.empty();
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
                    return Optional.empty();
                }
                positions.add(candidate.immutable());
            }
        }
        if (positions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(() -> {
            boolean placed = false;
            for (BlockPos position : positions) {
                if (level.setBlock(position, shell, Block.UPDATE_ALL)) {
                    level.scheduleTick(position, shell.getBlock(),
                        SpellConfig.SHORT_DURATION.get());
                    placed = true;
                }
            }
            return placed;
        });
    }

    /**
     * Prepares revealing entities, including invisible ones, using the approved finite-duration
     * outline.
     *
     * @return the effect, or empty when no other living entity is in range; the effect reports
     *         whether any entity accepted the outline
     */
    private Optional<Effect> prepareReveal(ServerPlayer player) {
        int radius = SpellConfig.REVEAL_RADIUS.get();
        List<LivingEntity> entities = player.serverLevel()
            .getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                entity -> entity != player
                    && entity.distanceToSqr(player) <= radius * radius);
        if (entities.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(() -> {
            boolean found = false;
            for (LivingEntity entity : entities) {
                found |= entity.addEffect(
                    new MobEffectInstance(
                        MobEffects.GLOWING, SpellConfig.SHORT_DURATION.get()));
            }
            return found;
        });
    }

    /**
     * Prepares an invisible no-drop light only in empty space, with saved scheduled expiry.
     *
     * @return the effect, or empty when the light position is occupied, unloaded, or vetoed
     */
    private Optional<Effect> prepareRadiance(ServerPlayer player, Target target) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = level.isEmptyBlock(target.block())
            ? target.block()
            : target.block().relative(target.face());
        BlockState light = SpellContent.LIGHT
            .get()
            .defaultBlockState()
            .setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL,
                SpellConfig.LIGHT_LEVEL.get());
        if (!level.hasChunkAt(pos)
            || !level.isEmptyBlock(pos)
            || !placementAllowed(player, pos, light)) {
            return Optional.empty();
        }
        return Optional.of(() -> {
            boolean placed = level.setBlock(pos, light, Block.UPDATE_ALL);
            if (placed) {
                level.scheduleTick(pos, light.getBlock(), SpellConfig.DURATION.get());
            }
            return placed;
        });
    }

    /**
     * Prepares restoring health only; equipment maintenance remains recipe/material-dependent.
     * {@link LivingEntity#heal} does not report a cancelled {@code LivingHealEvent}, so the effect
     * compares health before and after.
     *
     * @return the effect, or empty when the target is already at full health
     */
    private Optional<Effect> prepareHeal(LivingEntity target) {
        if (target.getHealth() >= target.getMaxHealth()) {
            return Optional.empty();
        }
        return Optional.of(() -> {
            float before = target.getHealth();
            target.heal(SpellConfig.HEAL.get().floatValue());
            return target.getHealth() > before;
        });
    }

    /**
     * Prepares the standard base Growth's extra tick opportunities for plants in its fixed layer.
     *
     * <p>
     * Validation collects every eligible plant in the 9x9 layer around the target; with none, the
     * cast is refused before payment. The effect re-checks each position (another plant's random
     * tick can change a neighbor) and applies imbalanced mortality or the fractional tick
     * multiplier. Both are random, so a paid cast can still change nothing; the effect reports
     * that, and the cast is refunded.
     *
     * @return the effect, or empty when no eligible plant is in range
     */
    private Optional<Effect> prepareGrowth(
        ServerPlayer player, BlockPos center, SpellEngine.Growth growth) {
        ServerLevel level = player.serverLevel();
        List<BlockPos> plants = new ArrayList<>();
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-4, 0, -4),
            center.offset(4, 0, 4))) {
            if (growable(player, position)) {
                plants.add(position.immutable());
            }
        }
        if (plants.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(() -> {
            boolean affected = false;
            for (BlockPos position : plants) {
                if (!growable(player, position)) {
                    continue;
                }
                Block block = level.getBlockState(position).getBlock();
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
                    current.randomTick(level, position, level.random);
                    affected = true;
                }
            }
            return affected;
        });
    }

    /**
     * Whether Growth may act on one position: loaded, interactable, a randomly ticking plant
     * without a block entity, and not in {@code technomagy:growth_excluded} (D48).
     */
    private boolean growable(ServerPlayer player, BlockPos position) {
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(position) || !level.mayInteract(player, position)) {
            return false;
        }
        BlockState state = level.getBlockState(position);
        Block block = state.getBlock();
        boolean plant = block instanceof BushBlock
            || block instanceof GrowingPlantBlock
            || block instanceof CactusBlock
            || block instanceof SugarCaneBlock
            || block instanceof BambooStalkBlock
            || block instanceof BambooSaplingBlock;
        return plant
            && state.isRandomlyTicking()
            && !state.hasBlockEntity()
            && !state.is(GROWTH_EXCLUDED);
    }

    /**
     * Prepares delivering only what the FE receiver accepts. A simulated transfer validates that
     * the receiver will take energy, so a refusing receiver is never charged for.
     *
     * @return the effect, or empty without a receiver that currently accepts energy
     */
    private Optional<Effect> prepareEnergy(ServerLevel level, BlockPos pos, Direction face) {
        if (!level.hasChunkAt(pos)) {
            return Optional.empty();
        }
        var receiver = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face);
        if (receiver == null || receiver.receiveEnergy(SpellConfig.FE.get(), true) <= 0) {
            return Optional.empty();
        }
        return Optional.of(() -> receiver.receiveEnergy(SpellConfig.FE.get(), false) > 0);
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
     * A validated spell effect, applied only after the caster has paid. Applying it may still
     * produce nothing when a decision made only at application time (an event, a refused placement,
     * a random roll) prevents it; that case is refunded.
     */
    @FunctionalInterface
    private interface Effect {
        /**
         * Changes the world.
         *
         * @return whether the effect actually happened
         */
        boolean apply();
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
