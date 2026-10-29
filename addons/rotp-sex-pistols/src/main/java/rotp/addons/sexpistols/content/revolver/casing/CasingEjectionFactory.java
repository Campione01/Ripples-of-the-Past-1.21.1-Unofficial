package rotp.addons.sexpistols.content.revolver.casing;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

final class CasingEjectionFactory {

    private CasingEjectionFactory() {}

    static List<CasingEjectionSpec> create(
            ServerPlayer player, int requestedCount) {
        long seed = player.getRandom().nextLong()
                ^ Long.rotateLeft(player.level().getGameTime(), 17)
                ^ player.getUUID().getMostSignificantBits();
        return create(
                player.position(),
                player.getEyeHeight(),
                player.getLookAngle(),
                requestedCount,
                seed,
                player.level().getGameTime());
    }

    static List<CasingEjectionSpec> create(
            Vec3 playerPosition,
            float eyeHeight,
            Vec3 look,
            int requestedCount,
            long seed,
            long spawnGameTime) {
        int count = Math.min(
                CasingEjectionSpec.MAX_CASING_COUNT,
                Math.max(0, requestedCount));
        if (count == 0) {
            return List.of();
        }

        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-6D) {
            horizontalLook = new Vec3(0.0D, 0.0D, 1.0D);
        }
        horizontalLook = horizontalLook.normalize();
        Vec3 right = new Vec3(
                -horizontalLook.z, 0.0D, horizontalLook.x)
                .normalize();
        Vec3 basePosition = playerPosition
                .add(horizontalLook.scale(0.34D))
                .add(right.scale(0.42D))
                .add(0.0D, eyeHeight - 0.34D, 0.0D);

        RandomSource random = RandomSource.create(seed);
        List<CasingEjectionSpec> specs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double spread = (i - (count - 1) * 0.5D) * 0.055D;
            Vec3 position = basePosition
                    .add(right.scale(
                            spread
                                    + (random.nextDouble() - 0.5D)
                                            * 0.035D))
                    .add(horizontalLook.scale(
                            (random.nextDouble() - 0.5D) * 0.035D))
                    .add(
                            0.0D,
                            i * 0.018D
                                    + random.nextDouble() * 0.025D,
                            0.0D);
            Vec3 velocity = right.scale(
                            0.18D + random.nextDouble() * 0.1D)
                    .add(horizontalLook.scale(
                            -0.03D + random.nextDouble() * 0.06D))
                    .add(
                            0.0D,
                            0.11D + random.nextDouble() * 0.09D,
                            0.0D);
            specs.add(new CasingEjectionSpec(
                    random.nextLong(),
                    spawnGameTime,
                    position.x,
                    position.y,
                    position.z,
                    velocity.x,
                    velocity.y,
                    velocity.z,
                    CasingEjectionSpec.MIN_LIFETIME_TICKS
                            + random.nextInt(16),
                    CasingEjectionSpec.MIN_SIZE
                            + random.nextFloat() * 0.03F,
                    random.nextInt(4),
                    0.92F + random.nextFloat() * 0.22F));
        }
        return List.copyOf(specs);
    }
}
