package com.github.standobyte.jojo.client.entityanim;

import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.entityanim.RotpAnimDefinition.AnimWithId;
import com.github.standobyte.jojo.client.entityanim.pose.AnimFramePose;
import com.github.standobyte.jojo.client.entityrender.stand.StandEntityRenderer;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.util.functions.StringUtil;
import com.mojang.datafixers.util.Pair;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * Has some stuff specific to Stands, but it can be used for other entities as well.
 */
public class AnimationSet {
	private static final Map<String, String> LEGACY_INDEXED_ANIM_ALIASES = Map.ofEntries(
			Map.entry("punch", "attack"),
			Map.entry("armsOnly_punch", "armsOnly_attack"));

	private static final Map<String, String> LEGACY_ANIM_ALIASES = Map.ofEntries(
			Map.entry("heavy_punch", "heavyPunch"),
			Map.entry("finisher_uppercut", "uppercut"),
			Map.entry("star_finger", "starFinger"),
			Map.entry("time_stop", "timeStop"),
			Map.entry("time_breaker", "timeBreaker"),
			Map.entry("ts_punch", "timeBreaker"),
			Map.entry("finisher", "finisherPunch"),
			Map.entry("grab_punch", "punch"),
			Map.entry("grab_barrage", "barrage"),
			Map.entry("grab_heavy_punch", "heavy_punch"),
			Map.entry("grab_finisher", "finisher"),
			Map.entry("grab_throw", "grab"),
			Map.entry("heavy_charged", "heavy_punch"),
			Map.entry("bearing_shot", "heavy_punch"),
			Map.entry("emerald_splash", "rangedAttack"),
			Map.entry("emerald_splash_concentrated", "emerald_splash"),
			Map.entry("grappleHook", "grapple"),
			Map.entry("grapple_entity", "grapple"),
			Map.entry("light_attack", "attack"),
			Map.entry("no_rapier_light_attack", "light_attack"),
			Map.entry("melee_barrage", "barrage"),
			Map.entry("rapier_launch", "rangedAttack"),
			Map.entry("sweeping_attack", "heavyPunch"),
			Map.entry("dash_attack", "heavyPunch"),
			Map.entry("flame_burst", "flameBurst"),
			Map.entry("fireball", "flameBurst"),
			Map.entry("crossfire_hurricane", "flameBurst"),
			Map.entry("crossfire_hurricane_special", "crossfire_hurricane"),
			Map.entry("red_bind", "redBind"),
			Map.entry("block_bullet", "blockBullet"),
			Map.entry("blood_cutter", "bloodCutter"),
			Map.entry("repair_item", "itemFix"),
			Map.entry("uncraft", "itemFix"),
			Map.entry("overdrive_barrage", "punch_barrage"),
			Map.entry("sunlight_yellow_overdrive_barrage", "syo_barrage_start"),
			Map.entry("vampirism_claw_lacerate", "vampire_claws"),
			Map.entry("pillarman_atmospheric_rift", "atmospheric_rift"),
			Map.entry("pillarman_blade_barrage", "blade_barrage"),
			Map.entry("pillarman_blade_dash_attack", "blade_dash"),
			Map.entry("pillarman_blade_slash", "blade_slash"),
			Map.entry("pillarman_divine_sandstorm", "divine_sandstorm"),
			Map.entry("pillarman_erratic_blaze_king", "erratic_blaze_king"),
			Map.entry("pillarman_evasion", "evasion"),
			Map.entry("pillarman_giant_carthwheel_prison", "giant_cartwheel_prison"),
			Map.entry("pillarman_hide_in_entity", "pillar_man_possession"),
			Map.entry("pillarman_heavy_punch", "pillar_man_punch"),
			Map.entry("pillarman_light_flash", "light_flash"),
			Map.entry("pillarman_light_flash_decoy", "light_flash_decoy"),
			Map.entry("pillarman_self_detonation", "self_detonation"),
			Map.entry("pillarman_stone_form", "stone_form_"),
			Map.entry("pillarman_unnatural_agility", "unnatural_agility"));

