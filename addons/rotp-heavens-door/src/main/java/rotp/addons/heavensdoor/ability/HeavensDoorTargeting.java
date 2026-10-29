package rotp.addons.heavensdoor.ability;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.entity.HeavensDoorStandEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

public final class HeavensDoorTargeting {
    private HeavensDoorTargeting() {}

    public static LivingEntity activeUser(Power<?> context) {
        LivingEntity user = context != null ? context.getUser() : null;
        return user != null
                && HeavensDoorAddon.controller(user) != null
                ? user
                : null;
    }

    /**
     * 1.16's mouse target, which the client picked from its camera entity and sent with the press and on every held
     * tick (InputHandler.pickMouseTarget, EntityStandType.clientHitResult :122-151): the vanilla pick, a block by its
     * outline within the player's block reach or an entity within its entity reach (the user's synced aim); behind a
     * miss with the Stand out, the Stand's precision ray from the user's eyes at the user's reach. Under manual control
     * the camera is the Stand (StandUtil.java:160): its synced aim, its own pick and precision ray.
     */
    public static ActionTarget pressedAim(LivingEntity user) {
        Level level = user.level();
        StandEntity stand = StandUtil.getSummonedStand(user);
        LivingEntity aiming = stand != null && stand.isManuallyControlled()
                ? stand
                : user;
        ActionTarget target = syncedAim(user, aiming, level);
        if (target.getType() == ActionTarget.TargetType.EMPTY
                && aiming == user && stand != null) {
            // without the aim assist the strict crosshair at the same
            // reach, as the core's punches take it
            target = StandEntityPunchAbility.aimAssist(user)
                    ? StandPrecisionRay.trace(user, stand)
                    : StandPrecisionRay.crosshair(user, stand,
                            StandPrecisionRay.reach(user, stand), true);
            target = target.resolveEntityId(level);
        }
        return target;
    }

    // The aims only go from the user's client to the server, so that
    // client reads the ones it sends.
    private static ActionTarget syncedAim(
            LivingEntity user, LivingEntity aiming, Level level) {
        ActionTargetAim aim = level.isClientSide()
                        && user == ClientProxy.getClientPlayer()
                ? (aiming == user
                        ? ClientsideAim.playerAim
                        : ClientsideAim.standAim)
                : LivingComponentAction.getAim(aiming);
        ActionTarget target = aim != null ? aim.getTarget() : null;
        return target != null
                ? target.resolveEntityId(level)
                : ActionTarget.EMPTY;
    }

    public static LivingEntity livingTarget(ActionTarget target) {
        return target != null
                && target.getEntity() instanceof LivingEntity living
                && canTarget(living)
                        ? normalize(living)
                        : null;
    }

    /**
     * 1.16 StandEntity.canAttack asked of the user, for a press made while
     * the Stand is away (1.16 summoned it before checkStandTarget): no
     * friendly fire, not an animal riding with the user, not the user's own
     * tamed animal.
     */
    public static boolean standMayAttack(
            LivingEntity user, LivingEntity target) {
        if (target == user
                || !DamageUtil.isNotFriendlyFire(
                        user, StandUtil.getStandUser(target))) {
            return false;
        }
        return !(target instanceof Animal)
                || !target.isPassengerOfSameVehicle(user)
                        && !(target instanceof TamableAnimal tamed
                                && tamed.getOwner() == user);
    }

    public static LivingEntity normalize(LivingEntity target) {
        if (target instanceof StandEntity stand
                && stand.getUser() != null) {
            return stand.getUser();
        }
        return target;
    }

    private static boolean canTarget(Entity entity) {
        return entity instanceof LivingEntity living
                && living.isAlive()
                && !(living instanceof HeavensDoorStandEntity);
    }
}
