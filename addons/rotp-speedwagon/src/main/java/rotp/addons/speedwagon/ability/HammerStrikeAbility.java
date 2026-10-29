package rotp.addons.speedwagon.ability;

import rotp.core.init.ModItems;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class HammerStrikeAbility
        extends SpeedwagonActionAbility {
    private static final double MAX_TARGET_RANGE_SQR = 25.0D;

    public HammerStrikeAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                750.0F,
                0.0F,
                20,
                HammerStrikeAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP, 2);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 3);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        if (user == null || !hasHammer(user)) {
            return ConditionCheck.createNegative("hammer");
        }
        LivingEntity target = targetFromAim(user);
        return target != null
                && user.distanceToSqr(target)
                        <= MAX_TARGET_RANGE_SQR
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    private static boolean hasHammer(LivingEntity user) {
        return user.getMainHandItem()
                        .is(ModItems.SLEDGEHAMMER.get())
                || user.getOffhandItem()
                        .is(ModItems.SLEDGEHAMMER.get());
    }

    // The HUD and the client's own press check run on the user's client,
    // which holds no stored aim of its user (1.16: the mouse target).
    private static LivingEntity targetFromAim(
            LivingEntity user) {
        return livingTarget(
                LivingComponentAction.getAimTarget(user));
    }

    private static LivingEntity livingTarget(
            ActionTarget target) {
        if (target != null
                && target.getType() == TargetType.ENTITY
                && target.getMainEntity()
                        instanceof LivingEntity living
                && living.isAlive()) {
            return living;
        }
        return null;
    }

    public static final class HammerStrikeAction
            extends EntityActionInstance {
        public HammerStrikeAction(EntityActionType ability) {
            super(ability);
            userWalkSpeed = 0.5F;
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            LivingEntity user = getPowerUser();
            if (user != null) {
                captureActionTargetFromAim(user);
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(ability
                            instanceof HammerStrikeAbility
                                    strikeAbility)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null
                    || !hasHammer(user)
                    || !strikeAbility.consumeInitialEnergy(user)) {
                forceStop();
                syncPhaseChanges();
                return;
            }

            LivingEntity target = livingTarget(
                    getActionTargetSnapshot(level()));
            if (target == null
                    || user.distanceToSqr(target)
                            > MAX_TARGET_RANGE_SQR) {
                return;
            }
            DamageSource source =
                    user instanceof Player player
                            ? level().damageSources()
                                    .playerAttack(player)
                            : level().damageSources()
                                    .mobAttack(user);
            DamageUtil.hurtThroughInvulTicks(
                    target, source, 12.0F);
            user.swing(
                    user.getMainHandItem()
                                    .is(ModItems.SLEDGEHAMMER.get())
                            ? InteractionHand.MAIN_HAND
                            : InteractionHand.OFF_HAND,
                    true);
        }
    }
}
