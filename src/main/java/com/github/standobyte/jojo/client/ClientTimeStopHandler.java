package com.github.standobyte.jojo.client;

import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.customobjects.entity_projectile.KnifeEntity;
import com.github.standobyte.jojo.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import com.github.standobyte.jojo.client.shader.ModShaders;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.mixin.client.timestop.MinecraftTimeStopAccessor;
import com.github.standobyte.jojo.modcompat.ModInteractionUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.soul.SoulEntity;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopClientAwareness;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;

public final class ClientTimeStopHandler {
	private static boolean timeStopped;
	private static boolean lastCanMove = true;
	private static float partialTickStoppedAt;
	private static int timeStopTicks;
	private static int timeStopLength;
	private static Float lockedYRot;
	private static Float lockedXRot;

	private ClientTimeStopHandler() {}

	public static void clientTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			reset();
			TimeStopClientAwareness.reset();
			TimeStopState.clearClientInstances();
			return;
		}

		boolean active = isTimeStoppedAtClientPlayer(mc);
		boolean canMove = TimeStopClientAwareness.canMove();
		if (!active) {
			if (timeStopped) {
				timeStopTicks = 0;
				timeStopLength = 0;
			}
			TimeStopState.clearTimeStopFloat(mc.player);
			timeStopped = false;
			lastCanMove = canMove;
			partialTickStoppedAt = 0.0F;
			TimeStopState.tickClientInstances();
			return;
		}
		if (!timeStopped || lastCanMove != canMove) {
			partialTickStoppedAt = canMove ? mc.getTimer().getGameTimeDeltaPartialTick(true) : 0.0F;
		}
		if (!timeStopped) {
			timeStopTicks = 0;
			updateTimeStopTicksLeft();
		}
		timeStopped = true;
		lastCanMove = canMove;
		pauseClientIfVisionRestricted(mc);
		TimeStopState.tickClientInstances();
		timeStopTicks++;
		updateTimeStopTicksLeft();
		if (canMove) {
			applyTimeStopFloatClientPrediction(mc);
		}
		else {
			TimeStopState.clearTimeStopFloat(mc.player);
		}
	}

	public static void reset() {
		timeStopped = false;
		lastCanMove = true;
		partialTickStoppedAt = 0.0F;
		timeStopTicks = 0;
		timeStopLength = 0;
		clearLockedRotation();
	}

	public static void updateTimeStopTicksLeft() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && mc.player != null) {
			int ticksLeft = TimeStopState.getClientDisplayInstance(new ChunkPos(mc.player.blockPosition()))
					.map(TimeStopState.Instance::ticksLeft)
					.orElse(0);
			timeStopLength = timeStopTicks + ticksLeft;
		}
		else {
			timeStopLength = 0;
		}
	}

	public static int getTimeStopTicks() {
		return timeStopTicks;
	}

	public static int getTimeStopLength() {
		return timeStopLength;
	}

	public static void setTimeStopClientState(boolean canSee, boolean canMove) {
		TimeStopClientAwareness.apply(canSee, canMove);
		boolean effectiveCanMove = canSee && canMove;
		setCanMoveInStoppedTime(effectiveCanMove);
		Minecraft mc = Minecraft.getInstance();
		partialTickStoppedAt = effectiveCanMove && mc.player != null
				? mc.getTimer().getGameTimeDeltaPartialTick(true)
				: 0.0F;
		ModShaders shaders = ModShaders.getInstance();
		if (shaders != null && shaders.timeStopShaderManager != null) {
			if (canSee) {
				shaders.timeStopShaderManager.requestShaderRestart();
			}
			else {
				shaders.timeStopShaderManager.reset();
			}
		}
	}

	private static void setCanMoveInStoppedTime(boolean canMove) {
		Minecraft mc = Minecraft.getInstance();
		if (canMove || mc.player == null) {
			clearLockedRotation();
		}
		else {
			lockRotation(mc.player);
		}
	}

	private static void lockRotation(LivingEntity player) {
		lockedYRot = player.getYRot();
		lockedXRot = player.getXRot();
	}

	private static void clearLockedRotation() {
		lockedYRot = null;
		lockedXRot = null;
	}

	public static void applyLockedRotation(LivingEntity player) {
		if (player == null) {
			return;
		}
		if (lockedYRot != null) {
			player.setYRot(lockedYRot);
			player.yRotO = lockedYRot;
		}
		if (lockedXRot != null) {
			player.setXRot(lockedXRot);
			player.xRotO = lockedXRot;
		}
	}

	public static boolean isTimeStoppedStatic() {
		return isTimeStoppedAtClientPlayer(Minecraft.getInstance());
	}

	public static boolean shouldFreezeVisualTick() {
		return isTimeStoppedStatic();
	}

	public static float getConstantWorldPartialTick(float normalPartialTick) {
		return isTimeStoppedStatic() ? 1.0F : normalPartialTick;
	}

	private static boolean shouldFreezeClientTimerForVision() {
		return isTimeStoppedStatic() && TimeStopClientAwareness.isVisionRestricted();
	}

	private static void pauseClientIfVisionRestricted(Minecraft mc) {
		if (TimeStopClientAwareness.isVisionRestricted()) {
			((MinecraftTimeStopAccessor) mc).jojo_ripples$setPause(true);
		}
	}

	public static float getConstantEntityPartialTick(Entity entity, float normalPartialTick) {
		if (entity != null && isEntityVisuallyFrozen(entity)) {
			return partialTickStoppedAt;
		}
		return normalPartialTick;
	}

	public static boolean isEntityVisuallyFrozen(Entity entity) {
		if (entity == null || entity.isRemoved() || !isTimeStopped(entity)) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (entity == mc.player) {
			return !TimeStopClientAwareness.canMove();
		}
		if (entity instanceof StandEntity stand) {
			LivingEntity user = stand.getUser();
			return user == null || isEntityVisuallyFrozen(user);
		}
		if (entity instanceof SoulEntity soul) {
			LivingEntity origin = soul.getOriginEntity();
			return origin == null || isEntityVisuallyFrozen(origin);
		}
		if (entity instanceof KnifeEntity knife) {
			return !knife.canMoveInStoppedTime();
		}
		if (entity instanceof OwnerBoundProjectileEntity ownerBound) {
			if (ownerBound.canTickInStoppedTime()) {
				return false;
			}
			LivingEntity owner = ownerBound.getOwner();
			return owner == null || isEntityVisuallyFrozen(owner);
		}
		if (JojoModConfig.getCommonConfigInstance(entity.level().isClientSide()).endermenBeyondTimeSpace.get()
				&& ModInteractionUtil.isEntityEnderman(entity)) {
			return false;
		}
		return !(entity instanceof LivingEntity living && living.hasEffect(ModStatusEffects.TIME_STOP));
	}

	public static void refreshMovementInTimeStop(Entity entity, ChunkPos chunkPos, boolean canMove) {
		if (entity == null || !isTimeStoppedAtChunk(chunkPos)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (entity == mc.player) {
			boolean effectiveCanMove = TimeStopClientAwareness.canSee() && canMove;
			TimeStopClientAwareness.apply(TimeStopClientAwareness.canSee(), effectiveCanMove);
			setCanMoveInStoppedTime(effectiveCanMove);
			partialTickStoppedAt = effectiveCanMove ? mc.getTimer().getGameTimeDeltaPartialTick(true) : 0.0F;
		}
		alignEntityInterpolationToCurrent(entity);
	}

	private static void alignEntityInterpolationToCurrent(Entity entity) {
		entity.xo = entity.getX();
		entity.yo = entity.getY();
		entity.zo = entity.getZ();
		entity.xOld = entity.getX();
		entity.yOld = entity.getY();
		entity.zOld = entity.getZ();
		entity.yRotO = entity.getYRot();
		entity.xRotO = entity.getXRot();
	}

	public static boolean shouldCancelSound(SoundInstance sound) {
		if (!isTimeStoppedStatic() || sound == null || sound.getAttenuation() != SoundInstance.Attenuation.LINEAR) {
			return false;
		}
		return !TimeStopClientAwareness.canSee()
				|| sound.getSource() == SoundSource.WEATHER
				|| sound.getSource() == SoundSource.BLOCKS;
	}

	public static boolean shouldFreezeHurtCamera(Entity cameraEntity) {
		return cameraEntity != null && isEntityVisuallyFrozen(cameraEntity);
	}

	private static boolean isTimeStoppedAtClientPlayer(Minecraft mc) {
		return mc.level != null && mc.player != null && isTimeStopped(mc.player);
	}

	private static boolean isTimeStopped(Entity entity) {
		return isTimeStoppedAtChunk(new ChunkPos(entity.blockPosition()));
	}

	private static boolean isTimeStoppedAtChunk(ChunkPos chunkPos) {
		return TimeStopState.getClientDisplayInstance(chunkPos).isPresent();
	}

	private static void applyTimeStopFloatClientPrediction(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		boolean inputHeld = isTimeStopFloatInput(mc, player);
		TimeStopState.Instance instance = TimeStopState.getClientDisplayInstance(new ChunkPos(player.blockPosition()))
				.orElse(null);
		if (instance == null || instance.userId() != player.getId() || instance.isStartupSettling()) {
			TimeStopState.clearTimeStopFloat(player);
			return;
		}
		TimeStopState.applyTimeStopFloat(player, inputHeld);
	}

	private static boolean isTimeStopFloatInput(Minecraft mc, LocalPlayer player) {
		return player.input != null && sprintKeyDown(mc) && player.input.jumping;
	}

	private static boolean sprintKeyDown(Minecraft mc) {
		InputConstants.Key key = mc.options.keySprint.getKey();
		if (key.getType() == InputConstants.Type.KEYSYM) {
			return InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
		}
		return mc.options.keySprint.isDown();
	}
}
