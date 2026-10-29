package rotp.addons.metallica.ability;

import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.metallica.MetallicaAddon;
import rotp.addons.metallica.MetallicaPhase1Spec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class MetallicaHealAbility
        extends StandEntityAbility {
    public static final int REGENERATION_TICKS = 100;
    public static final int REGENERATION_AMPLIFIER = 1;

    public MetallicaHealAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(abilityType, abilityId, HealAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        cooldown(MetallicaPhase1Spec.HEAL_COOLDOWN_TICKS);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    private static final class HealAction
            extends EntityActionInstance {
        private HealAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel)
                    || !(ability
                            instanceof MetallicaHealAbility
                            healAbility)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null || power == null) {
                forceStop();
                return;
            }
            user.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION,
                    REGENERATION_TICKS,
                    REGENERATION_AMPLIFIER,
                    false,
                    false,
                    true));
            // 1.16 performed the heal before its debit, even at low stamina.
            StandAbilityStamina.consume(
                    (Ability) healAbility,
                    power,
                    MetallicaPhase1Spec.HEAL_STAMINA,
                    false);
            // 1.16 standSound(HEAL): a stand sound the user hears too, which
            // Player.playSound on the server would leave out.
            if (performer instanceof StandEntity stand) {
                StandUtil.playStandEntitySound(
                        stand, MetallicaAddon.HEAL.get(), 1.0F, 1.0F);
            }
            healAbility.setCooldownOnUse(power);
        }
    }
}
