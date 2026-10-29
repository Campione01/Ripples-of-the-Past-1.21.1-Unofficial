package rotp.addons.harvest.ability;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.harvest.entity.HarvestStandEntity;
import rotp.addons.harvest.stand.HarvestStandData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;

public final class HarvestCommandAbility extends StandEntityAbility {
    public static final int CARRY_UP_CHARGE_TICKS = 2;
    public static final int SEARCH_CHARGE_TICKS = 25;
    public static final int POTION_TRANSFER_CHARGE_TICKS = 25;
    // 1.16 every Harvest command was a StandAction: no Stand task, so nothing
    // held the next press back once it fired.
    public static final int RECOVERY_TICKS = 0;

    private final Command command;

    public HarvestCommandAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Command command) {
        super(abilityType, abilityId, HarvestCommandAction::new);
        this.command = command;
        partsRequired(StandPart.MAIN_BODY);
        if (command == Command.GIVE_POTION
                || command == Command.REMOVE_POTION) {
            standAutoSummonMode(AutoSummonMode.DISABLED);
        }
        if (requiresCharge(command)) {
            setDefaultPhaseLength(
                    ActionPhase.BUTTON_CHARGE,
                    chargeTicks(command));
        }
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(
                ActionPhase.RECOVERY, RECOVERY_TICKS);
    }

    public Command command() {
        return command;
    }

    // 1.16 InitStands :45-54: every command a StandAction, held and performed
    // by its user's power (the summoned Harvest while out), never a task of
    // the Stand
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    @Override
    public boolean retractsStandAfterAction(
            StandPower power, StandEntity stand,
            EntityActionInstance action) {
        // Commands move the swarm, not its zero-height coordinator.
        return false;
    }

    // 1.16 InitStands :47-54: the held commands were StandActions the user's
    // power performed in the tick the hold fired (the 25th or 2nd from the
    // press), not a Stand task that moved on a tick later.
    @Override
    public boolean chargeFiresAsItsLengthEnds(EntityActionInstance action) {
        return true;
    }

    /**
     * Both halves of this were measured wrong in game before they were fixed, and both are the shapes round 61
     * learned the hard way.
     *
     * <p><b>Components cannot be format arguments here.</b> This module keeps its ability names in the DEFAULT
     * SKIN overlay, so the core answers through {@code StandSkin.translatable(key, args)}, which formats with
     * {@code String.format} - and {@code String.format("%s", component)} prints the Component's
     * {@code toString()}. With a cow under the crosshair the name read
     * "Stay with translation{key='entity.minecraft.cow', args=[]}". Resolve to a String first.
     *
     * <p><b>A key that carries a placeholder must never be rendered without one.</b> {@code stay_with}'s base
     * string is "Stay with %s", and every path that found no target fell through to {@code super.getName},
     * which supplies no arguments - and nothing throws, so it simply printed "Stay with %s". Its two siblings
     * already had the right shape: a placeholder-free base key plus a {@code .additional} variant that takes
     * the argument. {@code stay_with} now follows them. (1.16 never hit this: {@code StayWith} dereferenced its
     * ray-trace result without a null check, so it either had a target or crashed.)
     */
    @Override
    public Component getName(Power<?> context) {
        // 1.16 BeCloser.getTranslatedName :261-267 read the user's flag,
        // Harvest out or not
        if (command == Command.BE_CLOSER && isCloser(context)) {
            return abilityName(context, ".not");
        }
        HarvestStandEntity coordinator = coordinator(context);
        LivingEntity owner =
                context != null ? context.getUser() : null;
        if (coordinator == null || owner == null) {
            return super.getName(context);
        }
        if (command == Command.SET_TARGET) {
            LivingEntity target = coordinator.previewLookTarget(
                    owner, true);
            if (target != null) {
                return abilityName(
                        context,
                        ".additional",
                        target.getName().getString());
            }
        }
        if (command == Command.STAY_WITH) {
            LivingEntity target = coordinator.previewLookTarget(
                    owner, false);
            if (target != null) {
                return abilityName(
                        context,
                        ".additional",
                        target.getName().getString());
            }
        }
        if (command == Command.GO_TO) {
            BlockHitResult hit =
                    coordinator.previewBlockLookTarget(owner);
            if (hit != null) {
                return abilityName(
                        context,
                        ".additional",
                        owner.level()
                                .getBlockState(hit.getBlockPos())
                                .getBlock()
                                .getName()
                                .getString());
            }
        }
        return super.getName(context);
    }

    // 1.16 BeCloser.getIconTexture :273-280: the user's flag while the user
    // has the Stand, summoned or not
    @Override
    public String getSpriteName(Power<?> context) {
        return command == Command.BE_CLOSER && isCloser(context)
                ? super.getSpriteName(context) + ".not"
                : super.getSpriteName(context);
    }

    public static boolean isCloser(@Nullable Power<?> context) {
        HarvestStandData data = HarvestStandData.of(context);
        return data != null && data.isCloser();
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base =
                super.checkSpecificConditions(context);
        HarvestStandEntity coordinator = coordinator(context);
        LivingEntity owner =
                context != null ? context.getUser() : null;
        if (!base.isPositive()
                || coordinator == null
                || owner == null) {
            return base;
        }
        return coordinator.canExecuteCommand(command, owner)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    /**
     * 1.16 PowerBaseImpl.tickHeldAction ran GiveAPotion's and RemoveAPotion's target check (ENTITY requirement, an own
     * Harvest under the crosshair) and conditions on every held tick; neither is holdOnly, so a failed check ended the
     * charge silently and nothing fired. Search and Carry Up have no target and only checked the user.
     */
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()
                || (command != Command.GIVE_POTION
                        && command != Command.REMOVE_POTION)) {
            return base;
        }
        HarvestStandEntity coordinator = coordinator(context);
        LivingEntity owner =
                context != null ? context.getUser() : null;
        return coordinator != null
                        && owner != null
                        && coordinator.canExecuteCommand(command, owner)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    @Override
    protected ConditionCheck checkStandEntityConditions(
            StandPower standPower, StandEntity standEntity) {
        LivingEntity owner = standPower != null
                ? standPower.getUser()
                : null;
        return standEntity instanceof HarvestStandEntity coordinator
                        && owner != null
                        && coordinator.canExecuteCommand(
                                command, owner)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    @Nullable
    private static HarvestStandEntity coordinator(
            @Nullable Power<?> context) {
        if (!(context instanceof StandPower standPower)) {
            return null;
        }
        Entity stand = standPower.getSummonedStandEntity();
        return stand instanceof HarvestStandEntity coordinator
                ? coordinator
                : null;
    }

    public enum Command {
        SET_TARGET,
        FORGET_TARGET,
        GO_TO,
        STAY_WITH,
        SEARCH,
        BE_CLOSER,
        GIVE_POTION,
        REMOVE_POTION,
        CARRY_UP,
        CONTROL_HARVEST
    }

    public static final class HarvestCommandAction
            extends EntityActionInstance {
        public HarvestCommandAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 InitStands :45-54: every command a StandAction its user's
        // power performed, which a new Stand's summon lock never held back
        @Override
        public boolean isHeldBackBySummonLock() {
            return false;
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(ability
                            instanceof HarvestCommandAbility commandAbility)) {
                return;
            }
            if (!(getPerformer()
                    instanceof HarvestStandEntity coordinator)) {
                // 1.16 Search.perform :47-56 needed no Harvest: it left the
                // swarm status for the next summon.
                HarvestStandData data =
                        HarvestStandData.of(getPowerUser());
                if (commandAbility.command() == Command.SEARCH
                        && data != null) {
                    data.markSearchLeftStandless();
                }
                return;
            }
            LivingEntity owner = getPowerUser();
            if (owner != null
                    && coordinator.isActiveFor(owner)) {
                coordinator.executeCommand(
                        commandAbility.command(), owner);
            }
        }

        @Override
        public void onButtonStopHold() {
            if (ability instanceof HarvestCommandAbility commandAbility
                    && requiresCharge(commandAbility.command())
                    && getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
        }
    }

    private static boolean requiresCharge(Command command) {
        return command == Command.CARRY_UP
                || command == Command.SEARCH
                || command == Command.GIVE_POTION
                || command == Command.REMOVE_POTION;
    }

    private static int chargeTicks(Command command) {
        return switch (command) {
            case CARRY_UP -> CARRY_UP_CHARGE_TICKS;
            case SEARCH -> SEARCH_CHARGE_TICKS;
            case GIVE_POTION, REMOVE_POTION ->
                    POTION_TRANSFER_CHARGE_TICKS;
            default -> throw new IllegalArgumentException(
                    "Command does not charge: " + command);
        };
    }
}
