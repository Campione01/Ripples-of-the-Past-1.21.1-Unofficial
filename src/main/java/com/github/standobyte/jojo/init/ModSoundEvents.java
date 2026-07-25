package com.github.standobyte.jojo.init;

import java.util.function.Supplier;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.util.sound.OstSoundList;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// XXX sound subtitles
public class ModSoundEvents {
	public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, JojoMod.MOD_ID);


	public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_HAT_THROW = SOUNDS.register("blade_hat_throw", SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_HAT_SPINNING = register("blade_hat_spinning"); // this method does the same thing, it's just shorter
	public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_HAT_ENTITY_HIT = register("blade_hat_entity_hit");
	public static final DeferredHolder<SoundEvent, SoundEvent> AJA_STONE_CHARGING = register("aja_stone_charging");
	public static final DeferredHolder<SoundEvent, SoundEvent> AJA_STONE_BEAM = register("aja_stone_beam");
	public static final DeferredHolder<SoundEvent, SoundEvent> LISA_LISA_AJA_STONE = register("lisa_lisa_aja_stone");
	public static final DeferredHolder<SoundEvent, SoundEvent> LISA_LISA_SUPER_AJA = register("lisa_lisa_super_aja");
	public static final DeferredHolder<SoundEvent, SoundEvent> LISA_LISA_SNAKE_MUFFLER = register("lisa_lisa_snake_muffler");
	public static final DeferredHolder<SoundEvent, SoundEvent> CLACKERS = register("clackers");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_CLACKER_VOLLEY = register("joseph_clacker_volley");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_HAMON_CLACKER_VOLLEY = register("joseph_hamon_clacker_volley");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_CLACKER_BOOMERANG = register("joseph_clacker_boomerang");
	public static final DeferredHolder<SoundEvent, SoundEvent> TOMMY_GUN_LOOP = register("tommy_gun_loop");
	public static final DeferredHolder<SoundEvent, SoundEvent> TOMMY_GUN_SHOT = register("tommy_gun_shot");
	public static final DeferredHolder<SoundEvent, SoundEvent> TOMMY_GUN_NO_AMMO = register("tommy_gun_no_ammo");
	public static final DeferredHolder<SoundEvent, SoundEvent> MOLOTOV_THROW = register("molotov_throw");
	public static final DeferredHolder<SoundEvent, SoundEvent> KNIFE_THROW = register("knife_throw");
	public static final DeferredHolder<SoundEvent, SoundEvent> KNIVES_THROW = register("knives_throw");
	public static final DeferredHolder<SoundEvent, SoundEvent> KNIFE_HIT = register("knife_hit");
	public static final DeferredHolder<SoundEvent, SoundEvent> WATER_SPLASH = register("water_splash");
	public static final DeferredHolder<SoundEvent, SoundEvent> CLOTHES_SEWED = register("clothes_sewed");

	public static final DeferredHolder<SoundEvent, SoundEvent> WALKMAN_REWIND = register("walkman_rewind");

	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_WHITE = register("cassette_white");
	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_ORANGE = register("cassette_orange");
	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_MAGENTA = register("cassette_magenta");
	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_LIGHT_BLUE = register("cassette_light_blue");
	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_YELLOW = register("cassette_yellow");
	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_LIME = register("cassette_lime");
	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_PINK = register("cassette_pink");
	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_GRAY = register("cassette_gray");
	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_LIGHT_GRAY = register("cassette_light_gray");
	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_CYAN = register("cassette_cyan");
	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_PURPLE = register("cassette_purple");
	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_BLUE = register("cassette_blue");
	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_BROWN = register("cassette_brown");
	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_GREEN = register("cassette_green");
	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_RED = register("cassette_red");
	public static final DeferredHolder<SoundEvent, SoundEvent> CASSETTE_BLACK = register("cassette_black");

	public static final DeferredHolder<SoundEvent, SoundEvent> MAP_BOUGHT_METEORITE = register("map_bought_snowy");
	public static final DeferredHolder<SoundEvent, SoundEvent> MAP_BOUGHT_HAMON_TEMPLE = register("map_bought_mountain");
	public static final DeferredHolder<SoundEvent, SoundEvent> MAP_BOUGHT_PILLAR_MAN_TEMPLE = register("map_bought_jungle");

	public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_PUNCH = register("heavy_punch");

	public static final DeferredHolder<SoundEvent, SoundEvent> STONE_MASK_ACTIVATION_ENTITY = register("stone_mask_activation_entity");
	public static final DeferredHolder<SoundEvent, SoundEvent> STONE_MASK_ACTIVATION = register("stone_mask_activation");
	public static final DeferredHolder<SoundEvent, SoundEvent> STONE_MASK_DEACTIVATION = register("stone_mask_deactivation");

	public static final DeferredHolder<SoundEvent, SoundEvent> VAMPIRE_BLOOD_DRAIN = register("vampire_blood_drain");
	public static final DeferredHolder<SoundEvent, SoundEvent> VAMPIRE_SWIPE = register("vampire_swipe");
	public static final DeferredHolder<SoundEvent, SoundEvent> VAMPIRE_CLAW_LACERATE = register("vampire_claw_lacerate");
	public static final DeferredHolder<SoundEvent, SoundEvent> VAMPIRE_FREEZE = register("vampire_freeze");
	public static final DeferredHolder<SoundEvent, SoundEvent> VAMPIRE_EVIL_ATMOSPHERE = register("vampire_dark_aura");
	public static final DeferredHolder<SoundEvent, SoundEvent> VAMPIRE_CURE_START = register("vampire_cure_start");
	public static final DeferredHolder<SoundEvent, SoundEvent> VAMPIRE_CURE_END = register("vampire_cure_end");

	public static final DeferredHolder<SoundEvent, SoundEvent> ZOMBIE_DEVOUR = register("zombie_devour");
	public static final DeferredHolder<SoundEvent, SoundEvent> ZOMBIE_SWIPE = register("zombie_swipe");
	public static final DeferredHolder<SoundEvent, SoundEvent> ZOMBIE_CLAW_LACERATE = register("zombie_claw_lacerate");

	public static final DeferredHolder<SoundEvent, SoundEvent> PILLAR_MAN_AWAKENING = register("pillar_man_awakening");
	public static final DeferredHolder<SoundEvent, SoundEvent> PILLAR_MAN_HEAT_MODE = register("pillar_man_heat_mode");
	public static final DeferredHolder<SoundEvent, SoundEvent> PILLAR_MAN_WIND_MODE = register("pillar_man_wind_mode");
	public static final DeferredHolder<SoundEvent, SoundEvent> PILLAR_MAN_LIGHT_MODE = register("pillar_man_light_mode");

	public static final DeferredHolder<SoundEvent, SoundEvent> PILLAR_MAN_ABSORPTION = register("pillar_man_absorption");
	public static final DeferredHolder<SoundEvent, SoundEvent> PILLAR_MAN_SWING = register("pillar_man_swing");
	public static final DeferredHolder<SoundEvent, SoundEvent> PILLAR_MAN_PUNCH = register("pillar_man_punch");
	public static final DeferredHolder<SoundEvent, SoundEvent> PILLAR_MAN_STRONG_REGEN = register("pillar_man_strong_regen");
	public static final DeferredHolder<SoundEvent, SoundEvent> PILLAR_MAN_EVASION = register("pillar_man_evasion");

	public static final DeferredHolder<SoundEvent, SoundEvent> BUCKET_FILL_BOILING_BLOOD = register("bucket_fill_boiling_blood");
	public static final DeferredHolder<SoundEvent, SoundEvent> BUCKET_EMPTY_BOILING_BLOOD = register("bucket_empty_boiling_blood");
	public static final DeferredHolder<SoundEvent, SoundEvent> BOILING_BLOOD_POP = register("boiling_blood_pop");
	public static final DeferredHolder<SoundEvent, SoundEvent> BOILING_BLOOD_AMBIENT = register("boiling_blood_ambient");

	public static final DeferredHolder<SoundEvent, SoundEvent> HAMON_SPARK = register("hamon_spark");
	public static final DeferredHolder<SoundEvent, SoundEvent> HAMON_SPARKS_LONG = register("hamon_sparks_long");
	public static final DeferredHolder<SoundEvent, SoundEvent> HAMON_SPARK_SHORT = register("hamon_spark_short");
	public static final DeferredHolder<SoundEvent, SoundEvent> HAMON_CONCENTRATION = register("hamon_concentration");
	public static final DeferredHolder<SoundEvent, SoundEvent> HAMON_HEALING = register("hamon_healing");
	public static final DeferredHolder<SoundEvent, SoundEvent> HAMON_SYO_CHARGE = register("hamon_syo_charge");
	public static final DeferredHolder<SoundEvent, SoundEvent> HAMON_SYO_PUNCH = register("hamon_syo_punch");
	public static final DeferredHolder<SoundEvent, SoundEvent> HAMON_SYO_SWING = register("hamon_syo_swing");
	public static final DeferredHolder<SoundEvent, SoundEvent> HAMON_PICK_JONATHAN = register("hamon_pick_jonathan");
	public static final DeferredHolder<SoundEvent, SoundEvent> HAMON_PICK_ZEPPELI = register("hamon_pick_zeppeli");
	public static final DeferredHolder<SoundEvent, SoundEvent> HAMON_PICK_JOSEPH = register("hamon_pick_joseph");
	public static final DeferredHolder<SoundEvent, SoundEvent> HAMON_PICK_CAESAR = register("hamon_pick_caesar");
	public static final DeferredHolder<SoundEvent, SoundEvent> HAMON_PICK_LISA_LISA = register("hamon_pick_lisa_lisa");
	public static final DeferredHolder<SoundEvent, SoundEvent> GLIDER_FLIGHT = register("glider_flight");
	public static final DeferredHolder<SoundEvent, SoundEvent> HAMON_DETECTOR = register("hamon_detector");
	public static final DeferredHolder<SoundEvent, SoundEvent> HAMON_REBUFF_PUNCH = register("hamon_rebuff_punch");
	public static final DeferredHolder<SoundEvent, SoundEvent> BREATH_DEFAULT = register("player_breath");
	public static final DeferredHolder<SoundEvent, SoundEvent> BREATH_JONATHAN = register("jonathan_breath");
	public static final DeferredHolder<SoundEvent, SoundEvent> BREATH_ZEPPELI = register("zeppeli_breath");
	public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELI_FORCE_BREATH = register("zeppeli_force_breath");
	public static final DeferredHolder<SoundEvent, SoundEvent> BREATH_JOSEPH = register("joseph_breath");
	public static final DeferredHolder<SoundEvent, SoundEvent> BREATH_CAESAR = register("caesar_breath");
	public static final DeferredHolder<SoundEvent, SoundEvent> BREATH_LISA_LISA = register("lisa_lisa_breath");
	public static final DeferredHolder<SoundEvent, SoundEvent> JONATHAN_SENDO_OVERDRIVE = register("jonathan_sendo_overdrive");
	public static final DeferredHolder<SoundEvent, SoundEvent> JONATHAN_SUNLIGHT_YELLOW_OVERDRIVE = register("jonathan_sunlight_yellow_overdrive");
	public static final DeferredHolder<SoundEvent, SoundEvent> JONATHAN_ZOOM_PUNCH = register("jonathan_zoom_punch");
	public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELI_SUNLIGHT_YELLOW_OVERDRIVE = register("zeppeli_sunlight_yellow_overdrive");
	public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELI_HAMON_OF_THE_SUN = register("zeppeli_hamon_of_the_sun");
	public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELI_THIS_IS_SENDO = register("zeppeli_this_is_sendo");
	public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELI_THIS_IS_SENDO_POWER = register("zeppeli_this_is_sendo_power");
	public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELI_ZOOM_PUNCH = register("zeppeli_zoom_punch");
	public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELI_LIFE_MAGNETISM_OVERDRIVE = register("zeppeli_life_magnetism_overdrive");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_SUNLIGHT_YELLOW_OVERDRIVE = register("joseph_sunlight_yellow_overdrive");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_HAMON_OVERDRIVE_BEAT = register("joseph_hamon_overdrive_beat");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_HAMON_PUNCH = register("joseph_hamon_punch");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_ZOOM_PUNCH = register("joseph_zoom_punch");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_BARRIER = register("joseph_barrier");
	public static final DeferredHolder<SoundEvent, SoundEvent> CAESAR_SUNLIGHT_YELLOW_OVERDRIVE = register("caesar_sunlight_yellow_overdrive");
	public static final DeferredHolder<SoundEvent, SoundEvent> CAESAR_SUN_VIBRATION = register("caesar_sun_vibration");
	public static final DeferredHolder<SoundEvent, SoundEvent> CAESAR_HAMON_OF_THE_SUN = register("caesar_hamon_of_the_sun");
	public static final DeferredHolder<SoundEvent, SoundEvent> CAESAR_HAMON_SPARK = register("caesar_hamon_spark");
	public static final DeferredHolder<SoundEvent, SoundEvent> JONATHAN_OVERDRIVE_BARRAGE = register("jonathan_overdrive_barrage");
	public static final DeferredHolder<SoundEvent, SoundEvent> JONATHAN_SYO_BARRAGE_START = register("jonathan_syo_barrage_start");
	public static final DeferredHolder<SoundEvent, SoundEvent> JONATHAN_SYO_BARRAGE = register("jonathan_syo_barrage");
	public static final DeferredHolder<SoundEvent, SoundEvent> JONATHAN_SCARLET_OVERDRIVE = register("jonathan_scarlet_overdrive");
	public static final DeferredHolder<SoundEvent, SoundEvent> JONATHAN_HAMON_OF_FLAME = register("jonathan_hamon_of_flame");
	public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELI_HAMON_CUTTER = register("zeppeli_hamon_cutter");
	public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELI_POPOW_POW_POW = register("zeppeli_popow_pow_pow");
	public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELI_SENDO_WAVE_KICK = register("zeppeli_sendo_wave_kick");
	public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELI_TORNADO_OVERDRIVE = register("zeppeli_tornado_overdrive");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_REBUFF_OVERDRIVE = register("joseph_rebuff_overdrive");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_OH_NO = register("joseph_oh_no");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_GIGGLE = register("joseph_giggle");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_RUN_AWAY = register("joseph_run_away");
	public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELI_DEEP_PASS = register("zeppeli_deep_pass");
	public static final DeferredHolder<SoundEvent, SoundEvent> JONATHAN_DEEP_PASS_REACTION = register("jonathan_deep_pass_reaction");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_SCREAM_SHOOTING = register("joseph_scream");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_WAR_DECLARATION = register("joseph_war_declaration");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_SHOOT = register("joseph_shoot");
	public static final DeferredHolder<SoundEvent, SoundEvent> CAESAR_BUBBLE_LAUNCHER = register("caesar_bubble_launcher");
	public static final DeferredHolder<SoundEvent, SoundEvent> CAESAR_SECRET_HAMON_BUBBLE_LAUNCHER = register("caesar_secret_hamon_bubble_launcher");
	public static final DeferredHolder<SoundEvent, SoundEvent> CAESAR_BUBBLE_BARRIER = register("caesar_bubble_barrier");
	public static final DeferredHolder<SoundEvent, SoundEvent> CAESAR_BUBBLE_CUTTER = register("caesar_bubble_cutter");
	public static final DeferredHolder<SoundEvent, SoundEvent> CAESAR_DISC_SHAPED_HAMON_CUTTER = register("caesar_disc_shaped_hamon_cutter");
	public static final DeferredHolder<SoundEvent, SoundEvent> CAESAR_BUBBLE_CUTTER_GLIDING = register("caesar_bubble_cutter_gliding");
	public static final DeferredHolder<SoundEvent, SoundEvent> CAESAR_LAST_HAMON = register("caesar_last_hamon");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSEPH_CRIMSON_BUBBLE_REACTION = register("joseph_crimson_bubble_reaction");

	public static final DeferredHolder<SoundEvent, SoundEvent> STAND_SUMMON = register("stand_summon");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAND_UNSUMMON = register("stand_unsummon");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAND_DAMAGE_BLOCK = register("stand_damage_block");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAND_PARRY = register("stand_parry");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAND_PUNCH_LIGHT = register("stand_punch_light");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAND_PUNCH_BARRAGE = register("stand_punch_barrage");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAND_PUNCH_HEAVY = register("stand_punch_heavy");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAND_PUNCH_HEAVY_CHARGED = register("stand_punch_heavy_charged");
	public static final DeferredHolder<SoundEvent, SoundEvent> BEARING_SHOT = SOUNDS.register("bearing_shot", SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> THE_WORLD_SUMMON = register("the_world_summon");
	public static final DeferredHolder<SoundEvent, SoundEvent> THE_WORLD_UNSUMMON = register("the_world_unsummon");
	public static final DeferredHolder<SoundEvent, SoundEvent> THE_WORLD_PUNCH_LIGHT = register("the_world_punch_light");
	public static final DeferredHolder<SoundEvent, SoundEvent> THE_WORLD_PUNCH_HEAVY = register("the_world_punch_heavy");
	public static final DeferredHolder<SoundEvent, SoundEvent> THE_WORLD_PUNCH_HEAVY_ENTITY = register("the_world_punch_heavy_entity");
	public static final DeferredHolder<SoundEvent, SoundEvent> THE_WORLD_PUNCH_HEAVY_TS_IMPACT = register("the_world_punch_heavy_ts_impact");
	public static final DeferredHolder<SoundEvent, SoundEvent> THE_WORLD_KICK_HEAVY = register("the_world_kick_heavy");
	public static final DeferredHolder<SoundEvent, SoundEvent> THE_WORLD_MUDA_MUDA_MUDA = register("the_world_muda_muda_muda");
	public static final DeferredHolder<SoundEvent, SoundEvent> THE_WORLD_PUNCH_BARRAGE = THE_WORLD_PUNCH_LIGHT;
	public static final DeferredHolder<SoundEvent, SoundEvent> STAND_LEAP = register("stand_leap");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAND_PUNCH_SWING = register("stand_punch_swing");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAND_PUNCH_HEAVY_SWING = register("stand_punch_heavy_swing");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAND_PUNCH_BARRAGE_SWING = register("stand_punch_barrage_swing");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAND_PUNCH_CRY = register("stand_punch_cry");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAND_PUNCH_HEAVY_CRY = register("stand_punch_heavy_cry");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAND_BARRAGE_CRY = register("stand_barrage_cry");

	public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLATINUM_SUMMON = register("star_platinum_summon");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLATINUM_UNSUMMON = register("star_platinum_unsummon");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLATINUM_ORA = register("star_platinum_ora");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLATINUM_ORA_LONG = register("star_platinum_ora_long");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLATINUM_ORA_RUSH = register("star_platinum_ora_ora_ora");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLATINUM_PUNCH_LIGHT = register("star_platinum_punch_light");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLATINUM_PUNCH_HEAVY = register("star_platinum_punch_heavy");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLATINUM_PUNCH_BARRAGE = register("star_platinum_punch_barrage");
	public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLATINUM_STAR_FINGER = SOUNDS.register("star_platinum_star_finger", SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLATINUM_ZOOM = SOUNDS.register("star_platinum_zoom", SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLATINUM_ZOOM_CLICK = SOUNDS.register("star_platinum_zoom_click", SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLATINUM_INHALE = SOUNDS.register("star_platinum_inhale", SoundEvent::createVariableRangeEvent);
	public static final OstSoundList STAR_PLATINUM_OST = new OstSoundList(JojoMod.resLoc("star_platinum_ost"), SOUNDS);

    public static final DeferredHolder<SoundEvent, SoundEvent> JOTARO_STAR_FINGER = SOUNDS.register("jotaro_star_finger", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> POLNAREFF_FENCING = register("polnareff_fencing");
    public static final DeferredHolder<SoundEvent, SoundEvent> POLNAREFF_HORA_HORA_HORA = register("polnareff_hora_hora_hora");
    public static final DeferredHolder<SoundEvent, SoundEvent> POLNAREFF_CHARIOT = register("polnareff_chariot");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOTARO_STAR_PLATINUM = register("jotaro_star_platinum");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_THIS_IS_THE_WORLD = register("dio_this_is_the_world");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_MUDA = register("dio_muda");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_MUDA_MUDA = register("dio_muda_muda");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_WRY = register("dio_wry");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_DIE = register("dio_die");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOTARO_STAR_PLATINUM_THE_WORLD = register("jotaro_star_platinum_the_world");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOTARO_TIME_RESUMES_DASU = register("jotaro_time_resumes_dasu");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOTARO_TIME_RESUMES_HAJIMETA = register("jotaro_time_resumes_hajimeta");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOTARO_TIME_RESUMES = register("jotaro_time_resumes");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_THE_WORLD = register("dio_the_world");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_TIME_STOP = register("dio_time_stop");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_TOKI_YO_TOMARE = register("dio_toki_yo_tomare");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_TOMARE_TOKI_YO = register("dio_tomare_toki_yo");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_TIME_RESUMES = register("dio_time_resumes");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_TIMES_UP = register("dio_times_up");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_ZERO = register("dio_zero");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_CANT_MOVE = register("dio_cant_move");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_5_SECONDS = register("dio_5_seconds");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_ONE_MORE = register("dio_one_more");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIO_ROAD_ROLLER = register("dio_road_roller");
	public static final DeferredHolder<SoundEvent, SoundEvent> JONATHAN_THE_WORLD = register("jonathan_the_world");

	public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLATINUM_TIME_STOP = SOUNDS.register("star_platinum_time_stop", SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLATINUM_TIME_RESUME = SOUNDS.register("star_platinum_time_resume", SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> STAR_PLATINUM_TIME_STOP_BLINK = SOUNDS.register("star_platinum_time_stop_blink", SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> THE_WORLD_TIME_STOP = SOUNDS.register("the_world_time_stop", SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> THE_WORLD_TIME_RESUME = SOUNDS.register("the_world_time_resume", SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> THE_WORLD_TIME_STOP_BLINK = SOUNDS.register("the_world_time_stop_blink", SoundEvent::createVariableRangeEvent);
	public static final OstSoundList THE_WORLD_OST = new OstSoundList(JojoMod.resLoc("the_world_ost"), SOUNDS);
	public static final DeferredHolder<SoundEvent, SoundEvent> TIME_STOP = SOUNDS.register("time_stop", SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> TIME_RESUME = SOUNDS.register("time_resume", SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> TIME_STOP_BLINK = SOUNDS.register("time_stop_blink", SoundEvent::createVariableRangeEvent);
	public static final DeferredHolder<SoundEvent, SoundEvent> THE_WORLD_TIME_STOP_UNREVEALED = SOUNDS.register("the_world_time_stop_unrevealed", SoundEvent::createVariableRangeEvent);

	public static final DeferredHolder<SoundEvent, SoundEvent> HIEROPHANT_GREEN_TENTACLES = register("hierophant_green_tentacles");
	public static final DeferredHolder<SoundEvent, SoundEvent> KAKYOIN_HIEROPHANT_GREEN = register("kakyoin_hierophant_green");
	public static final DeferredHolder<SoundEvent, SoundEvent> KAKYOIN_HIEROPHANT = register("kakyoin_hierophant");
	public static final DeferredHolder<SoundEvent, SoundEvent> KAKYOIN_EMERALD_SPLASH = register("kakyoin_emerald_splash");
	public static final DeferredHolder<SoundEvent, SoundEvent> HIEROPHANT_GREEN_SUMMON = register("hierophant_green_summon");
	public static final DeferredHolder<SoundEvent, SoundEvent> HIEROPHANT_GREEN_UNSUMMON = register("hierophant_green_unsummon");
	public static final DeferredHolder<SoundEvent, SoundEvent> HIEROPHANT_GREEN_EMERALD_SPLASH = register("hierophant_green_emerald_splash");
	public static final DeferredHolder<SoundEvent, SoundEvent> HIEROPHANT_GREEN_BARRIER_PLACED = register("hierophant_green_barrier_placed");
	public static final DeferredHolder<SoundEvent, SoundEvent> HIEROPHANT_GREEN_BARRIER_RIPPED = register("hierophant_green_barrier_ripped");
	public static final DeferredHolder<SoundEvent, SoundEvent> HIEROPHANT_GREEN_GRAPPLE_CATCH = register("hierophant_green_grapple_catch");
	public static final OstSoundList HIEROPHANT_GREEN_OST = new OstSoundList(JojoMod.resLoc("hierophant_green_ost"), SOUNDS);

	public static final DeferredHolder<SoundEvent, SoundEvent> ROAD_ROLLER_HIT = register("road_roller_hit");
	public static final DeferredHolder<SoundEvent, SoundEvent> ROAD_ROLLER_LAND = register("road_roller_land");

	public static final DeferredHolder<SoundEvent, SoundEvent> SILVER_CHARIOT_SWEEP_LIGHT = register("silver_chariot_sweep_light");
	public static final DeferredHolder<SoundEvent, SoundEvent> POLNAREFF_SILVER_CHARIOT = register("polnareff_silver_chariot");
	public static final DeferredHolder<SoundEvent, SoundEvent> SILVER_CHARIOT_SUMMON = register("silver_chariot_summon");
	public static final DeferredHolder<SoundEvent, SoundEvent> SILVER_CHARIOT_UNSUMMON = register("silver_chariot_unsummon");
	public static final DeferredHolder<SoundEvent, SoundEvent> SILVER_CHARIOT_DASH = register("silver_chariot_dash");
	public static final DeferredHolder<SoundEvent, SoundEvent> SILVER_CHARIOT_BARRAGE_SWIPE = register("silver_chariot_barrage_swipe");
	public static final DeferredHolder<SoundEvent, SoundEvent> SILVER_CHARIOT_RAPIER_SHOT = register("silver_chariot_rapier_shot");
	public static final DeferredHolder<SoundEvent, SoundEvent> SILVER_CHARIOT_SWEEP_HEAVY = register("silver_chariot_sweep_heavy");
	public static final DeferredHolder<SoundEvent, SoundEvent> SILVER_CHARIOT_ARMOR_OFF = register("silver_chariot_armor_off");
	public static final DeferredHolder<SoundEvent, SoundEvent> SILVER_CHARIOT_BLOCK = register("silver_chariot_block");
	public static final OstSoundList SILVER_CHARIOT_OST = new OstSoundList(JojoMod.resLoc("silver_chariot_ost"), SOUNDS);

	public static final DeferredHolder<SoundEvent, SoundEvent> MAGICIANS_RED_FIRE_BLAST = register("magicians_red_fire_ability");
	public static final DeferredHolder<SoundEvent, SoundEvent> MAGICIANS_RED_PUNCH_LIGHT = register("magicians_red_punch_light");
	public static final DeferredHolder<SoundEvent, SoundEvent> MAGICIANS_RED_PUNCH_HEAVY = register("magicians_red_punch_heavy");
	public static final DeferredHolder<SoundEvent, SoundEvent> MAGICIANS_RED_KICK_HEAVY = register("magicians_red_kick_heavy");
	public static final DeferredHolder<SoundEvent, SoundEvent> AVDOL_MAGICIANS_RED = register("avdol_magicians_red");
	public static final DeferredHolder<SoundEvent, SoundEvent> MAGICIANS_RED_SUMMON = register("magicians_red_summon");
	public static final DeferredHolder<SoundEvent, SoundEvent> MAGICIANS_RED_UNSUMMON = register("magicians_red_unsummon");
	public static final DeferredHolder<SoundEvent, SoundEvent> AVDOL_HELL_2_U = register("avdol_hell_2_u");
	public static final DeferredHolder<SoundEvent, SoundEvent> AVDOL_CROSSFIRE_HURRICANE = register("avdol_crossfire_hurricane");
	public static final DeferredHolder<SoundEvent, SoundEvent> AVDOL_CROSSFIRE_HURRICANE_SPECIAL = register("avdol_crossfire_hurricane_special");
	public static final DeferredHolder<SoundEvent, SoundEvent> AVDOL_RED_BIND = register("avdol_red_bind");
	public static final Supplier<SoundEvent> MAGICIANS_RED_FIREBALL = () -> SoundEvents.FIRECHARGE_USE;
	public static final DeferredHolder<SoundEvent, SoundEvent> MAGICIANS_RED_CROSSFIRE_HURRICANE = MAGICIANS_RED_FIRE_BLAST;
	public static final DeferredHolder<SoundEvent, SoundEvent> MAGICIANS_RED_RED_BIND = MAGICIANS_RED_FIRE_BLAST;
	public static final OstSoundList MAGICIANS_RED_OST = new OstSoundList(JojoMod.resLoc("magicians_red_ost"), SOUNDS);

	public static final DeferredHolder<SoundEvent, SoundEvent> CRAZY_DIAMOND_FIX_STARTED = register("crazy_diamond_fix_started");
	public static final DeferredHolder<SoundEvent, SoundEvent> CRAZY_DIAMOND_FIX_LOOP = register("crazy_diamond_fix_loop");
	public static final DeferredHolder<SoundEvent, SoundEvent> CRAZY_DIAMOND_FIX_ENDED = register("crazy_diamond_fix_ended");
	public static final DeferredHolder<SoundEvent, SoundEvent> CRAZY_DIAMOND_SUMMON = register("crazy_diamond_summon");
	public static final DeferredHolder<SoundEvent, SoundEvent> CRAZY_DIAMOND_UNSUMMON = register("crazy_diamond_unsummon");
	public static final DeferredHolder<SoundEvent, SoundEvent> CRAZY_DIAMOND_PUNCH_LIGHT = register("crazy_diamond_punch_light");
	public static final DeferredHolder<SoundEvent, SoundEvent> CRAZY_DIAMOND_PUNCH_BARRAGE = CRAZY_DIAMOND_PUNCH_LIGHT;
	public static final DeferredHolder<SoundEvent, SoundEvent> CRAZY_DIAMOND_PUNCH_HEAVY = register("crazy_diamond_punch_heavy");
	public static final DeferredHolder<SoundEvent, SoundEvent> CRAZY_DIAMOND_DORA = register("crazy_diamond_dora");
	public static final DeferredHolder<SoundEvent, SoundEvent> CRAZY_DIAMOND_DORA_LONG = register("crazy_diamond_dora_long");
	public static final DeferredHolder<SoundEvent, SoundEvent> CRAZY_DIAMOND_DORARARA = register("crazy_diamond_dorarara");
	public static final DeferredHolder<SoundEvent, SoundEvent> CRAZY_DIAMOND_BULLET_SHOT = register("crazy_diamond_bullet_shot");
	public static final DeferredHolder<SoundEvent, SoundEvent> CRAZY_DIAMOND_BLOOD_CUTTER_SHOT = register("crazy_diamond_blood_cutter_shot");
	public static final DeferredHolder<SoundEvent, SoundEvent> ANGELO_ROCK_GRUNT = register("angelo_rock_grunt");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSUKE_CRAZY_DIAMOND = register("josuke_crazy_diamond");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSUKE_FIX = register("josuke_fix");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSUKE_PRAY_FOR_ETERNITY = register("josuke_pray_for_eternity");
	public static final DeferredHolder<SoundEvent, SoundEvent> JOSUKE_YO_ANGELO = register("josuke_yo_angelo");
	public static final OstSoundList CRAZY_DIAMOND_OST = new OstSoundList(JojoMod.resLoc("crazy_diamond_ost"), SOUNDS);

	public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_EXPERIENCE_SUMMON = register("gold_experience_summon");
	public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_EXPERIENCE_UNSUMMON = register("gold_experience_unsummon");
	public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_EXPERIENCE_PUNCH_LIGHT = register("gold_experience_punch_light");
	public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_EXPERIENCE_PUNCH_HEAVY = register("gold_experience_punch_heavy");
	public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_EXPERIENCE_PUNCH_HEAVY_EXTRA = register("gold_experience_punch_heavy_extra");
	public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_EXPERIENCE_PUNCH_BARRAGE = register("gold_experience_punch_barrage");
	public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_EXPERIENCE_MUDA = register("gold_experience_muda");
	public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_EXPERIENCE_MUDA_LONG = register("gold_experience_muda_long");
	public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_EXPERIENCE_MUDA_RUSH = register("gold_experience_muda_muda_muda");
	public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_EXPERIENCE_WRY = register("gold_experience_wry");
	public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_EXPERIENCE_LIFE_START = register("gold_experience_life_start");
	public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_EXPERIENCE_LIFE_REVERT = register("gold_experience_life_revert");
	public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_EXPERIENCE_LIFE_ITEM = register("gold_experience_life_item");
	public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_EXPERIENCE_HEAL = register("gold_experience_heal");
	public static final DeferredHolder<SoundEvent, SoundEvent> GIORNO_GOLD_EXPERIENCE = register("giorno_gold_experience");
	public static final DeferredHolder<SoundEvent, SoundEvent> GIORNO_NEW_LIFE = register("giorno_new_life");
	public static final OstSoundList GOLD_EXPERIENCE_OST = new OstSoundList(JojoMod.resLoc("gold_experience_ost"), SOUNDS);
	
	
	private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
		return SOUNDS.register(name, SoundEvent::createVariableRangeEvent);
	}
	
}
