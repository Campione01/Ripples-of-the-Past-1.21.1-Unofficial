package rotp.addons.stickyfingers.mixin.common;

import rotp.addons.stickyfingers.state.ZipperPhasing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class ZipperBlockCollisionMixin {
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void stickyFingers$phase(BlockGetter level, BlockPos pos,
            CollisionContext context, CallbackInfoReturnable<VoxelShape> callback) {
        callback.setReturnValue(ZipperPhasing.collisionShape(
                (BlockState) (Object) this, level, pos, context, callback.getReturnValue()));
    }
}
