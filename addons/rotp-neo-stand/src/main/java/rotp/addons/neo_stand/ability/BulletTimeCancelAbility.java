package rotp.addons.neo_stand.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class BulletTimeCancelAbility
        extends EntityActionAbility {
    public BulletTimeCancelAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, CancelAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
    }

    // 1.16 star_platinum_bullet_time_cancel (021 neojojostand/aa/aa.java,
    // af/ab.java :82) was a StandAction its user's power performed, the
    // Stand out or not
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    // 1.16 StandAction.getPerformer :191-193: the summoned Stand, else the
    // user, whose stun PowerBaseImpl.checkRequirements :264 checked
    @Override
    protected LivingEntity getPerformer(LivingEntity user) {
        StandEntity stand = StandUtil.getSummonedStand(user);
        return stand != null ? stand : user;
    }

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user,
            FriendlyByteBuf extraClientInput, InputMethod inputMethod,
            float clickHoldResolveTime, BufferingState bufferingState) {
        if (level.isClientSide()) {
            return null;
        }
        return setOrBufferAction(level, user, getPerformer(user),
                inputMethod, extraClientInput, clickHoldResolveTime,
                bufferingState);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        LivingEntity user =
                context != null ? context.getUser() : null;
        return base.isPositive()
                && user != null
                && (user.level().isClientSide()
                        ? BulletTimeService.isClientOwnerActive(
                                user.getUUID())
                        : BulletTimeService.isActive(user))
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    public static final class CancelAction
            extends EntityActionInstance {
        public CancelAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (getPowerUser() instanceof ServerPlayer user) {
                BulletTimeService.cancel(user);
            }
        }
    }
}
