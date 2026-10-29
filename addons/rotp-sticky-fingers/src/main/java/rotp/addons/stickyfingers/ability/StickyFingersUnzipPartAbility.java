package rotp.addons.stickyfingers.ability;

import java.util.OptionalInt;

import javax.annotation.Nullable;

import rotp.core.api.client.render.AbilitySelectionVisualQuery;
import rotp.core.client.ClientGlobals;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectType;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import rotp.addons.stickyfingers.ability.StickyFingersUnzipPunchAbility.UnzipPunchAction;
import rotp.addons.stickyfingers.entity.DetachedBodyPartEntity;
import rotp.addons.stickyfingers.state.DetachedPartTargeting;
import rotp.addons.stickyfingers.state.DetachedPartType;
import rotp.addons.stickyfingers.state.ZipperCombatCloseReason;
import rotp.addons.stickyfingers.state.ZipperCombatData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 sticky_fingers_unzip_head / _arm / _leg (070 InitStands :76-79, StickyFingersUnzipBodyPart), the unzip punch's
 * attackRecoveryFollowup: while the finisher runs, the heavy key offers the one for the body part its user aimed at when
 * it started (StandAction.replaceActionKostyl), and a press puts it on the running punch as its modifier; the limb comes
 * off at the end of the punch's recovery.
 */
public final class StickyFingersUnzipPartAbility extends Ability {
    private final DetachedPartType.Region part;

    public StickyFingersUnzipPartAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            DetachedPartType.Region part) {
        super(abilityType, abilityId);
        this.part = part;
        isSubAbility = true;
    }

    public DetachedPartType.Region getPart() {
        return part;
    }

    // 1.16 StandEntityActionModifier.greenSelection: a selected unzip follow-up was framed green while its conditions
    // held
    public static OptionalInt selectionTint(AbilitySelectionVisualQuery query) {
        return query.ability() instanceof StickyFingersUnzipPartAbility
                && query.conditionCheck().isPositive()
                ? OptionalInt.of(SELECTION_GREEN) : OptionalInt.empty();
    }

    private static final int SELECTION_GREEN = 0xFF00FF00;

    // 1.16 StandAction.canSetFollowUp ran checkRequirements on the finisher's task target: the Stand not stunned
    // (K5-70 A), no held place_zipper, a humanoid within the action's range of the Stand, the task with no modifier
    // yet and the part recorded
    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        StandPower standPower = PowerClass.STAND.cast(context);
        UnzipPunchAction punch = runningUnzipPunch(standPower);
        if (abilities != null
                && punch != null
                && punch.getRecordedPart() == part
                && heavyKeyResolvesTo(abilities, punch)
                && takesTaskTarget(punch, context.getUser())
                && checkPerformerStun(standPower).isPositive()
                && !StickyFingersZipperAbility.refusedByHeldZipper(
                        context.getUser(), this)) {
            abilities.replaceOtherAbilityWith(
                    standPower, "heavy_punch", this);
        }
        return null;
    }

    // 1.16 StickyFingersUnzipBodyPart.checkSpecificConditions on the click's mouse target: a humanoid that is no
    // body part, within 8 blocks of the Stand (4 unseen), the running task with no modifier and this part; refused
    // without a message
    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        LivingEntity user = context.getUser();
        UnzipPunchAction punch =
                runningUnzipPunch(PowerClass.STAND.cast(context));
        if (user == null
                || punch == null
                || punch.getRecordedPart() != part) {
            return ConditionCheck.NEGATIVE;
        }
        Entity aimed = LivingComponentAction.getAimTarget(user)
                .getEntity();
        return ConditionCheck.noMessage(aimed != null
                && canUnzip(aimed, user)
                && ActionTargetRange.isEntityWithinRange(
                        ActionTargetRange.standPerformer(user),
                        aimed,
                        ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ));
    }

    // 1.16 StandAction.onPerform paid the 50 on the click with the result
    // ignored, and StandEntityActionModifier.perform added the modifier to
    // the Stand's running task
    @Override
    public void onClick(
            Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()) {
            return;
        }
        StandPower standPower = StandPower.get(user);
        if (runningUnzipPunch(standPower) != null) {
            StandAbilityStamina.consume(
                    this,
                    standPower,
                    StickyFingersPhase1Spec.UNZIP_PART_STAMINA,
                    false);
            standPower.userStandEffects.addEffect(
                    modifierType(part).create(level));
        }
    }

    @Override
    public float getStaminaCost(Power<?> context) {
        return StickyFingersPhase1Spec.UNZIP_PART_STAMINA;
    }

    @Nullable
    static UnzipPunchAction runningUnzipPunch(
            @Nullable StandPower standPower) {
        StandEntity stand = standPower != null
                ? standPower.getSummonedStandEntity()
                : null;
        EntityActionInstance action = stand != null
                ? stand.getCurStandAction()
                : null;
        return action instanceof UnzipPunchAction punch
                && !punch.isOver()
                && punch.getPunchModifiers().isEmpty()
                        ? punch
                        : null;
    }

    private static boolean heavyKeyResolvesTo(
            AvailableAbilities abilities, UnzipPunchAction punch) {
        Ability key = abilities.getContextVariation("heavy_punch");
        return key != null
                && key.getAbilityId().equals(
                        punch.ability.getAbilityId());
    }

    private static boolean takesTaskTarget(
            UnzipPunchAction punch, @Nullable LivingEntity user) {
        Entity target = user != null
                ? punch.getTaskTarget(user.level()).getEntity()
                : null;
        return target != null
                && canUnzip(target, user)
                && ActionTargetRange.isEntityWithinRange(
                        ActionTargetRange.standPerformer(user),
                        target,
                        ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ);
    }

    // ZipperWorldCap.isHumanoid is the rotp_stfn:zipper_humanoids tag here;
    // a detached body part is no living entity
    private static boolean canUnzip(Entity target, LivingEntity user) {
        return target instanceof LivingEntity living
                && !target.is(user)
                && ZipperCombatData.isSupportedTarget(living);
    }

    private static EntityCustomEffectType<UnzipPartModifier> modifierType(
            DetachedPartType.Region part) {
        return switch (part) {
            case HEAD -> StickyFingersAddon.UNZIP_HEAD_MODIFIER.get();
            case TORSO_ARMS -> StickyFingersAddon.UNZIP_ARM_MODIFIER.get();
            case LEGS -> StickyFingersAddon.UNZIP_LEG_MODIFIER.get();
        };
    }

    /**
     * 1.16 StickyFingersUnzipBodyPart as the modifier of the running unzip punch: its standTickRecovery played the
     * unzip sound on the clients that hear Stands in the recovery's first tick, and in its last one (ticksLeft <= 1)
     * took the part off the task target, healed it by half the damage the punch dealt and knocked the part along its
     * user's head yaw.
     */
    public static final class UnzipPartModifier extends StandEffectInstance {
        private final DetachedPartType.Region part;

        public UnzipPartModifier(
                EntityCustomEffectType<?> type,
                DetachedPartType.Region part) {
            super(type);
            this.part = part;
            isFromStandAction = true;
        }

        public DetachedPartType.Region getPart() {
            return part;
        }

        @Override
        protected void start() {}

        // the punch it modified ended or was replaced before its recovery's
        // last tick
        @Override
        protected void tick() {
            if (!level.isClientSide()
                    && (standAction == null || standAction.isOver())) {
                remove();
            }
        }

        @Override
        protected void stop() {}

        @Override
        public void onModifiedActionTick(EntityActionInstance action) {
            if (action.getPhase() != ActionPhase.RECOVERY
                    || !(action.getPerformer() instanceof StandEntity stand)
                    || !(action.getTaskTarget(level).getEntity()
                            instanceof LivingEntity entity)
                    || !entity.isAlive()) {
                return;
            }
            if (level.isClientSide()) {
                if (action.getPhaseTick() < 1
                        && ClientGlobals.canHearStand(stand)) {
                    level.playLocalSound(
                            entity.getX(),
                            entity.getY(0.5D),
                            entity.getZ(),
                            StickyFingersAddon.STICKY_FINGERS_UNZIP_PART.get(),
                            stand.getSoundSource(),
                            1.0F,
                            1.0F,
                            false);
                }
            }
            else if (action.getPhaseTicksLeft() <= 1.0F) {
                remove();
                unzip(action, StandUtil.getStandUser(entity));
            }
        }

        private void unzip(EntityActionInstance action, LivingEntity target) {
            LivingEntity user = getStandUser();
            if (!(level instanceof ServerLevel serverLevel) || user == null) {
                return;
            }
            ZipperCombatData data = ZipperCombatData.get(serverLevel);
            DetachedPartType detachedPart =
                    DetachedPartTargeting.selectAvailable(
                            part,
                            data.partsFor(target.getUUID()),
                            target.getMainArm(),
                            user.getRandom());
            DetachedBodyPartEntity detached = null;
            if (detachedPart != null) {
                detached = new DetachedBodyPartEntity(
                        entityType(detachedPart), serverLevel);
                positionDetachedPart(detached, target, detachedPart);
                if (!data.registerDetachedPart(
                        serverLevel, user, target, detachedPart, detached)) {
                    detached = null;
                }
                else if (!serverLevel.addFreshEntity(detached)) {
                    data.recoverPart(
                            serverLevel,
                            detached.getUUID(),
                            ZipperCombatCloseReason.ORPHAN_RECOVERY);
                    detached = null;
                }
            }
            target.setHealth(target.getHealth()
                    + action.lastDamageDealtToLiving
                            * StickyFingersPhase1Spec
                                    .UNZIP_DAMAGE_REFUND_MULTIPLIER);
            if (detached != null) {
                knockback(detached, user.getYHeadRot());
            }
        }

        // 1.16 DamageUtil.knockback(part, 1, yRot): LivingEntity.knockback
        // with strength 1 towards the yaw
        private static void knockback(Entity entity, float yRotDeg) {
            float radians = yRotDeg * Mth.DEG_TO_RAD;
            Vec3 push = new Vec3(Mth.sin(radians), 0.0D, -Mth.cos(radians))
                    .normalize();
            Vec3 motion = entity.getDeltaMovement();
            entity.setDeltaMovement(
                    motion.x / 2.0D - push.x,
                    entity.onGround()
                            ? Math.min(0.4D, motion.y / 2.0D + 1.0D)
                            : motion.y,
                    motion.z / 2.0D - push.z);
            entity.hasImpulse = true;
        }

        private static EntityType<DetachedBodyPartEntity> entityType(
                DetachedPartType part) {
            return switch (part) {
                case HEAD -> StickyFingersAddon.PLAYER_HEAD.get();
                case LEFT_ARM, RIGHT_ARM ->
                        StickyFingersAddon.PLAYER_ARM.get();
                case LEFT_LEG, RIGHT_LEG ->
                        StickyFingersAddon.PLAYER_LEG.get();
            };
        }

        private static void positionDetachedPart(
                DetachedBodyPartEntity detached,
                LivingEntity target,
                DetachedPartType part) {
            Vec3 pos = DetachedPartTargeting.spawnPosition(part, target);
            detached.moveTo(
                    pos.x, pos.y, pos.z, target.getYRot(), target.getXRot());
        }
    }
}
