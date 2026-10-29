package rotp.addons.cmoonreimagined.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;

import rotp.addons.cmoonreimagined.CMoonAddon;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class InversionBarrageAbility
        extends StandEntityBarrageAbility {
    public InversionBarrageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                InversionBarrageAction::new);
    }

    public static final class InversionBarrageAction
            extends StandEntityBarrage {
        public InversionBarrageAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected void hitEntity(
                ActionTarget target,
                Level level,
                StandEntity stand) {
            Entity entity = target.getMainEntity();
            if (!level.isClientSide()
                    && entity instanceof LivingEntity living
                    && living.isAlive()) {
                living.addEffect(new MobEffectInstance(
                        CMoonAddon.ARMS_INVERSION,
                        100,
                        1,
                        false,
                        false,
                        false));
            }
            super.hitEntity(target, level, stand);
        }
    }
}
