package rotp.addons.madeinheaven.ability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class TimeAuraAbility extends StandEntityAbility {
    public static final int HOLD_TICKS = 1000;
    public static final int COOLDOWN_TICKS = 50;
    public static final float START_STAMINA = 150.0F;
    public static final float TICK_STAMINA = 1.0F;
    public static final double RANGE = 5.0D;

    private static final Vec3[] TELEPORT_OFFSETS = {
        new Vec3(2.0D, 0.0D, 0.0D),
        new Vec3(-2.0D, 0.0D, 0.0D),
        new Vec3(0.0D, 0.0D, 2.0D),
        new Vec3(0.0D, 0.0D, -2.0D),
        new Vec3(1.4D, 0.0D, 1.4D),
        new Vec3(-1.4D, 0.0D, 1.4D),
        new Vec3(1.4D, 0.0D, -1.4D),
        new Vec3(-1.4D, 0.0D, -1.4D)
    };

    public TimeAuraAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, TimeAuraAction::new);
        partsRequired(StandPart.ARMS);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, HOLD_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(COOLDOWN_TICKS);
    }

    // 1.16 tpaura was holdType(1000): the whole aura is the hold, so a
    // release from any bind still ends it.
    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.PERFORM
                || super.isActionHeld(action);
    }

    // 1.16's client sent no press of a held action while the power held
    // another (ActionsOverlayGui.onActionClick): the press waits in the
    // input buffer until that hold ends.
    @Override
    public boolean isHoldAction() {
        return true;
    }

    // 1.16 started a hold-only action's cooldown when the hold stopped,
    // not when the key went down (see TimeAuraAction.onActionCleared).
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    public static final class TimeAuraAction
            extends EntityActionInstance {
        public TimeAuraAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 Tpaura had no stamina condition: StandEntityTask took the
        // 150 at perform start and the 1 every perform tick, and the aura
        // went on whatever consumeStamina returned.
        @Override
        public void actionPerformStart() {
            LivingEntity user = getPowerUser();
            if (user == null) {
                stopAndSynchronize();
                return;
            }
            if (!level().isClientSide()) {
                StandAbilityStamina.consume(
                        ability,
                        StandPower.get(user),
                        START_STAMINA,
                        false);
            }
        }

        // However the aura ends (release, 1000 ticks, another Stand action,
        // the Stand leaving), the cooldown starts then, as 1.16
        // stopHeldAction did.
        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            super.onActionCleared(newAction);
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (power != null
                    && !level().isClientSide()
                    && ability instanceof TimeAuraAbility aura) {
                aura.setCooldownOnUse(power);
            }
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || level().isClientSide()
                    || getPowerUser() == null) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    TICK_STAMINA,
                    true);

            user.addEffect(new MobEffectInstance(
                    // 1.16 Tpaura: field_76429_m = Resistance.
                    MobEffects.DAMAGE_RESISTANCE,
                    100,
                    2,
                    false,
                    false));
            List<LivingEntity> enemies =
                    new ArrayList<>(
                            level().getEntitiesOfClass(
                                    LivingEntity.class,
                                    user.getBoundingBox()
                                            .inflate(
                                                    RANGE,
                                                    4.0D,
                                                    RANGE)
                                            .expandTowards(
                                                    0.0D,
                                                    6.0D,
                                                    0.0D),
                                    target -> target != user
                                            && target.isAlive()
                                            && !target.isAlliedTo(
                                                    user)));
            if (enemies.isEmpty()) {
                return;
            }
            enemies.sort(
                    Comparator.comparing(
                            target -> target.hasEffect(
                                    ModStatusEffects.BLEEDING)));
            int unbled = 0;
            while (unbled < enemies.size()
                    && !enemies.get(unbled).hasEffect(
                            ModStatusEffects.BLEEDING)) {
                unbled++;
            }
            int poolSize = unbled > 0
                    ? unbled
                    : enemies.size();
            LivingEntity target = enemies.get(
                    user.getRandom().nextInt(poolSize));
            teleportAroundTarget(level(), user, target);
            target.hurt(
                    user.damageSources().mobAttack(user), 7.0F);
            applyBleeding(target);
            for (LivingEntity nearby : enemies) {
                if (nearby != target
                        && nearby.distanceToSqr(target) < 9.0D) {
                    nearby.hurt(
                            user.damageSources().mobAttack(user),
                            5.0F);
                    applyBleeding(nearby);
                }
            }
        }

        @Override
        public void onButtonStopHold() {
            stopAndSynchronize();
        }

        // 1.16 StandEntityAction.isCancelable: a hold-only task with no
        // recovery gave way to any other Stand action at once. isOfAbility,
        // not identity: a client's action holds the base-moveset instance of
        // its ability, not its user's own.
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return !isOfAbility(cancellingAbility);
        }

        // 1.16 tpaura (Tpaura) was a holdType(1000) Stand action
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        private void stopAndSynchronize() {
            forceStop();
            if (!level().isClientSide()) {
                syncPhaseChanges();
            }
        }

        private static void applyBleeding(
                LivingEntity target) {
            if (!target.hasEffect(ModStatusEffects.BLEEDING)) {
                target.addEffect(new MobEffectInstance(
                        ModStatusEffects.BLEEDING,
                        500,
                        2,
                        false,
                        false));
            }
        }

        private static void teleportAroundTarget(
                Level level,
                LivingEntity user,
                LivingEntity target) {
            int start = user.getRandom().nextInt(
                    TELEPORT_OFFSETS.length);
            for (int index = 0;
                    index < TELEPORT_OFFSETS.length;
                    index++) {
                Vec3 base = target.position().add(
                        TELEPORT_OFFSETS[
                                (start + index)
                                        % TELEPORT_OFFSETS.length]);
                for (int y = -1; y <= 2; y++) {
                    Vec3 candidate =
                            base.add(0.0D, y, 0.0D);
                    if (isSafe(level, user, candidate)) {
                        user.teleportTo(
                                candidate.x,
                                candidate.y,
                                candidate.z);
                        return;
                    }
                }
            }
        }

        private static boolean isSafe(
                Level level,
                LivingEntity user,
                Vec3 candidate) {
            BlockPos pos = BlockPos.containing(candidate);
            if (!level.hasChunkAt(pos)
                    || level.getBlockState(pos.below())
                            .getCollisionShape(
                                    level, pos.below())
                            .isEmpty()) {
                return false;
            }
            Vec3 movement =
                    candidate.subtract(user.position());
            return level.noCollision(
                    user,
                    user.getBoundingBox().move(movement));
        }
    }
}
