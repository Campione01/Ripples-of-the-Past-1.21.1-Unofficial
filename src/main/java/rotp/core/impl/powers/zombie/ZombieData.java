package rotp.core.impl.powers.zombie;

import java.util.List;

import javax.annotation.Nullable;

import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.PowerType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerData;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.impl.powers.vampirism.VampirismUtil;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class ZombieData extends PlayerPowerData {
	public static final float BASE_MAX_ENERGY = 1000.0F;
	private static final int LAST_BLOOD_LEVEL_UNKNOWN = -999;
	private static final List<Holder<MobEffect>> PASSIVE_EFFECTS = List.of(
			MobEffects.HEALTH_BOOST, MobEffects.DAMAGE_BOOST, MobEffects.MOVEMENT_SPEED,
			MobEffects.DIG_SPEED, MobEffects.JUMP, MobEffects.NIGHT_VISION);

	private int lastBloodLevel = LAST_BLOOD_LEVEL_UNKNOWN;
	private boolean disguised;
	private float energy;
	private boolean removingPassiveEffects;
	
	public ZombieData() {
		super(ZombiePowerType.ZOMBIE.get());
	}

	@Override
	public void onPowerGiven(Power<?> userPower, @Nullable PowerType oldType, @Nullable PowerData oldData) {
		LivingEntity user = userPower.getUser();
		if (!user.level().isClientSide()) {
			setEnergy(user, getMaxEnergy(user));
			updateZombiePassiveEffects(user);
		}
		super.onPowerGiven(userPower, oldType, oldData);
	}

	@Override
	public void onPowerCleared(Power<?> userPower, @Nullable PowerType newType) {
		removeZombiePassiveEffects(userPower.getUser());
		super.onPowerCleared(userPower, newType);
	}

	@Override
	public void onTemporaryPowerSuspended(
			PlayerPower power,
			PlayerPowerType<?> temporaryType) {
		removeZombiePassiveEffects(power.getUser());
	}

	@Override
	public void onTemporaryPowerRestored(
			PlayerPower power,
			PlayerPowerType<?> temporaryType) {
		updateZombiePassiveEffects(power.getUser());
	}

	@Override
	public void onTemporaryPowerEnded(
			PlayerPower temporaryPower,
			PlayerPower restoredPower,
			PlayerPowerType<?> restoredType) {
		removeZombiePassiveEffects(temporaryPower.getUser());
		if (restoredPower.getUser() != temporaryPower.getUser()) {
			removeZombiePassiveEffects(restoredPower.getUser());
		}
	}

	@Override
	public void tick(Power<?> userPower) {
		super.tick(userPower);
		LivingEntity user = userPower.getUser();
		if (!user.isAlive()) {
			disguised = false;
		}
		tickEnergy(user);
		if (user.level().isClientSide()) {
			return;
		}

		if (user instanceof Player player) {
			player.getFoodData().setFoodLevel(17);
		}
		user.setAirSupply(user.getMaxAirSupply());
		int bloodLevel = bloodLevel(user);
		if (refreshBloodLevel(bloodLevel)) {
			updateZombiePassiveEffects(user);
		}
	}

	public void toggleDisguise() {
		setDisguiseEnabled(!disguised);
	}

	public void toggleDisguise(LivingEntity user) {
		setDisguiseEnabled(!disguised, user);
	}

	public void setDisguiseEnabled(boolean isEnabled) {
		setDisguiseEnabled(isEnabled, null);
	}

	public void setDisguiseEnabled(boolean isEnabled, LivingEntity user) {
		if (this.disguised != isEnabled) {
			this.disguised = isEnabled;
			if (user != null && !user.level().isClientSide()) {
				updateZombiePassiveEffects(user);
				syncOnUpdate(user);
			}
		}
	}

	public boolean isDisguiseEnabled() {
		return disguised;
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag nbt = super.serializeNBT(provider);
		nbt.putBoolean("DisguiseEnabled", disguised);
		nbt.putFloat("ZombieEnergy", energy);
		return nbt;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
		super.deserializeNBT(provider, nbt);
		disguised = nbt.getBoolean("DisguiseEnabled");
		setEnergyUnchecked(nbt.getFloat("ZombieEnergy"));
		lastBloodLevel = LAST_BLOOD_LEVEL_UNKNOWN;
	}

	@Override
	public void toBuf(FriendlyByteBuf buf, boolean isSentToTracking) {
		super.toBuf(buf, isSentToTracking);
		buf.writeBoolean(disguised);
		buf.writeFloat(energy);
	}

	@Override
	public void fromBuf(FriendlyByteBuf buf, boolean isSentToTracking) {
		super.fromBuf(buf, isSentToTracking);
		disguised = buf.readBoolean();
		setEnergyUnchecked(buf.readFloat());
		lastBloodLevel = LAST_BLOOD_LEVEL_UNKNOWN;
	}

	public float getEnergy() {
		return energy;
	}

	public float getMaxEnergy(LivingEntity user) {
		return BASE_MAX_ENERGY * VampirismUtil.maxBloodMultiplier(user);
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

	public boolean isHighSaturation(LivingEntity user) {
		return getEnergyRatio(user) >= 0.8F;
	}

	public int bloodLevel(LivingEntity user) {
		int difficultyId = getDifficultyId(user);
		if (difficultyId == 0) {
			return -1;
		}
		return Math.min((int) (getEnergyRatio(user) * 7.5F), 4) + difficultyId;
	}

	public boolean refreshBloodLevel(int bloodLevel) {
		boolean bloodLevelChanged = this.lastBloodLevel != bloodLevel;
		this.lastBloodLevel = bloodLevel;
		return bloodLevelChanged;
	}

	private void tickEnergy(LivingEntity user) {
		float inc = -VampirismUtil.bloodTickDown(user);
		if (user instanceof Player player && player.getAbilities().instabuild) {
			inc = Math.max(inc, 0.0F);
		}
		setEnergy(user, getEnergy() + inc);
	}

	private static int getDifficultyId(LivingEntity user) {
		return Mth.clamp(user.level().getDifficulty().getId(), 0, 3);
	}

	private void updateZombiePassiveEffects(LivingEntity user) {
		for (Holder<MobEffect> effect : PASSIVE_EFFECTS) {
			refreshHiddenEffect(user, effect, getPassiveEffectLevel(user, effect));
		}
	}

	// 1.16 ZombiePowerType.getPassiveEffectLevel; -1 means no effect
	private int getPassiveEffectLevel(LivingEntity user, Holder<MobEffect> effect) {
		if (effect.is(MobEffects.HEALTH_BOOST)) {
			return getDifficultyId(user) * 2;
		}
		if (effect.is(MobEffects.NIGHT_VISION)) {
			return 0;
		}
		if (effect.is(MobEffects.DAMAGE_BOOST) || effect.is(MobEffects.MOVEMENT_SPEED)
				|| effect.is(MobEffects.DIG_SPEED) || effect.is(MobEffects.JUMP)) {
			return disguised ? -1 : bloodLevel(user) - 5;
		}
		return -1;
	}

	/**
	 * 1.16 GameplayEventHandler.cancelPotionRemoval: milk, totems and effect clearing do not remove
	 * a hidden passive effect the zombie power grants at its current level.
	 */
	public static boolean isKeptPassiveEffect(LivingEntity user, MobEffectInstance instance) {
		if (instance.isVisible() || instance.showIcon()) {
			return false;
		}
		ZombieData data = PlayerPower.getPowerData(user, ModPlayerPowers.ZOMBIE).orElse(null);
		return data != null && !data.removingPassiveEffects
				&& data.getPassiveEffectLevel(user, instance.getEffect()) == instance.getAmplifier();
	}

	private void removeZombiePassiveEffects(LivingEntity user) {
		// Suspension still runs while this power is current, so let the removal through.
		removingPassiveEffects = true;
		try {
			for (Holder<MobEffect> effect : PASSIVE_EFFECTS) {
				removeHiddenPassiveEffect(user, effect);
			}
		}
		finally {
			removingPassiveEffects = false;
		}
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
