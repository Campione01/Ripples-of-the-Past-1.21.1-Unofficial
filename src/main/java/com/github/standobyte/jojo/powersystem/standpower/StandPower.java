package com.github.standobyte.jojo.powersystem.standpower;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.JojoModLivingVariables;
import com.github.standobyte.jojo.api.leap.LeapAccessPolicies;
import com.github.standobyte.jojo.api.leap.LeapSource;
import com.github.standobyte.jojo.api.stand.StandLeapUnlockProviders;
import com.github.standobyte.jojo.config.client.PlayerClientBroadcastedSettings;
import com.github.standobyte.jojo.entityattachment.PostNbtReadEntityData;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModEntityAttributes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.mechanics.JojoDefinitions;
import com.github.standobyte.jojo.mechanics.resolve.ResolveCounter;
import com.github.standobyte.jojo.mechanics.resolve.ResolveModeEffect;
import com.github.standobyte.jojo.mechanics.standarrow.StandArrowItem;
import com.github.standobyte.jojo.network.s2c.SoulSpawnPacket;
import com.github.standobyte.jojo.network.s2c.StandFullClearPacket;
import com.github.standobyte.jojo.network.s2c.StandEntitySoundPacket;
import com.github.standobyte.jojo.network.s2c.TrPowerStandInstancePacket;
import com.github.standobyte.jojo.network.s2c.TrStandSkinPacket;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.condition.AvailableAbilities;
import com.github.standobyte.jojo.powersystem.ability.ProgressionSkipHandler;
import com.github.standobyte.jojo.powersystem.ability.TrainableAbility;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.StandAwakening.AwakeningStage;
import com.github.standobyte.jojo.powersystem.standpower.effect.UserStandEffects;
import com.github.standobyte.jojo.powersystem.standpower.entity.EntityStandType;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandStatFormulas;
import com.github.standobyte.jojo.powersystem.standpower.packet.TrStandAbilityCooldownPacket;
import com.github.standobyte.jojo.powersystem.standpower.packet.TrStandLeapCooldownPacket;
import com.github.standobyte.jojo.powersystem.standpower.packet.TrStaminaPacket;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojo.powersystem.standpower.type.StandTypePersistentData;
import com.github.standobyte.jojo.powersystem.standpower.type.SummonedStand;
import com.github.standobyte.jojo.subsystems.soul.SoulEntity;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojo.util.functions.NBTUtil;
import com.github.standobyte.jojo.util.objects_java.Lerp;
import com.github.standobyte.jojo.util.sound.MultiSoundEventResolver;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.network.PacketDistributor;

public class StandPower extends Power<StandPower> implements PostNbtReadEntityData {
	private static final double SOUL_DUPLICATE_CHECK_RADIUS = 128.0D;

	protected Optional<StandInstance> standInstance = Optional.empty();
	protected SummonedStand summonedStand;
	
	protected Lerp.FloatValue staminaLerp = new Lerp.FloatValue();
	protected float staminaAddNextTick = 0;
	private int leapCooldown;
	private final Map<String, Integer> abilityCooldowns = new HashMap<>();
	private final Map<String, Integer> abilityCooldownTotals = new HashMap<>();
	private boolean willSoulSpawn;
	
	public ResolveCounter resolveCounter = new ResolveCounter();
	public UserStandEffects userStandEffects = new UserStandEffects(this);
	public StandAwakening userStandAwakeningState = new StandAwakening();
	public boolean healingDamageFromArrow = false;
	
	public StandPower(LivingEntity user) {
		super(user);
		addPostNbtReadCallback(user); // to update the user's base attribute values after the attributes are read
	}
	
	
	@Override
	public void tick() {
		super.tick();
		tickStamina();
		tickLeapCooldown();
		tickAbilityCooldowns();
		tickResolve();
		tickSoulCheck();
		if (hasPower()) {
			userStandEffects.tick();
			standInstance.ifPresent(stand -> stand.syncIfDirty(user));
		}
		if (!user.level().isClientSide()) {
			tickWrongLevelStandEntity();
			tickMissingStandPartEffects();
			if (healingDamageFromArrow && !StandArrowItem.healArrowDamage(user)) {
				healingDamageFromArrow = false;
			}
			if (!canUsePower()) {
				StandType type = getPowerType();
				if (type != null) {
					type.forceUnsummon(user, this);
				}
			}
		}
		if (summonedStand != null) {
			summonedStand.tickStand(getUser(), this);
		}
	}

