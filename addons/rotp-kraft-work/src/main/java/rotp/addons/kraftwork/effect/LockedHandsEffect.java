package rotp.addons.kraftwork.effect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public final class LockedHandsEffect
        extends LockedPositionEffect {
    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        return super.applyEffectTick(entity, amplifier);
    }
}
