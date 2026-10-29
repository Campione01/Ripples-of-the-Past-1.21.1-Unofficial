package rotp.addons.kraftwork.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class KraftWorkComboPunchAbility
        extends StandEntityHeavyPunchAbility {
    private static final double SLIDE_DISTANCE = 1.5D;

    public KraftWorkComboPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = KraftWorkComboPunch::new;
    }

    // 1.16 KraftWorkComboPunch.standMovesByItself: from the last two windup
    // ticks the Stand slides on its own and stays where it punched.
    @Override
    public boolean noAdheringToUserOffset(
            StandPower standPower, StandEntity standEntity) {
        return movesByItself(standEntity);
    }

    @Override
    public boolean noAdheringToUserOffsetClientFallback(
            StandEntity standEntity) {
        return movesByItself(standEntity);
    }

    @Override
    public boolean lockStandManualMovement(
            StandPower standPower, StandEntity standEntity) {
        return movesByItself(standEntity);
    }

    private static boolean movesByItself(StandEntity standEntity) {
        EntityActionInstance action = standEntity.getCurStandAction();
        if (!(action instanceof KraftWorkComboPunch)) {
            return false;
        }
        ActionPhase phase = action.getPhase();
        return phase == ActionPhase.WINDUP && action.getPhaseTicksLeft() <= 2
                || phase == ActionPhase.PERFORM
                || phase == ActionPhase.RECOVERY;
    }

    // 1.16 KraftWorkComboPunch extends KraftWorkHeavyPunch: the finisher
    // keeps the heavy punch's Lock Equipment follow-up.
    public static final class KraftWorkComboPunch
            extends KraftWorkHeavyPunchAbility.KraftWorkHeavyPunch {
        public KraftWorkComboPunch(EntityActionType ability) {
            super(ability);
        }

        // 1.16 KraftWorkComboPunch.getOffsetFromUser: noYOffset(0, 0.5) in
        // either mode, at that mode's default y and turned by the view yaw,
        // where the heavy punch it extends put the Stand in front of its
        // target; the heavy punch places it through here.
        @Override
        public boolean setStandFrontOffsetFromTarget(
                StandEntity stand,
                ActionTarget target,
                double minOffset,
                double maxOffset) {
            boolean armsOnly = stand.isArmsOnlyMode();
            double y = armsOnly
                    ? stand.offsetFromUser.armsOnlyIdleOffset.y
                    : stand.Y_OFFSET;
            setStandOffset(
                    new Vec3(0.0D, y, 0.5D),
                    StandOffsetFromUser.Rotations.HEAD,
                    armsOnly);
            return true;
        }

        @Override
        public void actionPerformStart() {
            if (performer instanceof StandEntity stand) {
                Vec3 slide = stand.getLookAngle()
                        .normalize()
                        .scale(SLIDE_DISTANCE);
                stand.move(MoverType.SELF, slide);
                stand.setDeltaMovement(Vec3.ZERO);
            }
            super.actionPerformStart();
        }

        @Override
        protected float getAdditionalHeavyPunchKnockback(
                StandEntity stand) {
            return 0.5F
                    + (float) stand.getAttackDamage() / 8.0F;
        }
    }
}
