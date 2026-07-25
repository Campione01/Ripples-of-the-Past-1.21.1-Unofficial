package com.github.standobyte.jojoimpl.powers.hamon;

import static com.github.standobyte.jojo.init.power.ModStands.SWITCH_SPECIAL;
import static com.github.standobyte.jojo.init.power.ModStands.USE_SPECIAL;
import static com.github.standobyte.jojo.core.JojoRegistries.ABILITY_TYPES;
import static com.github.standobyte.jojo.init.power.ModPlayerPowers.PLAYER_POWERS;

import java.util.function.Supplier;

import com.github.standobyte.jojo.init.ModSoundEvents;
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
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonActionRuntimeAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonBreathAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonBubbleBarrierAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonBubbleCutterAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonBubbleLauncherAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonCutterAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonDetectorAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonHealingAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonHypnosisAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonLifeMagnetismAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonLiquidWalkingAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonMetalSilverOverdriveAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonMetalSilverOverdriveWeaponAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonOrganismInfusionAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonOverdriveAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonOverdriveBarrageAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonOverdriveBeatAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonPlantInfusionAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonPlantItemInfusionAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonProjectileShieldAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonProtectionAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonRebuffOverdriveAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonRopeTrapAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonScarletOverdriveAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonSendoOverdriveAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonSendoWaveKickAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonShockAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonSnakeMufflerAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonSpeedBoostAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonSunlightYellowOverdriveAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonSunlightYellowOverdriveBarrageAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonTornadoOverdriveAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonTurquoiseBlueOverdriveAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonWallClimbingAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonZoomPunchAbility;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

public class HamonPowerType extends PlayerPowerType<HamonData> {

