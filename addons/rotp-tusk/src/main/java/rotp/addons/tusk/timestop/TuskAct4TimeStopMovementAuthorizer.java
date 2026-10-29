package rotp.addons.tusk.timestop;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.entity.TuskEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class TuskAct4TimeStopMovementAuthorizer {
    public static final String REGISTRATION_PATH =
            "act_4_manifested_stand_time_stop_movement";

    private TuskAct4TimeStopMovementAuthorizer() {}

    public static boolean canMoveInStoppedTime(Entity entity) {
        if (!(entity instanceof TuskEntity tusk)
                || !tusk.isAddedToLevel()
                || tusk.isRemoved()) {
            return false;
        }

        LivingEntity user = tusk.getUser();
        if (user == null || !TuskAddon.isAct4User(user)) {
            return false;
        }

        StandPower power = StandPower.get(user);
        return power != null
                && power.getSummonedStandEntity() == tusk
                && TuskAddon.id("tusk_act_4").equals(
                        tusk.getStandType());
    }
}
