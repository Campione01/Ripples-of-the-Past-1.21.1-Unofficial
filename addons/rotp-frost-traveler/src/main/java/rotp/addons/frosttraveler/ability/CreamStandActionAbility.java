package rotp.addons.frosttraveler.ability;

import javax.annotation.Nullable;

import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.sounds.EntityStoppableSoundInstance;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.frosttraveler.FrostTravelerAddon;
import rotp.addons.frosttraveler.state.CreamStarterService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The nine Cream Starter actions. 1.16 registered every one as a StandEntityAction (InitStands.java:50-58): FULL
 * auto-summon, the IDLE pose and a Stand task timed by the hold. give_cs and cs_switch performed for one tick;
 * cs_heal (holdType(60)), cs_item_fill and cs_entity_fill (holdType()) performed on every tick the key was held;
 * cs_deform, cs_def, cs_dsg_item and cs_item charged (holdToFire(15 / 10 / 15 / 15, false)) and then performed once.
 * Only Deform's task held a target (BLOCK), and cs_def's and cs_item's (ENTITY).
 */
public final class CreamStandActionAbility extends NoPoseStandEntityAbility {
    public static final float GIVE_STAMINA = 1.0F;
    public static final int HEAL_COOLDOWN = 20;
    public static final int SWITCH_COOLDOWN = 10;

    private final Operation operation;

