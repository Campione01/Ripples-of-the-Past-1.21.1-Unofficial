package rotp.addons.kraftwork;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.BladeHatEntity;
import rotp.core.customobjects.entity_projectile.ClackersEntity;
import rotp.core.customobjects.entity_projectile.DamagingEntity;
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.customobjects.entity_projectile.LightBeamEntity;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.impl.powers.hamon.entity.HamonBubbleBarrierEntity;
import rotp.core.impl.powers.hamon.entity.HamonBubbleCutterEntity;
import rotp.core.impl.powers.hamon.entity.HamonBubbleEntity;
import rotp.core.impl.powers.hamon.entity.HamonCutterEntity;
import rotp.core.impl.powers.hamon.entity.HamonTurquoiseBlueOverdriveEntity;
import rotp.core.impl.powers.hamon.entity.HamonZoomPunchEntity;
import rotp.core.impl.powers.hamon.entity.SatiporojaScarfEntity;
import rotp.core.impl.powers.pillarman.PillarmanDivineSandstormEntity;
import rotp.core.impl.powers.pillarman.PillarmanHornEntity;
import rotp.core.impl.powers.pillarman.PillarmanRibEntity;
import rotp.core.impl.powers.pillarman.PillarmanVeinEntity;
import rotp.core.impl.stands.magiciansred.MRCrossfireHurricaneEntity;
import rotp.core.impl.stands.magiciansred.MRFireballEntity;
import rotp.core.impl.stands.magiciansred.MRFlameEntity;
import rotp.core.mechanics.standarrow.StandArrowEntity;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.kraftwork.ability.KraftWorkActionAbility;
import rotp.addons.kraftwork.entity.KraftWorkBlockMarkerEntity;
import rotp.addons.kraftwork.entity.KraftWorkItemProjectile;
import rotp.addons.kraftwork.network.KraftWorkPayloads;
import rotp.addons.kraftwork.state.KraftWorkLockService;
import rotp.addons.kraftwork.state.KraftWorkLockState;
import rotp.addons.kraftwork.state.KraftWorkUserState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = KraftWorkAddon.MOD_ID)
public final class KraftWorkGameEvents {
    private KraftWorkGameEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                KraftWorkAddon.KRAFT_WORK_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onEntityTickPre(
            EntityTickEvent.Pre event) {
        KraftWorkLockService.preTick(event.getEntity());
        if (KraftWorkLockService.suspendsTick(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityTickPost(
            EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        KraftWorkLockService.postTick(entity);
        if (entity instanceof Creeper creeper
                && creeper.isIgnited()
                && KraftWorkLockService
                        .hasAnyLockEffect(creeper)) {
            KraftWorkLockService.clearLockEffects(creeper);
        }
        // Only a guard the user holds: 1.16 blocked a hit on an idle Stand
        // with the plain BLOCK_STAND_ENTITY, which never ran these rules.
        if (entity instanceof StandEntity stand
                && stand.level() instanceof ServerLevel level
                && stand.isStandBlocking()
                && !stand.isAutoGuarding()
                && stand.getUser() != null
                && KraftWorkAddon.isKraftWorkUser(
                        stand.getUser())) {
            guardTick(level, stand, stand.getUser());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(
            PlayerTickEvent.Post event) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }
        if (!KraftWorkAddon.isKraftWorkUser(player)) {
            KraftWorkPayloads.clear(player);
            if (player.hasData(KraftWorkAddon.USER_STATE)) {
                KraftWorkUserState stale =
                        KraftWorkAddon.userState(player);
                if (stale.selfLocked()
                        || stale.blockingItems()) {
                    clearOwner(player);
                }
            }
            return;
        }
        StandPower power = StandPower.get(player);
        KraftWorkUserState state =
                KraftWorkAddon.userState(player);
        float current = power.getStamina();
        float previous = state.lastStamina();
        if (state.blockingItems()
                && Float.isFinite(previous)
                && current > previous
                && !power.isStaminaInfinite()) {
            current = previous + (current - previous) * 0.5F;
            power.setStamina(current);
        }
        state.setLastStamina(current);
        if (state.selfLocked()
                && !KraftWorkLockService.state(player).locked()) {
            state.setSelfLocked(false);
        }
        if (player.getAbilities().instabuild
                && KraftWorkLockService
                        .hasAnyLockEffect(player)) {
            KraftWorkLockService.clearLockEffects(player);
            KraftWorkLockService.releaseTarget(player, player);
            state.setSelfLocked(false);
        }
    }

    // 1.16's knife placed in flight hit nothing in that flight: an entity
    // stopped it unhurt (KnifeEntity.onHit with timeStop)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHeldFlightImpact(ProjectileImpactEvent event) {
        if (!event.getProjectile().level().isClientSide()
                && event.getRayTraceResult() instanceof EntityHitResult
                && KraftWorkLockService.stopsHeldFlight(
                        event.getProjectile())) {
            event.setCanceled(true);
        }
    }

    // Port addition held to the 1.16 guard rules: a projectile that crosses
    // the lock distance within one tick is locked where it hits the guard.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(
            ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(projectile.level() instanceof ServerLevel level)
                || !(event.getRayTraceResult()
                        instanceof EntityHitResult hit)) {
            return;
        }
        StandEntity stand = guardingStand(hit.getEntity());
        if (stand == null) {
            return;
        }
        LivingEntity user = stand.getUser();
        if (guardLocks(user)
                && isGuardProjectile(projectile)
                && !isStandDamage(projectile)
                && projectile.getOwner() != user
                && facesGuard(
                        stand.getLookAngle(),
                        guardConeCos(stand),
                        projectile.getDeltaMovement())
                && guardLockProjectile(
                        level, stand, user, projectile)) {
            event.setCanceled(true);
        }
    }

