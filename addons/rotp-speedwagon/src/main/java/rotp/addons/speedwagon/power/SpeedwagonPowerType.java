package rotp.addons.speedwagon.power;

import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.speedwagon.SpeedwagonConfig;
import net.minecraft.resources.ResourceLocation;

public final class SpeedwagonPowerType
        extends PlayerPowerType<SpeedwagonData> {
    public static final int COLOR = 0x2A2833;

    public SpeedwagonPowerType(
            ResourceLocation registryKey,
            MovesetBuilder moveset) {
        super(registryKey, moveset);
    }

    @Override
    public SpeedwagonData newDataInstance() {
        return new SpeedwagonData();
    }

    // 1.16 SpeedwagonPowerType.isReplaceableWith: Hamon (a teacher) or
    // Vampirism (the Stone Mask) may replace the Speedwagon power.
    @Override
    public boolean isReplaceableWith(PlayerPowerType<?> newType) {
        return newType != null
                && (newType == ModPlayerPowers.HAMON.get()
                        || newType == ModPlayerPowers.VAMPIRISM.get());
    }

    @Override
    public boolean keepOnDeath(PlayerPower power) {
        return SpeedwagonConfig.KEEP_SP_ON_DEATH.get();
    }

    // This overrides the same-signature hook when run on a newer core.
    public float getTargetResolveMultiplier(
            PlayerPower power, StandPower attackingStand) {
        return 2.0F;
    }

    @Override
    public float getStandStaminaRegenFactor(
            PlayerPower power, StandPower standPower) {
        return 100.0F;
    }
}
