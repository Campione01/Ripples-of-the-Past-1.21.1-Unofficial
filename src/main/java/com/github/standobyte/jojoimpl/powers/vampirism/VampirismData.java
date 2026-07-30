package com.github.standobyte.jojoimpl.powers.vampirism;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;

import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.PowerType;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerData;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerType;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class VampirismData extends PlayerPowerData {
	public static final float BASE_MAX_BLOOD = BloodEconomy.DEFAULT_MAX_BLOOD;
	private static final int MAX_CHARACTER_NAME_LENGTH = 128;
	private static final int MAX_ABILITY_NAME_LENGTH = 256;
	private static final int MAX_ABILITY_COOLDOWNS = 1024;
	private static final double[] CURING_NAUSEA_CHANCE =
			new double[] { 0.0D, 0.0D, 1.0D / 2400.0D, 1.0D / 1200.0D, 1.0D / 600.0D };
	private static final int LAST_BLOOD_LEVEL_UNKNOWN = -999;

	private float bloodLevel;
	private int backboneTicks;
	private boolean vampireFullPower;
	private int lastBloodLevel = LAST_BLOOD_LEVEL_UNKNOWN;
	private int curingTicks;
	private boolean curingStageChanged;
	private boolean vampireHamonUser;
	private int hamonStrengthLevel;
	private String prevHamonCharacter = "";
	private final Map<String, Integer> abilityCooldowns = new HashMap<>();
	private final Map<String, Integer> abilityCooldownTotals = new HashMap<>();

	public VampirismData() {
		super(VampirismPowerType.VAMPIRISM.get());
	}

	@Override
	public void onPowerGiven(Power<?> userPower, @Nullable PowerType oldType, @Nullable PowerData oldData) {
		LivingEntity user = userPower.getUser();
		if (!user.level().isClientSide()) {
			if (oldType == ModPlayerPowers.HAMON.get() && oldData instanceof HamonData hamon) {
				setVampireHamonUser(true, hamon);
			}
			BloodEconomy blood = VampirismState.get(user).blood();
			blood.setMax(getMaxBlood(user), true);
			blood.replenish(300.0F);
			setBloodLevel(blood.current());
			updateVampirePassiveEffects(user);
			syncOnUpdate(user);
		}
		super.onPowerGiven(userPower, oldType, oldData);
	}

	@Override
	public void onPowerCleared(Power<?> userPower, @Nullable PowerType newType) {
		removeVampirePassiveEffects(userPower.getUser());
		super.onPowerCleared(userPower, newType);
	}

	@Override
	public void onTemporaryPowerSuspended(
			PlayerPower power,
			PlayerPowerType<?> temporaryType) {
		removeVampirePassiveEffects(power.getUser());
	}

	@Override
	public void onTemporaryPowerRestored(
			PlayerPower power,
			PlayerPowerType<?> temporaryType) {
		updateVampirePassiveEffects(power.getUser());
	}

	@Override
	public void onTemporaryPowerEnded(
			PlayerPower temporaryPower,
			PlayerPower restoredPower,
			PlayerPowerType<?> restoredType) {
		removeVampirePassiveEffects(temporaryPower.getUser());
		if (restoredPower.getUser() != temporaryPower.getUser()) {
			removeVampirePassiveEffects(restoredPower.getUser());
		}
	}

	@Override
	public void tick(Power<?> userPower) {
		super.tick(userPower);
		LivingEntity user = userPower.getUser();
		tickAbilityCooldowns();
		if (user.level().isClientSide()) {
			tickClientBlood(user);
			return;
		}

		backboneTicks++;
		tickBlood(user);
		if (user instanceof Player player) {
			player.getFoodData().setFoodLevel(17);
		}
		user.setAirSupply(user.getMaxAirSupply());
		tickCuring(user);
		int bloodLevel = bloodLevel(user);
		if (refreshBloodLevel(bloodLevel) || curingStageChanged) {
			updateVampirePassiveEffects(user);
			curingStageChanged = false;
		}
		setBloodLevel(VampirismState.get(user).blood().current());
	}

	public float getBloodLevel() {
		return bloodLevel;
	}

	public void setBloodLevel(float value) {
		bloodLevel = Math.max(0.0F, value);
	}

	public boolean hasBlood(LivingEntity user, float amount) {
		return amount <= 0.0F || user != null && currentBlood(user) >= amount;
	}

	public boolean consumeBlood(LivingEntity user, float amount) {
		if (amount <= 0.0F) {
			return true;
		}
		if (user == null || user.level().isClientSide()) {
			return false;
		}
		BloodEconomy blood = VampirismState.get(user).blood();
		if (blood.current() < amount) {
			return false;
		}
		blood.consume(amount);
		setBloodLevel(blood.current());
		syncOnUpdate(user);
		return true;
	}

	public void addBlood(LivingEntity user, float amount) {
		if (user == null || user.level().isClientSide() || amount <= 0.0F) {
			return;
		}
		BloodEconomy blood = VampirismState.get(user).blood();
		blood.replenish(amount);
		setBloodLevel(blood.current());
		syncOnUpdate(user);
	}

	public int getBackboneTicks() {
		return backboneTicks;
	}

	public void setBackboneTicks(int value) {
		backboneTicks = value;
	}

	public boolean isVampireAtFullPower() {
		return vampireFullPower;
	}

	public boolean shouldKeepOnDeath(boolean isClientSide) {
		JojoModConfig.Common config = JojoModConfig.getCommonConfigInstance(isClientSide);
		return config.keepVampirismOnDeath.get() && isVampireAtFullPower();
	}

	public void setVampireFullPower(boolean fullPower, LivingEntity user) {
		if (vampireFullPower != fullPower) {
			vampireFullPower = fullPower;
			lastBloodLevel = LAST_BLOOD_LEVEL_UNKNOWN;
			if (user != null && !user.level().isClientSide()) {
				updateVampirePassiveEffects(user);
				syncOnUpdate(user);
			}
		}
	}

	public boolean isVampireHamonUser() {
		return vampireHamonUser;
	}

	public void setVampireHamonUser(boolean vampireHamonUser, @Nullable HamonData oldHamonData) {
		this.vampireHamonUser = vampireHamonUser;
		if (oldHamonData != null) {
			this.hamonStrengthLevel = oldHamonData.getHamonStrengthLevel();
			this.prevHamonCharacter = oldHamonData.getCharacterTechniqueName();
		}
	}

	public int getHamonStrengthLevel() {
		return hamonStrengthLevel;
	}

	public String getPrevHamonCharacter() {
		return prevHamonCharacter;
	}

	public int getCuringTicks() {
		return curingTicks;
	}

	private boolean setCuringTicks(int curingTicks, int maxCuringTicks) {
		int oldStage = getCuringStage(maxCuringTicks);
		int oldTicks = this.curingTicks;
		this.curingTicks = Mth.clamp(curingTicks, 0, maxCuringTicks);
		curingStageChanged |= oldStage != getCuringStage(maxCuringTicks);
		return oldTicks != this.curingTicks;
	}

	public void setCuringTicks(LivingEntity user, int curingTicks) {
		if (setCuringTicks(curingTicks, getMaxCuringTicks(user)) && !user.level().isClientSide()) {
			if (curingStageChanged) {
				updateVampirePassiveEffects(user);
				curingStageChanged = false;
			}
			syncOnUpdate(user);
		}
	}

	public static boolean startCuringFromEnchantedGoldenApple(LivingEntity user) {
		if (user.level().isClientSide()) {
			return false;
		}
		MobEffectInstance weakness = user.getEffect(MobEffects.WEAKNESS);
		if (weakness == null || weakness.getAmplifier() < 4) {
			return false;
		}
		VampirismData data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElse(null);
		if (data == null) {
			return false;
		}
		if (!user.isSilent()) {
			user.level().playSound(null, user, ModSoundEvents.VAMPIRE_CURE_START.get(), user.getSoundSource(), 1.0F, 1.0F);
		}
		data.setCuringTicks(user, 1);
		return true;
	}

	public boolean isBeingCured() {
		return curingTicks > 0;
	}

	public int getCuringStage(LivingEntity user) {
		return getCuringStage(getMaxCuringTicks(user));
	}

	private int getCuringStage(int maxCuringTicks) {
		if (curingTicks <= 0) {
			return 0;
		}
		return Math.min((int) ((long) curingTicks * 4L / maxCuringTicks), 3) + 1;
	}

	public boolean isCuringComplete(LivingEntity user) {
		return curingTicks >= getMaxCuringTicks(user);
	}

	public boolean isHighOnBlood(LivingEntity user) {
		float maxBlood = getMaxBlood(user);
		return maxBlood > 0.0F && currentBlood(user) / maxBlood >= 0.8F;
	}

	public int getAbilityCooldown(String abilityName) {
		return abilityCooldowns.getOrDefault(abilityName, 0);
	}

	public boolean isAbilityOnCooldown(String abilityName) {
		return getAbilityCooldown(abilityName) > 0;
	}

	public float getAbilityCooldownRatio(String abilityName, float partialTick) {
		int cooldown = getAbilityCooldown(abilityName);
		int totalCooldown = abilityCooldownTotals.getOrDefault(abilityName, cooldown);
		if (cooldown <= 0 || totalCooldown <= 0) {
			return 0.0F;
		}
		return Mth.clamp((cooldown - partialTick) / totalCooldown, 0.0F, 1.0F);
	}

	public void setAbilityCooldown(String abilityName, int cooldown) {
		setAbilityCooldown(abilityName, cooldown, cooldown);
	}

	public void setAbilityCooldown(String abilityName, int cooldown, int totalCooldown) {
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
	}

	public int bloodLevel(LivingEntity user) {
		int difficultyId = getDifficultyId(user);
		if (difficultyId == 0) {
			return -1;
		}
		float maxBlood = getMaxBlood(user);
		float ratio = maxBlood > 0.0F ? currentBlood(user) / maxBlood : 0.0F;
		int level = Math.min((int) (ratio * 5.0F), 4) + difficultyId;
		if (!vampireFullPower) {
			level = Math.max(1, level - 2);
		}
		return level;
	}

	public boolean refreshBloodLevel(int bloodLevel) {
		boolean changed = this.lastBloodLevel != bloodLevel;
		this.lastBloodLevel = bloodLevel;
		return changed;
	}

	public float getMaxBlood(LivingEntity user) {
		return BASE_MAX_BLOOD * VampirismUtil.maxBloodMultiplier(user);
	}

	private float currentBlood(LivingEntity user) {
		return user.level().isClientSide() ? bloodLevel : VampirismState.get(user).blood().current();
	}

	private void tickClientBlood(LivingEntity user) {
		float drain = getBloodTickDown(user);
		if (drain > 0.0F) {
			setBloodLevel(bloodLevel - drain);
		}
	}

	private void tickBlood(LivingEntity user) {
		BloodEconomy blood = VampirismState.get(user).blood();
		if (blood.max() != getMaxBlood(user)) {
			blood.setMax(getMaxBlood(user), true);
		}
		float drain = getBloodTickDown(user);
		if (drain > 0.0F) {
			blood.consume(drain);
		}
	}

	private float getBloodTickDown(LivingEntity user) {
		float drain = VampirismUtil.bloodTickDown(user);
		if (isBeingCured()) {
			drain *= Math.max(1, getCuringStage(user)) * 4.0F;
		}
		if (user instanceof Player player && player.getAbilities().instabuild) {
			drain = Math.min(drain, 0.0F);
		}
		return drain;
	}

	private void tickCuring(LivingEntity user) {
		if (!isBeingCured()) {
			return;
		}
		user.setYRot(user.getYRot() + (float) (Math.cos((double) user.tickCount * 3.25D) * Math.PI * 0.4D));
		int stage = getCuringStage(user);
		int nauseaIndex = Math.min(stage, CURING_NAUSEA_CHANCE.length - 1);
		if (stage >= 2 && user.getRandom().nextDouble() <= CURING_NAUSEA_CHANCE[nauseaIndex]) {
			user.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200));
		}
		if (isCuringComplete(user) && user instanceof ServerPlayer player) {
			player.displayClientMessage(Component.translatable("jojo_ripples.vampire.ready_to_cure"), true);
		}
		BloodEconomy blood = VampirismState.get(user).blood();
		if (curingTicks < getMaxCuringTicks(user) && blood.isExhausted()) {
			setCuringTicks(user, curingTicks + getCuringTickProgress(user));
		}
	}

	private int getCuringTickProgress(LivingEntity user) {
		int progress = 1;
		if (user.getRandom().nextFloat() < 0.01F) {
			int accelBlocks = 0;
			BlockPos pos = user.blockPosition();
			BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
			for (int x = pos.getX() - 4; x < pos.getX() + 4; x++) {
				for (int y = pos.getY() - 4; y < pos.getY() + 4; y++) {
					for (int z = pos.getZ() - 4; z < pos.getZ() + 4; z++) {
						Block block = user.level().getBlockState(mutablePos.set(x, y, z)).getBlock();
						if (block == Blocks.IRON_BARS || block instanceof BedBlock) {
							if (user.getRandom().nextFloat() < 0.3F) {
								progress++;
							}
							if (++accelBlocks >= 14) {
								break;
							}
						}
					}
				}
			}
		}
		return progress;
	}

	public static boolean finishCuringOnWakingUp(LivingEntity user) {
		VampirismData data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElse(null);
		if (data == null || !data.isCuringComplete(user)) {
			return false;
		}
		PlayerPower power = PlayerPower.get(user);
		if (power != null) {
			power.setPowerType(null);
		}
		if (user instanceof Player player) {
			player.getFoodData().setFoodLevel(1);
		}
		user.level().playSound(null, user, ModSoundEvents.VAMPIRE_CURE_END.get(), user.getSoundSource(), 1.0F, 1.0F);
		user.removeEffect(ModStatusEffects.VAMPIRE_SUN_BURN);
		user.removeEffect(MobEffects.WEAKNESS);
		user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 1, false, true));
		user.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 1, false, true));
		user.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 1, false, true));
		return true;
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag nbt = super.serializeNBT(provider);
		nbt.putFloat("BloodLevel", bloodLevel);
		nbt.putInt("BackboneTicks", backboneTicks);
		nbt.putBoolean("VampireFullPower", vampireFullPower);
		nbt.putInt("CuringTicks", curingTicks);
		nbt.putBoolean("VampireHamonUser", vampireHamonUser);
		nbt.putInt("HamonStrengthLevel", hamonStrengthLevel);
		nbt.putString("PrevHamonCharacter", prevHamonCharacter);
		if (!abilityCooldowns.isEmpty()) {
			CompoundTag cooldowns = new CompoundTag();
			abilityCooldowns.forEach((abilityName, cooldown) -> cooldowns.putIntArray(abilityName,
					new int[] { cooldown, abilityCooldownTotals.getOrDefault(abilityName, cooldown) }));
			nbt.put("VampirismAbilityCooldowns", cooldowns);
		}
		return nbt;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
		super.deserializeNBT(provider, nbt);
		setBloodLevel(nbt.getFloat("BloodLevel"));
		setBackboneTicks(nbt.getInt("BackboneTicks"));
		vampireFullPower = nbt.getBoolean("VampireFullPower");
		setCuringTicksUnchecked(nbt.getInt("CuringTicks"));
		vampireHamonUser = nbt.getBoolean("VampireHamonUser");
		hamonStrengthLevel = nbt.getInt("HamonStrengthLevel");
		prevHamonCharacter = nbt.getString("PrevHamonCharacter");
		abilityCooldowns.clear();
		abilityCooldownTotals.clear();
		if (nbt.get("VampirismAbilityCooldowns") instanceof CompoundTag cooldowns) {
			for (String abilityName : cooldowns.getAllKeys()) {
				int[] values = cooldowns.getIntArray(abilityName);
				int cooldown = values.length > 0 ? values[0] : 0;
				int totalCooldown = values.length > 1 ? values[1] : cooldown;
				if (cooldown > 0) {
					abilityCooldowns.put(abilityName, cooldown);
					abilityCooldownTotals.put(abilityName, Math.max(totalCooldown, cooldown));
				}
			}
		}
		lastBloodLevel = LAST_BLOOD_LEVEL_UNKNOWN;
	}

	@Override
	public void toBuf(FriendlyByteBuf buf, boolean isSentToTracking) {
		super.toBuf(buf, isSentToTracking);
		buf.writeFloat(bloodLevel);
		buf.writeInt(backboneTicks);
		buf.writeBoolean(vampireFullPower);
		buf.writeInt(curingTicks);
		buf.writeBoolean(vampireHamonUser);
		buf.writeInt(hamonStrengthLevel);
		buf.writeUtf(prevHamonCharacter, MAX_CHARACTER_NAME_LENGTH);
		NetworkPayloadValidation.requireOutboundCollectionSize(
				abilityCooldowns.size(), MAX_ABILITY_COOLDOWNS,
				"Vampirism ability cooldown");
		buf.writeVarInt(abilityCooldowns.size());
		for (Map.Entry<String, Integer> cooldown : abilityCooldowns.entrySet()) {
			String abilityName = cooldown.getKey();
			buf.writeUtf(abilityName, MAX_ABILITY_NAME_LENGTH);
			buf.writeVarInt(cooldown.getValue());
			buf.writeVarInt(abilityCooldownTotals.getOrDefault(abilityName, cooldown.getValue()));
		}
	}

	@Override
	public void fromBuf(FriendlyByteBuf buf, boolean isSentToTracking) {
		super.fromBuf(buf, isSentToTracking);
		setBloodLevel(buf.readFloat());
		setBackboneTicks(buf.readInt());
		vampireFullPower = buf.readBoolean();
		setCuringTicksUnchecked(buf.readInt());
		vampireHamonUser = buf.readBoolean();
		hamonStrengthLevel = buf.readInt();
		prevHamonCharacter = buf.readUtf(MAX_CHARACTER_NAME_LENGTH);
		abilityCooldowns.clear();
		abilityCooldownTotals.clear();
		int cooldownCount = NetworkPayloadValidation.requireCollectionSize(
				buf.readVarInt(), MAX_ABILITY_COOLDOWNS,
				"Vampirism ability cooldown");
		for (int i = 0; i < cooldownCount; i++) {
			String abilityName = buf.readUtf(MAX_ABILITY_NAME_LENGTH);
			int cooldown = buf.readVarInt();
			int totalCooldown = buf.readVarInt();
			if (cooldown > 0) {
				abilityCooldowns.put(abilityName, cooldown);
				abilityCooldownTotals.put(abilityName, Math.max(totalCooldown, cooldown));
			}
		}
		lastBloodLevel = LAST_BLOOD_LEVEL_UNKNOWN;
	}

	private void tickAbilityCooldowns() {
		for (Iterator<Map.Entry<String, Integer>> iterator = abilityCooldowns.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<String, Integer> cooldown = iterator.next();
			int ticksLeft = cooldown.getValue() - 1;
			if (ticksLeft > 0) {
				cooldown.setValue(ticksLeft);
			}
			else {
				abilityCooldownTotals.remove(cooldown.getKey());
				iterator.remove();
			}
		}
	}

	@Override
	public void tickWhileTemporarilySuspended(
			PlayerPower power) {
		tickAbilityCooldowns();
	}

	private void setCuringTicksUnchecked(int curingTicks) {
		this.curingTicks = Math.max(curingTicks, 0);
		curingStageChanged = true;
	}

	private static int getDifficultyId(LivingEntity user) {
		Difficulty difficulty = user.level().getDifficulty();
		return Mth.clamp(difficulty.getId(), 0, 3);
	}

	private static int getMaxCuringTicks(LivingEntity user) {
		return Math.max(1, JojoModConfig.getCommonConfigInstance(user.level().isClientSide())
				.vampirismCuringDuration.get());
	}

	private void updateVampirePassiveEffects(LivingEntity user) {
		int difficultyId = getDifficultyId(user);
		int bloodLevel = bloodLevel(user);
		int curingStage = getCuringStage(user);
		if (curingStage > 0) {
			if (curingStage >= 3) {
				bloodLevel = -1;
			}
			else {
				bloodLevel -= curingStage;
			}
		}
		refreshHiddenEffect(user, MobEffects.HEALTH_BOOST, difficultyId * (curingStage > 0 ? 5 - curingStage * 2 : 5) - 1);
		refreshHiddenEffect(user, MobEffects.REGENERATION, Math.min(bloodLevel - 2, 4));
		refreshHiddenEffect(user, MobEffects.DAMAGE_BOOST, bloodLevel - 5);
		refreshHiddenEffect(user, MobEffects.MOVEMENT_SPEED, bloodLevel - 4);
		refreshHiddenEffect(user, MobEffects.DIG_SPEED, bloodLevel - 4);
		refreshHiddenEffect(user, MobEffects.JUMP, bloodLevel - 4);
		refreshHiddenEffect(user, MobEffects.NIGHT_VISION, 0);
		int harmfulAmp = curingStage >= 4 ? 3 - difficultyId : -1;
		refreshHiddenEffect(user, MobEffects.MOVEMENT_SLOWDOWN, harmfulAmp);
		refreshHiddenEffect(user, MobEffects.DIG_SLOWDOWN, harmfulAmp);
		refreshHiddenEffect(user, MobEffects.WEAKNESS, harmfulAmp);
		refreshHiddenEffect(user, MobEffects.BLINDNESS, curingStage >= 4 ? 0 : -1);
	}

	private void removeVampirePassiveEffects(LivingEntity user) {
		removeHiddenPassiveEffect(user, MobEffects.HEALTH_BOOST);
		removeHiddenPassiveEffect(user, MobEffects.REGENERATION);
		removeHiddenPassiveEffect(user, MobEffects.DAMAGE_BOOST);
		removeHiddenPassiveEffect(user, MobEffects.MOVEMENT_SPEED);
		removeHiddenPassiveEffect(user, MobEffects.DIG_SPEED);
		removeHiddenPassiveEffect(user, MobEffects.JUMP);
		removeHiddenPassiveEffect(user, MobEffects.NIGHT_VISION);
		removeHiddenPassiveEffect(user, MobEffects.MOVEMENT_SLOWDOWN);
		removeHiddenPassiveEffect(user, MobEffects.DIG_SLOWDOWN);
		removeHiddenPassiveEffect(user, MobEffects.WEAKNESS);
		removeHiddenPassiveEffect(user, MobEffects.BLINDNESS);
	}

	private static void refreshHiddenEffect(LivingEntity user, Holder<MobEffect> effect, int amplifier) {
		if (amplifier < 0) {
			removeHiddenPassiveEffect(user, effect);
			return;
		}
		MobEffectInstance current = user.getEffect(effect);
		boolean currentHidden = current != null && !current.isVisible() && !current.showIcon();
		if (current == null || currentHidden && current.getAmplifier() != amplifier || !currentHidden && current.getAmplifier() < amplifier) {
			user.removeEffectNoUpdate(effect);
			user.addEffect(new MobEffectInstance(effect, Integer.MAX_VALUE, amplifier, false, false, false));
		}
	}

	private static void removeHiddenPassiveEffect(LivingEntity user, Holder<MobEffect> effect) {
		MobEffectInstance current = user.getEffect(effect);
		if (current != null && !current.isVisible() && !current.showIcon()) {
			user.removeEffect(effect);
		}
	}
}
