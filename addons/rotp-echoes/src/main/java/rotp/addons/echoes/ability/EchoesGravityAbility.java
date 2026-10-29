package rotp.addons.echoes.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.state.EchoesGravityService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class EchoesGravityAbility extends StandEntityAbility {
    public enum Mode {
        PROJECTILE(100.0F, 30),
        FREEZE_TARGET(150.0F, 40),
        HAND_FREEZE(150.0F, 40),
        FREEZE_SELF(50.0F, 20),
        INFUSE(75.0F, 20);

        private final float staminaCost;
        private final int recoveryTicks;

        Mode(float staminaCost, int recoveryTicks) {
            this.staminaCost = staminaCost;
            this.recoveryTicks = recoveryTicks;
        }

        public float staminaCost() {
            return staminaCost;
        }

        public int recoveryTicks() {
            return recoveryTicks;
        }
    }

    public EchoesGravityAbility(
            AbilityType<?> abilityType, AbilityId abilityId, Mode mode) {
        super(
                abilityType,
                abilityId,
                type -> new GravityAction(type, mode));
        usageGroup = mode == Mode.PROJECTILE
                ? AbilityUsageGroup.COMBAT
                : AbilityUsageGroup.UTILITY;
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.ARMS);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, mode.recoveryTicks());
    }

    // 092 GravityProjectileAction has no gravity-item condition: without an
    // item it throws nothing, and StandEntityTask still pays its cost.
    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()
                || !(context instanceof StandPower power)
                || !RotpEchoes.isEchoesUser(power.getUser())) {
            return base.isPositive() ? ConditionCheck.NEGATIVE : base;
        }
        return base;
    }

    public static final class GravityAction extends EntityActionInstance {
        private final Mode mode;

        public GravityAction(EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        // 092 staminaCost: StandEntityTask performs the action, then pays
        // its cost and ignores the result, so a short bar is drained and
        // the action still happens.
        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power = user != null ? StandPower.get(user) : null;
            if (!(user instanceof ServerPlayer player) || power == null) {
                startRecovery();
                return;
            }
            EchoesGravityService.perform(player, mode);
            StandAbilityStamina.consume(
                    ability, power, mode.staminaCost(), false);
        }
    }
}
