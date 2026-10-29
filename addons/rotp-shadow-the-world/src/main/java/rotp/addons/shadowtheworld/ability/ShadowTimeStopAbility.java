package rotp.addons.shadowtheworld.ability;

import java.util.Locale;

import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.impl.stands.theworld.TimeStopAbility;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class ShadowTimeStopAbility extends TimeStopAbility {
    // 1.16 SHADOW_WORLD_TIME_STOP holdToFire(40). SHADOWWORLDTimeStop's
    // 20-tick Resolve 4 helper (shortedHoldDuration) was private and never
    // called, so the charge never shortened with Resolve.
    private static final int LEGACY_CHARGE_TICKS = 40;

    public ShadowTimeStopAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        // 1.16 cancelHeldOnGettingAttacked: a hit during the charge
        // drops it.
        setCancelHeldOnGettingAttacked();
        // 1.16 SHADOW_WORLD_TIME_STOP builder: ignoresPerformerStun(), so a
        // stunned Stand keeps its charge.
        setIgnoresPerformerStun();
    }

    @Override
    protected int getRequiredResolveLevelToUnlock(
            Power<?> context) {
        return 2;
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        // 1.16 getHoldDurationToFire: TimeStop's charge is 0 in stopped
        // time, and the health ratio keeps it 0.
        if (action.phasesLength.getFloat(
                ActionPhase.BUTTON_CHARGE) <= 0.0F) {
            return;
        }
        StandPower power = powerUser != null
                ? PowerClass.STAND.get(powerUser)
                : null;
        int chargeTicks = LEGACY_CHARGE_TICKS;
        if (powerUser != null
                && powerUser.getMaxHealth() > 0.0F
                && (power == null || !power.isUserCreative())) {
            chargeTicks = Mth.ceil(
                    LEGACY_CHARGE_TICKS
                            * powerUser.getHealth()
                            / powerUser.getMaxHealth());
        }
        action.phasesLength.put(
                ActionPhase.BUTTON_CHARGE,
                Math.max(chargeTicks, 1));
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
        // 1.16 TimeStop#getShout: no line inside stopped time.
        boolean inStoppedTime = level instanceof ServerLevel serverLevel
                && serverLevel.getData(
                        ModDataAttachmentTypes.TIME_STOP.get())
                        .isTimeStopped(user);
        HeldInput heldInput = super.onKeyPress(
                level,
                user,
                extraClientInput,
                inputMethod,
                clickHoldResolveTime,
                bufferingState);
        if (!level.isClientSide()
                && bufferingState.isActionSuccess
                && heldInput != null
                && power != null
                && !inStoppedTime) {
            // 1.16 Action#playVoiceLine: no line while sneaking.
            sayShout(
                    user,
                    standAlreadySummoned
                            ? ShadowTheWorldAddon.SDDIO_TIME_STOP
                            : ShadowTheWorldAddon.SDDIO_SHADOW_WORLD);
        }
        return heldInput;
    }

    @Override
    public Component getName(Power<?> context) {
        StandPower power = PowerClass.STAND.cast(context);
        float seconds =
                TimeStopLearning.getTimeStopTicks(power) / 20.0F;
        return Component.translatable(
                "rotp_shadowtheworld.ability.time_stop",
                String.format(Locale.ROOT, "%.2f", seconds));
    }
}
