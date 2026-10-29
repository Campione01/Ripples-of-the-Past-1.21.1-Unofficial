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

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.entity.NailEntity;
import rotp.addons.tusk.entity.WormholeArmEntity;
import rotp.addons.tusk.state.TuskNailState;
import rotp.addons.tusk.state.TuskPortalOperations;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class TuskNailShotAbility extends EntityActionAbility {
    public static final int SHOT_INTERVAL_TICKS = 5;
    // The donor registered this HUD cost but never consumed it for a plain held action.
    public static final float STAMINA_COST_TICK = 2.0F;
    public static final float SHOT_VELOCITY = 2.0F;
    public static final float SHOT_INACCURACY = 1.0F;
    public static final int RECOVERY_TICKS = 4;

    private static final ResourceLocation PLAYER_ANIM_SET =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples", "addons/rotp_t/tusk_player");
    private static final ActionAnimIdentifier SHOT_LEFT =
            ActionAnimIdentifier.getOrCreate(
                    "tusk_nail_shot_left", false);
    private static final ActionAnimIdentifier SHOT_RIGHT =
            ActionAnimIdentifier.getOrCreate(
                    "tusk_nail_shot_right", false);

    public TuskNailShotAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, NailShotAction::new);
        setButtonHoldPhase(ActionPhase.PERFORM);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        if (!(context.getUser() instanceof Player player)
                || !TuskAddon.isTuskUser(player)) {
            return ConditionCheck.NEGATIVE;
        }

        TuskNailState state = TuskAddon.nailState(player);
        if (player.getAbilities().instabuild) {
            return ConditionCheck.POSITIVE;
        }
        // 1.16 TuskNailShot.checkSpecificConditions tested the nail hand before the nail count.
        if (isNailHandBusy(player, state.nails())) {
            return ConditionCheck.createNegative("hands");
        }
        if (state.nails() <= 0) {
            return ConditionCheck.createNegative(Component.translatable(
                    "rotp_t.message.action_condition.no_nails"));
        }
        return ConditionCheck.POSITIVE;
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
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        LivingEntity user = action.getPerformer();
        boolean mainHand = !(action instanceof NailShotAction shot)
                || shot.usesMainHand();
        HumanoidArm arm = physicalArm(user, mainHand);
        return arm == HumanoidArm.LEFT ? SHOT_LEFT : SHOT_RIGHT;
    }

    private static InteractionHand handForNails(int nails) {
        return nails > 5
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
    }

    // 1.16 TuskNailShot.checkSpecificConditions: Creative passes whatever the hands hold.
    private static boolean isNailHandBusy(Player player, int nails) {
        return !player.getAbilities().instabuild
                && !player.getItemInHand(handForNails(nails)).isEmpty();
    }

    /**
     * 1.16 TuskNailShot.holdTick and WormholeArmEntity.tick: a nail shot through the remote arm takes the
     * arm's own rotation and motion and starts 0.5 above it; the sound stays at the user's eyes.
     */
    static void shootNail(
            NailEntity nail,
            Player player,
            WormholeArmEntity remoteArm,
            float baseVelocity,
            float inaccuracy) {
        if (remoteArm != null) {
            nail.shootFromRotation(
                    remoteArm,
                    baseVelocity
                            + (float) remoteArm.getDeltaMovement().length(),
                    inaccuracy);
            nail.setPos(remoteArm.position().add(0.0D, 0.5D, 0.0D));
            remoteArm.countNailShot();
        } else {
            nail.setShootingPosOf(player);
            nail.shootFromRotation(
                    player,
                    baseVelocity
                            + (float) player.getDeltaMovement().length(),
                    inaccuracy);
        }
        player.level().addFreshEntity(nail);
        player.level().playSound(
                null,
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                TuskAddon.NAIL_SHOT_SOUND.get(),
                player.getSoundSource(),
                1.0F,
                1.0F);
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

    public static final class NailShotAction
            extends EntityActionInstance
            implements SyncedDataHolderExtended {
        private static final EntityDataAccessor<Boolean> USES_MAIN_HAND =
                SynchedEntityData.defineId(
                        NailShotAction.class,
                        EntityDataSerializers.BOOLEAN);

        public NailShotAction(EntityActionType ability) {
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
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || level().isClientSide()) {
                return;
            }
            if (!(getPerformer() instanceof Player player)
                    || !TuskAddon.isTuskUser(player)) {
                startRecovery();
                return;
            }

            TuskNailState state = TuskAddon.nailState(player);
            if (state.nails() <= 0 && !player.getAbilities().instabuild) {
                startRecovery();
                return;
            }
            if (isNailHandBusy(player, state.nails())) {
                startRecovery();
                return;
            }
            InteractionHand hand = handForNails(state.nails());

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
                startRecovery();
                return;
            }

            // 1.16 counted the held tick before holdTick ran: nails leave on held ticks 5, 10, 15.
            if (((int) getPhaseTick() + 1) % SHOT_INTERVAL_TICKS != 0) {
                return;
            }
            // 1.16 TuskNailShot.holdTick fired only with nails left: a Creative hold at 0 nails stays and shoots nothing.
            if (state.nails() <= 0) {
                return;
            }

            boolean mainHand = hand == InteractionHand.MAIN_HAND;
            synchedData.set(USES_MAIN_HAND, mainHand);
            if (!player.getAbilities().instabuild) {
                if (!state.consumeNail(TuskAddon.nailRegenTicks(player))) {
                    startRecovery();
                    return;
                }
                TuskAddon.syncNailState(player);
            }

            NailEntity nail = new NailEntity(player, level());
            nail.setSpinCharge(0);
            shootNail(
                    nail, player, remoteArm, SHOT_VELOCITY, SHOT_INACCURACY);
            player.swing(hand, true);
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        @Override
        public void defineSynchedData(SynchedDataBuilder builder) {
            builder.define(USES_MAIN_HAND, true);
        }

        @Override
        public <T> void onSyncedDataUpdated(
                T oldValue,
                T newValue,
                EntityDataAccessor<T> dataAccessor) {}
    }
}
