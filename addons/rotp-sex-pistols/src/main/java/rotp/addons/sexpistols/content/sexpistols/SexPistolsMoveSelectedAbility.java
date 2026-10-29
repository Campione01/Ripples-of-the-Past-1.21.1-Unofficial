package rotp.addons.sexpistols.content.sexpistols;

import java.util.UUID;

import javax.annotation.Nullable;

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

import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsSounds;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 1.16 SexPistolsMoveSelectedAction: a holdType StandAction whose work is all
// done while its key is held (onHoldTick); held from the press, so the power's
// other actions are refused until the release (heldAllowsOtherAction false).
public final class SexPistolsMoveSelectedAbility extends EntityActionAbility {
    // 1.16 InitStands: cooldown(4), no stamina cost on any branch.
    public static final int COOLDOWN_TICKS = 4;
    public static final int RESOLVE_LEVEL = 1;
    public static final double TARGET_RANGE =
            SexPistolsEntity.COMMAND_RANGE;
    // 1.16 getHoldDurationMax, MOVE_SPEED and MOVE_MAX_DISTANCE.
    public static final int HOLD_DURATION_MAX = 72000;
    public static final double MOVE_STEP = 0.36D;
    public static final double MOVE_MAX_DISTANCE = 10.0D;
    public static final String PISTOL_NAME_KEY =
            "rotp_sex_pistols.sex_pistols.pistol_name";

    public SexPistolsMoveSelectedAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, MoveSelectedAction::new);
        cooldown(COOLDOWN_TICKS);
        resolveLevelToUnlock(RESOLVE_LEVEL);
        setButtonHoldPhase(ActionPhase.PERFORM);
        setDefaultPhaseLength(ActionPhase.PERFORM, HOLD_DURATION_MAX);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return null;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck parent = super.checkSpecificConditions(context);
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
        int number = coordinator.selectedNumber();
        if (!coordinator.isSummoned()) {
            return ConditionCheck.createNegative(
                    "sex_pistols_not_summoned");
        }
        if (coordinator.reviveTicks(number) > 0) {
            return ConditionCheck.createNegative(
                    "sex_pistols_member_reviving");
        }

        SexPistolsEntity member = findSelectedMember(
                owner.level(), coordinator);
        if (member == null || !member.isAlive()) {
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
        SexPistolsEntity member = findSelectedMember(
                owner.level(), coordinator);
        // 1.16 getTranslatedName :145-164: the pistol's name, and the item
        // it gives or would pick up.
        Component pistol = pistolName(coordinator.selectedNumber());
        if (member != null) {
            if (member.hasCarriedItem()) {
                return Component.translatable(
                        getTranslationKey(".give"),
                        pistol,
                        member.getCarriedItem().getHoverName());
            }
            if (member.isRetrievingItem()
                    || member.getMemberState()
                            == SexPistolsMemberState.SCOUTING) {
                return Component.translatable(
                        getTranslationKey(".recall"), pistol);
            }
        }
        ItemEntity itemTarget = findLookedAtItem(owner, TARGET_RANGE);
        if (itemTarget != null) {
            return Component.translatable(
                    getTranslationKey(".pickup"),
                    pistol,
                    itemTarget.getItem().getHoverName());
        }
        return Component.translatable(getTranslationKey(""), pistol);
    }

    // 1.16 entity.rotp_nextalbum.sex_pistols_N, "No.N" in every language.
    public static Component pistolName(int number) {
        return Component.translatable(PISTOL_NAME_KEY, number);
    }

    @Override
    public String getSpriteName(Power<?> context) {
        LivingEntity owner =
                context != null ? context.getUser() : null;
        int number = owner != null
                ? SexPistolsAttachments.coordinator(owner)
                        .selectedNumber()
                : 1;
        return "sex_pistols_no_" + number;
    }

    public static final class MoveSelectedAction
            extends EntityActionInstance {
        // 1.16 MovingState: the member, the line it flies along and how far
        // it has gone. Server only.
        @Nullable
        private UUID flyingMember;
        private Vec3 flightOrigin = Vec3.ZERO;
        private Vec3 flightDirection = Vec3.ZERO;
        private double flightDistance;

        public MoveSelectedAction(EntityActionType ability) {
            super(ability);
        }

        // A Stand user's held StandAction left the user's body alone.
        @Override
        protected boolean alignsBodyYawToLook() {
            return false;
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || !(level() instanceof ServerLevel serverLevel)) {
                return;
            }
            LivingEntity owner = getPowerUser();
            if (owner == null) {
                return;
            }
            if (flyingMember != null) {
                tickFlight(serverLevel);
            }
            else if (getPhaseTick() < 1) {
                firstHeldTick(serverLevel, owner);
            }
        }

        // 1.16 stoppedHolding: the release stops the member where it is.
        @Override
        public void onButtonStopHold() {
            stopFlight();
            startRecovery();
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            if (newPhase != ActionPhase.PERFORM) {
                stopFlight();
            }
        }

        @Override
        public void onActionCleared(
                @Nullable EntityActionInstance newAction) {
            stopFlight();
        }

        // 1.16 onHoldTick, held tick 1: deliver, recall, retrieve or start
        // the flight from the member's own position along the user's look.
        private void firstHeldTick(
                ServerLevel serverLevel, LivingEntity owner) {
            StandPower standPower = StandPower.get(owner);
            if (standPower == null
                    || standPower.getPowerType()
                            != SexPistolsStands.SEX_PISTOLS.get()) {
                return;
            }

            SexPistolsCoordinator coordinator =
                    SexPistolsAttachments.coordinator(owner);
            SexPistolsEntity member =
                    coordinator.selectedMember(serverLevel);
            if (member == null || !member.isAlive()) {
                fail(owner, "sex_pistols_member_unavailable");
                return;
            }

            boolean recalling = member.getMemberState()
                            == SexPistolsMemberState.SCOUTING
                    || member.isRetrievingItem()
                    || member.hasCarriedItem();
            ItemEntity itemTarget = !recalling
                    ? findLookedAtItem(owner, TARGET_RANGE)
                    : null;

            if (member.hasCarriedItem()) {
                member.deliverCarriedItem();
            }
            else if (member.isRetrievingItem()) {
                member.cancelItemRetrieval();
                member.setMemberState(
                        SexPistolsMemberState.FORMATION);
            }
            else if (itemTarget != null) {
                member.startRetrievingItem(itemTarget);
            }
            else if (member.getMemberState()
                    == SexPistolsMemberState.SCOUTING) {
                // 1.16 returnToUserAfterKick: a fixed member comes back
                coordinator.toggleSelectedMove(
                        serverLevel, owner, member.position());
            }
            else if (member.getMemberState()
                    == SexPistolsMemberState.ENCIRCLEMENT) {
                fail(owner, "sex_pistols_member_unavailable");
                return;
            }
            else {
                flyingMember = member.getUUID();
                flightOrigin = member.position();
                flightDirection = owner.getLookAngle().normalize();
                flightDistance = 0.0D;
                member.holdCommandPosition();
            }

            serverLevel.playSound(
                    null,
                    owner.getX(),
                    owner.getY(),
                    owner.getZ(),
                    SexPistolsSounds.randomRicochet(
                            owner.getRandom()),
                    owner.getSoundSource(),
                    0.7F,
                    recalling ? 0.85F : 1.05F);
        }

        // 1.16 tickMovingState: 0.36 further along the line each held tick,
        // up to 10; the hold itself goes on until the release.
        private void tickFlight(ServerLevel serverLevel) {
            if (!(serverLevel.getEntity(flyingMember)
                            instanceof SexPistolsEntity member)
                    || !member.isAlive()
                    || member.getMemberState()
                            != SexPistolsMemberState.SCOUTING) {
                flyingMember = null;
                return;
            }
            flightDistance = Math.min(
                    MOVE_MAX_DISTANCE, flightDistance + MOVE_STEP);
            member.moveToCommandPosition(flightOrigin.add(
                    flightDirection.scale(flightDistance)));
            if (flightDistance >= MOVE_MAX_DISTANCE - 1.0E-4D) {
                stopFlight();
            }
        }

        private void stopFlight() {
            UUID memberId = flyingMember;
            flyingMember = null;
            if (memberId != null
                    && level() instanceof ServerLevel serverLevel
                    && serverLevel.getEntity(memberId)
                            instanceof SexPistolsEntity member
                    && member.isAlive()
                    && member.getMemberState()
                            == SexPistolsMemberState.SCOUTING) {
                member.holdCommandPosition();
            }
        }

        private void fail(LivingEntity owner, String reason) {
            ConditionCheck.sendActionFailedMessage(
                    (SexPistolsMoveSelectedAbility) ability,
                    ConditionCheck.createNegative(reason),
                    owner);
        }
    }

    private static SexPistolsEntity findSelectedMember(
            Level level,
            SexPistolsCoordinator coordinator) {
        if (level instanceof ServerLevel serverLevel) {
            return coordinator.selectedMember(serverLevel);
        }
        int entityId = coordinator.childEntityId(
                coordinator.selectedNumber());
        return level.getEntity(entityId)
                instanceof SexPistolsEntity member
                ? member
                : null;
    }

    public static ItemEntity findLookedAtItem(
            LivingEntity owner, double range) {
        if (owner == null || owner.level() == null) {
            return null;
        }
        Vec3 eye = owner.getEyePosition();
        Vec3 end = eye.add(owner.getLookAngle().scale(range));
        BlockHitResult blockHit = owner.level().clip(
                new ClipContext(
                        eye,
                        end,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        owner));
        double maximumDistance = blockHit.getType()
                        == HitResult.Type.BLOCK
                ? eye.distanceToSqr(blockHit.getLocation())
                : eye.distanceToSqr(end);
        AABB search = new AABB(eye, end).inflate(1.25D);
        ItemEntity best = null;
        double bestDistance = maximumDistance;
        for (ItemEntity item :
                owner.level().getEntitiesOfClass(
                        ItemEntity.class,
                        search,
                        SexPistolsMoveSelectedAbility
                                ::isValidItemTarget)) {
            AABB itemBox = item.getBoundingBox().inflate(0.4D);
            // 1.16 :180: a box around the eye is hit at the eye.
            Vec3 hit = itemBox.clip(eye, end)
                    .orElseGet(() -> itemBox.contains(eye) ? eye : null);
            if (hit == null) {
                continue;
            }
            double distance = eye.distanceToSqr(hit);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = item;
            }
        }
        return best;
    }

    public static boolean isValidItemTarget(
            ItemEntity candidate) {
        return candidate != null
                && candidate.isAlive()
                && !candidate.getItem().isEmpty()
                && !candidate.getPersistentData()
                        .getBoolean(
                                SexPistolsEntity
                                        .ITEM_RETRIEVED_TAG);
    }
}
