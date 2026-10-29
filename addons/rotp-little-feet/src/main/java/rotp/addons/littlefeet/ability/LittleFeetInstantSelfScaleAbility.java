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

public final class LittleFeetInstantSelfScaleAbility
        extends StandEntityAbility {
    public static final float STAMINA_COST = 75.0F;
    public static final int COOLDOWN_TICKS = 80;

    public LittleFeetInstantSelfScaleAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            boolean shrinking) {
        super(
                abilityType,
                abilityId,
                type -> new InstantScaleAction(type, shrinking));
        setDefaultPhaseLength(ActionPhase.WINDUP, 4);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 4);
        standAutoSummonMode(AutoSummonMode.ARMS);
        partsRequired(StandPart.ARMS);
        resolveLevelToUnlock(2);
        cooldown(COOLDOWN_TICKS);
    }

    public static final class InstantScaleAction
            extends EntityActionInstance {
        private final boolean shrinking;

        public InstantScaleAction(
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
            if (user == null) {
                return;
            }
            StandPower power = StandPower.get(user);
            getPerformer().playSound(
                    (shrinking
                            ? LittleFeetAddon
                                    .LITTLE_FEET_INSTANT_SELF_SHRINK
                            : LittleFeetAddon
                                    .LITTLE_FEET_INSTANT_SELF_GROW)
                            .get(),
                    1.0F,
                    1.0F);
            if (shrinking) {
                LittleFeetScaleService.setSelfSmall(user);
            } else {
                LittleFeetScaleService.setSelfNormal(user);
            }
            // 1.16 StandEntityTask paid after the perform with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST, false);
        }
    }
}
