package com.github.standobyte.jojo.mixin.mrpresident;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.mrpresident.CocoJumboTurtleEntity;

import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerCocoCarryMixin {
	@Shadow public ServerPlayer player;

	@Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$dropCarriedCocoJumbo(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
			for (Entity passenger : player.getPassengers()) {
				if (CocoJumboTurtleEntity.isCarriedTurtle(passenger, player)) {
					passenger.stopRiding();
					ci.cancel();
					return;
				}
			}
		}
	}
}
