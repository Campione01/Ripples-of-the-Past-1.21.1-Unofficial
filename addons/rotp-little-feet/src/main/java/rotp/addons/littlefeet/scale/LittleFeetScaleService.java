package rotp.addons.littlefeet.scale;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.littlefeet.LittleFeetAddon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import virtuoel.pehkui.api.ScaleData;

public final class LittleFeetScaleService {
    public static final float MIN_SCALE =
            LittleFeetScaleMath.MIN_SCALE;
    public static final float TARGET_SHRINK_STEP =
            LittleFeetScaleMath.TARGET_SHRINK_STEP;
    public static final int TARGET_SHRINK_INTERVAL =
            LittleFeetScaleMath.TARGET_SHRINK_INTERVAL;
    public static final float SELF_SLOW_STEP =
            LittleFeetScaleMath.SELF_SLOW_STEP;

    private static final LittleFeetScaleOwnership OWNERSHIP =
            new LittleFeetScaleOwnership();
    private static final Set<LivingEntity> PENDING_JOIN_REFRESH =
            Collections.synchronizedSet(
                    Collections.newSetFromMap(new WeakHashMap<>()));

    private LittleFeetScaleService() {}

    public static void shrinkAfterHit(
            LivingEntity owner, LivingEntity hitTarget) {
        if (owner.level().isClientSide()
                || hitTarget == null
                || !owner.isAlive()) {
            return;
        }
        LivingEntity target = canonicalTarget(hitTarget);
        if (target == owner || !target.isAlive()) {
            return;
        }

        UUID ownerId = owner.getUUID();
        UUID targetId = target.getUUID();
        Object stand = standOf(owner);
        // A hit before the owner's tick after a Stand change.
        if (OWNERSHIP.isStandReplaced(ownerId, stand)) {
            clearOwner(owner);
        }
        OWNERSHIP.track(
                ownerId,
                targetId,
                target.level().getGameTime()
                        + TARGET_SHRINK_INTERVAL);
        OWNERSHIP.bindStand(ownerId, stand);
    }

    public static void setSelfSmall(LivingEntity user) {
        if (!user.level().isClientSide()) {
            setBodyScale(user, MIN_SCALE);
            playShrinkSound(user);
        }
    }

    public static void setSelfNormal(LivingEntity user) {
        if (!user.level().isClientSide()) {
            resetScale(user);
        }
    }

    public static void changeSelfSlowly(
            LivingEntity user, boolean shrinking) {
        if (user.level().isClientSide()) {
            return;
        }
        float current = currentTargetScale(user);
        float next = LittleFeetScaleMath.nextSelfScale(
                current, shrinking);
        if (next >= 1.0F) {
            resetScale(user);
        } else {
            setBodyScale(user, next);
        }
    }

