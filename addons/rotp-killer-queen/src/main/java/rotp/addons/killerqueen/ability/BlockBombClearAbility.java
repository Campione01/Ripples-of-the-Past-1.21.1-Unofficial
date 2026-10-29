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
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.KillerQueenPhase1Spec;
import rotp.addons.killerqueen.state.KillerQueenStateService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class BlockBombClearAbility
        extends StandEntityAbility {
    public static final int WINDUP_TICKS = 5;
    public static final int PERFORM_TICKS = 1;
    public static final int RECOVERY_TICKS = 4;

    public BlockBombClearAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ClearAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
        partsRequired(StandPart.ARMS);
        // 1.16 StandEntityLightAttack.Builder :193: pressed with Killer Queen
        // away, only its main arm comes out
        standAutoSummonMode(AutoSummonMode.MAIN_ARM);
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        return super.isAbilityAvailable(context)
                && context.getUser() instanceof Player player
                && KillerQueenStateService.blockBombPos(player)
                        != null;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        // 1.16 BlockQuitBomb: refused while Bites the Dust is armed
        if (BitesTheDustKitLock.locked(context)) {
            return ConditionCheck.NEGATIVE;
        }
        if (!(context.getUser() instanceof Player player)
                || KillerQueenStateService.blockBombPos(player)
                        == null) {
            return ConditionCheck.createNegative(
                    "killer_queen_no_block_bomb");
        }
        return ConditionCheck.POSITIVE;
    }

    // 1.16 StandEntityLightAttack.standKeepsTarget: its task held what it
    // was pressed at, of any kind
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return true;
    }

    // 1.16 StandEntityLightAttack.lockOnTargetPosition
    @Override
    public boolean lockOnTargetPosition(StandPower standPower,
            StandEntity standEntity, EntityActionInstance action) {
        return false;
    }

    // 1.16 BlockQuitBomb (kq_block_quit): a StandEntityLightAttack (KillerQueenLightAttack)
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

    public static final class ClearAction
            extends EntityActionInstance {
        public ClearAction(EntityActionType ability) {
            super(ability);
        }

        private final KillerQueenLightAttack.SwingSound swingSound =
                new KillerQueenLightAttack.SwingSound();

        @Override
        public void actionTick() {
            super.actionTick();
            swingSound.tick(this);
        }

        // 1.16 StandEntityLightAttack: any action, this one too, replaced it
        // in its recovery
        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return KillerQueenLightAttack.givesWay(this);
        }

        // 1.16 StandEntityLightAttack: the task held the pressed target,
        // which the Stand turned to and stood before its user toward
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (!level().isClientSide()
                    && getPerformer() instanceof StandEntity stand) {
                KillerQueenLightAttack.alternateHands(this, stand);
                ActionTarget target = captureActionTargetFromAim(stand);
                KillerQueenLightAttack.standAtFront(this, stand, target);
                keepStandAimedAtTarget(target);
            }
        }

        @Override
        protected void onTaskTargetInherited(ActionTarget target) {
            super.onTaskTargetInherited(target);
            if (getPerformer() instanceof StandEntity stand) {
                KillerQueenLightAttack.standAtFront(this, stand, target);
            }
        }

        @Override
        public void updateStandOffset(StandEntity stand) {
            KillerQueenLightAttack.refreshAtFront(this, stand);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer()
                            instanceof StandEntity stand)
                    || !(getPowerUser()
                            instanceof ServerPlayer user)) {
                return;
            }
            // 1.16 kq_block_quit standSound(KQ_UNBOMB) (InitStands :89): at
            // the Stand as the perform began, whatever the clear found
            if (KillerQueenLightAttack.playsStandSound(this, stand)) {
                StandUtil.playStandEntitySound(
                        stand,
                        KillerQueenAddon.KILLER_QUEEN_UNBOOM,
                        1.0F,
                        1.0F);
            }
            KillerQueenStateService.clearBlockBomb(user);
            // 1.16 BlockQuitBomb had no stamina condition: the Stand task
            // pays the cost after standPerform and a short bar drops to 0
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    KillerQueenPhase1Spec.BLOCK_BOMB_CLEAR_STAMINA,
                    false);
        }
    }
}
