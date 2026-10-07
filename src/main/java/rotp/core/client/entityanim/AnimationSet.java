package rotp.core.client.entityanim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import rotp.core.client.entityanim.RotpAnimDefinition.AnimWithId;
import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.util.functions.StringUtil;
import com.mojang.datafixers.util.Pair;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

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
			Map.entry("finisher_punch", "finisherPunch"),
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
			Map.entry("no_rapier_block", "block"),
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
			Map.entry("zombie_claw_lacerate", "vampire_claws"),
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
		String handedName = mirroredBaseFallback(animId.name(), namedAnimations::containsKey);
		if (handedName != null) {
			return getNamedAnim(handedName, animId.index());
		}
		return getAliasedNamedAnim(animId.name(), animId.index());
	}

	/**
	 * Mirroring replaces a clip's base key with its _left/_right keys, so an unsuffixed request
	 * plays the right-hand variant (then the left one) instead of dropping to idle.
	 */
	@Nullable
	public static String mirroredBaseFallback(String name, Predicate<String> hasKey) {
		if (name.endsWith("_left") || name.endsWith("_right")) {
			return null;
		}
		if (hasKey.test(name + "_right")) {
			return name + "_right";
		}
		return hasKey.test(name + "_left") ? name + "_left" : null;
	}

	/**
	 * 1.16 posed one-sided Stand actions (Crazy Diamond's repair reach) by the user's main arm: a base-name
	 * request of a mirrored clip takes that side. Null when the base clip exists or that side is missing.
	 */
	@Nullable
	public static String userSideKey(String name, HumanoidArm mainArm, Predicate<String> hasKey) {
		if (hasKey.test(name) || name.endsWith("_left") || name.endsWith("_right")) {
			return null;
		}
		String key = name + (mainArm == HumanoidArm.LEFT ? "_left" : "_right");
		return hasKey.test(key) ? key : null;
	}

	/** Clips whose arms-only pose copied the user's own biped pose in 1.16 (CopyBipedUserPose). */
	private static final Set<String> COPY_USER_POSE_ARMS_ONLY = Set.of(
			"repair_item", "itemFix", "uncraft", "block_bullet", "blockBullet");

	public static boolean copiesUserPoseInArmsOnly(String name) {
		String base = name.endsWith("_left") ? name.substring(0, name.length() - "_left".length())
				: name.endsWith("_right") ? name.substring(0, name.length() - "_right".length())
				: name;
		return COPY_USER_POSE_ARMS_ONLY.contains(base);
	}

	@Nullable
	private RotpAnimDefinition getNamedAnim(String name, int index) {
		List<RotpAnimDefinition> anims = namedAnimations.get(name);
		if (anims == null || anims.isEmpty()) return null;
		return anims.get(index % anims.size());
	}

	/**
	 * Alias targets are themselves aliased names in the 1.16 files ported add-ons still ship
	 * (heavy_charged -> heavy_punch -> heavyPunch), so a missing direct target follows the chain a few steps.
	 */
	private static final int MAX_ALIAS_DEPTH = 4;

	@Nullable
	private RotpAnimDefinition getAliasedNamedAnim(String name, int index) {
		String key = aliasedKey(name, this::hasNamedAnim);
		return key != null ? getNamedAnim(key, index) : null;
	}

	private boolean hasNamedAnim(String name) {
		List<RotpAnimDefinition> anims = namedAnimations.get(name);
		return anims != null && !anims.isEmpty();
	}

	/** First existing clip key down a name's legacy alias chain, or null. */
	@Nullable
	public static String aliasedKey(String name, Predicate<String> hasKey) {
		return aliasedKey(name, hasKey, 0);
	}

	@Nullable
	private static String aliasedKey(String name, Predicate<String> hasKey, int depth) {
		if (depth >= MAX_ALIAS_DEPTH) {
			return null;
		}
		String indexedAlias = LEGACY_INDEXED_ANIM_ALIASES.get(name);
		if (indexedAlias != null) {
			String key = keyOrAliased(indexedAlias, hasKey, depth);
			if (key != null) {
				return key;
			}
		}

		String alias = LEGACY_ANIM_ALIASES.get(name);
		if (alias != null) {
			String key = keyOrAliased(alias, hasKey, depth);
			if (key != null) {
				return key;
			}
		}

		List<String> extraAliases = EXTRA_LEGACY_ANIM_ALIASES.get(name);
		if (extraAliases != null) {
			for (String extraAlias : extraAliases) {
				String key = keyOrAliased(extraAlias, hasKey, depth);
				if (key != null) {
					return key;
				}
			}
		}
		return null;
	}

	@Nullable
	private static String keyOrAliased(String name, Predicate<String> hasKey, int depth) {
		return hasKey.test(name) ? name : aliasedKey(name, hasKey, depth + 1);
	}

	private static final String STAND_TYPE_REGISTRY_PREFIX = "stand_";
	private static final String HEAVY_PUNCH_CLIP = "heavy_punch";

	/**
	 * First clip name with a clip among the fallbacks of a Stand action whose own moveset name has none, or null:
	 * the name without the Stand's prefix, then the ability type's registry path. A finisher that still has no
	 * clip takes the heavy punch, the pose 1.16 HumanoidStandModel gave HEAVY_ATTACK_FINISHER by default.
	 */
	@Nullable
	public static String portedActionClipName(
			String name,
			@Nullable String standTypePath,
			@Nullable String abilityTypePath,
			boolean finisher,
			Predicate<String> hasClip) {
		List<String> candidates = new ArrayList<>(3);
		if (standTypePath != null && name.startsWith(standTypePath + "_")) {
			candidates.add(name.substring(standTypePath.length() + 1));
		}
		if (abilityTypePath != null) {
			candidates.add(abilityTypePath);
			// the core attack types are registered as stand_punch/stand_barrage/...; 1.16 played their clips
			// (punch, barrage, heavy_punch, ...) whatever the add-on named the action
			if (abilityTypePath.startsWith(STAND_TYPE_REGISTRY_PREFIX)) {
				candidates.add(abilityTypePath.substring(STAND_TYPE_REGISTRY_PREFIX.length()));
			}
		}
		for (String candidate : candidates) {
			if (!candidate.isEmpty() && !candidate.equals(name) && hasClip.test(candidate)) {
				return candidate;
			}
		}
		return finisher && !HEAVY_PUNCH_CLIP.equals(name) && hasClip.test(HEAVY_PUNCH_CLIP) ? HEAVY_PUNCH_CLIP : null;
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

	/** Right-handed player clips 1.16 mirrored for a left-handed player (KosmXHandsideMirrorModifier). */
	private static final Set<String> MAIN_ARM_MIRRORED_PLAYER_CLIPS = Set.of(
			"hamon_beat", "sunlight_yellow_overdrive", "scarlet_overdrive",
			"pillar_man_punch", "blade_slash", "blade_dash", "light_flash", "light_flash_decoy",
			"vampire_claws");

	/** Arm a performer's handed player clips follow; 1.16 only had these layers on players. */
	public static HumanoidArm handedClipArm(@Nullable LivingEntity performer) {
		return performer instanceof Player player ? player.getMainArm() : HumanoidArm.RIGHT;
	}

	/** Whether the clip a player action name resolves to plays mirrored for that main arm. */
	public static boolean mirrorsForMainArm(String name, HumanoidArm mainArm, Predicate<String> hasKey) {
		if (mainArm != HumanoidArm.LEFT) {
			return false;
		}
		// same resolution order as getNamedAnim(ActionAnimIdentifier)
		String key = hasKey.test(name) ? name
				: mirroredBaseFallback(name, hasKey) != null ? null
				: aliasedKey(name, hasKey);
		return key != null && MAIN_ARM_MIRRORED_PLAYER_CLIPS.contains(key);
	}

	/** A player clip for the performer's main arm: the handed clips play mirrored for left-handed players. */
	@Nullable
	public RotpAnimDefinition getPlayerAnim(ActionAnimIdentifier animId, HumanoidArm mainArm) {
		RotpAnimDefinition anim = getNamedAnim(animId);
		if (anim == null || !mirrorsForMainArm(animId.name(), mainArm, this::hasNamedAnim)) {
			return anim;
		}
		return implicitHandMirrors.computeIfAbsent(anim, base -> base.copyWithAnim(
				AnimationMirror.mirror(base.boneAnimations, 0, Float.MAX_VALUE)));
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
