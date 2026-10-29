package rotp.addons.killerqueen.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.killerqueen.KillerQueenAddon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class SnowBombEntity
        extends KillerQueenBombProjectile {
    public SnowBombEntity(
            EntityType<? extends SnowBombEntity> type,
            Level level) {
        super(type, level);
    }

    public SnowBombEntity(
            ServerPlayer owner,
            StandEntity stand,
            Level level) {
        super(
                KillerQueenAddon.SNOW_BOMB_ENTITY.get(),
                owner,
                stand,
                level);
    }
}
