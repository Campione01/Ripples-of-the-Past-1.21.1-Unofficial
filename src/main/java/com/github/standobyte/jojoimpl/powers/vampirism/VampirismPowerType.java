package com.github.standobyte.jojoimpl.powers.vampirism;

import static com.github.standobyte.jojo.init.power.ModStands.SWITCH_SPECIAL;
import static com.github.standobyte.jojo.init.power.ModStands.USE_SPECIAL;
import static com.github.standobyte.jojo.core.JojoRegistries.ABILITY_TYPES;
import static com.github.standobyte.jojo.init.power.ModPlayerPowers.PLAYER_POWERS;

import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.AbilityUsageGroup;
import com.github.standobyte.jojo.powersystem.ability.controls.InputKey;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.ability.controls.InputUseVanillaMapping;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojoimpl.powers.vampirism.abilities.VampirismBloodDrainAbility;
import com.github.standobyte.jojoimpl.powers.vampirism.abilities.VampirismBloodGiftAbility;
import com.github.standobyte.jojoimpl.powers.vampirism.abilities.VampirismClawLacerateAbility;
import com.github.standobyte.jojoimpl.powers.vampirism.abilities.VampirismDarkAuraAbility;
import com.github.standobyte.jojoimpl.powers.vampirism.abilities.VampirismFreezeAbility;
import com.github.standobyte.jojoimpl.powers.vampirism.abilities.VampirismHamonSuicideAbility;
import com.github.standobyte.jojoimpl.powers.vampirism.abilities.VampirismSpaceRipperStingyEyesAbility;
import com.github.standobyte.jojoimpl.powers.vampirism.abilities.VampirismZombieSummonAbility;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.registries.DeferredHolder;

