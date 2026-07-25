package com.github.standobyte.jojoimpl.powers.pillarman;

import static com.github.standobyte.jojo.core.JojoRegistries.ABILITY_TYPES;
import static com.github.standobyte.jojo.init.power.ModPlayerPowers.PLAYER_POWERS;

import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.controls.InputKey;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.ability.controls.InputUseVanillaMapping;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanAbsorptionAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanAtmosphericRiftAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanBladeBarrageAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanBladeDashAttackAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanBladeSlashAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanDivineSandstormAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanEnhancedSensesAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanErraticBlazeKingAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanEvasionAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanGiantCarthwheelPrisonAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanHeavyPunchAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanHideInEntityAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanHornAttackAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanLightFlashAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanLightFlashDecoyAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanRegenerationAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanRibsBladesAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanSelfDetonationAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanSmallSandstormAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanStoneFormAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanUnnaturalAgilityAbility;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanWindCloakAbility;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

public class PillarmanPowerType extends PlayerPowerType<PillarmanData> {
	public static final int COLOR = 0xFFAA00;

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_HEAVY_PUNCH = ABILITY_TYPES.register(
			"pillarman_heavy_punch", key -> new AbilityType<>(key, PillarmanHeavyPunchAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_STONE_FORM = ABILITY_TYPES.register(
			"pillarman_stone_form", key -> new AbilityType<>(key, PillarmanStoneFormAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_ABSORPTION = ABILITY_TYPES.register(
			"pillarman_absorption", key -> new AbilityType<>(key, PillarmanAbsorptionAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_HORN_ATTACK = ABILITY_TYPES.register(
			"pillarman_horn_attack", key -> new AbilityType<>(key, PillarmanHornAttackAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_RIBS_BLADES = ABILITY_TYPES.register(
			"pillarman_ribs_blades", key -> new AbilityType<>(key, PillarmanRibsBladesAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_HIDE_IN_ENTITY = ABILITY_TYPES.register(
			"pillarman_hide_in_entity", key -> new AbilityType<>(key, PillarmanHideInEntityAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_REGENERATION = ABILITY_TYPES.register(
			"pillarman_regeneration", key -> new AbilityType<>(key, PillarmanRegenerationAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_ENHANCED_SENSES = ABILITY_TYPES.register(
			"pillarman_enhanced_senses", key -> new AbilityType<>(key, PillarmanEnhancedSensesAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_EVASION = ABILITY_TYPES.register(
			"pillarman_evasion", key -> new AbilityType<>(key, PillarmanEvasionAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_UNNATURAL_AGILITY = ABILITY_TYPES.register(
			"pillarman_unnatural_agility", key -> new AbilityType<>(key, PillarmanUnnaturalAgilityAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_SMALL_SANDSTORM = ABILITY_TYPES.register(
			"pillarman_small_sandstorm", key -> new AbilityType<>(key, PillarmanSmallSandstormAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_DIVINE_SANDSTORM = ABILITY_TYPES.register(
			"pillarman_divine_sandstorm", key -> new AbilityType<>(key, PillarmanDivineSandstormAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_WIND_CLOAK = ABILITY_TYPES.register(
			"pillarman_wind_cloak", key -> new AbilityType<>(key, PillarmanWindCloakAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_ATMOSPHERIC_RIFT = ABILITY_TYPES.register(
			"pillarman_atmospheric_rift", key -> new AbilityType<>(key, PillarmanAtmosphericRiftAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_ERRATIC_BLAZE_KING = ABILITY_TYPES.register(
			"pillarman_erratic_blaze_king", key -> new AbilityType<>(key, PillarmanErraticBlazeKingAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_GIANT_CARTHWHEEL_PRISON = ABILITY_TYPES.register(
			"pillarman_giant_carthwheel_prison", key -> new AbilityType<>(key, PillarmanGiantCarthwheelPrisonAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_SELF_DETONATION = ABILITY_TYPES.register(
			"pillarman_self_detonation", key -> new AbilityType<>(key, PillarmanSelfDetonationAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_LIGHT_FLASH = ABILITY_TYPES.register(
			"pillarman_light_flash", key -> new AbilityType<>(key, PillarmanLightFlashAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_LIGHT_FLASH_DECOY = ABILITY_TYPES.register(
			"pillarman_light_flash_decoy", key -> new AbilityType<>(key, PillarmanLightFlashDecoyAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_BLADE_SLASH = ABILITY_TYPES.register(
			"pillarman_blade_slash", key -> new AbilityType<>(key, PillarmanBladeSlashAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_BLADE_DASH_ATTACK = ABILITY_TYPES.register(
			"pillarman_blade_dash_attack", key -> new AbilityType<>(key, PillarmanBladeDashAttackAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> PILLAR_MAN_BLADE_BARRAGE = ABILITY_TYPES.register(
			"pillarman_blade_barrage", key -> new AbilityType<>(key, PillarmanBladeBarrageAbility::new));

	public static final DeferredHolder<PlayerPowerType<?>, PillarmanPowerType> PILLAR_MAN = PLAYER_POWERS.register(
			"pillarman", key -> new PillarmanPowerType(key, new MovesetBuilder()
					.addAbility("pillarman_heavy_punch", PILLAR_MAN_HEAVY_PUNCH)
					.addAbility("pillarman_stone_form", PILLAR_MAN_STONE_FORM)
					.addAbility("pillarman_absorption", PILLAR_MAN_ABSORPTION)
					.addAbility("pillarman_horn_attack", PILLAR_MAN_HORN_ATTACK)
					.addAbility("pillarman_ribs_blades", PILLAR_MAN_RIBS_BLADES)
					.addAbility("pillarman_hide_in_entity", PILLAR_MAN_HIDE_IN_ENTITY)
					.addAbility("pillarman_regeneration", PILLAR_MAN_REGENERATION)
					.addAbility("pillarman_enhanced_senses", PILLAR_MAN_ENHANCED_SENSES)
					.addAbility("pillarman_evasion", PILLAR_MAN_EVASION)
					.addAbility("pillarman_unnatural_agility", PILLAR_MAN_UNNATURAL_AGILITY)
					.addAbility("pillarman_small_sandstorm", PILLAR_MAN_SMALL_SANDSTORM)
					.addAbility("pillarman_divine_sandstorm", PILLAR_MAN_DIVINE_SANDSTORM)
					.addAbility("pillarman_wind_cloak", PILLAR_MAN_WIND_CLOAK)
					.addAbility("pillarman_atmospheric_rift", PILLAR_MAN_ATMOSPHERIC_RIFT)
					.addAbility("pillarman_erratic_blaze_king", PILLAR_MAN_ERRATIC_BLAZE_KING)
					.addAbility("pillarman_giant_carthwheel_prison", PILLAR_MAN_GIANT_CARTHWHEEL_PRISON)
					.addAbility("pillarman_self_detonation", PILLAR_MAN_SELF_DETONATION)
					.addAbility("pillarman_light_flash", PILLAR_MAN_LIGHT_FLASH)
					.addAbility("pillarman_light_flash_decoy", PILLAR_MAN_LIGHT_FLASH_DECOY)
					.addAbility("pillarman_blade_slash", PILLAR_MAN_BLADE_SLASH)
					.addAbility("pillarman_blade_dash_attack", PILLAR_MAN_BLADE_DASH_ATTACK)
					.addAbility("pillarman_blade_barrage", PILLAR_MAN_BLADE_BARRAGE)
					.makeControlScheme("default")
						.makeMovesetGroup("moveset_group.pillarman.combat", new InputUseVanillaMapping("jojo_ripples.key.non_stand_mode"))
							.bind("pillarman_heavy_punch", InputMethod.CLICK, InputKey.LMB)
							.bind("pillarman_stone_form", InputMethod.HOLD, InputKey.RMB)
							.bind("pillarman_absorption", InputMethod.HOLD, InputKey.MMB)
							.bind("pillarman_regeneration", InputMethod.CLICK, InputKey.R)
						.makeMovesetGroup("moveset_group.pillarman.body", new InputUseVanillaMapping("jojo_ripples.key.non_stand_mode"))
							.bind("pillarman_horn_attack", InputMethod.CLICK, InputKey.LMB)
							.bind("pillarman_ribs_blades", InputMethod.CLICK, InputKey.LMB)
							.bind("pillarman_hide_in_entity", InputMethod.HOLD, InputKey.RMB)
						.makeMovesetGroup("moveset_group.pillarman.utility", new InputUseVanillaMapping("jojo_ripples.key.non_stand_mode"))
							.bind("pillarman_enhanced_senses", InputMethod.HOLD, InputKey.C)
							.bind("pillarman_evasion", InputMethod.HOLD, InputKey.X)
							.bind("pillarman_unnatural_agility", InputMethod.HOLD, InputKey.X.withModifier(InputKey.Modifier.SHIFT))
						.makeMovesetGroup("moveset_group.pillarman.wind", new InputUseVanillaMapping("jojo_ripples.key.non_stand_mode"))
							.bind("pillarman_small_sandstorm", InputMethod.CLICK, InputKey.LMB)
							.bind("pillarman_divine_sandstorm", InputMethod.HOLD, InputKey.LMB.withModifier(InputKey.Modifier.SHIFT))
							.bind("pillarman_wind_cloak", InputMethod.HOLD, InputKey.RMB)
							.bind("pillarman_atmospheric_rift", InputMethod.HOLD, InputKey.LMB)
						.makeMovesetGroup("moveset_group.pillarman.heat", new InputUseVanillaMapping("jojo_ripples.key.non_stand_mode"))
							.bind("pillarman_erratic_blaze_king", InputMethod.CLICK, InputKey.LMB)
							.bind("pillarman_giant_carthwheel_prison", InputMethod.HOLD, InputKey.LMB)
							.bind("pillarman_self_detonation", InputMethod.HOLD, InputKey.LMB)
						.makeMovesetGroup("moveset_group.pillarman.light", new InputUseVanillaMapping("jojo_ripples.key.non_stand_mode"))
							.bind("pillarman_light_flash", InputMethod.HOLD, InputKey.RMB)
							.bind("pillarman_light_flash_decoy", InputMethod.HOLD, InputKey.RMB.withModifier(InputKey.Modifier.SHIFT))
							.bind("pillarman_blade_dash_attack", InputMethod.HOLD, InputKey.LMB)
							.bind("pillarman_blade_slash", InputMethod.CLICK, InputKey.LMB.withModifier(InputKey.Modifier.SHIFT))
							.bind("pillarman_blade_barrage", InputMethod.HOLD, InputKey.LMB.withModifier(InputKey.Modifier.SHIFT))
					.finalizeControlScheme()));

	
	protected PillarmanPowerType(ResourceLocation registryKey, MovesetBuilder abilitySet) {
		super(registryKey, abilitySet);
	}
	
	@Override
	public PillarmanData newDataInstance() {
		return new PillarmanData();
	}

	@Override
	public boolean keepOnDeath(PlayerPower power) {
		return true;
	}

	@Override
	public boolean isLeapUnlocked(PlayerPower power) {
		return true;
	}

	@Override
	public float getLeapStrength(PlayerPower power) {
		return (2.0F + Math.min(getEvolutionStage(power), 2.25F) / 2.0F) * 0.6F;
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
		return getEvolutionStage(power);
	}

	@Override
	public float getStandStaminaRegenFactor(PlayerPower power, StandPower standPower) {
		return getEvolutionStage(power);
	}

	private float getEvolutionStage(PlayerPower power) {
		return power.getCurTypeData(PILLAR_MAN)
				.map(PillarmanData::getEvolutionStage)
				.orElse(1);
	}

}
