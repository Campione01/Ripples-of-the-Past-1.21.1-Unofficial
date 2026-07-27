package com.github.standobyte.jojo.powersystem.playerpower;

import java.util.Map;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.standobyte.jojo.api.playerpower.PlayerPowerMovesetExtensions;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.PowerType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.Lazy;

public abstract class PlayerPowerType<D extends PlayerPowerData> extends PowerType {
	private final ResourceLocation registryKey;

	public PlayerPowerType(ResourceLocation registryKey, MovesetBuilder abilitySet) {
		super(abilitySet);
		this.registryKey = registryKey;
	}
	
	@Override
	@Nonnull public abstract D newDataInstance();

	
	@Override
	public ResourceLocation getId() {
		return registryKey;
	}

	@Override
	protected ResourceLocation getMovesetExtensionTargetId() {
		return registryKey;
	}

	@Override
	protected long getMovesetExtensionRevision(
			ResourceLocation extensionTarget) {
		return PlayerPowerMovesetExtensions.targetRevision(
				extensionTarget);
	}

	@Override
	protected void applyMovesetExtensions(
			ResourceLocation extensionTarget,
			MovesetBuilder moveset) {
		PlayerPowerMovesetExtensions.applyRegisteredExtensions(
				extensionTarget, moveset);
	}

    @Nullable
    public static PlayerPowerType<?> fromId(ResourceLocation id) {
        return JojoRegistries.PLAYER_POWER_TYPES_REG.get(id);
    }
	
	@Override
	public PowerClass<PlayerPower> getPowerClass() {
		return PowerClass.PLAYER_POWER;
	}
	
	public Lazy<Component> name = Lazy.of(() -> Component.translatable(Util.makeDescriptionId("power", this.getId())));
	@Override
	public Component getName(Power<?> playerPowerData) {
		return name.get();
	}

	public boolean keepOnDeath(PlayerPower power) {
		return false;
	}

	public boolean isLeapUnlocked(PlayerPower power) {
		return false;
	}

	public float getLeapStrength(PlayerPower power) {
		return 0.0F;
	}

	public int getLeapCooldownPeriod(PlayerPower power) {
		return 20;
	}

	public float getLeapEnergyCost(PlayerPower power) {
		return 0.0F;
	}

	public boolean hasLeapEnergy(PlayerPower power, float energyCost) {
		return energyCost <= 0.0F;
	}

	public boolean consumeLeapEnergy(PlayerPower power, float energyCost) {
		return energyCost <= 0.0F;
	}

	public void onLeap(PlayerPower power) {}

	public float getStandMaxStaminaFactor(PlayerPower power, StandPower standPower) {
		return 1.0F;
	}

	public float getStandStaminaRegenFactor(PlayerPower power, StandPower standPower) {
		return 1.0F;
	}

    public static Stream<PlayerPowerType<?>> getAllEnabledPlayerPowers() {
        return JojoRegistries.PLAYER_POWER_TYPES_REG.entrySet().stream().map(Map.Entry::getValue);
    }
}
