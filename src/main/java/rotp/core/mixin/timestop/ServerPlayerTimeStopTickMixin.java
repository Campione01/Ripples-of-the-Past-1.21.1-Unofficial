package rotp.core.mixin.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTimeStopTickMixin {

    // 1.16 ServerPlayerEntityMixin cancelled a stopped player's doTick. The level only runs ServerPlayer#tick
    // (interrupted in ServerLevelTimeStopTickMixin); the player's own tick comes from its connection.
    // A fake player is ticked by whoever owns it, which has its own freeze.
    @Inject(method = "doTick", at = @At("HEAD"), cancellable = true)
    private void jojo_ripples$cancelPlayerTickInTimeStop(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!(player instanceof FakePlayer) && TimeStopState.shouldFreezeOnServer(player)) {
            ci.cancel();
        }
    }
}
