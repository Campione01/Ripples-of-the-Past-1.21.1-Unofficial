package rotp.addons.ger.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

public final class GerStandPunchAbility
        extends StandEntityPunchAbility {
    public GerStandPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        if (action instanceof StandEntityPunch punch
                && action.getPerformer() instanceof StandEntity stand) {
            HumanoidArm side = punch.getPunchingHand() == InteractionHand.MAIN_HAND
                    ? stand.getMainArm() : stand.getMainArm().getOpposite();
            return ActionAnimIdentifier.getOrCreate(
                    side == HumanoidArm.LEFT ? "punch2" : "punch", false);
        }
        return GerActionAnimations.resolve(
                getAbilityId(), super.getEntityAnim(action));
    }
}
