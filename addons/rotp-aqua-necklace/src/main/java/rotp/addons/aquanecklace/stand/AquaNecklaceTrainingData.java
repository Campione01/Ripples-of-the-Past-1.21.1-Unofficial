package rotp.addons.aquanecklace.stand;

import net.minecraft.server.level.ServerPlayer;
import rotp.addons.aquanecklace.AquaNecklaceAddon;
import rotp.addons.aquanecklace.ability.AquaNecklaceAbilities;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;

public final class AquaNecklaceTrainingData extends StandTypePersistentData {
    private static final int SNAPSHOT_INTERVAL = 20;
    private boolean deferLiquidSnapshot;
    private boolean pendingSnapshot;
    private int deferredTrainingTicks;

    public AquaNecklaceTrainingData(AquaNecklaceStandType type) {
        super(type);
    }

    public void addLiquidTrainingTick(StandPower power) {
        float before = getAbilityLearningProgressPoints(AquaNecklaceAddon.CHANGE_TO_LIQUID_NAME);
        deferLiquidSnapshot = true;
        try {
            super.addAbilityLearningProgressPoints(
                    AquaNecklaceAddon.CHANGE_TO_LIQUID_NAME,
                    AquaNecklaceAbilities.LIQUID_TRAINING_PER_TICK,
                    AquaNecklaceAbilities.LIQUID_MAX_TRAINING, power);
        }
        finally {
            deferLiquidSnapshot = false;
        }
        float max = AquaNecklaceAbilities.LIQUID_MAX_TRAINING;
        boolean reachedMax = before >= 0.0F && before < max
                && getAbilityLearningProgressPoints(AquaNecklaceAddon.CHANGE_TO_LIQUID_NAME) >= max;
        if (pendingSnapshot && (++deferredTrainingTicks >= SNAPSHOT_INTERVAL || reachedMax)) {
            flushLiquidTraining(power);
        }
    }

    @Override
    protected void syncAbilityLearningProgressToUser(StandPower power) {
        if (deferLiquidSnapshot && power != null && power.getUser() instanceof ServerPlayer) {
            pendingSnapshot = true;
            return;
        }
        super.syncAbilityLearningProgressToUser(power);
        if (power != null && power.getUser() instanceof ServerPlayer) {
            pendingSnapshot = false;
            deferredTrainingTicks = 0;
        }
    }

    public void flushLiquidTraining(StandPower power) {
        if (pendingSnapshot && power != null && power.getUser() instanceof ServerPlayer) {
            super.syncAbilityLearningProgressToUser(power);
            pendingSnapshot = false;
            deferredTrainingTicks = 0;
        }
    }
}
