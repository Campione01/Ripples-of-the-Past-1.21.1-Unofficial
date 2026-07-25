package com.github.standobyte.jojo.network.s2c;

import org.joml.Vector3f;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.client.polaroid.PolaroidHelper;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PhotoForOtherPlayerPacket(int giveToPlayerId) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<PhotoForOtherPlayerPacket> type;

	public PhotoForOtherPlayerPacket(RegistryFriendlyByteBuf buf) {
		this(buf.readInt());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeInt(giveToPlayerId);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

	public static class Handler implements PacketsRegister.PacketCodecHandler<PhotoForOtherPlayerPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<PhotoForOtherPlayerPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, PhotoForOtherPlayerPacket> reader() {
			return StreamCodec.ofMember(PhotoForOtherPlayerPacket::write, PhotoForOtherPlayerPacket::new);
		}

		@Override
		public void handle(PhotoForOtherPlayerPacket payload, IPayloadContext context) {
			Player player = ClientProxy.getClientPlayer();
			if (player == null) {
				return;
			}
			float randomAngle = player.getRandom().nextFloat() * (float) (Math.PI * 2.0D);
			Vec3 playerPos = player.getEyePosition(1.0F);
			Vec3 cameraPos = playerPos.add(new Vec3(0.0D, 0.0D, 2.0D).yRot(randomAngle));
			PolaroidHelper.takePicture(cameraPos,
					rot -> new Vector3f(0.0F, 180.0F - randomAngle * 180.0F / (float) Math.PI, 0.0F),
					true, payload.giveToPlayerId);
		}
	}
}
