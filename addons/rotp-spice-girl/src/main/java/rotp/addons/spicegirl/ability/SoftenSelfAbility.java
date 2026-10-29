package rotp.addons.spicegirl.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.spicegirl.SpiceGirlAddon;
import rotp.addons.spicegirl.entity.SpiceGirlStandEntity;
import rotp.addons.spicegirl.state.SpiceGirlElasticService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class SoftenSelfAbility
        extends StandEntityAbility {
    public static final float STAMINA_COST_PER_TICK = 3.0F;
    public static final int DURATION_TICKS = 750;
    public static final int HOLD_TO_FIRE_TICKS = 10;
    public static final int MAX_HOLD_TICKS = 80;
    public static final int COOLDOWN_TICKS = 160;

    public SoftenSelfAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, SoftenSelfAction::new);
        // 1.16 SHIFT variation (or its finisher): shouts on sneak too
        setPlaysVoiceLineOnSneak();
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE,
                HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 spice_girl_bounce_her (067 InitStands:72) set no
        // standRecoveryTicks
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
        cooldown(COOLDOWN_TICKS);
    }

    // 1.16 bounce_her set the cooldown when the hold fired, so a
    // cancelled hold leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    public static final class SoftenSelfAction
            extends EntityActionInstance {
        private int holdTicks;

        public SoftenSelfAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 holdType(80) + holdToFire(10, false): a full charge waits
        // for the key to be let go and fires then, or fires by itself
        // once the hold reaches 80 ticks; letting go earlier cancels it.
        @Override
        protected boolean shouldHoldPhaseAtEnd() {
            return getPhase() == ActionPhase.BUTTON_CHARGE;
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.BUTTON_CHARGE
                    || level().isClientSide()) {
                return;
            }
            LivingEntity owner = getPowerUser();
            if (owner == null
                    || SpiceGirlAddon.activeStand(owner)
                            != getPerformer()) {
                forceStop();
                syncPhaseChanges();
            }
            else if (++holdTicks >= MAX_HOLD_TICKS) {
                setPhaseStart(ActionPhase.WINDUP);
                syncPhaseChanges();
            }
        }

        // 1.16 stopHeldAction(true) checked the requirements once more
        // before the released charge fired. (At the 80-tick limit the core
        // held-action check has just run in the same tick.)
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                if (getPhaseTick() >= getCurPhaseLength()
                        && ability instanceof SoftenSelfAbility self
                        && self.canFireReleasedHold(this)) {
                    setPhaseStart(ActionPhase.WINDUP);
                }
                else {
                    forceStop();
                }
                syncPhaseChanges();
            }
        }

        // 0.2.2.2 took staminaCostTick only in PERFORM, and bounce_her
        // performs for one tick, so a use costs 3 stamina once.
        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer owner)
                    || !(getPerformer()
                            instanceof SpiceGirlStandEntity stand)
                    || SpiceGirlAddon.activeStand(owner) != stand) {
                return;
            }
            StandPower power = StandPower.get(owner);
            if (ability instanceof SoftenSelfAbility self) {
                self.setCooldownOnUse(power);
            }
            SpiceGirlElasticService.apply(
                    owner,
                    stand,
                    owner,
                    DURATION_TICKS,
                    0);
            StandUtil.playStandEntitySound(
                    stand,
                    SpiceGirlAddon.SG_ABILITY_1,
                    1.0F,
                    1.0F);
            // 1.16 .shout(SG_ABILITY1_VOICE) from the user
            sayShoutOf(ability, getPowerUser(), SpiceGirlAddon.SG_ABILITY1_VOICE);
            // 1.16 StandEntityTask paid the tick cost with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    power,
                    STAMINA_COST_PER_TICK,
                    true);
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }

        // 1.16 spice_girl_bounce_her (StandEntityAction, no isChainable) refused its own press via canClickDuringTask
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }
    }
}
