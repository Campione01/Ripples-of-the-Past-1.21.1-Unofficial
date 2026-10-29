package rotp.addons.littlefeet.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class LittleFeetEntity extends StandEntity {
    public static final float LEGACY_LEAP_MULTIPLIER = 2.0F;

    public LittleFeetEntity(
            EntityType<? extends StandEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public float getLeapStrength() {
        return super.getLeapStrength() * LEGACY_LEAP_MULTIPLIER;
    }
}
