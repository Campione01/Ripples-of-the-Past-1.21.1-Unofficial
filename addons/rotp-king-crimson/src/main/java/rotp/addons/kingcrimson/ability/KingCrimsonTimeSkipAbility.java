package rotp.addons.kingcrimson.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class KingCrimsonTimeSkipAbility extends Ability
        implements EntityActionType {
    public KingCrimsonTimeSkipAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        cooldown(
                KingCrimsonPhase5Spec.TIME_SKIP_COOLDOWN_TICKS);
    }

    // 1.16 KCConfig timeSkipCooldown
    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        return KingCrimsonConfig.configuredCooldown(
                super.getCooldown(context, ticksHeld),
                KingCrimsonConfig.timeSkipCooldownTicks());
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        // 1.16 kingcrimson_timeskip did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        if (!(context.getUser() instanceof ServerPlayer player)) {
            return context.getUser() instanceof LivingEntity
                    ? localStateCheck(context)
                    : ConditionCheck.NEGATIVE;
        }
        if (!KingCrimsonTemporalStateService
                .canStartTimeSkip(player)) {
            return negative("temporal_state_active");
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public HeldInput onKeyPress(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput,
            InputMethod inputMethod,
            float clickHoldResolveTime,
            BufferingState bufferingState) {
        bufferingState.isActionSuccess = false;
        if (!(user instanceof ServerPlayer player)
                || level.isClientSide()) {
            return null;
        }
        StandPower power = StandPower.get(player);
        if (power == null
                || !checkConditions(power).isPositive()) {
            return null;
        }
        // 1.16 StandAction.onPerform pays before the skip and ignores the result: a short bar drops to 0.
        StandAbilityStamina.consume(
                (Ability) this,
                power,
                KingCrimsonPhase5Spec.TIME_SKIP_STAMINA,
                false);
        if (KingCrimsonTemporalStateService
                .beginTimeSkip(player)) {
            setCooldownOnUse(power);
            bufferingState.isActionSuccess = true;
            startPlayerAction(player);
        }
        return null;
    }

    // 1.16 KingCrimsonTimeSkip.perform set its IPlayerAction: while it ran
    // (Instance.invTicks), Action.checkConditions :140-143 refused its user's
    // non-Stand actions without a message; Stand actions went through.
    private void startPlayerAction(ServerPlayer player) {
        EntityActionInstance action = createActionObj();
        action.setStartingPhase();
        LivingComponentAction.getComponent(player, PowerClass.STAND)
                .setAction(action, player, SyncType.TRACKING_AND_SELF);
    }

    @Override
    public EntityActionInstance createActionObj() {
        return new TimeSkipPlayerAction(this);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return null;
    }

    @Override
    public ResourceLocation getEntityAnimSet(LivingEntity user) {
        return null;
    }

    public static final class TimeSkipPlayerAction
            extends EntityActionInstance {
        private int actionCooldown;

        private TimeSkipPlayerAction(EntityActionType ability) {
            super(ability);
            phasesLength.put(
                    ActionPhase.PERFORM,
                    (float) KingCrimsonPhase5Spec
                            .TIME_SKIP_INVULNERABLE_TICKS);
        }

        // 1.16 ContinuousActionInstance took getCooldown(power, -1) as the
        // action was created (:29-37) and onStop (:105-109) set it again
        // however the action stopped: the skip's cooldown restarted then.
        @Override
        public void onActionSet(EntityActionInstance prevAction) {
            StandPower power = serverPower();
            if (power != null) {
                actionCooldown = ((KingCrimsonTimeSkipAbility) ability)
                        .getCooldown(power, -1);
            }
        }

        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            StandPower power = serverPower();
            if (power != null && actionCooldown > 0) {
                power.setAbilityCooldown(
                        ((KingCrimsonTimeSkipAbility) ability).name(),
                        actionCooldown);
            }
        }

        private StandPower serverPower() {
            LivingEntity user = getPowerUser();
            return user != null && !user.level().isClientSide()
                    ? StandPower.get(user)
                    : null;
        }

        @Override
        public boolean isPlayerAction() {
            return true;
        }

        // 1.16 StandAction.getPerformer: the summoned Stand, else the user;
        // the user's pose stays its own while King Crimson is out.
        @Override
        public LivingEntity getPerformer() {
            LivingEntity user = super.getPerformer();
            StandEntity stand = user != null
                    ? StandUtil.getSummonedStand(user)
                    : null;
            return stand != null ? stand : user;
        }

        @Override
        protected boolean alignsBodyYawToLook() {
            return false;
        }
    }

    private static ConditionCheck localStateCheck(
            Power<?> context) {
        LivingEntity user = context.getUser();
        if (user instanceof net.minecraft.world.entity.player.Player
                player
                && player.hasData(KingCrimsonAddon.TEMPORAL_STATE)
                && !KingCrimsonAddon.temporalState(player).isIdle()) {
            return negative("temporal_state_active");
        }
        return ConditionCheck.POSITIVE;
    }

    // Full addon key; the String overload would add the jojo prefix.
    private static ConditionCheck negative(String key) {
        return ConditionCheck.createNegative(
                Component.translatable(
                        "rotp_kingcrimson.message." + key));
    }
}
