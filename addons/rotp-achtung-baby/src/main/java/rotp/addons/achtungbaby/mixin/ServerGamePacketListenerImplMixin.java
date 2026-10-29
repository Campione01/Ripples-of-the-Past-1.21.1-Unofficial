package rotp.addons.achtungbaby.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.addons.achtungbaby.AchtungBabyService;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Ghost mode does not let you teleport to whoever you like.
 *
 * <p>The client half of this is {@link SpectatorGuiMixin}, but hiding the menu is not a restriction on its own - the
 * packet is what actually moves the player, and a client that sends it anyway must still be refused. 1.16 refused it
 * in SpectatorModeRestrictionMixin; the port kept neither half.
 *
 * <p>The injection point is the {@code isSpectator} call rather than HEAD **on purpose**: HEAD sits in front of
 * {@code PacketUtils.ensureRunningOnSameThread}, so it runs first on the netty thread, where reading the player's
 * attachment would be an off-thread read. By the time that call is reached the packet has been handed to the server
 * thread.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(
            method = "handleTeleportToEntityPacket"
                    + "(Lnet/minecraft/network/protocol/game/ServerboundTeleportToEntityPacket;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;isSpectator()Z"),
            cancellable = true)
    private void rotp_ab$blockGhostSpectatorTeleport(
            ServerboundTeleportToEntityPacket packet, CallbackInfo ci) {
        if (AchtungBabyService.isGhost(player)) {
            ci.cancel();
        }
    }
}
