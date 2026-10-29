package rotp.addons.scarymonsters.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.ScaryMonstersService;
import net.minecraft.server.level.ServerPlayer;

public final class ScaryMonstersAbility extends StandEntityAbility {
    public enum Mode {
        // The punch, barrage, heavy punch, finisher, tail sweep and slash are
        // ScaryMonstersPunchAbility, ScaryMonstersBarrageAbility and
        // ScaryMonstersHeavyAbility, on the core punches as in 1.16.
        // 1.16 InitStands :77-78, :80-87: only dynamic vision had a cooldown
        // (40); dynamic vision and dragon had no standRecoveryTicks, so once
        // performed they left the Stand free
        DYNAMIC_VISION(0, 1, 0, 40, false, 0.0F, 2),
        DYNAMIC_VISION_CLOSE(0, 5, 5, 0, false, 0.0F, 0),
        DINOSAURIFY(5, 1, 20, 0, false, 37.5F, 0),
        DINOSAURIFY_REVERT(5, 1, 20, 0, false, 15.0F, 0),
        DRAGON(80, 1, 0, 0, false, 75.0F, 2),
        DRAGON_CLOSE(0, 5, 5, 0, false, 0.0F, 0),
        BLOCK_TO_DINOSAUR(5, 1, 20, 0, false, 37.5F, 0),
        REVERT_BLOCK_TO_DINOSAUR(5, 1, 20, 0, false, 15.0F, 0),
        DINOSAUR_FORM(5, 1, 20, 0, false, 75.0F, 1),
        DINOSAUR_FORM_CLOSE(5, 1, 20, 0, false, 15.0F, 0);

        final int windup, perform, recovery, cooldown, resolve;
        final boolean held;
        final float stamina;
        Mode(int windup, int perform, int recovery, int cooldown,
                boolean held, float stamina, int resolve) {
            this.windup = windup;
            this.perform = perform;
            this.recovery = recovery;
            this.cooldown = cooldown;
            this.held = held;
            this.stamina = stamina;
            this.resolve = resolve;
        }

        // 1.16 holdToFire(80, false) for dragon and holdToFire(5, false)
        // for the six dinosaur actions.
        boolean holdToFire() {
            return switch (this) {
                case DINOSAURIFY, DINOSAURIFY_REVERT, DRAGON,
                        BLOCK_TO_DINOSAUR, REVERT_BLOCK_TO_DINOSAUR,
                        DINOSAUR_FORM, DINOSAUR_FORM_CLOSE -> true;
                default -> false;
            };
        }
    }

    private final Mode mode;

    public Mode mode() {
        return mode;
    }

    public ScaryMonstersAbility(
            AbilityType<?> type, AbilityId id, Mode mode) {
        super(type, id, action -> new ScaryAction(action, mode));
        this.mode = mode;
        usageGroup = switch (mode) {
            case DYNAMIC_VISION, DYNAMIC_VISION_CLOSE,
                    DRAGON, DRAGON_CLOSE, DINOSAUR_FORM,
                    DINOSAUR_FORM_CLOSE, BLOCK_TO_DINOSAUR,
                    REVERT_BLOCK_TO_DINOSAUR -> AbilityUsageGroup.UTILITY;
            default -> AbilityUsageGroup.COMBAT;
        };
        standAutoSummonMode(AutoSummonMode.FULL);
        // 1.16 InitStands :77, :82: dynamic vision and dragon had no
        // partsRequired
        if (mode != Mode.DRAGON && mode != Mode.DYNAMIC_VISION) partsRequired(StandPart.ARMS);
        setDefaultPhaseLength(mode.holdToFire()
                ? ActionPhase.BUTTON_CHARGE : ActionPhase.WINDUP, mode.windup);
        setDefaultPhaseLength(ActionPhase.PERFORM, mode.perform);
        setDefaultPhaseLength(ActionPhase.RECOVERY, mode.recovery);
        if (mode.held) setButtonHoldPhase(ActionPhase.PERFORM);
        if (mode.cooldown > 0) cooldown(mode.cooldown);
        if (mode.resolve > 0) resolveLevelToUnlock(mode.resolve);
    }

    // A held action's cooldown starts when the hold fires, so a
    // cancelled hold leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return !mode.holdToFire()
                && super.shouldSetCooldownOnKeyPress(inputMethod);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()
                || !(context instanceof StandPower power)
                || !ScaryMonstersAddon.isUser(power.getUser())) {
            return base.isPositive() ? ConditionCheck.NEGATIVE : base;
        }
        // 1.16 had no stamina gate: short stamina still fires and drops to 0.
        return base;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return ActionAnimIdentifier.getOrCreate("idle", false);
    }

    private static final class ScaryAction extends EntityActionInstance {
        private final Mode mode;
        private ScaryAction(EntityActionType type, Mode mode) {
            super(type);
            this.mode = mode;
        }

        @Override
        public void actionPerformStart() {
            if (!(getPowerUser() instanceof ServerPlayer player)) return;
            StandPower power = StandPower.get(player);
            if (power == null) {
                startRecovery();
                return;
            }
            // 1.16 paid the cost unchecked; short stamina goes to 0.
            power.consumeStamina(mode.stamina);
            if (mode.holdToFire() && ability instanceof ScaryMonstersAbility held) {
                held.setCooldownOnUse(power);
            }
            switch (mode) {
                case DYNAMIC_VISION -> ScaryMonstersService.enableDynamicVision(player);
                case DYNAMIC_VISION_CLOSE -> ScaryMonstersService.disableDynamicVision(player);
                case DRAGON -> ScaryMonstersService.enableDragonForm(player);
                case DRAGON_CLOSE -> ScaryMonstersService.disableDragonForm(player);
                case DINOSAUR_FORM -> ScaryMonstersService.enableDinosaurForm(player);
                case DINOSAUR_FORM_CLOSE -> ScaryMonstersService.disableDinosaurForm(player, true);
                case DINOSAURIFY -> ScaryMonstersService.dinosaurifyNearest(player);
                case DINOSAURIFY_REVERT -> ScaryMonstersService.revertLookedAtDinosaur(player);
                case BLOCK_TO_DINOSAUR -> ScaryMonstersService.dinosaursToBlocks(player);
                case REVERT_BLOCK_TO_DINOSAUR -> ScaryMonstersService.blocksToDinosaurs(player);
            }
        }

        // 1.16 holdToFire(n, false): letting go before the charge
        // completes cancels the action.
        @Override
        public void onButtonStopHold() {
            if (mode.holdToFire() && getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
                return;
            }
            if (mode.held) startRecovery();
        }
    }
}
