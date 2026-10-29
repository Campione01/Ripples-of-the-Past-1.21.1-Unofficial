package rotp.addons.killerqueen.ability;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
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
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.KillerQueenPhase1Spec;
import rotp.addons.killerqueen.state.KillerQueenStateService;
import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class PrimaryBombAbility extends StandEntityAbility {
    public static final int WINDUP_TICKS = 10;
    public static final int PERFORM_TICKS = 1;
    public static final int RECOVERY_TICKS = 40;
    public static final int COOLDOWN_TICKS = 60;

    public PrimaryBombAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, PrimaryBombAction::new);
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
        cooldown(COOLDOWN_TICKS);
        resolveLevelToUnlock(
                KillerQueenPhase1Spec.PRIMARY_BOMB_RESOLVE_LEVEL);
        partsRequired(StandPart.ARMS);
        noFinisherBarDecay = true;
        // 1.16 kq_entitybomb: a StandEntityLightAttack.Builder, which
        // brings out the main arm only
        standAutoSummonMode(AutoSummonMode.MAIN_ARM);
    }

    // 1.16 PunchBomb.getTargetRequirement ENTITY: PowerBaseImpl.checkTarget
    // refused the press, with no message, unless the client's target was an
    // entity; Action.checkRangeAndTarget then asked for the range
    // ("target_too_far"), another entity than the user and, with the Stand
    // out, a living one it may attack.
    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        LivingEntity user = context != null ? context.getUser() : null;
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        ConditionCheck target = checkPressedEntity(context, user);
        return target.isPositive()
                ? super.checkSpecificConditions(context)
                : target;
    }

    private ConditionCheck checkPressedEntity(
            Power<?> context, LivingEntity user) {
        Level level = user.level();
        StandEntity stand = StandUtil.getSummonedStand(context);
        // 1.16 PowerBaseImpl.checkTarget ran overrideVanillaMouseTarget
        // first: a pressed torch or grass gave way to what the
        // performer's collision ray met
        ActionTarget pressed = overridePressedTarget(user,
                pressedTarget(level, user, stand))
                .resolveEntityId(level);
        Entity entity = pressed.getType() == ActionTarget.TargetType.ENTITY
                ? pressed.getMainEntity()
                : null;
        if (entity == null || entity.isRemoved()) {
            return ConditionCheck.NEGATIVE;
        }
        LivingEntity performer = stand != null ? stand : user;
        if (!ActionTargetRange.isEntityWithinRange(
                performer,
                pressed.getEntity(),
                ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        if (entity == user) {
            return ConditionCheck.NEGATIVE;
        }
        return stand == null
                || entity instanceof LivingEntity living
                        && stand.canAttack(living)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    // 1.16 EntityStandType.clientHitResult: the vanilla pick, which is the
    // user's synced aim, or when that met nothing and the Stand is out its
    // precision ray from the user's eyes. 1.16's ray met entities through
    // blocks; the owner's wall rule stops it unless the skill is marked.
    private ActionTarget pressedTarget(
            Level level, LivingEntity user, StandEntity stand) {
        LivingEntity aiming = stand != null && stand.isManuallyControlled()
                ? stand : user;
        // The aims only go from the user's client to the server, so that
        // client reads the aims it sends.
        ActionTarget target;
        if (level.isClientSide() && user == ClientProxy.getClientPlayer()) {
            target = (aiming == user
                    ? ClientsideAim.playerAim
                    : ClientsideAim.standAim).getTarget();
        } else {
            var aim = LivingComponentAction.getAim(aiming);
            target = aim != null && aim.getTarget() != null
                    ? aim.getTarget() : ActionTarget.EMPTY;
        }
        if (target.getType() == ActionTarget.TargetType.EMPTY
                && aiming == user && stand != null) {
            double reach = StandPrecisionRay.reach(user, stand);
            boolean wallsHideEntities =
                    StandSkillWalls.wallsHideEntities(level, this);
            target = StandEntityPunchAbility.aimAssist(user)
                    ? StandPrecisionRay.trace(user, stand, reach, 0.0D,
                            true, wallsHideEntities)
                    : StandPrecisionRay.crosshair(user, stand, reach,
                            true, wallsHideEntities);
        }
        return target;
    }
    // 1.16 PunchBomb: the ENTITY requirement passes an entity and
    // StandEntityLightAttack.standKeepsTarget any other target
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return true;
    }

    // 1.16 PunchBomb.stopOnHeavyAttack: a heavy punch that hurt the Stand
    // sent the plant into its recovery, so no bomb was planted
    @Override
    public boolean stopsOnHeavyAttack(EntityActionInstance action) {
        return true;
    }

    // 1.16 StandEntityLightAttack.lockOnTargetPosition: the front offset
    // stays along the user's view
    @Override
    public boolean lockOnTargetPosition(StandPower standPower,
            StandEntity standEntity, EntityActionInstance action) {
        return false;
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        KillerQueenLightAttack.setPhases(this, action, level, performer,
                KillerQueenLightAttack.Windup.HEAVY);
        // 1.16 kq_entitybomb .shout(USER_KQ): a user voice line, silent while sneaking
        if (!level.isClientSide() && powerUser != null) {
            sayShout(powerUser, KillerQueenAddon.USER_KIRA);
        }
    }

    // 1.16 PunchBomb.replaceAction: while the bombed entity is within 16
    // blocks, the slot shows kq_entity_explo in place of the plant
    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        if (!super.isAbilityAvailable(context)) {
            return false;
        }
        Ability detonate = context.getMoveset().getAbility(
                KillerQueenAddon.PRIMARY_BOMB_DETONATE_NAME);
        return detonate == null || !detonate.isAbilityAvailable(context);
    }

    static Player activePlayer(Power<?> context) {
        return context != null
                && context.getUser() instanceof Player player
                && KillerQueenAddon.isKillerQueenUser(player)
                ? player
                : null;
    }

    public static final class PrimaryBombAction
            extends EntityActionInstance {
        public PrimaryBombAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 PunchBomb kept the light attack's swingSound and standTickPerform
        private final KillerQueenLightAttack.SwingSound swingSound =
                new KillerQueenLightAttack.SwingSound();

        @Override
        public void actionTick() {
            super.actionTick();
            swingSound.tick(this);
        }

        // 1.16 StandEntityLightAttack: any action, this one too, replaced it
        // in its recovery
        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return KillerQueenLightAttack.givesWay(this);
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (!(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            // 1.16 PunchBomb.onTaskSet: an arms-only Stand whose last swing
            // was the off hand shows its main arm alone
            if (!level().isClientSide() && stand.isArmsOnlyMode()
                    && stand.swingingArm == InteractionHand.OFF_HAND) {
                stand.setArmsOnlyMode(true, false);
            }
            ActionTarget target = captureActionTargetFromAim(stand);
            setStandFrontOffsetFromTarget(
                    stand,
                    target,
                    Math.min(0.5D, stand.getEffectiveRange()),
                    Math.min(2.0D, stand.getMaxRange()));
            keepStandAimedAtTarget(target);
            aimAs = AimingEntity.STAND;
        }

        // 1.16 StandEntityLightAttack.getOffsetFromUser measured the front
        // offset to the task's target, one taken over too
        @Override
        protected void onTaskTargetInherited(ActionTarget target) {
            super.onTaskTargetInherited(target);
            if (getPerformer() instanceof StandEntity stand) {
                setStandFrontOffsetFromTarget(
                        stand,
                        target,
                        Math.min(0.5D, stand.getEffectiveRange()),
                        Math.min(2.0D, stand.getMaxRange()));
            }
        }

        // 1.16 PunchBomb had no getOffsetFromUser of its own: StandEntity.updatePosition asked the light attack's
        // front3dOffset on every tick
        @Override
        public void updateStandOffset(StandEntity stand) {
            KillerQueenLightAttack.refreshAtFront(this, stand);
        }

        // 1.16 StandEntity.setTaskTarget(null) dropped the one target the task
        // held; the kept target set beside the task target goes with it
        @Override
        protected void storeTaskTarget(@Nullable ActionTarget target) {
            super.storeTaskTarget(target);
            if (target == null) {
                setActionTargetSnapshot(null);
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer() instanceof StandEntity stand)
                    || !(getPowerUser()
                            instanceof ServerPlayer user)) {
                return;
            }
            // 1.16 StandEntityLightAttack.standPerform: StandEntity.punch at
            // the task's target, one taken over from the previous task too
            ActionTarget freshTarget =
                    StandEntityPunchAbility.getFreshPunchTarget(
                            stand,
                            getTaskTarget(level()));
            // 1.16 StandEntity.punch -> aimWithThisOrUser turned the Stand
            // toward what the strike aimed at, just before the hit
            stand.turnTowardsStrike(StandEntityPunchAbility.strikeAim(
                    stand,
                    freshTarget,
                    StandEntityPunchAbility.CrosshairRule.BLOCK_OR_ENTITY));
            setActionTargetSnapshot(freshTarget);
            // 1.16 StandEntity.punch made the strike's target the task's
            // (setTaskTarget), the one a next action pressed at nothing took
            // over
            setTaskTarget(freshTarget);
            Entity entity = freshTarget.getMainEntity();
            if (entity instanceof LivingEntity living
                    && living != user
                    && !(living instanceof StandEntity)
                    && living.isAlive()
                    && JojoModUtil.canHarm(user, living)) {
                // 1.16 PunchBomb.punchEntity stored the bomb before the hit
                // ran, so a blocked or refused hit still planted it; marked
                // first because a punch that kills leaves no living target
                KillerQueenStateService.markPrimaryBomb(user, living);
                DamageSource damageSource = makePunchDamageSource();
                if (standEntityAttack(
                        stand, living, damageSource, 1.0F)) {
                    StandUtil.playStandEntitySound(
                            stand,
                            KillerQueenAddon.KILLER_QUEEN_PUNCH,
                            1.0F,
                            1.0F);
                }
            }
            // 1.16 PunchBomb had no stamina condition: the Stand task
            // pays the cost after standPerform and a short bar drops to 0
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    KillerQueenPhase1Spec.PRIMARY_BOMB_MARK_STAMINA,
                    false);
        }
    }
}
