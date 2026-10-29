package rotp.addons.d4c.ability;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.d4c.D4CAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** D4C barrage: 1.16 d4c_barrage shout(valentine_barrage) on top of the core barrage. */
public final class D4CBarrageAbility extends StandEntityBarrageAbility {

    public D4CBarrageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, D4CBarrage::new);
    }

    public static final class D4CBarrage extends StandEntityBarrage {

        public D4CBarrage(EntityActionType ability) {
            super(ability);
        }

        // Server side, as the barrage starts (1.16 said it on the accepted click).
        @Override
        protected void onBarrageSet(Level level, StandEntity stand) {
            super.onBarrageSet(level, stand);
            if (level.isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user != null) {
                Ability.sayShoutOf(
                        ability, user, D4CAddon.VALENTINE_BARRAGE);
            }
        }

        // 1.16 StandEntityMeleeBarrage.isCancelable: a heavy attack
        // (canFollowUpBarrage) followed the barrage up in its recovery; the
        // hand chop was one (StandEntityHeavyAttack.Builder, ai/ah.java:72)
        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return super.canBeCancelledInto(cancellingAbility)
                    || getPhase() == ActionPhase.RECOVERY
                            && cancellingAbility instanceof D4CPhase2BAbility chop
                            && chop.mode() == D4CPhase2BAbility.Mode.HAND_CHOP;
        }
    }
}
