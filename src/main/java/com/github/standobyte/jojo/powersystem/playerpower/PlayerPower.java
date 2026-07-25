package com.github.standobyte.jojo.powersystem.playerpower;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.network.s2c.TrPowerTypePacket;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.playerpower.packet.TrPlayerPowerLeapCooldownPacket;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;

public class PlayerPower extends Power<PlayerPower> {
	private static final ResourceLocation LEGACY_PILLAR_MAN_ID = JojoMod.resLoc("pillar_man");
	private static final ResourceLocation PILLAR_MAN_ID = JojoMod.resLoc("pillarman");
	protected Optional<PlayerPowerType<?>> curPowerType = Optional.empty();
	private int leapCooldown;

	public PlayerPower(LivingEntity user) {
		super(user);
	}

	@Override
	public PlayerPowerType<?> getPowerType() {
		return curPowerType.orElse(null);
	}

	@Override
	public void tick() {
		super.tick();
		if (leapCooldown > 0) {
			--leapCooldown;
		}
	}
	
	public void setPowerType(@Nullable PlayerPowerType<?> type) {
		PlayerPowerType<?> old = getPowerType();
		if (old != type) {
			this.curPowerType = Optional.ofNullable(type);
			if (!user.level().isClientSide()) {
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrPowerTypePacket(user.getId(), type));
				if (user instanceof ServerPlayer player && type != null) {
					ModCriteriaTriggers.triggerGetPower(player, this);
				}
			}
		}
		onSetPowerType(old, type);
	}
	
	@Override
	public boolean hasPower() {
		return curPowerType.isPresent();
	}

	public boolean isLeapUnlocked() {
		PlayerPowerType<?> powerType = getPowerType();
		return powerType != null && powerType.isLeapUnlocked(this);
	}

	public boolean canLeap() {
		PlayerPowerType<?> powerType = getPowerType();
		if (powerType == null || leapCooldown > 0 || !isLeapUnlocked()) {
			return false;
		}
		return powerType.hasLeapEnergy(this, powerType.getLeapEnergyCost(this));
	}

	public void onLeap() {
		PlayerPowerType<?> powerType = getPowerType();
		if (powerType == null) {
			return;
		}
		setLeapCooldown(getLeapCooldownPeriod());
		powerType.consumeLeapEnergy(this, powerType.getLeapEnergyCost(this));
		powerType.onLeap(this);
	}

	public float leapStrength() {
		PlayerPowerType<?> powerType = getPowerType();
		if (powerType == null) {
			return 0.0F;
		}
		float strength = powerType.getLeapStrength(this);
		AttributeInstance movementSpeed = user.getAttribute(Attributes.MOVEMENT_SPEED);
		if (movementSpeed != null && movementSpeed.getBaseValue() > 0.0D) {
			strength *= (float) (movementSpeed.getValue() / movementSpeed.getBaseValue());
		}
		return strength;
	}

	public int getLeapCooldownPeriod() {
		PlayerPowerType<?> powerType = getPowerType();
		return powerType != null ? powerType.getLeapCooldownPeriod(this) : 0;
	}

	public int getLeapCooldown() {
		return leapCooldown;
	}

	public void setLeapCooldown(int cooldown) {
		int newCooldown = Math.max(cooldown, 0);
		boolean changed = this.leapCooldown != newCooldown;
		this.leapCooldown = newCooldown;
		if (changed) {
			syncLeapCooldown();
		}
	}

	public void syncLeapCooldown() {
		if (!user.level().isClientSide() && user instanceof ServerPlayer player) {
			PacketDistributor.sendToPlayer(player,
					new TrPlayerPowerLeapCooldownPacket(user.getId(), leapCooldown));
		}
	}
	
	@SuppressWarnings("unchecked")
	public <T extends PlayerPowerType<D>, D extends PlayerPowerData> Optional<D> getCurTypeData(Supplier<T> matchCurrentType) {
		return this.curPowerType
				.filter(curType -> matchCurrentType != null && matchCurrentType.get() == curType)
				.map(type -> (D) getPowerTypeData(type));
	}
	
	@Override
	public PowerClass<PlayerPower> getPowerClass() {
		return PowerClass.PLAYER_POWER;
	}
	
	/**
	 * @deprecated Placeholder. Energy will be kept in PowerData subclasses (Hamon energy in HamonData, vampire energy in VampirismData)
	 */
	@Deprecated
	public void addEnergy(float energy) {}


	@Override
	public void syncToPlayer(ServerPlayer user) {
		PacketDistributor.sendToPlayer(user, new TrPowerTypePacket(user.getId(), getPowerType()));
		super.syncToPlayer(user);
		PacketDistributor.sendToPlayer(user,
				new TrPlayerPowerLeapCooldownPacket(this.user.getId(), leapCooldown));
	}

	@Override
	public void syncToTracking(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new TrPowerTypePacket(user.getId(), getPowerType()));
		super.syncToTracking(player);
	}
	
	@Override
	public void onPlayerCloneData(PlayerPower newEntityData, boolean wasDeath) {
		super.onPlayerCloneData(newEntityData, wasDeath);
		PlayerPowerType<?> powerType = getPowerType();
		if (powerType != null && (!wasDeath || powerType.keepOnDeath(this))) {
			newEntityData.curPowerType = this.curPowerType;
			newEntityData.moveset = newEntityData.initMoveset(powerType);
			newEntityData.leapCooldown = this.leapCooldown;
		}
		else if (wasDeath) {
			newEntityData.curPowerType = Optional.empty();
			newEntityData.moveset = null;
			newEntityData.powerData = new HashMap<>();
			newEntityData.leapCooldown = 0;
		}
	}
	
	
	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag nbt = super.serializeNBT(provider);
		curPowerType.ifPresent(curType -> {
			nbt.putString("PowerType", curType.getId().toString());
		});
		nbt.putInt("LeapCd", leapCooldown);
		return nbt;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
		super.deserializeNBT(provider, nbt);
		ResourceLocation powerTypeId = ResourceLocation.parse(nbt.getString("PowerType"));
		if (LEGACY_PILLAR_MAN_ID.equals(powerTypeId)) {
			powerTypeId = PILLAR_MAN_ID;
		}
		PlayerPowerType<?> powerType = JojoRegistries.PLAYER_POWER_TYPES_REG.get(powerTypeId);
		this.curPowerType = Optional.ofNullable(powerType);
		leapCooldown = nbt.getInt("LeapCd");
	}
	
	
	@Nullable
	public static PlayerPower get(LivingEntity entity) {
		return PowerClass.PLAYER_POWER.get(entity);
	}
	
	public static Optional<PlayerPower> getOptional(LivingEntity entity) {
		return PowerClass.PLAYER_POWER.getOptional(entity);
	}

	public static <T extends PlayerPowerType<D>, D extends PlayerPowerData> Optional<D> getPowerData(LivingEntity user, Supplier<T> specificType) {
		PlayerPower playerPower = get(user);
		return playerPower != null ? playerPower.getCurTypeData(specificType) : Optional.empty();
	}

}
