package com.github.standobyte.jojoimpl.powers.pillarman;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.PowerType;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerData;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismUtil;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;

public class PillarmanData extends PlayerPowerData {
	public static final int MAX_STAGE_LEVEL = 4;
	public static final float BASE_MAX_ENERGY = 1000.0F;
	private static final AttributeModifier ATTACK_DAMAGE = new AttributeModifier(
			JojoMod.resLoc("pillar_man_attack_damage"), 1.0, AttributeModifier.Operation.ADD_VALUE);
	private static final AttributeModifier ATTACK_SPEED = new AttributeModifier(
			JojoMod.resLoc("pillar_man_attack_speed"), 0.15, AttributeModifier.Operation.ADD_VALUE);
	private static final AttributeModifier MOVEMENT_SPEED = new AttributeModifier(
			JojoMod.resLoc("pillar_man_movement_speed"), 0.01, AttributeModifier.Operation.ADD_VALUE);
	private static final AttributeModifier SWIMMING_SPEED = new AttributeModifier(
			JojoMod.resLoc("pillar_man_swimming_speed"), 0.15, AttributeModifier.Operation.ADD_VALUE);
	private static final AttributeModifier MAX_HEALTH = new AttributeModifier(
			JojoMod.resLoc("pillar_man_max_health"), 10, AttributeModifier.Operation.ADD_VALUE);

	private int stage = 1;
	private PillarmanMode mode = PillarmanMode.NONE;
	private boolean stoneForm;
	private int stoneFormPose;
	private int stoneFormAnimTicks;
	private boolean bladesVisible;
	private int lastAppliedBuffStage = Integer.MIN_VALUE;
	private float energy;
	private final List<Integer> eatenTntFuse = new ArrayList<>();
	private final Map<String, Integer> abilityCooldowns = new HashMap<>();
	private final Map<String, Integer> abilityCooldownTotals = new HashMap<>();
	
