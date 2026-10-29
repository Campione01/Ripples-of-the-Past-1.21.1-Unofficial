package rotp.addons.mandom.mixin;

import java.util.ArrayDeque;
import java.util.Deque;

import rotp.addons.mandom.state.MandomRewindService;
import rotp.addons.mandom.state.MandomRewindService.BlockPreimage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelBlockJournalMixin {
    @Unique
    private static final ThreadLocal<Deque<Pending>>
            ROTP_MANDOM$PENDING =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private record Pending(BlockPreimage preimage) {}

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/"
                    + "BlockState;II)Z",
            at = @At("HEAD"))
    private void rotpMandom$captureBeforeSetBlock(
            BlockPos position,
            BlockState newState,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> callback) {
        ROTP_MANDOM$PENDING.get().push(new Pending(
                MandomRewindService.captureBlockPreimage(
                        (Level) (Object) this, position)));
    }

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/"
                    + "BlockState;II)Z",
            at = @At("RETURN"))
    private void rotpMandom$recordAfterSetBlock(
            BlockPos position,
            BlockState newState,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> callback) {
        Deque<Pending> pending =
                ROTP_MANDOM$PENDING.get();
        BlockPreimage preimage = pending.isEmpty()
                ? null
                : pending.pop().preimage();
        if (pending.isEmpty()) {
            ROTP_MANDOM$PENDING.remove();
        }
        if (callback.getReturnValueZ()) {
            MandomRewindService.recordBlockMutation(preimage);
        }
    }
}
