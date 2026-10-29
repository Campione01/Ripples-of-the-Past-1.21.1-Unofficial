package rotp.addons.cmoonreimagined.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.cmoonreimagined.CMoonAddon;
import rotp.addons.cmoonreimagined.entity.CMoonStandEntity;
import net.minecraft.world.entity.LivingEntity;

public final class GravityChangeAbility extends StandEntityAbility {
    private static final ActionAnimIdentifier ANIMATION =
            ActionAnimIdentifier.getOrCreate(
                    CMoonAddon.GRAVITY_CHANGE, false);

    public GravityChangeAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, GravityChangeAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
        standAutoSummonMode(AutoSummonMode.FULL);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ANIMATION;
    }

    public static final class GravityChangeAction
            extends EntityActionInstance {
        public GravityChangeAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer()
                            instanceof CMoonStandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }
            stand.setGravityDirection(user.getDirection());
            StandUtil.playStandEntitySound(
                    stand,
                    CMoonAddon.C_MOON_GRAVITY_SHIFT_NORMAL,
                    1.0F,
                    1.0F);
        }
    }
}
