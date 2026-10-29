package rotp.addons.killerqueenlion.entity;

import java.util.UUID;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class BubbleBombEntity
        extends KillerQueenBombProjectile {
    public BubbleBombEntity(
            EntityType<? extends BubbleBombEntity> type,
            Level level) {
        super(type, level);
    }

    public BubbleBombEntity(
            ServerPlayer owner,
            StandEntity stand,
            Level level) {
        super(
                KillerQueenLionAddon.BUBBLE_BOMB_ENTITY.get(),
                owner,
                stand,
                level);
    }

    @Override
    protected boolean isFlightControllerValid(
            ServerLevel level, UUID ownerUuid, UUID standUuid) {
        Entity controller = level.getEntity(ownerUuid);
        Entity projectileOwner = getOwner();
        return controller instanceof ServerPlayer player
                && player.isAlive()
                && !player.isRemoved()
                && player.level() == level
                && projectileOwner != null
                && ownerUuid.equals(projectileOwner.getUUID())
                && KillerQueenLionAddon.isKillerQueenUser(player);
    }
}
