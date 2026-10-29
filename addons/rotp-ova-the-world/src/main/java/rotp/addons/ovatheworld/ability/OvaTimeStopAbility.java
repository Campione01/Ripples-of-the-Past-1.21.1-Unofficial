package rotp.addons.ovatheworld.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands.theworld.TimeStopAbility;

import rotp.addons.ovatheworld.OvaTheWorldAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class OvaTimeStopAbility extends TimeStopAbility {
    // 1.16 THE_WORLD_OVA_TIME_STOP: staminaCost(250), staminaCostTick(7.5).
    // The start is priced by the OVA time-stop policy and the per-tick cost
    // at PreStart, both scaled from the core's 225 / 9 like the core cost.
    public static final float LEGACY_INITIAL_STAMINA = 250.0F;
    public static final float LEGACY_STAMINA_PER_TICK = 7.5F;

    public OvaTimeStopAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        // 1.16 TheWorldTimeStop: a hit during the charge drops it.
        setCancelHeldOnGettingAttacked();
    }

    @Override
    protected int getRequiredResolveLevelToUnlock(
            Power<?> context) {
        return 2;
    }

    // 1.16 TheWorldTimeStop: the 30-tick charge takes 20 from Resolve 4.
    @Override
    protected boolean shortensChargeAtResolveFour(StandPower power) {
        return true;
    }

    @Override
    public HeldInput onKeyPress(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput,
            InputMethod inputMethod,
            float clickHoldResolveTime,
            BufferingState bufferingState) {
        StandPower power = !level.isClientSide()
                ? PowerClass.STAND.get(user)
                : null;
        boolean standAlreadySummoned =
                power != null
                        && power.getSummonedStandEntity() != null;
        HeldInput heldInput = super.onKeyPress(
                level,
                user,
                extraClientInput,
                inputMethod,
                clickHoldResolveTime,
                bufferingState);
        if (!level.isClientSide()
                && bufferingState.isActionSuccess
                && heldInput != null) {
            // 1.16 .shout / voiceLineWithStandSummoned: sneak rule
            sayShout(
                    user,
                    standAlreadySummoned
                            ? OvaTheWorldAddon.DIO_OVA_TIME_STOP
                            : OvaTheWorldAddon.DIO_OVA_THE_WORLD);
        }
        return heldInput;
    }
}
