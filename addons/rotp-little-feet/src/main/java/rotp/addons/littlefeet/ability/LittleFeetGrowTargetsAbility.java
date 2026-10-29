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

public final class LittleFeetGrowTargetsAbility
        extends StandEntityAbility {
    public static final float STAMINA_COST = 75.0F;

    public LittleFeetGrowTargetsAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            boolean allTargets) {
        super(
                abilityType,
                abilityId,
                type -> new GrowTargetsAction(type, allTargets));
        setDefaultPhaseLength(ActionPhase.WINDUP, 4);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 4);
        standAutoSummonMode(AutoSummonMode.ARMS);
        partsRequired(StandPart.ARMS);
        resolveLevelToUnlock(1);
    }

    public static final class GrowTargetsAction
            extends EntityActionInstance {
        private final boolean allTargets;

        public GrowTargetsAction(
                EntityActionType ability, boolean allTargets) {
            super(ability);
            this.allTargets = allTargets;
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }
            StandPower power = StandPower.get(user);
            getPerformer().playSound(
                    LittleFeetAddon.LITTLE_FEET_SLOW_SELF_GROW.get(),
                    1.0F,
                    1.0F);
            if (allTargets) {
                LittleFeetScaleService.growAll(user);
            } else {
                LittleFeetScaleService.growNearest(user);
            }
            // 1.16 StandEntityTask paid after the perform with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST, false);
        }
    }
}
