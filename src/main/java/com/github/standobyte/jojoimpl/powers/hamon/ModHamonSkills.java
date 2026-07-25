package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.unlockableskill.UnlockableSkill.DevStatus;
import com.github.standobyte.jojoimpl.powers.hamon.HamonSkillDefinition.HamonSkillBranch;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModHamonSkills {
	public static final DeferredRegister<HamonSkill> HAMON_SKILLS = DeferredRegister.create(JojoRegistries.HAMON_SKILLS_REG, JojoMod.MOD_ID);
	public static final DeferredRegister<HamonTechnique> HAMON_CHARACTER_TECHNIQUES = DeferredRegister.create(JojoRegistries.HAMON_TECHNIQUES_REG, JojoMod.MOD_ID);

	private static final HamonSkillBranch BRANCH_OVERDRIVE = HamonSkillBranch.OVERDRIVE;
	private static final HamonSkillBranch BRANCH_INFUSION = HamonSkillBranch.INFUSION;
	private static final HamonSkillBranch BRANCH_FLEXIBILITY = HamonSkillBranch.FLEXIBILITY;
	private static final HamonSkillBranch BRANCH_HEALING = HamonSkillBranch.HEALING;
	private static final HamonSkillBranch BRANCH_ATTRACTANT_REPELLENT = HamonSkillBranch.ATTRACTANT_REPELLENT;
	private static final HamonSkillBranch BRANCH_BODY_MANIPULATION = HamonSkillBranch.BODY_MANIPULATION;
	private static final HamonSkillBranch BRANCH_CHARACTER_TECHNIQUE = HamonSkillBranch.CHARACTER_TECHNIQUE;

	public static final HamonSkillDefinition OVERDRIVE_DEF = skill("overdrive", BRANCH_OVERDRIVE, true, abilities("hamon_overdrive", "hamon_beat"), none());
	public static final HamonSkillDefinition SENDO_OVERDRIVE_DEF = skill("sendo_overdrive", BRANCH_OVERDRIVE, false, abilities("sendo_overdrive"), requires("overdrive"));
	public static final HamonSkillDefinition TURQUOISE_BLUE_OVERDRIVE_DEF = skill("turquoise_blue_overdrive", BRANCH_OVERDRIVE, false, abilities("turquoise_blue_overdrive"), requires("overdrive"));
	public static final HamonSkillDefinition SUNLIGHT_YELLOW_OVERDRIVE_DEF = skill("sunlight_yellow_overdrive", BRANCH_OVERDRIVE, false, abilities("sunlight_yellow_overdrive"), requires("sendo_overdrive", "turquoise_blue_overdrive"));
	public static final HamonSkillDefinition THROWABLES_INFUSION_DEF = skill("throwables_infusion", BRANCH_INFUSION, false, none(), none());
	public static final HamonSkillDefinition PLANT_BLOCK_INFUSION_DEF = skill("plant_infusion", BRANCH_INFUSION, false, abilities("plant_infusion"), requires("throwables_infusion"));
	public static final HamonSkillDefinition PLANT_ITEM_INFUSION_DEF = skill("plant_item_infusion", BRANCH_INFUSION, false, abilities("plant_item_infusion"), requires("throwables_infusion"));
	public static final HamonSkillDefinition ANIMAL_INFUSION_DEF = skill("animal_infusion", BRANCH_INFUSION, false, abilities("organism_infusion"), requires("plant_infusion"));
	public static final HamonSkillDefinition ARROW_INFUSION_DEF = skill("arrow_infusion", BRANCH_INFUSION, false, none(), requires("plant_item_infusion"));
	public static final HamonSkillDefinition ZOOM_PUNCH_DEF = skill("zoom_punch", BRANCH_FLEXIBILITY, false, abilities("zoom_punch"), none());
	public static final HamonSkillDefinition JUMP_DEF = skill("jump", BRANCH_FLEXIBILITY, false, none(), requires("zoom_punch"));
	public static final HamonSkillDefinition SPEED_BOOST_DEF = skill("speed_boost", BRANCH_FLEXIBILITY, false, abilities("hamon_speed_boost"), requires("zoom_punch"));
	public static final HamonSkillDefinition AFTERIMAGES_DEF = skill("afterimages", BRANCH_FLEXIBILITY, false, DevStatus.IMPLEMENTED, none(), requires("jump", "speed_boost"));
	public static final HamonSkillDefinition HEALING_DEF = skill("healing", BRANCH_HEALING, true, abilities("hamon_healing"), none());
	public static final HamonSkillDefinition PLANTS_GROWTH_DEF = skill("plants_growth", BRANCH_HEALING, false, none(), requires("healing"));
	public static final HamonSkillDefinition EXPEL_VENOM_DEF = skill("expel_venom", BRANCH_HEALING, false, none(), requires("healing"));
	public static final HamonSkillDefinition HEALING_TOUCH_DEF = skill("healing_touch", BRANCH_HEALING, false, none(), requires("plants_growth", "expel_venom"));
	public static final HamonSkillDefinition WALL_CLIMBING_DEF = skill("wall_climbing", BRANCH_ATTRACTANT_REPELLENT, false, abilities("wall_climbing"), none());
	public static final HamonSkillDefinition LIQUID_WALKING_DEF = skill("liquid_walking", BRANCH_ATTRACTANT_REPELLENT, false, abilities("liquid_walking"), none());
	public static final HamonSkillDefinition LIFE_MAGNETISM_DEF = skill("life_magnetism", BRANCH_ATTRACTANT_REPELLENT, false, abilities("life_magnetism"), requires("wall_climbing"));
	public static final HamonSkillDefinition PROJECTILE_SHIELD_DEF = skill("projectile_shield", BRANCH_ATTRACTANT_REPELLENT, false, abilities("projectile_shield"), requires("liquid_walking"));
	public static final HamonSkillDefinition PROTECTION_DEF = skill("protection", BRANCH_ATTRACTANT_REPELLENT, false, abilities("hamon_protection"), requires("life_magnetism", "projectile_shield"));
	public static final HamonSkillDefinition DETECTOR_DEF = skill("detector", BRANCH_BODY_MANIPULATION, false, abilities("hamon_detector"), none());
	public static final HamonSkillDefinition HYPNOSIS_DEF = skill("hypnosis", BRANCH_BODY_MANIPULATION, false, abilities("hypnosis"), requires("detector"));
	public static final HamonSkillDefinition HAMON_SHOCK_DEF = skill("hamon_shock", BRANCH_BODY_MANIPULATION, false, abilities("hamon_shock"), requires("detector"));
	public static final HamonSkillDefinition HAMON_SPREAD_DEF = skill("hamon_spread", BRANCH_BODY_MANIPULATION, false, none(), requires("hypnosis", "hamon_shock"));
	public static final HamonSkillDefinition NATURAL_TALENT_DEF = skill("natural_talent", BRANCH_CHARACTER_TECHNIQUE, false, none(), none());
	public static final HamonSkillDefinition METAL_SILVER_OVERDRIVE_DEF = skill("metal_silver_overdrive", BRANCH_CHARACTER_TECHNIQUE, false, abilities("metal_silver_overdrive", "metal_silver_overdrive_weapon"), requires("sendo_overdrive", "turquoise_blue_overdrive"));
	public static final HamonSkillDefinition SCARLET_OVERDRIVE_DEF = skill("scarlet_overdrive", BRANCH_CHARACTER_TECHNIQUE, false, abilities("scarlet_overdrive"), requires("sunlight_yellow_overdrive"));
	public static final HamonSkillDefinition OVERDRIVE_BARRAGE_DEF = skill("overdrive_barrage", BRANCH_CHARACTER_TECHNIQUE, false, abilities("overdrive_barrage"), requires("zoom_punch"));
	public static final HamonSkillDefinition SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE_DEF = skill("syo_barrage", BRANCH_CHARACTER_TECHNIQUE, false, abilities("sunlight_yellow_overdrive_barrage"), requires("overdrive_barrage", "sunlight_yellow_overdrive"));
	public static final HamonSkillDefinition DEEP_PASS_DEF = skill("deep_pass", BRANCH_CHARACTER_TECHNIQUE, false, none(), none());
	public static final HamonSkillDefinition HAMON_CUTTER_DEF = skill("hamon_cutter", BRANCH_CHARACTER_TECHNIQUE, false, abilities("hamon_cutter"), requires("throwables_infusion"));
	public static final HamonSkillDefinition SENDO_WAVE_KICK_DEF = skill("sendo_wave_kick", BRANCH_CHARACTER_TECHNIQUE, false, abilities("sendo_wave_kick"), requires("jump"));
	public static final HamonSkillDefinition TORNADO_OVERDRIVE_DEF = skill("tornado_overdrive", BRANCH_CHARACTER_TECHNIQUE, false, abilities("tornado_overdrive"), requires("jump", "speed_boost"));
	public static final HamonSkillDefinition ROPE_TRAP_DEF = skill("rope_trap", BRANCH_CHARACTER_TECHNIQUE, false, abilities("rope_trap"), requires("sendo_overdrive"));
	public static final HamonSkillDefinition CLACKER_VOLLEY_DEF = skill("clacker_volley", BRANCH_CHARACTER_TECHNIQUE, false, none(), requires("throwables_infusion"));
	public static final HamonSkillDefinition REBUFF_OVERDRIVE_DEF = skill("rebuff_overdrive", BRANCH_CHARACTER_TECHNIQUE, false, abilities("rebuff_overdrive"), requires("protection"));
	public static final HamonSkillDefinition CHEAT_DEATH_DEF = skill("cheat_death", BRANCH_CHARACTER_TECHNIQUE, false, none(), none());
	public static final HamonSkillDefinition CRIMSON_BUBBLE_DEF = skill("crimson_bubble", BRANCH_CHARACTER_TECHNIQUE, false, none(), none());
	public static final HamonSkillDefinition BUBBLE_LAUNCHER_DEF = skill("bubble_launcher", BRANCH_CHARACTER_TECHNIQUE, false, abilities("bubble_launcher"), requires("throwables_infusion"));
	public static final HamonSkillDefinition BUBBLE_CUTTER_DEF = skill("bubble_cutter", BRANCH_CHARACTER_TECHNIQUE, false, abilities("bubble_cutter", "bubble_cutter_gliding"), requires("bubble_launcher", "arrow_infusion"));
	public static final HamonSkillDefinition BUBBLE_BARRIER_DEF = skill("bubble_barrier", BRANCH_CHARACTER_TECHNIQUE, false, abilities("bubble_barrier"), requires("bubble_launcher", "hamon_shock"));
	public static final HamonSkillDefinition AJA_STONE_KEEPER_DEF = skill("aja_stone_keeper", BRANCH_CHARACTER_TECHNIQUE, false, none(), none());
	public static final HamonSkillDefinition SATIPOROJA_SCARF_DEF = skill("satiporoja_scarf", BRANCH_CHARACTER_TECHNIQUE, false, none(), requires("animal_infusion"));
	public static final HamonSkillDefinition SNAKE_MUFFLER_DEF = skill("snake_muffler", BRANCH_CHARACTER_TECHNIQUE, false, abilities("snake_muffler"), requires("satiporoja_scarf", "detector", "jump"));

	public static final List<HamonSkillDefinition> SKILL_DEFINITIONS = List.of(
			OVERDRIVE_DEF, SENDO_OVERDRIVE_DEF, TURQUOISE_BLUE_OVERDRIVE_DEF, SUNLIGHT_YELLOW_OVERDRIVE_DEF,
			THROWABLES_INFUSION_DEF, PLANT_BLOCK_INFUSION_DEF, PLANT_ITEM_INFUSION_DEF, ANIMAL_INFUSION_DEF, ARROW_INFUSION_DEF,
			ZOOM_PUNCH_DEF, JUMP_DEF, SPEED_BOOST_DEF, AFTERIMAGES_DEF,
			HEALING_DEF, PLANTS_GROWTH_DEF, EXPEL_VENOM_DEF, HEALING_TOUCH_DEF,
			WALL_CLIMBING_DEF, LIQUID_WALKING_DEF, LIFE_MAGNETISM_DEF, PROJECTILE_SHIELD_DEF, PROTECTION_DEF,
			DETECTOR_DEF, HYPNOSIS_DEF, HAMON_SHOCK_DEF, HAMON_SPREAD_DEF,
			NATURAL_TALENT_DEF, METAL_SILVER_OVERDRIVE_DEF, SCARLET_OVERDRIVE_DEF, OVERDRIVE_BARRAGE_DEF, SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE_DEF,
			DEEP_PASS_DEF, HAMON_CUTTER_DEF, SENDO_WAVE_KICK_DEF, TORNADO_OVERDRIVE_DEF,
			ROPE_TRAP_DEF, CLACKER_VOLLEY_DEF, REBUFF_OVERDRIVE_DEF, CHEAT_DEATH_DEF,
			CRIMSON_BUBBLE_DEF, BUBBLE_LAUNCHER_DEF, BUBBLE_CUTTER_DEF, BUBBLE_BARRIER_DEF,
			AJA_STONE_KEEPER_DEF, SATIPOROJA_SCARF_DEF, SNAKE_MUFFLER_DEF);

	private static final Map<String, HamonSkillDefinition> DEFINITIONS_BY_NAME = SKILL_DEFINITIONS.stream()
			.collect(Collectors.toUnmodifiableMap(HamonSkillDefinition::name, Function.identity()));

	public static final HamonTechniqueDefinition JONATHAN_TECHNIQUE_DEF = technique("jonathan",
			skills("metal_silver_overdrive", "scarlet_overdrive", "overdrive_barrage"), perks("natural_talent"),
			Map.of(BRANCH_OVERDRIVE, 0.2F), () -> ModSoundEvents.HAMON_PICK_JONATHAN);
	public static final HamonTechniqueDefinition ZEPPELI_TECHNIQUE_DEF = technique("zeppeli",
			skills("hamon_cutter", "sendo_wave_kick", "tornado_overdrive"), perks("deep_pass"),
			Map.of(BRANCH_FLEXIBILITY, 0.2F), () -> ModSoundEvents.HAMON_PICK_ZEPPELI);
	public static final HamonTechniqueDefinition JOSEPH_TECHNIQUE_DEF = technique("joseph",
			skills("rope_trap", "clacker_volley", "rebuff_overdrive"), none(),
			Map.of(BRANCH_OVERDRIVE, 0.1F, BRANCH_INFUSION, 0.1F, BRANCH_ATTRACTANT_REPELLENT, 0.1F), () -> ModSoundEvents.HAMON_PICK_JOSEPH);
	public static final HamonTechniqueDefinition CAESAR_TECHNIQUE_DEF = technique("caesar",
			skills("bubble_launcher", "bubble_cutter", "bubble_barrier"), perks("crimson_bubble"),
			Map.of(BRANCH_INFUSION, 0.2F), () -> ModSoundEvents.HAMON_PICK_CAESAR);
	public static final HamonTechniqueDefinition LISA_LISA_TECHNIQUE_DEF = technique("lisa_lisa",
			skills("aja_stone_keeper", "satiporoja_scarf", "snake_muffler"), none(),
			Map.of(BRANCH_INFUSION, 0.1F, BRANCH_FLEXIBILITY, 0.1F, BRANCH_BODY_MANIPULATION, 0.1F), () -> ModSoundEvents.HAMON_PICK_LISA_LISA);

	public static final List<HamonTechniqueDefinition> TECHNIQUE_DEFINITIONS = List.of(
			JONATHAN_TECHNIQUE_DEF, ZEPPELI_TECHNIQUE_DEF, JOSEPH_TECHNIQUE_DEF, CAESAR_TECHNIQUE_DEF, LISA_LISA_TECHNIQUE_DEF);

	private static final Map<String, HamonTechniqueDefinition> TECHNIQUE_DEFINITIONS_BY_NAME = TECHNIQUE_DEFINITIONS.stream()
			.collect(Collectors.toUnmodifiableMap(HamonTechniqueDefinition::name, Function.identity()));

	public static final DeferredHolder<HamonSkill, HamonSkill> OVERDRIVE = HAMON_SKILLS.register("overdrive", key -> new HamonSkill(key, OVERDRIVE_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> SENDO_OVERDRIVE = HAMON_SKILLS.register("sendo_overdrive", key -> new HamonSkill(key, SENDO_OVERDRIVE_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> TURQUOISE_BLUE_OVERDRIVE = HAMON_SKILLS.register("turquoise_blue_overdrive", key -> new HamonSkill(key, TURQUOISE_BLUE_OVERDRIVE_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> SUNLIGHT_YELLOW_OVERDRIVE = HAMON_SKILLS.register("sunlight_yellow_overdrive", key -> new HamonSkill(key, SUNLIGHT_YELLOW_OVERDRIVE_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> THROWABLES_INFUSION = HAMON_SKILLS.register("throwables_infusion", key -> new HamonSkill(key, THROWABLES_INFUSION_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> PLANT_BLOCK_INFUSION = HAMON_SKILLS.register("plant_infusion", key -> new HamonSkill(key, PLANT_BLOCK_INFUSION_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> PLANT_ITEM_INFUSION = HAMON_SKILLS.register("plant_item_infusion", key -> new HamonSkill(key, PLANT_ITEM_INFUSION_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> ANIMAL_INFUSION = HAMON_SKILLS.register("animal_infusion", key -> new HamonSkill(key, ANIMAL_INFUSION_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> ARROW_INFUSION = HAMON_SKILLS.register("arrow_infusion", key -> new HamonSkill(key, ARROW_INFUSION_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> ZOOM_PUNCH = HAMON_SKILLS.register("zoom_punch", key -> new HamonSkill(key, ZOOM_PUNCH_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> JUMP = HAMON_SKILLS.register("jump", key -> new HamonSkill(key, JUMP_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> SPEED_BOOST = HAMON_SKILLS.register("speed_boost", key -> new HamonSkill(key, SPEED_BOOST_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> AFTERIMAGES = HAMON_SKILLS.register("afterimages", key -> new HamonSkill(key, AFTERIMAGES_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> HEALING = HAMON_SKILLS.register("healing", key -> new HamonSkill(key, HEALING_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> PLANTS_GROWTH = HAMON_SKILLS.register("plants_growth", key -> new HamonSkill(key, PLANTS_GROWTH_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> EXPEL_VENOM = HAMON_SKILLS.register("expel_venom", key -> new HamonSkill(key, EXPEL_VENOM_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> HEALING_TOUCH = HAMON_SKILLS.register("healing_touch", key -> new HamonSkill(key, HEALING_TOUCH_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> WALL_CLIMBING = HAMON_SKILLS.register("wall_climbing", key -> new HamonSkill(key, WALL_CLIMBING_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> LIQUID_WALKING = HAMON_SKILLS.register("liquid_walking", key -> new HamonSkill(key, LIQUID_WALKING_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> LIFE_MAGNETISM = HAMON_SKILLS.register("life_magnetism", key -> new HamonSkill(key, LIFE_MAGNETISM_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> PROJECTILE_SHIELD = HAMON_SKILLS.register("projectile_shield", key -> new HamonSkill(key, PROJECTILE_SHIELD_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> PROTECTION = HAMON_SKILLS.register("protection", key -> new HamonSkill(key, PROTECTION_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> DETECTOR = HAMON_SKILLS.register("detector", key -> new HamonSkill(key, DETECTOR_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> HYPNOSIS = HAMON_SKILLS.register("hypnosis", key -> new HamonSkill(key, HYPNOSIS_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> HAMON_SHOCK = HAMON_SKILLS.register("hamon_shock", key -> new HamonSkill(key, HAMON_SHOCK_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> HAMON_SPREAD = HAMON_SKILLS.register("hamon_spread", key -> new HamonSkill(key, HAMON_SPREAD_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> NATURAL_TALENT = HAMON_SKILLS.register("natural_talent", key -> new HamonSkill(key, NATURAL_TALENT_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> METAL_SILVER_OVERDRIVE = HAMON_SKILLS.register("metal_silver_overdrive", key -> new HamonSkill(key, METAL_SILVER_OVERDRIVE_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> SCARLET_OVERDRIVE = HAMON_SKILLS.register("scarlet_overdrive", key -> new HamonSkill(key, SCARLET_OVERDRIVE_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> OVERDRIVE_BARRAGE = HAMON_SKILLS.register("overdrive_barrage", key -> new HamonSkill(key, OVERDRIVE_BARRAGE_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE = HAMON_SKILLS.register("syo_barrage", key -> new HamonSkill(key, SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> DEEP_PASS = HAMON_SKILLS.register("deep_pass", key -> new HamonSkill(key, DEEP_PASS_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> HAMON_CUTTER = HAMON_SKILLS.register("hamon_cutter", key -> new HamonSkill(key, HAMON_CUTTER_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> SENDO_WAVE_KICK = HAMON_SKILLS.register("sendo_wave_kick", key -> new HamonSkill(key, SENDO_WAVE_KICK_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> TORNADO_OVERDRIVE = HAMON_SKILLS.register("tornado_overdrive", key -> new HamonSkill(key, TORNADO_OVERDRIVE_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> ROPE_TRAP = HAMON_SKILLS.register("rope_trap", key -> new HamonSkill(key, ROPE_TRAP_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> CLACKER_VOLLEY = HAMON_SKILLS.register("clacker_volley", key -> new HamonSkill(key, CLACKER_VOLLEY_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> REBUFF_OVERDRIVE = HAMON_SKILLS.register("rebuff_overdrive", key -> new HamonSkill(key, REBUFF_OVERDRIVE_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> CHEAT_DEATH = HAMON_SKILLS.register("cheat_death", key -> new HamonSkill(key, CHEAT_DEATH_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> CRIMSON_BUBBLE = HAMON_SKILLS.register("crimson_bubble", key -> new HamonSkill(key, CRIMSON_BUBBLE_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> BUBBLE_LAUNCHER = HAMON_SKILLS.register("bubble_launcher", key -> new HamonSkill(key, BUBBLE_LAUNCHER_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> BUBBLE_CUTTER = HAMON_SKILLS.register("bubble_cutter", key -> new HamonSkill(key, BUBBLE_CUTTER_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> BUBBLE_BARRIER = HAMON_SKILLS.register("bubble_barrier", key -> new HamonSkill(key, BUBBLE_BARRIER_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> AJA_STONE_KEEPER = HAMON_SKILLS.register("aja_stone_keeper", key -> new HamonSkill(key, AJA_STONE_KEEPER_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> SATIPOROJA_SCARF = HAMON_SKILLS.register("satiporoja_scarf", key -> new HamonSkill(key, SATIPOROJA_SCARF_DEF));
	public static final DeferredHolder<HamonSkill, HamonSkill> SNAKE_MUFFLER = HAMON_SKILLS.register("snake_muffler", key -> new HamonSkill(key, SNAKE_MUFFLER_DEF));

	public static final DeferredHolder<HamonTechnique, HamonTechnique> CHARACTER_JONATHAN = HAMON_CHARACTER_TECHNIQUES.register("jonathan", key -> new HamonTechnique(key, JONATHAN_TECHNIQUE_DEF));
	public static final DeferredHolder<HamonTechnique, HamonTechnique> CHARACTER_ZEPPELI = HAMON_CHARACTER_TECHNIQUES.register("zeppeli", key -> new HamonTechnique(key, ZEPPELI_TECHNIQUE_DEF));
	public static final DeferredHolder<HamonTechnique, HamonTechnique> CHARACTER_JOSEPH = HAMON_CHARACTER_TECHNIQUES.register("joseph", key -> new HamonTechnique(key, JOSEPH_TECHNIQUE_DEF));
	public static final DeferredHolder<HamonTechnique, HamonTechnique> CHARACTER_CAESAR = HAMON_CHARACTER_TECHNIQUES.register("caesar", key -> new HamonTechnique(key, CAESAR_TECHNIQUE_DEF));
	public static final DeferredHolder<HamonTechnique, HamonTechnique> CHARACTER_LISA_LISA = HAMON_CHARACTER_TECHNIQUES.register("lisa_lisa", key -> new HamonTechnique(key, LISA_LISA_TECHNIQUE_DEF));

	public static HamonSkillDefinition definitionFor(String name) {
		return DEFINITIONS_BY_NAME.get(name);
	}

	public static HamonTechniqueDefinition techniqueDefinitionFor(String name) {
		return TECHNIQUE_DEFINITIONS_BY_NAME.get(name);
	}

	public static boolean isTechniqueSkill(String skillName) {
		return TECHNIQUE_DEFINITIONS.stream().anyMatch(technique -> technique.isTechniqueSkill(skillName));
	}

	public static HamonSkill skillByName(String name) {
		for (DeferredHolder<HamonSkill, ? extends HamonSkill> skill : HAMON_SKILLS.getEntries()) {
			if (skill.getId().getPath().equals(name)) {
				return skill.get();
			}
		}
		return null;
	}

	public static HamonTechnique techniqueByName(String name) {
		for (DeferredHolder<HamonTechnique, ? extends HamonTechnique> technique : HAMON_CHARACTER_TECHNIQUES.getEntries()) {
			if (technique.getId().getPath().equals(name)) {
				return technique.get();
			}
		}
		return null;
	}

	public static MovesetBuilder addSkills(MovesetBuilder builder) {
		for (HamonSkillDefinition definition : SKILL_DEFINITIONS) {
			builder.addSkill(HamonUnlockableSkill.fromDefinition(definition));
		}
		return builder;
	}

	private static HamonSkillDefinition skill(String name, HamonSkillBranch branch, boolean startingSkill, List<String> unlocksAbilities, List<String> prerequisiteSkills) {
		return skill(name, branch, startingSkill, DevStatus.IMPLEMENTED, unlocksAbilities, prerequisiteSkills);
	}

	private static HamonSkillDefinition skill(String name, HamonSkillBranch branch, boolean startingSkill, DevStatus status, List<String> unlocksAbilities, List<String> prerequisiteSkills) {
		boolean requiresTeacher = branch != BRANCH_CHARACTER_TECHNIQUE;
		return new HamonSkillDefinition(name, branch, startingSkill, unlocksAbilities, prerequisiteSkills, requiresTeacher, status);
	}

	private static HamonTechniqueDefinition technique(String name, List<String> skillIds, List<String> perksOnPick,
			Map<HamonSkillBranch, Float> branchEfficiencies, java.util.function.Supplier<? extends net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent>> musicOnPick) {
		return new HamonTechniqueDefinition(name, skillIds, perksOnPick, branchEfficiencies, musicOnPick);
	}

	private static List<String> skills(String... skillNames) {
		return List.of(skillNames);
	}

	private static List<String> perks(String... skillNames) {
		return List.of(skillNames);
	}

	private static List<String> abilities(String... abilityNames) {
		return List.of(abilityNames);
	}

	private static List<String> requires(String... skillNames) {
		return List.of(skillNames);
	}

	private static List<String> none() {
		return List.of();
	}
}
