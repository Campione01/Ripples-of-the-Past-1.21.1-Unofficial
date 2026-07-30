package com.github.standobyte.jojoimpl.stands.crazydiamond.brokenblocks;

import java.util.Collection;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.client.particle.CustomParticlesHelper;
import com.github.standobyte.jojo.util.functions_network.NetworkUtil;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDHealAbility;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDRestoreTerrainAbility;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CDBlocksRestoredPacket(Collection<BlockPos> positions, Collection<Integer> entities) implements CustomPacketPayload {
	private static final int MAX_RESTORED_BLOCKS = 4096;
	private static final int MAX_RESTORED_ENTITIES = 1024;

	private static CustomPacketPayload.Type<CDBlocksRestoredPacket> type;

	public static class Handler implements PacketsRegister.PacketOGHandler<CDBlocksRestoredPacket> {

		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<CDBlocksRestoredPacket> type() {
			return type;
		}

		@Override
		public void encode(CDBlocksRestoredPacket packet, RegistryFriendlyByteBuf buf) {
			NetworkUtil.writeCollection(
					buf, packet.positions, BlockPos.STREAM_CODEC, MAX_RESTORED_BLOCKS);
			NetworkUtil.writeCollection(
					buf, packet.entities, ByteBufCodecs.INT, MAX_RESTORED_ENTITIES);
		}

		@Override
		public CDBlocksRestoredPacket decode(RegistryFriendlyByteBuf buf) {
			Collection<BlockPos> positions = NetworkUtil.readCollection(
					buf, BlockPos.STREAM_CODEC, MAX_RESTORED_BLOCKS);
			Collection<Integer> entities = NetworkUtil.readCollection(
					IntArraySet::new, buf, ByteBufCodecs.INT, MAX_RESTORED_ENTITIES);
			return new CDBlocksRestoredPacket(positions, entities);
		}

		@Override
		public void handle(CDBlocksRestoredPacket payload, IPayloadContext context) {
			if (ClientGlobals.canSeeStands) {
				Level level = ClientProxy.getClientWorld();
				if (level == null) {
					return;
				}
				for (BlockPos pos : payload.positions) {
					CrazyDRestoreTerrainAbility.addParticlesAroundBlock(level, pos, level.getRandom());
				}
				for (int entityId : payload.entities) {
					Entity entity = ClientProxy.getEntityById(entityId);
					if (entity != null) {
						if (entity instanceof LivingEntity livingEntity) {
							CustomParticlesHelper.createCDRestorationParticle(livingEntity, InteractionHand.MAIN_HAND);
							CustomParticlesHelper.createCDRestorationParticle(livingEntity, InteractionHand.OFF_HAND);
						}
						else {
							CrazyDHealAbility.addParticlesAround(entity);
						}
					}
				}
			}
		}

	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

}
