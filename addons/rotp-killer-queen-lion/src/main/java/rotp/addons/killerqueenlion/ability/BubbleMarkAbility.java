package rotp.addons.killerqueenlion.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.killerqueenlion.KillerQueenPhase1Spec;
import rotp.addons.killerqueenlion.entity.BubbleMarkEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class BubbleMarkAbility extends StandEntityAbility {
    public static final int RECOVERY_TICKS = 0;
    public static final int COOLDOWN_TICKS = 30;

    public BubbleMarkAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, BubbleMarkAction::new);
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
        cooldown(COOLDOWN_TICKS);
        partsRequired(StandPart.ARMS);
        setIgnoresPerformerStun();
    }

    // 1.16 StandAction.getPerformer, whose stun refused the press, was the
    // summoned Stand, else the user
    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        return check.isPositive() ? checkPerformerStun(context) : check;
    }

    // 1.16 BubbleMarkAction (100 InitStands :96) was a StandAction of the
    // user: no Stand task and no summon, the mark left at the press.
    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user,
            FriendlyByteBuf extraClientInput, InputMethod inputMethod,
            float clickHoldResolveTime, BufferingState bufferingState) {
        return level.isClientSide() ? null : setOrBufferAction(level, user,
                user, inputMethod, extraClientInput, clickHoldResolveTime,
                bufferingState);
    }

    @Override
    protected LivingEntity getPerformer(LivingEntity user) {
        return user;
    }

    public static final class BubbleMarkAction extends EntityActionInstance {
        public BubbleMarkAction(EntityActionType ability) {
            super(ability);
        }

        // A StandAction took no time: no press, its own neither, waits for this one
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }

        // 1.16 PowerBaseImpl.onClickAction performed it at the press
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (level().isClientSide()
                    || isRestoringInputLifecycle()
                    || !(getPowerUser() instanceof ServerPlayer owner)) {
                return;
            }
            StandEntity stand = StandUtil.getSummonedStand(owner);
            // 1.16 StandAction.onPerform pays the cost first and goes on whatever the bar held
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(owner),
                    KillerQueenPhase1Spec.BUBBLE_MARK_STAMINA,
                    false);
            BubbleMarkEntity mark = new BubbleMarkEntity(
                    owner, stand, level());
            mark.setShootingPosOf(owner);
            mark.shootFromRotation(owner, 0.25F, 1.0F);
            level().addFreshEntity(mark);
        }
    }
}
