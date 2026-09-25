package rotp.core.network.c2s;

import rotp.core.PacketsRegister;
import rotp.core.impl.powers.hamon.entity.LeavesGliderEntity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client-computed glider leaf tint (1.16 ClLeavesGliderColorPacket)
public record ClLeavesGliderColorPacket(int entityId, int color) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClLeavesGliderColorPacket> type;

	public static class Handler implements PacketsRegister.PacketOGHandler<ClLeavesGliderColorPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClLeavesGliderColorPacket> type() {
			return type;
		}

		@Override
		public void encode(ClLeavesGliderColorPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
			buf.writeInt(packet.color);
		}

		@Override
		public ClLeavesGliderColorPacket decode(RegistryFriendlyByteBuf buf) {
			return new ClLeavesGliderColorPacket(buf.readInt(), buf.readInt());
		}

		@Override
		public void handle(ClLeavesGliderColorPacket payload, IPayloadContext context) {
			apply(context.player().level(), payload);
		}
	}

	// First report wins, as in 1.16; the glider keeps it in NBT "Color" and later spawn data
	public static boolean apply(Level level, ClLeavesGliderColorPacket payload) {
		return level.getEntity(payload.entityId) instanceof LeavesGliderEntity glider
				&& glider.acceptClientFoliageColor(payload.color);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}
