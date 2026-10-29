package rotp.addons.killerqueen.state;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.killerqueen.KillerQueenAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class KillerQueenAuthority {
    private KillerQueenAuthority() {}

    public static boolean validController(
            LivingEntity owner, StandEntity stand) {
        if (owner == null
                || stand == null
                || !owner.isAlive()
                || !stand.isAlive()
                || stand.isRemoved()
                || stand.getUser() != owner) {
            return false;
        }
        StandPower power = StandPower.get(owner);
        return power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType()
                        == KillerQueenAddon.KILLER_QUEEN.get()
                && power.getSummonedStandEntity() == stand;
    }

    public static boolean validController(
            ServerLevel level,
            UUID ownerUuid,
            UUID standUuid) {
        if (level == null
                || ownerUuid == null
                || standUuid == null) {
            return false;
        }
        Entity owner = level.getEntity(ownerUuid);
        Entity stand = level.getEntity(standUuid);
        return owner instanceof LivingEntity living
                && stand instanceof StandEntity standEntity
                && validController(living, standEntity);
    }

    public static boolean validSheerHeartLink(
            ServerLevel level,
            UUID ownerUuid,
            UUID sheerHeartUuid) {
        if (level == null
                || ownerUuid == null
                || sheerHeartUuid == null) {
            return false;
        }
        Entity entity = level.getEntity(ownerUuid);
        if (!(entity instanceof ServerPlayer owner)
                || !owner.isAlive()
                || !KillerQueenAddon.isKillerQueenUser(owner)
                || !owner.hasData(
                        KillerQueenAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        KillerQueenState state = KillerQueenAddon.state(owner);
        return sheerHeartUuid.equals(state.sheerHeartUuid())
                && level.dimension().location().equals(
                        state.sheerHeartDimension());
    }

    @Nullable
    public static StandEntity currentStand(LivingEntity owner) {
        StandPower power = owner != null
                ? StandPower.get(owner)
                : null;
        return power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType()
                        == KillerQueenAddon.KILLER_QUEEN.get()
                && power.getSummonedStandEntity()
                        instanceof StandEntity stand
                && validController(owner, stand)
                        ? stand
                        : null;
    }
}
