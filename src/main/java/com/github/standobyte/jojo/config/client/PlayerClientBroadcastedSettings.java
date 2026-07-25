package com.github.standobyte.jojo.config.client;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.entityattachment.SynchronizablePlayerData;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;

import com.github.standobyte.jojo.network.c2s.ClBroadcastedModSettingsPacket;
import com.github.standobyte.jojo.network.s2c.TrPlayerModSettingsPacket;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

public class PlayerClientBroadcastedSettings implements SynchronizablePlayerData, INBTSerializable<CompoundTag> {
	public static final float NO_COOLDOWN_TIME_STOP_STAMINA_COST_MULTIPLIER = 3.0F;
	public static final float NO_COOLDOWN_TIME_STOP_STAMINA_REGEN_MULTIPLIER = 0.2F;
	private static final Map<Integer, PlayerClientBroadcastedSettings> REMOTE_SETTINGS = new ConcurrentHashMap<>();

	@Nullable private final transient Player owner;
	public HumanoidArm standSide = HumanoidArm.RIGHT;
	public boolean vampireGlowingEyes = true;
	public boolean noStandAbilityCooldown = false;

	public PlayerClientBroadcastedSettings() {
		this.owner = null;
	}

	public PlayerClientBroadcastedSettings(Player owner) {
		this.owner = owner;
		addSynchronization(owner);
	}


	public void toBuf(FriendlyByteBuf buf) {
		buf.writeEnum(standSide);
		buf.writeBoolean(vampireGlowingEyes);
		buf.writeBoolean(noStandAbilityCooldown);
	}

	public void fromBuf(FriendlyByteBuf buf) {
		standSide = buf.readEnum(HumanoidArm.class);
		vampireGlowingEyes = buf.readBoolean();
		noStandAbilityCooldown = buf.readBoolean();
	}

	public PlayerClientBroadcastedSettings copy() {
		PlayerClientBroadcastedSettings copy = new PlayerClientBroadcastedSettings();
		copy.copyFrom(this);
		return copy;
	}

	public void copyFrom(PlayerClientBroadcastedSettings other) {
		this.standSide = other.standSide;
		this.vampireGlowingEyes = other.vampireGlowingEyes;
		this.noStandAbilityCooldown = other.noStandAbilityCooldown;
	}


	public void broadcastToServer() {
		PacketDistributor.sendToServer(new ClBroadcastedModSettingsPacket(this.copy()));
	}

	public void syncToAll(Player player) {
		PacketDistributor.sendToPlayersTrackingEntity(player, new TrPlayerModSettingsPacket(player.getId(), this.copy()));
	}

	public void syncToTracking(Player player, ServerPlayer tracking) {
		PacketDistributor.sendToPlayer(tracking, new TrPlayerModSettingsPacket(player.getId(), this.copy()));
	}

	@Override
	public void syncToPlayer(ServerPlayer entityAsPlayer) {
		if (owner != null) {
			syncToTracking(owner, entityAsPlayer);
		}
	}

	@Override
	public void syncToTracking(ServerPlayer trackingPlayer) {
		if (owner != null) {
			syncToTracking(owner, trackingPlayer);
		}
	}

	@Override
	public void onPlayerClone(Player newPlayer, boolean wasDeath) {
		PlayerClientBroadcastedSettings newSettings = getServerStored(newPlayer)
				.orElseGet(() -> newPlayer.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()));
		newSettings.copyFrom(this);
	}

	@Override
	public CompoundTag serializeNBT(Provider provider) {
		CompoundTag nbt = new CompoundTag();
		nbt.putString("StandSide", standSide.getSerializedName());
		nbt.putBoolean("VampireGlowingEyes", vampireGlowingEyes);
		nbt.putBoolean("NoStandAbilityCooldown", noStandAbilityCooldown);
		return nbt;
	}

	@Override
	public void deserializeNBT(Provider provider, CompoundTag nbt) {
		standSide = HumanoidArm.LEFT.getSerializedName().equals(nbt.getString("StandSide")) ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
		vampireGlowingEyes = nbt.getBoolean("VampireGlowingEyes");
		noStandAbilityCooldown = nbt.getBoolean("NoStandAbilityCooldown");
	}

	public static boolean isNoStandAbilityCooldownEnabled(StandPower standPower) {
		if (standPower == null || !standPower.hasPower()) {
			return false;
		}
		if (standPower.getUser() instanceof Player player) {
			return getPlayerSettings(player).map(settings -> settings.noStandAbilityCooldown).orElse(false);
		}
		return false;
	}

	public static float getTimeStopStaminaCostMultiplier(StandPower standPower) {
		return isNoStandAbilityCooldownEnabled(standPower) ? NO_COOLDOWN_TIME_STOP_STAMINA_COST_MULTIPLIER : 1.0F;
	}

	public static float getTimeStopStaminaRegenMultiplier(StandPower standPower) {
		return isNoStandAbilityCooldownEnabled(standPower) ? NO_COOLDOWN_TIME_STOP_STAMINA_REGEN_MULTIPLIER : 1.0F;
	}

	public static Optional<PlayerClientBroadcastedSettings> getPlayerSettings(Player player) {
		if (player.isLocalPlayer()) {
			return Optional.of(ClientModSettings.getSettingsReadOnly().broadcasted);
		}
		if (!player.level().isClientSide()) {
			return getServerStored(player);
		}
		return getRemoteSettings(player.getId());
	}

	public static Optional<PlayerClientBroadcastedSettings> getServerStored(Player player) {
		AttachmentType<PlayerClientBroadcastedSettings> type = ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get();
		return player.hasData(type) ? Optional.of(player.getData(type)) : Optional.empty();
	}

	public static Optional<PlayerClientBroadcastedSettings> getRemoteSettings(int entityId) {
		return Optional.ofNullable(REMOTE_SETTINGS.get(entityId));
	}

	public static void putRemoteSettings(int entityId, PlayerClientBroadcastedSettings settings) {
		REMOTE_SETTINGS.put(entityId, settings);
	}
}
