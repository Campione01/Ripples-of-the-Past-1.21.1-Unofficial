package rotp.addons.jumpingjackflash.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class JumpingJackFlashStandEntity extends StandEntity {
    public JumpingJackFlashStandEntity(
            EntityType<? extends JumpingJackFlashStandEntity> type,
            Level level) {
        super(type, level);
    }
}
