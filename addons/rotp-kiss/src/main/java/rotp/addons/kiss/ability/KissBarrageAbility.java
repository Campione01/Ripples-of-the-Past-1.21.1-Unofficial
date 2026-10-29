package rotp.addons.kiss.ability;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 KissBarrageAction: the core melee barrage, its cry given by getSounds rather than a registered standSound.
 * StandEntityMeleeBarrage.playSoundAtStand repeated only the registered cry under Resolve, so Kiss's played one clip.
 */
public final class KissBarrageAbility extends StandEntityBarrageAbility {
    public KissBarrageAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, KissBarrage::new);
    }

    public static final class KissBarrage extends StandEntityBarrage {
        public KissBarrage(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected boolean usesEndlessBarrageCry(StandEntity stand) {
            return false;
        }
    }
}
