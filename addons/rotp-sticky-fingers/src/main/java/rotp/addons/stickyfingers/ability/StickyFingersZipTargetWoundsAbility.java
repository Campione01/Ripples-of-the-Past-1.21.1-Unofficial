package rotp.addons.stickyfingers.ability;

import javax.annotation.Nullable;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import rotp.addons.stickyfingers.state.ZipperCombatData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class StickyFingersZipTargetWoundsAbility
        extends NoPoseStandEntityAbility {
    public StickyFingersZipTargetWoundsAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ZipTargetWoundsAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        cooldown(
                StickyFingersPhase1Spec
                        .ZIP_WOUNDS_COOLDOWN_TICKS);
        partsRequired(StandPart.ARMS);
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
        if (user == null
                || findLookTarget(user, user.level()) == null) {
            return ConditionCheck.NEGATIVE;
        }
        return ConditionCheck.POSITIVE;
    }

    // 1.16 sticky_fingers_zip_target_wounds was a StandAction, whose checkTarget refused only the user itself: the
    // user's own pet, mount or ally was zipped as well (070 StickyFingersZipTargetWounds.perform)
    @Override
    public boolean takesPressedTarget(
            StandEntity standEntity, ActionTarget target) {
        return true;
    }

    // A 1.16 StandAction had no overrideVanillaMouseTarget: a pressed torch
    // or grass stayed the press's target
    @Override
    public boolean pressedBlockGivesWayToEntity() {
        return false;
    }

    @Nullable
    static LivingEntity findLookTarget(
            LivingEntity user, Level level) {
        ActionTarget synced = getSyncedLookTarget(user, level);
        // 1.16 TargetRequirement.ENTITY refused a press at a block, a torch
        // or grass too, whatever stood behind it
        if (synced.getType() == ActionTarget.TargetType.BLOCK) {
            return null;
        }
        if (!synced.isEmpty(level)
                && HitResultUtil.isTargetWithinRange(
                        synced,
                        user,
                        level,
                        StickyFingersPhase1Spec
                                .ZIP_WOUNDS_TARGET_RANGE,
                        StickyFingersPhase1Spec
                                .ZIP_WOUNDS_TARGET_RANGE)) {
            Entity entity = synced.getMainEntity();
            if (entity instanceof LivingEntity living
                    && living != user) {
                return living;
            }
        }
        ActionTarget clipped = HitResultUtil.clip(
                user.getEyePosition(),
                user.getLookAngle(),
                StickyFingersPhase1Spec
                        .ZIP_WOUNDS_TARGET_RANGE,
                StickyFingersPhase1Spec
                        .ZIP_WOUNDS_TARGET_RANGE,
                level,
                entity -> entity instanceof LivingEntity
                        && entity != user,
                user,
                0.0D);
        Entity entity = clipped.getMainEntity();
        return entity instanceof LivingEntity living
                && living != user ? living : null;
    }

    // The aims only go from the user's client to the server, so that client
    // reads the aim it sends
    private static ActionTarget getSyncedLookTarget(
            LivingEntity user, Level level) {
        ActionTargetAim aim = level.isClientSide()
                        && user == ClientProxy.getClientPlayer()
                ? ClientsideAim.playerAim
                : LivingComponentAction.getAim(user);
        if (aim == null || aim.getTarget() == null) {
            return ActionTarget.EMPTY;
        }
        return aim.getTarget().resolveEntityId(level);
    }

    public static final class ZipTargetWoundsAction
            extends EntityActionInstance {
        public ZipTargetWoundsAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            LivingEntity user = getPowerUser();
            if (user != null) {
                captureActionTargetFromAim(user);
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }
            LivingEntity target = targetFromSnapshot(user);
            if (target == null) {
                target = findLookTarget(user, level());
            }
            if (target == null) {
                return;
            }
            // 1.16 StandAction.onPerform: the cost is paid with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    StickyFingersPhase1Spec.ZIP_WOUNDS_STAMINA,
                    false);
            ZipperCombatData.get(serverLevel)
                    .applyWounds(serverLevel, user, target);
            serverLevel.playSound(
                    null,
                    target,
                    StickyFingersAddon.ZIPPER_CLOSE.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        }

        @Nullable
        private LivingEntity targetFromSnapshot(
                LivingEntity user) {
            ActionTarget target =
                    getActionTargetSnapshot(level());
            if (target.isEmpty(level())
                    || !HitResultUtil.isTargetWithinRange(
                            target,
                            user,
                            level(),
                            StickyFingersPhase1Spec
                                    .ZIP_WOUNDS_TARGET_RANGE,
                            StickyFingersPhase1Spec
                                    .ZIP_WOUNDS_TARGET_RANGE)) {
                return null;
            }
            Entity entity = target.getMainEntity();
            return entity instanceof LivingEntity living
                    && living != user ? living : null;
        }
    }
}
