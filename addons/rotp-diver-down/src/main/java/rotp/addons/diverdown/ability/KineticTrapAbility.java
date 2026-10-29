package rotp.addons.diverdown.ability;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.util.functions.MathUtil;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.addons.diverdown.entity.KineticTrapEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 diver_down_kinetic_trap, a StandEntityHeavyAttack (InitStands :73): the Stand winds up as a heavy attack,
 * strikes the block in its one perform tick and the trap is set there, then it recovers as a heavy attack.
 */
public final class KineticTrapAbility extends StandEntityAbility {
    public static final int RESOLVE_LEVEL = 2;
    public static final float STAMINA_COST = 150.0F;
    public static final int COOLDOWN_TICKS = 120;
    private static final ActionAnimIdentifier HEAVY_ATTACK_ANIM =
            ActionAnimIdentifier.getOrCreate("heavy_punch", false);

    public KineticTrapAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, KineticTrapAction::new);
        resolveLevelToUnlock(RESOLVE_LEVEL);
        cooldown(COOLDOWN_TICKS);
        // 1.16 StandEntityHeavyAttack: windup and recovery by the Stand's
        // speed, the builder's default single perform tick
        setDefaultPhaseLength(ActionPhase.WINDUP,
                StandStatFormulas.getHeavyAttackWindup(12, 0));
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY,
                StandStatFormulas.getHeavyAttackRecovery(12, 0));
        noFinisherBarDecay = true;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        // 1.16 kinetic_trap did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        LivingEntity owner =
                context != null ? context.getUser() : null;
        // 1.16 StandEntityAction summoned the Stand for the press, so only
        // the block target is asked of a user without it
        if (owner == null
                || pressTarget(owner, DiverDownAddon.activeStand(owner), this)
                        .getType() != HitResult.Type.BLOCK) {
            return ConditionCheck.createNegative("block_target");
        }
        return ConditionCheck.POSITIVE;
    }

    /*
     * 1.16 took the client's target (TargetRequirement.BLOCK): the vanilla pick of the user's pick range, and with
     * Diver Down out, after a miss, its precision ray (EntityStandType.clientHitResult), which follows the aim assist
     * and the walls rule as the trap's strike does. With Diver Down away the client had no Stand to ask.
     */
    static HitResult pressTarget(LivingEntity owner, DiverDownStandEntity stand, Object skill) {
        return DiverDownTargeting.crosshair(
                owner, stand, StandSkillWalls.wallsHideEntities(owner.level(), skill));
    }

    // 1.16 StandEntityHeavyAttack.checkStandConditions
    @Override
    protected ConditionCheck checkStandEntityConditions(
            StandPower standPower, StandEntity standEntity) {
        return ConditionCheck.noMessage(standEntity.canAttackMelee());
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity owner,
            LivingEntity performer) {
        super.initActionFromConfig(action, level, owner, performer);
        if (!level.isClientSide()
                && performer instanceof StandEntity stand) {
            action.phasesLength.put(
                    ActionPhase.WINDUP,
                    StandStatFormulas.getHeavyAttackWindup(
                            stand.getAttackSpeed(),
                            stand.getFinisherMeter()));
        }
        if (!level.isClientSide() && owner != null) {
            // 1.16 diver_down_kinetic_trap .shout(ANASUI_DIVER_ENTITY_PHASING)
            sayShout(owner, DiverDownAddon.ANASUI_DIVER_ENTITY_PHASING);
        }
    }

    // 1.16 StandEntityHeavyAttack.lockOnTargetPosition: the Stand stands
    // along the user's view at its distance from the block
    @Override
    public boolean lockOnTargetPosition(
            @Nullable StandPower standPower,
            StandEntity standEntity,
            EntityActionInstance action) {
        return false;
    }

    // 1.16 StandEntityAction.checkTarget: the BLOCK requirement, and
    // StandEntityHeavyAttack.standKeepsTarget an entity
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == ActionTarget.TargetType.BLOCK
                || target.getType() == ActionTarget.TargetType.ENTITY;
    }

    // 1.16 StandPose.HEAVY_ATTACK
    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return HEAVY_ATTACK_ANIM;
    }

    // 1.16 StandEntityHeavyAttack.getOffsetFromUser: front3dOffset
    static double minFrontOffset(StandEntity stand) {
        return Math.min(0.5D, stand.getEffectiveRange());
    }

    static double maxFrontOffset(StandEntity stand) {
        return Math.min(2.0D, stand.getMaxRange());
    }

    // 1.16 DiverDownKineticTrap.standPerform: inside the aimed block, or a
    // block lower when its top face is not sturdy
    static Vec3 trapPosition(Level level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)
                ? Vec3.atCenterOf(pos)
                : Vec3.atBottomCenterOf(pos).add(0.0D, -0.5D, 0.0D);
    }

    public static final class KineticTrapAction
            extends EntityActionInstance {
        private boolean playedSwingSound;

        public KineticTrapAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            if (!(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            // 1.16 StandEntityHeavyAttack.onTaskSet and onClick; a failed
            // input that puts the trap back keeps what it took
            if (!isRestoringInputLifecycle()) {
                stand.alternateHands();
            }
            if (takesTargetsFromServer()) {
                return;
            }
            if (!isRestoringInputLifecycle()) {
                stand.setHeavyPunchFinisher();
                phasesLength.put(
                        ActionPhase.RECOVERY,
                        StandStatFormulas.getHeavyAttackRecovery(
                                stand.getAttackSpeed(),
                                stand.getLastHeavyFinisherValue()));
                stand.addFinisherMeter(-0.51F, 0);
            }
            LivingEntity owner = getPowerUser();
            ActionTarget pressed = ActionTarget.EMPTY;
            if (owner != null
                    && pressTarget(
                                    owner,
                                    stand instanceof DiverDownStandEntity diverDown
                                            ? diverDown
                                            : null,
                                    this)
                            instanceof BlockHitResult hit
                    && hit.getType() == HitResult.Type.BLOCK) {
                pressed = new ActionTarget(
                        hit.getBlockPos(), hit.getDirection());
            }
            // the press target is the task's (pressedTaskTarget), and 1.16
            // turned the Stand to its task's target
            standRotationTarget = pressed.getType()
                    != ActionTarget.TargetType.EMPTY ? pressed : null;
            setStandFrontOffsetFromTarget(
                    stand,
                    placementTarget(pressed),
                    minFrontOffset(stand),
                    maxFrontOffset(stand));
        }

        // 1.16 StandEntity.updatePosition asked the task's getOffsetFromUser
        // on every tick, summon lock included, right before it placed the
        // Stand: the distance from the target it holds then
        @Override
        public void updateStandOffset(StandEntity stand) {
            if (!takesTargetsFromServer()) {
                StandEntityBarrageAbility.refreshFrontOffset(
                        this,
                        stand,
                        placementTarget(getTaskTarget(stand.level())),
                        minFrontOffset(stand),
                        maxFrontOffset(stand));
            }
        }

        @Override
        public void actionTick() {
            if (!(getPerformer() instanceof StandEntity stand)
                    || level().isClientSide()) {
                return;
            }
            // 1.16 StandEntityHeavyAttack.standTickWindup: the heavy swing
            // with 3 windup ticks left
            if (getPhase() == ActionPhase.WINDUP && !playedSwingSound
                    && (int) (calcFullTicks(ActionPhase.PERFORM, 0)
                            - getFullTicksPassed()) <= 3) {
                playedSwingSound = true;
                StandUtil.playStandEntitySound(
                        stand,
                        ModSoundEvents.STAND_PUNCH_HEAVY_SWING,
                        1.0F,
                        0.65F + stand.getRandom().nextFloat() * 0.2F);
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)
                    || !(getPerformer() instanceof DiverDownStandEntity stand)) {
                return;
            }
            LivingEntity owner = getPowerUser();
            // 1.16 standSound(DIVER_DOWN_ENTITY_PHASING) of the perform phase
            StandUtil.playStandEntitySound(
                    stand,
                    DiverDownAddon.DIVER_DOWN_ENTITY_PHASING,
                    1.0F,
                    1.0F);
            ActionTarget block = owner != null
                    ? trapAim(serverLevel, stand, owner)
                    : ActionTarget.EMPTY;
            // 1.16 DiverDownKineticTrap.standPerform set the trap in any block, spawn protection and Adventure mode too
            if (block.getType() == ActionTarget.TargetType.BLOCK) {
                KineticTrapEntity trap =
                        new KineticTrapEntity(owner, stand, serverLevel);
                trap.setPos(trapPosition(serverLevel, block.getBlockPos()));
                trap.setYRot(owner.getYRot());
                serverLevel.addFreshEntity(trap);
            }
            // 1.16 StandEntityTask paid the cost after the perform and
            // ignored the result: a short bar only drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    owner != null ? StandPower.get(owner) : null,
                    STAMINA_COST,
                    false);
        }

        /*
         * 1.16 DiverDownEntity.aimWithThisOrUser(5, task target), which replaced StandEntity's: only an ENTITY the
         * task holds stays the target while the Stand reaches it, so the pressed block does not; otherwise the
         * user's ray at the strike unless the Stand is manually controlled, and the Stand's own ray only then or
         * while it is inside a target (isInside). A miss of the user's ray stays a miss. With the user's aim assist
         * the ray is 1.16's precisionRayTrace (hitboxes grown by the Stand's precision, players and hostile mobs
         * first), walls hiding entities as for every unmarked skill; without it the crosshair's first hit. The Stand
         * turns toward what it chose; the caller sets the trap only in a block.
         */
        private ActionTarget trapAim(
                ServerLevel level,
                DiverDownStandEntity stand,
                LivingEntity owner) {
            ActionTarget kept = getTaskTarget(level);
            ActionTarget target;
            if (kept.getType() == ActionTarget.TargetType.ENTITY
                    && HitResultUtil.isTargetWithinRange(
                            kept,
                            stand,
                            level,
                            stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE),
                            stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))) {
                target = kept;
            } else {
                Entity aiming = stand.isManuallyControlled()
                        || stand.isEmbedded() ? stand : owner;
                target = StandEntityPunchAbility.aimAssist(owner)
                        ? StandPrecisionRay.trace(
                                aiming,
                                stand,
                                DiverDownTargeting.RANGE,
                                0.0D,
                                true,
                                StandSkillWalls.wallsHideEntities(level, this))
                        : ActionTarget.fromVanilla(
                                DiverDownTargeting.firstHit(aiming, stand));
            }
            Vec3 targetPos = switch (target.getType()) {
                case ENTITY -> target.getEntity() != null
                        && target.getEntity() != stand
                        ? target.getEntity().getEyePosition()
                        : null;
                case BLOCK -> Vec3.atCenterOf(target.getBlockPos());
                default -> null;
            };
            if (targetPos != null) {
                rotateTowards(stand, targetPos,
                        (float) stand.getAttackSpeed() / 16.0F * 18.0F);
            }
            return target;
        }

        // 1.16 MCUtil.rotateTowards: the yaw and the pitch each turn by at
        // most maxAngle, the head with the yaw
        static void rotateTowards(
                StandEntity stand, Vec3 targetPos, float maxAngle) {
            Vec3 toTarget = targetPos.subtract(stand.getEyePosition());
            float yRot = stand.getYRot() + Mth.clamp(
                    Mth.degreesDifference(stand.getYRot(),
                            MathUtil.yRotDegFromVec(toTarget)),
                    -maxAngle, maxAngle);
            float xRot = stand.getXRot() + Mth.clamp(
                    Mth.degreesDifference(stand.getXRot(),
                            MathUtil.xRotDegFromVec(toTarget)),
                    -maxAngle, maxAngle);
            stand.setYRot(yRot % 360.0F);
            stand.setXRot(xRot % 360.0F);
            stand.setYHeadRot(yRot);
        }

        // 1.16 offsetToTarget measured to ActionTarget.getTargetPos: a
        // block's centre, not where the aim met its face
        private static ActionTarget placementTarget(ActionTarget target) {
            return target.getType() == ActionTarget.TargetType.BLOCK
                    ? new ActionTarget(target.getBlockPos(), target.getFace())
                    : target;
        }
    }
}
