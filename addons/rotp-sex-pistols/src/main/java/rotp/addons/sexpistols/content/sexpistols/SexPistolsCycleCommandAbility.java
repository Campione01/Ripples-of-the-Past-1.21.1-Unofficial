package rotp.addons.sexpistols.content.sexpistols;

import java.util.Locale;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class SexPistolsCycleCommandAbility
        extends Ability {
    public static final int COMMAND_COOLDOWN_TICKS = 4;
    public static final int TARGET_MODE_RESOLVE_LEVEL = 2;

    private final Command command;

    public SexPistolsCycleCommandAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Command command) {
        super(abilityType, abilityId);
        this.command = command;
        cooldown(COMMAND_COOLDOWN_TICKS);
        // 1.16 sex_pistols_target_mode: resolveLevelToUnlock(2)
        if (command == Command.TARGET_MODE) {
            resolveLevelToUnlock(TARGET_MODE_RESOLVE_LEVEL);
        }
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    // 1.16 picked the member with the mouse wheel
    // (SwitchSexPistolsSelectionPacket), no action, so it neither
    // ended a held action nor was refused by one.
    @Override
    public boolean endsOtherHoldOnPress() {
        return command != Command.SELECT;
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
        if (!(context instanceof StandPower standPower)
                || standPower.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()) {
            return ConditionCheck.NEGATIVE;
        }
        LivingEntity owner = context.getUser();
        if (owner == null || !standPower.isSummoned()) {
            return ConditionCheck.createNegative(
                    "sex_pistols_not_summoned");
        }
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        if (!coordinator.isSummoned()) {
            return ConditionCheck.createNegative(
                    "sex_pistols_not_summoned");
        }
        if (command == Command.SELECT
                && coordinator.trackedMemberCount() <= 0) {
            return ConditionCheck.createNegative(
                    "sex_pistols_member_unavailable");
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public Component getName(Power<?> context) {
        LivingEntity owner =
                context != null ? context.getUser() : null;
        if (owner == null) {
            return super.getName(context);
        }
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        return switch (command) {
            case SELECT -> Component.translatable(
                    getTranslationKey(),
                    coordinator.selectedNumber());
            case TARGET_MODE -> Component.translatable(
                    getTranslationKey(),
                    translatedTargetMode(
                            coordinator.targetMode()));
            case TRANSFER_ORDER -> Component.translatable(
                    getTranslationKey(),
                    translatedTransferOrder(
                            coordinator.transferOrder()));
        };
    }

    @Override
    public String getSpriteName(Power<?> context) {
        LivingEntity owner =
                context != null ? context.getUser() : null;
        SexPistolsCoordinator coordinator =
                owner != null
                        ? SexPistolsAttachments.coordinator(owner)
                        : null;
        return switch (command) {
            case SELECT -> "sex_pistols_no_"
                    + (coordinator != null
                            ? coordinator.selectedNumber()
                            : 1);
            case TARGET_MODE -> "sex_pistols_target_"
                    + (coordinator != null
                            ? coordinator.targetMode()
                                    .name()
                                    .toLowerCase(Locale.ROOT)
                            : "players");
            case TRANSFER_ORDER ->
                    "sex_pistols_transfer_order";
        };
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
                        != SexPistolsStands.SEX_PISTOLS.get()
                || !standPower.isSummoned()) {
            return;
        }

        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        boolean backwards = owner.isShiftKeyDown();
        boolean changed = switch (command) {
            case SELECT -> coordinator.cycleSelected(
                    serverLevel, owner, backwards);
            case TARGET_MODE -> {
                coordinator.cycleTargetMode(owner, backwards);
                yield true;
            }
            case TRANSFER_ORDER -> {
                coordinator.cycleTransferOrder(
                        owner, backwards);
                yield true;
            }
        };
        if (!changed) {
            ConditionCheck.sendActionFailedMessage(
                    this,
                    ConditionCheck.createNegative(
                            "sex_pistols_member_unavailable"),
                    owner);
            return;
        }
        setCooldownOnUse(standPower);
    }

    private static Component translatedTargetMode(
            SexPistolsTargetMode mode) {
        return Component.translatable(
                "rotp_sex_pistols.sex_pistols.target."
                        + mode.name()
                                .toLowerCase(Locale.ROOT));
    }

    private static Component translatedTransferOrder(
            SexPistolsTransferOrder order) {
        return Component.translatable(
                "rotp_sex_pistols.sex_pistols.transfer."
                        + order.name()
                                .toLowerCase(Locale.ROOT));
    }

    public enum Command {
        SELECT,
        TARGET_MODE,
        TRANSFER_ORDER
    }
}
