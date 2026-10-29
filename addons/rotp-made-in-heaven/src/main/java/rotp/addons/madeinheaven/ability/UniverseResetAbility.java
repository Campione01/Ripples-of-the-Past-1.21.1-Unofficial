package rotp.addons.madeinheaven.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.madeinheaven.MadeInHeavenAddon;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class UniverseResetAbility
        extends StandEntityAbility {
    public static final float CONDITION_STAMINA = 300.0F;
    public static final float EXECUTION_STAMINA = 500.0F;
    public static final int RESET_TICKS = 1200;
    public static final double RESET_RADIUS = 192.0D;

    public UniverseResetAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                UniverseResetAction::new);
        partsRequired(StandPart.MAIN_BODY);
        resolveLevelToUnlock(4);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base =
                super.checkSpecificConditions(context);
        return base.isPositive()
                ? ConditionCheck.noMessage(
                        context instanceof StandPower power
                                && meetsConditionStamina(power))
                : base;
    }

    // 1.16 UniverseReset.checkStandConditions: below 300 stamina a plain
    // NEGATIVE, so no message.
    private static boolean meetsConditionStamina(StandPower power) {
        return power.getStamina() >= CONDITION_STAMINA;
    }

    /** The automatic reset: 1.16 clicked the action unsneaked, so it always shouts. */
    public static boolean execute(ServerPlayer owner) {
        // That click still went through the action's stamina condition.
        StandPower ownerPower = StandPower.get(owner);
        if (ownerPower == null
                || !meetsConditionStamina(ownerPower)) {
            return false;
        }
        return execute(owner, null);
    }

    /** shoutingAbility: the used ability; its shout follows the 1.16 sneak rule. */
    public static boolean execute(
            ServerPlayer owner, EntityActionType shoutingAbility) {
        StandPower ownerPower = StandPower.get(owner);
        if (ownerPower == null
                || !ownerPower.isAbilityUnlocked(
                        MadeInHeavenAddon
                                .UNIVERSE_RESET_NAME)) {
            return false;
        }
        ServerLevel level = owner.serverLevel();
        StandEntity ownerStand =
                ownerPower.getSummonedStandEntity();
        // 1.16 mih_universe_reset .shout(MIH_UNIVERSE_RESET_USER): a user voice line
        if (shoutingAbility != null) {
            Ability.sayShoutOf(
                    shoutingAbility,
                    owner,
                    MadeInHeavenAddon.MIH_UNIVERSE_RESET_USER);
        }
        else {
            JojoModUtil.sayVoiceLine(
                    owner,
                    MadeInHeavenAddon.MIH_UNIVERSE_RESET_USER);
        }

        for (LivingEntity target :
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        owner.getBoundingBox()
                                .inflate(RESET_RADIUS),
                        LivingEntity::isAlive)) {
            level.sendParticles(
                    ParticleTypes.END_ROD,
                    target.getX(),
                    target.getY(0.5D),
                    target.getZ(),
                    30,
                    0.5D,
                    0.5D,
                    0.5D,
                    1.0D);
            if (target == owner || target == ownerStand) {
                // 1.16 UniverseReset: field_76429_m = Resistance.
                target.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_RESISTANCE,
                        RESET_TICKS,
                        4,
                        false,
                        false,
                        true));
                ownerPower.setAbilityCooldown(
                        MadeInHeavenAddon.TIME_SYSTEM_NAME,
                        RESET_TICKS);
                continue;
            }

            StandPower targetStandPower =
                    StandPower.get(target);
            if (targetStandPower != null
                    && targetStandPower.hasPower()) {
                for (String abilityName :
                        targetStandPower
                                .getMoveset()
                                .abilities
                                .keySet()) {
                    if (targetStandPower
                            .isAbilityUnlocked(abilityName)) {
                        targetStandPower.setAbilityCooldown(
                                abilityName, RESET_TICKS);
                    }
                }
                targetStandPower.setStamina(0.0F);
                targetStandPower.setLeapCooldown(
                        RESET_TICKS);
                if (targetStandPower
                                .getSummonedStandEntity()
                        == null
                        && targetStandPower.getPowerType()
                                instanceof EntityStandType type) {
                    type.summon(
                            target,
                            targetStandPower);
                }
            }
            PlayerPower playerPower =
                    PowerClass.PLAYER_POWER.get(target);
            if (playerPower != null) {
                playerPower.setLeapCooldown(RESET_TICKS);
            }
            // 1.16: field_76419_f = Mining Fatigue.
            target.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SLOWDOWN,
                    RESET_TICKS,
                    10,
                    false,
                    false,
                    true));
        }
        // 1.16 StandEntityTask took staminaCost(500) after standPerform and
        // ignored the result: a short bar is drained to 0.
        ownerPower.consumeStamina(EXECUTION_STAMINA);
        return true;
    }

    private static final class UniverseResetAction
            extends EntityActionInstance {
        private UniverseResetAction(
                EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!level().isClientSide()
                    && getPowerUser()
                            instanceof ServerPlayer owner) {
                execute(owner, ability);
            }
        }
    }
}
