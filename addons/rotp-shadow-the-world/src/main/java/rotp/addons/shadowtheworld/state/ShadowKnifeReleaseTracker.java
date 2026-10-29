package rotp.addons.shadowtheworld.state;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import rotp.core.customobjects.entity_projectile.KnifeEntity;

import net.minecraft.world.phys.Vec3;

public final class ShadowKnifeReleaseTracker {
    private static final Map<UUID, Release> RELEASES =
            new HashMap<>();

    private ShadowKnifeReleaseTracker() {}

    public static void schedule(
            KnifeEntity knife,
            Vec3 target,
            int delayTicks,
            int lifetimeTicks) {
        RELEASES.put(
                knife.getUUID(),
                new Release(
                        target,
                        Math.max(delayTicks, 0),
                        Math.max(lifetimeTicks, 1)));
    }

    public static void tick(KnifeEntity knife) {
        Release release = RELEASES.get(knife.getUUID());
        if (release == null) {
            return;
        }
        if (knife.isRemoved()) {
            RELEASES.remove(knife.getUUID());
            return;
        }
        release.lifetime--;
        if (release.lifetime <= 0) {
            RELEASES.remove(knife.getUUID());
            knife.discard();
            return;
        }
        if (release.released) {
            return;
        }
        if (release.delay-- > 0) {
            knife.setDeltaMovement(Vec3.ZERO);
            return;
        }
        release.released = true;
        knife.setNoGravity(false);
        Vec3 direction = release.target
                .subtract(knife.position())
                .normalize();
        knife.shoot(
                direction.x,
                direction.y,
                direction.z,
                1.5F,
                0.5F);
    }

    private static final class Release {
        private final Vec3 target;
        private int delay;
        private int lifetime;
        private boolean released;

        private Release(
                Vec3 target, int delay, int lifetime) {
            this.target = target;
            this.delay = delay;
            this.lifetime = lifetime;
        }
    }
}
