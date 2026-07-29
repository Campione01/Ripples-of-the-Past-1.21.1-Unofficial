package com.github.standobyte.jojo.mixin.control;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.control.CarriedPassengerActionHooks;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerCarriedPassengerActionMixin {
	@Shadow public ServerPlayer player;

	@Inject(
			method = "handlePlayerAction",
			at = @At("HEAD"),
			cancellable = true)
	private void jojo_ripples$handleCarriedPassengerAction(
			ServerboundPlayerActionPacket packet,
			CallbackInfo ci) {
		if (CarriedPassengerActionHooks.handle(
				player, packet.getAction())) {
			int selected = player.getInventory().selected;
			player.connection.send(
					new ClientboundContainerSetSlotPacket(
							-2,
							0,
							selected,
							player.getInventory().getItem(selected)));
			ci.cancel();
		}
	}
}
