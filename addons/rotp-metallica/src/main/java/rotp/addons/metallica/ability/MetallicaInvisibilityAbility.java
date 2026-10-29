package rotp.addons.metallica.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.metallica.MetallicaAddon;
import rotp.addons.metallica.MetallicaPhase1Spec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class MetallicaInvisibilityAbility
        extends StandEntityAbility {
    public MetallicaInvisibilityAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                InvisibilityAction::new);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE,
                MetallicaPhase1Spec
                        .INVISIBILITY_CHARGE_TICKS);
        setDefaultPhaseLength(
                ActionPhase.WINDUP,
                MetallicaPhase1Spec
                        .INVISIBILITY_WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    @Override
    public String getSpriteName(Power<?> context) {
        LivingEntity user =
                context != null ? context.getUser() : null;
        return user != null
                && user.hasEffect(MobEffects.INVISIBILITY)
                ? MetallicaAddon.INVISIBILITY_NAME + "_disable"
                : MetallicaAddon.INVISIBILITY_NAME;
    }

    private static final class InvisibilityAction
            extends EntityActionInstance {
        private InvisibilityAction(
                EntityActionType ability) {
            super(ability);
        }

        // 1.16 holdType() + holdToFire(30, false): a full charge waits
        // until the key is let go and fires then; letting go earlier
        // cancels it.
        @Override
        protected boolean shouldHoldPhaseAtEnd() {
            return getPhase() == ActionPhase.BUTTON_CHARGE;
        }

        // 1.16 stopHeldAction(true) checked the requirements once more
        // before the released charge fired.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                if (getPhaseTick() >= getCurPhaseLength()
                        && ability instanceof MetallicaInvisibilityAbility held
                        && held.canFireReleasedHold(this)) {
                    setPhaseStart(ActionPhase.WINDUP);
                }
                else {
                    forceStop();
                }
                syncPhaseChanges();
            }
        }

        // 1.16 played the voice line and the on/off sound when the
        // windup began, 30 ticks before the toggle.
        @Override
        public void onSetPhase(ActionPhase newPhase) {
            if (newPhase != ActionPhase.WINDUP
                    || !(level() instanceof ServerLevel)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }
            if (!MetallicaAddon.invisibility(user).active()) {
                JojoModUtil.sayVoiceLine(
                        user,
                        MetallicaAddon.USER_INVISIBILITY);
                playStandSound(MetallicaAddon.INVISIBILITY_ON.get());
            } else {
                playStandSound(MetallicaAddon.INVISIBILITY_OFF.get());
            }
        }

        // 1.16 played these as stand sounds, which the user hears too;
        // Player.playSound on the server leaves the player out.
        private void playStandSound(SoundEvent sound) {
            if (performer instanceof StandEntity stand) {
                StandUtil.playStandEntitySound(stand, sound, 1.0F, 1.0F);
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel)
                    || !(ability
                            instanceof MetallicaInvisibilityAbility
                            invisibilityAbility)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null || power == null) {
                forceStop();
                return;
            }
            MetallicaAddon.invisibility(user).toggle(user);
            // 1.16 toggled before draining stamina, even when short.
            StandAbilityStamina.consume(
                    (Ability) invisibilityAbility,
                    power,
                    MetallicaPhase1Spec.INVISIBILITY_STAMINA,
                    false);
        }
    }
}
