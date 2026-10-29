package rotp.addons.gratefuldead.effect;

import rotp.core.entityattachment.custom_effect.EntityCustomEffectType;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.service.AgingService;

public final class AgingAuraEffect extends StandEffectInstance {
    public AgingAuraEffect(
            EntityCustomEffectType<?> effectType) {
        super(effectType);
        removeOnUserDeath = true;
        removeOnUserLogout = true;
        // Boy II Man taking a part re-sets the StandInstance; that must not end the aura.
        removeOnStandChanged = false;
    }

    // 1.16 power.clear() dropped the aura, so a same-Stand replace starts without it.
    public static void removeAll(StandPower power) {
        power.userStandEffects
                .getEffectsOfType(
                        GratefulDeadAddon.AGING_AURA_EFFECT.get())
                .toList()
                .forEach(power.userStandEffects::removeEffect);
    }

    public static final class StandData
            extends StandTypePersistentData {
        public StandData(StandType powerType) {
            super(powerType);
        }

        @Override
        public void onPowerCleared(
                Power<?> userPower, PowerType newType) {
            super.onPowerCleared(userPower, newType);
            if (userPower instanceof StandPower power) {
                removeAll(power);
            }
        }
    }

    @Override
    protected void start() {}

    @Override
    protected void tick() {
        if (level.isClientSide()) {
            return;
        }
        StandPower power = getUserPower();
        if (power == null
                || power.getPowerType()
                        != GratefulDeadAddon
                                .THE_GRATEFUL_DEAD.get()) {
            remove();
            return;
        }
        AgingService.tickAura(getStandUser());
    }

    @Override
    protected void stop() {}
}
