package com.github.standobyte.jojo.powersystem.playerpower;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.api.playerpower.PlayerPowerDelegations;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.api.leap.LeapAccessPolicies;
import com.github.standobyte.jojo.api.leap.LeapSource;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.network.s2c.TrPowerTypePacket;
import com.github.standobyte.jojo.powersystem.Moveset;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.PowerType;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.playerpower.packet.TrPlayerPowerLeapCooldownPacket;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.mojang.datafixers.util.Either;

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
	@Nullable
	private PlayerPowerData temporarilySuspendedData;
	@Nullable
	private ResourceLocation temporarilySuspendedTypeId;

	public PlayerPower(LivingEntity user) {
		super(user);
	}

	@Override
	public PlayerPowerType<?> getPowerType() {
		return curPowerType.orElse(null);
	}

	/**
	 * Checks whether a new type can be granted without forcing replacement.
	 */
	public boolean canGetPower(PlayerPowerType<?> type) {
		if (type == null) {
			return false;
		}
		PlayerPowerType<?> current = getPowerType();
		if (current == null) {
			return true;
		}
		try {
			return current.isReplaceableWith(type);
		}
		catch (RuntimeException error) {
			JojoMod.getLogger().error(
					"PlayerPower replacement policy {} -> {} failed for {}.",
					current.getId(),
					type.getId(),
					user.getScoreboardName(),
					error);
			return false;
		}
	}

	/**
	 * Grants a type on the logical server when the current type permits it.
	 */
	public boolean trySetPowerType(PlayerPowerType<?> type) {
		if (user.level().isClientSide() || !canGetPower(type)) {
			return false;
		}
		setPowerType(type);
		return true;
	}

	/**
	 * Resolves the safe target multiplier used by the Resolve award path.
	 */
	public float getTargetResolveMultiplier(
			StandPower attackingStand) {
		PlayerPowerType<?> current = getPowerType();
		if (current == null || attackingStand == null) {
			return 1.0F;
		}
		try {
			float multiplier = current.getTargetResolveMultiplier(
					this, attackingStand);
			if (Float.isFinite(multiplier) && multiplier >= 0.0F) {
				return multiplier;
			}
			JojoMod.getLogger().error(
					"PlayerPower target Resolve multiplier {} returned {} for {}.",
					current.getId(),
					multiplier,
					user.getScoreboardName());
		}
		catch (RuntimeException error) {
			JojoMod.getLogger().error(
					"PlayerPower target Resolve multiplier {} failed for {}.",
					current.getId(),
					user.getScoreboardName(),
					error);
		}
		return 0.0F;
	}

	@Override
	public void tick() {
		super.tick();
		PlayerPowerData suspendedData =
				resolveTemporarilySuspendedData();
		if (suspendedData != null) {
			if (isDelegating(suspendedData.getPowerType())) {
				suspendedData.tick(this);
			}
			else {
				suspendedData
						.tickWhileTemporarilySuspended(this);
			}
		}
		if (leapCooldown > 0) {
			--leapCooldown;
		}
	}
	
	public void setPowerType(@Nullable PlayerPowerType<?> type) {
		PlayerPowerType<?> old = getPowerType();
		if (old != type) {
			temporarilySuspendedData = null;
			temporarilySuspendedTypeId = null;
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

	@ApiStatus.Internal
	public void applyTrackedPowerType(
			@Nullable PlayerPowerType<?> type,
			@Nullable PlayerPowerType<?> retainedType) {
		if (!user.level().isClientSide()) {
			return;
		}
		curPowerType = Optional.ofNullable(type);
		temporarilySuspendedData = null;
		temporarilySuspendedTypeId =
				retainedType != null ? retainedType.getId() : null;
		moveset = initMoveset(type);
	}

	/**
	 * Installs a fresh temporary power without clearing the retained power.
	 * Addons should call this through PlayerPowerTransitions.
	 */
	@ApiStatus.Internal
	public boolean beginTemporaryTransition(
			PlayerPowerType<?> temporaryType) {
		if (temporaryType == null || user.level().isClientSide()) {
			return false;
		}
		if (temporarilySuspendedTypeId != null) {
			return false;
		}
		PlayerPowerType<?> previousType = getPowerType();
		PowerData previousUntypedData = getCurTypeData();
		if (previousType == null
				|| previousUntypedData == null
				|| previousType == temporaryType
				|| !(previousUntypedData
						instanceof PlayerPowerData previousData)) {
			return false;
		}

		ResourceLocation temporaryId = temporaryType.getId();
		Either<PowerData, CompoundTag> previousTemporaryEntry =
				powerData.get(temporaryId);
		PlayerPowerData temporaryData =
				temporaryType.newDataInstance();
		boolean delegates = PlayerPowerDelegations.delegates(
				this, temporaryType, previousType);
		TemporarySuspensionAttempt suspension =
				new TemporarySuspensionAttempt();
		try {
			if (!delegates) {
				suspension.run(() ->
						previousData.onTemporaryPowerSuspended(
								this, temporaryType));
			}
			powerData.put(temporaryId, Either.left(temporaryData));
			temporarilySuspendedData = previousData;
			temporarilySuspendedTypeId = previousType.getId();
			curPowerType = Optional.of(temporaryType);
			moveset = initMoveset(temporaryType);
			temporaryData.onInit(this);
			temporaryData.onPowerGiven(
					this, previousType, previousData);
		}
		catch (RuntimeException | Error error) {
			try {
				temporaryData.onTemporaryPowerEnded(
						this, this, previousType);
			}
			catch (RuntimeException | Error cleanupError) {
				addSuppressedIfDistinct(error, cleanupError);
			}
			if (previousTemporaryEntry != null) {
				powerData.put(
						temporaryId, previousTemporaryEntry);
			}
			else {
				powerData.remove(temporaryId);
			}
			curPowerType = Optional.of(previousType);
			temporarilySuspendedData = null;
			temporarilySuspendedTypeId = null;
			try {
				moveset = initMoveset(previousType);
			}
			catch (RuntimeException | Error rollbackError) {
				moveset = null;
				addSuppressedIfDistinct(error, rollbackError);
			}
			suspension.restoreAfterFailure(
					() -> previousData.onTemporaryPowerRestored(
							this, temporaryType),
					error);
			JojoMod.getLogger().error(
					"Failed to install temporary PlayerPower {} for {}",
					temporaryId,
					user.getScoreboardName(),
					error);
			return false;
		}

		syncTemporaryTransition(
				temporaryData, true);
		return true;
	}

	static final class TemporarySuspensionAttempt {
		private boolean attempted;

		void run(Runnable suspension) {
			attempted = true;
			suspension.run();
		}

		void restoreAfterFailure(
				Runnable restoration, Throwable failure) {
			if (!attempted) {
				return;
			}
			try {
				restoration.run();
			}
			catch (RuntimeException | Error restoreFailure) {
				addSuppressedIfDistinct(failure, restoreFailure);
			}
		}
	}

	static void addSuppressedIfDistinct(
			Throwable failure, Throwable secondary) {
		if (secondary != failure) {
			failure.addSuppressed(secondary);
		}
	}

	/**
	 * Restores retained data on the same entity without normal clear/grant
	 * callbacks.
	 */
	@ApiStatus.Internal
	public boolean endTemporaryTransition() {
		if (user.level().isClientSide()) {
			return false;
		}
		PlayerPowerType<?> temporaryType = getPowerType();
		PlayerPowerType<?> retainedType =
				getRetainedTemporaryType();
		PlayerPowerData retainedData =
				resolveTemporarilySuspendedData();
		PowerData temporaryUntypedData = getCurTypeData();
		if (temporaryType == null
				|| retainedType == null
				|| retainedData == null
				|| !(temporaryUntypedData
						instanceof PlayerPowerData temporaryData)) {
			return false;
		}
		boolean delegated = PlayerPowerDelegations.delegates(
				this, temporaryType, retainedType);
		Moveset retainedMoveset;
		try {
			retainedMoveset = initMoveset(retainedType);
		}
		catch (RuntimeException | Error error) {
			JojoMod.getLogger().error(
					"Failed to stage retained PlayerPower moveset {} for {}",
					retainedType.getId(),
					user.getScoreboardName(),
					error);
			return false;
		}
		try {
			temporaryData.onTemporaryPowerEnded(
					this, this, retainedType);
		}
		catch (RuntimeException | Error error) {
			JojoMod.getLogger().error(
					"Failed to end temporary PlayerPower {} before restoring {} for {}",
					temporaryType.getId(),
					retainedType.getId(),
					user.getScoreboardName(),
					error);
			return false;
		}

		commitTemporaryRestore(
				powerData,
				temporaryType.getId(),
				retainedType.getId(),
				retainedData);
		curPowerType = Optional.of(retainedType);
		temporarilySuspendedData = null;
		temporarilySuspendedTypeId = null;
		moveset = retainedMoveset;
		if (!delegated) {
			try {
				retainedData.onTemporaryPowerRestored(
						this, temporaryType);
			}
			catch (RuntimeException | Error error) {
				JojoMod.getLogger().error(
						"Failed to refresh retained PlayerPower {} for {}",
						retainedType.getId(),
						user.getScoreboardName(),
						error);
			}
		}
		syncTemporaryTransition(retainedData, false);
		return true;
	}

	/**
	 * Restores retained data after a temporary-power death transition without
	 * invoking either power's normal clear/acquisition callbacks.
	 */
	@ApiStatus.Internal
	public boolean restoreTemporaryTypeData(
			PlayerPowerType<?> restoredType,
			CompoundTag restoredDataNbt,
			int deathTimeLeapCooldown,
			PlayerPower temporarySource,
			@Nullable PlayerPowerType<?> temporaryType) {
		if (restoredType == null
				|| restoredDataNbt == null
				|| temporarySource == null
				|| user.level().isClientSide()) {
			return false;
		}

		PlayerPowerData restoredData;
		Moveset restoredMoveset;
		try {
			restoredData = restoredType.newDataInstance();
			restoredData.onInit(this);
			restoredData.deserializeNBT(
					user.registryAccess(),
					restoredDataNbt.copy());
			restoredMoveset = initMoveset(restoredType);
		}
		catch (RuntimeException | Error error) {
			JojoMod.getLogger().error(
					"Failed to stage retained PlayerPower {} for {}",
					restoredType.getId(),
					user.getScoreboardName(),
					error);
			return false;
		}

		try {
			PowerData temporaryUntypedData =
					temporarySource.getCurTypeData();
			if (!(temporaryUntypedData
					instanceof PlayerPowerData temporaryData)) {
				JojoMod.getLogger().error(
						"Cannot restore PlayerPower {} for {}; temporary source {} has no PlayerPower data",
						restoredType.getId(),
						user.getScoreboardName(),
						temporarySource.getUser()
								.getScoreboardName());
				return false;
			}
			temporaryData.onTemporaryPowerEnded(
					temporarySource, this, restoredType);
		}
		catch (RuntimeException | Error error) {
			JojoMod.getLogger().error(
					"Failed to clean temporary PlayerPower {} before restoring {} for {}",
					temporaryType != null
							? temporaryType.getId()
							: "<unknown>",
					restoredType.getId(),
					user.getScoreboardName(),
					error);
			return false;
		}

		commitTemporaryRestore(
				powerData,
				temporaryType != null
						? temporaryType.getId()
						: null,
				restoredType.getId(),
				restoredData);
		curPowerType = Optional.of(restoredType);
		moveset = restoredMoveset;
		temporarilySuspendedData = null;
		temporarilySuspendedTypeId = null;
		leapCooldown = Math.max(deathTimeLeapCooldown, 0);
		try {
			restoredData.onTemporaryPowerRestored(
					this, temporaryType);
		}
		catch (RuntimeException | Error error) {
			JojoMod.getLogger().error(
					"Failed to refresh retained PlayerPower {} for {}",
					restoredType.getId(),
					user.getScoreboardName(),
					error);
		}
		syncTemporaryTransition(restoredData, false);
		syncLeapCooldown();
		return true;
	}

	@ApiStatus.Internal
	public Optional<CompoundTag> serializeRetainedTemporaryData(
			PlayerPowerType<?> retainedType) {
		if (retainedType == null
				|| !retainedType.getId().equals(
						temporarilySuspendedTypeId)) {
			return Optional.empty();
		}
		PlayerPowerData retainedData =
				resolveTemporarilySuspendedData();
		return retainedData != null
				? Optional.of(retainedData.serializeNBT(
						user.registryAccess()))
				: Optional.empty();
	}

	@Nullable
	private PlayerPowerData resolveTemporarilySuspendedData() {
		if (temporarilySuspendedData == null
				&& temporarilySuspendedTypeId != null) {
			PlayerPowerType<?> suspendedType =
					JojoRegistries.PLAYER_POWER_TYPES_REG.get(
							temporarilySuspendedTypeId);
			if (suspendedType != null
					&& suspendedType != getPowerType()) {
				PowerData data =
						getPowerTypeData(suspendedType);
				if (data instanceof PlayerPowerData playerData) {
					temporarilySuspendedData = playerData;
				}
			}
		}
		return temporarilySuspendedData;
	}

	@Nullable
	public PlayerPowerType<?> getRetainedTemporaryType() {
		return temporarilySuspendedTypeId != null
				? JojoRegistries.PLAYER_POWER_TYPES_REG.get(
						temporarilySuspendedTypeId)
				: null;
	}

	public boolean isDelegating(
			@Nullable PlayerPowerType<?> retainedType) {
		return retainedType != null
				&& PlayerPowerDelegations.delegates(
						this, getPowerType(), retainedType);
	}

	@Override
	@Nullable
	protected PowerType getMovesetPowerType(
			@Nullable PowerType currentPowerType) {
		if (currentPowerType instanceof PlayerPowerType<?>) {
			PlayerPowerType<?> delegated =
					PlayerPowerDelegations.delegatedType(this)
							.orElse(null);
			if (delegated != null) {
				return delegated;
			}
		}
		return currentPowerType;
	}

	@Override
	@Nullable
	public PowerData getDataForAbility(
			@Nullable Ability ability) {
		return super.getDataForAbility(ability);
	}

	@Override
	@Nullable
	public PowerData getDataForPowerType(
			@Nullable ResourceLocation powerTypeId) {
		PlayerPowerType<?> retainedType =
				getRetainedTemporaryType();
		if (retainedType != null
				&& retainedType.getId().equals(powerTypeId)
				&& isDelegating(retainedType)) {
			return resolveTemporarilySuspendedData();
		}
		return super.getDataForPowerType(powerTypeId);
	}

	private void syncTemporaryTransition(
			PlayerPowerData currentData,
			boolean triggerAcquisitionCriterion) {
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(
				user,
				new TrPowerTypePacket(
						user.getId(),
						getPowerType(),
						getRetainedTemporaryType()));
		if (triggerAcquisitionCriterion
				&& user instanceof ServerPlayer player) {
			ModCriteriaTriggers.triggerGetPower(player, this);
		}
		currentData.syncToAllTracking(user);
		if (user instanceof ServerPlayer player) {
			currentData.syncToPlayer(player);
		}
		PlayerPowerData retainedData =
				resolveTemporarilySuspendedData();
		if (retainedData != null && retainedData != currentData) {
			retainedData.syncToAllTracking(user);
			if (user instanceof ServerPlayer player) {
				retainedData.syncToPlayer(player);
			}
		}
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
		return powerType.hasLeapEnergy(
						this, powerType.getLeapEnergyCost(this))
				&& LeapAccessPolicies.allowsExecution(
						user, LeapSource.PLAYER_POWER);
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
		if (matchCurrentType == null) {
			return Optional.empty();
		}
		T requestedType = matchCurrentType.get();
		if (requestedType == null) {
			return Optional.empty();
		}
		PlayerPowerType<?> currentType = getPowerType();
		if (requestedType == currentType) {
			return Optional.of((D) getPowerTypeData(
					requestedType));
		}
		PlayerPowerType<?> retainedType =
				getRetainedTemporaryType();
		if (requestedType == retainedType
				&& isDelegating(retainedType)) {
			return Optional.ofNullable(
					(D) resolveTemporarilySuspendedData());
		}
		return Optional.empty();
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
		PacketDistributor.sendToPlayer(
				user,
				new TrPowerTypePacket(
						user.getId(),
						getPowerType(),
						getRetainedTemporaryType()));
		super.syncToPlayer(user);
		PlayerPowerData retainedData =
				resolveTemporarilySuspendedData();
		if (retainedData != null
				&& retainedData != getCurTypeData()) {
			retainedData.syncToPlayer(user);
		}
		PacketDistributor.sendToPlayer(user,
				new TrPlayerPowerLeapCooldownPacket(this.user.getId(), leapCooldown));
	}

	@Override
	public void syncToTracking(ServerPlayer player) {
		PacketDistributor.sendToPlayer(
				player,
				new TrPowerTypePacket(
						user.getId(),
						getPowerType(),
						getRetainedTemporaryType()));
		super.syncToTracking(player);
		PlayerPowerData retainedData =
				resolveTemporarilySuspendedData();
		if (retainedData != null
				&& retainedData != getCurTypeData()) {
			retainedData.syncToTracking(user, player);
		}
	}
	
	@Override
	public void onPlayerCloneData(PlayerPower newEntityData, boolean wasDeath) {
		super.onPlayerCloneData(newEntityData, wasDeath);
		PlayerPowerType<?> powerType = getPowerType();
		if (powerType != null && (!wasDeath || powerType.keepOnDeath(this))) {
			newEntityData.curPowerType = this.curPowerType;
			newEntityData.leapCooldown = this.leapCooldown;
			newEntityData.temporarilySuspendedData = null;
			newEntityData.temporarilySuspendedTypeId =
					this.temporarilySuspendedTypeId;
			newEntityData.moveset =
					newEntityData.initMoveset(powerType);
		}
		else if (wasDeath) {
			newEntityData.curPowerType = Optional.empty();
			newEntityData.moveset = null;
			newEntityData.powerData = new HashMap<>();
			newEntityData.leapCooldown = 0;
			newEntityData.temporarilySuspendedData = null;
			newEntityData.temporarilySuspendedTypeId = null;
		}
	}

	@Override
	protected Map<ResourceLocation, Either<PowerData, CompoundTag>>
	copyPowerDataForClone(
			PlayerPower newEntityData, boolean wasDeath) {
		return detachedPowerDataCopy(
				this.powerData,
				newEntityData.user.registryAccess());
	}

	static Map<ResourceLocation, Either<PowerData, CompoundTag>>
	detachedPowerDataCopy(
			Map<ResourceLocation, Either<PowerData, CompoundTag>> source,
			HolderLookup.Provider provider) {
		Map<ResourceLocation, Either<PowerData, CompoundTag>> copy =
				new HashMap<>();
		source.forEach((id, entry) -> {
			CompoundTag dataNbt = entry.map(
					data -> data.serializeNBT(provider),
					CompoundTag::copy);
			copy.put(id, Either.right(dataNbt.copy()));
		});
		return copy;
	}

	static void commitTemporaryRestore(
			Map<ResourceLocation, Either<PowerData, CompoundTag>> data,
			@Nullable ResourceLocation temporaryTypeId,
			ResourceLocation restoredTypeId,
			PlayerPowerData restoredData) {
		if (temporaryTypeId != null
				&& !temporaryTypeId.equals(restoredTypeId)) {
			data.remove(temporaryTypeId);
		}
		data.put(restoredTypeId, Either.left(restoredData));
	}
	
	
	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag nbt = super.serializeNBT(provider);
		curPowerType.ifPresent(curType -> {
			nbt.putString("PowerType", curType.getId().toString());
		});
		nbt.putInt("LeapCd", leapCooldown);
		if (temporarilySuspendedTypeId != null) {
			nbt.putString(
					"TemporarilySuspendedPowerType",
					temporarilySuspendedTypeId.toString());
		}
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
		temporarilySuspendedData = null;
		temporarilySuspendedTypeId = null;
		if (nbt.contains(
				"TemporarilySuspendedPowerType",
				net.minecraft.nbt.Tag.TAG_STRING)) {
			try {
				temporarilySuspendedTypeId =
						ResourceLocation.parse(nbt.getString(
								"TemporarilySuspendedPowerType"));
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().warn(
						"Ignoring invalid suspended PlayerPower ID for {}",
						user.getScoreboardName());
			}
		}
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
