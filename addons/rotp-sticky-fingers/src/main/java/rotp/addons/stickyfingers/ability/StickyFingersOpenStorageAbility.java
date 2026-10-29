package rotp.addons.stickyfingers.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import rotp.addons.stickyfingers.entity.DetachedBodyPartEntity;
import rotp.addons.stickyfingers.storage.ZipperStorageData;
import rotp.addons.stickyfingers.storage.ZipperStorageData.OpenResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;

public final class StickyFingersOpenStorageAbility
        extends NoPoseStandEntityAbility {
    public StickyFingersOpenStorageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                OpenStorageAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
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
        return user != null
                && !findServerTarget(user, user.level())
                        .isEmpty(user.level())
                ? check
                : ConditionCheck.NEGATIVE;
    }

    public static ActionTarget findServerTarget(
            LivingEntity user, Level level) {
        ActionTarget target = HitResultUtil.clip(
                user.getEyePosition(),
                user.getLookAngle(),
                StickyFingersPhase1Spec.STORAGE_RANGE,
                StickyFingersPhase1Spec.STORAGE_RANGE,
                level,
                entity -> validEntityTarget(user, entity),
                user,
                0.0D,
                // 1.16 OpenStorageInTarget (TargetRequirement.ANY) took the client crosshair: an OUTLINE pick
                ClipContext.Block.OUTLINE);
        return HitResultUtil.isTargetWithinRange(
                        target,
                        user,
                        level,
                        StickyFingersPhase1Spec.STORAGE_RANGE,
                        StickyFingersPhase1Spec.STORAGE_RANGE)
                ? target
                : ActionTarget.EMPTY;
    }

    private static boolean validEntityTarget(
            LivingEntity user, Entity entity) {
        return entity instanceof LivingEntity
                && entity != user
                && !(entity instanceof DetachedBodyPartEntity);
    }

    public static final class OpenStorageAction
            extends EntityActionInstance {
        public OpenStorageAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)
                    || !(getPowerUser()
                            instanceof ServerPlayer user)) {
                return;
            }
            ActionTarget target =
                    findServerTarget(user, serverLevel);
            OpenResult result = switch (target.getType()) {
                case ENTITY ->
                        target.getMainEntity()
                                        instanceof LivingEntity living
                                ? ZipperStorageData
                                        .get(serverLevel.getServer())
                                        .openEntityStorage(
                                                user,
                                                living,
                                                false)
                                : OpenResult.INVALID_TARGET;
                case BLOCK -> ZipperStorageData
                        .get(serverLevel.getServer())
                        .openBlockStorage(
                                user, target.getBlockPos());
                default -> OpenResult.INVALID_TARGET;
            };
            if (result != OpenResult.OPENED) {
                user.displayClientMessage(
                        Component.translatable(
                                result == OpenResult.NOT_OWNER
                                        ? "rotp_stfn.message"
                                                + ".storage_not_owner"
                                        : "rotp_stfn.message"
                                                + ".storage_rejected"),
                        true);
            }
        }
    }
}
