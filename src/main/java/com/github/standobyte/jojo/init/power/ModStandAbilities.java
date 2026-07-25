package com.github.standobyte.jojo.init.power;

import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.entityattachment.custom_effect.EntityCustomEffectType;
import com.github.standobyte.jojo.init.ModEntityCustomEffects;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojoimpl.stands._entitybase.StandBearingShotAbility;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityBarrageAbility;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityBlockAbility;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityGrabAbility;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityGrabReleaseAbility;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityGrabThrowAbility;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityHeavyPunchChargedAbility;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityHeavyPunchAbility;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityManualControlToggle;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityPunchAbility;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDAnchorBlockAbility;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDAnchorMakeAbility;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDAngeloRockPunchEffect;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDAngeloRockPunchInput;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDBlockBulletAbility;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDBloodCutterAbility;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDHealAbility;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDLeaveObjectPunchEffect;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDLeaveObjectPunchInput;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDMisshapingPunchEffect;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDMisshapingPunchInput;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDRepairItemAbility;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDRestoreTerrainAbility;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDRevertEntityAndBlocksAbility;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDUncraftItemAbility;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDiamondHeavyPunchAbility;
import com.github.standobyte.jojoimpl.stands.crazydiamond.DriedBloodDropsEffect;
import com.github.standobyte.jojoimpl.stands.boyiiman.BoyIIManStandPartTakenEffect;
import com.github.standobyte.jojoimpl.stands.goldexperience.GECreatedLifeformEffect;
import com.github.standobyte.jojoimpl.stands.goldexperience.GEHealingEffect;
import com.github.standobyte.jojoimpl.stands.goldexperience.GEItemMarkEffect;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceBoneMealAbility;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceChooseLifeformAbility;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceCreateLifeformAbility;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceEntityLifeshotAbility;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceHealAbility;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceHealOtherAbility;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceHealingItemAbility;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceHeavyPunchAbility;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceLifeDetectorAbility;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceLifeshotPunchAbility;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceMarkItemAbility;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceRevertLifeformAbility;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceToothLifeformAbility;
import com.github.standobyte.jojoimpl.stands.hierophant.HierophantBarrierAbility;
import com.github.standobyte.jojoimpl.stands.hierophant.HierophantEmeraldSplashAbility;
import com.github.standobyte.jojoimpl.stands.hierophant.HierophantGrappleAbility;
import com.github.standobyte.jojoimpl.stands.hierophant.HierophantPuppetAbility;
import com.github.standobyte.jojoimpl.stands.hierophant.HierophantPuppetEffect;
import com.github.standobyte.jojoimpl.stands.hierophant.HierophantStringAttackAbility;
import com.github.standobyte.jojoimpl.stands.magiciansred.MagiciansRedCrossfireHurricaneAbility;
import com.github.standobyte.jojoimpl.stands.magiciansred.MagiciansRedDetectorAbility;
import com.github.standobyte.jojoimpl.stands.magiciansred.MagiciansRedFireballAbility;
import com.github.standobyte.jojoimpl.stands.magiciansred.MagiciansRedFlameBurstAbility;
import com.github.standobyte.jojoimpl.stands.magiciansred.MagiciansRedKickAbility;
import com.github.standobyte.jojoimpl.stands.magiciansred.MagiciansRedRedBindAbility;
import com.github.standobyte.jojoimpl.stands.silverchariot.SilverChariotBarrageAbility;
import com.github.standobyte.jojoimpl.stands.silverchariot.SilverChariotDashAttackAbility;
import com.github.standobyte.jojoimpl.stands.silverchariot.SilverChariotLightAttackAbility;
import com.github.standobyte.jojoimpl.stands.silverchariot.SilverChariotRapierLaunchAbility;
import com.github.standobyte.jojoimpl.stands.silverchariot.SilverChariotSweepingAttackAbility;
import com.github.standobyte.jojoimpl.stands.silverchariot.SilverChariotTakeOffArmorAbility;
import com.github.standobyte.jojoimpl.stands.starplatinum.HeavyPunchUppercutAbility;
import com.github.standobyte.jojoimpl.stands.starplatinum.StarFingerAbility;
import com.github.standobyte.jojoimpl.stands.starplatinum.StarInhaleAbility;
import com.github.standobyte.jojoimpl.stands.starplatinum.StarPlatinumZoomAbility;
import com.github.standobyte.jojoimpl.stands.theworld.TheWorldBarrageAbility;
import com.github.standobyte.jojoimpl.stands.theworld.TheWorldHeavyPunchAbility;
import com.github.standobyte.jojoimpl.stands.theworld.TheWorldKickAbility;
import com.github.standobyte.jojoimpl.stands.theworld.TheWorldTSPunchAbility;
import com.github.standobyte.jojoimpl.stands.theworld.TimeStopBlinkAbility;
import com.github.standobyte.jojoimpl.stands.theworld.TimeResumeAbility;
import com.github.standobyte.jojoimpl.stands.theworld.TimeStopAbility;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStandAbilities {
	public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES = JojoRegistries.ABILITY_TYPES;
	public static final DeferredRegister<EntityCustomEffectType<?>> STAND_EFFECT_TYPES = ModEntityCustomEffects.CUSTOM_EFFECTS;
	
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<StandEntityManualControlToggle>> MANUAL_CONTROL = ABILITY_TYPES.register(
			"stand_manual_control", key -> new AbilityType<>(key, StandEntityManualControlToggle::new));
	
	
//	public static final DeferredHolder<AbilityType<?>, AbilityType<StandAttackWithItemAbility>> ITEM_ATTACK = ABILITY_TYPES.register(
//			"stand_item_attack", key -> new AbilityType<>(key, StandAttackWithItemAbility::new));
	
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<StandEntityPunchAbility>> PUNCH = ABILITY_TYPES.register(
			"stand_punch", key -> new AbilityType<>(key, StandEntityPunchAbility::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<StandEntityBarrageAbility>> BARRAGE = ABILITY_TYPES.register(
			"stand_barrage", key -> new AbilityType<>(key, StandEntityBarrageAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<StandEntityBlockAbility>> GUARD = ABILITY_TYPES.register(
			"stand_guard", key -> new AbilityType<>(key, StandEntityBlockAbility::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<StandEntityHeavyPunchAbility>> HEAVY_PUNCH = ABILITY_TYPES.register(
			"stand_heavy_punch", key -> new AbilityType<>(key, StandEntityHeavyPunchAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<StandEntityHeavyPunchChargedAbility>> HEAVY_CHARGED = ABILITY_TYPES.register(
			"stand_heavy_charged", key -> new AbilityType<>(key, StandEntityHeavyPunchChargedAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<StandEntityGrabAbility>> GRAB = ABILITY_TYPES.register(
			"grab", key -> new AbilityType<>(key, StandEntityGrabAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<StandEntityGrabReleaseAbility>> GRAB_RELEASE = ABILITY_TYPES.register(
			"grab_release", key -> new AbilityType<>(key, StandEntityGrabReleaseAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<StandEntityGrabThrowAbility>> GRAB_THROW = ABILITY_TYPES.register(
			"grab_throw", key -> new AbilityType<>(key, StandEntityGrabThrowAbility::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<HeavyPunchUppercutAbility>> HEAVY_UPPERCUT = ABILITY_TYPES.register(
			"stand_heavy_uppercut", key -> new AbilityType<>(key, HeavyPunchUppercutAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<StandBearingShotAbility>> BEARING_SHOT = ABILITY_TYPES.register(
			"bearing_shot", key -> new AbilityType<>(key, StandBearingShotAbility::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<StarFingerAbility>> SP_STAR_FINGER = ABILITY_TYPES.register(
			"star_finger", key -> new AbilityType<>(key, StarFingerAbility::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<StarInhaleAbility>> SP_INHALE = ABILITY_TYPES.register(
			"inhale", key -> new AbilityType<>(key, StarInhaleAbility::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<StarPlatinumZoomAbility>> SP_EYESIGHT = ABILITY_TYPES.register(
			"star_platinum_zoom", key -> new AbilityType<>(key, StarPlatinumZoomAbility::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<TimeStopAbility>> TIME_STOP = ABILITY_TYPES.register(
			"time_stop", key -> new AbilityType<>(key, TimeStopAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<TimeStopBlinkAbility>> TIME_STOP_BLINK = ABILITY_TYPES.register(
			"time_stop_blink", key -> new AbilityType<>(key, TimeStopBlinkAbility::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<TimeResumeAbility>> TIME_RESUME = ABILITY_TYPES.register(
			"time_resume", key -> new AbilityType<>(key, TimeResumeAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<TheWorldTSPunchAbility>> TW_TS_PUNCH = ABILITY_TYPES.register(
			"the_world_ts_punch", key -> new AbilityType<>(key, TheWorldTSPunchAbility::new));
	
	public static final DeferredHolder<EntityCustomEffectType<?>, EntityCustomEffectType<GECreatedLifeformEffect>> EFFECT_GE_CREATED_LIFEFORM = STAND_EFFECT_TYPES.register(
			"ge_created_lifeform", key -> new EntityCustomEffectType<>(key, GECreatedLifeformEffect::new));

	public static final DeferredHolder<EntityCustomEffectType<?>, EntityCustomEffectType<GEItemMarkEffect>> EFFECT_GE_ITEM_MARK = STAND_EFFECT_TYPES.register(
			"ge_item_mark", key -> new EntityCustomEffectType<>(key, GEItemMarkEffect::new));

	public static final DeferredHolder<EntityCustomEffectType<?>, EntityCustomEffectType<GEHealingEffect>> EFFECT_GE_HEALING = STAND_EFFECT_TYPES.register(
			"ge_healing", key -> new EntityCustomEffectType<>(key, GEHealingEffect::new));

	public static final DeferredHolder<EntityCustomEffectType<?>, EntityCustomEffectType<BoyIIManStandPartTakenEffect>> EFFECT_BIIM_STAND_PART_TAKE = STAND_EFFECT_TYPES.register(
			"biim_stand_part_take", key -> new EntityCustomEffectType<>(key, BoyIIManStandPartTakenEffect::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<HierophantPuppetAbility>> HG_PUPPET = ABILITY_TYPES.register(
			"puppet", key -> new AbilityType<>(key, HierophantPuppetAbility::new));

	public static final DeferredHolder<EntityCustomEffectType<?>, EntityCustomEffectType<HierophantPuppetEffect>> EFFECT_HG_PUPPET = STAND_EFFECT_TYPES.register(
			"hg_puppet", key -> new EntityCustomEffectType<>(key, HierophantPuppetEffect::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<HierophantEmeraldSplashAbility>> HG_EMERALD_SPLASH = ABILITY_TYPES.register(
			"emerald_splash", key -> new AbilityType<>(key, HierophantEmeraldSplashAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<HierophantEmeraldSplashAbility>> HG_EMERALD_SPLASH_CONCENTRATED = ABILITY_TYPES.register(
			"emerald_splash_concentrated", key -> new AbilityType<>(key, HierophantEmeraldSplashAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<HierophantStringAttackAbility>> HG_STRING_ATTACK = ABILITY_TYPES.register(
			"string_attack", key -> new AbilityType<>(key, HierophantStringAttackAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<HierophantStringAttackAbility>> HG_STRING_BIND = ABILITY_TYPES.register(
			"string_bind", key -> new AbilityType<>(key, HierophantStringAttackAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<HierophantGrappleAbility>> HG_GRAPPLE = ABILITY_TYPES.register(
			"grapple", key -> new AbilityType<>(key, HierophantGrappleAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<HierophantGrappleAbility>> HG_GRAPPLE_ENTITY = ABILITY_TYPES.register(
			"grapple_entity", key -> new AbilityType<>(key, HierophantGrappleAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<HierophantBarrierAbility>> HG_BARRIER = ABILITY_TYPES.register(
			"barrier", key -> new AbilityType<>(key, HierophantBarrierAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<MagiciansRedFlameBurstAbility>> MR_FLAME_BURST = ABILITY_TYPES.register(
			"flame_burst", key -> new AbilityType<>(key, MagiciansRedFlameBurstAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<MagiciansRedFireballAbility>> MR_FIREBALL = ABILITY_TYPES.register(
			"fireball", key -> new AbilityType<>(key, MagiciansRedFireballAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<MagiciansRedCrossfireHurricaneAbility>> MR_CROSSFIRE_HURRICANE = ABILITY_TYPES.register(
			"crossfire_hurricane", key -> new AbilityType<>(key, MagiciansRedCrossfireHurricaneAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<MagiciansRedCrossfireHurricaneAbility>> MR_CROSSFIRE_HURRICANE_SPECIAL = ABILITY_TYPES.register(
			"crossfire_hurricane_special", key -> new AbilityType<>(key, MagiciansRedCrossfireHurricaneAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<MagiciansRedKickAbility>> MR_KICK = ABILITY_TYPES.register(
			"magicians_red_kick", key -> new AbilityType<>(key, MagiciansRedKickAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<MagiciansRedRedBindAbility>> MR_RED_BIND = ABILITY_TYPES.register(
			"red_bind", key -> new AbilityType<>(key, MagiciansRedRedBindAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<MagiciansRedDetectorAbility>> MR_DETECTOR = ABILITY_TYPES.register(
			"mr_detector", key -> new AbilityType<>(key, MagiciansRedDetectorAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<SilverChariotLightAttackAbility>> SC_LIGHT_ATTACK = ABILITY_TYPES.register(
			"silver_chariot_light_attack", key -> new AbilityType<>(key, SilverChariotLightAttackAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<SilverChariotLightAttackAbility>> SC_NO_RAPIER_LIGHT_ATTACK = ABILITY_TYPES.register(
			"silver_chariot_no_rapier_light_attack", key -> new AbilityType<>(key, SilverChariotLightAttackAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<SilverChariotBarrageAbility>> SC_BARRAGE = ABILITY_TYPES.register(
			"silver_chariot_barrage", key -> new AbilityType<>(key, SilverChariotBarrageAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<SilverChariotDashAttackAbility>> SC_DASH_ATTACK = ABILITY_TYPES.register(
			"dash_attack", key -> new AbilityType<>(key, SilverChariotDashAttackAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<SilverChariotSweepingAttackAbility>> SC_SWEEPING_ATTACK = ABILITY_TYPES.register(
			"sweeping_attack", key -> new AbilityType<>(key, SilverChariotSweepingAttackAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<SilverChariotRapierLaunchAbility>> SC_RAPIER_LAUNCH = ABILITY_TYPES.register(
			"rapier_launch", key -> new AbilityType<>(key, SilverChariotRapierLaunchAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<SilverChariotTakeOffArmorAbility>> SC_TAKE_OFF_ARMOR = ABILITY_TYPES.register(
			"take_off_armor", key -> new AbilityType<>(key, SilverChariotTakeOffArmorAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<GoldExperienceLifeDetectorAbility>> GE_LIFE_DETECTOR = ABILITY_TYPES.register(
			"life_detector", key -> new AbilityType<>(key, GoldExperienceLifeDetectorAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<GoldExperienceHealAbility>> GE_HEAL = ABILITY_TYPES.register(
			"ge_heal", key -> new AbilityType<>(key, GoldExperienceHealAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<GoldExperienceHeavyPunchAbility>> GE_HEAVY_PUNCH = ABILITY_TYPES.register(
			"ge_heavy_punch", key -> new AbilityType<>(key, GoldExperienceHeavyPunchAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<GoldExperienceChooseLifeformAbility>> GE_CHOOSE_LIFEFORM = ABILITY_TYPES.register(
			"ge_choose_lifeform", key -> new AbilityType<>(key, GoldExperienceChooseLifeformAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<GoldExperienceCreateLifeformAbility>> GE_CREATE_LIFEFORM = ABILITY_TYPES.register(
			"ge_create_lifeform", key -> new AbilityType<>(key, GoldExperienceCreateLifeformAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<GoldExperienceMarkItemAbility>> GE_MARK_ITEM = ABILITY_TYPES.register(
			"ge_mark_item", key -> new AbilityType<>(key, GoldExperienceMarkItemAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<GoldExperienceBoneMealAbility>> GE_BONE_MEAL = ABILITY_TYPES.register(
			"ge_bone_meal", key -> new AbilityType<>(key, GoldExperienceBoneMealAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<GoldExperienceHealingItemAbility>> GE_HEALING_ITEM = ABILITY_TYPES.register(
			"ge_healing_item", key -> new AbilityType<>(key, GoldExperienceHealingItemAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<GoldExperienceHealOtherAbility>> GE_HEAL_OTHER = ABILITY_TYPES.register(
			"ge_heal_other", key -> new AbilityType<>(key, GoldExperienceHealOtherAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<GoldExperienceEntityLifeshotAbility>> GE_ENTITY_LIFESHOT = ABILITY_TYPES.register(
			"ge_lifeshot", key -> new AbilityType<>(key, GoldExperienceEntityLifeshotAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<GoldExperienceLifeshotPunchAbility>> GE_LIFESHOT_PUNCH = ABILITY_TYPES.register(
			"ge_lifeshot_punch", key -> new AbilityType<>(key, GoldExperienceLifeshotPunchAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<GoldExperienceToothLifeformAbility>> GE_TOOTH_LIFEFORM = ABILITY_TYPES.register(
			"ge_tooth_lifeform", key -> new AbilityType<>(key, GoldExperienceToothLifeformAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<GoldExperienceRevertLifeformAbility>> GE_REVERT_LIFEFORM = ABILITY_TYPES.register(
			"ge_revert_lifeform", key -> new AbilityType<>(key, GoldExperienceRevertLifeformAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<TheWorldKickAbility>> TW_KICK = ABILITY_TYPES.register(
			"kick", key -> new AbilityType<>(key, TheWorldKickAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<TheWorldHeavyPunchAbility>> TW_HEAVY_PUNCH = ABILITY_TYPES.register(
			"tw_heavy_punch", key -> new AbilityType<>(key, TheWorldHeavyPunchAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<TheWorldBarrageAbility>> TW_BARRAGE = ABILITY_TYPES.register(
			"tw_barrage", key -> new AbilityType<>(key, TheWorldBarrageAbility::new));

	
	public static final DeferredHolder<AbilityType<?>, AbilityType<CrazyDiamondHeavyPunchAbility>> CD_HEAVY_PUNCH = ABILITY_TYPES.register(
			"crazy_diamond_heavy_punch", key -> new AbilityType<>(key, CrazyDiamondHeavyPunchAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<CrazyDBloodCutterAbility>> CD_BLOOD_CUTTER = ABILITY_TYPES.register(
			"blood_cutter", key -> new AbilityType<>(key, CrazyDBloodCutterAbility::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<CrazyDBlockBulletAbility>> CD_BLOCK_BULLET = ABILITY_TYPES.register(
			"block_bullet", key -> new AbilityType<>(key, CrazyDBlockBulletAbility::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<CrazyDRevertEntityAndBlocksAbility>> CD_REVERT_STATE = ABILITY_TYPES.register(
			"revert_state", key -> new AbilityType<>(key, CrazyDRevertEntityAndBlocksAbility::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<CrazyDHealAbility>> CD_HEAL = ABILITY_TYPES.register(
			"heal", key -> new AbilityType<>(key, CrazyDHealAbility::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<CrazyDRestoreTerrainAbility>> CD_RESTORE_TERRAIN = ABILITY_TYPES.register(
			"restore_terrain", key -> new AbilityType<>(key, CrazyDRestoreTerrainAbility::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<CrazyDAnchorBlockAbility>> CD_ANCHOR_MOVE = ABILITY_TYPES.register(
			"anchor_move", key -> new AbilityType<>(key, CrazyDAnchorBlockAbility::new));

	public static final DeferredHolder<AbilityType<?>, AbilityType<CrazyDAnchorMakeAbility>> CD_ANCHOR_MAKE = ABILITY_TYPES.register(
			"anchor_make", key -> new AbilityType<>(key, CrazyDAnchorMakeAbility::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<CrazyDRepairItemAbility>> CD_REPAIR_ITEM = ABILITY_TYPES.register(
			"repair_item", key -> new AbilityType<>(key, CrazyDRepairItemAbility::new));
	public static final DeferredHolder<AbilityType<?>, AbilityType<CrazyDUncraftItemAbility>> CD_UNCRAFT_ITEM = ABILITY_TYPES.register(
			"uncraft_item", key -> new AbilityType<>(key, CrazyDUncraftItemAbility::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<CrazyDLeaveObjectPunchInput>> CD_LEAVE_OBJECT_ON_PUNCH = ABILITY_TYPES.register(
			"leave_object", key -> new AbilityType<>(key, CrazyDLeaveObjectPunchInput::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<CrazyDMisshapingPunchInput>> CD_DISFIGURE_ON_PUNCH = ABILITY_TYPES.register(
			"misshape", key -> new AbilityType<>(key, CrazyDMisshapingPunchInput::new));
	
	public static final DeferredHolder<AbilityType<?>, AbilityType<CrazyDAngeloRockPunchInput>> CD_ANGELO_ROCK_ON_PUNCH = ABILITY_TYPES.register(
			"angelo_rock", key -> new AbilityType<>(key, CrazyDAngeloRockPunchInput::new));

	public static final DeferredHolder<EntityCustomEffectType<?>, EntityCustomEffectType<DriedBloodDropsEffect>> EFFECT_CD_BLOOD_DROPS = STAND_EFFECT_TYPES.register(
			"cd_blood_drops", key -> new EntityCustomEffectType<>(key, DriedBloodDropsEffect::new));

	public static final DeferredHolder<EntityCustomEffectType<?>, EntityCustomEffectType<CrazyDLeaveObjectPunchEffect>> EFFECT_CD_PUNCH_LEAVE_OBJECT = STAND_EFFECT_TYPES.register(
			"cd_punch_leave_object", key -> new EntityCustomEffectType<>(key, CrazyDLeaveObjectPunchEffect::new));

	public static final DeferredHolder<EntityCustomEffectType<?>, EntityCustomEffectType<CrazyDMisshapingPunchEffect>> EFFECT_CD_PUNCH_MISSHAPING = STAND_EFFECT_TYPES.register(
			"cd_punch_misshaping", key -> new EntityCustomEffectType<>(key, CrazyDMisshapingPunchEffect::new));

	public static final DeferredHolder<EntityCustomEffectType<?>, EntityCustomEffectType<CrazyDAngeloRockPunchEffect>> EFFECT_CD_PUNCH_ANGELO_ROCK = STAND_EFFECT_TYPES.register(
			"cd_punch_angelo_rock", key -> new EntityCustomEffectType<>(key, CrazyDAngeloRockPunchEffect::new));



	public static final DeferredHolder<AbilityType<?>, AbilityType<Ability>> _PLACEHOLDER = ABILITY_TYPES.register(
			"placeholder", key -> new AbilityType<>(key, Ability::new));
}