public class VampirismPowerType extends PlayerPowerType<VampirismData> {
	public static final int COLOR = 0xFF0000;

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> VAMPIRE_BLOOD_DRAIN = ABILITY_TYPES.register(
			"vampire_blood_drain", key -> new AbilityType<>(key, VampirismBloodDrainAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> VAMPIRE_BLOOD_GIFT = ABILITY_TYPES.register(
			"vampire_blood_gift", key -> new AbilityType<>(key, VampirismBloodGiftAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> VAMPIRE_CLAW_LACERATE = ABILITY_TYPES.register(
			"vampire_claw_lacerate", key -> new AbilityType<>(key, VampirismClawLacerateAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> VAMPIRE_DARK_AURA = ABILITY_TYPES.register(
			"vampire_dark_aura", key -> new AbilityType<>(key, VampirismDarkAuraAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> VAMPIRE_FREEZE = ABILITY_TYPES.register(
			"vampire_freeze", key -> new AbilityType<>(key, VampirismFreezeAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> VAMPIRE_HAMON_SUICIDE = ABILITY_TYPES.register(
			"vampire_hamon_suicide", key -> new AbilityType<>(key, VampirismHamonSuicideAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> VAMPIRE_SPACE_RIPPER_STINGY_EYES = ABILITY_TYPES.register(
			"space_ripper_stingy_eyes", key -> new AbilityType<>(key, VampirismSpaceRipperStingyEyesAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> VAMPIRE_ZOMBIE_SUMMON = ABILITY_TYPES.register(
			"vampire_zombie_summon", key -> new AbilityType<>(key, VampirismZombieSummonAbility::new));

	public static final DeferredHolder<PlayerPowerType<?>, VampirismPowerType> VAMPIRISM = PLAYER_POWERS.register(
			"vampirism", key -> new VampirismPowerType(key, new MovesetBuilder()
					.addAbility("vampirism_claw_lacerate", VAMPIRE_CLAW_LACERATE, VampirismPowerType::combat)
					.addAbility("vampirism_blood_drain", VAMPIRE_BLOOD_DRAIN, VampirismPowerType::combat)
					.addAbility("vampirism_freeze", VAMPIRE_FREEZE, VampirismPowerType::combat)
					.addAbility("vampirism_space_ripper_stingy_eyes", VAMPIRE_SPACE_RIPPER_STINGY_EYES, VampirismPowerType::combat)
					.addAbility("vampirism_blood_gift", VAMPIRE_BLOOD_GIFT, VampirismPowerType::utility)
					.addAbility("vampirism_zombie_summon", VAMPIRE_ZOMBIE_SUMMON, VampirismPowerType::utility)
					.addAbility("vampirism_dark_aura", VAMPIRE_DARK_AURA, VampirismPowerType::utility)
					.addAbility("vampirism_hamon_suicide", VAMPIRE_HAMON_SUICIDE, VampirismPowerType::utility)
					.makeControlScheme("default")
						.makeMovesetGroup("moveset_default_group", new InputUseVanillaMapping("jojo_ripples.key.non_stand_mode"))
							.bind("vampirism_claw_lacerate", InputMethod.CLICK, InputKey.LMB)
							.bind("vampirism_blood_drain", InputMethod.HOLD, InputKey.MMB)
							.bind("vampirism_blood_gift", InputMethod.HOLD, InputKey.RMB)
							.makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
							.addToHotbar("vampirism_claw_lacerate", 0, InputMethod.CLICK)
							.addToHotbar("vampirism_blood_drain", 0, InputMethod.HOLD)
							.addToHotbar("vampirism_freeze", 0, InputMethod.HOLD)
							.addToHotbar("vampirism_space_ripper_stingy_eyes", 0, InputMethod.HOLD)
							.addToHotbar("vampirism_blood_gift", 0, InputMethod.HOLD)
							.addToHotbar("vampirism_zombie_summon", 0, InputMethod.CLICK)
							.addToHotbar("vampirism_dark_aura", 0, InputMethod.CLICK)
							.addToHotbar("vampirism_hamon_suicide", 0, InputMethod.HOLD)
					.finalizeControlScheme()));


	protected VampirismPowerType(ResourceLocation registryKey, MovesetBuilder abilitySet) {
		super(registryKey, abilitySet);
	}

	@Override
	public VampirismData newDataInstance() {
		return new VampirismData();
	}

	@Override
	public boolean keepOnDeath(PlayerPower power) {
		return power.getCurTypeData(VAMPIRISM)
				.map(vampirism -> vampirism.shouldKeepOnDeath(power.getUser().level().isClientSide()))
				.orElse(false);
	}

	@Override
	public boolean isLeapUnlocked(PlayerPower power) {
		return power.getCurTypeData(VAMPIRISM)
				.map(vampirism -> vampirism.getCuringStage(power.getUser()) < 3)
				.orElse(false);
	}

	@Override
	public float getLeapStrength(PlayerPower power) {
		LivingEntity entity = power != null ? power.getUser() : null;
		if (entity == null) {
			return 0.0F;
		}
		return power.getCurTypeData(VAMPIRISM)
				.map(vampirism -> {
					float leapStrength = Math.max(vampirism.bloodLevel(entity), 0);
					if (!vampirism.isVampireAtFullPower()) {
						leapStrength *= 0.15F;
					}
					return leapStrength * 0.25F;
				})
				.orElse(0.0F);
	}

	@Override
	public int getLeapCooldownPeriod(PlayerPower power) {
		return 20;
	}

	@Override
	public float getLeapEnergyCost(PlayerPower power) {
		return 0.0F;
	}

	public boolean isHighOnBlood(LivingEntity entity) {
		VampirismData data = PlayerPower.getPowerData(entity, VAMPIRISM).orElse(null);
		if (data == null) {
			return false;
		}
		VampirismState.get(entity).blood();
		return data.isHighOnBlood(entity);
	}

	public int bloodLevel(LivingEntity entity) {
		VampirismData data = PlayerPower.getPowerData(entity, VAMPIRISM).orElse(null);
		return data != null ? data.bloodLevel(entity) : 0;
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
