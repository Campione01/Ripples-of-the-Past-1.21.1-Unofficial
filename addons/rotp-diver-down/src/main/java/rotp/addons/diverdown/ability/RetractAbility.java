package rotp.addons.diverdown.ability;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

// 1.16 DiverDownRetract was a StandEntityAction (049 :23, InitStands :75), so a Stand task that could not be cancelled
// held its press back in the Stand's queue (StandEntityAction.checkTaskCancelling :127-143), as Entity Phasing's; the
// retraction itself needs no task of its own here, it goes off at the press once nothing holds it back.
public final class RetractAbility extends StandEntityAbility {
    // Users whose buffered press of the Entity Phasing key is a Retract: the core input buffer keeps the key's base
    // ability and resolves it again at the replay, 1.16 queued the Retract itself (StandEntity.queueNextAction).
    private static final Set<LivingEntity> QUEUED_PRESS =
            Collections.newSetFromMap(new WeakHashMap<>());

    public RetractAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, EntityActionInstance::new);
        resolveLevelToUnlock(EntityPhasingAbility.RESOLVE_LEVEL);
        // 1.16 SHIFT variation: shouts while sneaking too
        setPlaysVoiceLineOnSneak();
    }

    @Override
    public HeldInput onKeyPress(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput,
            InputMethod inputMethod,
            float clickHoldResolveTime,
            BufferingState bufferingState) {
        if (level.isClientSide()) {
            return null;
        }
        if (wouldBufferInput(user)) {
            if (bufferingState.canBuffer()) {
                bufferingState.setToBuffer();
                QUEUED_PRESS.add(user);
            }
            return null;
        }
        bufferingState.setActionSuccess();
        onClick(level, user, extraClientInput);
        return null;
    }

    // true once for a user whose buffered press is a queued Retract
    static boolean takeQueuedPress(LivingEntity user) {
        return QUEUED_PRESS.remove(user);
    }

    // a press of the key that is buffered itself replaces the queued Retract in the core buffer
    static void forgetQueuedPress(LivingEntity user) {
        QUEUED_PRESS.remove(user);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        // 1.16 retract did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        DiverDownStandEntity stand =
                DiverDownAddon.activeStand(
                        context != null
                                ? context.getUser()
                                : null);
        return stand != null && stand.isEmbedded()
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(
                        "diver_down_not_embedded");
    }

    @Override
    public void onClick(
            Level level,
            LivingEntity owner,
            FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()) {
            return;
        }
        DiverDownStandEntity stand =
                DiverDownAddon.activeStand(owner);
        if (stand == null || !stand.isEmbedded()) {
            return;
        }
        stand.clearEmbeddedTarget();
        sayShout(owner, DiverDownAddon.ANASUI_DIVER_RETRACT);
        // 1.16 diver_down_retract .standSound(DIVER_DOWN_UNSUMMON) (InitStands :75)
        StandUtil.playStandEntitySound(
                stand,
                DiverDownAddon.DIVER_DOWN_UNSUMMON.get(),
                1.0F,
                1.0F);
    }
}
