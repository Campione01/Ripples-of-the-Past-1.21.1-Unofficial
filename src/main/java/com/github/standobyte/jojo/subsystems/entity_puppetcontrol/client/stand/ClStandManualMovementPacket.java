package com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.stand;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.api.stand.StandManualMovementObservers;
import com.github.standobyte.jojo.api.stand.StandManualMovementObservers.LogicalSide;
import com.github.standobyte.jojo.api.stand.StandManualMovementObservers.ServerDecision;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.network.s2c.TrDirectEntityPosPacket;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.google.common.primitives.Floats;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClStandManualMovementPacket(double x, double y, double z, float xRot, float yRot, boolean resetDeltaMovement) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClStandManualMovementPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<ClStandManualMovementPacket> {

		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClStandManualMovementPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, ClStandManualMovementPacket> reader() {
			return STREAM_CODEC;
		}


		public static final StreamCodec<RegistryFriendlyByteBuf, ClStandManualMovementPacket> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.DOUBLE, ClStandManualMovementPacket::x,
				ByteBufCodecs.DOUBLE, ClStandManualMovementPacket::y,
				ByteBufCodecs.DOUBLE, ClStandManualMovementPacket::z,
				ByteBufCodecs.FLOAT, ClStandManualMovementPacket::xRot,
				ByteBufCodecs.FLOAT, ClStandManualMovementPacket::yRot,
				ByteBufCodecs.BOOL, ClStandManualMovementPacket::resetDeltaMovement,
				ClStandManualMovementPacket::new);

		@Override
		public void handle(ClStandManualMovementPacket packet, IPayloadContext context) {
			ServerPlayer player = (ServerPlayer) context.player();
			if (StandManualMovementObservers.hasObservers()) {
				StandManualMovementObservers.publish(
						LogicalSide.SERVER, player.level(),
						new StandManualMovementObservers.PacketReceipt(
								player.getUUID(),
								packet.x(), packet.y(), packet.z(),
								packet.xRot(), packet.yRot(),
								packet.resetDeltaMovement()));
			}
			if (isInvalid(packet)) {
				publishUnavailableResult(player, packet, ServerDecision.INVALID);
				player.connection.disconnect(Component.translatable("multiplayer.disconnect.invalid_stand_movement"));
				return;
			}
			StandPower power = StandPower.get(player);
			if (power == null) {
				publishUnavailableResult(player, packet, ServerDecision.NO_POWER);
				return;
			}
			StandEntity stand = power.getSummonedStandEntity();
			if (stand == null) {
				publishUnavailableResult(player, packet, ServerDecision.NO_STAND);
				return;
			}
			manualControlPacket(player, stand, packet);
		}
		
		public void manualControlPacket(ServerPlayer player, StandEntity stand, ClStandManualMovementPacket msg) {
			double posX1 = stand.getX(); // d0
			double posY1 = stand.getY(); // d1
			double posZ1 = stand.getZ(); // d2
			double posXcl = clampHorizontal(msg.x()); // d3
			double posYcl = clampVertical(msg.y()); // d4
			double posZcl = clampHorizontal(msg.z()); // d5
			boolean observing = StandManualMovementObservers.hasObservers();
			boolean manuallyControlled = observing
					&& stand.isManuallyControlled();
			boolean canMoveManually = stand.canMoveManually();
			EntityActionInstance action = observing
					? LivingComponentAction.getCurEntityAction(stand) : null;
			String actionId = observing
					? StandManualMovementObservers.stableActionId(action) : "none";
			double diffX = posXcl - posX1; // d6
			double diffY = posYcl - posY1; // d7
			double diffZ = posZcl - posZ1; // d8
			double diffSq = diffX * diffX + diffY * diffY + diffZ * diffZ; // d10
			if (!canMoveManually) {
				stand.setDeltaMovement(Vec3.ZERO);
				PacketDistributor.sendToPlayer(player, new TrDirectEntityPosPacket(stand.getId(), stand.position()));
				publishResult(
						player, stand, msg, ServerDecision.MANUAL_LOCKED,
						manuallyControlled, false, actionId, true,
						msg.resetDeltaMovement(), true,
						posX1, posY1, posZ1,
						posXcl, posYcl, posZcl,
						diffSq, 0.0D,
						false, true);
				return;
			}
//			ServerLevel level = player.getLevel();
			float xRot = Mth.wrapDegrees(msg.xRot());
			float yRot = Mth.wrapDegrees(msg.yRot());
//			double diffX = posXcl - firstGoodX;
//			double diffY = posYcl - firstGoodY;
//			double diffZ = posZcl - firstGoodZ;
			double motionSq = stand.getDeltaMovement().lengthSqr(); // d9
			if (diffSq - motionSq > 100.0D) {
				JojoMod.getLogger().warn("{} ({}'s stand) moved too quickly! {},{},{}",
						stand.getName().getString(), player.getName().getString(), diffX, diffY, diffZ);
				PacketDistributor.sendToPlayer(player, new TrDirectEntityPosPacket(stand.getId(), stand.position()));
				publishResult(
						player, stand, msg, ServerDecision.TOO_QUICK,
						manuallyControlled, true, actionId, true,
						msg.resetDeltaMovement(), false,
						posX1, posY1, posZ1,
						posXcl, posYcl, posZcl,
						diffSq, 0.0D,
						false, true);
				return;
			}
//			boolean flag = world.noCollision(stand, stand.getBoundingBox().deflate(0.0625D));
//			diffX = posXcl - lastGoodX;
//			diffY = posYcl - lastGoodY;
//			diffZ = posZcl - lastGoodZ;
			stand.move(MoverType.PLAYER, new Vec3(diffX, diffY, diffZ)); // also moves the stand towards user if the client tries to go to far away
//			diffX = posXcl - entity.getX();
//			diffY = posYcl - entity.getY();
//			if (diffY > -0.5D || diffY < 0.5D) {
//			   diffY = 0.0D;
//			}
//			diffZ = posZcl - entity.getZ();
//			diffSq = diffX * diffX + diffY * diffY + diffZ * diffZ;
//			boolean flag1 = false;
//			if (diffSq > 0.0625D) {
//			   flag1 = true;
//			   LOGGER.warn("{} ({}'s stand) moved wrongly! {}", stand.getName().getString(), player.getName().getString(), Math.sqrt(diffSq));
//			}
			stand.absMoveTo(stand.getX(), stand.getY(), stand.getZ(), yRot, xRot);
//			boolean flag2 = world.noCollision(stand, stand.getBoundingBox().deflate(0.0625D));
//			if (flag && (flag1 || !flag2)) {
//			   stand.absMoveTo(d0, d1, d2);
//			   PacketManager.sendToClient(new standCancelManualMovementPacket(stand.getX(), stand.getY(), stand.getZ()), player);
//			   return;
//			}
//			lastGoodX = stand.getX();
//			lastGoodY = stand.getY();
//			lastGoodZ = stand.getZ();
			if (msg.resetDeltaMovement()) {
				stand.setDeltaMovement(Vec3.ZERO);
			}
			double postX = stand.getX();
			double postY = stand.getY();
			double postZ = stand.getZ();
			double actualX = postX - posX1;
			double actualY = postY - posY1;
			double actualZ = postZ - posZ1;
			publishResult(
					player, stand, msg, ServerDecision.APPLIED,
					manuallyControlled, true, actionId, false,
					msg.resetDeltaMovement(), msg.resetDeltaMovement(),
					posX1, posY1, posZ1,
					posXcl, posYcl, posZcl,
					diffSq,
					actualX * actualX + actualY * actualY + actualZ * actualZ,
					true, false);
		}

		private void publishUnavailableResult(
				ServerPlayer player,
				ClStandManualMovementPacket packet,
				ServerDecision decision) {
			if (!StandManualMovementObservers.hasObservers()) {
				return;
			}
			StandManualMovementObservers.publish(
					LogicalSide.SERVER, player.level(),
					new StandManualMovementObservers.ServerPacketResult(
							decision, player.getUUID(), null,
							false, false, "none", true,
							packet.resetDeltaMovement(), false,
							Double.NaN, Double.NaN, Double.NaN,
							packet.x(), packet.y(), packet.z(),
							Double.NaN, Double.NaN, Double.NaN,
							Double.NaN, Double.NaN,
							false, false));
		}

		private void publishResult(
				ServerPlayer player,
				StandEntity stand,
				ClStandManualMovementPacket packet,
				ServerDecision decision,
				boolean manuallyControlled,
				boolean canMoveManually,
				String actionId,
				boolean requestRejected,
				boolean deltaResetRequested,
				boolean deltaResetApplied,
				double preX,
				double preY,
				double preZ,
				double requestedX,
				double requestedY,
				double requestedZ,
				double requestedDeltaSqr,
				double actualDeltaSqr,
				boolean moveInvoked,
				boolean correctionSent) {
			if (!StandManualMovementObservers.hasObservers()) {
				return;
			}
			StandManualMovementObservers.publish(
					LogicalSide.SERVER, player.level(),
					new StandManualMovementObservers.ServerPacketResult(
							decision,
							player.getUUID(), stand.getUUID(),
							manuallyControlled, canMoveManually,
							actionId, requestRejected,
							deltaResetRequested, deltaResetApplied,
							preX, preY, preZ,
							requestedX, requestedY, requestedZ,
							stand.getX(), stand.getY(), stand.getZ(),
							requestedDeltaSqr, actualDeltaSqr,
							moveInvoked, correctionSent));
		}

		private boolean isInvalid(ClStandManualMovementPacket msg) {
			return !Double.isFinite(msg.x) || !Double.isFinite(msg.y) || !Double.isFinite(msg.z)
					|| Math.abs(msg.x) > 3.0E7 || Math.abs(msg.y) > 3.0E7 || Math.abs(msg.z) > 3.0E7
					|| !Floats.isFinite(msg.xRot) || !Floats.isFinite(msg.yRot);
		}

		public static double clampHorizontal(double value) {
			return Mth.clamp(value, -3.0E7, 3.0E7);
		}

		public static double clampVertical(double value) {
			return Mth.clamp(value, -2.0E7, 2.0E7);
		}

	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

}
