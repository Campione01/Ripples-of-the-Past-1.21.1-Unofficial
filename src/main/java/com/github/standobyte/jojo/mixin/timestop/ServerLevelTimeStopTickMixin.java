package com.github.standobyte.jojo.mixin.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.entityattachment.DataEventListeners;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.material.Fluid;

@Mixin(ServerLevel.class)
public abstract class ServerLevelTimeStopTickMixin {
    @Inject(method = "tickFluid", at = @At("HEAD"), cancellable = true)
    private void jojo_ripples$cancelFluidTickInTimeStop(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        TimeStopState state = jojo_ripples$getTimeStopState();
        if (state != null && state.isTimeStopped(new ChunkPos(pos))) {
            ServerLevel level = (ServerLevel) (Object) this;
            ((LevelAccessor) level).scheduleTick(pos, fluid, fluid.getTickDelay(level));
            ci.cancel();
        }
    }

    @Inject(method = "tickBlock", at = @At("HEAD"), cancellable = true)
    private void jojo_ripples$cancelBlockTickInTimeStop(BlockPos pos, Block block, CallbackInfo ci) {
        TimeStopState state = jojo_ripples$getTimeStopState();
        if (state != null && state.isTimeStopped(new ChunkPos(pos)) && !(block instanceof CommandBlock)) {
            ServerLevel level = (ServerLevel) (Object) this;
            ((LevelAccessor) level).scheduleTick(pos, block, 1);
            ci.cancel();
        }
    }

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void jojo_ripples$cancelEntityTickInTimeStop(Entity entity, CallbackInfo ci) {
        TimeStopState state = jojo_ripples$getTimeStopState();
        if (state != null && !(entity instanceof ItemEntity) && state.interruptTickEarly(entity)) {
            jojo_ripples$tickFrozenEntityData(entity);
            ci.cancel();
        }
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
    private void jojo_ripples$cancelPassengerTickInTimeStop(Entity ridingEntity, Entity passengerEntity, CallbackInfo ci) {
        TimeStopState state = jojo_ripples$getTimeStopState();
        if (state != null && state.interruptTickEarly(passengerEntity)) {
            jojo_ripples$tickFrozenEntityData(passengerEntity);
            ci.cancel();
        }
    }

    private void jojo_ripples$tickFrozenEntityData(Entity entity) {
        var key = ModDataAttachmentTypes.DATA_EVENT_HELPER.get();
        if (entity.hasData(key)) {
            DataEventListeners data = entity.getData(key);
            data.onTick();
        }
    }

    private TimeStopState jojo_ripples$getTimeStopState() {
        ServerLevel level = (ServerLevel) (Object) this;
        var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
        return level.hasData(attachmentType) ? level.getData(attachmentType) : null;
    }
}