	private static final Map<String, List<String>> EXTRA_LEGACY_ANIM_ALIASES = Map.ofEntries(
			Map.entry("no_rapier_light_attack", List.of("attack")),
			Map.entry("grab_uppercut", List.of("finisher_uppercut", "uppercut", "heavy_punch")),
			Map.entry("grab_finisher", List.of("heavy_punch")),
			Map.entry("grab_throw", List.of("heavy_punch")),
			Map.entry("emerald_splash_concentrated", List.of("rangedAttack")),
			Map.entry("grapple_entity", List.of("grappleHook")),
			Map.entry("crossfire_hurricane_special", List.of("flameBurst")));

	public final Map<String, List<RotpAnimDefinition>> namedAnimations;
	private final Map<RotpAnimDefinition, RotpAnimDefinition> implicitHandMirrors = new IdentityHashMap<>();
	@Nullable public List<AnimFramePose> coolPoses;
	@Nullable public RotpAnimDefinition idleAnim;
//	@Nullable protected AnimWithExtras curAnim;
	
	protected AnimationSet(Map<String, List<RotpAnimDefinition>> namedAnimations) {
		this.namedAnimations = namedAnimations;
		AnimationMirror.doMirroringOnAnimSet(this.namedAnimations);
		this.idleAnim = getNamedAnim(StandEntityRenderer.IDLE_ANIM);
		this.coolPoses = allAnims().map(anim -> anim.coolPoses).filter(Objects::nonNull).flatMap(List::stream).toList();
	}
	
	protected Stream<RotpAnimDefinition> allAnims() {
		return namedAnimations.values().stream().flatMap(List::stream);
	}

	@Nullable
	public RotpAnimDefinition getNamedAnim(ActionAnimIdentifier animId) {
		RotpAnimDefinition directAnim = getNamedAnim(animId.name(), animId.index());
		if (directAnim != null) {
			return directAnim;
		}
		return getAliasedNamedAnim(animId.name(), animId.index());
	}

	@Nullable
	private RotpAnimDefinition getNamedAnim(String name, int index) {
		List<RotpAnimDefinition> anims = namedAnimations.get(name);
		if (anims == null || anims.isEmpty()) return null;
		return anims.get(index % anims.size());
	}

	@Nullable
	private RotpAnimDefinition getAliasedNamedAnim(String name, int index) {
		String indexedAlias = LEGACY_INDEXED_ANIM_ALIASES.get(name);
		if (indexedAlias != null) {
			RotpAnimDefinition anim = getNamedAnim(indexedAlias, index);
			if (anim != null) {
				return anim;
			}
		}

		String alias = LEGACY_ANIM_ALIASES.get(name);
		if (alias != null) {
			RotpAnimDefinition anim = getNamedAnim(alias, index);
			if (anim != null) {
				return anim;
			}
		}

		List<String> extraAliases = EXTRA_LEGACY_ANIM_ALIASES.get(name);
		if (extraAliases != null) {
			for (String extraAlias : extraAliases) {
				RotpAnimDefinition anim = getNamedAnim(extraAlias, index);
				if (anim != null) {
					return anim;
				}
			}
		}
		return null;
	}

	@Nullable
	public RotpAnimDefinition getNamedAnim(ActionAnimIdentifier animId, boolean armsOnly) {
		return getNamedAnim(animId, armsOnly, false);
	}

