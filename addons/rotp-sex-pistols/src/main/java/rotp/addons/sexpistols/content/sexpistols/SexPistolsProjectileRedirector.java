package rotp.addons.sexpistols.content.sexpistols;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.sexpistols.config.SexPistolsConfig;
import rotp.addons.sexpistols.content.revolver.entity.RevolverBulletEntity;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsEntities;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

public final class SexPistolsProjectileRedirector {
    private static final String LAST_MEMBER =
            "SexPistolsLastTransferMember";
    private static final String LOCKED_TARGET =
            "SexPistolsLockedTarget";
    private static final String REDIRECTED_MASK =
            "SexPistolsRedirectedMask";
    private static final String RICOCHETS =
            "SexPistolsRicochets";

    private SexPistolsProjectileRedirector() {}

    public static boolean redirect(
            ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile instanceof RevolverBulletEntity
                || !(projectile.level()
                        instanceof ServerLevel level)
                || !(projectile.getOwner()
                        instanceof LivingEntity owner)
                || !owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            return false;
        }
        StandPower power = StandPower.get(owner);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        if (power == null
                || power.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()
                || !power.isSummoned()
                || !coordinator.isSummoned()) {
            return false;
        }

        Vec3 movement = projectile.getDeltaMovement();
        if (movement.lengthSqr() <= 1.0E-7D) {
            return false;
        }
        HitResult hit = event.getRayTraceResult();
        Vec3 hitPosition = hit.getLocation();
        SexPistolsEntity member = hitMember(
                projectile,
                owner,
                coordinator,
                hit,
                hitPosition,
                movement);
        if (member == null) {
            return false;
        }
        if (!payKickStamina(power, owner)) {
            // 1.16: an unpaid kick lets the shot pass through the struck Pistol.
            if (hit instanceof EntityHitResult entityHit
                    && entityHit.getEntity() == member) {
                event.setCanceled(true);
                return true;
            }
            // Pistol found only on the path: the real block/entity hit happens.
            return false;
        }

        LivingEntity target = findTarget(
                level,
                projectile,
                owner,
                coordinator.targetMode(),
                hitPosition);
        SexPistolsEntity relay = findRelay(
                level,
                projectile,
                member,
                target,
                coordinator,
                hitPosition);
        Vec3 aim = relay != null
                ? relay.getBoundingBox().getCenter()
                : target != null
                        ? target.getBoundingBox().getCenter()
                        : hitPosition.add(movement.normalize());
        Vec3 direction = aim.subtract(hitPosition);
        if (direction.lengthSqr() <= 1.0E-7D) {
            direction = owner.getLookAngle();
        }
        direction = direction.normalize();
        double speed = Math.max(0.25D, movement.length());
        projectile.setPos(
                hitPosition.add(direction.scale(0.35D)));
        projectile.setDeltaMovement(direction.scale(speed));
        projectile.setYRot((float) (Math.atan2(
                direction.x, direction.z) * 180.0D / Math.PI));
        projectile.setXRot((float) (Math.atan2(
                direction.y,
                Math.sqrt(
                        direction.x * direction.x
                                + direction.z * direction.z))
                * 180.0D / Math.PI));
        projectile.hasImpulse = true;

