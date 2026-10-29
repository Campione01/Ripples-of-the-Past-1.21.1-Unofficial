package rotp.addons.aquanecklace.ability;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

final class AquaNecklaceTargeting {
    private AquaNecklaceTargeting() {}

    // 1.16 took the user's crosshair target (the Stand's under manual
    // control); the Stand's aim is that target. The server has it from the
    // user's client, which reads the aim it sends.
    static ActionTarget standAim(StandEntity stand, Level level) {
        if (level.isClientSide()) {
            return stand.getUser() != null
                            && stand.getUser() == ClientProxy.getClientPlayer()
                    ? ClientsideAim.standAim.getTarget()
                            .resolveEntityId(level)
                    : ActionTarget.EMPTY;
        }
        return aimOf(stand, level);
    }

    // with the power inactive, EntityStandType.clientHitResult sent the
    // vanilla pick alone
    static ActionTarget userPick(LivingEntity user, Level level) {
        if (level.isClientSide()) {
            return user == ClientProxy.getClientPlayer()
                    ? ClientsideAim.playerAim.getTarget()
                            .resolveEntityId(level)
                    : ActionTarget.EMPTY;
        }
        return aimOf(user, level);
    }

    // StandEntity.canAttack read of the user, as a Stand summoned for the
    // press read it: no friendly fire, not an animal sharing the user's
    // vehicle, not the user's own tamed animal
    static boolean userMayAttack(LivingEntity user, LivingEntity target) {
        if (!DamageUtil.isNotFriendlyFire(
                user, StandUtil.getStandUser(target))) {
            return false;
        }
        return !(target instanceof Animal)
                || !target.isPassengerOfSameVehicle(user)
                        && !(target instanceof TamableAnimal tamed
                                && tamed.getOwner() == user);
    }

    private static ActionTarget aimOf(LivingEntity aiming, Level level) {
        var aim = LivingComponentAction.getAim(aiming);
        ActionTarget target = aim != null ? aim.getTarget() : null;
        return target != null
                ? target.resolveEntityId(level)
                : ActionTarget.EMPTY;
    }
}
