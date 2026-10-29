package rotp.addons.thehand.state;

import java.util.UUID;

import rotp.core.customobjects.RoadRollerEntity;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.thehand.TheHandAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class ErasedHealthService {
    public static final int DURATION_TICKS = 200;

    private ErasedHealthService() {}

    public static LivingEntity stateTarget(LivingEntity target) {
        if (target instanceof StandEntity standTarget
                && standTarget.getUser() != null) {
            return standTarget.getUser();
        }
        return target;
    }

    public static float absorptionBeforeHit(LivingEntity target) {
        return stateTarget(target).getAbsorptionAmount();
    }

    public static boolean applyAfterSuccessfulHit(
            StandEntity sourceStand,
            LivingEntity rawTarget,
            float intendedDamage,
            float absorptionBeforeHit) {
        if (!(sourceStand.level() instanceof ServerLevel)
                || rawTarget == null
                || !Float.isFinite(intendedDamage)) {
            return false;
        }
        LivingEntity sourceUser = sourceStand.getUser();
        LivingEntity target = stateTarget(rawTarget);
        float contribution = Math.max(
                intendedDamage - Math.max(absorptionBeforeHit, 0.0F),
                0.0F);
        if (sourceUser == null
                || !TheHandAddon.isTheHandUser(sourceUser)
                || !target.isAlive()
                || contribution <= 0.0F) {
            return false;
        }

        long now = target.level().getGameTime();
        ErasedHealthState state =
                TheHandAddon.erasedHealthState(target);
        float accumulated = target.hasEffect(
                        TheHandAddon.ERASED_EFFECT)
                && state.activeAt(now)
                ? state.erasedHealth() + contribution
                : contribution;
        if (!Float.isFinite(accumulated)) {
            accumulated = Float.MAX_VALUE;
        }
        state.applyServer(
                accumulated,
                now + DURATION_TICKS,
                sourceUser.getUUID(),
                sourceUser.level().dimension().location(),
                target.level().dimension().location());
        target.addEffect(
                new MobEffectInstance(
                        TheHandAddon.ERASED_EFFECT,
                        DURATION_TICKS,
                        0,
                        false,
                        false,
                        false),
                sourceUser);
        TheHandAddon.syncErasedHealth(target);
        return true;
    }

    public static boolean applyRoadRollerAfterSuccessfulHit(
            StandEntity sourceStand,
            RoadRollerEntity target,
            float intendedDamage) {
        if (!(sourceStand.level() instanceof ServerLevel)
                || target == null
                || !Float.isFinite(intendedDamage)
                || intendedDamage <= 0.0F) {
            return false;
        }
        LivingEntity sourceUser = sourceStand.getUser();
        if (sourceUser == null
                || !TheHandAddon.isTheHandUser(sourceUser)) {
            return false;
        }
        ErasedHealthState state =
                TheHandAddon.erasedHealthState(target);
        float erased = state.erasedHealth();
        float accumulated = erased + intendedDamage;
        if (!Float.isFinite(accumulated)) {
            accumulated = Float.MAX_VALUE;
        }
        state.applyServer(
                accumulated,
                Long.MAX_VALUE,
                sourceUser.getUUID(),
                sourceUser.level().dimension().location(),
                target.level().dimension().location());
        if (target.getMaxHealth() < erased + accumulated) {
            target.discard();
        }
        return true;
    }

    public static boolean validateServer(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel targetLevel)
                || !target.hasData(TheHandAddon.ERASED_HEALTH)) {
            return false;
        }
        ErasedHealthState state =
                TheHandAddon.erasedHealthState(target);
        long gameTime = targetLevel.getGameTime();
        if (!target.isAlive()
                || !state.activeAt(gameTime)
                || !state.isInTargetDimension(
                        targetLevel.dimension().location())
                || !target.hasEffect(TheHandAddon.ERASED_EFFECT)
                || !sourceStillOwnsErasure(targetLevel, state)) {
            clear(target);
            return false;
        }
        return true;
    }

    public static float healingCeiling(LivingEntity target) {
        if (!validateServer(target)) {
            return target.getMaxHealth();
        }
        return Math.max(
                target.getMaxHealth()
                        - TheHandAddon.erasedHealthState(target)
                                .erasedHealth(),
                0.0F);
    }

    public static boolean clear(LivingEntity target) {
        if (target.level().isClientSide()) {
            return false;
        }
        boolean stateChanged = target.hasData(
                        TheHandAddon.ERASED_HEALTH)
                && TheHandAddon.erasedHealthState(target).clear();
        boolean effectChanged = target.removeEffect(
                TheHandAddon.ERASED_EFFECT);
        if (stateChanged || effectChanged) {
            TheHandAddon.syncErasedHealth(target);
            return true;
        }
        return false;
    }

    public static int clearOwnedBy(
            MinecraftServer server, UUID ownerUuid) {
        if (server == null || ownerUuid == null) {
            return 0;
        }
        int cleared = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living
                        && living.hasData(TheHandAddon.ERASED_HEALTH)
                        && TheHandAddon.erasedHealthState(living)
                                .isOwnedBy(ownerUuid)
                        && clear(living)) {
                    cleared++;
                } else if (entity instanceof RoadRollerEntity
                        && entity.hasData(TheHandAddon.ERASED_HEALTH)
                        && TheHandAddon.erasedHealthState(entity)
                                .isOwnedBy(ownerUuid)
                        && TheHandAddon.erasedHealthState(entity)
                                .clear()) {
                    cleared++;
                }
            }
        }
        return cleared;
    }

    private static boolean sourceStillOwnsErasure(
            ServerLevel targetLevel, ErasedHealthState state) {
        UUID sourceUuid = state.sourceUser();
        ResourceLocation sourceDimension = state.sourceDimension();
        if (sourceUuid == null || sourceDimension == null) {
            return false;
        }
        ResourceKey<Level> sourceDimensionKey =
                ResourceKey.create(
                        Registries.DIMENSION, sourceDimension);
        ServerLevel sourceLevel =
                targetLevel.getServer().getLevel(sourceDimensionKey);
        Entity source = sourceLevel != null
                ? sourceLevel.getEntity(sourceUuid)
                : null;
        return source instanceof LivingEntity sourceLiving
                && sourceLiving.isAlive()
                && TheHandAddon.isTheHandUser(sourceLiving);
    }
}
