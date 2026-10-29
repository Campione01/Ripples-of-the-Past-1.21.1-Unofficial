package rotp.addons.stand_bearing_creatures.event;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

// 1.16 050 StandEntityMixin.defaultRotation: a mob's Stand faces the mob's target, dead or alive, and sets no rotation
// without one; clients know no target, so they keep the rotation their server sent
public final class MobStandRotation {
    private MobStandRotation() {}

    public static boolean rotate(StandEntity stand, LivingEntity user) {
        if (!(user instanceof Mob mob)) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (target != null) {
            double dx = target.getX() - stand.getX();
            double dy = target.getEyeY() - stand.getEyeY();
            double dz = target.getZ() - stand.getZ();
            stand.setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
            stand.setXRot((float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
        }
        return true;
    }
}
