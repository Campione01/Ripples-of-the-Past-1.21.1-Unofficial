package rotp.addons.vampire_climb.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.impl.powers.vampirism.abilities.VampirismActionAbility;

import rotp.addons.vampire_climb.VampireClimbAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class VampireWallClimbAbility
        extends VampirismActionAbility {
    public static final float BLOOD_COST_PER_TICK = 5.0F;
    public static final double VELOCITY_SCALE = 0.25D;

    public VampireWallClimbAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                Integer.MAX_VALUE,
                0.0F,
                WallClimbInstance::new);
        setButtonHoldPhase(ActionPhase.PERFORM);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    @Override
    public String getResourceNamespace() {
        return VampireClimbAddon.MOD_ID;
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
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        // The donor's resource gate and debit exempt Creative players.
        if (!isCreative(context)
                && !getVampirismData(context).hasBlood(user, BLOOD_COST_PER_TICK)) {
            return ConditionCheck.NEGATIVE;
        }
        if (!user.getMainHandItem().isEmpty()) {
            return ConditionCheck.createNegative("hand");
        }
        return hasWallContact(user)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("wall");
    }

    private static boolean hasWallContact(LivingEntity user) {
        if (user.level().isClientSide()) {
            return user.horizontalCollision;
        }
        // Movement packets contain already-clipped positions, not the client's
        // collision flag. Exclude floor/ceiling contact from the server probe.
        AABB sides = user.getBoundingBox()
                .deflate(0.0D, 1.0E-4D, 0.0D)
                .inflate(1.0E-3D, 0.0D, 1.0E-3D);
        return user.level().getBlockCollisions(user, sides)
                .iterator().hasNext();
    }

    @Override
    public boolean canContinueAction(EntityActionInstance action) {
        LivingEntity user =
                action != null ? action.getPowerUser() : null;
        if (user == null || !user.isAlive()) {
            return false;
        }
        Power<?> context = getUserPower(user);
        return context != null
                && isAbilityAvailable(context)
                && checkConditions(context).isPositive();
    }

    public static Vec3 climbVelocity(Vec3 lookAngle) {
        return lookAngle.scale(VELOCITY_SCALE);
    }

    public static final class WallClimbInstance
            extends EntityActionInstance {
        public WallClimbInstance(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null || user.level().isClientSide()) {
                return;
            }
            if (!(user instanceof Player player && player.getAbilities().instabuild)
                    && !consumeBlood(user, BLOOD_COST_PER_TICK)) {
                forceStop();
                syncPhaseChanges();
                return;
            }
            user.setDeltaMovement(
                    climbVelocity(user.getLookAngle()));
            user.hurtMarked = true;
        }

        @Override
        public void onButtonStopHold() {
            forceStop();
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }
    }
}
