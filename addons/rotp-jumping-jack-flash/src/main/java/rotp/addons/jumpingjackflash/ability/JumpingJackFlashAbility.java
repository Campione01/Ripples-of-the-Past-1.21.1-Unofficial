package rotp.addons.jumpingjackflash.ability;

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
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.jumpingjackflash.JumpingJackFlashAddon;
import rotp.addons.jumpingjackflash.state.JumpingJackFlashService;
import net.minecraft.server.level.ServerPlayer;

public final class JumpingJackFlashAbility extends StandEntityAbility {
    public enum Mode {
        VOLLEY(30, 1, 10, 20, 50.0F),
        GRAVITY_SNOWBALL(20, 10, 5, 20, 100.0F),
        CANCEL_GRAVITY(0, 1, 0, 20, 0.0F),
        DOMAIN_OPEN(20, 20, 10, 300, 400.0F),
        DOMAIN_CLOSE(0, 5, 5, 0, 0.0F);

        final int windup;
        final int perform;
        final int recovery;
        final int cooldown;
        final float stamina;

        Mode(
                int windup,
                int perform,
                int recovery,
                int cooldown,
                float stamina) {
            this.windup = windup;
            this.perform = perform;
            this.recovery = recovery;
            this.cooldown = cooldown;
            this.stamina = stamina;
        }

        // 1.16 throw_pickaxe is holdToFire(30, true); cd_blood_cutter1
        // and domain are holdToFire(20, false).
        boolean holdToFire() {
            return this == VOLLEY
                    || this == GRAVITY_SNOWBALL
                    || this == DOMAIN_OPEN;
        }

        boolean firesOnRelease() {
            return this == VOLLEY;
        }
    }

    private final Mode mode;

    public JumpingJackFlashAbility(
            AbilityType<?> type, AbilityId id, Mode mode) {
        super(
                type,
                id,
                action -> new JumpingJackFlashAction(action, mode));
        this.mode = mode;
        usageGroup = mode == Mode.CANCEL_GRAVITY
                        || mode == Mode.DOMAIN_CLOSE
                ? AbilityUsageGroup.UTILITY
                : AbilityUsageGroup.COMBAT;
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.ARMS);
        setDefaultPhaseLength(
                mode.holdToFire()
                        ? ActionPhase.BUTTON_CHARGE
                        : ActionPhase.WINDUP,
                mode.windup);
        setDefaultPhaseLength(ActionPhase.PERFORM, mode.perform);
        setDefaultPhaseLength(ActionPhase.RECOVERY, mode.recovery);
        if (mode.cooldown > 0) {
            cooldown(mode.cooldown);
        }
        // 1.16 used the full name: cd_blood_cutter1 keeps its own icon
        // and stays keybindable.
        this.spriteName = id.nameInMoveset();
        this.isSubAbility = false;
    }

    // 1.16 set a held action's cooldown when the hold fired, so a
    // cancelled hold leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return !mode.holdToFire()
                && super.shouldSetCooldownOnKeyPress(inputMethod);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()
                || !(context instanceof StandPower power)
                || !JumpingJackFlashAddon.isUser(power.getUser())) {
            return base.isPositive() ? ConditionCheck.NEGATIVE : base;
        }
        if (power.getUser() instanceof ServerPlayer player) {
            boolean domainActive =
                    JumpingJackFlashService.hasDomain(player);
            if (mode == Mode.DOMAIN_OPEN && domainActive
                    || mode == Mode.DOMAIN_CLOSE && !domainActive) {
                return ConditionCheck.NEGATIVE;
            }
        }
        return base;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ActionAnimIdentifier.getOrCreate("fire", false);
    }

    private static final class JumpingJackFlashAction
            extends EntityActionInstance {
        private final Mode mode;

        private JumpingJackFlashAction(
                EntityActionType type, Mode mode) {
            super(type);
            this.mode = mode;
        }

        // A throw_pickaxe charge waits for the key to be let go and
        // fires then; the other two fire when the charge completes.
        // Letting go before the charge completes cancels all three.
        @Override
        protected boolean shouldHoldPhaseAtEnd() {
            return mode.firesOnRelease()
                    && getPhase() == ActionPhase.BUTTON_CHARGE;
        }

        @Override
        public void onButtonStopHold() {
            // 1.16 stopHeldAction(true) checked the requirements once more
            // before the released charge fired.
            if (!mode.holdToFire()
                    || getPhase() != ActionPhase.BUTTON_CHARGE) {
                return;
            }
            if (mode.firesOnRelease()
                    && getPhaseTick() >= getCurPhaseLength()
                    && ability instanceof JumpingJackFlashAbility held
                    && held.canFireReleasedHold(this)) {
                setPhaseStart(ActionPhase.WINDUP);
            }
            else {
                forceStop();
            }
            syncPhaseChanges();
        }

        @Override
        public void actionPerformStart() {
            if (!(getPowerUser() instanceof ServerPlayer player)) {
                return;
            }
            StandPower power = StandPower.get(player);
            if (power == null) {
                startRecovery();
                return;
            }
            if (mode.holdToFire()
                    && ability instanceof JumpingJackFlashAbility held) {
                held.setCooldownOnUse(power);
            }
            switch (mode) {
                case VOLLEY ->
                        JumpingJackFlashService.fireVolley(player);
                case GRAVITY_SNOWBALL ->
                        JumpingJackFlashService.throwGravitySnowball(
                                player);
                case CANCEL_GRAVITY ->
                        JumpingJackFlashService.cancelGravity(player);
                case DOMAIN_OPEN ->
                        JumpingJackFlashService.openDomain(player);
                case DOMAIN_CLOSE ->
                        JumpingJackFlashService.closeDomain(player);
            }
            // 1.16 StandEntityTask paid the staminaCost after standPerform and ignored the result
            StandAbilityStamina.consume(ability, power, mode.stamina, false);
        }
    }
}
