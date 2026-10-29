package rotp.addons.d4c.block;

import rotp.addons.d4c.travel.D4CDimensionTravelService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class D4CPortalBlock extends Block {
    private final ResourceLocation targetDimension;

    public D4CPortalBlock(
            Properties properties,
            ResourceLocation targetDimension) {
        super(properties);
        this.targetDimension = targetDimension;
    }

    public ResourceLocation targetDimension() {
        return targetDimension;
    }

    @Override
    protected void entityInside(
            BlockState state,
            Level level,
            BlockPos position,
            Entity entity) {
        if (!(level instanceof ServerLevel serverLevel)
                || !entity.isAlive()
                || entity.isPassenger()
                || entity.isVehicle()) {
            return;
        }
        if (D4CDimensionTravelService.teleportThroughPortal(
                serverLevel,
                position,
                entity,
                targetDimension)) {
            serverLevel.removeBlock(position, false);
        }
    }

    @Override
    protected void tick(
            BlockState state,
            ServerLevel level,
            BlockPos position,
            RandomSource random) {
        if (level.getBlockState(position).is(this)) {
            level.removeBlock(position, false);
        }
    }
}
