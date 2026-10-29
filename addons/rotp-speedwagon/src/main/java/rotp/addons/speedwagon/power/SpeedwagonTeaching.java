package rotp.addons.speedwagon.power;

import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

import rotp.addons.speedwagon.SpeedwagonAddon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class SpeedwagonTeaching {
    private SpeedwagonTeaching() {}

    public static boolean requestFromPlayer(
            ServerPlayer learner, LivingEntity teacher) {
        PlayerPower learnerPower = PlayerPower.get(learner);
        SpeedwagonData teacherData = PlayerPower.getPowerData(
                teacher, SpeedwagonAddon.SPEEDWAGON)
                .orElse(null);
        if (learnerPower == null
                || learnerPower.hasPower()
                || teacherData == null) {
            return false;
        }
        return teacherData.addPendingLearner(
                teacher, learner);
    }

    public static boolean teachPendingPlayer(
            LivingEntity teacher, ServerPlayer learner) {
        SpeedwagonData teacherData = PlayerPower.getPowerData(
                teacher, SpeedwagonAddon.SPEEDWAGON)
                .orElse(null);
        if (teacherData == null
                || !teacherData.hasPendingLearner(
                        learner.getUUID())) {
            return false;
        }
        teacherData.removePendingLearner(
                teacher, learner.getUUID());
        return grantPower(learner);
    }

    public static boolean grantFromInstructor(
            ServerPlayer learner) {
        return grantPower(learner);
    }

    private static boolean grantPower(ServerPlayer learner) {
        PlayerPower power =
                PowerClass.PLAYER_POWER.attachGet(learner);
        if (power.hasPower()) {
            learner.displayClientMessage(
                    Component.translatable(
                            "rotp_sp.chat.message.cant_learn_sp"),
                    true);
            return false;
        }
        power.setPowerType(SpeedwagonAddon.SPEEDWAGON.get());
        learner.sendSystemMessage(
                Component.translatable(
                        "rotp_sp.chat.message.learnt_sp"));
        return true;
    }
}
