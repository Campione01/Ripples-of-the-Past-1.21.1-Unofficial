package rotp.core.impl.powers.zombie;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;
import static rotp.core.core.JojoRegistries.ABILITY_TYPES;
import static rotp.core.init.power.ModPlayerPowers.PLAYER_POWERS;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.controls.InputUseVanillaMapping;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.impl.powers.zombie.abilities.ZombieClawLacerateAbility;
import rotp.core.impl.powers.zombie.abilities.ZombieDevourAbility;
import rotp.core.impl.powers.zombie.abilities.ZombieDisguiseAbility;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ZombiePowerType extends PlayerPowerType<ZombieData> {
	public static final int COLOR = 0x99BB00;

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> ZOMBIE_CLAW_LACERATE = ABILITY_TYPES.register(
			"zombie_claw_lacerate", key -> new AbilityType<>(key, ZombieClawLacerateAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> ZOMBIE_DEVOUR = ABILITY_TYPES.register(
			"zombie_devour", key -> new AbilityType<>(key, ZombieDevourAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> ZOMBIE_DISGUISE = ABILITY_TYPES.register("zombie_disguise",
			key -> new AbilityType<>(key, ZombieDisguiseAbility::new));

	public static final DeferredHolder<PlayerPowerType<?>, ZombiePowerType> ZOMBIE = PLAYER_POWERS.register(
			"zombie", key -> new ZombiePowerType(key, new MovesetBuilder()
					.addAbility("zombie_claw_lacerate", ZOMBIE_CLAW_LACERATE, ZombiePowerType::combat)
					.addAbility("zombie_devour", ZOMBIE_DEVOUR, ZombiePowerType::combat)
					.addAbility("zombie_disguise", ZOMBIE_DISGUISE, ZombiePowerType::utility)
					.makeControlScheme("default")
						.makeMovesetGroup("moveset_default_group", new InputUseVanillaMapping("jojo_ripples.key.non_stand_mode"))
							.bind("zombie_claw_lacerate", InputMethod.CLICK, InputKey.LMB)
							.bind("zombie_devour", InputMethod.HOLD, InputKey.MMB)
							.bind("zombie_disguise", InputMethod.HOLD, InputKey.RMB)
							.makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
							.addToHotbar("zombie_claw_lacerate", 0, InputMethod.CLICK)
							.addToHotbar("zombie_devour", 0, InputMethod.HOLD)
							.addToHotbar("zombie_disguise", 0, InputMethod.HOLD)
					.finalizeControlScheme()));

	
	protected ZombiePowerType(ResourceLocation registryKey, MovesetBuilder abilitySet) {
		super(registryKey, abilitySet);
	}
	
	@Override
	public ZombieData newDataInstance() {
		return new ZombieData();
	}

	public boolean isHighSaturation(LivingEntity entity) {
		return entity != null && PlayerPower.getPowerData(entity, ZOMBIE)
				.map(data -> data.isHighSaturation(entity))
				.orElse(false);
	}

	public int bloodLevel(LivingEntity entity) {
		return entity != null ? PlayerPower.getPowerData(entity, ZOMBIE)
				.map(data -> data.bloodLevel(entity))
				.orElse(0) : 0;
	}

	@Override
	public boolean isLeapUnlocked(PlayerPower power) {
		return true;
	}

	@Override
	public float getLeapStrength(PlayerPower power) {
		LivingEntity user = power != null ? power.getUser() : null;
		return user != null ? Math.max(bloodLevel(user), 0) * 0.2F : 0.0F;
	}

	@Override
	public int getLeapCooldownPeriod(PlayerPower power) {
		return 20;
	}

	@Override
	public float getLeapEnergyCost(PlayerPower power) {
		return 0.0F;
	}

	@Override
	public float getStandMaxStaminaFactor(PlayerPower power, StandPower standPower) {
		return bloodStaminaFactor(power, 2);
	}

	@Override
	public float getStandStaminaRegenFactor(PlayerPower power, StandPower standPower) {
		return bloodStaminaFactor(power, 4);
	}

	private float bloodStaminaFactor(PlayerPower power, int multiplier) {
		LivingEntity user = power != null ? power.getUser() : null;
		return user != null ? Math.max((bloodLevel(user) - 4) * multiplier, 1) : 1.0F;
	}

	private static void combat(Ability ability) {
		ability.usageGroup = AbilityUsageGroup.COMBAT;
	}

	private static void utility(Ability ability) {
		ability.usageGroup = AbilityUsageGroup.UTILITY;
	}

}
