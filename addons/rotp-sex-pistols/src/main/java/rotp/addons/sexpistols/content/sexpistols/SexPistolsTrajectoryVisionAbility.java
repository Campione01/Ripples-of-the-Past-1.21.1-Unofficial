package rotp.addons.sexpistols.content.sexpistols;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

// 1.16 InitStands :95 built it as a bare StandAction.Builder: no cooldown.
public final class SexPistolsTrajectoryVisionAbility
        extends Ability {
    public SexPistolsTrajectoryVisionAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck parent =
                super.checkSpecificConditions(context);
        if (!parent.isPositive()) {
            return parent;
        }
        // 1.16 sex_pistols actions did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        return context instanceof StandPower standPower
                && standPower.getPowerType()
                        == SexPistolsStands.SEX_PISTOLS.get()
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    @Override
    public Component getName(Power<?> context) {
        LivingEntity owner =
                context != null ? context.getUser() : null;
        boolean enabled = owner != null
                && SexPistolsAttachments.coordinator(owner)
                        .trajectoryVision();
        return Component.translatable(
                getTranslationKey(
                        enabled ? ".on" : ".off"));
    }

    @Override
    public String getSpriteName(Power<?> context) {
        LivingEntity owner =
                context != null ? context.getUser() : null;
        boolean enabled = owner != null
                && SexPistolsAttachments.coordinator(owner)
                        .trajectoryVision();
        return enabled
                ? "trajectory_vision_on"
                : "trajectory_vision_off";
    }

    @Override
    public void onClick(
            Level level,
            LivingEntity owner,
            FriendlyByteBuf extraClientInput) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        StandPower standPower = StandPower.get(owner);
        if (standPower == null
                || standPower.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()) {
            return;
        }
        SexPistolsAttachments.coordinator(owner)
                .toggleTrajectoryVision(owner);
    }
}
