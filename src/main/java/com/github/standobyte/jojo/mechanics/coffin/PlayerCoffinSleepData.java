package com.github.standobyte.jojo.mechanics.coffin;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.block.WoodenCoffinBlock;
import com.github.standobyte.jojo.entityattachment.SynchronizablePlayerData;
import com.github.standobyte.jojo.entityattachment.TickingEntityData;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.network.s2c.TrPlayerCoffinSleepPacket;
import com.github.standobyte.jojo.util.reflection.CommonReflection;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

public class PlayerCoffinSleepData implements SynchronizablePlayerData, TickingEntityData, INBTSerializable<CompoundTag> {
	@Nullable private final Player owner;
	private boolean coffinPreventDayTimeSkip;

	public PlayerCoffinSleepData() {
		this.owner = null;
	}

	public PlayerCoffinSleepData(Player owner) {
		this.owner = owner;
		addSynchronization(owner);
		addTicking(owner);
	}

	public boolean preventsDayTimeSkip() {
		return coffinPreventDayTimeSkip;
	}

	public void setFromPacket(boolean coffinPreventDayTimeSkip) {
		this.coffinPreventDayTimeSkip = coffinPreventDayTimeSkip;
	}

	public void onSleepingInCoffin(boolean isVampireRespawning) {
		setCoffinPreventDayTimeSkip(isVampireRespawning);
	}

	public void onWakeUp() {
		setCoffinPreventDayTimeSkip(false);
	}

	private void setCoffinPreventDayTimeSkip(boolean coffinPreventDayTimeSkip) {
		if (this.coffinPreventDayTimeSkip == coffinPreventDayTimeSkip) {
			return;
		}
		this.coffinPreventDayTimeSkip = coffinPreventDayTimeSkip;
		if (owner instanceof ServerPlayer serverPlayer) {
			PacketDistributor.sendToPlayer(serverPlayer, new TrPlayerCoffinSleepPacket(coffinPreventDayTimeSkip));
		}
	}

	@Override
	public void tick() {
		if (owner == null) {
			return;
		}
		if (!owner.isSleeping()) {
			if (coffinPreventDayTimeSkip) {
				onWakeUp();
			}
			return;
		}
		if (coffinPreventDayTimeSkip && WoodenCoffinBlock.isSleepingInCoffin(owner) && !isSunny(owner.level())
				&& owner instanceof ServerPlayer serverPlayer) {
			CommonReflection.setSleepCounter(serverPlayer, 0);
		}
	}

	private static boolean isSunny(Level level) {
		return level.dimensionType().hasSkyLight()
				&& !level.dimensionType().hasCeiling()
				&& level.isDay()
				&& !level.isRaining()
				&& !level.isThundering();
	}

	@Override
	public void syncToPlayer(ServerPlayer entityAsPlayer) {
		if (owner == entityAsPlayer) {
			PacketDistributor.sendToPlayer(entityAsPlayer, new TrPlayerCoffinSleepPacket(coffinPreventDayTimeSkip));
		}
	}

	@Override
	public void syncToTracking(ServerPlayer trackingPlayer) {}

	@Override
	public void onPlayerClone(Player newPlayer, boolean wasDeath) {
		PlayerCoffinSleepData newData = newPlayer.getData(ModDataAttachmentTypes.PLAYER_COFFIN_SLEEP.get());
		newData.coffinPreventDayTimeSkip = this.coffinPreventDayTimeSkip;
	}

	@Override
	public CompoundTag serializeNBT(Provider provider) {
		CompoundTag nbt = new CompoundTag();
		nbt.putBoolean("CoffinRespawn", coffinPreventDayTimeSkip);
		return nbt;
	}

	@Override
	public void deserializeNBT(Provider provider, CompoundTag nbt) {
		coffinPreventDayTimeSkip = nbt.getBoolean("CoffinRespawn");
	}

	public static PlayerCoffinSleepData get(Player player) {
		return player.getData(ModDataAttachmentTypes.PLAYER_COFFIN_SLEEP.get());
	}

	public static void onSleepingInCoffin(Player player, boolean isVampireRespawning) {
		get(player).onSleepingInCoffin(isVampireRespawning);
	}

	public static void onWakeUp(Player player) {
		get(player).onWakeUp();
	}
}