    public CreamStandActionAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Operation operation) {
        super(abilityType, abilityId, CreamStandAction::new);
        this.operation = operation;
        switch (operation) {
            case HEAL -> {
                cooldown(HEAL_COOLDOWN);
                resolveLevelToUnlock(3);
            }
            case SWITCH -> {
                cooldown(SWITCH_COOLDOWN);
                resolveLevelToUnlock(2);
            }
            case ENTITY_FILL -> resolveLevelToUnlock(1);
            case DEFORM -> resolveLevelToUnlock(3);
            case DEFENSE -> resolveLevelToUnlock(3);
            case DISGUISE_ITEM -> resolveLevelToUnlock(2);
            case ITEM_INJECTION -> resolveLevelToUnlock(4);
            default -> {
            }
        }
        if (operation.holdType()) {
            setButtonHoldPhase(ActionPhase.PERFORM);
            if (operation == Operation.HEAL) {
                setDefaultPhaseLength(
                        ActionPhase.PERFORM,
                        CreamOperations.SELF_HEAL_MAX_TICKS);
            }
        }
        else {
            setDefaultPhaseLength(
                    ActionPhase.BUTTON_CHARGE, chargeTicks(operation));
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        }
    }

    public static int chargeTicks(Operation operation) {
        return switch (operation) {
            case DEFENSE -> 10;
            case DEFORM, DISGUISE_ITEM, ITEM_INJECTION -> 15;
            default -> 0;
        };
    }

    public Operation operation() {
        return operation;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        if (!(context.getUser() instanceof Player player)) {
            return ConditionCheck.NEGATIVE;
        }
        if (CreamOperations.canBegin(player, operation)) {
            return ConditionCheck.POSITIVE;
        }
        // 1.16 checked these after StandEntityAction.onClick had summoned
        // the Stand, so they warn with the Stand away too.
        return switch (operation) {
            // Healing: no Cream Starter with cream in either hand
            case HEAL -> ConditionCheck.createNegative("no_cream");
            // ChangeMode: no Cream Starter in either hand
            case SWITCH -> CreamStarterService.heldOwnedSprayer(player).isEmpty()
                    ? ConditionCheck.createNegative("no_cream")
                    : ConditionCheck.NEGATIVE;
            // DeformAction.checkTarget, asked only for a block target
            case DEFORM -> CreamOperations.aimedBlock(player) != null
                    && CreamStarterService.heldOwnedSprayer(player).isEmpty()
                    ? ConditionCheck.createNegative("no_cream")
                    : ConditionCheck.NEGATIVE;
            case DISGUISE_ITEM -> disguiseItemCheck(player);
            default -> ConditionCheck.NEGATIVE;
        };
    }

    // 1.16 DisguiseItemAction.checkHeldItems, in its order.
    private static ConditionCheck disguiseItemCheck(Player player) {
        if (!CreamStarterService.isOwnedSprayer(player, player.getMainHandItem())) {
            return ConditionCheck.createNegative("no_cream.main");
        }
        if (!player.getOffhandItem().isEmpty()) {
            return ConditionCheck.createNegative("hand");
        }
        if (CreamStarterService.state(player).ammo() < CreamOperations.DISGUISE_ITEM_AMMO) {
            return ConditionCheck.createNegative("");
        }
        return ConditionCheck.NEGATIVE;
    }

    // 1.16 tickHeldAction checked the press conditions again on every held tick.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return checkSpecificConditions(context);
    }

    // 1.16 holdOnly: cs_heal's cooldown starts when its hold stops.
    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return operation != Operation.HEAL;
    }

    // 1.16 GiveCreamStarter.enabledInHudDefault false: off the default
    // hotbar, but the player may add it; the entity fill is item fill's
    // Shift variation.
    @Override
    public boolean addToControlSchemeEditing() {
        return operation != Operation.ENTITY_FILL;
    }

    // 1.16 FillMeatItems.replaceAction: with no meat in the inventory the
    // item fill is the entity fill.
    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        if (operation != Operation.ITEM_FILL
                || abilities == null
                || !(context.getUser() instanceof Player player)
                || CreamOperations.hasMeatSource(player)) {
            return this;
        }
        Ability variation = abilities.getContextVariation(
                FrostTravelerAddon.CS_ENTITY_FILL_NAME);
        return variation != null ? variation : this;
    }

    // 1.16 StandEntityTask.keepTarget: DeformAction.checkTarget (a Cream
    // Starter in hand, a block it can pass that way, free space past it);
    // cs_def and cs_item kept a living entity the Stand may attack; the
    // other actions had no target requirement and kept none.
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return switch (operation) {
            case DEFORM -> target.getType() == ActionTarget.TargetType.BLOCK
                    && standEntity.getUser() instanceof Player player
                    && !CreamStarterService.heldOwnedSprayer(player).isEmpty()
                    && CreamOperations.deformDestination(
                            player, target.getBlockPos()) != null;
            case DEFENSE, ITEM_INJECTION ->
                    target.getType() == ActionTarget.TargetType.ENTITY;
            default -> false;
        };
    }

    /**
     * 1.16 GameplayHandler.onPlayerHurt: a hit over 10 stopped a held cs_heal with stopHeldAction(false), which also
     * started its cooldown.
     */
    public static boolean stopHeldHealOnHit(Player player) {
        StandEntity stand = FrostTravelerAddon.controller(player);
        EntityActionInstance action = stand != null
                ? stand.getCurStandAction()
                : null;
        if (action == null
                || action.isOver()
                || !(action.ability instanceof CreamStandActionAbility heal)
                || heal.operation != Operation.HEAL
                || !heal.isActionHeld(action)) {
            return false;
        }
        heal.stopHeldActionOnGettingAttacked(action);
        return true;
    }

    public static final class CreamStandAction
            extends EntityActionInstance {
        public CreamStandAction(EntityActionType ability) {
            super(ability);
        }

        private Operation operation() {
            return ((CreamStandActionAbility) ability).operation;
        }

        // 1.16 cs_heal (holdType(60)), cs_item_fill and cs_entity_fill
        // (holdType()) had no recovery: another Stand action pressed during
        // them replaced them at once (isCancelable), and any other action of
        // the power ended the hold (stopHeldAction(false)); the hold-to-fire
        // ones let none through while they charged
        @Override
        public boolean isHoldTypeStandAction() {
            return operation().holdType();
        }

        @Override
        protected ActionTarget pressedTaskTarget() {
            if (!(getPowerUser() instanceof Player player)) {
                return ActionTarget.EMPTY;
            }
            LivingEntity target;
            switch (operation()) {
                case DEFORM -> {
                    BlockHitResult hit = CreamOperations.deformSource(player);
                    return hit != null
                            ? new ActionTarget(hit.getBlockPos(), hit.getDirection())
                            : ActionTarget.EMPTY;
                }
                case DEFENSE -> target = CreamOperations.impairmentTarget(player);
                case ITEM_INJECTION -> target = CreamOperations.injectionTarget(player);
                default -> target = null;
            }
            return target != null
                    ? new ActionTarget(target)
                    : ActionTarget.EMPTY;
        }

        @Override
        public void actionTick() {
            if (level().isClientSide()
                    || !(getPowerUser() instanceof Player player)) {
                return;
            }
            // 1.16 DeformAction.onHoldTick sprayed on every held tick before
            // the one it went off on.
            if (operation() == Operation.DEFORM
                    && getPhase() == ActionPhase.BUTTON_CHARGE
                    && getPhaseTick() + 1 < getCurPhaseLength()) {
                player.level().playSound(
                        null,
                        player.blockPosition(),
                        FrostTravelerAddon.CS_SHOT.get(),
                        player.getSoundSource(),
                        1.0F,
                        1.0F);
            }
            // 1.16 standTickPerform: the held actions work on every tick of
            // their task's perform.
            if (getPhase() != ActionPhase.PERFORM) {
                return;
            }
            switch (operation()) {
                case HEAL -> {
                    if (performer instanceof StandEntity stand) {
                        CreamOperations.selfHealTick(
                                player, stand, StandPower.get(player), ability);
                    }
                }
                case ITEM_FILL -> CreamOperations.fillFromItem(player);
                case ENTITY_FILL -> CreamOperations.fillFromEntity(player);
                default -> {
                }
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser() instanceof Player player)) {
                return;
            }
            ActionTarget target = getTaskTarget(level());
            switch (operation()) {
                case GIVE -> {
                    // 1.16 GameplayHandler gave the Cream Starter on the
                    // first player tick the Stand was out.
                    CreamStarterService.reconcile(player);
                    StandAbilityStamina.consume(
                            ability,
                            StandPower.get(player),
                            GIVE_STAMINA,
                            false);
                }
                case SWITCH -> {
                    CreamOperations.switchMode(player);
                    if (performer instanceof StandEntity stand) {
                        StandUtil.playStandEntitySound(
                                stand,
                                FrostTravelerAddon.CS_SHAKE.get(),
                                1.0F,
                                1.0F);
                    }
                }
                case DEFORM -> {
                    if (target.getType()
                            == ActionTarget.TargetType.BLOCK) {
                        CreamOperations.deform(
                                player, target.getBlockPos());
                    }
                }
                // 1.16 DeformTargetAction.standPerform aimed again along
                // the user's look, not at its task's target.
                case DEFENSE -> CreamOperations.impairTarget(player);
                case DISGUISE_ITEM -> CreamOperations.createDisguiseItem(player);
                case ITEM_INJECTION -> {
                    CreamOperations.injectOffhandItem(
                            player,
                            target.getEntity() instanceof LivingEntity living
                                    ? living
                                    : null);
                    // 1.16 StandEntityTask paid staminaCost(100) after the
                    // perform whatever it did; a short bar drops to 0.
                    StandAbilityStamina.consume(
                            ability,
                            StandPower.get(player),
                            CreamOperations.ITEM_INJECTION_STAMINA,
                            false);
                }
                default -> {
                }
            }
        }

        // 1.16 standSound(CS_REFILL) of a held task: played at the Stand for
        // those who hear Stands, and cut off when the task left its perform.
        @Override
        public void onSetPhase(ActionPhase newPhase) {
            if (newPhase == ActionPhase.PERFORM
                    && (operation() == Operation.ITEM_FILL
                            || operation() == Operation.ENTITY_FILL)
                    && level().isClientSide()
                    && performer instanceof StandEntity stand
                    && ClientGlobals.canHearStand(stand)) {
                ClientsideSoundsHelper.playNonVanillaClassSound(
                        new EntityStoppableSoundInstance(
                                ClientsideSoundsHelper.withStandSkin(
                                        FrostTravelerAddon.CS_ABS.get(), stand),
                                stand.getSoundSource(),
                                1.0F,
                                1.0F,
                                stand,
                                id,
                                () -> isOver()
                                        || getPhase() != ActionPhase.PERFORM));
            }
        }

        // 1.16 holdToFire(n, false): letting go before the charge completes
        // stops the task unfired; a holdType task goes to its recovery.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
            else if (getPhase() == ActionPhase.PERFORM
                    && operation().holdType()) {
                startRecovery();
            }
        }

        @Override
        public void onActionCleared(
                @Nullable EntityActionInstance newAction) {
            if (level().isClientSide()
                    || !(getPowerUser() instanceof Player player)) {
                return;
            }
            if (operation() == Operation.ITEM_INJECTION) {
                CreamOperations.releaseMainHandSprayer(player);
            }
            else if (operation() == Operation.HEAL) {
                StandPower power = StandPower.get(player);
                if (power != null) {
                    ((CreamStandActionAbility) ability).setCooldownOnUse(power);
                }
            }
        }
    }

    public enum Operation {
        GIVE,
        HEAL,
        SWITCH,
        ITEM_FILL,
        ENTITY_FILL,
        DEFORM,
        DEFENSE,
        DISGUISE_ITEM,
        ITEM_INJECTION;

        /** 1.16 holdType: performs on every tick the key is held. */
        public boolean holdType() {
            return this == HEAL || this == ITEM_FILL || this == ENTITY_FILL;
        }
    }
}
