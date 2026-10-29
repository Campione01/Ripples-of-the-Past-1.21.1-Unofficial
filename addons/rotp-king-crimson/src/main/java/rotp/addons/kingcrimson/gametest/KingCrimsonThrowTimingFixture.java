package rotp.addons.kingcrimson.gametest;

import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.standpower.entity.StandEntity;

final class KingCrimsonThrowTimingFixture {
    private KingCrimsonThrowTimingFixture() {}
    static void stats(KingCrimsonThrowGameTestFixture scene, double speed, float meter) {
        scene.user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(speed);
        StandEntity.updateStandStatAttributes(scene.stand, scene.user);
        scene.stand.updateStrengthMultipliers();
        scene.stand.setFinisherMeter(meter);
    }
    // 1.16 StandStatFormulas.getLightAttackRecovery, used by the inherited PERFORM duration.
    static int donorPerform(double speed, float meter) {
        double length = (24 - speed) / 2;
        if (length <= 1) return 1;
        if (length > 4) length = Math.max(length * (1.0F - meter * 0.4F), 4);
        return (int) Math.ceil(length);
    }
    static int donorRecovery(double speed, float lastHeavy) {
        float max = (40 - (float) speed * 1.25F) * 0.75F;
        float min = max / 2;
        return (int) Math.floor(max + lastHeavy * (min - max));
    }
}
