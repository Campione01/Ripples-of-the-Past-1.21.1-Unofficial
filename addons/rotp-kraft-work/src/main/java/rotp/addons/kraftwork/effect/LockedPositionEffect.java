package rotp.addons.kraftwork.effect;

import rotp.core.mechanics.ImmobilizeEffect;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.kraftwork.KraftWorkAddon;
import net.minecraft.world.entity.LivingEntity;

public class LockedPositionEffect extends ImmobilizeEffect {
    public static final int COLOR = 0xDB4736;

    public LockedPositionEffect() {
        super(COLOR);
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        super.applyEffectTick(entity, amplifier);
        entity.setDeltaMovement(0.0D, 0.0D, 0.0D);
        entity.setNoGravity(true);
        entity.fallDistance = 0.0F;
        if (!entity.hasEffect(
                KraftWorkAddon.TRANSPORTATION_LOCKED)) {
            entity.stopRiding();
        }
        return true;
    }

    @Override
    public boolean isApplicable(LivingEntity entity) {
        return super.isApplicable(entity)
                && !(entity instanceof StandEntity);
    }
}
