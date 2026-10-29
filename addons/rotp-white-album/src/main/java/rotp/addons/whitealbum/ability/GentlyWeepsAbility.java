package rotp.addons.whitealbum.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.whitealbum.WhiteAlbumAddon;
import rotp.addons.whitealbum.WhiteAlbumSpec;
import rotp.addons.whitealbum.entity.WhiteAlbumControllerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public final class GentlyWeepsAbility
        extends StandEntityAbility {
    public GentlyWeepsAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                GentlyWeepsAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(
                WhiteAlbumSpec
                        .GENTLY_WEEPS_COOLDOWN_TICKS);
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
                && controller.isGentlyWeepsActive()
                ? WhiteAlbumAddon.GENTLY_WEEPS_OFF_SPRITE
                : WhiteAlbumAddon.GENTLY_WEEPS_NAME;
    }

    private static final class GentlyWeepsAction
            extends EntityActionInstance {
        private GentlyWeepsAction(
                EntityActionType ability) {
            super(ability);
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
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null
                    || power == null
                    || WhiteAlbumAddon.controller(user)
                            != controller) {
                return;
            }

            boolean enabled =
                    !controller.isGentlyWeepsActive();
            controller.setGentlyWeepsActive(enabled);
            rotp.core.powersystem.standpower.StandUtil
                    .playStandEntitySound(
                            controller,
                            enabled
                                    ? WhiteAlbumAddon.WA_GW_ON
                                    : WhiteAlbumAddon.WA_GW_OFF,
                            1.0F,
                            1.0F);
            user.playSound(
                    (enabled
                            ? WhiteAlbumAddon
                                    .USER_GW_ON
                            : WhiteAlbumAddon
                                    .USER_GW_OFF)
                            .get(),
                    1.0F,
                    1.0F);
        }
    }
}
