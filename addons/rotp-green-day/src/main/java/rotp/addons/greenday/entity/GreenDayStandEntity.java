package rotp.addons.greenday.entity;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;

import rotp.addons.greenday.GreenDayAddon;
import rotp.addons.greenday.state.GreenDayMoldState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GreenDayStandEntity extends StandEntity {
    public static final double MOLD_FIELD_RANGE = 128.0D;
    public static final float MOLD_STAMINA_PER_TICK = 1.0F;

    // 1.16 GreenDayStandEntity.getDefaultOffsetFromUser: straight behind
    // the user, 0.5 up, turned with the view yaw, arms-only mode included.
    // The 0.5 is also the default y of task offsets without their own.
    public GreenDayStandEntity(
            EntityType<? extends StandEntity> type,
            Level level) {
        super(type, level);
        Y_OFFSET = 0.5D;
        Vec3 idleOffset = new Vec3(0.0D, Y_OFFSET, -0.75D);
        offsetFromUser = new StandOffsetFromUser(
                this,
                idleOffset,
                StandOffsetFromUser.Rotations.HEAD)
                .armsOnlyIdleOffset(
                        idleOffset,
                        StandOffsetFromUser.Rotations.HEAD)
                .grabOffset(
                        new Vec3(-1.0D, Y_OFFSET, 1.5D));
    }

    @Override
    public double getMaxRange() {
        return 5.3D;
    }

    public static void tickMoldField(
            ServerLevel level,
            LivingEntity owner,
            StandPower power) {
        GreenDayMoldState state =
                GreenDayMoldState.get(owner);
        if (!state.moldActive()
                || power.getPowerType()
                        != GreenDayAddon.GREEN_DAY.get()) {
            return;
        }

        // 1.16 MCUtil.entitiesAround(..., includeSelf = false, ...): the user
        // is not its own mold source.
        long gameTime = level.getGameTime();
        for (LivingEntity target :
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        owner.getBoundingBox().inflate(
                                MOLD_FIELD_RANGE),
                        target -> target != owner
                                && target.isAlive()
                                && !(target
                                        instanceof StandEntity)
                                && !GreenDayMoldState
                                        .isImmune(target))) {
            GreenDayMoldState.get(target)
                    .markExposed(
                            target,
                            owner.getUUID(),
                            gameTime);
        }
        // 1.16 GreenDayStandEntity spread the mold, paid a non-ticking 1 with
        // the result ignored, then switched the mold off once the bar read
        // exactly 0.
        power.consumeStamina(
                MOLD_STAMINA_PER_TICK, false);
        if (power.getStamina() == 0.0F) {
            state.setMoldActive(owner, false);
        }
    }
}
