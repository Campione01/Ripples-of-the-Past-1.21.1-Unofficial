package rotp.addons.lovers.ability;

import javax.annotation.Nullable;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

final class LoversTargeting {
    private LoversTargeting() {}

    record Pressed(@Nullable LivingEntity target, ConditionCheck check) {}

    // 1.16 IntoEntity (TargetRequirement ENTITY) through
    // PowerBaseImpl.checkTarget: the press took the crosshair target the
    // client sent (EntityStandType.clientHitResult: the vanilla pick, or
    // after a miss the Stand's precision ray), a pressed block without a
    // collision shape giving way to what the Stand's collision ray met
    // (overrideVanillaMouseTarget). No other entity was looked for. With
    // Lovers away the client sent its vanilla pick alone, and
    // StandEntityAction.onClick summoned Lovers (AutoSummonMode FULL) before
    // that check: the press is judged as the new Stand at its user's side
    // would judge it, and the core summons it.
    static Pressed pressTarget(
            EntityActionType ability,
            LivingEntity user,
            @Nullable StandEntity stand,
            Level level) {
        if (user == null) {
            return new Pressed(null, ConditionCheck.NEGATIVE);
        }
        ActionTarget pressed = stand != null
                ? mouseTarget(user, stand, level)
                : userPick(user, level);
        if (ability instanceof StandEntityAbility standAbility) {
            pressed = standAbility.overridePressedTarget(user, pressed);
        }
        return judge(user, stand, pressed, level);
    }

    @Nullable
    static LivingEntity findLookTarget(
            EntityActionType ability,
            LivingEntity user,
            @Nullable StandEntity stand,
            Level level) {
        return pressTarget(ability, user, stand, level).target();
    }

    // a target the action already holds, by the press's rules
    @Nullable
    static LivingEntity takenTarget(
            LivingEntity user,
            @Nullable StandEntity stand,
            ActionTarget target,
            Level level) {
        return user != null && stand != null
                ? judge(user, stand, target, level).target()
                : null;
    }

    // Anything but an entity was refused silently; an entity past 8 blocks of
    // the Stand, 4 without a line of sight, with "target_too_far"
    // (Action.checkRangeAndTarget); then StandEntityAction.checkStandTarget
    // -> StandEntity.canAttack refused the user's own pet, an animal sharing
    // its vehicle or a teammate without friendly fire silently. A Stand
    // summoned for the press stood at its user, so with none out both are
    // asked of the user.
    private static Pressed judge(
            LivingEntity user,
            @Nullable StandEntity stand,
            @Nullable ActionTarget target,
            Level level) {
        Entity entity = target != null
                && target.getType() == ActionTarget.TargetType.ENTITY
                && !target.isEmpty(level)
                ? target.getMainEntity()
                : null;
        if (entity == null || entity == user || entity == stand) {
            return new Pressed(null, ConditionCheck.NEGATIVE);
        }
        if (!ActionTargetRange.isEntityWithinRange(
                stand != null ? stand : user,
                entity,
                ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)) {
            return new Pressed(
                    null, ConditionCheck.createNegative("target_too_far"));
        }
        return entity instanceof LivingEntity living
                && living.isAlive()
                && !living.isRemoved()
                && (stand != null
                        ? stand.canAttack(living)
                        : userMayAttack(user, living))
                ? new Pressed(living, ConditionCheck.POSITIVE)
                : new Pressed(null, ConditionCheck.NEGATIVE);
    }

    // StandEntity.canAttack read of the user, as a Stand summoned for the
    // press read it: no friendly fire, not an animal sharing the user's
    // vehicle, not the user's own tamed animal
    private static boolean userMayAttack(
            LivingEntity user, LivingEntity target) {
        if (!DamageUtil.isNotFriendlyFire(
                user, StandUtil.getStandUser(target))) {
            return false;
        }
        return !(target instanceof Animal)
                || !target.isPassengerOfSameVehicle(user)
                        && !(target instanceof TamableAnimal tamed
                                && tamed.getOwner() == user);
    }

    // The server has the user's vanilla pick and, for a miss, the Stand's
    // aim; the user's own client reads the Stand aim it sends, which holds
    // both. A pick of the Stand itself was a miss.
    private static ActionTarget mouseTarget(
            LivingEntity user, StandEntity stand, Level level) {
        if (level.isClientSide()) {
            return user == ClientProxy.getClientPlayer()
                    ? ClientsideAim.standAim.getTarget()
                            .resolveEntityId(level)
                    : ActionTarget.EMPTY;
        }
        ActionTarget userAim = aimOf(user, level);
        if (!userAim.isEmpty(level)) {
            return userAim.getType() != ActionTarget.TargetType.ENTITY
                            || userAim.getEntity() != stand
                    ? userAim
                    : ActionTarget.EMPTY;
        }
        return aimOf(stand, level);
    }

    // with the power inactive, EntityStandType.clientHitResult sent the
    // vanilla pick alone
    private static ActionTarget userPick(LivingEntity user, Level level) {
        if (level.isClientSide()) {
            return user == ClientProxy.getClientPlayer()
                    ? ClientsideAim.playerAim.getTarget()
                            .resolveEntityId(level)
                    : ActionTarget.EMPTY;
        }
        return aimOf(user, level);
    }

    private static ActionTarget aimOf(LivingEntity aiming, Level level) {
        var aim = LivingComponentAction.getAim(aiming);
        if (aim == null || aim.getTarget() == null) {
            return ActionTarget.EMPTY;
        }
        return aim.getTarget().resolveEntityId(level);
    }
}
