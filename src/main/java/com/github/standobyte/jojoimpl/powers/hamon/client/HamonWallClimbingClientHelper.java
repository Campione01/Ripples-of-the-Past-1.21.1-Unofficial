package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonStopWallClimbPacket;
import com.github.standobyte.jojoimpl.powers.hamon.ClHamonWallClimbMovementPacket;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonWallClimbingHelper;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HamonWallClimbingClientHelper {
	private static final double SIDE_SPEED_MULT = 0.5D;
	private static final double DOWN_SPEED_MULT = 0.5D;
	private static final double SIDE_VEC_LEN_MULT = 1.0D / Math.sqrt(1.0D + SIDE_SPEED_MULT * SIDE_SPEED_MULT);
	private static final double DOWN_SIDE_VEC_LEN_MULT = Math.max(DOWN_SPEED_MULT, SIDE_SPEED_MULT)
			/ Math.sqrt(DOWN_SPEED_MULT * DOWN_SPEED_MULT + SIDE_SPEED_MULT * SIDE_SPEED_MULT);
	private static final float MIN_MOVEMENT_SPEED = 0.06F;
	private static final float DEG_TO_RAD = (float) Math.PI / 180.0F;

	private HamonWallClimbingClientHelper() {}

	public static boolean travelWallClimb(LocalPlayer player, Vec3 inputVec) {
		HamonData hamon = PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElse(null);
		if (hamon == null || !hamon.isWallClimbing()) {
			return false;
		}

		player.fallDistance = 0.0F;
		float climbYRot = hamon.getWallClimbYRot(player.yBodyRot) * DEG_TO_RAD;
		Vec3 gripVec = new Vec3(0.0D, 0.0D, HamonWallClimbingHelper.MAX_WALL_DISTANCE).yRot(climbYRot);

		if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty() || player.isSpectator()) {
			stopWallClimbing(hamon);
			return false;
		}
		Vec3 collided = HamonWallClimbingHelper.collide(player, player.getBoundingBox(), gripVec, true);
		if (collided.distanceToSqr(gripVec) < 1.0E-7D) {
			stopWallClimbing(hamon);
			return false;
		}

		double climbSpeed = hamon.getWallClimbSpeed(player) * player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		boolean canPullUp = false;
		Vec3 movement = new Vec3(inputVec.x * SIDE_SPEED_MULT,
				inputVec.z > 0.0D ? inputVec.z : inputVec.z * DOWN_SPEED_MULT, 0.0D);
		if (movement.lengthSqr() > 1.0D) {
			movement = movement.normalize();
		}
		if (inputVec.x != 0.0D && inputVec.z != 0.0D) {
			climbSpeed *= inputVec.z > 0.0D ? SIDE_VEC_LEN_MULT : DOWN_SIDE_VEC_LEN_MULT;
		}
		movement = movement.scale(climbSpeed);
		if (climbYRot != 0.0F) {
			movement = movement.yRot(climbYRot);
		}

		Vec3 horizontalMovementOnly = new Vec3(movement.x, 0.0D, movement.z);
		AABB gripBox = player.getBoundingBox().contract(0.0D, -player.getBbHeight() * 0.5D, 0.0D);
		if (movement.y < 0.0D) {
			if (player.onGround()) {
				stopWallClimbing(hamon);
				return false;
			}
			Vec3 collideAfterMove = HamonWallClimbingHelper.collide(player,
					gripBox.move(0.0D, -gripBox.getYsize() + movement.y, 0.0D),
					gripVec, true);
			if (collideAfterMove.distanceToSqr(gripVec) < 1.0E-7D) {
				movement = horizontalMovementOnly;
			}
		}

		Vec3 collideAfterMove = HamonWallClimbingHelper.collide(player,
				gripBox.move(0.0D, gripBox.getYsize(), 0.0D), gripVec, true);
		if (collideAfterMove.distanceToSqr(gripVec) < 1.0E-7D) {
			if (movement.y > 0.0D) {
				movement = horizontalMovementOnly;
			}
			canPullUp = true;
		}

		if (horizontalMovementOnly.lengthSqr() > 1.0E-7D) {
			collideAfterMove = HamonWallClimbingHelper.collide(player,
					gripBox.move(horizontalMovementOnly.normalize()
							.scale(horizontalMovementOnly.length() + player.getBbWidth() + 0.1D)),
					gripVec, true);
			if (collideAfterMove.distanceToSqr(gripVec) < 1.0E-7D) {
				movement = new Vec3(0.0D, movement.y, 0.0D);
			}
		}

		if (player.input.jumping) {
			stopWallClimbing(hamon);
			canPullUp &= HamonWallClimbingHelper.collide(player,
					gripBox.move(0.0D, gripBox.getYsize(), 0.0D), gripVec, false)
					.distanceToSqr(gripVec) < 1.0E-7D;
			if (canPullUp) {
				player.move(MoverType.SELF, new Vec3(0.0D, player.getBbHeight(), 0.0D));
				player.setDeltaMovement(new Vec3(0.0D, 0.0D, 0.1D).yRot(climbYRot));
			}
			return false;
		}

		boolean moving = movement.lengthSqr() > 1.0E-7D;
		double movementUp = movement.y;
		double movementLeft = movement.yRot(-climbYRot).x;
		float animSpeed = (float) movement.length() / MIN_MOVEMENT_SPEED;
		sendMotionState(hamon, moving, movementUp, movementLeft, animSpeed);
		player.setDeltaMovement(movement);
		player.move(MoverType.SELF, movement);
		player.calculateEntityAnimation(false);
		return true;
	}

	private static void stopWallClimbing(HamonData hamon) {
		hamon.trSetWallClimbing(false, false, 0.0F, false, 0.0F);
		sendMotionState(hamon, false, 0.0D, 0.0D, 0.0F);
		PacketDistributor.sendToServer(new ClHamonStopWallClimbPacket());
	}

	private static void sendMotionState(HamonData hamon, boolean moving,
			double movementUp, double movementLeft, float speed) {
		hamon.setWallClimbMotion(moving, movementUp, movementLeft, speed);
		PacketDistributor.sendToServer(new ClHamonWallClimbMovementPacket(
				moving, movementUp, movementLeft, speed));
	}
}