    public static void growNearest(LivingEntity owner) {
        Set<UUID> targetIds =
                OWNERSHIP.targets(owner.getUUID());
        if (targetIds.isEmpty()) {
            return;
        }
        MinecraftServer server = owner.getServer();
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (UUID targetId : new ArrayList<>(targetIds)) {
            LivingEntity target = findLiving(server, targetId);
            if (target == null) {
                removeTargetId(targetId);
                continue;
            }
            if (target.level() == owner.level()) {
                double distance = owner.distanceToSqr(target);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = target;
                }
            }
        }
        if (nearest != null) {
            clearLease(owner.getUUID(), nearest);
        }
    }

    public static void growAll(LivingEntity owner) {
        clearOwner(owner);
    }

    public static void tickLiving(LivingEntity living) {
        if (living.level().isClientSide()) {
            return;
        }
        if (PENDING_JOIN_REFRESH.remove(living)) {
            LittleFeetScaleTypes.scale().getScaleData(living).onUpdate();
            LittleFeetScaleTypes.targetHealthScale().getScaleData(living).onUpdate();
        }
        if (living instanceof StandEntity stand) {
            syncStandScale(stand);
            return;
        }

        Set<LittleFeetScaleOwnership.TargetLease> leases =
                OWNERSHIP.leases(living.getUUID());
        if (!leases.isEmpty()) {
            tickTarget(living, leases);
        }

        if (OWNERSHIP.hasOwner(living.getUUID())
                && (!LittleFeetAddon.isLittleFeetUser(living)
                        || OWNERSHIP.isStandReplaced(
                                living.getUUID(), standOf(living)))) {
            clearOwner(living);
        }
        if (!LittleFeetAddon.isLittleFeetUser(living)
                && !OWNERSHIP.hasTarget(living.getUUID())
                && currentTargetScale(living) < 1.0F) {
            resetScale(living);
        }
    }

    public static void onEntityJoin(LivingEntity living) {
        if (living.level().isClientSide()) {
            return;
        }
        // Clear old saved flags before tracking, but defer collision-sensitive
        // dimension callbacks until the entity can tick after chunk insertion.
        ScaleData data = LittleFeetScaleTypes.scale().getScaleData(living);
        ScaleData healthData = LittleFeetScaleTypes.targetHealthScale().getScaleData(living);
        if (!data.isReset() || !healthData.isReset()) {
            data.resetScale(false);
            healthData.resetScale(false);
            PENDING_JOIN_REFRESH.add(living);
        }
        if (living instanceof StandEntity stand && stand.getUser() != null) {
            ScaleData userData = LittleFeetScaleTypes.scale().getScaleData(stand.getUser());
            if (userData.getTargetScale() < 1.0F) {
                // Pairing can send the copied values before the safe Post-tick geometry refresh.
                data.fromScale(userData, false);
                if (data.getPersistence() != null) {
                    data.setPersistence(null);
                }
                PENDING_JOIN_REFRESH.add(living);
            }
        }
    }

    public static void clearEntity(LivingEntity living) {
        if (living.level().isClientSide()) {
            return;
        }
        PENDING_JOIN_REFRESH.remove(living);
        if (living instanceof StandEntity) {
            resetScale(living);
            return;
        }
        clearTarget(living);
        clearOwner(living);
        resetScale(living);
    }

    public static void clearRuntimeState() {
        PENDING_JOIN_REFRESH.clear();
        OWNERSHIP.clear();
    }

    public static boolean isOwnedTarget(LivingEntity target) {
        return OWNERSHIP.hasTarget(
                canonicalTarget(target).getUUID());
    }

    public static int ownedTargetCount(LivingEntity owner) {
        return OWNERSHIP.targetCount(owner.getUUID());
    }

    private static void tickTarget(
            LivingEntity target,
            Set<LittleFeetScaleOwnership.TargetLease> leases) {
        MinecraftServer server = target.getServer();
        if (!target.isAlive()) {
            clearTarget(target);
            return;
        }

        long now = target.level().getGameTime();
        for (LittleFeetScaleOwnership.TargetLease state : leases) {
            LivingEntity owner =
                    findLiving(server, state.ownerId());
            if (owner == null
                    || !owner.isAlive()
                    || !LittleFeetAddon.isLittleFeetUser(owner)
                    || OWNERSHIP.isStandReplaced(
                            state.ownerId(), standOf(owner))) {
                OWNERSHIP.remove(
                        state.ownerId(), target.getUUID());
                resetScale(target);
                continue;
            }
            if (now < state.nextShrinkTick()) {
                continue;
            }
            float current = currentTargetScale(target);
            if (current > MIN_SCALE) {
                setTargetScale(
                        target,
                        LittleFeetScaleMath.nextTargetShrink(
                                current));
                playShrinkSound(target);
            }
            OWNERSHIP.advance(
                    state.ownerId(),
                    target.getUUID(),
                    now + TARGET_SHRINK_INTERVAL);
        }
    }

    private static void clearOwner(LivingEntity owner) {
        Set<UUID> targetIds =
                OWNERSHIP.removeOwner(owner.getUUID());
        if (targetIds.isEmpty()) {
            return;
        }
        MinecraftServer server = owner.getServer();
        for (UUID targetId : targetIds) {
            LivingEntity target = findLiving(server, targetId);
            if (target != null) {
                resetScale(target);
            }
        }
    }

    private static void clearTarget(LivingEntity target) {
        UUID targetId = canonicalTarget(target).getUUID();
        OWNERSHIP.removeTarget(targetId);
        LivingEntity canonical = canonicalTarget(target);
        resetScale(canonical);
    }

    private static void clearLease(
            UUID ownerId, LivingEntity target) {
        LivingEntity canonical = canonicalTarget(target);
        OWNERSHIP.remove(ownerId, canonical.getUUID());
        resetScale(canonical);
    }

    private static void removeTargetId(UUID targetId) {
        OWNERSHIP.removeTarget(targetId);
    }

    // The core swaps this object on every Stand change
    // (StandPower.setStandInstance), e.g. /stand give ... true.
    private static Object standOf(LivingEntity owner) {
        StandPower power = StandPower.get(owner);
        return power != null
                ? power.getStandInstance().orElse(null)
                : null;
    }

    private static LivingEntity canonicalTarget(
            LivingEntity target) {
        if (target instanceof StandEntity stand
                && stand.getUser() != null) {
            return stand.getUser();
        }
        return target;
    }

    // Runs every tick for every Stand. Each Pehkui write marks the Stand
    // for a pehkui:scale sync to every tracking player and refreshes its
    // dimensions, so only a change is written. Nothing needs the per-tick
    // refresh: Stand dimensions are fixed per Stand type and vanilla
    // refreshes them on a pose change.
    private static void syncStandScale(StandEntity stand) {
        LivingEntity user = stand.getUser();
        float userScale = user != null
                ? currentTargetScale(user)
                : 1.0F;
        if (userScale < 1.0F) {
            if (!isSteadyAt(stand, userScale)) {
                setBodyScale(stand, userScale);
            }
        } else {
            resetChangedScale(stand);
        }
    }

    // A second setBodyScale with the same value leaves the data as it is.
    static boolean isSteadyAt(ScaleData data, float requestedScale) {
        float scale = LittleFeetScaleMath.clampScale(requestedScale);
        return data.getScaleTickDelay() == 1
                && data.getBaseScale() == scale
                && data.getTargetScale() == scale
                && data.getInitialScale() == scale
                && data.getPrevBaseScale() == scale;
    }

    private static boolean isSteadyAt(
            LivingEntity entity, float requestedScale) {
        return isSteadyAt(
                LittleFeetScaleTypes.scale().getScaleData(entity),
                requestedScale);
    }

    // resetScale() on data already at its defaults only re-syncs it.
    static void resetIfChanged(ScaleData data) {
        if (!data.isReset()) {
            data.resetScale();
        }
    }

    private static void resetChangedScale(LivingEntity entity) {
        resetIfChanged(
                LittleFeetScaleTypes.scale().getScaleData(entity));
        resetIfChanged(LittleFeetScaleTypes.targetHealthScale()
                .getScaleData(entity));
    }

    private static LivingEntity findLiving(
            MinecraftServer server, UUID entityId) {
        if (server == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private static float currentTargetScale(LivingEntity entity) {
        return LittleFeetScaleTypes.scale()
                .getScaleData(entity)
                .getTargetScale();
    }

    // Persistence is never set here: both types default to non-persistent,
    // and resetScale() writes null. A false-then-null flip races Pehkui's
    // Netty-thread ScaleData.toPacket (two reads of the field) and kicked
    // LAN clients with an NPE on pehkui:scale.
    private static void setBodyScale(
            LivingEntity entity, float requestedScale) {
        float scale =
                LittleFeetScaleMath.clampScale(requestedScale);
        ScaleData data =
                LittleFeetScaleTypes.scale().getScaleData(entity);
        data.setScaleTickDelay(1);
        data.setScale(scale);
    }

    private static void setTargetScale(
            LivingEntity entity, float requestedScale) {
        float scale =
                LittleFeetScaleMath.clampScale(requestedScale);
        setBodyScale(entity, scale);
        ScaleData healthData =
                LittleFeetScaleTypes.targetHealthScale()
                        .getScaleData(entity);
        healthData.setScaleTickDelay(1);
        healthData.setScale(scale);
    }

    private static void resetScale(LivingEntity entity) {
        ScaleData data =
                LittleFeetScaleTypes.scale().getScaleData(entity);
        data.resetScale();
        ScaleData healthData =
                LittleFeetScaleTypes.targetHealthScale()
                        .getScaleData(entity);
        healthData.resetScale();
    }

    private static void playShrinkSound(LivingEntity target) {
        target.playSound(
                LittleFeetAddon.LITTLE_FEET_SHRINK2.get(),
                1.0F,
                1.0F);
    }
}
