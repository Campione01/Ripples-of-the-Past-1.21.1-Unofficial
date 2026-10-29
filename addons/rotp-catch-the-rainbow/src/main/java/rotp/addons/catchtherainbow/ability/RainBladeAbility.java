package rotp.addons.catchtherainbow.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import rotp.addons.catchtherainbow.entity.RainBladeEntity;
import rotp.addons.catchtherainbow.util.RainExposure;
import net.minecraft.world.entity.LivingEntity;

public final class RainBladeAbility extends StandEntityAbility {
    public static final float STAMINA_COST = 25.0F;
    // 1.16 InitStands :58: a StandAction, performed at the press with no
    // Stand task after it, so no other press waited for it
    public static final int RECOVERY_TICKS = 0;
    public static final int COOLDOWN_TICKS = 10;
    public static final float PROJECTILE_SPEED = 2.0F;
    // 1.16 getProjectileInaccuracy(base attack speed / 9), 9 / 9
    public static final float PROJECTILE_INACCURACY = 1.0F;

    public RainBladeAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, RainBladeAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(
                ActionPhase.RECOVERY, RECOVERY_TICKS);
        partsRequired(StandPart.MAIN_BODY);
        cooldown(COOLDOWN_TICKS);
    }

    // 1.16 InitStands :58: a StandAction, held and performed by its user's
    // power (its Stand while out), never a task of the Stand
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context != null
                ? context.getUser()
                : null;
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(user);
        if (user == null
                || controller == null
                        && !CatchTheRainbowAddon.summonsOnPress(user)) {
            return ConditionCheck.createNegative(
                    "catch_the_rainbow_not_summoned");
        }
        if (!CatchTheRainbowAddon.isCreative(user)
                && !RainExposure.isExposedToRain(user)) {
            return ConditionCheck.createNegative("needs_rain");
        }
        if (!CatchTheRainbowAddon.isCreative(user)
                && controller != null && controller.isMergeActive()) {
            return ConditionCheck.createNegative(
                    "dont_have_merge");
        }
        return ConditionCheck.POSITIVE;
    }

    public static final class RainBladeAction
            extends EntityActionInstance {
        public RainBladeAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 InitStands :58-65: a StandAction its user's power performed,
        // which a new Stand's summon lock never held back
        @Override
        public boolean isHeldBackBySummonLock() {
            return false;
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer()
                            instanceof CatchTheRainbowEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null
                    || !CatchTheRainbowAddon.isCreative(user)
                            && (stand.isMergeActive()
                                    || !RainExposure.isExposedToRain(user))) {
                startRecovery();
                return;
            }
            // 1.16 StandAction.onPerform: the cost is paid with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(ability, power, STAMINA_COST, false);

            int variant = user.getRandom().nextInt(3) + 1;
            RainBladeEntity blade =
                    new RainBladeEntity(user, level(), variant);
            blade.setShootingPosOf(user);
            blade.shootFromRotation(
                    user,
                    PROJECTILE_SPEED,
                    stand.getProjectileInaccuracy(PROJECTILE_INACCURACY));
            addProjectileWithStandStats(blade);
            StandUtil.playStandEntitySound(
                    stand,
                    CatchTheRainbowAddon.RAIN_BLADE_SOUND,
                    1.0F,
                    1.0F);
        }
    }
}
