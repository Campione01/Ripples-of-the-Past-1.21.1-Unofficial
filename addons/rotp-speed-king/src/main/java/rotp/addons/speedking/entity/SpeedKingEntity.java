package rotp.addons.speedking.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.speedking.SpeedKingAddon;
import rotp.addons.speedking.SpeedKingService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class SpeedKingEntity extends StandEntity {
    public SpeedKingEntity(
            EntityType<? extends SpeedKingEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity user = getUser();
        if (!level().isClientSide()
                && user instanceof ServerPlayer player
                && SpeedKingAddon.isUser(player)) {
            SpeedKingService.tickHeatTrap(player, this);
        }
    }
}
