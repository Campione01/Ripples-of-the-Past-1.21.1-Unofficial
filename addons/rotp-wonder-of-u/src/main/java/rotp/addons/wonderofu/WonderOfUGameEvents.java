package rotp.addons.wonderofu;

import java.util.HashSet;
import java.util.Set;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.wonderofu.ability.CalamityActiveAbility;
import rotp.addons.wonderofu.ability.CarCalamityAbility;
import rotp.addons.wonderofu.entity.CalamityHazardAuthority;
import rotp.addons.wonderofu.entity.CarCalamityEntity;
import rotp.addons.wonderofu.entity.CarCalamityEntity.CarRemovalReason;
import rotp.addons.wonderofu.entity.WonderOfUStandEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = WonderOfUAddon.MOD_ID)
public final class WonderOfUGameEvents {
    private static final float INCOMING_DAMAGE_MULTIPLIER = 0.2F;
    private static final int EFFECT_DURATION_TICKS = 100;
    private static final double ACTIVE_RADIUS = 6.0D;
    private static final float ACTIVE_AGGRESSOR_DAMAGE = 2.0F;
    private static final float ACTIVE_NEARBY_DAMAGE = 0.5F;

    private WonderOfUGameEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                WonderOfUAddon.WONDER_OF_YOU_ENTITY.get(),
                StandEntity.createAttributes().build());
        event.put(
                WonderOfUAddon.CAR_CALAMITY_ENTITY.get(),
                CarCalamityEntity.createAttributes().build());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        if (event.getSource().is(
                WonderOfUDamageTypes.CALAMITY_CAR)) {
            CarCalamityEntity car = resolveCarSource(
                    event.getSource());
            if (car == null || !car.canDamage(victim)) {
                event.setCanceled(true);
            }
            return;
        }
        if (!victim.isAlive()
                || event.getAmount() <= 0.0F
                || isCalamityDamage(event.getSource())) {
            return;
        }

        WonderOfUStandEntity carStand =
                carCalamityStand(victim);
        LivingEntity carAttacker = resolveCarAttacker(
                event.getSource());
        if (carStand != null
                && carAttacker != null
                && carStand.interceptAttackWithCar(carAttacker)) {
            event.setCanceled(true);
            return;
        }

        WonderOfUStandEntity stand = passiveStand(victim);
        if (stand == null) {
            return;
        }

        float preReductionAmount = event.getAmount();
        event.setAmount(
                preReductionAmount * INCOMING_DAMAGE_MULTIPLIER);

        LivingEntity attacker = resolveLivingAttacker(
                event.getSource());
        if (!canReflect(victim, attacker, stand)) {
            return;
        }

        DamageSource reflectionSource = DamageUtil.make(
                victim.level(),
                WonderOfUDamageTypes.CALAMITY_REFLECTION,
                stand,
                victim);
        if (attacker.isInvulnerable()
                || attacker.isInvulnerableTo(reflectionSource)) {
            return;
        }

        if (attacker.hurt(reflectionSource, preReductionAmount)) {
            attacker.addEffect(new MobEffectInstance(
                    MobEffects.POISON,
                    EFFECT_DURATION_TICKS,
                    3));
            attacker.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    EFFECT_DURATION_TICKS,
                    2));
            attacker.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS,
                    EFFECT_DURATION_TICKS,
                    0));
        }
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(
            LivingChangeTargetEvent event) {
        LivingEntity user = event.getNewAboutToBeSetTarget();
        if (user == null || user.level().isClientSide()) {
            return;
        }
        WonderOfUStandEntity stand = activeCalamityStand(user);
        LivingEntity aggressor = resolveLiving(event.getEntity());
        if (stand == null || !canAffect(user, aggressor, stand)) {
            return;
        }

        DamageSource calamitySource = activeCalamitySource(
                user, stand);
        applyTargetChangeAreaEffects(user, stand);
        if (!aggressor.isInvulnerableTo(calamitySource)
                && aggressor.hurt(
                        calamitySource, ACTIVE_AGGRESSOR_DAMAGE)) {
            aggressor.addEffect(new MobEffectInstance(
                    MobEffects.POISON,
                    EFFECT_DURATION_TICKS,
                    1));
            aggressor.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    EFFECT_DURATION_TICKS,
                    2));
            aggressor.addEffect(new MobEffectInstance(
                    MobEffects.CONFUSION,
                    60,
                    0));
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(
            ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide()
                || !(event.getRayTraceResult()
                        instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity()
                        instanceof LivingEntity user)) {
            return;
        }
        WonderOfUStandEntity stand = activeCalamityStand(user);
        if (stand == null) {
            return;
        }

        LivingEntity owner = resolveLiving(projectile.getOwner());
        if (owner != null && !canAffect(user, owner, stand)) {
            return;
        }
        projectile.discard();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player user = event.getEntity();
        if (user.level().isClientSide()) {
            return;
        }
        WonderOfUStandEntity carStand =
                carCalamityStand(user);
        WonderOfUStandEntity stand = activeCalamityStand(user);
        StandPower power = carStand != null || stand != null
                ? StandPower.get(user)
                : null;
        if (carStand != null) {
            if (!CarCalamityAbility.drainStamina(power)) {
                carStand.setCarEnabled(false);
            }
        }

        if (stand == null) {
            return;
        }
        if (!CalamityActiveAbility.drainStamina(power)) {
            stand.setActiveEnabled(false);
            return;
        }

        DamageSource calamitySource = activeCalamitySource(
                user, stand);
        for (Player target : user.level().getEntitiesOfClass(
                Player.class,
                user.getBoundingBox().inflate(ACTIVE_RADIUS),
                target -> canAffect(user, target, stand))) {
            if (target.isInvulnerableTo(calamitySource)
                    || !target.hurt(
                            calamitySource, ACTIVE_NEARBY_DAMAGE)) {
                continue;
            }
            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    EFFECT_DURATION_TICKS,
                    3));
            target.addEffect(new MobEffectInstance(
                    MobEffects.CONFUSION,
                    60,
                    0));
            target.addEffect(new MobEffectInstance(
                    MobEffects.POISON,
                    EFFECT_DURATION_TICKS,
                    1));
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!event.getLevel().isClientSide()
                && CalamityHazardAuthority.isBound(entity)
                && !CalamityHazardAuthority.isAuthorized(entity)) {
            entity.discard();
        }
    }

    @SubscribeEvent
    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (!entity.level().isClientSide()
                && CalamityHazardAuthority.isBound(entity)
                && !CalamityHazardAuthority.isAuthorized(entity)) {
            entity.discard();
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity()
                        instanceof WonderOfUStandEntity stand) {
            stand.clearOwnedState(CarRemovalReason.STAND_REMOVED);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        clearStandState(
                event.getEntity(), CarRemovalReason.OWNER_DEATH);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        clearStandState(
                event.getOriginal(), CarRemovalReason.OWNER_DEATH);
        clearStandState(
                event.getEntity(), CarRemovalReason.OWNER_DEATH);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        clearStandState(
                event.getEntity(), CarRemovalReason.OWNER_DEATH);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(
            PlayerEvent.PlayerLoggedOutEvent event) {
        clearStandState(
                event.getEntity(), CarRemovalReason.OWNER_LOGOUT);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        clearStandState(
                event.getEntity(), CarRemovalReason.DIMENSION_CHANGE);
    }

    private static WonderOfUStandEntity passiveStand(
            LivingEntity victim) {
        StandPower power = StandPower.get(victim);
        if (power == null
                || power.getPowerType() != WonderOfUAddon.WONDER_OF_YOU.get()
                || !(power.getSummonedStandEntity()
                        instanceof WonderOfUStandEntity stand)
                || stand.getUser() != victim
                || !stand.isAlive()
                || stand.isRemoved()
                || !stand.isPassiveEnabled()) {
            return null;
        }
        return stand;
    }

    private static WonderOfUStandEntity activeCalamityStand(
            LivingEntity user) {
        StandPower power = StandPower.get(user);
        if (power == null
                || power.getPowerType() != WonderOfUAddon.WONDER_OF_YOU.get()
                || !(power.getSummonedStandEntity()
                        instanceof WonderOfUStandEntity stand)
                || stand.getUser() != user
                || !stand.isAlive()
                || stand.isRemoved()
                || !stand.isActiveEnabled()) {
            return null;
        }
        return stand;
    }

    private static WonderOfUStandEntity carCalamityStand(
            LivingEntity victim) {
        LivingEntity user = resolveLiving(victim);
        StandPower power = user != null ? StandPower.get(user) : null;
        if (power == null
                || power.getPowerType()
                        != WonderOfUAddon.WONDER_OF_YOU.get()
                || !(power.getSummonedStandEntity()
                        instanceof WonderOfUStandEntity stand)
                || stand.getUser() != user
                || (victim != user && victim != stand)
                || !stand.isAlive()
                || stand.isRemoved()
                || !stand.isCarEnabled()) {
            return null;
        }
        return stand;
    }

    private static LivingEntity resolveLivingAttacker(
            DamageSource source) {
        LivingEntity resolved = resolveLiving(source.getEntity());
        return resolved != null
                ? resolved
                : resolveLiving(source.getDirectEntity());
    }

    private static LivingEntity resolveCarAttacker(
            DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity living) {
            return living;
        }
        Entity direct = source.getDirectEntity();
        return direct instanceof LivingEntity living
                ? living
                : null;
    }

    private static CarCalamityEntity resolveCarSource(
            DamageSource source) {
        if (source.getDirectEntity()
                instanceof CarCalamityEntity car) {
            return car;
        }
        return source.getEntity() instanceof CarCalamityEntity car
                ? car
                : null;
    }

    private static boolean isCalamityDamage(
            DamageSource source) {
        return source.is(
                        WonderOfUDamageTypes.CALAMITY_REFLECTION)
                || source.is(
                        WonderOfUDamageTypes.CALAMITY_ACTIVE)
                || source.is(
                        WonderOfUDamageTypes.CALAMITY_CAR);
    }

    public static LivingEntity resolveLiving(Entity entity) {
        if (entity instanceof StandEntity stand) {
            return stand.getUser();
        }
        return entity instanceof LivingEntity living ? living : null;
    }

    private static boolean canReflect(
            LivingEntity victim,
            LivingEntity attacker,
            WonderOfUStandEntity stand) {
        if (attacker == null
                || attacker == victim
                || attacker == stand
                || attacker.level() != victim.level()
                || attacker.isRemoved()
                || !attacker.isAlive()) {
            return false;
        }
        if (attacker instanceof Player player
                && (player.isCreative() || player.isSpectator())) {
            return false;
        }

        PlayerTeam victimTeam = victim.getTeam();
        PlayerTeam attackerTeam = attacker.getTeam();
        if (victimTeam != null
                && victimTeam.isAlliedTo(attackerTeam)
                && !victimTeam.isAllowFriendlyFire()) {
            return false;
        }

        return !(victim instanceof Player
                        && attacker instanceof Player)
                || victim.getServer() == null
                || victim.getServer().isPvpAllowed();
    }

    private static DamageSource activeCalamitySource(
            LivingEntity user, WonderOfUStandEntity stand) {
        return DamageUtil.make(
                user.level(),
                WonderOfUDamageTypes.CALAMITY_ACTIVE,
                stand,
                user);
    }

    public static boolean canAffect(
            LivingEntity user,
            LivingEntity target,
            WonderOfUStandEntity stand) {
        if (target == null
                || target == user
                || target == stand
                || target.level() != user.level()
                || target.isRemoved()
                || !target.isAlive()
                || target.isInvulnerable()) {
            return false;
        }
        if (target instanceof Player player
                && (player.isCreative() || player.isSpectator())) {
            return false;
        }

        PlayerTeam userTeam = user.getTeam();
        PlayerTeam targetTeam = target.getTeam();
        if (userTeam != null
                && userTeam.isAlliedTo(targetTeam)
                && !userTeam.isAllowFriendlyFire()) {
            return false;
        }

        return !(user instanceof Player && target instanceof Player)
                || user.getServer() == null
                || user.getServer().isPvpAllowed();
    }

    private static void applyTargetChangeAreaEffects(
            LivingEntity user, WonderOfUStandEntity stand) {
        Set<Integer> affected = new HashSet<>();
        for (LivingEntity nearby :
                user.level().getEntitiesOfClass(
                        LivingEntity.class,
                        user.getBoundingBox().inflate(ACTIVE_RADIUS))) {
            LivingEntity relationTarget = resolveLiving(nearby);
            if (!canAffect(user, relationTarget, stand)
                    || !affected.add(nearby.getId())) {
                continue;
            }
            nearby.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    EFFECT_DURATION_TICKS,
                    3));
            nearby.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS,
                    60,
                    0));
        }
    }

    private static void clearStandState(
            LivingEntity user, CarRemovalReason reason) {
        if (user == null || user.level().isClientSide()) {
            return;
        }
        StandPower power = StandPower.get(user);
        if (power != null
                && power.getSummonedStandEntity()
                        instanceof WonderOfUStandEntity stand
                && stand.getUser() == user) {
            stand.clearOwnedState(reason);
        }
    }
}
