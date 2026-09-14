package rotp.core.mechanics.resolve;

import rotp.core.PacketsRegister;
import rotp.core.client.ClientProxy;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.util.functions_network.StreamCodecs;
import rotp.core.util.objects_java.OptionalFloat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ResolveBoostsPacket(
		float boostAttack, 
		float boostRemoteControl, 
		float boostChat, 
		OptionalFloat hpOnGettingAttacked, 
		int noBoostDecayTicks, 
		int resolveModeTimer, 
		int resolveModeTimerMax) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ResolveBoostsPacket> type;
	
	public ResolveBoostsPacket(ResolveCounter boosts) {
		this(
				boosts.boostAttack, 
				boosts.boostRemoteControl, 
				boosts.boostChat, 
				boosts.hpOnGettingAttacked, 
				boosts.noBoostDecayTicks, 
				boosts.resolveModeTimer.value, 
				boosts.resolveModeTimer.defaultValue);
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<ResolveBoostsPacket> {

		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ResolveBoostsPacket> type() {
			return type;
		}

		@Override
		public void encode(ResolveBoostsPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeFloat(packet.boostAttack);
			buf.writeFloat(packet.boostRemoteControl);
			buf.writeFloat(packet.boostChat);
			StreamCodecs.OPTIONAL_FLOAT.encode(buf, packet.hpOnGettingAttacked);
			buf.writeVarInt(packet.noBoostDecayTicks);
			buf.writeInt(packet.resolveModeTimer);
			buf.writeInt(packet.resolveModeTimerMax);
		}

		@Override
		public ResolveBoostsPacket decode(RegistryFriendlyByteBuf buf) {
			float boostAttack = buf.readFloat();
			float boostRemoteControl = buf.readFloat();
			float boostChat = buf.readFloat();
			OptionalFloat hpOnGettingAttacked = StreamCodecs.OPTIONAL_FLOAT.decode(buf);
			int noBoostDecayTicks = buf.readVarInt();
			int resolveModeTimer = buf.readInt();
			int resolveModeTimerMax = buf.readInt();
			return new ResolveBoostsPacket(boostAttack, boostRemoteControl, boostChat, hpOnGettingAttacked, noBoostDecayTicks, resolveModeTimer, resolveModeTimerMax);
		}

		@Override
		public void handle(ResolveBoostsPacket payload, IPayloadContext context) {
			LivingEntity player = ClientProxy.getClientPlayer();
			if (player != null) {
				StandPower standPower = StandPower.get(player);
				if (standPower != null) {
					standPower.resolveCounter.boostAttack = payload.boostAttack;
					standPower.resolveCounter.boostRemoteControl = payload.boostRemoteControl;
					standPower.resolveCounter.boostChat = payload.boostChat;
					standPower.resolveCounter.hpOnGettingAttacked = payload.hpOnGettingAttacked;
					standPower.resolveCounter.noBoostDecayTicks = payload.noBoostDecayTicks;
					standPower.resolveCounter.resolveModeTimer.value = payload.resolveModeTimer;
					standPower.resolveCounter.resolveModeTimer.defaultValue = payload.resolveModeTimerMax;
				}
			}
		}

	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

}
