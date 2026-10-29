package rotp.addons.planetwaves.ability;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.planetwaves.PlanetWavesAddon;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 PlanetWavesGrab: a held grab that pins a living target within
 * 7 blocks, slowing it and keeping the Stand in front of it.
 */
public final class PlanetWavesGrabAbility extends StandEntityAbility {
    // 1.16 holdType(140), staminaCost(75), cooldown(200).
    public static final int MAX_HOLD_TICKS = 140;
    public static final float STAMINA_COST = 75.0F;
    public static final int COOLDOWN_TICKS = 200;
    public static final double GRAB_RANGE = 7.0D;
    public static final float HEALTH_LOSS_TO_LET_GO = 4.0F;
    public static final int SLOWNESS_TICKS = 20;
    public static final int SLOWNESS_AMPLIFIER = 11;
    public static final double STAND_OFFSET = 0.85D;
    public static final int LEAP_LOCK_TICKS = 20;
    // Add-on key, so other add-ons' jojo no_target text cannot override it.
    public static final String NO_TARGET = "rotp_pw.message.no_target";
    // The core's 1.16 target_too_far text (jojo.message.action_condition).
    public static final String TARGET_TOO_FAR = "target_too_far";
    // The 1.16 icon planet_waves_grab.png lives on as ability/grab.png.
    public static final String SPRITE = "grab";

    private static final ActionAnimIdentifier GRAB_POSE =
            ActionAnimIdentifier.getOrCreate("grab", false);

