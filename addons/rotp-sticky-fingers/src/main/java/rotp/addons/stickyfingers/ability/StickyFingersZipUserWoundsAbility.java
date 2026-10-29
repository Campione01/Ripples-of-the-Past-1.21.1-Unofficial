package rotp.addons.stickyfingers.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import rotp.addons.stickyfingers.state.ZipperCombatData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

public final class StickyFingersZipUserWoundsAbility
        extends NoPoseStandEntityAbility {
    public StickyFingersZipUserWoundsAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ZipUserWoundsAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        cooldown(
                StickyFingersPhase1Spec
                        .ZIP_WOUNDS_COOLDOWN_TICKS);
        partsRequired(StandPart.ARMS);
    }

    public static final class ZipUserWoundsAction
            extends EntityActionInstance {
        public ZipUserWoundsAction(EntityActionType ability) {
            super(ability);
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
            // 1.16 StandAction.onPerform: the cost is paid with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    StickyFingersPhase1Spec.ZIP_WOUNDS_STAMINA,
                    false);
            ZipperCombatData.get(serverLevel)
                    .applyWounds(serverLevel, user, user);
            serverLevel.playSound(
                    null,
                    user,
                    StickyFingersAddon.ZIPPER_CLOSE.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        }
    }
}
