package rotp.addons.killerqueen.ability;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.killerqueen.state.ItemBombService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class ItemBombAbility extends StandEntityAbility {
    public static final int WINDUP_TICKS = 5;
    public static final int PERFORM_TICKS = 1;
    public static final int RECOVERY_TICKS = 4;
    public static final int COOLDOWN_TICKS = 5;

    public ItemBombAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ItemBombAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
        cooldown(COOLDOWN_TICKS);
        resolveLevelToUnlock(1);
        partsRequired(StandPart.ARMS);
        // 1.16 ItemFBomb: a StandEntityLightAttack.Builder, which brings
        // out the main arm only
        standAutoSummonMode(AutoSummonMode.MAIN_ARM);
    }

    // 1.16 StandEntityLightAttack.standKeepsTarget
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return true;
    }

    // 1.16 StandEntityLightAttack.lockOnTargetPosition: the front offset
    // stays along the user's view
    @Override
    public boolean lockOnTargetPosition(StandPower standPower,
            StandEntity standEntity, EntityActionInstance action) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        return context.getUser() instanceof Player player
                && ItemBombService.canMark(player)
                        ? check
                        : ConditionCheck.createNegative(
                                "killer_queen_item_bomb_hands");
    }

    // 1.16 ItemFBomb (kq_itembomb): a StandEntityLightAttack (KillerQueenLightAttack)
    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        KillerQueenLightAttack.setPhases(this, action, level, performer,
                KillerQueenLightAttack.Windup.LIGHT);
    }

    public static final class ItemBombAction
            extends EntityActionInstance {
        public ItemBombAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 StandEntityLightAttack: any action, this one too, replaced it
        // in its recovery
        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return KillerQueenLightAttack.givesWay(this);
        }

        // 1.16 StandEntityLightAttack.getOffsetFromUser: front3dOffset,
        // 0.5 to 2 toward the target in full mode; the Stand turned to it
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (!(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            KillerQueenLightAttack.alternateHands(this, stand);
            ActionTarget target = captureActionTargetFromAim(stand);
            setStandFrontOffsetFromTarget(
                    stand,
                    target,
                    Math.min(0.5D, stand.getEffectiveRange()),
                    Math.min(2.0D, stand.getMaxRange()));
            keepStandAimedAtTarget(target);
        }

        // 1.16 front3dOffset was measured to the task's target, one taken
        // over from the previous task too
        @Override
        protected void onTaskTargetInherited(ActionTarget target) {
            super.onTaskTargetInherited(target);
            if (getPerformer() instanceof StandEntity stand) {
                setStandFrontOffsetFromTarget(
                        stand,
                        target,
                        Math.min(0.5D, stand.getEffectiveRange()),
                        Math.min(2.0D, stand.getMaxRange()));
            }
        }

        // 1.16 ItemFBomb had no getOffsetFromUser of its own: StandEntity.updatePosition asked the light attack's
        // front3dOffset on every tick
        @Override
        public void updateStandOffset(StandEntity stand) {
            KillerQueenLightAttack.refreshAtFront(this, stand);
        }

        @Override
        public void actionPerformStart() {
            if (!level().isClientSide()
                    && getPerformer() instanceof StandEntity
                    && getPowerUser()
                            instanceof ServerPlayer user) {
                ItemBombService.markOffhandItem(user);
            }
        }
    }
}
