package rotp.core.init.power;

import rotp.core.core.JojoRegistries;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectType;
import rotp.core.init.ModEntityCustomEffects;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.impl.stands._entitybase.StandBearingShotAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import rotp.core.impl.stands._entitybase.StandEntityGrabAbility;
import rotp.core.impl.stands._entitybase.StandEntityGrabReleaseAbility;
import rotp.core.impl.stands._entitybase.StandEntityGrabThrowAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDAnchorBlockAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDAnchorMakeAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDAngeloRockPunchEffect;
import rotp.core.impl.stands.crazydiamond.CrazyDAngeloRockPunchInput;
import rotp.core.impl.stands.crazydiamond.CrazyDBlockBulletAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDBloodCutterAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDHealAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDLeaveObjectPunchEffect;
import rotp.core.impl.stands.crazydiamond.CrazyDLeaveObjectPunchInput;
import rotp.core.impl.stands.crazydiamond.CrazyDMisshapingPunchEffect;
import rotp.core.impl.stands.crazydiamond.CrazyDMisshapingPunchInput;
import rotp.core.impl.stands.crazydiamond.CrazyDRepairItemAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDRestoreTerrainAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDRevertEntityAndBlocksAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDUncraftItemAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDiamondHeavyPunchAbility;
import rotp.core.impl.stands.crazydiamond.DriedBloodDropsEffect;
import rotp.core.impl.stands.boyiiman.BoyIIManStandPartTakenEffect;
import rotp.core.impl.stands.goldexperience.GECreatedLifeformEffect;
import rotp.core.impl.stands.goldexperience.GEHealingEffect;
import rotp.core.impl.stands.goldexperience.GEItemMarkEffect;
import rotp.core.impl.stands.goldexperience.GoldExperienceBoneMealAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceChooseLifeformAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceCreateLifeformAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceEntityLifeshotAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceHealAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceHealOtherAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceHealingItemAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceHeavyPunchAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeDetectorAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeshotPunchAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceMarkItemAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceRevertLifeformAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceToothLifeformAbility;
import rotp.core.impl.stands.hierophant.HierophantBarrierAbility;
import rotp.core.impl.stands.hierophant.HierophantEmeraldSplashAbility;
import rotp.core.impl.stands.hierophant.HierophantGrappleAbility;
import rotp.core.impl.stands.hierophant.HierophantPuppetAbility;
import rotp.core.impl.stands.hierophant.HierophantPuppetEffect;
import rotp.core.impl.stands.hierophant.HierophantStringAttackAbility;
import rotp.core.impl.stands.magiciansred.MagiciansRedCrossfireHurricaneAbility;
import rotp.core.impl.stands.magiciansred.MagiciansRedDetectorAbility;
import rotp.core.impl.stands.magiciansred.MagiciansRedFireballAbility;
import rotp.core.impl.stands.magiciansred.MagiciansRedFlameBurstAbility;
import rotp.core.impl.stands.magiciansred.MagiciansRedKickAbility;
import rotp.core.impl.stands.magiciansred.MagiciansRedRedBindAbility;
import rotp.core.impl.stands.silverchariot.SilverChariotBarrageAbility;
import rotp.core.impl.stands.silverchariot.SilverChariotDashAttackAbility;
import rotp.core.impl.stands.silverchariot.SilverChariotLightAttackAbility;
import rotp.core.impl.stands.silverchariot.SilverChariotRapierLaunchAbility;
import rotp.core.impl.stands.silverchariot.SilverChariotSweepingAttackAbility;
import rotp.core.impl.stands.silverchariot.SilverChariotTakeOffArmorAbility;
import rotp.core.impl.stands.starplatinum.HeavyPunchUppercutAbility;
import rotp.core.impl.stands.starplatinum.StarFingerAbility;
import rotp.core.impl.stands.starplatinum.StarInhaleAbility;
import rotp.core.impl.stands.starplatinum.StarPlatinumZoomAbility;
import rotp.core.impl.stands.theworld.TheWorldBarrageAbility;
import rotp.core.impl.stands.theworld.TheWorldHeavyPunchAbility;
import rotp.core.impl.stands.theworld.TheWorldKickAbility;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.impl.stands.theworld.TimeResumeAbility;
import rotp.core.impl.stands.theworld.TimeStopAbility;

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
