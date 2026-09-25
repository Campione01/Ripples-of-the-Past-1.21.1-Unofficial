package rotp.core.mixin.timestop;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;

/**
 * Block entities in a stopped chunk do not tick on the server, as in 1.16, where ServerChunkProviderMixin made
 * isTickingChunk false there (World.tickBlockEntities checked it). Command blocks are left alone, as in 1.16.
 * RebindableTickingBlockEntityWrapper delegates here, so every ticking block entity passes through.
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class TimeStopBlockEntityTickMixin {
    @Shadow @Final private BlockEntity blockEntity;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void jojo_ripples$skipBlockEntityTickInTimeStop(CallbackInfo ci) {
        if (blockEntity instanceof CommandBlockEntity || !(blockEntity.getLevel() instanceof ServerLevel level)) {
            return;
        }
        var type = ModDataAttachmentTypes.TIME_STOP.get();
        if (!level.hasData(type)) {
            return;
        }
        TimeStopState state = level.getData(type);
        if (!state.getInstances().isEmpty() && state.isTimeStopped(new ChunkPos(blockEntity.getBlockPos()))) {
            ci.cancel();
        }
    }
}
