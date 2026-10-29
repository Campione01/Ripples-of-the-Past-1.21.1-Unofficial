package rotp.addons.diverdown.ability;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

// 1.16 DiverDownEntityPhasing was a StandEntityAction (049 :35, InitStands :72), so a Stand task that could not be cancelled
// held its press back in the Stand's queue (StandEntityAction.checkTaskCancelling :127-143); the dive itself needs no
// task of its own here, it goes off at the press once nothing holds it back.
public final class EntityPhasingAbility extends StandEntityAbility {
    public static final int RESOLVE_LEVEL = 3;

    public EntityPhasingAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, EntityActionInstance::new);
        resolveLevelToUnlock(RESOLVE_LEVEL);
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
        // the replay of a queued Retract that finds Diver Down out of its target: 1.16 clicked the Retract itself again
        // and refused it (049 DiverDownRetract :33-41), it never dived
        if (bufferingState.isBuffered() && RetractAbility.takeQueuedPress(user)) {
            ConditionCheck.sendActionFailedMessage(
                    this,
                    ConditionCheck.createNegative("diver_down_not_embedded"),
                    user);
            return null;
        }
        if (wouldBufferInput(user)) {
            if (bufferingState.canBuffer()) {
                bufferingState.setToBuffer();
                RetractAbility.forgetQueuedPress(user);
            }
            return null;
        }
        bufferingState.setActionSuccess();
        onClick(level, user, extraClientInput);
        return null;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        // 1.16 entity_phasing did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        // 1.16 had no other press condition: a press with Diver Down away
        // summons it (onClick)
        return ConditionCheck.POSITIVE;
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        DiverDownStandEntity stand =
                DiverDownAddon.activeStand(
                        context != null
                                ? context.getUser()
                                : null);
        if (stand != null && stand.isEmbedded()
                && abilities != null) {
            Ability retract = abilities.getContextVariation(
                    DiverDownAddon.RETRACT_NAME);
            if (retract != null
                    && retract.isAbilityAvailable(context)) {
                return retract;
            }
        }
        return super.replaceWithSubAbility(context, abilities);
    }

    @Override
    public void onClick(
            Level level,
            LivingEntity owner,
            FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()) {
            return;
        }
        DiverDownStandEntity out =
                DiverDownAddon.activeStand(owner);
        StandPower power = StandPower.get(owner);
        // 1.16 DiverDownEntityPhasing was a Stand entity action: its press
        // summoned Diver Down, not yet in the level, before the target was
        // checked (StandEntityAction.onClick), and kept it only for a press
        // that went on
        DiverDownStandEntity summoned =
                out == null ? summonForPress(owner, power) : null;
        LivingEntity target = DiverDownTargeting.living(
                owner,
                out,
                StandSkillWalls.wallsHideEntities(level, this));
        if (summoned != null) {
            DiverDownAddon.DIVER_DOWN.get().finalizeStandSummonFromAction(
                    owner, power, summoned, target != null);
        }
        DiverDownStandEntity stand = out != null ? out : summoned;
        if (stand != null
                && target != null
                && stand.setEmbeddedTarget(target)) {
            sayShout(
                    owner,
                    DiverDownAddon
                            .ANASUI_DIVER_ENTITY_PHASING);
            StandUtil.playStandEntitySound(
                    stand,
                    DiverDownAddon.DIVER_DOWN_ENTITY_PHASING.get(),
                    1.0F,
                    1.0F);
        }
    }

    private static DiverDownStandEntity summonForPress(
            LivingEntity owner, StandPower power) {
        if (power != null
                && !power.isSummoned()
                && power.getPowerType() == DiverDownAddon.DIVER_DOWN.get()
                && DiverDownAddon.DIVER_DOWN.get().summon(
                        owner, power, entity -> {}, false)
                && power.getSummonedStandEntity()
                        instanceof DiverDownStandEntity stand) {
            return stand;
        }
        return null;
    }
}
