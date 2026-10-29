package rotp.addons.kraftwork.ability;

import java.util.OptionalInt;

import javax.annotation.Nullable;

import rotp.core.api.client.render.AbilitySelectionVisualQuery;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.kraftwork.KraftWorkAddon;
import rotp.addons.kraftwork.state.KraftWorkLockService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class KraftWorkHeavyPunchAbility
        extends StandEntityHeavyPunchAbility {
    public KraftWorkHeavyPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = KraftWorkHeavyPunch::new;
    }

    // 1.16 KraftWorkHeavyPunch.getRecoveryFollowup: Lock Equipment is an
    // opt-in press during the punch, applied on the last recovery tick.
    // Not final: the combo punch finisher extends it, as in 1.16.
    public static class KraftWorkHeavyPunch
            extends StandEntityHeavyPunch {
        private boolean lockArmorQueued;
        private boolean lockArmorApplied;
        private int hitTargetId = -1;

        public KraftWorkHeavyPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            super.actionTick();
            tryApplyQueuedLockArmor();
        }

        // 1.16 canSetFollowUp: once per punch, on a target the lock takes
        public boolean canAcceptLockArmorFollowup() {
            return !lockArmorQueued
                    && !lockArmorApplied
                    && !isGrabVariation()
                    && KraftWorkLockService.canLockArmorAfterHeavy(
                            getFollowupTarget());
        }

        public boolean queueLockArmor() {
            if (!canAcceptLockArmorFollowup()) {
                return false;
            }
            lockArmorQueued = true;
            return true;
        }

        @Override
        protected void afterHeavyPunchHit(
                StandEntity stand,
                LivingEntity target,
                DamageSource damageSource,
                float damageAmount,
                boolean hurt) {
            super.afterHeavyPunchHit(
                    stand,
                    target,
                    damageSource,
                    damageAmount,
                    hurt);
            hitTargetId = target.getId();
        }

        // 1.16 standTickRecovery: fires once, at task.getTicksLeft() <= 1
        private void tryApplyQueuedLockArmor() {
            if (!lockArmorQueued
                    || lockArmorApplied
                    || getPhase() != ActionPhase.RECOVERY
                    || getPhaseTicksLeft() > 1.0F
                    || level().isClientSide()) {
                return;
            }
            lockArmorApplied = true;
            LivingEntity user = getPowerUser();
            LivingEntity target = getLockArmorTarget();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null || target == null || power == null) {
                return;
            }
            int locked = KraftWorkLockService.lockArmorAfterHeavy(
                    user, target, lastDamageDealtToLiving);
            if (locked > 0) {
                power.consumeStamina(5.0F * locked);
            }
        }

        // Final lock: the hit target, else the task target, unranged
        @Nullable
        private LivingEntity getLockArmorTarget() {
            if (hitTargetId >= 0
                    && level().getEntity(hitTargetId)
                            instanceof LivingEntity hit
                    && hit.isAlive()) {
                return hit;
            }
            return isPastWindup()
                    ? getPerformTarget()
                    : getWindupTarget();
        }

        // 1.16 canSetFollowUp: from perform on, the punch target both
        // sides set at perform start, within 8 blocks (4 unseen)
        @Nullable
        private LivingEntity getFollowupTarget() {
            if (!isPastWindup()) {
                return getWindupTarget();
            }
            LivingEntity target = getPerformTarget();
            return target != null
                    && getPerformer() instanceof StandEntity stand
                    && isInFollowupRange(stand, target)
                            ? target
                            : null;
        }

        private boolean isPastWindup() {
            ActionPhase phase = getPhase();
            return phase == ActionPhase.PERFORM
                    || phase == ActionPhase.RECOVERY;
        }

        @Nullable
        private LivingEntity getPerformTarget() {
            Entity target = getActionTargetSnapshot(level())
                    .getMainEntity();
            return target instanceof LivingEntity living
                    && living.isAlive() ? living : null;
        }

        @Nullable
        private LivingEntity getWindupTarget() {
            if (getPerformer() instanceof StandEntity stand) {
                Entity target = StandEntityPunchAbility
                        .getFreshPunchTarget(
                                stand,
                                getActionTargetSnapshot(level()))
                        .getMainEntity();
                return target instanceof LivingEntity living
                        && living.isAlive() ? living : null;
            }
            return null;
        }

        // 1.16 Action.checkRangeAndTarget + JojoModUtil.getDistance
        private static boolean isInFollowupRange(
                StandEntity stand, LivingEntity target) {
            double range = stand.hasLineOfSight(target) ? 8.0D : 4.0D;
            Vec3 eye = stand.getEyePosition();
            AABB box = target.getBoundingBox();
            if (box.contains(eye)) {
                return true;
            }
            double height = stand.getBbHeight();
            Vec3 aim = new Vec3(
                    Mth.lerp(0.5D, box.minX, box.maxX),
                    Mth.lerp(
                            height == 0.0D
                                    ? 0.0D
                                    : stand.getEyeHeight() / height,
                            box.minY,
                            box.maxY),
                    Mth.lerp(0.5D, box.minZ, box.maxZ));
            return box.clip(eye, aim)
                    .map(hit -> eye.distanceTo(hit)
                            - stand.getBbWidth() / 2.0D <= range)
                    .orElse(true);
        }
    }

    // 1.16 KraftWorkLockArmor: while a Kraft Work heavy punch runs on an
    // equipped target the heavy slot reads Lock Equipment, and a press
    // queues the lock on that punch.
    public static final class LockArmorFollowup extends Ability {
        public LockArmorFollowup(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId);
            isSubAbility = true;
            // 1.16 isUnlocked: resolve level >= 1
            resolveLevelToUnlock(1);
        }

        @Override
        public Ability replaceWithSubAbility(
                Power<?> context, AvailableAbilities abilities) {
            // 1.16 StandAction.canSetFollowUp -> checkRequirements
            // :263-265 gave a stunned performer no follow-up
            // (KraftWorkLockArmor does not ignore the stun)
            if (abilities != null
                    && followupPunch(PowerClass.STAND.cast(context))
                            != null
                    && checkPerformerStun(context).isPositive()) {
                abilities.replaceOtherAbilityWith(
                        context, KraftWorkAddon.HEAVY_PUNCH, this);
            }
            return super.replaceWithSubAbility(context, abilities);
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            ConditionCheck stun = checkPerformerStun(context);
            if (!stun.isPositive()) {
                return stun;
            }
            return ConditionCheck.noMessage(
                    followupPunch(PowerClass.STAND.cast(context)) != null);
        }

        @Override
        public HeldInput onKeyPress(
                Level level,
                LivingEntity user,
                FriendlyByteBuf extraClientInput,
                InputMethod inputMethod,
                float clickHoldResolveTime,
                BufferingState bufferingState) {
            StandPower standPower = StandPower.get(user);
            KraftWorkHeavyPunch punch = followupPunch(standPower);
            // Queued on both sides so the client slot turns back at once
            if (punch != null
                    && isAbilityAvailable(standPower)
                    && punch.queueLockArmor()
                    && !level.isClientSide()) {
                bufferingState.setActionSuccess();
            }
            return null;
        }

        // 1.16 StandEntityActionModifier.greenSelection: a selected Lock Equipment was framed green while its
        // conditions held
        public static OptionalInt selectionTint(AbilitySelectionVisualQuery query) {
            return query.ability() instanceof LockArmorFollowup
                    && query.conditionCheck().isPositive()
                    ? OptionalInt.of(SELECTION_GREEN) : OptionalInt.empty();
        }

        private static final int SELECTION_GREEN = 0xFF00FF00;

        @Nullable
        public static KraftWorkHeavyPunch followupPunch(
                @Nullable StandPower standPower) {
            StandEntity stand = standPower != null
                    ? standPower.getSummonedStandEntity()
                    : null;
            return stand != null
                    && stand.getCurStandAction()
                            instanceof KraftWorkHeavyPunch punch
                    && punch.canAcceptLockArmorFollowup()
                            ? punch
                            : null;
        }
    }
}
