package com.github.standobyte.jojo.mixin.client.lifeshot;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.init.ModStatusEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

@Mixin(Connection.class)
public class ConnectionSensoryOverloadMixin {

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V",
			at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$blockSensoryOverloadServerboundPackets(Packet<?> packet,
			@Nullable PacketSendListener listener, boolean flush, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !mc.player.hasEffect(ModStatusEffects.SENSORY_OVERLOAD) || !isServerboundPacket(packet)) {
			return;
		}
		if (isAllowedDuringSensoryOverload(packet)) {
			return;
		}
		ci.cancel();
	}

	private static boolean isServerboundPacket(Packet<?> packet) {
		String className = packet.getClass().getName();
		return className.startsWith("net.minecraft.network.protocol.game.Serverbound")
				|| className.startsWith("net.minecraft.network.protocol.common.Serverbound");
	}

	private static boolean isAllowedDuringSensoryOverload(Packet<?> packet) {
		return packet instanceof ServerboundKeepAlivePacket
				|| packet instanceof ServerboundPongPacket
				|| packet instanceof ServerboundMovePlayerPacket
				|| packet instanceof ServerboundChatCommandPacket
				|| packet instanceof ServerboundChatCommandSignedPacket;
	}
}
