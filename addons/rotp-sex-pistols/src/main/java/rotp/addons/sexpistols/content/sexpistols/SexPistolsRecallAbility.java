package rotp.addons.sexpistols.content.sexpistols;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsSounds;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class SexPistolsRecallAbility extends Ability {
    public static final int COOLDOWN_TICKS = 10;

    public SexPistolsRecallAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        cooldown(COOLDOWN_TICKS);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
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
    public String getSpriteName(Power<?> context) {
        return "sex_pistols_stand_recall";
    }

    @Override
    public void onClick(
            Level level,
            LivingEntity owner,
            FriendlyByteBuf extraClientInput) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        StandPower standPower = StandPower.get(owner);
        if (standPower == null
                || standPower.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()) {
            return;
        }
        int recalled =
                SexPistolsAttachments.coordinator(owner)
                        .recallAttachmentsAndScouts(
                                serverLevel, owner);
        if (recalled <= 0) {
            ConditionCheck.sendActionFailedMessage(
                    this,
                    ConditionCheck.createNegative(
                            "sex_pistols_recall_empty"),
                    owner);
            return;
        }
        setCooldownOnUse(standPower);
        JojoModUtil.sayVoiceLine(
                owner,
                SexPistolsSounds.randomMistaRecall(
                        owner.getRandom()));
        level.playSound(
                null,
                owner.getX(),
                owner.getY(),
                owner.getZ(),
                SexPistolsSounds.randomRicochet(
                        owner.getRandom()),
                owner.getSoundSource(),
                0.7F,
                0.85F);
    }
}
