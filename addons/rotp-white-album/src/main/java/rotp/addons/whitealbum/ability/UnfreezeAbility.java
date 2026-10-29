package rotp.addons.whitealbum.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.whitealbum.WhiteAlbumAddon;
import rotp.addons.whitealbum.WhiteAlbumIce;
import rotp.addons.whitealbum.WhiteAlbumSpec;
import rotp.addons.whitealbum.entity.WhiteAlbumControllerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public final class UnfreezeAbility
        extends StandEntityAbility {
    public UnfreezeAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                UnfreezeAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(WhiteAlbumSpec.UNFREEZE_COOLDOWN_TICKS);
    }

    private static final class UnfreezeAction
            extends EntityActionInstance {
        private UnfreezeAction(EntityActionType ability) {
            super(ability);
            userWalkSpeed = 1.0F;
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel level)
                    || !(getPerformer()
                            instanceof WhiteAlbumControllerEntity
                            controller)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null
                    || WhiteAlbumAddon.controller(user)
                            != controller) {
                return;
            }

            WhiteAlbumIce.unfreeze(user, level);
            StandUtil.playStandEntitySound(
                    controller,
                    WhiteAlbumAddon.WA_UNFREEZE,
                    1.0F,
                    1.0F);
            user.playSound(
                    WhiteAlbumAddon.USER_UNFREEZE.get(),
                    1.0F,
                    1.0F);
            // 1.16 StandEntityTask paid staminaCost(60) after standPerform
            // and ignored the result; a short bar just drops to 0
            StandAbilityStamina.consume(
                    ability, power, WhiteAlbumSpec.UNFREEZE_STAMINA, false);
        }
    }
}
