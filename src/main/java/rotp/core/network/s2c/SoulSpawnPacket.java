package rotp.core.network.s2c;

import rotp.core.PacketsRegister;
import rotp.core.client.ClientPowerCache;
import rotp.core.client.ClientProxy;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.soul.client.ControllerSoul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SoulSpawnPacket(boolean failedSpawnPacket, boolean soulWillSpawnFlag) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<SoulSpawnPacket> type;

	public static SoulSpawnPacket noSoulSpawned() {
		return new SoulSpawnPacket(true, false);
	}

	public static SoulSpawnPacket spawnFlag(boolean soulCanSpawnFlag) {
		return new SoulSpawnPacket(false, soulCanSpawnFlag);
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<SoulSpawnPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<SoulSpawnPacket> type() {
			return type;
		}

		@Override
		public void encode(SoulSpawnPacket packet, RegistryFriendlyByteBuf buf) {
			byte flags = 0;
			if (packet.failedSpawnPacket) {
				flags = 1;
			}
			if (packet.soulWillSpawnFlag) {
				flags |= 2;
			}
			buf.writeByte(flags);
		}

		@Override
		public SoulSpawnPacket decode(RegistryFriendlyByteBuf buf) {
			byte flags = buf.readByte();
			return new SoulSpawnPacket((flags & 1) > 0, (flags & 2) > 0);
		}

		@Override
		public void handle(SoulSpawnPacket payload, IPayloadContext context) {
			StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
			Player player = ClientProxy.getClientPlayer();
			if (standPower == null && player != null) {
				standPower = StandPower.get(player);
			}
			if (standPower != null) {
				standPower.clSetSoulSpawnFlag(payload.soulWillSpawnFlag);
			}
			if (payload.failedSpawnPacket) {
				ControllerSoul.onSoulFailedSpawn();
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}
