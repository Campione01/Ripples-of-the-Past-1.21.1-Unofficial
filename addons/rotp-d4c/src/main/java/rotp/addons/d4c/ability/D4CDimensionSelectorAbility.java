package rotp.addons.d4c.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.d4c.D4CPhase2ASpec;
import rotp.addons.d4c.travel.D4CDimensionTravelService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class D4CDimensionSelectorAbility
        extends StandEntityAbility {
    public D4CDimensionSelectorAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(abilityType, abilityId, SelectorAction::new);
        usageGroup = AbilityUsageGroup.UTILITY;
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(
                D4CPhase2ASpec.SELECTOR_TECHNICAL_COOLDOWN_TICKS,
                D4CPhase2ASpec.SELECTOR_ADDITIONAL_COOLDOWN_TICKS,
                0.0F);
        resolveLevelToUnlock(D4CPhase2ASpec.SELECTOR_RESOLVE_LEVEL);
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.ARMS);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    public static final class SelectorAction extends EntityActionInstance {
        private boolean performed;

        public SelectorAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (performed) {
                return;
            }
            performed = true;
            LivingEntity user = getPowerUser();
            if (level().isClientSide()
                    || !(user instanceof ServerPlayer player)) {
                return;
            }

            StandPower power = StandPower.get(player);
            if (!D4CDimensionTravelService.openSelector(player)) {
                return;
            }
            // 1.16 StandEntityTask paid after the perform with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    power,
                    D4CPhase2ASpec.SELECTOR_STAMINA,
                    false);
            if (ability instanceof D4CDimensionSelectorAbility selector) {
                selector.setCooldownOnUse(power);
            }
        }
    }
}
