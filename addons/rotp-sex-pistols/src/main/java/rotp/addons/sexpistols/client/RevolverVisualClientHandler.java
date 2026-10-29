package rotp.addons.sexpistols.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import rotp.addons.sexpistols.content.revolver.casing.CasingEjectionSpec;
import rotp.addons.sexpistols.network.RevolverBulletHolePayload;
import rotp.addons.sexpistols.network.RevolverCasingEjectPayload;
import rotp.addons.sexpistols.network.RevolverMuzzleFlashPayload;
import rotp.addons.sexpistols.network.SexPistolsKickMuzzleFlashPayload;
import rotp.addons.sexpistols.registry.SexPistolsParticles;
import rotp.addons.sexpistols.particle.BulletHoleParticleOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class RevolverVisualClientHandler {
    private static final int MAX_CASING_COUNT = 6;
    private static final int PENDING_KICK_TICKS = 40;
    private static final Map<Integer, PendingKickFlash>
            PENDING_KICK_FLASHES = new HashMap<>();

    private RevolverVisualClientHandler() {}

    public static void spawnMuzzleFlash(
            RevolverMuzzleFlashPayload payload) {
        SexPistolsVisualDiagnostics.recordMuzzlePayload(
                payload.entityId(), payload.aiming());
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(payload.entityId());
        if (!(entity instanceof LivingEntity shooter)) {
            return;
        }
        if (shooter == minecraft.player && payload.applyRecoil()) {
            RevolverClientPresentation.addLocalRecoil(
                    payload.aiming());
        }

        Vec3 look = shooter.getLookAngle();
        if (look.lengthSqr() < 1.0E-6D) {
            return;
        }
        look = look.normalize();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x);
        if (right.lengthSqr() < 1.0E-6D) {
            right = Vec3.directionFromRotation(
                    0.0F, shooter.getYRot() + 90.0F);
        }
        right = right.normalize();
        if (shooter.getMainArm() == HumanoidArm.LEFT) {
            right = right.reverse();
        }
        Vec3 up = right.cross(look);
        up = up.lengthSqr() < 1.0E-6D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : up.normalize();

        boolean aiming = payload.aiming();
        double sideOffset = aiming ? 0.02D : 0.22D;
        double upOffset = aiming ? -0.02D : -0.1D;
        double forwardOffset = aiming ? 0.92D : 0.78D;
        float partialTick = minecraft.getTimer()
                .getGameTimeDeltaPartialTick(true);
        Vec3 muzzle = shooter.getEyePosition(partialTick)
                .add(look.scale(forwardOffset))
                .add(right.scale(sideOffset))
                .add(up.scale(upOffset));
        boolean firstPerson = minecraft.getCameraEntity() == shooter
                && minecraft.options.getCameraType().isFirstPerson();
        SexPistolsVisualDiagnostics.recordMuzzleAnchor(
                muzzle, aiming, firstPerson);

        double spread = aiming ? 0.014D : 0.028D;
        Vec3 flashVelocity = look.scale(
                0.035D + payload.random() * 0.025D);
        level.addParticle(
                SexPistolsParticles.REVOLVER_MUZZLE_FLASH.get(),
                muzzle.x,
                muzzle.y,
                muzzle.z,
                flashVelocity.x,
                flashVelocity.y,
                flashVelocity.z);
        for (int i = 0; i < 2; i++) {
            double angle = payload.random() * Math.PI * 2.0D
                    + i * Math.PI;
            Vec3 offset = right.scale(Math.cos(angle) * spread)
                    .add(up.scale(Math.sin(angle) * spread));
            Vec3 position = muzzle.add(offset)
                    .add(look.scale(0.035D * i));
            Vec3 velocity = look.scale(0.025D)
                    .add(offset.scale(0.35D));
            level.addParticle(
                    SexPistolsParticles.REVOLVER_MUZZLE_FLASH.get(),
                    position.x,
                    position.y,
                    position.z,
                    velocity.x,
                    velocity.y,
                    velocity.z);
        }

        Vec3 critPosition = muzzle.subtract(look.scale(0.05D));
        level.addParticle(
                ParticleTypes.CRIT,
                critPosition.x,
                critPosition.y,
                critPosition.z,
                look.x * 0.015D,
                look.y * 0.015D,
                look.z * 0.015D);
        Vec3 smokePosition = muzzle.subtract(look.scale(0.1D));
        level.addParticle(
                ParticleTypes.SMOKE,
                smokePosition.x,
                smokePosition.y,
                smokePosition.z,
                look.x * 0.01D,
                look.y * 0.01D + 0.006D,
                look.z * 0.01D);
        SexPistolsVisualDiagnostics.recordMuzzleSupplementalSpawns();
    }

    public static void spawnKickFlash(
            SexPistolsKickMuzzleFlashPayload payload) {
        SexPistolsVisualDiagnostics.recordKickPayload(
                payload.entityId(),
                payload.bulletAnchor(),
                payload.critical());
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null
                || !(level.getEntity(payload.entityId())
                        instanceof LivingEntity shooter)) {
            PENDING_KICK_FLASHES.put(
                    payload.entityId(),
                    new PendingKickFlash(payload, PENDING_KICK_TICKS));
            SexPistolsVisualDiagnostics.recordKickPendingQueued();
            return;
        }
        spawnKickFlashForEntity(shooter, payload);
    }

    public static void spawnBulletHole(
            RevolverBulletHolePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.hasChunkAt(payload.blockPos())) {
            return;
        }
        BulletHoleParticle.spawn(
                new BulletHoleParticleOptions(
                        payload.direction(),
                        payload.blockPos()),
                level,
                payload.position());
    }

    public static void tickPendingKickFlashes() {
        if (PENDING_KICK_FLASHES.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        Iterator<Map.Entry<Integer, PendingKickFlash>> iterator =
                PENDING_KICK_FLASHES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, PendingKickFlash> entry =
                    iterator.next();
            PendingKickFlash pending = entry.getValue();
            if (level != null
                    && level.getEntity(entry.getKey())
                            instanceof LivingEntity shooter) {
                iterator.remove();
                SexPistolsVisualDiagnostics
                        .recordKickPendingConsumed();
                spawnKickFlashForEntity(shooter, pending.payload());
                continue;
            }
            if (pending.ticksLeft() <= 1) {
                iterator.remove();
                SexPistolsVisualDiagnostics
                        .recordKickPendingExpired();
            }
            else {
                entry.setValue(new PendingKickFlash(
                        pending.payload(), pending.ticksLeft() - 1));
            }
        }
    }

    public static void clearPendingKickFlashes() {
        PENDING_KICK_FLASHES.clear();
    }

    private static void spawnKickFlashForEntity(
            LivingEntity shooter,
            SexPistolsKickMuzzleFlashPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Vec3 look = shooter.getLookAngle();
        if (look.lengthSqr() < 1.0E-6D) {
            return;
        }
        look = look.normalize();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x);
        if (right.lengthSqr() < 1.0E-6D) {
            right = Vec3.directionFromRotation(
                    0.0F, shooter.getYRot() + 90.0F);
        }
        right = right.normalize();
        if (shooter.getMainArm() == HumanoidArm.LEFT) {
            right = right.reverse();
        }
        Vec3 up = right.cross(look);
        up = up.lengthSqr() < 1.0E-6D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : up.normalize();

        boolean bulletAnchor = payload.bulletAnchor();
        double sideOffset = bulletAnchor ? 0.02D : 0.22D;
        double upOffset = bulletAnchor ? -0.02D : -0.1D;
        double forwardOffset = bulletAnchor ? 0.92D : 0.78D;
        float partialTick = minecraft.getTimer()
                .getGameTimeDeltaPartialTick(true);
        Vec3 muzzle = shooter.getEyePosition(partialTick)
                .add(look.scale(forwardOffset))
                .add(right.scale(sideOffset))
                .add(up.scale(upOffset));
        boolean firstPerson = minecraft.getCameraEntity() == shooter
                && minecraft.options.getCameraType().isFirstPerson();
        SexPistolsVisualDiagnostics.recordKickAnchor(
                muzzle,
                bulletAnchor,
                firstPerson,
                payload.critical());

        SimpleParticleType particleType = payload.critical()
                ? SexPistolsParticles
                        .KICK_MUZZLE_FLASH_CRITICAL.get()
                : SexPistolsParticles.KICK_MUZZLE_FLASH.get();
        double spread = bulletAnchor ? 0.014D : 0.028D;
        Vec3 flashVelocity = look.scale(
                0.035D + payload.random() * 0.025D);
        level.addParticle(
                particleType,
                muzzle.x,
                muzzle.y,
                muzzle.z,
                flashVelocity.x,
                flashVelocity.y,
                flashVelocity.z);
        for (int i = 0; i < 2; i++) {
            double angle = payload.random() * Math.PI * 2.0D
                    + i * Math.PI;
            Vec3 offset = right.scale(Math.cos(angle) * spread)
                    .add(up.scale(Math.sin(angle) * spread));
            Vec3 position = muzzle.add(offset)
                    .add(look.scale(0.035D * i));
            Vec3 velocity = look.scale(0.025D)
                    .add(offset.scale(0.35D));
            level.addParticle(
                    particleType,
                    position.x,
                    position.y,
                    position.z,
                    velocity.x,
                    velocity.y,
                    velocity.z);
        }

        Vec3 critPosition = muzzle.subtract(look.scale(0.05D));
        level.addParticle(
                ParticleTypes.CRIT,
                critPosition.x,
                critPosition.y,
                critPosition.z,
                look.x * 0.015D,
                look.y * 0.015D,
                look.z * 0.015D);
        Vec3 smokePosition = muzzle.subtract(look.scale(0.1D));
        level.addParticle(
                ParticleTypes.SMOKE,
                smokePosition.x,
                smokePosition.y,
                smokePosition.z,
                look.x * 0.01D,
                look.y * 0.01D + 0.006D,
                look.z * 0.01D);
        SexPistolsVisualDiagnostics.recordKickSupplementalSpawns();
    }

    public static void ejectCasings(
            RevolverCasingEjectPayload payload) {
        int count = Math.min(MAX_CASING_COUNT, payload.specs().size());
        SexPistolsVisualDiagnostics.recordCasingPayload(
                payload.entityId(), count);
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || count <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            CasingEjectionSpec spec = payload.specs().get(i);
            RevolverCasingParticle.spawn(level, spec);
        }
    }

    private record PendingKickFlash(
            SexPistolsKickMuzzleFlashPayload payload,
            int ticksLeft) {}
}
