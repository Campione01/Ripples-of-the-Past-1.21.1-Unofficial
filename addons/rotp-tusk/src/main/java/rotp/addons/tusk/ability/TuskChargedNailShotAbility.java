package rotp.addons.tusk.ability;

import rotp.core.entityattachment.syncheddata.SyncedDataHolderExtended;
import rotp.core.entityattachment.syncheddata.SynchedDataBuilder;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.entity.NailEntity;
import rotp.addons.tusk.entity.WormholeArmEntity;
import rotp.addons.tusk.state.TuskActRules;
import rotp.addons.tusk.state.TuskNailState;
import rotp.addons.tusk.state.TuskPortalOperations;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class TuskChargedNailShotAbility
        extends EntityActionAbility {
    public static final int MAX_CHARGE_TICKS =
            TuskActRules.MAX_CHARGE_TICKS;
    public static final int TICKS_PER_SPIN_CHARGE =
            TuskActRules.TICKS_PER_SPIN_CHARGE;
    public static final int MAX_SPIN_CHARGE =
            TuskActRules.MAX_SPIN_CHARGE;
    public static final float STAMINA_COST = 175.0F;
    public static final float SHOT_VELOCITY = 2.0F;
    public static final float SHOT_INACCURACY = 1.0F;
    public static final int RECOVERY_TICKS = 10;

    private static final ResourceLocation PLAYER_ANIM_SET =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples", "addons/rotp_t/tusk_player");
    private static final ActionAnimIdentifier CHARGED_LEFT =
            ActionAnimIdentifier.getOrCreate(
                    "tusk_charged_nail_shot_left", false);
    private static final ActionAnimIdentifier CHARGED_RIGHT =
            ActionAnimIdentifier.getOrCreate(
                    "tusk_charged_nail_shot_right", false);

    public TuskChargedNailShotAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ChargedNailShotAction::new);
        setButtonHoldPhase(ActionPhase.BUTTON_CHARGE);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, MAX_CHARGE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        if (!(context.getUser() instanceof Player player)
                || !TuskAddon.isChargedNailUser(player)) {
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 Action.checkConditions asked checkHeldItems (the hands) before TuskChargedNailShot.checkSpecificConditions.
        if (!player.getMainHandItem().isEmpty()
                || !player.getOffhandItem().isEmpty()) {
            return ConditionCheck.createNegative("hands");
        }
        // 1.16 checkSpecificConditions: nails > 0 or a Creative player.
        if (TuskAddon.nailState(player).nails() <= 0
                && !player.getAbilities().instabuild) {
            return ConditionCheck.createNegative(Component.translatable(
                    "rotp_t.message.action_condition.no_nails"));
        }
        return ConditionCheck.POSITIVE;
    }

    // 1.16 PowerBaseImpl.tickHeldAction reran TuskChargedNailShot.checkHeldItems
    // and checkSpecificConditions on every held tick.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        if (!(context.getUser() instanceof Player player)
                || !TuskAddon.isChargedNailUser(player)) {
            return ConditionCheck.NEGATIVE;
        }
        if (!player.getMainHandItem().isEmpty()
                || !player.getOffhandItem().isEmpty()) {
            return ConditionCheck.createNegative("hands");
        }
        return TuskAddon.nailState(player).nails() > 0
                        || player.getAbilities().instabuild
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    // 1.16 holdType: every end of the hold, a failed check included, ran
    // TuskChargedNailShot.stoppedHolding, which fired the nail.
    @Override
    public void stopHeldActionOnFailedCheck(EntityActionInstance action) {
        action.onKeyRelease(action.getPowerUser());
        action.syncPhaseChanges();
    }

    @Override
    public Component getName(Power<?> context) {
        int nails = context.getUser() instanceof Player player
                ? TuskAddon.nailState(player).nails()
                : 0;
        return abilityName(
                context, "", nails, TuskNailState.MAX_NAILS);
    }

    @Override
    public ResourceLocation getEntityAnimSet(LivingEntity user) {
        return PLAYER_ANIM_SET;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        LivingEntity user = action.getPerformer();
        boolean mainHand = !(action
                instanceof ChargedNailShotAction shot)
                || shot.usesMainHand();
        HumanoidArm arm = physicalArm(user, mainHand);
        return arm == HumanoidArm.LEFT
                ? CHARGED_LEFT
                : CHARGED_RIGHT;
    }

    private static InteractionHand handForNails(int nails) {
        return nails > 5
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
    }

    private static HumanoidArm physicalArm(
            LivingEntity user, boolean mainHand) {
        HumanoidArm mainArm = user != null
                ? user.getMainArm()
                : HumanoidArm.RIGHT;
        if (mainHand) {
            return mainArm;
        }
        return mainArm == HumanoidArm.RIGHT
                ? HumanoidArm.LEFT
                : HumanoidArm.RIGHT;
    }

    public static final class ChargedNailShotAction
            extends EntityActionInstance
            implements SyncedDataHolderExtended {
        private static final EntityDataAccessor<Boolean> USES_MAIN_HAND =
                SynchedEntityData.defineId(
                        ChargedNailShotAction.class,
                        EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Integer> CHARGE_TICKS =
                SynchedEntityData.defineId(
                        ChargedNailShotAction.class,
                        EntityDataSerializers.INT);

        public ChargedNailShotAction(EntityActionType ability) {
            super(ability);
        }

        public boolean usesMainHand() {
            return synchedData.get(USES_MAIN_HAND);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            if (!level().isClientSide()
                    && getPerformer() instanceof Player player) {
                synchedData.set(
                        USES_MAIN_HAND,
                        TuskAddon.nailState(player).nails() > 5);
            }
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                synchedData.set(
                        CHARGE_TICKS,
                        Mth.clamp(
                                Mth.floor(getPhaseTick()),
                                0,
                                MAX_CHARGE_TICKS));
                setPhaseStart(ActionPhase.PERFORM);
                syncPhaseChanges();
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer() instanceof Player player)
                    || !TuskAddon.isChargedNailUser(player)
                    || !player.isAlive()) {
                return;
            }

            // 1.16 stoppedHolding fired with any nail left, whatever the
            // hands held.
            TuskNailState state = TuskAddon.nailState(player);
            if (state.nails() <= 0) {
                return;
            }
            TuskPortalOperations operations = player
                    instanceof net.minecraft.server.level.ServerPlayer
                            serverPlayer
                    ? TuskPortalOperations.get(serverPlayer.server)
                    : null;
            WormholeArmEntity remoteArm = operations != null
                    ? operations.resolveArmForShot(
                            (net.minecraft.server.level.ServerPlayer)
                                    player)
                    : null;
            if (operations != null
                    && operations.hasActiveArm(
                            (net.minecraft.server.level.ServerPlayer)
                                    player)
                    && remoteArm == null) {
                return;
            }
            // 1.16 StandAction.onPerform: the cost is paid with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability, StandPower.get(player), STAMINA_COST, false);

            InteractionHand hand = handForNails(state.nails());
            synchedData.set(
                    USES_MAIN_HAND,
                    hand == InteractionHand.MAIN_HAND);
            if (!player.getAbilities().instabuild) {
                if (!state.consumeNail(TuskAddon.nailRegenTicks(player))) {
                    return;
                }
                TuskAddon.syncNailState(player);
            }

            int chargeTicks = synchedData.get(CHARGE_TICKS);
            int spinCharge =
                    TuskActRules.spinChargeForTicks(chargeTicks);
            NailEntity nail = new NailEntity(player, level());
            nail.setSpinCharge(spinCharge);
            TuskNailShotAbility.shootNail(
                    nail, player, remoteArm, SHOT_VELOCITY, SHOT_INACCURACY);
            player.swing(hand, true);
        }

        @Override
        public void defineSynchedData(SynchedDataBuilder builder) {
            builder.define(USES_MAIN_HAND, true);
            builder.define(CHARGE_TICKS, MAX_CHARGE_TICKS);
        }

        @Override
        public <T> void onSyncedDataUpdated(
                T oldValue,
                T newValue,
                EntityDataAccessor<T> dataAccessor) {}
    }
}
