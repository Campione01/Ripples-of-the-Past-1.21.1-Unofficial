package rotp.addons.stickyfingers;

import rotp.core.api.healing.CrazyDiamondRestoreExtension;
import rotp.core.api.healing.CrazyDiamondRestoreExtensions;
import rotp.core.api.healing.ExternalRestoreContext;
import rotp.core.api.healing.ExternalRestoreResult;
import rotp.core.api.healing.GoldExperienceExternalHealingTarget;
import rotp.core.api.healing.GoldExperienceExternalHealingTargets;
import rotp.core.api.healing.LivingRestoreContext;
import rotp.core.api.healing.RestoreAugmentation;
import rotp.core.api.item.LivingHandUseBlockers;
import rotp.core.api.stand.ManualControlBlockers;

import rotp.addons.stickyfingers.entity.DetachedBodyPartEntity;
import rotp.addons.stickyfingers.state.DetachedBodyPartMasks;
import rotp.addons.stickyfingers.state.DetachedPartType;
import rotp.addons.stickyfingers.state.ZipperCombatData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class StickyFingersCoreIntegrations {
    private StickyFingersCoreIntegrations() {}

    static void register() {
        CrazyDiamondRestoreExtensions.register(
                StickyFingersAddon.id("detached_body_part"),
                new DetachedPartCrazyDiamondRestore());
        GoldExperienceExternalHealingTargets.register(
                StickyFingersAddon.id("detached_body_part"),
                (rawTarget, healer) -> {
                    if (!(rawTarget
                            instanceof DetachedBodyPartEntity part)
                            || part.isRemoved()) {
                        return null;
                    }
                    LivingEntity owner =
                            part.resolveTargetForQuery();
                    return owner != null
                            ? new GoldExperienceExternalHealingTarget(
                                    part, owner, owner)
                            : null;
                });
        LivingHandUseBlockers.register(
                StickyFingersAddon.id("detached_body_part"),
                (entity, hand) -> {
                    if (entity.level()
                            instanceof ServerLevel serverLevel) {
                        return ZipperCombatData.get(serverLevel)
                                .isHandBlocked(entity, hand);
                    }
                    return entity.level().isClientSide()
                            && DetachedBodyPartMasks
                                    .isClientHandBlocked(
                                            entity, hand);
                });
        // 1.16 StandUtilMixin: a headless user cannot enter manual control
        ManualControlBlockers.register(
                StickyFingersAddon.id("detached_head"),
                (user, stand) -> {
                    if (user.level()
                            instanceof ServerLevel serverLevel) {
                        return ZipperCombatData.get(serverLevel)
                                .partsFor(user.getUUID())
                                .contains(DetachedPartType.HEAD);
                    }
                    return user.level().isClientSide()
                            && DetachedBodyPartMasks
                                    .isClientHeadDetached(
                                            user.getUUID());
                });
    }

    private static final class DetachedPartCrazyDiamondRestore
            implements CrazyDiamondRestoreExtension {
        @Override
        public boolean canTarget(Entity target) {
            return target instanceof DetachedBodyPartEntity part
                    && !part.isRemoved()
                    && part.resolveTargetForQuery() != null;
        }

        @Override
        public ExternalRestoreResult restoreExternal(
                ExternalRestoreContext context) {
            if (!(context.target()
                    instanceof DetachedBodyPartEntity part)
                    || part.isRemoved()
                    || !(part.level()
                            instanceof ServerLevel)) {
                return ExternalRestoreResult.unhandled();
            }
            LivingEntity owner = part.resolveTargetForQuery();
            if (owner == null || owner == context.healer()) {
                return ExternalRestoreResult.unhandled();
            }
            part.startReturning();
            return ExternalRestoreResult.handled(
                    true, false, 0);
        }

        @Override
        public RestoreAugmentation afterLivingRestoreAttempt(
                LivingRestoreContext context) {
            if (context.target().level()
                    instanceof ServerLevel serverLevel) {
                ZipperCombatData.get(serverLevel)
                        .startReturningParts(
                                serverLevel,
                                context.target());
            }
            return RestoreAugmentation.none();
        }
    }
}
