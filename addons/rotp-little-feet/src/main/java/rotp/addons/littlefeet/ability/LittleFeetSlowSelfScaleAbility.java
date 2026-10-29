package rotp.addons.littlefeet.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.littlefeet.LittleFeetAddon;
import rotp.addons.littlefeet.scale.LittleFeetScaleService;
import net.minecraft.world.entity.LivingEntity;

public final class LittleFeetSlowSelfScaleAbility
        extends StandEntityAbility {
    public static final float STAMINA_COST = 75.0F;
    public static final int HOLD_TICKS = 80;
    public static final int COOLDOWN_TICKS = 80;

    public LittleFeetSlowSelfScaleAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            boolean shrinking) {
        super(
                abilityType,
                abilityId,
                type -> new SlowScaleAction(type, shrinking));
        // 1.16 holdType(80): the press starts the held perform, with no
        // windup or recovery, and the hold ends at 80 ticks.
        setDefaultPhaseLength(ActionPhase.PERFORM, HOLD_TICKS);
        standAutoSummonMode(AutoSummonMode.ARMS);
        partsRequired(StandPart.ARMS);
        resolveLevelToUnlock(2);
        // 1.16 slow self grow had no cooldown(80); shrink took it as the
        // hold ended (PowerBaseImpl.stopHeldAction).
        if (shrinking) {
            cooldown(COOLDOWN_TICKS);
        }
    }

    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.PERFORM
                || super.isActionHeld(action);
    }

    @Override
    public boolean isHoldAction() {
        return true;
    }

    public static final class SlowScaleAction
            extends EntityActionInstance {
        private final boolean shrinking;
        private boolean activated;

        public SlowScaleAction(
                EntityActionType ability, boolean shrinking) {
            super(ability);
            this.shrinking = shrinking;
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null) {
                return;
            }
            activated = true;
            getPerformer().playSound(
                    (shrinking
                            ? LittleFeetAddon
                                    .LITTLE_FEET_SLOW_SELF_SHRINK
                            : LittleFeetAddon
                                    .LITTLE_FEET_SLOW_SELF_GROW)
                            .get(),
                    1.0F,
                    1.0F);
            LittleFeetScaleService.changeSelfSlowly(
                    user, shrinking);
            // 1.16 StandEntityTask paid after the perform with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST, false);
        }

        @Override
        public void actionTick() {
            if (level().isClientSide()
                    || getPhase() != ActionPhase.PERFORM
                    || !activated) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user != null) {
                LittleFeetScaleService.changeSelfSlowly(
                        user, shrinking);
            }
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        // 1.16 slow self shrink and grow were holdType Stand actions: another
        // Stand action pressed during the hold replaced it at once.
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }
    }
}
