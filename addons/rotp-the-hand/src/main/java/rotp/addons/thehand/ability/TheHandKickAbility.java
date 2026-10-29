package rotp.addons.thehand.ability;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.world.damagesource.DamageSource;

public final class TheHandKickAbility
        extends StandEntityHeavyPunchAbility {
    public TheHandKickAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = KickAction::new;
        partsRequired(StandPart.LEGS);
    }

    public static final class KickAction extends StandEntityHeavyPunch {
        public KickAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected void addKnockback(DamageSource damageSource) {
            super.addKnockback(damageSource);
            ((DamageSourceModified) damageSource)
                    .jojo_ripples$knockbackXRot(60.0F);
        }
    }
}
