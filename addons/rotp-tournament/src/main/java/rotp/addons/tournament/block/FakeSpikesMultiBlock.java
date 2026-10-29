package rotp.addons.tournament.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class FakeSpikesMultiBlock extends SpikesMultiBlock {
    public static final MapCodec<FakeSpikesMultiBlock> CODEC =
            simpleCodec(FakeSpikesMultiBlock::new);

    public FakeSpikesMultiBlock(
            BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends FakeSpikesMultiBlock> codec() {
        return CODEC;
    }

    @Override
    protected void entityInside(
            BlockState state,
            Level level,
            BlockPos pos,
            Entity entity) {
    }

    @Override
    public void fallOn(
            Level level,
            BlockState state,
            BlockPos pos,
            Entity entity,
            float fallDistance) {
        entity.causeFallDamage(
                fallDistance,
                0.0F,
                level.damageSources().fall());
    }

    @Override
    public void updateEntityAfterFallOn(
            BlockGetter level,
            Entity entity) {
        Vec3 movement = entity.getDeltaMovement();
        if (movement.y < 0.0D) {
            double bounce =
                    entity instanceof LivingEntity ? 1.2D : 1.0D;
            entity.setDeltaMovement(
                    movement.x,
                    -movement.y * bounce,
                    movement.z);
        }
    }
}
