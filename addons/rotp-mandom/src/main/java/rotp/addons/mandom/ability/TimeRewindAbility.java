package rotp.addons.mandom.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.mandom.MandomRollbackGate;
import rotp.addons.mandom.state.MandomRewindService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class TimeRewindAbility extends StandEntityAbility {
    public TimeRewindAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, TimeRewindAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        // 1.16 set this with setCooldownTimer after a rewind, so neither Resolve nor Creative shortened it.
        cooldown(MandomRollbackGate.COOLDOWN_TICKS, 0);
        standAutoSummonMode(AutoSummonMode.FULL);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    // The 1.16 builder had no cooldown, so the technical part was 0.
    @Override
    public int getCooldownTechnical(Power<?> context) {
        return 0;
    }

    @Override
    public String getSpriteName(Power<?> context) {
        return super.getSpriteName(context) + "_";
    }

    public static final class TimeRewindAction
            extends EntityActionInstance {
        public TimeRewindAction(EntityActionType ability) {
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

            MandomRewindService.Result result =
                    MandomRewindService.rewind(
                            serverLevel, user, getPerformer());
            if (result == MandomRewindService.Result.SUCCESS) {
                StandPower power = StandPower.get(user);
                if (power != null
                        && ability instanceof TimeRewindAbility rewind) {
                    rewind.setCooldownOnUse(power);
                }
            }
            else if (user instanceof ServerPlayer player) {
                player.displayClientMessage(
                        Component.translatable(result.translationKey()),
                        true);
            }
        }
    }
}