    public PlanetWavesGrabAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, GrabAction::new);
        // The hold ends by itself after 140 ticks, as 1.16 holdType did.
        setButtonHoldPhase(ActionPhase.PERFORM);
        setDefaultPhaseLength(ActionPhase.PERFORM, MAX_HOLD_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
        cooldown(COOLDOWN_TICKS);
        resolveLevelToUnlock(1);
        standAutoSummonMode(AutoSummonMode.FULL);
    }

    // 1.16 set the cooldown when the hold stopped.
    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck parent = super.checkSpecificConditions(context);
        if (!parent.isPositive()) {
            return parent;
        }
        LivingEntity user = context.getUser();
        // 1.16 Action.checkRangeAndTarget, which ran before the grab's own
        // checkTarget: the entity under the crosshair beyond 8 blocks of the
        // Stand (4 without a line of sight) is target_too_far, whatever it is
        if (user != null
                && ActionTargetRange.isEntityTargetOutOfRange(
                        ActionTargetRange.standPerformer(user),
                        aimedTarget(user, user.level()),
                        ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)) {
            return ConditionCheck.createNegative(TARGET_TOO_FAR);
        }
        // then the grab's own checkTarget (no_target below) and
        // StandEntityAction.checkStandTarget, which refused an entity the
        // Stand may not attack silently
        ActionTarget aimed = user != null
                ? aimedTarget(user, user.level()) : ActionTarget.EMPTY;
        if (aimed.getType() == TargetType.ENTITY
                && canGrab(user, aimed.getEntity())
                && !standMayAttack(user, (LivingEntity) aimed.getEntity())) {
            return ConditionCheck.NEGATIVE;
        }
        if (user == null || findTarget(user, user.level()) == null) {
            return ConditionCheck.createNegative(
                    Component.translatable(NO_TARGET));
        }
        return parent;
    }

    // 1.16 let go silently once the target or the user's health failed.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()) {
            return base;
        }
        return action instanceof GrabAction grab && grab.shouldLetGo()
                ? ConditionCheck.NEGATIVE
                : ConditionCheck.POSITIVE;
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (action instanceof GrabAction && powerUser != null) {
            LivingEntity target = findTarget(powerUser, level);
            if (target != null) {
                action.standRotationTarget = new ActionTarget(target);
                action.aimAs = AimingEntity.STAND;
            }
        }
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return GRAB_POSE;
    }

    @Override
    public String getSpriteName(Power<?> context) {
        return SPRITE;
    }

    // The Stand is placed at the target, not at its user.
    @Override
    public boolean noAdheringToUserOffset(
            StandPower standPower, StandEntity standEntity) {
        return true;
    }

    @Override
    public boolean noAdheringToUserOffsetClientFallback(
            StandEntity standEntity) {
        return true;
    }

    // 1.16 getTargetRequirement ENTITY and checkTarget: the task keeps the
    // living, alive entity within 7 blocks of the user that the grab holds,
    // and only that; the grab ends once its task holds none
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == TargetType.ENTITY;
    }

    @Override
    public boolean keepsTaskTarget(
            StandEntity standEntity, @Nullable ActionTarget target) {
        LivingEntity user = standEntity.getUser();
        ActionTarget kept = target != null
                ? target.resolveEntityId(standEntity.level())
                : ActionTarget.EMPTY;
        return user != null
                && keepsTaskTargetType(standEntity, kept)
                && canGrab(user, kept.getEntity())
                && standEntity.canAttack((LivingEntity) kept.getEntity());
    }

    // 1.16 standTickPerform: Stand.lookAt(FEET, the target's position), on
    // every tick the task holds the target
    @Override
    public void rotateStandTowardsTarget(
            StandEntity standEntity,
            EntityActionInstance action,
            ActionTarget target) {
        Entity grabbed = target.getEntity();
        if (grabbed != null) {
            standEntity.lookAt(
                    EntityAnchorArgument.Anchor.FEET, grabbed.position());
        }
    }

    // 1.16 checkTarget: a living, alive entity within 7 blocks.
    public static boolean canGrab(
            LivingEntity user, @Nullable Entity entity) {
        return entity instanceof LivingEntity living
                && living.isAlive()
                && living != user
                && !(living instanceof StandEntity stand
                        && stand.getUser() == user)
                && living.distanceTo(user) <= GRAB_RANGE;
    }

    // 1.16 StandEntityAction.checkStandTarget -> canStandTargetEntity (no
    // friendlyFire): StandEntity.canAttack of the Stand onClick had
    // summoned before the check. It asks only the user, so with the Stand
    // still away the same rule is taken from the user.
    static boolean standMayAttack(LivingEntity user, LivingEntity entity) {
        StandEntity stand = StandUtil.getSummonedStand(user);
        if (stand != null) {
            return stand.canAttack(entity);
        }
        boolean canHarm = DamageUtil.isNotFriendlyFire(
                user, StandUtil.getStandUser(entity));
        if (canHarm && entity instanceof Animal) {
            canHarm = !entity.isPassengerOfSameVehicle(user);
            if (canHarm && entity instanceof TamableAnimal tamable) {
                LivingEntity owner = tamable.getOwner();
                canHarm = !(owner != null && owner == user);
            }
        }
        return canHarm;
    }

    // 1.16's mouse target for a Stand power (EntityStandType
    // .clientHitResult): the vanilla pick, behind a miss the summoned
    // Stand's precision ray, as the client keeps it in the Stand's aim; the
    // user's own pick while the Stand is away
    private static ActionTarget pressedAim(LivingEntity user) {
        StandEntity stand = StandUtil.getSummonedStand(user);
        ActionTarget standAim = stand != null
                ? aimOf(stand) : ActionTarget.EMPTY;
        return standAim.getType() != TargetType.EMPTY
                ? standAim : aimOf(user);
    }

    // The user's own client (HUD, its press check) holds no aim of its
    // user, only the Stand's comes back; there it reads the aim it sends,
    // as 1.16's client checks read the mouse target
    private static ActionTarget aimOf(LivingEntity aiming) {
        return LivingComponentAction.getAimTarget(aiming);
    }

    // 1.16 PowerBaseImpl.checkTarget ran StandEntityAction
    // .overrideVanillaMouseTarget before any other check: a pressed block
    // without a collision shape gives way to what the Stand's ray meets
    private ActionTarget aimedTarget(LivingEntity user, Level level) {
        return overridePressedTarget(user, pressedAim(user))
                .resolveEntityId(level);
    }

    @Nullable
    private LivingEntity findTarget(LivingEntity user, Level level) {
        ActionTarget target = aimedTarget(user, level);
        // 1.16 took only the entity pressed at: PlanetWavesGrab.checkTarget
        // and checkStandTarget refused any other, with no look for another
        if (target.getType() == TargetType.ENTITY) {
            return grabbable(user, target.getEntity())
                    ? (LivingEntity) target.getEntity() : null;
        }
        // 1.16 refused a press at a block (TargetRequirement.ENTITY), also
        // when the Stand's ray met nothing grabbable past one without a
        // collision shape
        if (pressedAim(user).getType() == TargetType.BLOCK) {
            return null;
        }
        // Pressed at nothing: the port's 7-block look line, which the owner
        // kept (2026-10-16), for an entity the grab may take
        target = HitResultUtil.clip(
                user.getEyePosition(),
                user.getLookAngle(),
                GRAB_RANGE,
                GRAB_RANGE,
                level,
                entity -> grabbable(user, entity),
                user,
                0,
                ClipContext.Block.OUTLINE);
        return target.getType() == TargetType.ENTITY
                        && grabbable(user, target.getEntity())
                ? (LivingEntity) target.getEntity()
                : null;
    }

    private static boolean grabbable(LivingEntity user, @Nullable Entity entity) {
        return canGrab(user, entity)
                && standMayAttack(user, (LivingEntity) entity);
    }

    public static final class GrabAction extends EntityActionInstance {
        // the entity holding the slowness, to free once the hold ends
        @Nullable private LivingEntity slowed;
        private float initialHealth;
        private boolean holding;

        public GrabAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 holdType(140) with no recovery: another Stand action pressed
        // during the grab replaced it at once (isCancelable), and any other
        // action of the power ended the hold (stopHeldAction(false))
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power = user != null ? StandPower.get(user) : null;
            if (user == null || power == null) {
                startRecovery();
                return;
            }
            StandUtil.playStandEntitySound(
                    stand, PlanetWavesAddon.PLANET_WAVES_GRAB, 1.0F, 1.0F);
            initialHealth = user.getHealth();
            holding = true;
            tickGrab(stand, user);
            // 1.16 StandEntityTask paid staminaCost(75) after standPerform
            // and ignored the result; a short bar just drops to 0
            StandAbilityStamina.consume(ability, power, STAMINA_COST, false);
        }

        @Override
        public void actionTick() {
            // 1.16 PowerBaseImpl.tickHeldAction counted holdType(140) from the press, through the summon lock
            if (holding && getPhase() == ActionPhase.PERFORM && !level().isClientSide()
                    && getFullTicksPassed() + getTicksHeldInSummonLock() >= MAX_HOLD_TICKS) {
                startRecovery();
                return;
            }
            if (holding
                    && getPhase() == ActionPhase.PERFORM
                    && !level().isClientSide()
                    && getPerformer() instanceof StandEntity stand
                    && getPowerUser() != null) {
                tickGrab(stand, getPowerUser());
            }
            // 1.16 ticked the task on both sides (StandEntity.tick), and the
            // client that controls the Stand by hand owns its position and
            // turn, so it pins the Stand itself
            else if (getPhase() == ActionPhase.PERFORM
                    && level().isClientSide()
                    && getPerformer() instanceof StandEntity stand
                    && stand.isManuallyControlled()
                    && stand.isControlledByLocalInstance()) {
                LivingEntity user = getPowerUser();
                Entity grabbed = grabbedEntity();
                if (user != null && canGrab(user, grabbed)) {
                    pinStand(stand, (LivingEntity) grabbed);
                }
            }
        }

        // 1.16 standTickPerform.
        private void tickGrab(StandEntity stand, LivingEntity user) {
            if (shouldLetGo()) {
                startRecovery();
                return;
            }
            LivingEntity grabbed = (LivingEntity) grabbedEntity();
            slowed = grabbed;
            grabbed.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    SLOWNESS_TICKS,
                    SLOWNESS_AMPLIFIER,
                    false,
                    false));
            pinStand(stand, grabbed);
            StandPower targetPower = StandPower.get(grabbed);
            if (targetPower != null) {
                targetPower.setLeapCooldown(LEAP_LOCK_TICKS);
            }
        }

        // 1.16 standTickPerform: the Stand in front of the target, turned
        // to its feet.
        private static void pinStand(StandEntity stand, LivingEntity grabbed) {
            Vec3 targetPos = grabbed.position();
            Vec3 standPos = targetPos.add(
                    grabbed.getLookAngle().scale(STAND_OFFSET));
            stand.setPos(standPos.x, standPos.y, standPos.z);
            // 1.16 standTickPerform turned the Stand whether or not it was manually controlled; the core's
            // task-target turn (rotateStandTowardsTarget) is skipped under manual control
            if (stand.isManuallyControlled()) {
                stand.lookAt(EntityAnchorArgument.Anchor.FEET, targetPos);
            }
        }

        // Lost 4 HP since the grab started, or the target is gone.
        boolean shouldLetGo() {
            LivingEntity user = getPowerUser();
            return holding
                    && (user == null
                            || user.getHealth()
                                    <= initialHealth - HEALTH_LOSS_TO_LET_GO
                            || !canGrab(user, grabbedEntity()));
        }

        // 1.16 task.getTarget(): the living entity the task holds
        // (targetRequirement ENTITY), which a next action pressed at
        // nothing takes over (StandEntity.setTask) and which setTaskTarget
        // can drop
        @Nullable
        private Entity grabbedEntity() {
            return getTaskTarget(level()).getEntity();
        }

        // 1.16 pwLetGo and stopHeldAction(true).
        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase != ActionPhase.PERFORM) {
                release();
            }
        }

        @Override
        public void onActionCleared(
                @Nullable EntityActionInstance newAction) {
            release();
            super.onActionCleared(newAction);
        }

        // Ends the hold once: frees the target and starts the cooldown.
        private void release() {
            if (!holding) {
                return;
            }
            holding = false;
            if (level().isClientSide()) {
                return;
            }
            if (slowed != null) {
                slowed.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                slowed = null;
            }
            LivingEntity user = getPowerUser();
            StandPower power = user != null ? StandPower.get(user) : null;
            if (power != null
                    && ability instanceof PlanetWavesGrabAbility grab) {
                grab.setCooldownOnUse(power);
            }
        }
    }
}
