package rotp.core.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import rotp.core.client.entityanim.AnimationSet;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The core clips the official 1.21.1 port lacks, against the phase structure, poses and easing 1.16 drew them with, and
 * their hand-over to the Stand's idle. Clip times are read from the clips' phase markers, which the port plays phase by
 * phase (FIT_PHASE_LENGTH): a fraction of a 1.16 phase is the same fraction of the clip's phase.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreClipPhases116GameTests {
	private CoreClipPhases116GameTests() {}

	private static final String MR_ANIMS =
			"stand_skins/magicians_red/assets/jojo_ripples/animations/magicians_red.animation.json";
	private static final float[] LEVEL = { 0, 0 };
	private static final float[] LOOK = { 20, -35 };

	// a 1.16 pose a bone reaches at a clip time ("0", W/P/R<f> a fraction of the windup, perform or recovery, PERFORM,
	// RECOVERY, END), eased into by t to the given power (ModelPose's easing), or the idle clip's t=0 value (angles null)
	private record Key(String at, float[] angles, int power) {}

	private static Key key(String at, float[] angles, int power) {
		return new Key(at, angles, power);
	}

	private static float[] deg(float x, float y, float z) {
		return new float[] { x, y, z };
	}

	// HumanoidStandModel :449-480, jabRightAngles1-4 on the port's bones (body -> body_rot, upperPart -> torso_bend,
	// forearms -> *_arm_bend), in that order
	private static final String[] JAB_BONES = { "body_rot", "torso_bend", "left_arm", "left_arm_bend", "right_arm", "right_arm_bend" };
	private static final float[][][] JAB = {
			{ deg(0, 0, 0), deg(0, -15, 0), deg(-7.5F, 0, -15), deg(-100, 15, 7.5F), deg(22.5F, 0, 22.5F), deg(-105, 0, -15) },
			{ deg(0, -5, 0), deg(0, -20, 0), deg(30, 0, -15), deg(-107.5F, 15, 7.5F), deg(5.941F, 8.4211F, 69.059F), deg(-75, 0, 0) },
			{ deg(0, -12.5F, 0), deg(0, -17.5F, 0), deg(37.5F, 0, -15), deg(-115, 15, 7.5F), deg(-81.9244F, 11.0311F, 70.2661F), deg(0, 0, 0) },
			{ deg(0, -3.75F, 0), deg(0, -3.75F, 0), deg(5.63F, 0, -20.62F), deg(-103.75F, 3.75F, 13.13F), deg(5.941F, 8.4211F, 69.059F), deg(-75, 0, 0) } };

	// jabEnd of the right jab: mirrorAngles(jabRightAngles1), the other limb with y and z negated
	private static float[] jabEnd(int bone) {
		int from = switch (JAB_BONES[bone]) {
			case "left_arm" -> 4;
			case "left_arm_bend" -> 5;
			case "right_arm" -> 2;
			case "right_arm_bend" -> 3;
			default -> bone;
		};
		float[] a = JAB[0][from];
		return deg(a[0], -a[1], -a[2]);
	}

	/*
	 * 1.16's jab (HumanoidStandModel :499-536, the right jab; the port mirrors the clip for the other hand): over the
	 * windup jabStart lerped to jabArmTurn by .5, to jabImpact by .75 eased x^3 and held; over the perform the impact held
	 * to .25, turned back to jabArmTurnBack by .5 eased x^3 and lerped to jabEnd, the mirrored start, by the perform's end;
	 * the recovery held jabEnd to .75 and lerped to the idle. Every jab bone is sampled against that through the clip.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void magiciansRedsJabRunsThroughItsPosesOn116sPhaseFractions(GameTestHelper helper) {
		Map<String, JsonObject> files = new HashMap<>();
		JsonObject clip = clipOf(helper, files, MR_ANIMS, "punch");
		JsonObject idle = clipOf(helper, files, MR_ANIMS, "idle");
		List<String> wrong = new ArrayList<>();
		for (int b = 0; b < JAB_BONES.length; b++) {
			List<Key> track = List.of(key("0", JAB[0][b], 1), key("W0.5", JAB[1][b], 1), key("W0.75", JAB[2][b], 3),
					key("PERFORM", JAB[2][b], 1), key("P0.25", JAB[2][b], 1), key("P0.5", JAB[3][b], 3),
					key("RECOVERY", jabEnd(b), 1), key("R0.75", jabEnd(b), 1), key("END", null, 1));
			compare(clip, idle, JAB_BONES[b], track, 0.1F, wrong);
		}
		helper.assertTrue(wrong.isEmpty(), "Magician's Red's jab runs through 1.16's jab poses on its phase fractions: " + wrong);
		helper.succeed();
	}

	private static final String[] FLAME_CLIPS = { "flame_burst", "flameBurst", "fireball", "crossfire_hurricane" };
	private static final float[][] LOOKS = { LEVEL, LOOK, { -30, 40 } };
	private static final String[] HEAD_CHAIN = { "stand_pos", "body_rot", "body", "torso", "torso_bend", "head", "head_rot" };

	/*
	 * 1.16's FLAME_BURST_POSE (MagiciansRedModel :269-285; Flame Burst, Fireball and Crossfire Hurricane) set the head, a
	 * root child, to the look's pitch - 22.5 degrees and no yaw in every phase but the recovery, where the transition to the
	 * idle lerped it angle by angle to the look (HEAD_ROTATION); its first-person view read that head.xRot. The clips keep
	 * that head under the port's body chain and on the head bone itself, and end on the idle's own head and look (the
	 * official idle's 2.5 degrees of pitch blended in over the recovery).
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void magiciansRedsFlamePosesHoldTheHeadAt116sPitchAndLerpItToTheLook(GameTestHelper helper) {
		Map<String, JsonObject> files = new HashMap<>();
		JsonObject idle = clipOf(helper, files, MR_ANIMS, "idle");
		List<String> wrong = new ArrayList<>();
		for (String name : FLAME_CLIPS) {
			JsonObject clip = clipOf(helper, files, MR_ANIMS, name);
			for (float[] look : LOOKS) {
				for (String time : new String[] { "0", "W0.5", "PERFORM", "RECOVERY" }) {
					float t = at(clip, time);
					double off = StandClipPoses116GameTests.angleBetween(composedHead(clip, t, look),
							StandClipPoses116GameTests.rotationMatrix(deg(look[0] - 22.5F, 0, 0)));
					float headX = StandClipPoses116GameTests.rotationAt(clip, "head", t, look)[0];
					if (off > 0.5 || Math.abs(headX - (look[0] - 22.5F)) > 0.05F) {
						wrong.add(name + " at " + time + " looking " + Arrays.toString(look) + ": head " + String.format("%.1f", off)
								+ " degrees off 1.16's pitch - 22.5, the head bone's x " + headX);
					}
				}
				for (float f : new float[] { 0.25F, 0.5F, 0.75F }) {
					double off = StandClipPoses116GameTests.angleBetween(composedHead(clip, at(clip, "R" + f), look),
							StandClipPoses116GameTests.rotationMatrix(deg(look[0] - 22.5F * (1 - f), look[1] * f, 0)));
					if (off > 2.5 * f + 0.5) {
						wrong.add(name + " at R" + f + " looking " + Arrays.toString(look) + ": head " + String.format("%.1f", off)
								+ " degrees off 1.16's lerp to the look");
					}
				}
				for (String bone : new String[] { "head", "head_rot" }) {
					float[] got = StandClipPoses116GameTests.rotationAt(clip, bone, at(clip, "END"), look);
					float[] want = StandClipPoses116GameTests.rotationAt(idle, bone, 0, look);
					for (int axis = 0; axis < 3; axis++) {
						if (Math.abs(got[axis] - want[axis]) > 0.05F) {
							wrong.add(name + " ends " + bone + " on " + Arrays.toString(got) + " looking " + Arrays.toString(look)
									+ " where the idle starts on " + Arrays.toString(want));
							break;
						}
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "Magician's Red's flame poses hold the head at 1.16's pitch and lerp it to the look: " + wrong);
		helper.succeed();
	}

	// MagiciansRedModel :233-257, summon poses 1 and 2 (the head root-space); a bone a pose does not name is on the reset pose
	private static final String[] SUMMON_BONES = { "beak_upper", "beak_lower", "body_rot", "torso_bend", "left_arm", "left_arm_bend",
			"right_arm", "right_arm_bend", "left_leg", "left_leg_bend", "right_leg", "right_leg_bend" };
	private static final float[][][] MR_SUMMONS = {
			{ deg(-20, 0, 0), deg(30, 0, 0), deg(0, -22.5F, 0), null, deg(0, 0, -135), null, deg(0, 0, 135), null, deg(10, -45, 0), null,
					deg(-90, -45, 0), deg(135, 0, 0) },
			{ deg(-20, 0, 0), deg(30, 0, 0), null, null, deg(0, 135, -165), deg(0, 0, 135), deg(0, -135, 165), deg(0, 0, -135),
					deg(12.5F, 0, 7.5F), null, deg(30, 0, -7.5F), null } };
	private static final float[][] MR_SUMMON_HEADS = { deg(0, 7.5F, 0), deg(-45, 0, 0) };

	/*
	 * 1.16's LegacyStandAnimator.poseSummon reset the model, applied the summon pose and lerped the idle pose in by
	 * summonPoseRotation: nothing until tick 15 of the 20-tick summon (SUMMON_ANIMATION_LENGTH), all of it by tick 20, every
	 * bone linearly and the head angle by angle to the look (HEAD_ROTATION, its roll zeroed). The port plays a summon clip
	 * in real time for its length, so Magician's Red's run 1 s: the pose held to 0.75 s and blended to the idle by the end,
	 * the head as 1.16's (the official idle's 2.5 degrees of pitch blended in), the beak on the idle's (its rest) at the end.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void magiciansRedsSummonsHold116sPoseFifteenTicksAndBlendToTheIdleOverTheLastFive(GameTestHelper helper) {
		Map<String, JsonObject> files = new HashMap<>();
		JsonObject idle = clipOf(helper, files, MR_ANIMS, "idle");
		List<String> wrong = new ArrayList<>();
		for (int s = 0; s < MR_SUMMONS.length; s++) {
			String name = "summon" + (s + 1);
			JsonObject clip = clipOf(helper, files, MR_ANIMS, name);
			float end = clip.get("animation_length").getAsFloat();
			if (Math.abs(end - 1) > 0.0001F) {
				wrong.add(name + " runs " + end + " s where 1.16's summon ran 20 ticks");
				continue;
			}
			for (int b = 0; b < SUMMON_BONES.length; b++) {
				float[] pose = MR_SUMMONS[s][b] != null ? MR_SUMMONS[s][b] : new float[3];
				List<Key> track = List.of(key("0", pose, 1), key("0.75", pose, 1), key("END", null, 1));
				compare(clip, idle, SUMMON_BONES[b], track, 0.05F, wrong);
			}
			for (float[] look : LOOKS) {
				for (float t = 0; t <= 1.0001F; t += 0.05F) {
					float f = Math.max(0, Math.min(1, (t - 0.75F) / 0.25F));
					float[] posed = MR_SUMMON_HEADS[s];
					double off = StandClipPoses116GameTests.angleBetween(composedHead(clip, Math.min(t, end), look),
							StandClipPoses116GameTests.rotationMatrix(deg(posed[0] + (look[0] - posed[0]) * f, posed[1] + (look[1] - posed[1]) * f, 0)));
					if (off > 2.5 * f + 0.5) {
						wrong.add(name + " at " + t + " looking " + Arrays.toString(look) + ": head " + String.format("%.1f", off)
								+ " degrees off 1.16's summon head");
						break;
					}
				}
				for (String bone : new String[] { "head", "head_rot" }) {
					float[] got = StandClipPoses116GameTests.rotationAt(clip, bone, end, look);
					float[] want = StandClipPoses116GameTests.rotationAt(idle, bone, 0, look);
					if (Math.abs(got[0] - want[0]) > 0.05F || Math.abs(got[1] - want[1]) > 0.05F || Math.abs(got[2] - want[2]) > 0.05F) {
						wrong.add(name + " ends " + bone + " on " + Arrays.toString(got) + " looking " + Arrays.toString(look)
								+ " where the idle starts on " + Arrays.toString(want));
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "Magician's Red's summons hold 1.16's pose for 15 ticks and blend to the idle over the last 5: "
				+ wrong);
		helper.succeed();
	}

	/*
	 * 1.16's transitions ended Magician's Red's beak on the idle's (beakUpper and beakLower 0); the official idle keys no
	 * beak, so its beak is the geo's rest pose: the clips that move the beak end it there.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void magiciansRedsBeakEndsOnTheIdlesRest(GameTestHelper helper) {
		Map<String, JsonObject> files = new HashMap<>();
		JsonObject anims = files.computeIfAbsent(MR_ANIMS, a -> StandClipPoses116GameTests.readJson(helper, a)).getAsJsonObject("animations");
		JsonObject idle = clipOf(helper, files, MR_ANIMS, "idle");
		List<String> wrong = new ArrayList<>();
		int clips = 0;
		for (String name : anims.keySet()) {
			JsonObject clip = anims.getAsJsonObject(name);
			if (name.equals("idle") || !clip.has("bones")) {
				continue;
			}
			for (String bone : new String[] { "beak_upper", "beak_lower" }) {
				if (!clip.getAsJsonObject("bones").has(bone)) {
					continue;
				}
				clips++;
				float[] got = StandClipPoses116GameTests.rotationAt(clip, bone, clip.get("animation_length").getAsFloat(), LEVEL);
				float[] want = StandClipPoses116GameTests.rotationAt(idle, bone, 0, LEVEL);
				if (Math.abs(got[0] - want[0]) > 0.05F || Math.abs(got[1] - want[1]) > 0.05F || Math.abs(got[2] - want[2]) > 0.05F) {
					wrong.add(name + " ends " + bone + " on " + Arrays.toString(got) + " where the idle has " + Arrays.toString(want));
				}
			}
		}
		helper.assertTrue(clips >= 14, "setup: only " + clips + " beak channels found");
		helper.assertTrue(wrong.isEmpty(), "Magician's Red's clips end the beak on the idle's: " + wrong);
		helper.succeed();
	}

	private static final String CD_ANIMS =
			"stand_skins/crazy_diamond/assets/jojo_ripples/animations/crazy_diamond.animation.json";

	/*
	 * 1.16's guard (StandEntityBlock) had no recovery (StandEntityAction.Builder's standRecoveryDuration 0: the task
	 * stopped as the button was let go and the idle was drawn at once, the Java models' BLOCK transition and the JSON
	 * models' clip switch alike), and the port's guards end the same way. Crazy Diamond's 1.16 BLOCK was the humanoid one
	 * drawn over the idle pose: the legs it does not name stay in the idle's stance (the official idle's here), and
	 * rotateJoint turned the elbow joints by half the forearms (the knee joints follow the idle's).
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theGuardsEndAtOnceAndCrazyDiamondsKeepsTheIdlesLegsAndHalfBentElbowJoints(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (String stand : new String[] { "magicians_red", "star_platinum", "crazy_diamond" }) {
			Ability guard = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(stand)).getBaseMoveset().getAbility("guard");
			helper.assertTrue(guard instanceof EntityActionAbility, "setup: " + stand + " has no guard");
			EntityActionInstance action = ((EntityActionAbility) guard).initActionOnAbilityUse(helper.getLevel(), null, null, null);
			if (action.phasesLength.getFloat(ActionPhase.RECOVERY) > 0) {
				wrong.add(stand + "'s guard has a " + action.phasesLength.getFloat(ActionPhase.RECOVERY) + "-tick recovery");
			}
		}
		Map<String, JsonObject> files = new HashMap<>();
		JsonObject block = clipOf(helper, files, CD_ANIMS, "block");
		JsonObject idle = clipOf(helper, files, CD_ANIMS, "idle");
		for (float[] look : LOOKS) {
			for (String bone : new String[] { "left_leg", "left_leg_bend", "right_leg", "right_leg_bend", "left_leg_joint", "right_leg_joint" }) {
				float[] got = StandClipPoses116GameTests.rotationAt(block, bone, 0, look);
				float[] want = StandClipPoses116GameTests.rotationAt(idle, bone, 0, look);
				if (Math.abs(got[0] - want[0]) > 0.05F || Math.abs(got[1] - want[1]) > 0.05F || Math.abs(got[2] - want[2]) > 0.05F) {
					wrong.add("Crazy Diamond's block " + bone + " " + Arrays.toString(got) + " where the idle has " + Arrays.toString(want));
				}
			}
			for (String side : new String[] { "left", "right" }) {
				float[] bend = StandClipPoses116GameTests.rotationAt(block, side + "_arm_bend", 0, look);
				float[] joint = StandClipPoses116GameTests.rotationAt(block, side + "_arm_joint", 0, look);
				if (Math.abs(joint[0] - bend[0] / 2) > 0.05F || Math.abs(joint[1] - bend[1] / 2) > 0.05F || Math.abs(joint[2] - bend[2] / 2) > 0.05F) {
					wrong.add("Crazy Diamond's block " + side + "_arm_joint " + Arrays.toString(joint) + " where the forearm is " + Arrays.toString(bend));
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16's guards ended at once, and Crazy Diamond's kept the idle's legs and half-bent elbow joints: "
				+ wrong);
		helper.succeed();
	}

	private static final String SP_ANIMS =
			"stand_skins/star_platinum/assets/jojo_ripples/animations/star_platinum.animation.json";
	// Star Platinum's clips taken from 1.16's star_platinum.animation.json, the start of their last segment, and the bones
	// 1.16 left off its idle at the end (snapped at the clip switch)
	private record HandOver(String clip, float lastSegment, String... snapped) {}

	private static final List<HandOver> SP_HAND_OVERS = List.of(new HandOver("finisher_uppercut", 0.8F),
			new HandOver("uppercut", 0.8F), new HandOver("time_stop", 2.35F, "right_shoulder"),
			new HandOver("timeStop", 2.35F, "right_shoulder"));
	private static final String[] SP_STANCE = { "body_rot", "body", "torso", "torso_bend", "left_arm", "left_arm_bend", "right_arm",
			"right_arm_bend", "left_leg", "left_leg_bend", "right_leg", "right_leg_bend", "front_fabric", "back_fabric" };

	/*
	 * 1.16 animated Star Platinum from its JSON file (GeckoStandAnimator: one clip a frame, no blending); its uppercut and
	 * time stop lerped every bone into 1.16's idle stance over their last segment and the idle clip took over on that
	 * stance. The port's idle is the official one, another stance (the body turned the other way at the waist), so the
	 * last segment lerps into the official idle's start and the clip ends on it bone by bone, the head included.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void starPlatinumsClipsFrom116LerpIntoTheOfficialIdleOverTheirLastSegment(GameTestHelper helper) {
		Map<String, JsonObject> files = new HashMap<>();
		JsonObject idle = clipOf(helper, files, SP_ANIMS, "idle");
		List<String> wrong = new ArrayList<>();
		for (HandOver row : SP_HAND_OVERS) {
			JsonObject clip = clipOf(helper, files, SP_ANIMS, row.clip());
			float end = clip.get("animation_length").getAsFloat();
			List<String> snapped = List.of(row.snapped());
			for (float[] look : LOOKS) {
				for (String bone : idle.getAsJsonObject("bones").keySet()) {
					if (snapped.contains(bone)) {
						continue;
					}
					float[] got = StandClipPoses116GameTests.rotationAt(clip, bone, end, look);
					float[] want = StandClipPoses116GameTests.rotationAt(idle, bone, 0, look);
					if (Math.abs(got[0] - want[0]) > 0.05F || Math.abs(got[1] - want[1]) > 0.05F || Math.abs(got[2] - want[2]) > 0.05F) {
						wrong.add(row.clip() + " ends " + bone + " on " + Arrays.toString(got) + " looking " + Arrays.toString(look)
								+ " where the official idle starts on " + Arrays.toString(want));
					}
				}
			}
			float middle = (row.lastSegment() + end) / 2;
			for (String bone : SP_STANCE) {
				float[] from = StandClipPoses116GameTests.rotationAt(clip, bone, row.lastSegment(), LEVEL);
				float[] to = StandClipPoses116GameTests.rotationAt(idle, bone, 0, LEVEL);
				float[] got = StandClipPoses116GameTests.rotationAt(clip, bone, middle, LEVEL);
				for (int axis = 0; axis < 3; axis++) {
					if (Math.abs(got[axis] - (from[axis] + to[axis]) / 2) > 0.05F) {
						wrong.add(row.clip() + " " + bone + " half way through its last segment " + Arrays.toString(got) + ", not half way from "
								+ Arrays.toString(from) + " to the official idle's " + Arrays.toString(to));
						break;
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "Star Platinum's clips from 1.16 lerp into the official idle over their last segment: " + wrong);
		helper.succeed();
	}

	private static final String[] CORE_STANDS = { "star_platinum", "the_world", "crazy_diamond", "gold_experience", "magicians_red",
			"silver_chariot", "hierophant_green" };
	private static final String DEFAULT_ANIMS = "animations/stand_default.animation.json";

	// a core ability whose clip comes from stand_default (the humanoid set PreFrameEntityAnimCalc falls back to), and why
	private record Fallback(String stand, String ability, String clip, String reason) {}

	private static final List<Fallback> STAND_DEFAULT_FALLBACKS = List.of(new Fallback("the_world", "grab", "grab",
			"the grab is a port addition (1.16 had none, so no 1.16 pose to match), and The World keeps the port's animation"
					+ " files (D-core-67)"));

	/*
	 * 1.16 drew a Java-model humanoid's moves it had no pose of its own for with HumanoidStandModel's (the jab, the
	 * two-handed barrage, BLOCK, heavyAttackAnim for HEAVY_ATTACK and HEAVY_ATTACK_FINISHER, all by putIfAbsent), and the
	 * port's stand_default set stands in for those. Every core Stand's abilities draw a clip of the Stand's own file (its
	 * own move, converted from its 1.16 model or JSON or the official port's), so none falls back to stand_default but
	 * the listed ones: the clip each press asks for (as the action asks it, the combo index read as punch2-4, a handed
	 * clip as its base) resolves in the Stand's own file as AnimationSet does (direct, the mirrored base, the legacy
	 * aliases) before stand_default is asked.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theCoreStandsDrawTheirOwnClipsAndFallBackToStandDefaultOnlyWhereListed(GameTestHelper helper) {
		JsonObject defaults = StandClipPoses116GameTests.readJson(helper, DEFAULT_ANIMS).getAsJsonObject("animations");
		List<String> wrong = new ArrayList<>();
		List<Fallback> found = new ArrayList<>();
		int asked = 0;
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		for (String stand : CORE_STANDS) {
			JsonObject own = StandClipPoses116GameTests.readJson(helper, "stand_skins/" + stand + "/assets/jojo_ripples/animations/"
					+ stand + ".animation.json").getAsJsonObject("animations");
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(stand));
			helper.assertTrue(type != null, "setup: no Stand type " + stand);
			for (Map.Entry<String, Ability> entry : type.getBaseMoveset().abilities.entrySet()) {
				String clip = clipPlayed(helper, entry.getValue(), user);
				if (clip == null) {
					continue;
				}
				asked++;
				if (resolve(own, clip) != null) {
					continue;
				}
				String fallback = resolve(defaults, clip);
				if (fallback == null) {
					continue;
				}
				Fallback listed = STAND_DEFAULT_FALLBACKS.stream().filter(f -> f.stand().equals(stand) && f.ability().equals(entry.getKey()))
						.findFirst().orElse(null);
				if (listed == null || !listed.clip().equals(fallback)) {
					wrong.add(stand + "'s " + entry.getKey() + " draws stand_default's " + fallback + (listed == null ? ", not listed"
							: ", listed as " + listed.clip()));
				}
				else {
					found.add(listed);
				}
			}
		}
		user.discard();
		helper.assertTrue(asked > 0, "setup: no core ability asked for a clip");
		for (Fallback listed : STAND_DEFAULT_FALLBACKS) {
			if (!found.contains(listed)) {
				wrong.add(listed.stand() + "'s " + listed.ability() + " is listed as drawing stand_default's " + listed.clip() + " ("
						+ listed.reason() + ") but does not");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "The core Stands draw their own clips, stand_default only where listed: " + wrong);
		helper.succeed();
	}

	// the clip a press of the ability asks for (null for none or the idle): the combo index as the file's punch2-4, a
	// handed clip as its base
	@Nullable
	private static String clipPlayed(GameTestHelper helper, Ability ability, Player user) {
		if (!(ability instanceof EntityActionAbility action)) {
			return null;
		}
		ActionAnimIdentifier anim;
		try {
			anim = action.getEntityAnim(action.initActionOnAbilityUse(helper.getLevel(), user, user, null));
		}
		catch (RuntimeException e) {
			return null;
		}
		if (anim == null || anim.isIdle()) {
			return null;
		}
		return (anim.index() > 0 ? anim.name() + (anim.index() + 1) : anim.name()).replaceFirst("_(left|right)$", "");
	}

	// the key AnimationSet.getNamedAnim finds for a clip name in a file: the name, its mirrored base, its legacy alias chain
	@Nullable
	private static String resolve(JsonObject anims, String clip) {
		if (anims.has(clip)) {
			return clip;
		}
		String handed = AnimationSet.mirroredBaseFallback(clip, anims::has);
		return handed != null ? handed : AnimationSet.aliasedKey(clip, anims::has);
	}

	private static double[][] composedHead(JsonObject clip, float time, float[] look) {
		double[][] composed = StandClipPoses116GameTests.rotationMatrix(new float[3]);
		for (String bone : HEAD_CHAIN) {
			composed = StandClipPoses116GameTests.multiply(composed,
					StandClipPoses116GameTests.rotationMatrix(StandClipPoses116GameTests.rotationAt(clip, bone, time, look)));
		}
		return composed;
	}

	// the clip's bone against a 1.16 track sampled every 2 ms and at the track's times, at a level look
	private static void compare(JsonObject clip, JsonObject idle, String bone, List<Key> track, float tolerance, List<String> wrong) {
		float end = clip.get("animation_length").getAsFloat();
		List<Float> times = new ArrayList<>();
		for (float time = 0; time < end; time += 0.002F) {
			times.add(time);
		}
		track.forEach(k -> times.add(at(clip, k.at())));
		times.add(end);
		int off = 0;
		String first = null;
		for (float time : times) {
			float[] want = expected(clip, idle, bone, track, time);
			float[] got = StandClipPoses116GameTests.rotationAt(clip, bone, time, LEVEL);
			for (int axis = 0; axis < 3; axis++) {
				if (Math.abs(got[axis] - want[axis]) > tolerance) {
					off++;
					if (first == null) {
						first = "at " + time + " " + Arrays.toString(got) + " where 1.16 had " + Arrays.toString(want);
					}
					break;
				}
			}
		}
		if (off > 0) {
			wrong.add(bone + " off 1.16's track at " + off + " of " + times.size() + " samples, first " + first);
		}
	}

	private static float[] expected(JsonObject clip, JsonObject idle, String bone, List<Key> track, float time) {
		float[] prev = angles(idle, bone, track.get(0));
		float prevAt = at(clip, track.get(0).at());
		if (time <= prevAt) {
			return prev;
		}
		for (int i = 1; i < track.size(); i++) {
			float[] next = angles(idle, bone, track.get(i));
			float nextAt = at(clip, track.get(i).at());
			if (time <= nextAt) {
				float f = nextAt > prevAt ? (float) Math.pow((time - prevAt) / (nextAt - prevAt), track.get(i).power()) : 1;
				return deg(prev[0] + (next[0] - prev[0]) * f, prev[1] + (next[1] - prev[1]) * f, prev[2] + (next[2] - prev[2]) * f);
			}
			prev = next;
			prevAt = nextAt;
		}
		return prev;
	}

	private static float[] angles(JsonObject idle, String bone, Key key) {
		return key.angles() != null ? key.angles() : StandClipPoses116GameTests.rotationAt(idle, bone, 0, LEVEL);
	}

	// a clip time: "0", seconds, PERFORM, RECOVERY, END, or W/P/R<f> a fraction of the windup, the perform or the recovery
	static float at(JsonObject clip, String at) {
		float perform = StandClipPoses116GameTests.marker(clip, "phase=PERFORM;");
		float recovery = StandClipPoses116GameTests.marker(clip, "phase=RECOVERY;");
		float end = clip.get("animation_length").getAsFloat();
		return switch (at.charAt(0)) {
			case 'W' -> Float.parseFloat(at.substring(1)) * perform;
			case 'P' -> at.equals("PERFORM") ? perform : perform + Float.parseFloat(at.substring(1)) * (recovery - perform);
			case 'R' -> at.equals("RECOVERY") ? recovery : recovery + Float.parseFloat(at.substring(1)) * (end - recovery);
			case 'E' -> end;
			default -> Float.parseFloat(at);
		};
	}

	private static JsonObject clipOf(GameTestHelper helper, Map<String, JsonObject> files, String asset, String name) {
		JsonObject clip = files.computeIfAbsent(asset, a -> StandClipPoses116GameTests.readJson(helper, a))
				.getAsJsonObject("animations").getAsJsonObject(name);
		helper.assertTrue(clip != null && clip.has("bones"), asset + " lost " + name);
		return clip;
	}
}
