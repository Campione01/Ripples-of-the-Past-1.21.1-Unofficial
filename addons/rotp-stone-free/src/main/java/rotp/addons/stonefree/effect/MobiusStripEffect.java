package rotp.addons.stonefree.effect;

import rotp.core.customobjects.StatusEffectModified;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.StoneFreeServices;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class MobiusStripEffect extends StatusEffectModified {
    public MobiusStripEffect(
            MobEffectCategory category, int color) {
        super(category, color);
        setUncurable();
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (!StoneFreeServices.isStoneFreeUser(entity)) {
            entity.removeEffect(StoneFreeAddon.MOBIUS_STRIP);
            return true;
        }
        int inversionLevel =
                StoneFreeServices.inversionLevel(entity);
        if (inversionLevel <= 0) {
            return true;
        }

        StoneFreeServices.removeInversion(entity);
        StandPower power = StandPower.get(entity);
        if (power != null) {
            power.consumeStamina(10.0F * inversionLevel);
        }
        MobEffectInstance current =
                entity.getEffect(StoneFreeAddon.MOBIUS_STRIP);
        if (current != null) {
            entity.addEffect(new MobEffectInstance(
                    StoneFreeAddon.MOBIUS_STRIP,
                    current.getDuration() + 40,
                    0,
                    false,
                    false,
                    true));
        }
        return true;
    }
}
