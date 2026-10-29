package rotp.addons.yellowtemperance.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.yellowtemperance.YellowTemperancePhase1Spec;
import rotp.addons.yellowtemperance.effect.ErosionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class YellowTemperanceClearDebuffsAbility
        extends NoPoseStandEntityAbility {
    public YellowTemperanceClearDebuffsAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ClearAction::new);
        standAutoSummonMode(AutoSummonMode.DISABLED);
        // 1.16 clear_debuffs is holdToFire(20, false).
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE,
                YellowTemperancePhase1Spec.CLEAR_WINDUP_TICKS);
        // a StandAction in 1.16: once it fired nothing was left running, so
        // no recovery holds the next press back
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        cooldown(YellowTemperancePhase1Spec.CLEAR_COOLDOWN_TICKS);
        resolveLevelToUnlock(3);
    }

    // 1.16 yellow_temperance_clear_debuffs (075 InitStands :59) was a
    // StandAction its user's power performed, the Stand out or not, never a
    // Stand task: it ran beside the Stand's task (an unsummon too)
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    // It had no autoSummonStand(): a press with Yellow Temperance not out
    // summoned nothing and the user's power performed it (StandAction
    // onClick :263-267), which the core's Stand ability would refuse.
    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user,
            FriendlyByteBuf extraClientInput, InputMethod inputMethod,
            float clickHoldResolveTime, BufferingState bufferingState) {
        if (!level.isClientSide()
                && StandUtil.getSummonedStand(user) == null) {
            return setOrBufferAction(level, user, user, inputMethod,
                    extraClientInput, clickHoldResolveTime, bufferingState);
        }
        return super.onKeyPress(level, user, extraClientInput,
                inputMethod, clickHoldResolveTime, bufferingState);
    }

    // 1.16 StandAction.getPerformer :191-193: the Stand while it is out,
    // else the user, whose stun the press then checked
    @Override
    protected LivingEntity getPerformer(LivingEntity user) {
        LivingEntity stand = super.getPerformer(user);
        return stand != null ? stand : user;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return action == null
                        || action.getPerformer() instanceof StandEntity
                ? super.getEntityAnim(action)
                : null;
    }

    // 1.16 set the cooldown when the hold fired, so a cancelled hold
    // leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    // 1.16 clear_debuffs was a StandAction its user's power performed in the
    // tick the hold fired, the 20th from the press, not the Stand's next one.
    @Override
    public boolean chargeFiresAsItsLengthEnds(EntityActionInstance action) {
        return true;
    }

    private static final class ClearAction
            extends EntityActionInstance {
        private ClearAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 holdToFire(20, false): letting go before the charge
        // completes cancels it.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
        }

        // the user's power performed it in 1.16, so the Stand's summon lock
        // held back neither its charge nor its perform
        @Override
        public boolean isHeldBackBySummonLock() {
            return false;
        }

        // 1.16 EntityStandType.getStaminaRegen: a StandAction was no Stand
        // task, so the Stand out still regenerated 1.5 a tick
        @Override
        public boolean canStaminaRegen(
                StandPower standPower, StandEntity standEntity) {
            return true;
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user != null && power != null) {
                // 1.16 StandAction.onPerform paid staminaCost(200) before
                // perform and ignored the result
                StandAbilityStamina.consume(
                        ability,
                        power,
                        YellowTemperancePhase1Spec.CLEAR_STAMINA,
                        false);
                if (ability
                        instanceof YellowTemperanceClearDebuffsAbility clear) {
                    clear.setCooldownOnUse(power);
                }
                ErosionService.clearOwned(user);
            }
        }
    }
}