    // 1.16 damageReduction: only a hit with a direct entity is cut (not
    // lava, fire or cactus). Only 1.16's projectiles are locked, and only
    // a melee hit locks the attacker's main hand.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        Entity direct = source.getDirectEntity();
        if (target.level().isClientSide()
                || direct == null
                || !KraftWorkAddon.isKraftWorkUser(target)
                || !KraftWorkAddon.userState(target)
                        .blockingItems()
                || source.is(DamageTypeTags.BYPASSES_ARMOR)
                || direct instanceof StandEntity) {
            return;
        }
        StandPower power = StandPower.get(target);
        float reduction = Math.min(
                0.8F,
                0.2F * Math.max(0, power.getResolveLevel()));
        event.setAmount(
                Math.max(0.0F, event.getAmount() * (1.0F - reduction)));
        power.consumeStamina(
                KraftWorkSpec.BLOCKING_ITEMS_STAMINA);

        if (direct instanceof Projectile projectile) {
            if (blockingLocksProjectile(projectile)) {
                KraftWorkLockService.lockWhole(target, projectile);
            }
        }
        else if (isMeleeDamage(source)
                && direct instanceof LivingEntity attacker
                && attacker != target) {
            KraftWorkLockService.lockMainHand(
                    target, attacker);
        }
    }

    // 1.16 locked a stand arrow, trident, blade hat or a Kraft Work item
    // that stays on hit; other projectiles fly on.
    private static boolean blockingLocksProjectile(
            Projectile projectile) {
        return projectile instanceof StandArrowEntity
                || projectile instanceof ThrownTrident
                || projectile instanceof BladeHatEntity
                || projectile instanceof KraftWorkItemProjectile item
                        && !item.willBeRemovedOnEntityHit();
    }

    // 1.16 isMelee: not explosion, fire, magic or projectile damage.
    private static boolean isMeleeDamage(DamageSource source) {
        return !source.is(DamageTypeTags.IS_EXPLOSION)
                && !source.is(DamageTypeTags.IS_FIRE)
                && !source.is(DamageTypeTags.WITCH_RESISTANT_TO)
                && !source.is(DamageTypeTags.IS_PROJECTILE);
    }

    // 1.16 core onLivingDamage: a hit with a direct entity stopped a held
    // action whose cancelHeldOnGettingAttacked said so, as Lock Target's did.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        LivingEntity user = event.getEntity();
        if (!user.level().isClientSide()
                && event.getSource().getDirectEntity() != null
                && KraftWorkAddon.isKraftWorkUser(user)) {
            KraftWorkActionAbility.KraftWorkAction
                    .cancelLockTargetCharge(user);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTotemProjectileDeath(
            LivingDeathEvent event) {
        if (!(event.getSource().getDirectEntity()
                instanceof KraftWorkItemProjectile projectile)
                || !projectile.isBeneficial()
                || !projectile.getItem()
                        .is(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        LivingEntity target = event.getEntity();
        event.setCanceled(true);
        projectile.discard();
        target.setHealth(1.0F);
        target.removeAllEffects();
        target.addEffect(new MobEffectInstance(
                MobEffects.REGENERATION,
                900,
                1));
        target.addEffect(new MobEffectInstance(
                MobEffects.ABSORPTION,
                100,
                1));
        target.addEffect(new MobEffectInstance(
                MobEffects.FIRE_RESISTANCE,
                800,
                0));
        target.level().broadcastEntityEvent(target, (byte) 35);
    }

    @SubscribeEvent
    public static void onAttackEntity(
            AttackEntityEvent event) {
        if (KraftWorkLockService.hasLockedMainHand(
                event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        if (isHandLocked(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        if (isHandLocked(event.getEntity(), event.getHand())
                || hasLockedBlock(
                        event.getEntity().level(),
                        event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(
            PlayerInteractEvent.LeftClickBlock event) {
        if (KraftWorkLockService.hasLockedMainHand(
                        event.getEntity())
                || hasLockedBlock(
                        event.getEntity().level(),
                        event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(
            PlayerInteractEvent.EntityInteract event) {
        if (isHandLocked(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(
            PlayerInteractEvent.EntityInteractSpecific event) {
        if (isHandLocked(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(
            PlayerEvent.BreakSpeed event) {
        if (KraftWorkLockService.hasLockedMainHand(
                event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemPickup(
            ItemEntityPickupEvent.Pre event) {
        if (KraftWorkLockService.hasAnyLockEffect(
                event.getPlayer())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (isKraftLockEffect(event.getEffectInstance().getEffect())) {
            KraftWorkLockService.captureEffectPhysics(event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectRemoved(
            MobEffectEvent.Remove event) {
        if (isKraftLockEffect(event.getEffect())) {
            restoreIfKraftLock(event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectExpired(
            MobEffectEvent.Expired event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (effect != null && isKraftLockEffect(effect.getEffect())) {
            restoreIfKraftLock(event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(
            LivingDeathEvent event) {
        clearOwner(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(
            PlayerEvent.Clone event) {
        clearOwner(event.getOriginal());
        clearOwner(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        clearOwner(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        clearOwner(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            KraftWorkPayloads.forgetStandHud(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        KraftWorkPayloads.clearAll();
    }

    // 1.16 KraftWorkBlock.standTickPerform, every tick the guard performs.
    // From resolve 1 it turns Stand projectiles aside and cuts owner-bound
    // ones short; from resolve 2, unless the user sneaks, it locks what
    // flies at its front instead. Distances are squared, from the centre.
    private static void guardTick(
            ServerLevel level,
            StandEntity stand,
            LivingEntity user) {
        StandPower power = StandPower.get(user);
        if (power == null || power.getResolveLevel() < 1) {
            return;
        }
        boolean locks = guardLocks(user);
        Vec3 look = stand.getLookAngle();
        double coneCos = guardConeCos(stand);
        Vec3 centre = stand.getBoundingBox().getCenter();
        for (Entity entity : level.getEntities(
                stand,
                stand.getBoundingBox().inflate(stand.getAttributeValue(
                        Attributes.ENTITY_INTERACTION_RANGE)),
                candidate -> candidate.isAlive()
                        && facesGuard(
                                look, coneCos, approach(candidate)))) {
            double distanceSqr =
                    centre.distanceToSqr(entity.position());
            KraftWorkLockState state =
                    entity.hasData(KraftWorkAddon.LOCK_STATE)
                            ? KraftWorkLockService.state(entity)
                            : null;
            if (!locks) {
                if (entity instanceof Projectile projectile
                        && isGuardProjectile(projectile)
                        && distanceSqr
                                < KraftWorkSpec.GUARD_DEFLECT_RANGE_SQR) {
                    if (projectile
                            instanceof OwnerBoundProjectileEntity bound) {
                        cutShort(bound);
                    }
                    else if (isStandDamage(projectile)) {
                        deflect(level, stand, power, projectile);
                    }
                }
            }
            else if (state != null && state.locked()) {
                if (entity instanceof Projectile
                        && state.takeGuardCharge()) {
                    power.consumeStamina(
                            KraftWorkSpec.GUARD_HOLD_STAMINA);
                }
            }
            else if (KraftWorkLockService.isGuardVehicle(entity)) {
                if (user.distanceToSqr(entity)
                                < entity.getDeltaMovement().lengthSqr()
                                        * KraftWorkSpec
                                                .GUARD_VEHICLE_SPEED_FACTOR
                        && KraftWorkLockService.lockWhole(
                                user, entity)) {
                    power.consumeStamina(
                            KraftWorkSpec.GUARD_LOCK_STAMINA);
                }
            }
            else if (entity instanceof Projectile projectile
                    && isGuardProjectile(projectile)) {
                boolean standDamage = isStandDamage(projectile);
                if (standDamage
                        && !(projectile
                                instanceof OwnerBoundProjectileEntity)
                        && distanceSqr
                                < KraftWorkSpec.GUARD_DEFLECT_RANGE_SQR) {
                    deflect(level, stand, power, projectile);
                }
                if (!standDamage
                        && projectile.getOwner() != user
                        && distanceSqr < guardLockRangeSqr(projectile)) {
                    guardLockProjectile(
                            level, stand, user, projectile);
                }
            }
        }
    }

    // 1.16 KraftWorkBlock lock: from resolve 3 the projectile is first
    // turned to the user's aim, it becomes the user's unless it is an ender
    // pearl or a core item projectile, and each lock costs 10 stamina. An
    // owner-bound projectile is cut short instead; unlike 1.16 it keeps its
    // owner and aim, which would move its tether to the Kraft Work user. A
    // fishing hook also keeps its owner: taking it leaves the angler's rod
    // pointing at a hook that is gone.
    private static boolean guardLockProjectile(
            ServerLevel level,
            StandEntity stand,
            LivingEntity user,
            Projectile projectile) {
        if (projectile instanceof OwnerBoundProjectileEntity bound) {
            cutShort(bound);
            return false;
        }
        if (projectile.hasData(KraftWorkAddon.LOCK_STATE)
                && KraftWorkLockService.state(projectile).locked()) {
            return false;
        }
        StandPower power = StandPower.get(user);
        if (power.getResolveLevel() >= 3) {
            projectile.shootFromRotation(
                    user,
                    user.getXRot(),
                    user.getYRot(),
                    0.0F,
                    0.001F,
                    1.0F);
        }
        if (!(projectile instanceof ThrownEnderpearl
                || projectile instanceof KnifeEntity
                || projectile instanceof ClackersEntity
                || projectile instanceof BladeHatEntity
                || projectile instanceof FishingHook)) {
            projectile.setOwner(user);
        }
        if (!KraftWorkLockService.lockWhole(user, projectile)) {
            return false;
        }
        power.consumeStamina(KraftWorkSpec.GUARD_LOCK_STAMINA);
        KraftWorkLockService.state(projectile).markGuardCharge();
        level.playSound(
                null,
                stand,
                KraftWorkAddon.KRAFT_WORK_BLOCKS_A_PROJECTILE.get(),
                SoundSource.AMBIENT,
                1.0F,
                1.0F + (level.random.nextFloat() - 0.5F) * 0.15F);
        return true;
    }

    // 1.16 KraftWorkBlock.deflectProjectile: back along the guard's aim at
    // the projectile's own speed, for 0.5 stamina. The sound, heard by
    // Stand users, played even for a projectile that cannot be turned.
    private static void deflect(
            ServerLevel level,
            StandEntity stand,
            StandPower power,
            Projectile projectile) {
        if (!(projectile instanceof ModdedProjectileEntity modded)
                || modded.canBeDeflected(stand)) {
            Vec3 deflectPos = projectile.position();
            projectile.setDeltaMovement(stand.getLookAngle().normalize()
                    .scale(projectile.getDeltaMovement().length()));
            projectile.move(
                    MoverType.SELF, projectile.getDeltaMovement());
            projectile.hasImpulse = true;
            if (projectile instanceof ModdedProjectileEntity deflected) {
                deflected.setIsDeflected(
                        projectile.getDeltaMovement(), deflectPos);
            }
            power.consumeStamina(KraftWorkSpec.GUARD_DEFLECT_STAMINA);
        }
        LivingEntity user = power.getUser();
        StandUtil.broadcastSound(
                level,
                user.position(),
                KraftWorkAddon.KRAFT_WORK_BLOCKS_A_PROJECTILE,
                true,
                power,
                SoundSource.AMBIENT,
                1.0F,
                1.0F);
    }

    // 1.16 KraftWorkBlock.blockBound.
    private static void cutShort(OwnerBoundProjectileEntity bound) {
        if (bound instanceof SatiporojaScarfEntity) {
            bound.discard();
        }
        else if (bound instanceof HamonZoomPunchEntity
                || bound instanceof PillarmanHornEntity
                || bound instanceof PillarmanRibEntity
                || bound instanceof PillarmanVeinEntity) {
            bound.tickCount = bound instanceof PillarmanHornEntity
                    ? 27
                    : bound.ticksLifespan() / 2;
        }
    }

    // 1.16 KraftWorkBlock.AdmittedProjectiles.
    private static boolean isGuardProjectile(Projectile projectile) {
        return (projectile instanceof FishingHook
                        || projectile instanceof AbstractArrow
                        || projectile instanceof AbstractHurtingProjectile
                        || projectile instanceof ThrowableProjectile
                        || projectile instanceof DamagingEntity)
                && !(projectile instanceof Snowball
                        || projectile instanceof ThrownEgg
                        || projectile instanceof SmallFireball
                        || projectile instanceof WitherSkull
                        || projectile instanceof DragonFireball
                        || projectile instanceof HamonCutterEntity
                        || projectile
                                instanceof HamonTurquoiseBlueOverdriveEntity
                        || projectile instanceof HamonBubbleBarrierEntity
                        || projectile instanceof HamonBubbleEntity
                        || projectile instanceof HamonBubbleCutterEntity
                        || projectile
                                instanceof PillarmanDivineSandstormEntity
                        || projectile instanceof LightBeamEntity
                        || projectile instanceof MRFireballEntity
                        || projectile instanceof MRFlameEntity
                        || projectile instanceof MRCrossfireHurricaneEntity);
    }

    private static boolean isStandDamage(Projectile projectile) {
        return projectile instanceof ModdedProjectileEntity modded
                && modded.standDamage();
    }

    private static boolean guardLocks(LivingEntity user) {
        StandPower power = StandPower.get(user);
        return power != null
                && power.getResolveLevel() >= 2
                && !user.isShiftKeyDown();
    }

    // A fast projectile is locked within 3 blocks, a slow one within 2.24.
    private static double guardLockRangeSqr(Projectile projectile) {
        double speedSqr = Math.min(
                projectile.getDeltaMovement().lengthSqr(),
                KraftWorkSpec.GUARD_FAST_SPEED_SQR);
        return KraftWorkSpec.GUARD_LOCK_RANGE_SQR
                - (KraftWorkSpec.GUARD_SLOW_LOCK_SHRINK
                        - KraftWorkSpec.GUARD_SLOW_LOCK_SHRINK * speedSqr
                                / KraftWorkSpec.GUARD_FAST_SPEED_SQR);
    }

    // A cone in front of the guard that widens from 30 to 60 degrees with
    // the Stand's precision.
    private static double guardConeCos(StandEntity stand) {
        return Mth.cos((float) Math.toRadians(
                30.0D + Mth.clamp(stand.getPrecision(), 0.0D, 16.0D)
                        * 30.0D / 16.0D));
    }

    private static boolean facesGuard(
            Vec3 look, double coneCos, Vec3 motion) {
        Vec3 toward = motion.reverse();
        return toward.lengthSqr() > 1.0E-7D
                && look.dot(toward.normalize()) >= coneCos;
    }

    // A locked entity stands still; 1.16 froze it with its motion kept.
    private static Vec3 approach(Entity entity) {
        if (entity.hasData(KraftWorkAddon.LOCK_STATE)) {
            KraftWorkLockState state = KraftWorkLockService.state(entity);
            if (state.locked()) {
                return state.storedMotion();
            }
        }
        return entity.getDeltaMovement();
    }

    @Nullable
    private static StandEntity guardingStand(Entity target) {
        StandEntity stand = target instanceof StandEntity hitStand
                ? hitStand
                : target instanceof LivingEntity living
                        && KraftWorkAddon.isKraftWorkUser(living)
                                ? StandPower.get(living)
                                        .getSummonedStandEntity()
                                : null;
        return stand != null
                && stand.isStandBlocking()
                && !stand.isAutoGuarding()
                && stand.getUser() != null
                && KraftWorkAddon.isKraftWorkUser(stand.getUser())
                        ? stand
                        : null;
    }

    private static boolean isHandLocked(
            LivingEntity player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                ? KraftWorkLockService
                        .hasLockedMainHand(player)
                : KraftWorkLockService
                        .hasLockedOffHand(player);
    }

    private static boolean hasLockedBlock(
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos) {
        return !level.getEntitiesOfClass(
                        KraftWorkBlockMarkerEntity.class,
                        new AABB(pos))
                .isEmpty();
    }

    private static boolean isKraftLockEffect(Holder<MobEffect> effect) {
        MobEffect value = effect.value();
        return value == KraftWorkAddon.LOCKED_MAIN_HAND.get()
                || value == KraftWorkAddon.LOCKED_OFF_HAND.get()
                || value == KraftWorkAddon.LOCKED_HELMET.get()
                || value == KraftWorkAddon.LOCKED_CHESTPLATE.get()
                || value == KraftWorkAddon.LOCKED_LEGGINGS.get()
                || value == KraftWorkAddon.LOCKED_POSITION.get()
                || value == KraftWorkAddon.TRANSPORTATION_LOCKED.get()
                || value == KraftWorkAddon.FULL_TRANSPORTATION_LOCKED.get();
    }

    private static void restoreIfKraftLock(
            LivingEntity living) {
        if (living.level().isClientSide()) {
            return;
        }
        living.level().getServer().execute(
                () -> KraftWorkLockService
                        .restoreAfterEffects(living));
    }

    private static void clearOwner(LivingEntity owner) {
        if (owner instanceof ServerPlayer player) {
            KraftWorkPayloads.clear(player);
        }
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        KraftWorkLockService.releaseOwned(
                level, owner.getUUID());
        if (owner.hasData(KraftWorkAddon.LOCK_STATE)) {
            KraftWorkLockService.clearLockEffects(owner);
        }
        if (owner.hasData(KraftWorkAddon.USER_STATE)) {
            KraftWorkAddon.userState(owner).clearRuntime();
        }
    }
}
