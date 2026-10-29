package rotp.addons.purplehaze.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.purplehaze.effect.PurpleHazeVirusEffect;
import rotp.addons.purplehaze.entity.PurpleHazeCloudEntity;
import rotp.addons.purplehaze.entity.PurpleHazeEntity;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class PurpleHazeViralPunchAbility
        extends StandEntityHeavyPunchAbility {
    private Holder<SoundEvent> userShoutSound;

    public PurpleHazeViralPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        // heavy finisher: shouts while sneaking too
        setPlaysVoiceLineOnSneak();
        createActionObj = ViralPunchAction::new;
    }

    public PurpleHazeViralPunchAbility userShoutSound(
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
        return standEntity instanceof PurpleHazeEntity purpleHaze
                && !purpleHaze.isMad()
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(
                        "cant_control_stand");
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
                && userShoutSound != null
                && (!(standEntity instanceof PurpleHazeEntity purpleHaze)
                        || !purpleHaze.isAutomationDispatch())) {
            sayShout(standUser, userShoutSound);
        }
    }

    public static final class ViralPunchAction
            extends StandEntityHeavyPunch {
        public ViralPunchAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected void afterHeavyPunchHit(
                StandEntity stand,
                LivingEntity target,
                DamageSource damageSource,
                float damage,
                boolean hurt) {
            if (stand instanceof PurpleHazeEntity purpleHaze
                    && purpleHaze.useCapsule()) {
                PurpleHazeVirusEffect
                        .applyVirusUnattenuated(
                                target, 0, 200);
            }
        }

        @Override
        protected void hitBlock(
                ActionTarget target,
                Level level,
                StandEntity stand,
                DamageSource damageSource,
                float damage,
                float explosionRadius) {
            if (level instanceof ServerLevel serverLevel
                    && stand
                            instanceof PurpleHazeEntity purpleHaze
                    && purpleHaze.useCapsule()
                    && stand.getUser() != null) {
                PurpleHazeCloudEntity.spawn(
                        serverLevel,
                        stand.getUser(),
                        purpleHaze,
                        target.getCenterPos(),
                        PurpleHazeCloudEntity
                                .DEFAULT_SPREAD_SPEED);
            }
            super.hitBlock(
                    target,
                    level,
                    stand,
                    damageSource,
                    damage,
                    explosionRadius);
        }
    }
}
