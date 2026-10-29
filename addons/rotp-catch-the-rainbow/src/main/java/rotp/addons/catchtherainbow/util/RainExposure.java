package rotp.addons.catchtherainbow.util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public final class RainExposure {
    private static final double[][] HORIZONTAL_SAMPLES = {
        {0.0D, 0.0D},
        {0.45D, 0.0D},
        {-0.45D, 0.0D},
        {0.0D, 0.45D},
        {0.0D, -0.45D}
    };

    private RainExposure() {}

    public static boolean isExposedToRain(Entity entity) {
        if (entity == null
                || entity.level() == null
                || !entity.level().isRaining()) {
            return false;
        }

        AABB bounds = entity.getBoundingBox();
        int topY = Mth.floor(bounds.maxY + 0.25D);
        BlockPos.MutableBlockPos sample =
                new BlockPos.MutableBlockPos();
        for (double[] offset : HORIZONTAL_SAMPLES) {
            sample.set(
                    Mth.floor(entity.getX() + offset[0]),
                    topY,
                    Mth.floor(entity.getZ() + offset[1]));
            if (entity.level().isRainingAt(sample)) {
                return true;
            }
        }

        sample.set(
                Mth.floor(entity.getX()),
                Mth.floor(bounds.minY),
                Mth.floor(entity.getZ()));
        return entity.level().isRainingAt(sample);
    }
}
