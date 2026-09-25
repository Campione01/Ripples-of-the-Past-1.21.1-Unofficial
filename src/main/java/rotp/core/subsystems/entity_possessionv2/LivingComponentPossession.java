package rotp.core.subsystems.entity_possessionv2;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;
import rotp.core.entityattachment.SynchronizableEntityData;
import rotp.core.entityattachment.TickingEntityData;
import rotp.core.impl.stands.crazydiamond.AngeloRockEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class LivingComponentPossession implements TickingEntityData, SynchronizableEntityData {
	public static final String PILLARMAN_HIDE_IN_ENTITY = "pillarman_hide_in_entity";

	private final Entity thisEntity;
	private Set<LivingComponentPossession> possessingEntities = new HashSet<>();
	private Entity possessTarget = null;
	private Optional<GameType> prePossessGameMode = Optional.empty();
	public String possessionType;
	// server: Shift state last tick, so only a new press leaves the possession
	private boolean shiftWasDown;
	// server: death type when a player leaves an Angelo rock (null = rock_broken)
	@Nullable private ResourceKey<DamageType> rockReleaseDeath;
	
	public LivingComponentPossession(Entity entity) {
		this.thisEntity = entity;
		addTicking(entity);
		addSynchronization(entity);
	}
	
	
	@Nullable
	public static Entity getEntityPossessedBy(Entity possessing) {
		AttachmentType<LivingComponentPossession> type = ModDataAttachmentTypes.ENTITY_POSSESSION.get();
		if (!possessing.hasData(type)) return null;
		
		LivingComponentPossession possessing_ = possessing.getData(type);
		return possessing_.possessTarget;
	}
	
	public static boolean isPossessingSomeone(Entity entity) {
		return getEntityPossessedBy(entity) != null;
	}

	public static Optional<GameType> getActualGameModeWhilePossessing(Player player) {
		AttachmentType<LivingComponentPossession> type = ModDataAttachmentTypes.ENTITY_POSSESSION.get();
		if (!player.hasData(type)) {
			return Optional.empty();
		}
		LivingComponentPossession data = player.getData(type);
		return data.possessTarget != null ? data.prePossessGameMode : Optional.empty();
	}
	
	@Nullable
	public static Set<LivingComponentPossession> getEntitiesPossessing(Entity target) {
		AttachmentType<LivingComponentPossession> type = ModDataAttachmentTypes.ENTITY_POSSESSION.get();
		if (!target.hasData(type)) return null;
		
		LivingComponentPossession target_ = target.getData(type);
		return target_.possessingEntities;
	}
	
	
	@Override
	public void tick() {
		// 1.16 followed the possessed entity in ServerPlayer.tick, which a player stopped in time skipped
		if (TimeStopState.shouldFreezeOnServer(thisEntity)) {
			return;
		}
		if (possessTarget != null) {
			if (!possessTarget.isAlive()) {
				this.setPossessionTarget(null, null);
			}
			else if (!thisEntity.level().isClientSide() && thisEntity instanceof ServerPlayer serverPlayer) {
				keepServerPlayerInPossessionGameMode(serverPlayer);
				// 1.16 left the asAlive possession on sneak (ServerPlayer.setCamera(this))
				if (sneakPressed(serverPlayer) && PILLARMAN_HIDE_IN_ENTITY.equals(possessionType)) {
					stopPossession();
				}
			}
		}

		updatePosition();
	}
	
	public void updatePosition() {
		if (possessTarget != null) {
			thisEntity.noPhysics = true;
			thisEntity.setOnGround(false);
			thisEntity.fallDistance = 0;
			
			thisEntity.absMoveTo(
					possessTarget.getX(), 
					possessTarget.getY(), 
					possessTarget.getZ(), 
					possessTarget.getYRot(), 
					possessTarget.getXRot());
			thisEntity.xRotO = possessTarget.xRotO;
			thisEntity.yRotO = possessTarget.yRotO;
			LivingEntity thisAsLiving = (LivingEntity) thisEntity;
			if (possessTarget instanceof LivingEntity livingTarget) {
				thisAsLiving.yHeadRot = livingTarget.yHeadRot;
				thisAsLiving.yBodyRot = livingTarget.yBodyRot;
				thisAsLiving.yHeadRotO = livingTarget.yHeadRotO;
				thisAsLiving.yBodyRotO = livingTarget.yBodyRotO;
			}
			else {
				thisAsLiving.yHeadRot = thisAsLiving.getYRot();
				thisAsLiving.yBodyRot = thisAsLiving.getYRot();
			}
			
			if (thisEntity instanceof ServerPlayer serverPlayer) {
				serverPlayer.serverLevel().getChunkSource().move(serverPlayer);
			}
		}
	}
	
	public static void setPossessionTarget(LivingEntity possessing, @Nullable Entity target, @Nullable String possessionType) {
		LivingComponentPossession data =  possessing.getData(ModDataAttachmentTypes.ENTITY_POSSESSION.get());
		data.setPossessionTarget(target, possessionType);
	}

	public static void setPossessionTargetFromPacket(LivingEntity possessing, @Nullable Entity target,
			@Nullable String possessionType, Optional<GameType> prePossessGameMode) {
		LivingComponentPossession data = possessing.getData(ModDataAttachmentTypes.ENTITY_POSSESSION.get());
		data.setPossessionTarget(target, possessionType, prePossessGameMode);
	}
	
	public void stopPossession() {
		setPossessionTarget(null, null);
	}

	// Angelo rock Respawn button: leave the rock and die as rock_respawn instead of rock_broken
	public static void respawnFromAngeloRock(ServerPlayer player) {
		LivingComponentPossession data = player.getData(ModDataAttachmentTypes.ENTITY_POSSESSION.get());
		data.rockReleaseDeath = ModDamageTypes.ROCK_RESPAWN;
		try {
			data.stopPossession();
		}
		finally {
			data.rockReleaseDeath = null;
		}
	}

	private boolean sneakPressed(ServerPlayer player) {
		boolean down = player.isShiftKeyDown();
		boolean pressed = down && !shiftWasDown;
		shiftWasDown = down;
		return pressed;
	}
	
	public void setPossessionTarget(@Nullable Entity target, @Nullable String possessionType) {
		setPossessionTarget(target, possessionType, null);
	}

	private void setPossessionTarget(@Nullable Entity target, @Nullable String possessionType,
			@Nullable Optional<GameType> syncedPrePossessGameMode) {
		while (target instanceof PartEntity<?> partEntity) target = partEntity.getParent();
		Entity prevTarget = this.possessTarget;
		if (thisEntity.level().isClientSide()) {
			this.prePossessGameMode = syncedPrePossessGameMode != null ? syncedPrePossessGameMode : Optional.empty();
		}
		else if (thisEntity instanceof ServerPlayer serverPlayer) {
			if (target != null) {
				startServerPlayerPossessionGameMode(serverPlayer);
				shiftWasDown = serverPlayer.isShiftKeyDown();
			}
			else if (this.possessTarget != null) {
				restoreServerPlayerGameMode(serverPlayer);
			}
		}

		if (this.possessTarget != target) {
			if (this.possessTarget != null) {
				LivingComponentPossession oldTargetData = this.possessTarget.getData(ModDataAttachmentTypes.ENTITY_POSSESSION.get());
				oldTargetData.possessingEntities.remove(this);
				broadcastPossessionUpdate(this.possessTarget, this.thisEntity.getId(), 0, possessionType, Optional.empty());
			}

			if (target != null) {
				LivingComponentPossession newTargetData = target.getData(ModDataAttachmentTypes.ENTITY_POSSESSION.get());
				newTargetData.possessingEntities.add(this);
				broadcastPossessionUpdate(target, this.thisEntity.getId(), target.getId(), possessionType, prePossessGameMode);
			}
		}
		
		this.possessTarget = target;
		this.possessionType = possessionType;
		if (!thisEntity.level().isClientSide()) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(thisEntity, new TrPossessEntityPacket(
					thisEntity.getId(), possessTarget != null ? possessTarget.getId() : 0, possessionType, prePossessGameMode));
		}
		
		if (target != null) {
			thisEntity.stopRiding();
			
			if (thisEntity.level() instanceof ServerLevel serverLevel) {
				thisEntity.teleportTo(serverLevel, 
						target.getX(), target.getY(), target.getZ(), 
						Set.of(), target.getYRot(), target.getXRot());
				if (thisEntity instanceof ServerPlayer serverPlayer) {
					serverLevel.getChunkSource().move(serverPlayer);
					serverPlayer.connection.send(new ClientboundSetCameraPacket(target));
					serverPlayer.connection.resetPosition();
				}
			}
		}
		else {
			thisEntity.noPhysics = false;
			if (thisEntity instanceof ServerPlayer serverPlayer) {
				serverPlayer.connection.send(new ClientboundSetCameraPacket(serverPlayer));
				if (prevTarget instanceof AngeloRockEntity) {
					killReleasedFromRock(serverPlayer);
				}
			}
		}
	}

	// 1.16 PlayerEntityMixin: leaving an Angelo rock kills a still-alive player (game mode already restored)
	private void killReleasedFromRock(ServerPlayer player) {
		if (player.isAlive()) {
			player.invulnerableTime = 0;
			player.hurt(DamageUtil.make(player.level(),
					rockReleaseDeath != null ? rockReleaseDeath : ModDamageTypes.ROCK_BROKEN), Float.MAX_VALUE);
		}
	}

	private void startServerPlayerPossessionGameMode(ServerPlayer serverPlayer) {
		if (prePossessGameMode.isEmpty()) {
			prePossessGameMode = Optional.of(serverPlayer.gameMode.getGameModeForPlayer());
		}
		if (serverPlayer.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
			serverPlayer.setGameMode(GameType.SPECTATOR);
		}
	}

	private void keepServerPlayerInPossessionGameMode(ServerPlayer serverPlayer) {
		GameType currentGameMode = serverPlayer.gameMode.getGameModeForPlayer();
		if (currentGameMode != GameType.SPECTATOR) {
			prePossessGameMode = Optional.of(currentGameMode);
			serverPlayer.setGameMode(GameType.SPECTATOR);
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(thisEntity, new TrPossessEntityPacket(
					thisEntity.getId(), possessTarget.getId(), possessionType, prePossessGameMode));
		}
	}

	private void restoreServerPlayerGameMode(ServerPlayer serverPlayer) {
		prePossessGameMode.ifPresent(serverPlayer::setGameMode);
		prePossessGameMode = Optional.empty();
	}
	
	@Override
	public void syncToTracking(ServerPlayer trackingPlayer) {
		if (possessTarget != null) {
			PacketDistributor.sendToPlayer(trackingPlayer, new TrPossessEntityPacket(
					thisEntity.getId(), possessTarget.getId(), possessionType, prePossessGameMode));
		}
		for (LivingComponentPossession possessor : possessingEntities) {
			if (possessor.thisEntity != null && possessor.thisEntity.isAlive()) {
				PacketDistributor.sendToPlayer(trackingPlayer, new TrPossessEntityPacket(
						possessor.thisEntity.getId(), thisEntity.getId(), possessor.possessionType,
						possessor.prePossessGameMode));
			}
		}
	}

	// 1.16 beforeLivingDeath (jojoOnPossessingDead): a dying possessor leaves its target, so the restored game mode
	// is what respawn copies. LOWEST and not receiveCanceled: cheat death / Angelo rock sealing keep the possession.
	@EventBusSubscriber(modid = JojoMod.MOD_ID)
	public static final class DeathEvents {
		private DeathEvents() {}

		@SubscribeEvent(priority = EventPriority.LOWEST)
		public static void onPossessorDeath(LivingDeathEvent event) {
			LivingEntity dead = event.getEntity();
			if (!dead.level().isClientSide() && getEntityPossessedBy(dead) != null) {
				// a rock possessor is no longer alive here, so killReleasedFromRock does not hit it again
				dead.getData(ModDataAttachmentTypes.ENTITY_POSSESSION.get()).stopPossession();
			}
		}
	}

	private static void broadcastPossessionUpdate(Entity target, int possessorId, int targetIdOrZero,
			@Nullable String possessionType, Optional<GameType> prePossessGameMode) {
		if (target.level().isClientSide()) return;
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,
				new TrPossessEntityPacket(possessorId, targetIdOrZero, possessionType, prePossessGameMode));
	}

}
