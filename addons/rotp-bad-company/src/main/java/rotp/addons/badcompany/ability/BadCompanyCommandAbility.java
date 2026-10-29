package rotp.addons.badcompany.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;

import javax.annotation.Nullable;

import rotp.addons.badcompany.entity.BadCompanyStandEntity;
import rotp.addons.badcompany.entity.BadCompanyUnitType;
import rotp.addons.badcompany.network.BadCompanySelectionPayloads;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class BadCompanyCommandAbility
        extends StandEntityAbility {
    private final Command command;

    public BadCompanyCommandAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Command command) {
        super(abilityType, abilityId, CommandAction::new);
        this.command = command;
        partsRequired(StandPart.MAIN_BODY);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 055 InitStands:54-67: the Stand's commands set no
        // standRecoveryTicks, the others were StandActions with no Stand task
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    public Command command() {
        return command;
    }

    /**
     * 1.16 055 InitStands:54-67: units, shoot, random, order_wait, target, missile, close, closent and summon were
     * StandActions: the press performed them at once (PowerBaseImpl.performAction) and started no Stand task, so the
     * Stand's own task went on and a Stand press in the same tick was not held back. With the coordinator out they
     * are performed here; an unsummoned Stand is still summoned by a 1-tick task, as the port keeps the selection
     * and the units on the coordinator.
     */
    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
            InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
        if (!level.isClientSide() && !command.standTask()
                && StandPower.get(user) instanceof StandPower power
                && power.getSummonedStandEntity() instanceof BadCompanyStandEntity coordinator
                && coordinator.isActiveFor(user)) {
            perform(command, coordinator, user, aimedTarget(coordinator, user));
            bufferingState.isActionSuccess = true;
            return null;
        }
        return super.onKeyPress(level, user, extraClientInput,
                inputMethod, clickHoldResolveTime, bufferingState);
    }

    /*
     * 1.16 queued only a StandEntityAction behind the Stand's task (StandEntityAction.checkTaskCancelling) and drew a
     * StandAction by its requirements, so a command onKeyPress performs at once is not drawn queued during a
     * Stand-task command or the summoning task.
     */
    @Override
    public boolean isQueuedBehindStandTask(LivingEntity user) {
        return !performedAtPress(user) && super.isQueuedBehindStandTask(user);
    }

    // the condition of onKeyPress's direct branch
    private boolean performedAtPress(LivingEntity user) {
        return !command.standTask()
                && StandPower.get(user) instanceof StandPower power
                && power.getSummonedStandEntity() instanceof BadCompanyStandEntity coordinator
                && coordinator.isActiveFor(user);
    }

    private static void perform(Command command, BadCompanyStandEntity coordinator,
            LivingEntity owner, @Nullable ActionTarget target) {
        if ((command == Command.SELECT_UNITS
                        || command == Command.SELECT_FORMATION)
                && owner instanceof ServerPlayer player) {
            BadCompanySelectionPayloads.open(
                    player,
                    coordinator,
                    command);
            return;
        }
        coordinator.executeCommand(command, owner, target);
    }

    @Override
    protected int getRequiredResolveLevelToUnlock(
            Power<?> context) {
        return command.requiredResolve();
    }

    // 1.16 GoToBlockAction: TargetRequirement.BLOCK; SetTarget was a StandAction, with no Stand task
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return command == Command.GO_TO_BLOCK
                && target.getType() == ActionTarget.TargetType.BLOCK;
    }

    // 1.16 overrideVanillaMouseTarget let a pressed torch or grass give way
    // only for a StandEntityAction that takes an entity: GoToBlockAction
    // (TargetRequirement.BLOCK) kept the block, and the commands 1.16 made
    // StandActions had no such rule
    @Override
    public boolean pressedBlockGivesWayToEntity() {
        return command.standTask() && command != Command.GO_TO_BLOCK
                && super.pressedBlockGivesWayToEntity();
    }

    /**
     * 1.16 SetTarget had TargetRequirement.ENTITY and answered "target_too_far" past 50 blocks from the
     * Stand (25 without line of sight); GoToBlockAction had TargetRequirement.BLOCK, so air did not count.
     * The target is read from the same aim CommandAction.onActionSet captures.
     */
    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()
                || !command.capturesTarget()) {
            return check;
        }
        LivingEntity owner = context.getUser();
        if (owner == null) {
            return ConditionCheck.NEGATIVE;
        }
        BadCompanyStandEntity coordinator = coordinator(context);
        ActionTarget target =
                aimedTarget(coordinator, owner);
        return switch (command) {
            case SET_TARGET -> checkSetTarget(
                    coordinator, owner, target);
            case GO_TO_BLOCK ->
                    checkGoToBlock(owner, target);
            default -> check;
        };
    }

    // 1.16 SetTarget needs a summoned Stand
    private static ConditionCheck checkSetTarget(
            @Nullable BadCompanyStandEntity coordinator,
            LivingEntity owner,
            ActionTarget target) {
        if (coordinator == null
                || target.getType()
                        != ActionTarget.TargetType.ENTITY
                || !(target.getEntity()
                        instanceof LivingEntity living)) {
            return ConditionCheck.NEGATIVE;
        }
        if (BadCompanyStandEntity.isSetTargetTooFar(
                coordinator, living)) {
            return ConditionCheck.createNegative(
                    "target_too_far");
        }
        return BadCompanyStandEntity.canBeSetAsTarget(
                        owner, living)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    private static ConditionCheck checkGoToBlock(
            LivingEntity owner, ActionTarget target) {
        if (target.getType()
                        != ActionTarget.TargetType.BLOCK
                || target.isEmpty(owner.level())) {
            return ConditionCheck.NEGATIVE;
        }
        return BadCompanyStandEntity.isDestinationTooFar(
                        owner, target.getBlockPos())
                ? ConditionCheck.createNegative(
                        "target_too_far")
                : ConditionCheck.POSITIVE;
    }

    /** The Stand's aim, else the user's (a press that auto-summons the Stand). */
    private static ActionTarget aimedTarget(
            @Nullable LivingEntity stand,
            LivingEntity owner) {
        ActionTarget target = aimOf(stand);
        return target.getType()
                        != ActionTarget.TargetType.EMPTY
                ? target
                : aimOf(owner);
    }

    // The user's own client (HUD, its press check) holds no aim of its
    // user, only the Stand's comes back; there it reads the aim it sends,
    // as 1.16's client checks read the mouse target
    private static ActionTarget aimOf(@Nullable LivingEntity entity) {
        return entity != null
                ? LivingComponentAction.getAimTarget(entity)
                : ActionTarget.EMPTY;
    }

    /**
     * 1.16 named these commands after what the next press will do to the currently selected unit type.
     * {@code OrderToSit}, {@code BreakOrderToSit}, {@code SetTarget}, {@code GoToBlockAction},
     * {@code StayCloseAction}, {@code StayntCloseAction} and {@code DontShootOrder} each overrode
     * {@code getTranslatedName} to pass the unit name in; {@code SumonUnitsAction} passed the
     * summon-or-unsummon verb as well; {@code MissilesMode} passed the state the press would switch to.
     * The port kept none of those overrides and rewrote the English and Chinese into argument-free
     * sentences, which also stranded the five donor-byte locale files (es_es / es_mx / es_ve / ru_ru /
     * uk_ua) with translations whose {@code %s} had nothing left to fill it.
     *
     * <p>The arguments go in as resolved Strings rather than Components on purpose: when a stand skin
     * supplies its own translation the core formats it through {@code StandSkin.formatTranslation}, i.e.
     * {@code String.format}, which would print a Component's {@code toString()} instead of its text.
     *
     * <p><b>An unsummoned Stand still has to be given arguments.</b> 1.16 read the selection off the
     * player capability, so it always had one; the port keeps it on the Stand entity, which does not exist
     * until the Stand is summoned. Falling back to {@code super.getName} there renders the template with no
     * arguments at all, and nothing throws - {@code StandSkin.formatTranslation} catches
     * {@code IllegalFormatException} and {@code TranslatableContents.decompose} falls back the same way - so
     * the Stand Skills screen, the control-scheme tooltips and the new-skill notification would print a
     * literal "Order to %s units to stay close". The HUD hides it only because it drops the Stand element
     * while unsummoned, which is why a run that never opens a screen cannot see it. So the fallback uses the
     * values {@code BadCompanyStandEntity.defineSynchedData} will give the entity on the next summon: ALL,
     * every unit summoned, explosive missiles on.
     */
    @Override
    public Component getName(Power<?> context) {
        BadCompanyStandEntity coordinator = coordinator(context);
        BadCompanyUnitType unit = coordinator != null
                ? coordinator.selectedUnitType()
                : BadCompanyUnitType.ALL;
        return switch (command) {
            case CLEAR_TARGET, ORDER_WAIT, ORDER_NO_WAIT,
                    SET_TARGET, GO_TO_BLOCK, STAY_CLOSE,
                    STOP_STAY_CLOSE ->
                    abilityName(context, "", unitName(unit));
            case SUMMON_UNITS -> abilityName(
                    context,
                    "",
                    summonVerb(coordinator, unit, context),
                    unitName(unit));
            case MISSILE_MODE -> abilityName(
                    context,
                    "",
                    translated(explosiveMissiles(coordinator)
                            ? "gui.rotp_zbc.deactivate"
                            : "gui.rotp_zbc.activate"));
            default -> super.getName(context);
        };
    }

    /** EXPLOSIVE_MISSILES is defined true, so that is what an unsummoned Stand is about to have. */
    private static boolean explosiveMissiles(
            @Nullable BadCompanyStandEntity coordinator) {
        return coordinator == null || coordinator.explosiveMissiles();
    }

    @Nullable
    private static BadCompanyStandEntity coordinator(
            Power<?> context) {
        return context instanceof StandPower standPower
                        && standPower.getSummonedStandEntity()
                                instanceof BadCompanyStandEntity
                                        coordinator
                ? coordinator
                : null;
    }

    private static String unitName(BadCompanyUnitType unit) {
        return translated(switch (unit) {
            case SOLDIER -> "entity.rotp_zbc.bad_soldier";
            case TANK -> "entity.rotp_zbc.bad_tank";
            case HELICOPTER -> "entity.rotp_zbc.bad_helicopter";
            case ALL -> "entity.rotp_zbc.all";
        });
    }

    /**
     * ALL has no per-type summon bit, so 1.16 read the user's sneak key for it, exactly as here. With no
     * coordinator the unit is always ALL, so that branch answers the unsummoned case too.
     */
    private static String summonVerb(
            @Nullable BadCompanyStandEntity coordinator,
            BadCompanyUnitType unit,
            Power<?> context) {
        LivingEntity user = context.getUser();
        boolean unsummon = unit == BadCompanyUnitType.ALL || coordinator == null
                ? user != null && user.isShiftKeyDown()
                : coordinator.isUnitSummoned(unit);
        return translated(unsummon
                ? "entity.rotp_zbc.unsu"
                : "entity.rotp_zbc.su");
    }

    private static String translated(String key) {
        return Component.translatable(key).getString();
    }

    // standTask: 1.16 made it a StandEntityAction (order_no_wait, put_mines, explode_mines, go_block), the rest were
    // StandActions
    public enum Command {
        SELECT_UNITS(2, false, false),
        CLEAR_TARGET(3, false, false),
        SELECT_FORMATION(1, false, false),
        ORDER_WAIT(0, false, false),
        ORDER_NO_WAIT(0, false, true),
        PUT_MINES(1, false, true),
        EXPLODE_MINES(1, false, true),
        SET_TARGET(3, true, false),
        MISSILE_MODE(2, false, false),
        GO_TO_BLOCK(4, true, true),
        STAY_CLOSE(1, false, false),
        STOP_STAY_CLOSE(0, false, false),
        SUMMON_UNITS(2, false, false);

        private final int requiredResolve;
        private final boolean capturesTarget;
        private final boolean standTask;

        Command(
                int requiredResolve, boolean capturesTarget, boolean standTask) {
            this.requiredResolve = requiredResolve;
            this.capturesTarget = capturesTarget;
            this.standTask = standTask;
        }

        public boolean standTask() {
            return standTask;
        }

        public int requiredResolve() {
            return requiredResolve;
        }

        public boolean capturesTarget() {
            return capturesTarget;
        }
    }

    public static final class CommandAction
            extends EntityActionInstance {
        public CommandAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            if (!(ability
                    instanceof BadCompanyCommandAbility commandAbility)
                    || !commandAbility.command().capturesTarget()
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            ActionTarget target =
                    captureActionTargetFromAim(stand);
            LivingEntity owner = getPowerUser();
            // auto-summoned Stand has no aim yet: use the user's, as checkSpecificConditions did
            if (target.getType()
                            == ActionTarget.TargetType.EMPTY
                    && owner != null) {
                target = captureActionTargetFromAim(owner);
            }
            keepStandAimedAtTarget(target);
            aimAs = AimingEntity.STAND;
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(ability
                            instanceof BadCompanyCommandAbility
                                    commandAbility)
                    || !(getPerformer()
                            instanceof BadCompanyStandEntity
                                    coordinator)) {
                return;
            }
            LivingEntity owner = getPowerUser();
            if (owner == null
                    || !coordinator.isActiveFor(owner)) {
                return;
            }
            perform(commandAbility.command(), coordinator, owner,
                    getActionTargetSnapshot(level()));
        }
    }
}
