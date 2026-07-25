package com.github.standobyte.jojo.mixin.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

@Mixin(Entity.class)
public abstract class EntityFluidPushMixin {
    @Shadow public abstract Level level();

    @Redirect(method = "updateFluidHeightAndDoFluidPushing(Lnet/minecraft/tags/TagKey;D)Z", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getFlow(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 jojo_ripples$cancelFluidPushInTimeStop(FluidState fluidState, net.minecraft.world.level.BlockGetter level, BlockPos blockPos,
            TagKey<Fluid> fluidTag, double motionScale) {
        Entity entity = (Entity) (Object) this;
        if (entity.level() instanceof ServerLevel serverLevel) {
            var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
            if (serverLevel.hasData(attachmentType)) {
                TimeStopState state = serverLevel.getData(attachmentType);
                if (state.isTimeStopped(entity)) {
                    return Vec3.ZERO;
                }
            }
        }
        return fluidState.getFlow(level, blockPos);
    }
}