	@Nullable
	public RotpAnimDefinition getNamedAnim(
			ActionAnimIdentifier animId,
			boolean armsOnly,
			boolean implicitHandMirror) {
		if (armsOnly) {
			String armsOnlyName = "armsOnly_" + animId.name();
			RotpAnimDefinition armsOnlyAnim = getNamedAnim(armsOnlyName, animId.index());
			if (armsOnlyAnim != null) {
				return armsOnlyAnim;
			}
			RotpAnimDefinition legacyArmsOnlyAnim = getAliasedNamedAnim(armsOnlyName, animId.index());
			if (legacyArmsOnlyAnim != null) {
				return legacyArmsOnlyAnim;
			}
			if (implicitHandMirror) {
				RotpAnimDefinition mirroredArmsOnlyAnim = getImplicitHandedAnim(armsOnlyName, animId.index());
				if (mirroredArmsOnlyAnim != null) {
					return mirroredArmsOnlyAnim;
				}
			}
		}
		RotpAnimDefinition anim = getNamedAnim(animId);
		return anim != null || !implicitHandMirror
				? anim : getImplicitHandedAnim(animId.name(), animId.index());
	}

	@Nullable
	private RotpAnimDefinition getImplicitHandedAnim(String handedName, int index) {
		boolean mirror;
		String baseName;
		if (handedName.endsWith("_left")) {
			mirror = false;
			baseName = handedName.substring(0, handedName.length() - "_left".length());
		}
		else if (handedName.endsWith("_right")) {
			mirror = true;
			baseName = handedName.substring(0, handedName.length() - "_right".length());
		}
		else {
			return null;
		}

		RotpAnimDefinition baseAnim = getNamedAnim(baseName, index);
		if (baseAnim == null) {
			baseAnim = getAliasedNamedAnim(baseName, index);
		}
		if (baseAnim == null || !mirror) {
			return baseAnim;
		}
		RotpAnimDefinition resolvedBaseAnim = baseAnim;
		return implicitHandMirrors.computeIfAbsent(resolvedBaseAnim, anim -> anim.copyWithAnim(
				AnimationMirror.mirror(anim.boneAnimations, 0, Float.MAX_VALUE)));
	}
	
	@Nullable
	public RotpAnimDefinition getSummonAnim(String name, int randomLargeNum) {
		AnimWithId summonAnim = getSummonAnimWithId(name, randomLargeNum);
		return summonAnim != null ? summonAnim.anim : null;
	}

	@Nullable
	public AnimWithId getSummonAnimWithId(String name, int randomLargeNum) {
		List<RotpAnimDefinition> summonAnims = namedAnimations.get(name);
		if (summonAnims != null && !summonAnims.isEmpty()) {
			int index = Math.floorMod(randomLargeNum, summonAnims.size());
			return AnimWithId.with(ActionAnimIdentifier.getOrCreate(name, index, false), summonAnims.get(index));
		}
		return null;
	}
	
	@Nullable
	public RotpAnimDefinition getStandIdleAnim() {
		return idleAnim;
	}
	
	
	public static class Builder {
		Map<String, Int2ObjectMap<RotpAnimDefinition>> namedAnimations = new HashMap<>();
		
		public void putNamedAnim(String name, RotpAnimDefinition anim) {
			Pair<String, OptionalInt> enumeratedName = StringUtil.splitIntAtTheEnd(name);
			Int2ObjectMap<RotpAnimDefinition> anims = this.namedAnimations.computeIfAbsent(
					enumeratedName.getFirst(), __ -> new Int2ObjectArrayMap<>());
			anims.put(enumeratedName.getSecond().orElse(0), anim);
		}
		
		public boolean isEmpty() {
			return namedAnimations.isEmpty();
		}
		
		public AnimationSet build() {
			Map<String, List<RotpAnimDefinition>> anims = this.namedAnimations.entrySet().stream()
					.collect(Collectors.toMap(
							Map.Entry::getKey, 
							entry -> entry.getValue()
								.int2ObjectEntrySet().stream()
								.sorted(Comparator.comparingInt(Int2ObjectMap.Entry::getIntKey))
								.map(Int2ObjectMap.Entry::getValue)
								.toList()));
			AnimationSet animationSet = new AnimationSet(anims);
			return animationSet;
		}
	}
	
}
