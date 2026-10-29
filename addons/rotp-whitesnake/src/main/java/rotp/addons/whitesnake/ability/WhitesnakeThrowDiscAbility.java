package rotp.addons.whitesnake.ability;

import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.whitesnake.disc.DiscPayload;
import rotp.addons.whitesnake.entity.projectile.DiscProjectile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class WhitesnakeThrowDiscAbility extends StandEntityAbility {
    public static final int PERFORM_TICKS = 15;
    public static final float STAMINA_COST = 3.0F;
    public static final float SHOT_VELOCITY = 2.0F;
    public static final float SHOT_INACCURACY = 0.25F;

    public WhitesnakeThrowDiscAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ThrowDiscAction::new);
        partsRequired(StandPart.ARMS);
        // 1.16 standPerformDuration(15) with no standRecoveryTicks: the task
        // ended with its perform
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        if (user == null || DiscPayload.capture(user).isEmpty()) {
            return ConditionCheck.createNegative(
                    "whitesnake_disc_payload");
        }
        return ConditionCheck.POSITIVE;
    }

    public static final class ThrowDiscAction extends EntityActionInstance {
        public ThrowDiscAction(EntityActionType ability) {
            super(ability);
        }

        private void throwDisc(Level level, StandEntity stand,
                LivingEntity user, StandPower standPower) {
            DiscPayload payload =
                    user != null ? DiscPayload.capture(user).orElse(null) : null;
            if (user == null || standPower == null || payload == null) {
                fail(user, "whitesnake_disc_payload");
                return;
            }
            if (!payload.isStillHeldBy(user)) {
                return;
            }

            boolean returnOnFailure =
                    payload.requiresReturnOnFailure()
                    || !(user instanceof Player player)
                    || !player.getAbilities().instabuild;
            DiscProjectile projectile = new DiscProjectile(stand, level);
            projectile.setPayload(payload, returnOnFailure);
            if (!payload.consumeFrom(user)) {
                fail(user, "whitesnake_disc_payload");
                return;
            }

            projectile.setShootingPosOf(stand);
            // 1.16 standEntity.shootProjectile(disc, 2.0F, 0.25F)
            projectile.shootFromRotation(
                    stand, SHOT_VELOCITY,
                    stand.getProjectileInaccuracy(SHOT_INACCURACY));
            addProjectileWithStandStats(projectile);
            StandUtil.playStandEntitySound(
                    stand,
                    ModSoundEvents.STAND_PUNCH_SWING.get(),
                    1.0F,
                    1.0F);
        }

        // 1.16 whitesnake_throw_disc standOffsetFront(): 0.5 in front at the
        // default y in full mode, turned by the view yaw
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            setStandOffset(
                    0.0D,
                    0.5D,
                    StandOffsetFromUser.Rotations.HEAD,
                    false);
        }

        @Override
        public void actionPerformStart() {
            Level level = level();
            if (level.isClientSide()
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }

            LivingEntity user = getPowerUser();
            StandPower standPower =
                    user != null ? StandPower.get(user) : null;
            throwDisc(level, stand, user, standPower);
            // 1.16 StandEntityTask: the cost is paid after the perform, also
            // when the disc had gone; a short bar just drops to 0.
            if (standPower != null) {
                StandAbilityStamina.consume(
                        ability, standPower, STAMINA_COST, false);
            }
        }

        private void fail(LivingEntity user, String reason) {
            if (user == null) {
                return;
            }
            ConditionCheck.sendActionFailedMessage(
                    ability instanceof Ability abilityObject
                            ? abilityObject
                            : null,
                    ConditionCheck.createNegative(reason),
                    user);
        }
    }
}