	public PillarmanData() {
		super(PillarmanPowerType.PILLAR_MAN.get());
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag nbt = super.serializeNBT(provider);
		nbt.putInt("PillarmanStage", stage);
		nbt.putString("PillarmanMode", mode.name());
		nbt.putBoolean("StoneForm", stoneForm);
		nbt.putInt("StoneFormPose", stoneFormPose);
		nbt.putBoolean("BladesVisible", bladesVisible);
		nbt.putFloat("PillarmanEnergy", energy);
		if (!abilityCooldowns.isEmpty()) {
			CompoundTag cooldowns = new CompoundTag();
			abilityCooldowns.forEach((abilityName, cooldown) -> cooldowns.putIntArray(abilityName,
					new int[] { cooldown, abilityCooldownTotals.getOrDefault(abilityName, cooldown) }));
			nbt.put("PillarmanAbilityCooldowns", cooldowns);
		}
		if (!eatenTntFuse.isEmpty()) {
			int[] fuses = new int[eatenTntFuse.size()];
			for (int i = 0; i < eatenTntFuse.size(); i++) {
				fuses[i] = eatenTntFuse.get(i);
			}
			nbt.putIntArray("EatenTntFuse", fuses);
		}
		return nbt;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
		super.deserializeNBT(provider, nbt);
		stage = nbt.contains("PillarmanStage") ? nbt.getInt("PillarmanStage") : 1;
		stage = clampStage(stage);
		mode = parseMode(nbt.getString("PillarmanMode"));
		stoneForm = nbt.getBoolean("StoneForm");
		stoneFormPose = Mth.clamp(nbt.getInt("StoneFormPose"), 0, 2);
		stoneFormAnimTicks = 0;
		bladesVisible = nbt.getBoolean("BladesVisible");
		setEnergyUnchecked(nbt.getFloat("PillarmanEnergy"));
		eatenTntFuse.clear();
		for (int fuse : nbt.getIntArray("EatenTntFuse")) {
			if (fuse > 0) {
				eatenTntFuse.add(fuse);
			}
		}
		abilityCooldowns.clear();
		abilityCooldownTotals.clear();
		if (nbt.get("PillarmanAbilityCooldowns") instanceof CompoundTag cooldowns) {
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
		lastAppliedBuffStage = Integer.MIN_VALUE;
	}

	@Override
	public void toBuf(FriendlyByteBuf buf, boolean isSentToTracking) {
		super.toBuf(buf, isSentToTracking);
		buf.writeInt(stage);
		buf.writeEnum(mode);
		buf.writeBoolean(stoneForm);
		buf.writeByte(stoneFormPose);
		buf.writeBoolean(bladesVisible);
		buf.writeFloat(energy);
		buf.writeVarInt(abilityCooldowns.size());
		for (Map.Entry<String, Integer> cooldown : abilityCooldowns.entrySet()) {
			String abilityName = cooldown.getKey();
			buf.writeUtf(abilityName);
			buf.writeVarInt(cooldown.getValue());
			buf.writeVarInt(abilityCooldownTotals.getOrDefault(abilityName, cooldown.getValue()));
		}
	}

	@Override
	public void fromBuf(FriendlyByteBuf buf, boolean isSentToTracking) {
		super.fromBuf(buf, isSentToTracking);
		stage = clampStage(buf.readInt());
		mode = buf.readEnum(PillarmanMode.class);
		boolean wasStoneForm = stoneForm;
		stoneForm = buf.readBoolean();
		stoneFormPose = Mth.clamp(buf.readByte(), 0, 2);
		if (stoneForm != wasStoneForm) {
			stoneFormAnimTicks = 0;
		}
		bladesVisible = buf.readBoolean();
		setEnergyUnchecked(buf.readFloat());
		abilityCooldowns.clear();
		abilityCooldownTotals.clear();
		int cooldownCount = buf.readVarInt();
		for (int i = 0; i < cooldownCount; i++) {
			String abilityName = buf.readUtf();
			int cooldown = buf.readVarInt();
			int totalCooldown = buf.readVarInt();
			if (cooldown > 0) {
				abilityCooldowns.put(abilityName, cooldown);
				abilityCooldownTotals.put(abilityName, Math.max(totalCooldown, cooldown));
			}
		}
		lastAppliedBuffStage = Integer.MIN_VALUE;
	}

	@Override
	public void onPowerGiven(Power<?> userPower, @Nullable PowerType oldType, @Nullable PowerData oldData) {
		LivingEntity user = userPower.getUser();
		if (!user.level().isClientSide()) {
			addEnergy(user, BASE_MAX_ENERGY);
			updatePillarmanBuffs(user);
			user.level().playSound(null, user, ModSoundEvents.PILLAR_MAN_AWAKENING.get(), user.getSoundSource(), 1.0F, 1.0F);
		}
		super.onPowerGiven(userPower, oldType, oldData);
	}

	@Override
	public void onPowerCleared(Power<?> userPower, @Nullable PowerType newType) {
		removePillarmanBuffs(userPower.getUser());
		super.onPowerCleared(userPower, newType);
	}

	@Override
	public void tick(Power<?> userPower) {
		super.tick(userPower);
		LivingEntity user = userPower.getUser();
		if (!user.isAlive()) {
			stoneForm = false;
			bladesVisible = false;
		}
		if (stoneForm) {
			stoneFormAnimTicks++;
		}
		else {
			stoneFormAnimTicks = 0;
		}
		tickAbilityCooldowns();
		tickEnergy(user);
		if (user.level().isClientSide()) {
			return;
		}

		tickEatenTntFuse(user);
		updatePillarmanBuffs(user);
		if (stage > 1) {
			if (user instanceof Player player) {
				player.getFoodData().setFoodLevel(17);
			}
			user.setAirSupply(user.getMaxAirSupply());
			refreshHiddenEffect(user, MobEffects.NIGHT_VISION, Integer.MAX_VALUE, 0);
			updatePillarmanRegeneration(user);
		}
		else {
			removeHiddenPassiveEffect(user, MobEffects.NIGHT_VISION);
			removeHiddenPassiveEffect(user, MobEffects.REGENERATION);
		}
		if (isStoneFormEnabled()) {
			user.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 20, 0, false, false, true));
			user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 3, false, false, true));
			user.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, true));
			user.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
		}
	}

	public int getEvolutionStage() {
		return stage;
	}

	public void setEvolutionStage(int stage) {
		setEvolutionStage(stage, null);
	}

	public void setEvolutionStage(int stage, LivingEntity user) {
		int newStage = clampStage(stage);
		if (this.stage != newStage) {
			this.stage = newStage;
			if (user != null) {
				setEnergy(user, getEnergy());
			}
			if (user != null) {
				if (!user.level().isClientSide() && user instanceof ServerPlayer player) {
					if (newStage == 2) {
						ModCriteriaTriggers.triggerPillarmanEvolve(player);
					}
					if (newStage >= 3) {
						ModCriteriaTriggers.triggerPillarmanEvolve(player);
						ModCriteriaTriggers.triggerPillarmanEvolveAja(player);
					}
					updatePillarmanBuffs(user);
				}
				syncOnUpdate(user);
			}
		}
	}

	public PillarmanMode getMode() {
		return mode;
	}

	public void setMode(PillarmanMode mode) {
		setMode(mode, null);
	}

	public void setMode(PillarmanMode mode, LivingEntity user) {
		PillarmanMode newMode = mode != null ? mode : PillarmanMode.NONE;
		if (this.mode != newMode) {
			this.mode = newMode;
			if (user != null) {
				if (!user.level().isClientSide() && user instanceof ServerPlayer player) {
					switch (newMode) {
					case WIND -> {
						ModCriteriaTriggers.triggerPillarmanWindMode(player);
						user.level().playSound(null, user, ModSoundEvents.PILLAR_MAN_WIND_MODE.get(), user.getSoundSource(), 1.0F, 1.0F);
						bladesVisible = false;
					}
					case HEAT -> {
						ModCriteriaTriggers.triggerPillarmanHeatMode(player);
						user.level().playSound(null, user, ModSoundEvents.PILLAR_MAN_HEAT_MODE.get(), user.getSoundSource(), 1.0F, 1.0F);
						bladesVisible = false;
					}
					case LIGHT -> {
						ModCriteriaTriggers.triggerPillarmanLightMode(player);
						user.level().playSound(null, user, ModSoundEvents.PILLAR_MAN_LIGHT_MODE.get(), user.getSoundSource(), 1.0F, 1.0F);
					}
					case NONE -> {
					}
					}
				}
				syncOnUpdate(user);
			}
		}
	}

	public boolean toggleStoneForm() {
		setStoneFormEnabled(!stoneForm);
		return stoneForm;
	}

	public void setStoneFormEnabled(boolean stoneForm) {
		if (this.stoneForm != stoneForm) {
			stoneFormAnimTicks = 0;
		}
		this.stoneForm = stoneForm;
	}

	public boolean isStoneFormEnabled() {
		return stoneForm;
	}

	public void setStoneFormPose(int stoneFormPose) {
		this.stoneFormPose = Mth.clamp(stoneFormPose, 0, 2);
	}

	public int getStoneFormPose() {
		return stoneFormPose;
	}

	public int getStoneFormAnimTicks() {
		return stoneFormAnimTicks;
	}

	public void setBladesVisible(boolean bladesVisible) {
		this.bladesVisible = bladesVisible;
	}

	public boolean getBladesVisible() {
		return bladesVisible;
	}

	public float getEnergy() {
		return energy;
	}

	public float getMaxEnergy(LivingEntity user) {
		return BASE_MAX_ENERGY * VampirismUtil.maxBloodMultiplier(user) * getEvolutionStage();
	}

	public void addEnergy(LivingEntity user, float amount) {
		setEnergy(user, energy + amount);
	}

	public boolean hasEnergy(float amount) {
		return amount <= 0.0F || getEnergy() >= amount;
	}

	public boolean consumeEnergy(LivingEntity user, float amount) {
		if (!hasEnergy(amount)) {
			return false;
		}
		setEnergy(user, getEnergy() - Math.max(amount, 0.0F));
		return true;
	}

	public void addEatenTntFuse(int fuse) {
		if (fuse > 0) {
			eatenTntFuse.add(fuse);
		}
	}

	public void setEnergy(LivingEntity user, float amount) {
		energy = Mth.clamp(amount, 0.0F, Math.max(getMaxEnergy(user), 0.0F));
	}

	private void setEnergyUnchecked(float amount) {
		energy = Float.isFinite(amount) ? Math.max(amount, 0.0F) : 0.0F;
	}

	public float getEnergyRatio(LivingEntity user) {
		float maxEnergy = getMaxEnergy(user);
		return maxEnergy > 0.0F ? getEnergy() / maxEnergy : 0.0F;
	}

	public int getAbilityCooldown(String abilityName) {
		return abilityCooldowns.getOrDefault(abilityName, 0);
	}

	public boolean isAbilityOnCooldown(String abilityName) {
		return getAbilityCooldown(abilityName) > 0;
	}

	@Override
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

	private static int clampStage(int stage) {
		return Math.max(1, Math.min(MAX_STAGE_LEVEL, stage));
	}

	private void tickEnergy(LivingEntity user) {
		float inc = -VampirismUtil.bloodTickDown(user) * getEvolutionStage();
		if (user instanceof Player player && player.getAbilities().instabuild) {
			inc = Math.max(inc, 0.0F);
		}
		setEnergy(user, getEnergy() + inc);
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

	private void tickEatenTntFuse(LivingEntity user) {
		for (int i = eatenTntFuse.size() - 1; i >= 0; i--) {
			int ticksLeft = eatenTntFuse.get(i) - 1;
			if (ticksLeft <= 0) {
				user.level().playSound(null, user.getX(), user.getY(), user.getZ(),
						SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.1F,
						0.9F + user.getRandom().nextFloat() * 0.2F);
				eatenTntFuse.remove(i);
			}
			else {
				eatenTntFuse.set(i, ticksLeft);
			}
		}
	}

	private int getPassiveRegenerationAmplifier(LivingEntity user) {
		if (stage == 1) {
			return -1;
		}
		float energyRatio = getEnergyRatio(user);
		if (energyRatio >= 0.3F) {
			return 1;
		}
		if (energyRatio >= 0.1F) {
			return 0;
		}
		return -1;
	}

	private static PillarmanMode parseMode(String modeName) {
		if (modeName == null || modeName.isEmpty()) {
			return PillarmanMode.NONE;
		}
		try {
			return PillarmanMode.valueOf(modeName);
		}
		catch (IllegalArgumentException e) {
			return PillarmanMode.NONE;
		}
	}

	private void updatePillarmanBuffs(LivingEntity user) {
		if (user.level().isClientSide() || lastAppliedBuffStage == stage) {
			return;
		}
		int level = getPillarmanBuffLevel();
		updateAttributeModifier(user, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE, level);
		updateAttributeModifier(user, Attributes.ATTACK_SPEED, ATTACK_SPEED, level);
		updateAttributeModifier(user, Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED, level);
		updateAttributeModifier(user, NeoForgeMod.SWIM_SPEED, SWIMMING_SPEED, level);
		updateAttributeModifier(user, Attributes.MAX_HEALTH, MAX_HEALTH, level);
		lastAppliedBuffStage = stage;
	}

	private void removePillarmanBuffs(LivingEntity user) {
		if (user.level().isClientSide()) {
			return;
		}
		removeAttributeModifier(user, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE);
		removeAttributeModifier(user, Attributes.ATTACK_SPEED, ATTACK_SPEED);
		removeAttributeModifier(user, Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED);
		removeAttributeModifier(user, NeoForgeMod.SWIM_SPEED, SWIMMING_SPEED);
		removeAttributeModifier(user, Attributes.MAX_HEALTH, MAX_HEALTH);
		removeHiddenPassiveEffect(user, MobEffects.NIGHT_VISION);
		removeHiddenPassiveEffect(user, MobEffects.REGENERATION);
		lastAppliedBuffStage = Integer.MIN_VALUE;
	}

	private void updatePillarmanRegeneration(LivingEntity user) {
		int amplifier = getPassiveRegenerationAmplifier(user);
		if (amplifier >= 0) {
			refreshHiddenEffect(user, MobEffects.REGENERATION, Integer.MAX_VALUE, amplifier);
		}
		else {
			removeHiddenPassiveEffect(user, MobEffects.REGENERATION);
		}
	}

	private int getPillarmanBuffLevel() {
		int level = 2 * stage;
		if (stage == 3) --level;
		if (stage > 3) level -= 2;
		return level;
	}

	private static void updateAttributeModifier(LivingEntity user, Holder<Attribute> attribute, AttributeModifier modifier, int multiplier) {
		AttributeInstance instance = user.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(modifier.id());
		instance.addTransientModifier(new AttributeModifier(
				modifier.id(), modifier.amount() * multiplier, modifier.operation()));
	}

	private static void removeAttributeModifier(LivingEntity user, Holder<Attribute> attribute, AttributeModifier modifier) {
		AttributeInstance instance = user.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(modifier.id());
		}
	}

	private static void refreshHiddenEffect(LivingEntity user, Holder<MobEffect> effect, int duration, int amplifier) {
		MobEffectInstance current = user.getEffect(effect);
		boolean currentHidden = current != null && !current.isVisible() && !current.showIcon();
		if (current == null || currentHidden && current.getAmplifier() != amplifier || !currentHidden && current.getAmplifier() < amplifier) {
			user.removeEffectNoUpdate(effect);
			user.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, false));
		}
	}

	private static void removeHiddenPassiveEffect(LivingEntity user, Holder<MobEffect> effect) {
		MobEffectInstance current = user.getEffect(effect);
		if (current != null && !current.isVisible() && !current.showIcon()) {
			user.removeEffect(effect);
		}
	}
}
