package rotp.addons.purplehaze.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.purplehaze.entity.PurpleHazeEntity;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class PurpleHazeVirusAuraAbility
        extends StandEntityAbility {
    private Holder<SoundEvent> userShoutSound;

    public PurpleHazeVirusAuraAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, VirusAuraAction::new);
        usageGroup = AbilityUsageGroup.UTILITY;
        // 1.16 InitStands :67: perform 1, no standRecoveryTicks
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        standAutoSummonMode(AutoSummonMode.FULL);
    }

    public PurpleHazeVirusAuraAbility userShoutSound(
            Holder<SoundEvent> sound) {
        userShoutSound = sound;
        return this;
    }

    @Override
    protected ConditionCheck checkStandEntityConditions(
            StandPower standPower, StandEntity standEntity) {
        ConditionCheck parent = super.checkStandEntityConditions(
                standPower, standEntity);
        if (!parent.isPositive()) {
            return parent;
        }
        if (!(standEntity
                instanceof PurpleHazeEntity purpleHaze)) {
            return ConditionCheck.NEGATIVE;
        }
        if (purpleHaze.isMad()) {
            return ConditionCheck.createNegative(
                    "cant_control_stand");
        }
        return !purpleHaze.isCloseToUser()
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(
                        "can_hurt_user");
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity standUser,
            LivingEntity standEntity) {
        super.initActionFromConfig(
                action, level, standUser, standEntity);
        if (!level.isClientSide()
                && standUser != null
                && userShoutSound != null) {
            sayShout(standUser, userShoutSound);
        }
    }

    @Override
    public boolean retractsStandAfterAction(
            StandPower standPower,
            StandEntity standEntity,
            EntityActionInstance action) {
        return false;
    }

    public static final class VirusAuraAction
            extends EntityActionInstance {
        public VirusAuraAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!level().isClientSide()
                    && getPerformer()
                            instanceof PurpleHazeEntity stand) {
                stand.setAuraActive(!stand.hasAura());
            }
        }
    }
}
