package rotp.addons.weatherreport.block;

import rotp.core.util.functions.DamageUtil;

import rotp.addons.weatherreport.WeatherReportDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BloodSpikesBlock extends BloodPuddleBlock {
    private static final Vec3 STUCK_MULTIPLIER =
            new Vec3(0.8D, 0.75D, 0.8D);

    public BloodSpikesBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void entityInside(
            BlockState state,
            Level level,
            BlockPos position,
            Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        living.makeStuckInBlock(state, STUCK_MULTIPLIER);
        if (!level.isClientSide()) {
            living.hurt(
                    DamageUtil.make(
                            level,
                            WeatherReportDamageTypes.SPIKES),
                    (state.getValue(STAGE) + 1) / 2.0F);
        }
    }
}