	// Phase 1 backbone abilities (already source-closed)
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_BEAT = ABILITY_TYPES.register(
			"hamon_beat", key -> new AbilityType<>(key, HamonOverdriveBeatAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> SUNLIGHT_YELLOW_OVERDRIVE = ABILITY_TYPES.register(
			"sunlight_yellow_overdrive", key -> new AbilityType<>(key, HamonSunlightYellowOverdriveAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> REBUFF_OVERDRIVE = ABILITY_TYPES.register(
			"rebuff_overdrive", key -> new AbilityType<>(key, HamonRebuffOverdriveAbility::new));

	// Slice 3: full legacy parity scaffold
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_BREATH = ABILITY_TYPES.register(
			"hamon_breath", key -> new AbilityType<>(key, HamonBreathAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_BUBBLE_BARRIER = ABILITY_TYPES.register(
			"bubble_barrier", key -> new AbilityType<>(key, HamonBubbleBarrierAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_BUBBLE_CUTTER = ABILITY_TYPES.register(
			"bubble_cutter", key -> new AbilityType<>(key, HamonBubbleCutterAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_BUBBLE_CUTTER_GLIDING = ABILITY_TYPES.register(
			"bubble_cutter_gliding", key -> new AbilityType<>(key, (type, id) -> new HamonBubbleCutterAbility(type, id, true)));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_BUBBLE_LAUNCHER = ABILITY_TYPES.register(
			"bubble_launcher", key -> new AbilityType<>(key, HamonBubbleLauncherAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_CUTTER = ABILITY_TYPES.register(
			"hamon_cutter", key -> new AbilityType<>(key, HamonCutterAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_DETECTOR = ABILITY_TYPES.register(
			"hamon_detector", key -> new AbilityType<>(key, HamonDetectorAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_HEALING = ABILITY_TYPES.register(
			"hamon_healing", key -> new AbilityType<>(key, HamonHealingAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_HYPNOSIS = ABILITY_TYPES.register(
			"hypnosis", key -> new AbilityType<>(key, HamonHypnosisAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_LIFE_MAGNETISM = ABILITY_TYPES.register(
			"life_magnetism", key -> new AbilityType<>(key, HamonLifeMagnetismAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_LIQUID_WALKING = ABILITY_TYPES.register(
			"liquid_walking", key -> new AbilityType<>(key, HamonLiquidWalkingAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_METAL_SILVER_OVERDRIVE = ABILITY_TYPES.register(
			"metal_silver_overdrive", key -> new AbilityType<>(key, HamonMetalSilverOverdriveAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_METAL_SILVER_OVERDRIVE_WEAPON = ABILITY_TYPES.register(
			"metal_silver_overdrive_weapon", key -> new AbilityType<>(key, HamonMetalSilverOverdriveWeaponAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_ORGANISM_INFUSION = ABILITY_TYPES.register(
			"organism_infusion", key -> new AbilityType<>(key, HamonOrganismInfusionAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_OVERDRIVE = ABILITY_TYPES.register(
			"hamon_overdrive", key -> new AbilityType<>(key, HamonOverdriveAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_OVERDRIVE_BARRAGE = ABILITY_TYPES.register(
			"overdrive_barrage", key -> new AbilityType<>(key, HamonOverdriveBarrageAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_PLANT_INFUSION = ABILITY_TYPES.register(
			"plant_infusion", key -> new AbilityType<>(key, HamonPlantInfusionAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_PLANT_ITEM_INFUSION = ABILITY_TYPES.register(
			"plant_item_infusion", key -> new AbilityType<>(key, HamonPlantItemInfusionAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_PROJECTILE_SHIELD = ABILITY_TYPES.register(
			"projectile_shield", key -> new AbilityType<>(key, HamonProjectileShieldAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_PROTECTION = ABILITY_TYPES.register(
			"hamon_protection", key -> new AbilityType<>(key, HamonProtectionAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_ROPE_TRAP = ABILITY_TYPES.register(
			"rope_trap", key -> new AbilityType<>(key, HamonRopeTrapAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_SCARLET_OVERDRIVE = ABILITY_TYPES.register(
			"scarlet_overdrive", key -> new AbilityType<>(key, HamonScarletOverdriveAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_SENDO_OVERDRIVE = ABILITY_TYPES.register(
			"sendo_overdrive", key -> new AbilityType<>(key, HamonSendoOverdriveAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_SENDO_WAVE_KICK = ABILITY_TYPES.register(
			"sendo_wave_kick", key -> new AbilityType<>(key, HamonSendoWaveKickAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_SHOCK = ABILITY_TYPES.register(
			"hamon_shock", key -> new AbilityType<>(key, HamonShockAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_SNAKE_MUFFLER = ABILITY_TYPES.register(
			"snake_muffler", key -> new AbilityType<>(key, HamonSnakeMufflerAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_SPEED_BOOST = ABILITY_TYPES.register(
			"hamon_speed_boost", key -> new AbilityType<>(key, HamonSpeedBoostAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE = ABILITY_TYPES.register(
			"sunlight_yellow_overdrive_barrage", key -> new AbilityType<>(key, HamonSunlightYellowOverdriveBarrageAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_TORNADO_OVERDRIVE = ABILITY_TYPES.register(
			"tornado_overdrive", key -> new AbilityType<>(key, HamonTornadoOverdriveAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_TURQUOISE_BLUE_OVERDRIVE = ABILITY_TYPES.register(
			"turquoise_blue_overdrive", key -> new AbilityType<>(key, HamonTurquoiseBlueOverdriveAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_WALL_CLIMBING = ABILITY_TYPES.register(
			"wall_climbing", key -> new AbilityType<>(key, HamonWallClimbingAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> HAMON_ZOOM_PUNCH = ABILITY_TYPES.register(
			"zoom_punch", key -> new AbilityType<>(key, HamonZoomPunchAbility::new));

	public static final DeferredHolder<PlayerPowerType<?>, HamonPowerType> HAMON = PLAYER_POWERS.register(
			"hamon", key -> new HamonPowerType(key, ModHamonSkills.addSkills(new MovesetBuilder())

					.addAbility("hamon_beat", HAMON_BEAT, HamonPowerType::combat)
					.addAbility("sunlight_yellow_overdrive", SUNLIGHT_YELLOW_OVERDRIVE, ability -> {
						combat(ability);
						hamonHoldToFire(ability, 10, true, 40, 10);
						hamonHeldWalkSpeed(ability, 0.0F);
						sunlightYellowOverdriveShouts(ability);
					})
					.addAbility("rebuff_overdrive", REBUFF_OVERDRIVE, HamonPowerType::combat)

					.addAbility("hamon_breath", HAMON_BREATH, ability -> {
						heldUtility(ability, 0F, 0.0F);
						hamonShout(ability, ModSoundEvents.BREATH_DEFAULT);
						hamonTechniqueShout(ability, "jonathan", ModSoundEvents.BREATH_JONATHAN);
						hamonTechniqueShout(ability, "zeppeli", ModSoundEvents.BREATH_ZEPPELI);
						hamonTechniqueShout(ability, "joseph", ModSoundEvents.BREATH_JOSEPH);
						hamonTechniqueShout(ability, "caesar", ModSoundEvents.BREATH_CAESAR);
						hamonTechniqueShout(ability, "lisa_lisa", ModSoundEvents.BREATH_LISA_LISA);
					})
					.addAbility("bubble_barrier", HAMON_BUBBLE_BARRIER, ability -> {
						heldCombat(ability, 50F, 0.3F);
						hamonHoldToFire(ability, 20, false, 20, 6);
						hamonShout(ability, ModSoundEvents.CAESAR_BUBBLE_BARRIER);
					})
					.addAbility("bubble_cutter", HAMON_BUBBLE_CUTTER, ability -> {
						combatRuntime(ability, 500F, 10, null);
						hamonShout(ability, ModSoundEvents.CAESAR_BUBBLE_CUTTER);
					})
					.addAbility("bubble_cutter_gliding", HAMON_BUBBLE_CUTTER_GLIDING, ability -> {
						combatRuntime(ability, 600F, 10, null);
						hamonShout(ability, ModSoundEvents.CAESAR_BUBBLE_CUTTER_GLIDING);
					})
					.addAbility("bubble_launcher", HAMON_BUBBLE_LAUNCHER, ability -> {
						heldUtility(ability, 50F, 0.3F);
						hamonShout(ability, ModSoundEvents.CAESAR_BUBBLE_LAUNCHER);
					})
					.addAbility("hamon_cutter", HAMON_CUTTER, ability -> {
						combatRuntime(ability, 400F, 0, null);
						hamonShout(ability, ModSoundEvents.ZEPPELI_HAMON_CUTTER);
					})
					.addAbility("hamon_detector", HAMON_DETECTOR, ability -> heldUtility(ability, 5F, 0.5F))
					.addAbility("hamon_healing", HAMON_HEALING, ability -> heldUtility(ability, 5F, 0.9999F))
					.addAbility("hypnosis", HAMON_HYPNOSIS, ability -> {
						heldUtility(ability, 15F, 1.0F);
						hamonHoldToFire(ability, 60, false, 60, 6);
					})
					.addAbility("life_magnetism", HAMON_LIFE_MAGNETISM, ability -> {
						utilityRuntime(ability, 200F, 0, HamonData.HamonStat.CONTROL);
						hamonTechniqueShout(ability, "zeppeli", ModSoundEvents.ZEPPELI_LIFE_MAGNETISM_OVERDRIVE);
					})
					.addAbility("liquid_walking", HAMON_LIQUID_WALKING, HamonPowerType::utility)
					.addAbility("metal_silver_overdrive", HAMON_METAL_SILVER_OVERDRIVE, ability -> {
						combat(ability);
						runtime(ability, 1000F, 0, null);
					})
					.addAbility("metal_silver_overdrive_weapon", HAMON_METAL_SILVER_OVERDRIVE_WEAPON, ability -> {
						combat(ability);
						runtime(ability, 750F, 0, null);
					})
					.addAbility("organism_infusion", HAMON_ORGANISM_INFUSION, HamonPowerType::utility)
					.addAbility("hamon_overdrive", HAMON_OVERDRIVE, ability -> {
						combat(ability);
						runtime(ability, 600F, 0, null);
					})
					.addAbility("overdrive_barrage", HAMON_OVERDRIVE_BARRAGE, ability -> {
						heldCombat(ability, 70F, 0.5F);
						hamonShout(ability, ModSoundEvents.JONATHAN_OVERDRIVE_BARRAGE);
					})
					.addAbility("plant_infusion", HAMON_PLANT_INFUSION, HamonPowerType::utility)
					.addAbility("plant_item_infusion", HAMON_PLANT_ITEM_INFUSION, HamonPowerType::utility)
					.addAbility("projectile_shield", HAMON_PROJECTILE_SHIELD, ability -> {
						heldUtility(ability, 15F, 0.3F);
						hamonTechniqueShout(ability, "joseph", ModSoundEvents.JOSEPH_BARRIER);
					})
					.addAbility("hamon_protection", HAMON_PROTECTION, HamonPowerType::utility)
					.addAbility("rope_trap", HAMON_ROPE_TRAP, HamonPowerType::utility)
					.addAbility("scarlet_overdrive", HAMON_SCARLET_OVERDRIVE, ability -> {
						combat(ability);
						hamonHoldToFire(ability, 8, true, 32, 5);
						hamonHeldWalkSpeed(ability, 0.0F);
						hamonShout(ability, ModSoundEvents.JONATHAN_SCARLET_OVERDRIVE);
					})
					.addAbility("sendo_overdrive", HAMON_SENDO_OVERDRIVE, ability -> {
						combat(ability);
						runtime(ability, 900F, 0, null);
						hamonHoldToFire(ability, 30, false, 30, 5);
						hamonTechniqueShout(ability, "jonathan", ModSoundEvents.JONATHAN_SENDO_OVERDRIVE);
					})
					.addAbility("sendo_wave_kick", HAMON_SENDO_WAVE_KICK, ability -> {
						combat(ability);
						runtime(ability, 1000F, 0, null);
						hamonShout(ability, ModSoundEvents.ZEPPELI_SENDO_WAVE_KICK);
					})
					.addAbility("hamon_shock", HAMON_SHOCK, HamonPowerType::utility)
					.addAbility("snake_muffler", HAMON_SNAKE_MUFFLER, HamonPowerType::utility)
					.addAbility("hamon_speed_boost", HAMON_SPEED_BOOST, ability -> utilityRuntime(ability, 600F, 0, null))
					.addAbility("sunlight_yellow_overdrive_barrage", SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE, ability -> {
						combat(ability);
						hamonHoldToFire(ability, 60, false, 60, 80);
						hamonHeldWalkSpeed(ability, 0.0F);
						hamonShout(ability, ModSoundEvents.JONATHAN_SYO_BARRAGE_START);
					})
					.addAbility("tornado_overdrive", HAMON_TORNADO_OVERDRIVE, ability -> {
						heldCombat(ability, 75F, 1.0F);
						hamonShout(ability, ModSoundEvents.ZEPPELI_TORNADO_OVERDRIVE);
					})
					.addAbility("turquoise_blue_overdrive", HAMON_TURQUOISE_BLUE_OVERDRIVE, ability -> {
						combat(ability);
						runtime(ability, 1000F, 10, null);
					})
					.addAbility("wall_climbing", HAMON_WALL_CLIMBING, ability -> heldUtility(ability, 10F, 1.0F))
					.addAbility("zoom_punch", HAMON_ZOOM_PUNCH, ability -> {
						combat(ability);
						runtime(ability, 450F, 14, null);
						hamonTechniqueShout(ability, "jonathan", ModSoundEvents.JONATHAN_ZOOM_PUNCH);
						hamonTechniqueShout(ability, "zeppeli", ModSoundEvents.ZEPPELI_ZOOM_PUNCH);
						hamonTechniqueShout(ability, "joseph", ModSoundEvents.JOSEPH_ZOOM_PUNCH);
					})

					.makeControlScheme("default")
						.makeMovesetGroup("moveset_group.hamon.combat", new InputUseVanillaMapping("jojo_ripples.key.non_stand_mode"))
							.bind("hamon_overdrive", InputMethod.CLICK, InputKey.LMB)
							.bind("sunlight_yellow_overdrive", InputMethod.HOLD, InputKey.LMB)
							.bind("hamon_beat", InputMethod.CLICK, InputKey.LMB.withModifier(InputKey.Modifier.SHIFT))
							.makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
							.addToHotbar("hamon_overdrive", 0, InputMethod.CLICK)
							.addHotbarSlotVariation("hamon_beat", "hamon_overdrive", InputKey.Modifier.SHIFT, InputMethod.CLICK)
							.addToHotbar("sendo_overdrive", 0, InputMethod.CLICK)
							.addToHotbar("turquoise_blue_overdrive", 0, InputMethod.CLICK)
							.addToHotbar("sunlight_yellow_overdrive", 0, InputMethod.HOLD)
							.addToHotbar("zoom_punch", 0, InputMethod.CLICK)
							.addToHotbar("plant_infusion", 0, InputMethod.CLICK)
						.makeMovesetGroup("moveset_group.hamon.utility", new InputUseVanillaMapping("jojo_ripples.key.non_stand_mode"))
							.bind("hamon_breath", InputMethod.HOLD, new InputUseVanillaMapping("jojo_ripples.key.hamon_breath"))
							.bind("hamon_healing", InputMethod.HOLD, InputKey.RMB)
							.bind("hamon_protection", InputMethod.CLICK, InputKey.RMB.withModifier(InputKey.Modifier.SHIFT))
							.makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
							.addToHotbar("hamon_breath", 0, InputMethod.HOLD)
							.addToHotbar("hamon_healing", 0, InputMethod.HOLD)
							.addToHotbar("hamon_speed_boost", 0, InputMethod.CLICK)
							.addToHotbar("wall_climbing", 0, InputMethod.HOLD)
							.addToHotbar("life_magnetism", 0, InputMethod.CLICK)
							.addToHotbar("projectile_shield", 0, InputMethod.HOLD)
							.addToHotbar("hamon_protection", 0, InputMethod.CLICK)
							.addToHotbar("hamon_detector", 0, InputMethod.HOLD)
							.addToHotbar("hypnosis", 0, InputMethod.HOLD)
							.addToHotbar("hamon_shock", 0, InputMethod.CLICK)
							.addToHotbar("organism_infusion", 0, InputMethod.CLICK)
						.makeMovesetGroup("moveset_group.hamon.technique", new InputUseVanillaMapping("jojo_ripples.key.non_stand_mode"))
							.bind("rebuff_overdrive", InputMethod.CLICK, InputKey.RMB)
							.makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
							.addToHotbar("scarlet_overdrive", 0, InputMethod.HOLD)
							.addToHotbar("metal_silver_overdrive", 0, InputMethod.CLICK)
							.addHotbarSlotVariation("hamon_beat", "metal_silver_overdrive", InputKey.Modifier.SHIFT, InputMethod.CLICK)
							.addToHotbar("metal_silver_overdrive_weapon", 0, InputMethod.CLICK)
							.addToHotbar("overdrive_barrage", 0, InputMethod.HOLD)
							.addHotbarSlotVariation("sunlight_yellow_overdrive_barrage", "overdrive_barrage", InputKey.Modifier.SHIFT, InputMethod.HOLD)
							.addToHotbar("hamon_cutter", 0, InputMethod.CLICK)
							.addToHotbar("sendo_wave_kick", 0, InputMethod.CLICK)
							.addToHotbar("tornado_overdrive", 0, InputMethod.HOLD)
							.addToHotbar("rebuff_overdrive", 0, InputMethod.CLICK)
							.addToHotbar("bubble_launcher", 0, InputMethod.HOLD)
							.addToHotbar("bubble_barrier", 0, InputMethod.HOLD)
							.addToHotbar("bubble_cutter", 0, InputMethod.CLICK)
							.addHotbarSlotVariation("bubble_cutter_gliding", "bubble_cutter", InputKey.Modifier.SHIFT, InputMethod.CLICK)
					.finalizeControlScheme()
					));


	protected HamonPowerType(ResourceLocation registryKey, MovesetBuilder abilitySet) {
		super(registryKey, abilitySet);
	}

	@Override
	public HamonData newDataInstance() {
		return new HamonData();
	}

	@Override
	public boolean isLeapUnlocked(PlayerPower power) {
		return power != null && power.getCurTypeData(HAMON)
				.map(hamon -> hamon.isSkillLearned(ModHamonSkills.JUMP.get()))
				.orElse(false);
	}

	@Override
	public float getLeapStrength(PlayerPower power) {
		return power != null ? power.getCurTypeData(HAMON)
				.map(hamon -> hamon.isSkillLearned(ModHamonSkills.AFTERIMAGES.get()) ? 1.5F : 1.4F)
				.orElse(0.0F) : 0.0F;
	}

	@Override
	public int getLeapCooldownPeriod(PlayerPower power) {
		return 20;
	}

	@Override
	public float getLeapEnergyCost(PlayerPower power) {
		return 250.0F;
	}

	@Override
	public boolean hasLeapEnergy(PlayerPower power, float energyCost) {
		return power != null && power.getCurTypeData(HAMON)
				.map(hamon -> hamon.hasEnergy(energyCost, power.getUser()))
				.orElse(false);
	}

	@Override
	public boolean consumeLeapEnergy(PlayerPower power, float energyCost) {
		return power != null && power.getCurTypeData(HAMON)
				.map(hamon -> hamon.consumeEnergy(energyCost, power.getUser()))
				.orElse(false);
	}

	@Override
	public void onLeap(PlayerPower power) {
		if (power != null) {
			power.getCurTypeData(HAMON).ifPresent(hamon -> {
				hamon.hamonPointsFromAction(HamonData.HamonStat.CONTROL, getLeapEnergyCost(power));
				hamon.syncOnUpdate(power.getUser());
			});
		}
	}

	@Override
	public float getStandStaminaRegenFactor(PlayerPower power, StandPower standPower) {
		return power.getCurTypeData(HAMON)
				.map(hamon -> 1.0F + hamon.getBreathingLevel() * 0.01F)
				.orElse(1.0F);
	}

	private static void combat(Ability ability) {
		ability.usageGroup = AbilityUsageGroup.COMBAT;
	}

	private static void utility(Ability ability) {
		ability.usageGroup = AbilityUsageGroup.UTILITY;
	}

	private static void combatRuntime(Ability ability, float energyCost, int cooldownTicks, HamonData.HamonStat stat) {
		combat(ability);
		runtime(ability, energyCost, cooldownTicks, stat);
	}

	private static void utilityRuntime(Ability ability, float energyCost, int cooldownTicks, HamonData.HamonStat stat) {
		utility(ability);
		runtime(ability, energyCost, cooldownTicks, stat);
	}

	private static void heldCombat(Ability ability, float heldTickEnergyCost, float heldWalkSpeed) {
		combat(ability);
		heldRuntime(ability, heldTickEnergyCost, heldWalkSpeed);
	}

	private static void heldUtility(Ability ability, float heldTickEnergyCost, float heldWalkSpeed) {
		utility(ability);
		heldRuntime(ability, heldTickEnergyCost, heldWalkSpeed);
	}

	private static void runtime(Ability ability, float energyCost, int cooldownTicks, HamonData.HamonStat stat) {
		if (ability instanceof HamonActionRuntimeAbility hamonAbility) {
			hamonAbility.hamonRuntime(energyCost, cooldownTicks, stat);
		}
	}

	private static void heldRuntime(Ability ability, float heldTickEnergyCost, float heldWalkSpeed) {
		if (ability instanceof HamonActionRuntimeAbility hamonAbility) {
			hamonAbility.hamonHeldRuntime(heldTickEnergyCost, heldWalkSpeed);
		}
	}

	private static void sunlightYellowOverdriveShouts(Ability ability) {
		hamonTechniqueShout(ability, "jonathan", ModSoundEvents.JONATHAN_SUNLIGHT_YELLOW_OVERDRIVE);
		hamonTechniqueShout(ability, "zeppeli", ModSoundEvents.ZEPPELI_SUNLIGHT_YELLOW_OVERDRIVE);
		hamonTechniqueShout(ability, "joseph", ModSoundEvents.JOSEPH_SUNLIGHT_YELLOW_OVERDRIVE);
		hamonTechniqueShout(ability, "caesar", ModSoundEvents.CAESAR_SUNLIGHT_YELLOW_OVERDRIVE);
	}

	private static void hamonShout(Ability ability, Supplier<? extends SoundEvent> shout) {
		if (ability instanceof HamonActionRuntimeAbility hamonAbility) {
			hamonAbility.hamonShout(shout);
		}
	}

	private static void hamonTechniqueShout(Ability ability, String techniqueName, Supplier<? extends SoundEvent> shout) {
		if (ability instanceof HamonActionRuntimeAbility hamonAbility) {
			hamonAbility.hamonTechniqueShout(techniqueName, shout);
		}
	}

	private static void hamonHoldToFire(Ability ability, int ticksToFire, boolean continueHolding, int windupTicks, float performTicks) {
		if (ability instanceof HamonActionRuntimeAbility hamonAbility) {
			hamonAbility.hamonHoldToFire(ticksToFire, continueHolding, windupTicks, performTicks);
		}
	}

	private static void hamonHeldWalkSpeed(Ability ability, float heldWalkSpeed) {
		if (ability instanceof HamonActionRuntimeAbility hamonAbility) {
			hamonAbility.hamonHeldWalkSpeed(heldWalkSpeed);
		}
	}

}
