package rotp.addons.killerqueenlion.client;

import rotp.addons.killerqueenlion.entity.SheerHeartAttackEntity;

public final class SheerHeartAttackVisuals {
    private SheerHeartAttackVisuals() {}

    public static float swellProgress(int swellTicks, int idleTicks) {
        // The blast sets the full idle count in the same tick as its final swell.
        if (idleTicks > 0 && idleTicks < SheerHeartAttackEntity.POST_EXPLOSION_IDLE_TICKS) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F,
                (float) swellTicks / SheerHeartAttackEntity.SWELL_TICKS));
    }
}
