package rotp.addons.whitealbum.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.whitealbum.WhiteAlbumAddon;
import rotp.addons.whitealbum.WhiteAlbumSpec;
import rotp.addons.whitealbum.entity.WhiteAlbumControllerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public final class FreezeFloorAbility
        extends StandEntityAbility {
    public FreezeFloorAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                FreezeFloorAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(
                WhiteAlbumSpec
                        .FREEZE_FLOOR_COOLDOWN_TICKS);
    }

    @Override
    public String getSpriteName(Power<?> context) {
        LivingEntity user =
                context != null ? context.getUser() : null;
        WhiteAlbumControllerEntity controller =
                user != null
                        ? WhiteAlbumAddon.controller(user)
                        : null;
        return controller != null
                && controller.isFreezeFloorActive()
                ? WhiteAlbumAddon.UNFREEZE_NAME
                : WhiteAlbumAddon.FREEZE_FLOOR_NAME;
    }

    private static final class FreezeFloorAction
            extends EntityActionInstance {
        private FreezeFloorAction(EntityActionType ability) {
            super(ability);
            userWalkSpeed = 1.0F;
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel)
                    || !(getPerformer()
                            instanceof WhiteAlbumControllerEntity
                            controller)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null
                    || WhiteAlbumAddon.controller(user)
                            != controller) {
                return;
            }

            boolean enabled =
                    !controller.isFreezeFloorActive();
            controller.setFreezeFloorActive(enabled);
            StandUtil.playStandEntitySound(
                    controller,
                    enabled
                            ? WhiteAlbumAddon.WA_WALKER
                            : WhiteAlbumAddon.WA_UNFREEZE,
                    1.0F,
                    1.0F);
        }
    }
}