        CompoundTag data = projectile.getPersistentData();
        int slot = SexPistolsEntities.slotForNumber(
                member.getMemberNumber());
        if (slot >= 0) {
            data.putInt(
                    REDIRECTED_MASK,
                    data.getInt(REDIRECTED_MASK)
                            | 1 << slot);
        }
        data.putUUID(LAST_MEMBER, member.getUUID());
        if (target != null) {
            data.putUUID(LOCKED_TARGET, target.getUUID());
        }
        else {
            data.remove(LOCKED_TARGET);
        }
        data.putInt(
                RICOCHETS,
                Math.max(0, data.getInt(RICOCHETS)) + 1);
        member.performKick(direction);
        event.setCanceled(true);
        return true;
    }

    // Like 1.16 consumeKickStamina: without Resolve, a short bar is left untouched.
    private static boolean payKickStamina(
            StandPower power, LivingEntity owner) {
        float cost = RevolverBulletEntity.MEMBER_KICK_STAMINA;
        if (!power.isStaminaInfinite()
                && power.getStamina() < cost
                && ResolveModeEffect.getResolveEffectLvl(owner) < 0) {
            return false;
        }
        // Shared payment: no bar wipe, 10-tick regen pause.
        return SexPistolsStamina.tryConsume(power, owner, cost);
    }

    @Nullable
    private static SexPistolsEntity hitMember(
            Projectile projectile,
            LivingEntity owner,
            SexPistolsCoordinator coordinator,
            HitResult hit,
            Vec3 hitPosition,
            Vec3 movement) {
        if (hit instanceof EntityHitResult entityHit
                && entityHit.getEntity()
                        instanceof SexPistolsEntity member
                && member.isOwnedBy(owner)
                && member.canRedirectBullet()) {
            return member;
        }
        Vec3 start = projectile.position().subtract(movement);
        return coordinator.loadedMembers(
                        (ServerLevel) projectile.level())
                .stream()
                .filter(SexPistolsEntity::canRedirectBullet)
                .filter(member -> member.getBoundingBox()
                        .inflate(0.35D)
                        .clip(start, hitPosition)
                        .isPresent())
                .min(Comparator.comparingDouble(
                        member -> member.distanceToSqr(start)))
                .orElse(null);
    }

    @Nullable
    private static LivingEntity findTarget(
            ServerLevel level,
            Projectile projectile,
            LivingEntity owner,
            SexPistolsTargetMode mode,
            Vec3 origin) {
        double targetRange = SexPistolsConfig.targetSearchRange();
        CompoundTag data = projectile.getPersistentData();
        if (data.hasUUID(LOCKED_TARGET)
                && level.getEntity(
                        data.getUUID(LOCKED_TARGET))
                        instanceof LivingEntity locked
                && mode.matches(owner, locked)
                && locked.distanceToSqr(origin)
                        <= targetRange * targetRange
                && hasLineOfSight(
                        level,
                        projectile,
                        origin,
                        locked.getBoundingBox().getCenter())) {
            return locked;
        }
        AABB search = AABB.ofSize(
                origin,
                targetRange * 2.0D,
                targetRange * 2.0D,
                targetRange * 2.0D);
        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        search,
                        candidate -> mode.matches(
                                        owner, candidate)
                                && hasLineOfSight(
                                        level,
                                        projectile,
                                        origin,
                                        candidate.getBoundingBox()
                                                .getCenter()))
                .stream()
                .min(Comparator.comparingDouble(
                        candidate -> candidate
                                .distanceToSqr(origin)))
                .orElse(null);
    }

    @Nullable
    private static SexPistolsEntity findRelay(
            ServerLevel level,
            Projectile projectile,
            SexPistolsEntity source,
            @Nullable LivingEntity target,
            SexPistolsCoordinator coordinator,
            Vec3 origin) {
        SexPistolsTransferOrder order =
                coordinator.transferOrder();
        if (order == SexPistolsTransferOrder.NONE) {
            return null;
        }
        double transferRange = SexPistolsConfig.transferAssistRange();
        CompoundTag data = projectile.getPersistentData();
        UUID previous = data.hasUUID(LAST_MEMBER)
                ? data.getUUID(LAST_MEMBER)
                : null;
        int redirected = data.getInt(REDIRECTED_MASK);
        List<SexPistolsEntity> candidates =
                coordinator.loadedMembers(level).stream()
                        .filter(member -> member != source)
                        .filter(SexPistolsEntity::canRedirectBullet)
                        .filter(member -> previous == null
                                || !previous.equals(
                                        member.getUUID()))
                        .filter(member -> {
                            int slot = SexPistolsEntities
                                    .slotForNumber(
                                            member.getMemberNumber());
                            return slot >= 0
                                    && (redirected
                                            & 1 << slot) == 0;
                        })
                        .filter(member -> member.distanceToSqr(origin)
                                <= transferRange * transferRange)
                        .filter(member -> hasLineOfSight(
                                level,
                                projectile,
                                origin,
                                member.getBoundingBox().getCenter()))
                        .toList();
        if (order == SexPistolsTransferOrder.NUMBER) {
            return candidates.stream()
                    .filter(member -> member.getMemberNumber()
                            > source.getMemberNumber())
                    .min(Comparator.comparingInt(
                            SexPistolsEntity::getMemberNumber))
                    .orElse(null);
        }
        if (target == null) {
            return null;
        }
        Vec3 targetPosition =
                target.getBoundingBox().getCenter();
        double sourceDistance =
                source.distanceToSqr(targetPosition);
        return candidates.stream()
                .filter(member -> member.distanceToSqr(
                        targetPosition) < sourceDistance)
                .min(Comparator.comparingDouble(
                        member -> member.distanceToSqr(
                                targetPosition)))
                .orElse(null);
    }

    private static boolean hasLineOfSight(
            ServerLevel level,
            Entity context,
            Vec3 start,
            Vec3 end) {
        return level.clip(new ClipContext(
                        start,
                        end,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        context))
                .getType() == HitResult.Type.MISS;
    }
}