	private void tickWrongLevelStandEntity() {
		if (getPowerType() instanceof EntityStandType standType) {
			StandEntity standEntity = getSummonedStandEntity();
			if (standEntity != null && standEntity.level() != user.level()) {
				standType.forceUnsummon(user, this);
			}
		}
	}

	private void tickMissingStandPartEffects() {
		if (getPowerType() instanceof EntityStandType) {
			standInstance.ifPresent(standInstance -> {
				if (!standInstance.hasPart(StandPart.ARMS)) {
					user.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 319, 1));
					user.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 319, 1));
				}
				if (!standInstance.hasPart(StandPart.LEGS)) {
					user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 319, 1));
				}
			});
		}
	}
	
	
	public void setStand(@Nullable StandType stand) {
		setStandInstance(stand != null ? Optional.of(new StandInstance(stand)) : Optional.empty());
	}
	
	@ApiStatus.Internal
	public void setStandInstance(Optional<StandInstance> standInstance) {
		Optional<StandInstance> newStandInstance = copyStandInstance(standInstance);
		if (!standInstanceChanged(this.standInstance, newStandInstance)) {
			return;
		}

		StandType oldStand = getPowerType();
		boolean standChanged = newStandInstance.map(newStand -> oldStand != newStand.getStandType())
				.orElseGet(() -> oldStand != null);
		if (oldStand != null) {
			oldStand.forceUnsummon(user, this);
		}
		
		this.standInstance = newStandInstance;
		StandType newStand = getPowerType();
		
		LivingEntity user = getUser();
		if (user != null) {
			StandStats.updateStandStatAttributes(this, user);
		}
		
		if (user != null && !user.level().isClientSide()) {
			userStandEffects.onStandChanged(user);
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrPowerStandInstancePacket(user.getId(), this.standInstance));
			if (newStand != null && user instanceof ServerPlayer player) {
				ModCriteriaTriggers.triggerGetPower(player, this);
			}
		}
		
		if (newStand == null) {
			setStamina(0);
		}
		onSetPowerType(oldStand, newStand);
		if (standChanged && newStand != null && user != null && !user.level().isClientSide()
				&& JojoModConfig.getCommonConfigInstance(false).skipStandProgression.get()) {
			skipProgression();
		}
	}

	static boolean standInstanceChanged(Optional<StandInstance> current, Optional<StandInstance> replacement) {
		Objects.requireNonNull(current, "current");
		Objects.requireNonNull(replacement, "replacement");
		if (!current.equals(replacement)) {
			return true;
		}
		if (current.isEmpty()) {
			return false;
		}

		StandInstance currentStand = current.orElseThrow();
		StandInstance replacementStand = replacement.orElseThrow();
		return currentStand.standExists() != replacementStand.standExists()
				|| currentStand.getStandType() != replacementStand.getStandType();
	}

	static Optional<StandInstance> copyStandInstance(Optional<StandInstance> standInstance) {
		return Objects.requireNonNull(standInstance, "standInstance").map(StandInstance::copy);
	}

	@Override
	public StandType getPowerType() {
		return standInstance.map(StandInstance::getStandType).orElse(null);
	}
	
	@Override
	public boolean hasPower() {
		return standInstance.filter(StandInstance::standExists).isPresent();
	}
	
	public Optional<StandInstance> getStandInstance() {
		return standInstance;
	}

	/**
	 * Commits a preflighted destructive Stand transition.
	 */
	@ApiStatus.Internal
	public void applyDestructiveTransition(boolean fullReset) {
		setStandInstance(Optional.empty());
		staminaAddNextTick = 0;
		resetAbilityCooldowns();
		setLeapCooldown(0);
		resolveCounter.resetResolveValue(this);
		willSoulSpawn = false;
		healingDamageFromArrow = false;

		if (user instanceof ServerPlayer player) {
			PacketDistributor.sendToPlayer(
					player, SoulSpawnPacket.spawnFlag(false));
		}
		if (fullReset) {
			clearFullStandProgressionState();
			if (user instanceof ServerPlayer player) {
				PacketDistributor.sendToPlayer(
						player, new StandFullClearPacket());
			}
		}
	}

	@ApiStatus.Internal
	public void clientApplyFullStandClear() {
		if (user.level().isClientSide()) {
			clearFullStandProgressionState();
		}
	}

	private void clearFullStandProgressionState() {
		powerData.clear();
		moveset = initMoveset(getPowerType());
		_curAvailableMoves = new AvailableAbilities();
		cachedMovesThisTick = false;
		userStandAwakeningState = new StandAwakening();
	}

	@Override
	public PowerClass<StandPower> getPowerClass() {
		return PowerClass.STAND;
	}
	
	
	public SummonedStand getSummonedStand() {
		return summonedStand;
	}
	
	@Nullable
	public StandEntity getSummonedStandEntity() {
		return summonedStand != null ? summonedStand.getStandEntity() : null;
	}
	
	public boolean isSummoned() {
		return summonedStand != null;
	}
	
	public void setSummonedStand(@Nullable SummonedStand summonedStand) {
		this.summonedStand = summonedStand;
		if (summonedStand != null) {
			summonedStand.setUserAndPower(getUser(), this);
			summonedStand.setSelectedSkin(standInstance.flatMap(StandInstance::getSelectedSkin));
		}
	}
	
	
	@Nullable
	@Override
	public StandTypePersistentData getCurTypeData() {
		// who needs generics, amirite
		return (StandTypePersistentData) super.getCurTypeData();
	}
	
	public static int addExp(LivingEntity user, float exp) {
		if (user != null && !user.level().isClientSide()) {
			StandPower stand = StandPower.get(user);
			return stand != null ? stand.addExp(exp) : null;
		}
		return 0;
	}
	
	public int addExp(float exp) {
		StandTypePersistentData data = getCurTypeData();
		return data != null ? data.addExp(exp, user) : 0;
	}
	
	public void skipProgression() {
		StandTypePersistentData data = getCurTypeData();
		if (data != null) {
			setResolveLevel(getMaxResolveLevel());
			if (!user.level().isClientSide()) {
				for (String skillName : data.getAllSkills().keySet()) {
					data._setSkillUnlocked(skillName, true, false);
				}
				Set<String> trainedAbilities = new HashSet<>();
				for (Ability ability : getMoveset().abilities.values()) {
					if (ability instanceof TrainableAbility trainable) {
						String learningAbilityName = trainable.getLearningAbilityName();
						if (learningAbilityName != null && trainedAbilities.add(learningAbilityName)
								&& data.getAbilityLearningProgressPoints(learningAbilityName) >= 0.0F) {
							float maxTraining = trainable.getMaxTrainingPoints(this);
							data.setAbilityLearningProgressPoints(learningAbilityName, maxTraining, maxTraining, this);
						}
					}
					if (ability instanceof ProgressionSkipHandler progressionSkipHandler) {
						progressionSkipHandler.onProgressionSkipped(this);
					}
				}
				data.syncOnUpdate(user);
			}
		}
	}
	
	
	public boolean usesStamina() {
		return hasPower() ? getPowerType().usesStamina(this) : false;
	}
	
	public float getStamina() {
		if (isStaminaInfinite()) {
			return getMaxStamina();
		}
		return staminaLerp.get();
	}
	
	public float getMaxStamina() {
		return hasPower() ? getPowerType().getMaxStamina(this) * getPlayerPowerStandMaxStaminaFactor() : 0;
	}
	
	public float getStaminaRatio() {
		float maxStamina = getMaxStamina();
		float stamina = getStamina();
		return MathUtil.ratioSafe(stamina, maxStamina);
	}
	
	public float getStaminaRatio(float partialTick) {
		if (isStaminaInfinite()) {
			return 1;
		}
		float maxStamina = getMaxStamina();
		float stamina = staminaLerp.lerp(partialTick);
		return stamina == maxStamina ? 1 : maxStamina > 0 ? stamina / maxStamina : 0;
	}
	
	public void setStamina(float stamina) {
		boolean clientSide = user.level().isClientSide();
		if (!clientSide) {
			stamina = Mth.clamp(stamina, 0, getMaxStamina());
		}
		if (this.staminaLerp.set(stamina, true)) {
			if (!clientSide) {
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrStaminaPacket(user.getId(), stamina));
			}
		}
	}
	
	public boolean consumeStamina(float amount) {
		return consumeStamina(amount, false);
	}
	
	public boolean consumeStamina(float amount, boolean ticking) {
		if (isStaminaInfinite()) {
			return true;
		}
		float curAmount = getStamina();
		if (curAmount >= amount) {
			if (ticking) {
				staminaAddNextTick -= amount;
			}
			else {
				setStamina(curAmount - amount);
			}
			return true;
		}
		else {
			setStamina(0);
			return ResolveModeEffect.getResolveEffectLvl(getUser()) >= 0;
		}
	}

	public boolean isStaminaInfinite() {
		return user == null || isUserCreative()
				|| !JojoModConfig.getCommonConfigInstance(user.level().isClientSide()).standStamina.get();
	}
	
	protected void tickStamina() {
		if (this.usesStamina()) {
			float staminaRegen = getStaminaTickGain() + staminaAddNextTick;
			staminaAddNextTick = 0;
			staminaLerp.set(Mth.clamp(staminaLerp.get() + staminaRegen, 0, getMaxStamina()), true);
		}
	}

	public float getStaminaTickGain() {
		if (!hasPower()) {
			return 0.0F;
		}
		float staminaRegen = getPowerType().getStaminaRegenBeforeExternalModifiers(this);
		LivingEntity user = getUser();
		if (user != null) {
			JojoModLivingVariables vars = JojoModLivingVariables.get(user);
			if (vars.isDyingBody() && vars.getDyingBodyTicksLeft() == 0) {
				staminaRegen -= 3.5F;
			}
		}
		if (staminaRegen > 0.0F) {
			staminaRegen *= getPlayerPowerStandStaminaRegenFactor();
			if (user instanceof Player player && player.getFoodData().getFoodLevel() > 17) {
				staminaRegen *= 1.25F;
			}
			if (isUserInStoppedTime()) {
				staminaRegen *= PlayerClientBroadcastedSettings.getTimeStopStaminaRegenMultiplier(this);
			}
		}
		return staminaRegen * getPowerType().getStaminaDurabilityMultiplier(this);
	}

	private boolean isUserInStoppedTime() {
		LivingEntity user = getUser();
		if (user != null && user.level() instanceof ServerLevel serverLevel
				&& serverLevel.hasData(ModDataAttachmentTypes.TIME_STOP.get())) {
			return serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get()).isTimeStopped(user);
		}
		return false;
	}

	private float getPlayerPowerStandMaxStaminaFactor() {
		PlayerPower playerPower = PlayerPower.get(getUser());
		return playerPower != null && playerPower.hasPower()
				? playerPower.getPowerType().getStandMaxStaminaFactor(playerPower, this)
				: 1.0F;
	}

	private float getPlayerPowerStandStaminaRegenFactor() {
		PlayerPower playerPower = PlayerPower.get(getUser());
		return playerPower != null && playerPower.hasPower()
				? playerPower.getPowerType().getStandStaminaRegenFactor(playerPower, this)
				: 1.0F;
	}

	private void tickLeapCooldown() {
		if (leapCooldown > 0) {
			leapCooldown--;
		}
	}

	private void tickAbilityCooldowns() {
		for (Iterator<Map.Entry<String, Integer>> iterator = abilityCooldowns.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<String, Integer> entry = iterator.next();
			int cooldown = entry.getValue() - 1;
			if (cooldown > 0) {
				entry.setValue(cooldown);
			}
			else {
				abilityCooldownTotals.remove(entry.getKey());
				iterator.remove();
			}
		}
	}

	public int getAbilityCooldown(String abilityName) {
		return abilityCooldowns.getOrDefault(abilityName, 0);
	}

	@ApiStatus.Internal
	public int getAbilityCooldownTotal(String abilityName) {
		int cooldown = getAbilityCooldown(abilityName);
		return abilityCooldownTotals.getOrDefault(abilityName, cooldown);
	}

	public boolean isAbilityOnCooldown(String abilityName) {
		return getAbilityCooldown(abilityName) > 0;
	}

	public float getAbilityCooldownRatio(String abilityName, float partialTick) {
		int cooldown = getAbilityCooldown(abilityName);
		if (cooldown <= 0) {
			return 0.0F;
		}
		int totalCooldown = abilityCooldownTotals.getOrDefault(abilityName, cooldown);
		if (totalCooldown <= 0) {
			return 0.0F;
		}
		return Mth.clamp(((float) cooldown - partialTick) / (float) totalCooldown, 0.0F, 1.0F);
	}

	public void setAbilityCooldown(String abilityName, int cooldown) {
		setAbilityCooldown(abilityName, cooldown, cooldown);
	}

	public void setAbilityCooldown(String abilityName, int cooldown, int totalCooldown) {
		if (abilityName == null || abilityName.isEmpty()) {
			return;
		}
		int newCooldown = Math.max(cooldown, 0);
		int newTotalCooldown = Math.max(totalCooldown, newCooldown);
		if (newCooldown > 0) {
			abilityCooldowns.put(abilityName, newCooldown);
			abilityCooldownTotals.put(abilityName, newTotalCooldown);
		}
		else {
			abilityCooldowns.remove(abilityName);
			abilityCooldownTotals.remove(abilityName);
		}
		syncAbilityCooldown(abilityName, newCooldown, newTotalCooldown);
	}

	public void resetAbilityCooldowns() {
		abilityCooldowns.clear();
		abilityCooldownTotals.clear();
		if (!user.level().isClientSide()) {
			serverPlayerUser.ifPresent(player -> PacketDistributor.sendToPlayer(player, TrStandAbilityCooldownPacket.resetAll(user.getId())));
		}
	}

	private void syncAbilityCooldown(String abilityName, int cooldown, int totalCooldown) {
		if (!user.level().isClientSide()) {
			serverPlayerUser.ifPresent(player -> PacketDistributor.sendToPlayer(player,
					new TrStandAbilityCooldownPacket(user.getId(), abilityName, cooldown, totalCooldown)));
		}
	}

	private void syncAbilityCooldownsTo(ServerPlayer player) {
		for (Map.Entry<String, Integer> entry : abilityCooldowns.entrySet()) {
			String abilityName = entry.getKey();
			int cooldown = entry.getValue();
			if (cooldown > 0) {
				PacketDistributor.sendToPlayer(player, new TrStandAbilityCooldownPacket(user.getId(), abilityName, cooldown,
						abilityCooldownTotals.getOrDefault(abilityName, cooldown)));
			}
		}
	}

	public boolean isLeapUnlocked() {
		return getStandInstance().map(stand -> stand.hasPart(StandPart.LEGS)).orElse(false) && leapStrength() >= 1.5F;
	}

	public float leapStrength() {
		StandEntity standEntity = getSummonedStandEntity();
		if (standEntity != null && !standEntity.isArmsOnlyMode() && standEntity.isFollowingUser()) {
			return standEntity.getLeapStrength();
		}
		return 0;
	}

	public boolean canLeap() {
		if (!hasPower() || leapCooldown != 0 || !isLeapUnlocked() || leapStrength() <= 0) {
			return false;
		}
		if (!(getPowerType() instanceof EntityStandType standType)
				|| !(standType.canLeap()
						|| StandLeapUnlockProviders.unlocks(
								user, this, standType))) {
			return false;
		}
		StandEntity standEntity = getSummonedStandEntity();
		return standEntity != null
				&& standEntity.getCurStandAction() == null
				&& LeapAccessPolicies.allowsExecution(
						user, LeapSource.STAND);
	}

	public int getLeapCooldownPeriod() {
		StandEntity standEntity = getSummonedStandEntity();
		if (standEntity != null && !standEntity.isArmsOnlyMode() && standEntity.isFollowingUser()) {
			double speed = standEntity.getAttributeValue(Attributes.MOVEMENT_SPEED);
			return StandStatFormulas.leapCooldown(speed);
		}
		return 0;
	}

	public void onLeap() {
		if (!isUserCreative()) {
			setLeapCooldown(getLeapCooldownPeriod());
		}
		consumeStamina(250);
		StandEntity standEntity = getSummonedStandEntity();
		if (standEntity != null && !user.level().isClientSide()) {
			float volume = standEntity.getLeapStrength() / 2.4F;
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(standEntity, new StandEntitySoundPacket(
					standEntity, MultiSoundEventResolver.resolve(ModSoundEvents.STAND_LEAP), volume, 1.0F));
		}
	}

	public int getLeapCooldown() {
		return leapCooldown;
	}

	public void setLeapCooldown(int cooldown) {
		int newCooldown = Math.max(cooldown, 0);
		boolean changed = this.leapCooldown != newCooldown;
		this.leapCooldown = newCooldown;
		if (changed && !user.level().isClientSide() && user instanceof ServerPlayer player) {
			PacketDistributor.sendToPlayer(player, new TrStandLeapCooldownPacket(user.getId(), leapCooldown));
		}
	}
	
	
	public boolean usesResolve() {
		return hasPower() && getPowerType().usesResolve(this) && userStandAwakeningState.stage == AwakeningStage.FULL_CONTROL;
	}
	
	public ResolveCounter getResolveCounter() {
		return resolveCounter;
	}

	public int getResolveLevel() {
		if (!usesResolve()) {
			return 0;
		}
		StandTypePersistentData data = getCurTypeData();
		return data != null ? data.getResolveLevel() : 0;
	}

	public int getMaxResolveLevel() {
		if (!usesResolve()) {
			return 0;
		}
		StandType type = getPowerType();
		return type != null ? type.getMaxResolveLevel() : StandTypePersistentData.MAX_RESOLVE_LEVEL;
	}

	public float getStatsDevelopment() {
		int maxResolveLevel = getMaxResolveLevel();
		return usesResolve() && maxResolveLevel > 0 ? (float) getResolveLevel() / (float) maxResolveLevel : 0;
	}

	public void setResolveLevel(int level) {
		if (!usesResolve()) {
			return;
		}
		StandTypePersistentData data = getCurTypeData();
		StandType type = getPowerType();
		if (data != null && type != null && data.setResolveLevel(this, level) && !user.level().isClientSide()) {
			StandStats.updateStandStatAttributes(this, user);
			type.onNewResolveLevel(this);
		}
	}
	
	protected void tickResolve() {
		resolveCounter.tick(this);
	}

	public boolean canSpawnSoulOnDeath() {
		if (user == null || user.level().isClientSide() || !(user.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!usesResolve()) {
			return false;
		}
		if (!JojoModConfig.getCommonConfigInstance(false).soulAscension.get()) {
			return false;
		}
		if (getResolveLevel() <= 0) {
			return false;
		}
		if (JojoDefinitions.isUndeadOrVampiric(user)) {
			return false;
		}
		return !(user instanceof Player) || !level.getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
	}

	private void tickSoulCheck() {
		if (user != null && user.isAlive() && user instanceof ServerPlayer player) {
			boolean soulCanSpawn = canSpawnSoulOnDeath();
			if (this.willSoulSpawn != soulCanSpawn) {
				this.willSoulSpawn = soulCanSpawn;
				PacketDistributor.sendToPlayer(player, SoulSpawnPacket.spawnFlag(soulCanSpawn));
			}
		}
	}

	public boolean willSoulSpawn() {
		return willSoulSpawn;
	}

	public void clSetSoulSpawnFlag(boolean flag) {
		this.willSoulSpawn = flag;
	}

	public boolean spawnSoulOnDeath() {
		if (!canSpawnSoulOnDeath() || !(user.level() instanceof ServerLevel level)) {
			serverPlayerUser.ifPresent(player -> PacketDistributor.sendToPlayer(player, SoulSpawnPacket.noSoulSpawned()));
			return false;
		}
		if (hasActiveSoulEntity(level)) {
			return true;
		}
		int ticks = getSoulLifespanTicks(level);
		boolean resolveCanLvlUp = level.getLevelData().isHardcore()
				|| !JojoModConfig.getCommonConfigInstance(false).keepStandOnDeath.get();
		Runnable spawnSoul = () -> {
			if (hasActiveSoulEntity(level)) {
				return;
			}
			serverPlayerUser.ifPresent(ModCriteriaTriggers::triggerSoulAscension);
			SoulEntity soulEntity = new SoulEntity(level, user, ticks, resolveCanLvlUp);
			LivingEntity killer = user.getKillCredit();
			if (killer != null) {
				soulEntity.setNoResolveToEntity(StandUtil.getStandUser(killer));
			}
			level.addFreshEntity(soulEntity);
			if (user instanceof ServerPlayer serverPlayer) {
				serverPlayer.connection.send(new ClientboundSetCameraPacket(soulEntity));
			}
		};
		if (level.hasData(ModDataAttachmentTypes.TIME_STOP.get())) {
			TimeStopState timeStopState = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
			timeStopState.queueOnTimeResume(user, spawnSoul);
		}
		else {
			spawnSoul.run();
		}
		return true;
	}

	private int getSoulLifespanTicks(ServerLevel level) {
		float resolveRatio = ResolveModeEffect.getResolveEffectLvl(user) >= 0 ? 1.0F : resolveCounter.getResolveRatio(this);
		int ticks = (int) (60.0F * (getResolveLevel() + resolveRatio));
		if (level.getLevelData().isHardcore()) {
			ticks += ticks / 2;
		}
		return Math.max(ticks, 1);
	}

	private boolean hasActiveSoulEntity(ServerLevel level) {
		return !level.getEntitiesOfClass(SoulEntity.class, user.getBoundingBox().inflate(SOUL_DUPLICATE_CHECK_RADIUS),
				soul -> soul.isAlive() && user.equals(soul.getOriginEntity())).isEmpty();
	}
	
	
	public void setSelectedSkin(Optional<ResourceLocation> skin) {
		if (standInstance.isPresent()) {
			standInstance.get().setCustomSkin(skin);
			if (!user.level().isClientSide()) {
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrStandSkinPacket(user.getId(), getSelectedSkin()));
			}
		}
		if (summonedStand != null) {
			summonedStand.setSelectedSkin(skin);
		}
	}
	
	public Optional<ResourceLocation> getSelectedSkin() {
		if (standInstance.isEmpty()) return Optional.empty();
		return standInstance.get().getSelectedSkin();
	}
	
	
	@Override
	public void afterConfigApply() {
		super.afterConfigApply();
		StandStats.updateStandStatAttributes(this, user);
	}

	@Override
	public void syncToPlayer(ServerPlayer user) {
		PacketDistributor.sendToPlayer(user, new TrPowerStandInstancePacket(user.getId(), standInstance));
		super.syncToPlayer(user);
		syncStaminaFixed(user, user);
		resolveCounter.syncToUser(user);
		PacketDistributor.sendToPlayer(user, new TrStandSkinPacket(user.getId(), getSelectedSkin()));
		userStandEffects.syncToPlayer(user);
		userStandAwakeningState.syncToUser(user);
		syncAbilityCooldownsTo(user);
		tickSoulCheck();
		PacketDistributor.sendToPlayer(user, SoulSpawnPacket.spawnFlag(willSoulSpawn));
	}

	@Override
	public void syncToTracking(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new TrPowerStandInstancePacket(user.getId(), standInstance));
		super.syncToTracking(player);
		syncStaminaFixed(player, user);
		resolveCounter.syncToTracking(user, player);
		PacketDistributor.sendToPlayer(player, new TrStandSkinPacket(user.getId(), getSelectedSkin()));
		userStandEffects.syncToTracking(player);
	}
	
	protected void syncStaminaFixed(ServerPlayer player, LivingEntity user) {
		var durabilityAttribute = user.getAttribute(ModEntityAttributes.STAND_DURABILITY);
		if (durabilityAttribute != null) {
			player.connection.send(new ClientboundUpdateAttributesPacket(user.getId(), Collections.singletonList(durabilityAttribute)));
		}
		PacketDistributor.sendToPlayer(player, new TrStaminaPacket(user.getId(), staminaLerp.get()));
		PacketDistributor.sendToPlayer(player, new TrStandLeapCooldownPacket(user.getId(), leapCooldown));
	}
	
	@Override
	public void onPlayerClone(Player newPlayer, boolean wasDeath) {
		super.onPlayerClone(newPlayer, wasDeath);
		this.userStandEffects.onPlayerClone(newPlayer, wasDeath);
	}
	
	@Override
	protected void onPlayerCloneData(StandPower newEntityData, boolean wasDeath) {
		super.onPlayerCloneData(newEntityData, wasDeath);
		StandType standType = getPowerType();
		if (standType == null || wasDeath && !standType.keepOnDeath(this)) {
			clearClonedStandData(newEntityData);
			return;
		}
		newEntityData.standInstance = copyStandInstance(this.standInstance);
		newEntityData.staminaLerp = this.staminaLerp;
		newEntityData.leapCooldown = this.leapCooldown;
		newEntityData.abilityCooldowns.clear();
		newEntityData.abilityCooldowns.putAll(this.abilityCooldowns);
		newEntityData.abilityCooldownTotals.clear();
		newEntityData.abilityCooldownTotals.putAll(this.abilityCooldownTotals);
		newEntityData.resolveCounter.copyValues(this.resolveCounter, wasDeath);
		newEntityData.userStandEffects = this.userStandEffects;
		newEntityData.userStandEffects.setPowerData(newEntityData);
		newEntityData.userStandAwakeningState = this.userStandAwakeningState;
	}

	private void clearClonedStandData(StandPower newEntityData) {
		newEntityData.standInstance = Optional.empty();
		newEntityData.summonedStand = null;
		newEntityData.moveset = null;
		newEntityData.powerData = new HashMap<>();
		newEntityData.staminaLerp = new Lerp.FloatValue();
		newEntityData.leapCooldown = 0;
		newEntityData.abilityCooldowns.clear();
		newEntityData.abilityCooldownTotals.clear();
		newEntityData.resolveCounter = new ResolveCounter();
		newEntityData.userStandEffects = new UserStandEffects(newEntityData);
		newEntityData.userStandAwakeningState = new StandAwakening();
		newEntityData.willSoulSpawn = false;
		newEntityData.healingDamageFromArrow = false;
	}
	
	
	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag nbt = super.serializeNBT(provider);
		standInstance.ifPresent(
				stand -> StandInstance.CODEC.encodeStart(NbtOps.INSTANCE, stand)
				.ifSuccess(standNbt -> nbt.put("StandInstance", standNbt)));
		nbt.putFloat("Stamina", staminaLerp.get());
		nbt.putInt("LeapCd", leapCooldown);
		if (!abilityCooldowns.isEmpty()) {
			CompoundTag cooldowns = new CompoundTag();
			abilityCooldowns.forEach((abilityName, cooldown) -> cooldowns.putIntArray(abilityName,
					new int[] { cooldown, abilityCooldownTotals.getOrDefault(abilityName, cooldown) }));
			nbt.put("AbilityCooldowns", cooldowns);
		}
		nbt.put("Resolve", resolveCounter.writeNBT());
		nbt.put("Effects", userStandEffects.serializeNBT(provider));
		nbt.put("Awakening", userStandAwakeningState.serializeNBT());
		nbt.putBoolean("HealFromArrow", healingDamageFromArrow);
		return nbt;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
		super.deserializeNBT(provider, nbt);
		standInstance = NBTUtil.getCompoundOptional(nbt, "StandInstance")
				.flatMap(standNbt -> StandInstance.CODEC.decode(NbtOps.INSTANCE, standNbt).result())
				.map(pair -> pair.getFirst());
		staminaLerp.set(nbt.getFloat("Stamina"), false);
		leapCooldown = nbt.getInt("LeapCd");
		abilityCooldowns.clear();
		abilityCooldownTotals.clear();
		NBTUtil.getCompoundOptional(nbt, "AbilityCooldowns").ifPresent(cooldowns -> {
			for (String abilityName : cooldowns.getAllKeys()) {
				int cooldown;
				int totalCooldown;
				if (cooldowns.contains(abilityName, Tag.TAG_INT_ARRAY)) {
					int[] values = cooldowns.getIntArray(abilityName);
					cooldown = values.length > 0 ? values[0] : 0;
					totalCooldown = values.length > 1 ? values[1] : cooldown;
				}
				else {
					cooldown = cooldowns.getInt(abilityName);
					totalCooldown = cooldown;
				}
				if (cooldown > 0) {
					abilityCooldowns.put(abilityName, cooldown);
					abilityCooldownTotals.put(abilityName, Math.max(totalCooldown, cooldown));
				}
			}
		});
		NBTUtil.getCompoundOptional(nbt, "Resolve").ifPresent(resolveCounter::readNBT);
		NBTUtil.getCompoundOptional(nbt, "Effects").ifPresent(effectsNbt -> userStandEffects.deserializeNBT(provider, effectsNbt));
		NBTUtil.getCompoundOptional(nbt, "Awakening").ifPresent(userStandAwakeningState::deserializeNBT);
		healingDamageFromArrow = nbt.getBoolean("HealFromArrow");
	}
	
	/* unlike deserializeNBT, this is called after the entity attributes are read, 
	 * allowing me to edit their base values from the Stand stats
	 */
	@Override
	public void afterNbtRead() {
		StandStats.updateStandStatAttributes(this, user);
	}
	
	
	@Nullable
	public static StandPower get(LivingEntity entity) {
		return PowerClass.STAND.get(entity);
	}
	
	public static Optional<StandPower> getOptional(LivingEntity entity) {
		return PowerClass.STAND.getOptional(entity);
	}
	
	
	public final boolean isUserCreative() {
		LivingEntity user = getUser();
		return user instanceof Player player && player.getAbilities().instabuild;
	}

}
