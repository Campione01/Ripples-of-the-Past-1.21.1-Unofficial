package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityHeavyAttack kept the builder's standPerformDuration, 1 (StandEntityAction.AbstractBuilder): no core heavy
 * attack (ModStandsInit) and no add-on one set another. StandEntityTask.tick ran standPerform at the perform's phase tick 0
 * and moved to the recovery in that same tick, so the recovery, a finisher's free recovery and the end of the task came
 * right after the hit. The port's heavy punch performed for 6 ticks; the core Stands' own heavies were set back to 1 at
 * registration, while their grab variations and every add-on heavy built on the core heavy punch without a perform of
 * its own kept 5 ticks of nothing after the hit.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavyPunchPerform116GameTests {
	private HeavyPunchPerform116GameTests() {}

	private static final String[] HUMANOIDS = { "star_platinum", "the_world", "magicians_red", "gold_experience",
			"crazy_diamond" };
	// the port's grab variations of the core heavies (the grab chain is a port addition)
	private static final String[][] GRAB_VARIATIONS = { { "star_platinum", "grab_heavy_punch" },
			{ "star_platinum", "grab_uppercut" }, { "the_world", "grab_heavy_punch" }, { "the_world", "grab_kick" },
			{ "crazy_diamond", "grab_heavy_punch" }, { "crazy_diamond", "grab_finisher" } };
	// the core heavy types an add-on registers in its own moveset
	private static final List<Supplier<? extends AbilityType<?>>> CORE_HEAVY_TYPES = List.of(ModStandAbilities.HEAVY_PUNCH,
			ModStandAbilities.HEAVY_UPPERCUT, ModStandAbilities.TW_HEAVY_PUNCH, ModStandAbilities.TW_KICK,
			ModStandAbilities.MR_KICK, ModStandAbilities.CD_HEAVY_PUNCH, ModStandAbilities.CD_MISSHAPING_PUNCH,
			ModStandAbilities.GE_HEAVY_PUNCH, ModStandAbilities.GE_LIFESHOT_PUNCH);
	private static final String ADDON_HEAVY = "addon_heavy_punch";
	private static final String DEFAULT_ANIMS = "animations/stand_default.animation.json";
	private static final String SP_ANIMS =
			"stand_skins/star_platinum/assets/jojo_ripples/animations/star_platinum.animation.json";
	private static final String CD_ANIMS =
			"stand_skins/crazy_diamond/assets/jojo_ripples/animations/crazy_diamond.animation.json";
	private static final String TW_ANIMS = "stand_skins/the_world/assets/jojo_ripples/animations/the_world.animation.json";
	private static final String MR_ANIMS =
			"stand_skins/magicians_red/assets/jojo_ripples/animations/magicians_red.animation.json";
	private static final String GE_ANIMS =
			"stand_skins/gold_experience/assets/jojo_ripples/animations/gold_experience.animation.json";
	private static final float CLIP_TICK = 0.05F;

	// a clip a core heavy plays with its one-tick perform, the bone that strikes and where its strike lands; the stand and
	// ability that play it, none for the generic clips other Stands and add-ons fall back to
	private record HeavyClip(String asset, String clip, String bone, float impact, String stand, String ability) {}

	private static final List<HeavyClip> HEAVY_CLIPS = List.of(
			new HeavyClip(DEFAULT_ANIMS, "heavy_punch", "right_arm_bend", 0.6F, null, null),
			new HeavyClip(DEFAULT_ANIMS, "finisher_uppercut", "right_arm", 0.45F, null, null),
			new HeavyClip(DEFAULT_ANIMS, "grab_uppercut", "right_arm", 0.5F, null, null),
			new HeavyClip(SP_ANIMS, "heavy_punch", "right_arm_bend", 0.6F, "star_platinum", "heavy_punch"),
			new HeavyClip(SP_ANIMS, "finisher_uppercut", "right_arm", 0.45F, "star_platinum", "finisher_uppercut"),
			new HeavyClip(SP_ANIMS, "grab_uppercut", "right_arm", 0.5F, "star_platinum", "grab_uppercut"),
			new HeavyClip(TW_ANIMS, "heavy_punch", "right_arm", 0.6F, "the_world", "heavy_punch"),
			new HeavyClip(TW_ANIMS, "kick", "right_leg", 0.5F, "the_world", "kick"),
			new HeavyClip(TW_ANIMS, "ts_punch", "right_arm", 0.3F, "the_world", "ts_punch"),
			new HeavyClip(MR_ANIMS, "heavy_punch", "right_arm_bend", 0.6F, "magicians_red", "heavy_punch"),
			new HeavyClip(GE_ANIMS, "heavy_punch", "right_arm", 0.7F, "gold_experience", "heavy_punch"),
			new HeavyClip(GE_ANIMS, "finisher", "right_arm", 0.8F, "gold_experience", "lifeshot_punch"),
			new HeavyClip(CD_ANIMS, "grab_heavy_punch", "right_arm", 0.5F, "crazy_diamond", "grab_heavy_punch"),
			new HeavyClip(CD_ANIMS, "grab_finisher", "right_arm", 0.5833F, "crazy_diamond", "grab_finisher"));

	// a clip of a strike 1.16's Java model played inside the one-tick perform: the striking bone with its 1.16 pose at the
	// end of the windup and at the end of the perform, whether 1.16 held that pose over the first half of the recovery,
	// and the Stand's abilities that play the clip with that perform; for a windup 1.16 began from the idle pose, the clip
	// starts on its own idle (end) pose, and a pose 1.16 reached at a fifth of the perform is checked there
	private record PerformStrikeClip(String asset, String clip, String bone, float[] windupEnd, float[] performEnd,
			boolean holdsHalfRecovery, boolean windupFromIdle, float[] performFifth, int perform, String stand,
			String... abilities) {}

	private static final String SC_ANIMS =
			"stand_skins/silver_chariot/assets/jojo_ripples/animations/silver_chariot.animation.json";

	private static final List<PerformStrikeClip> PERFORM_STRIKE_CLIPS = List.of(
			// MagiciansRedModel :287-322: windup kickPose1 -> kickPose2, perform kickPose2 -> kickPose3, then to the idle
			new PerformStrikeClip(MR_ANIMS, "kick", "right_leg", new float[] { -105, 30, 30 },
					new float[] { -42.6168F, 9.6867F, 44.9391F }, false, false, null, 1, "magicians_red", "kick"),
			// CrazyDiamondModel :685-737: windup pose1 -> pose2, perform pose2 -> pose3 -> pose4, pose4 held to half the recovery
			new PerformStrikeClip(CD_ANIMS, "finisher", "right_arm", new float[] { 37.9264F, 14.6364F, 103.3191F },
					new float[] { -72.6819F, 35.6647F, 53.5229F }, true, false, null, 1, "crazy_diamond", "finisher",
					"block_anchor_make"),
			// SilverChariotModel :305-334: windup idle -> sweepPose1, perform sweepPose1 -> sweepPose2 (0.2) -> sweepPose3,
			// standPerformDuration 3
			new PerformStrikeClip(SC_ANIMS, "sweeping_attack", "right_arm", new float[] { 0, 45, 75 },
					new float[] { 12.5F, 0, 120 }, false, true, new float[] { 2.5F, 36, 84 }, 3, "silver_chariot",
					"sweeping_attack"));

	// a heavy clip converted from a 1.16 Java model whose recovery held the impact pose to its middle, the striking bone and
	// the clip time its impact pose is reached
	private record HeldImpactClip(String asset, String clip, String bone, float impactPoseAt) {}

	private static final List<HeldImpactClip> HELD_IMPACT_CLIPS = List.of(
			// HumanoidStandModel :577-586, the default heavy: impact -> idle eased max(2(pr - 1) + 1, 0)
			new HeldImpactClip(DEFAULT_ANIMS, "heavy_punch", "right_arm_bend", 0.6F),
			// 1.16 star_platinum.animation.json heavyPunch, the same poses: the impact held to 1.0, the recovery's middle
			new HeldImpactClip(SP_ANIMS, "heavy_punch", "right_arm_bend", 0.6F),
			new HeldImpactClip(MR_ANIMS, "heavy_punch", "right_arm_bend", 0.6F),
			// CrazyDiamondModel :669-680: pose8 held to half the recovery
			new HeldImpactClip(CD_ANIMS, "heavy_punch", "right_arm_bend", 0.5F));

	// a 1.16 head at a clip time (0, PERFORM, RECOVERY, FIFTH a fifth into the perform, MIDDLE the recovery's middle, or
	// W<f> a fraction of the windup): its root-space angles, or null where 1.16's head followed the look
	private record HeadPose(String asset, String clip, String at, float[] head) {}

	private static final List<HeadPose> HEAD_POSES = List.of(
			// SilverChariotModel :305-324: sweepPose1-3
			new HeadPose(SC_ANIMS, "sweeping_attack", "PERFORM", new float[] { 0, -45, 0 }),
			new HeadPose(SC_ANIMS, "sweeping_attack", "FIFTH", new float[] { 0, -36, 0 }),
			new HeadPose(SC_ANIMS, "sweeping_attack", "RECOVERY", new float[] { 0, 0, 0 }),
			// MagiciansRedModel :287-316: kickPose1-3
			new HeadPose(MR_ANIMS, "kick", "0", new float[] { -15, 0, 0 }),
			new HeadPose(MR_ANIMS, "kick", "PERFORM", new float[] { -30, -15, 0 }),
			new HeadPose(MR_ANIMS, "kick", "RECOVERY", new float[] { -45, -10, 0 }),
			// HumanoidStandModel :540-586: the heavy poses leave the head on the look
			new HeadPose(MR_ANIMS, "heavy_punch", "0", null),
			new HeadPose(MR_ANIMS, "heavy_punch", "W0.5", null),
			new HeadPose(MR_ANIMS, "heavy_punch", "W0.97", null),
			new HeadPose(MR_ANIMS, "heavy_punch", "PERFORM", null),
			new HeadPose(MR_ANIMS, "heavy_punch", "MIDDLE", null),
			new HeadPose(DEFAULT_ANIMS, "heavy_punch", "0", null),
			new HeadPose(DEFAULT_ANIMS, "heavy_punch", "W0.5", null),
			new HeadPose(DEFAULT_ANIMS, "heavy_punch", "W0.97", null),
			new HeadPose(DEFAULT_ANIMS, "heavy_punch", "PERFORM", null),
			new HeadPose(DEFAULT_ANIMS, "heavy_punch", "MIDDLE", null),
			// CrazyDiamondModel :685-737: heavyFinisherPose1-2, no head in pose3-4
			new HeadPose(CD_ANIMS, "finisher", "0", new float[] { 5, 0, 6.36F }),
			new HeadPose(CD_ANIMS, "finisher", "PERFORM", new float[] { -6.9176F, 15.7939F, 16.6495F }),
			new HeadPose(CD_ANIMS, "finisher", "RECOVERY", null),
			new HeadPose(CD_ANIMS, "finisher", "MIDDLE", null),
			// 1.16 star_platinum.animation.json heavyPunch: the head (a root child) on the look throughout
			new HeadPose(SP_ANIMS, "heavy_punch", "0", null),
			new HeadPose(SP_ANIMS, "heavy_punch", "W0.5", null),
			new HeadPose(SP_ANIMS, "heavy_punch", "PERFORM", null),
			new HeadPose(SP_ANIMS, "heavy_punch", "MIDDLE", null),
			// CrazyDiamondModel :565-668: heavyPunchPose1, 2, 5, 6 and 8 (no look until the return to the idle)
			new HeadPose(CD_ANIMS, "heavy_punch", "0", new float[] { 0, 385, 0 }),
			new HeadPose(CD_ANIMS, "heavy_punch", "W0.2222", new float[] { 10, 400.5F, 0 }),
			new HeadPose(CD_ANIMS, "heavy_punch", "W0.5555", new float[] { -17.5F, 300, -22.5F }),
			new HeadPose(CD_ANIMS, "heavy_punch", "W0.7777", new float[] { 15, 60, 0 }),
			new HeadPose(CD_ANIMS, "heavy_punch", "PERFORM", new float[] { 0, 0, 0 }),
			new HeadPose(CD_ANIMS, "heavy_punch", "RECOVERY", new float[] { 0, 0, 0 }),
			new HeadPose(CD_ANIMS, "heavy_punch", "MIDDLE", new float[] { 0, 0, 0 }));

	// CrazyDiamondModel :565-681, the heavy punch's windup: heavyPunchPose1-8 at these fractions of the windup, the bones
	// body (on body_rot), upperPart (on torso), the right arm and forearm and the right leg
	private static final float[] CD_HEAVY_FRACTIONS = { 0, 0.2222F, 0.3333F, 0.4444F, 0.5555F, 0.7777F, 0.8888F, 1 };
	private static final String[] CD_HEAVY_BONES = { "body_rot", "torso", "right_arm", "right_arm_bend", "right_leg" };
	private static final float[][][] CD_HEAVY_POSES = {
			{ { 7.5F, 382.5F, 0 }, { 0, 0, 0 }, { 0, 0, 80 }, { -70, -22.5F, -30 }, { -7.5F, 0, 5 } },
			{ { 30, 390, 0 }, { 0, 7.5F, 0 }, { 15, -5, 100 }, { -90, -10, -70 }, { -12.5F, 0, 10 } },
			{ { -7.5F, 337.5F, -7.5F }, { 0, 7.5F, 0 }, { 0, 0, 75 }, { 0, 90, 0 }, { 1.6667F, -1.6667F, 34.1667F } },
			{ { -21.25F, 282.5F, -13.75F }, { 0, 7.5F, 0 }, { 30, 0, 75 }, { 0, 90, 0 }, { 15.8334F, -3.3334F, 58.3334F } },
			{ { -30, 225, -15 }, { 0, 7.5F, 0 }, { 60, 0, 75 }, { -20, 60, -20 }, { 30, -5, 82.5F } },
			{ { 15, 102.5F, 0 }, { 0, 7.5F, 0 }, { 60, 0, 75 }, { -60, 0, -60 }, { -49.1667F, 10.8333F, 36.6667F } },
			{ { 22.5F, 36.25F, -7.5F }, { 0, 7.5F, 0 }, { 45, 0, 75 }, { -45, -5, -120 }, { -88.75F, 18.75F, 13.75F } },
			{ { 30, -30, -15 }, { 0, 7.5F, 0 }, { -45, -10, 75 }, { -165.665F, -4.4638F, -133.0616F }, { 15, 30, 0 } } };

	// a key of a 1.16 JSON clip as the port's clip must carry it: bone, time, vector and the easing towards it
	private record Key116(String asset, String clip, String bone, float time, float[] vector, String easing) {}

	// 1.16 star_platinum.animation.json heavyPunch (:2162-2420), played for StandPose HEAVY_ATTACK by GeckoStandAnimator;
	// body on body_rot and upperPart on torso, the pivots 1.16 gave them
	private static final List<Key116> SP_HEAVY_PUNCH_116 = List.of(
			new Key116(SP_ANIMS, "heavy_punch", "right_arm", 0, new float[] { 22.5F, 0, 60 }, "linear"),
			new Key116(SP_ANIMS, "heavy_punch", "right_arm", 0.55F, new float[] { 22.5F, 0, 60 }, "easeInQuad"),
			new Key116(SP_ANIMS, "heavy_punch", "right_arm", 0.6F, new float[] { -67.5F, 0, 90 }, "easeInCubic"),
			new Key116(SP_ANIMS, "heavy_punch", "right_arm", 1.0F, new float[] { -67.5F, 0, 90 }, "easeInCubic"),
			new Key116(SP_ANIMS, "heavy_punch", "right_arm_bend", 0.55F, new float[] { -135, 0, 0 }, "easeInQuad"),
			new Key116(SP_ANIMS, "heavy_punch", "right_arm_bend", 0.6F, new float[] { 0, 0, 0 }, "easeInCubic"),
			new Key116(SP_ANIMS, "heavy_punch", "left_arm", 0.55F, new float[] { -67.5F, 0, -90 }, "easeInQuad"),
			new Key116(SP_ANIMS, "heavy_punch", "left_arm", 0.6F, new float[] { 22.5F, 0, -60 }, "easeInCubic"),
			new Key116(SP_ANIMS, "heavy_punch", "left_arm_bend", 0.6F, new float[] { -135, 0, 0 }, "easeInCubic"),
			new Key116(SP_ANIMS, "heavy_punch", "body_rot", 0, new float[] { 0, 15, 0 }, "linear"),
			new Key116(SP_ANIMS, "heavy_punch", "body_rot", 0.55F, new float[] { 0, 26.25F, 0 }, "linear"),
			new Key116(SP_ANIMS, "heavy_punch", "body_rot", 0.6F, new float[] { 0, -26.25F, 0 }, "linear"),
			new Key116(SP_ANIMS, "heavy_punch", "body_rot", 1.0F, new float[] { 0, -26.25F, 0 }, "linear"),
			new Key116(SP_ANIMS, "heavy_punch", "torso", 0.55F, new float[] { 0, 26.25F, 0 }, "easeInQuad"),
			new Key116(SP_ANIMS, "heavy_punch", "torso", 0.6F, new float[] { 0, -26.25F, 0 }, "easeInCubic"),
			new Key116(SP_ANIMS, "heavy_punch", "torso", 1.0F, new float[] { 0, -26.25F, 0 }, "easeInCubic"));

	// a clip of HumanoidStandModel's heavyAttackAnim (:569-586) and the bone carrying upperPart's angles
	private record HumanoidHeavyClip(String asset, String clip, String upperPartBone) {}

	private static final List<HumanoidHeavyClip> HUMANOID_HEAVY_CLIPS = List.of(
			new HumanoidHeavyClip(MR_ANIMS, "heavy_punch", "torso_bend"),
			// the clip a humanoid Stand without its own heavy punch clip falls back to
			new HumanoidHeavyClip(DEFAULT_ANIMS, "heavy_punch", "torso"));

	// heavyRightStart, heavyRightBackswing, heavyRightImpact for body (body_rot), upperPart, the arms and forearms
	private static final String[] HUMANOID_HEAVY_BONES = { "body_rot", null, "right_arm", "right_arm_bend", "left_arm", "left_arm_bend" };
	private static final float[][][] HUMANOID_HEAVY_POSES = {
			{ { 0, 15, 0 }, { 0, 15, 0 }, { 22.5F, 0, 60 }, { -135, 0, 0 }, { -90, 0, -90 }, { 0, 0, 0 } },
			{ { 0, 26.25F, 0 }, { 0, 26.25F, 0 }, { 30, 0, 60 }, { -120, 0, 0 }, { -67.5F, 0, -90 }, { 0, 0, 0 } },
			{ { 0, -26.25F, 0 }, { 0, -26.25F, 0 }, { -67.5F, 0, 90 }, { 0, 0, 0 }, { 22.5F, 0, -60 }, { -135, 3.75F, 13.13F } } };

	// the port's bones from the model root to the head; head_rot carries the look
	private static final String[] HEAD_CHAIN = { "stand_pos", "body_rot", "body", "torso", "torso_bend", "head" };

	/*
	 * Every heavy punch the core Stands' movesets hold, their grab variations included, and every core heavy type as an
	 * add-on registers it (no perform of its own) stores 1.16's one-tick perform. The port's charged heavy punch and grab
	 * throw are not built on the heavy punch and keep their own.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void everyHeavyPunchPerformsForOneTickAsIn116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Set<String> heavies = new HashSet<>();
		for (String standId : HUMANOIDS) {
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			helper.assertTrue(type != null, "Missing Stand type " + standId);
			for (Map.Entry<String, Ability> entry : type.getBaseMoveset().abilities.entrySet()) {
				if (entry.getValue() instanceof StandEntityHeavyPunchAbility heavy) {
					heavies.add(standId + " " + entry.getKey());
					float perform = storedPhase(helper, heavy, ActionPhase.PERFORM);
					if (perform != 1) {
						wrong.add(standId + "'s " + entry.getKey() + " " + perform);
					}
				}
			}
		}
		for (String[] grab : GRAB_VARIATIONS) {
			helper.assertTrue(heavies.contains(grab[0] + " " + grab[1]),
					"setup: " + grab[0] + "'s " + grab[1] + " is not a heavy punch of its moveset: " + heavies);
		}
		for (Supplier<? extends AbilityType<?>> holder : CORE_HEAVY_TYPES) {
			AbilityType<?> type = holder.get();
			Ability bare = type.createInstance(new AbilityId(PowerClass.STAND, JojoMod.resLoc("star_platinum"), ADDON_HEAVY));
			helper.assertTrue(bare instanceof StandEntityHeavyPunchAbility, "setup: " + type.registryKey + " is not a heavy punch");
			float perform = storedPhase(helper, (EntityActionAbility) bare, ActionPhase.PERFORM);
			if (perform != 1) {
				wrong.add("an add-on's " + type.registryKey.getPath() + " " + perform);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16 StandEntityHeavyAttack performed for the builder's one tick (standPerformDuration 1),"
				+ " every core and add-on heavy attack alike; these heavy punches perform for longer: " + wrong);

		StandType starPlatinum = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Ability charged = starPlatinum.getBaseMoveset().getAbility("heavy_charged");
		Ability grabThrow = starPlatinum.getBaseMoveset().getAbility("grab_throw");
		helper.assertTrue(charged instanceof EntityActionAbility chargedAbility && !(charged instanceof StandEntityHeavyPunchAbility)
				&& storedPhase(helper, chargedAbility, ActionPhase.PERFORM) == 7
				&& grabThrow instanceof EntityActionAbility throwAbility && !(grabThrow instanceof StandEntityHeavyPunchAbility)
				&& storedPhase(helper, throwAbility, ActionPhase.PERFORM) == 6,
				"The port's charged heavy punch (a 7-tick lunge before the press sets it from the windup) and grab throw (6 ticks"
						+ " to the throw) keep their own perform, not the heavy punch's");
		helper.succeed();
	}

	/*
	 * Pressed, a heavy punch winds up for StandStatFormulas' windup, hits on its perform's first tick, is in its recovery at
	 * the end of that tick and ends with the formula's recovery after it: an add-on's heavy punch, the grab heavy punch,
	 * and the grab uppercut, a finisher variation whose recovery takes any other press from the end of its hit tick on.
	 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aHeavyPunchRecoversFromTheEndOfItsHitTickAsIn116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (Case kase : Case.values()) {
			try (Scene scene = new Scene(helper, kase)) {
				scene.run(wrong);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16 StandEntityTask moved a heavy attack to its recovery in the tick it hit"
				+ " (standPerformDuration 1), so the recovery, a finisher's free recovery and the end of the task followed the"
				+ " hit at once: " + wrong);
		helper.succeed();
	}

	/*
	 * 1.16's heavy clips spent one tick between their PERFORM and RECOVERY markers and landed the strike within that tick
	 * of the PERFORM marker (star_platinum heavyPunch 0.6 / 0.65, uppercut 0.45 / 0.5, the_world kick 0.5 / 0.5417,
	 * timeBreaker 0.25 / 0.3 with the punch landing on 0.3, gold_experience heavyPunch 0.7 / 0.75), so the windup led into
	 * the impact on the hit tick and the follow-through played over the recovery. Every clip a core heavy plays with its
	 * one-tick perform, but the strikes 1.16's Java models played inside that tick (PERFORM_STRIKE_CLIPS), does the same: the striking bone ends its fastest move (the impact) within a tick of PERFORM and
	 * RECOVERY follows PERFORM by at most a tick. The grab heavy clips, made for the six-tick perform, also keep their
	 * PERFORM marker and give the perform one tick of clip.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theHeavyClipsLandTheirImpactOnTheOneTickPerformAsIn116Clips(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		performSegment(helper, DEFAULT_ANIMS, "grab_uppercut", 0.4583F, wrong);
		performSegment(helper, SP_ANIMS, "grab_uppercut", 0.4583F, wrong);
		performSegment(helper, CD_ANIMS, "grab_heavy_punch", 0.5F, wrong);
		performSegment(helper, CD_ANIMS, "grab_finisher", 0.5417F, wrong);
		Map<String, JsonObject> files = new HashMap<>();
		for (HeavyClip heavy : HEAVY_CLIPS) {
			if (heavy.stand() != null) {
				Ability ability = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(heavy.stand())).getBaseMoveset()
						.getAbility(heavy.ability());
				helper.assertTrue(ability instanceof EntityActionAbility entityAbility
						&& heavy.clip().equals(entityAbility.getEntityAnim(null).name())
						&& storedPhase(helper, entityAbility, ActionPhase.PERFORM) == 1,
						"setup: " + heavy.stand() + "'s " + heavy.ability() + " does not play " + heavy.clip()
								+ " with a one-tick perform");
			}
			JsonObject clip = files.computeIfAbsent(heavy.asset(), asset -> readJson(helper, asset))
					.getAsJsonObject("animations").getAsJsonObject(heavy.clip());
			helper.assertTrue(clip != null && clip.has("timeline") && clip.has("bones"), heavy.asset() + " lost " + heavy.clip());
			float impact = fastestMoveEnd(helper, heavy, clip);
			helper.assertTrue(Math.abs(impact - heavy.impact()) < 0.0001F, "setup: " + heavy.clip() + " of " + heavy.asset()
					+ ": " + heavy.bone() + " ends its fastest move at " + impact + ", not at the impact " + heavy.impact());
			float performAt = marker(clip, "phase=PERFORM;");
			float recoveryAt = marker(clip, "phase=RECOVERY;");
			if (!(Math.abs(performAt - impact) <= CLIP_TICK + 0.0002F && recoveryAt > performAt
					&& recoveryAt - performAt <= CLIP_TICK + 0.0002F)) {
				wrong.add(heavy.clip() + " of " + heavy.asset() + ": PERFORM " + performAt + ", RECOVERY " + recoveryAt
						+ ", impact " + impact);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "A heavy clip played with the one-tick perform must land its impact within a tick of"
				+ " its PERFORM marker and give the perform at most a tick of clip, as 1.16's heavy clips did: " + wrong);
		helper.succeed();
	}

	/*
	 * Magician's Red's kick and Crazy Diamond's finisher struck inside their one perform tick in 1.16 (PosedActionAnimation
	 * with a PERFORM pose): the windup ended on the chambered pose and the perform swung to the strike's end pose. Their
	 * clips keep that phase structure: the striking bone shows 1.16's windup end pose at PERFORM and the strike's end pose
	 * at RECOVERY, and where 1.16 held that pose over the first half of the recovery the clip holds it to the middle.
	 * Silver Chariot's sweeping attack swung the whole sweep over its 3-tick perform after a windup from the idle pose.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theStrikesOf116PerformPosesPlayBetweenPerformAndRecovery(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, JsonObject> files = new HashMap<>();
		for (PerformStrikeClip strike : PERFORM_STRIKE_CLIPS) {
			for (String abilityName : strike.abilities()) {
				Ability ability = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(strike.stand())).getBaseMoveset()
						.getAbility(abilityName);
				helper.assertTrue(ability instanceof EntityActionAbility entityAbility
						&& strike.clip().equals(entityAbility.getEntityAnim(null).name())
						&& storedPhase(helper, entityAbility, ActionPhase.PERFORM) == strike.perform(),
						"setup: " + strike.stand() + "'s " + abilityName + " does not play " + strike.clip()
								+ " with a perform of " + strike.perform());
			}
			JsonObject clip = files.computeIfAbsent(strike.asset(), asset -> readJson(helper, asset))
					.getAsJsonObject("animations").getAsJsonObject(strike.clip());
			helper.assertTrue(clip != null && clip.has("timeline") && clip.has("bones"), strike.asset() + " lost " + strike.clip());
			List<Map.Entry<Float, float[]>> keys = rotationKeys(helper, clip, strike.clip() + " of " + strike.asset(),
					strike.bone());
			float performAt = marker(clip, "phase=PERFORM;");
			float recoveryAt = marker(clip, "phase=RECOVERY;");
			float middle = (recoveryAt + clip.get("animation_length").getAsFloat()) / 2;
			boolean held = !strike.holdsHalfRecovery() || near(rotationAt(keys, middle), strike.performEnd());
			boolean fromIdle = !strike.windupFromIdle()
					|| near(rotationAt(keys, 0), rotationAt(keys, clip.get("animation_length").getAsFloat()));
			float fifth = performAt + (recoveryAt - performAt) / 5;
			boolean atFifth = strike.performFifth() == null || near(rotationAt(keys, fifth), strike.performFifth());
			if (!(recoveryAt > performAt && near(rotationAt(keys, performAt), strike.windupEnd())
					&& near(rotationAt(keys, recoveryAt), strike.performEnd()) && held && fromIdle && atFifth)) {
				wrong.add(strike.clip() + " of " + strike.asset() + ": PERFORM " + performAt + ", RECOVERY " + recoveryAt + ", "
						+ strike.bone() + " at the start " + Arrays.toString(rotationAt(keys, 0)) + ", at PERFORM "
						+ Arrays.toString(rotationAt(keys, performAt)) + ", at a fifth of the perform "
						+ Arrays.toString(rotationAt(keys, fifth)) + ", at RECOVERY "
						+ Arrays.toString(rotationAt(keys, recoveryAt)) + ", at the recovery's middle "
						+ Arrays.toString(rotationAt(keys, middle)));
			}
		}
		helper.assertTrue(wrong.isEmpty(), "These strikes played inside 1.16's perform, from the windup's end pose to the"
				+ " strike's end pose (held to the recovery's middle where 1.16 held it): " + wrong);
		helper.succeed();
	}

	// the phase lengths a press stores before the action is set: the ability's own, without a performer's stat formulas
	private static float storedPhase(GameTestHelper helper, EntityActionAbility ability, ActionPhase phase) {
		EntityActionInstance action = ability.initActionOnAbilityUse(helper.getLevel(), null, null, null);
		helper.assertTrue(action != null, "setup: " + ability.name() + " made no action");
		return action.phasesLength.getFloat(phase);
	}

	private static void performSegment(GameTestHelper helper, String assetPath, String clipName, float perform,
			List<String> wrong) {
		JsonObject clip = readJson(helper, assetPath).getAsJsonObject("animations").getAsJsonObject(clipName);
		helper.assertTrue(clip != null && clip.has("timeline"), assetPath + " lost " + clipName);
		float performAt = marker(clip, "phase=PERFORM;");
		float recoveryAt = marker(clip, "phase=RECOVERY;");
		if (Math.abs(performAt - perform) > 0.0001F || Math.abs(recoveryAt - performAt - CLIP_TICK) > 0.0002F) {
			wrong.add(clipName + " of " + assetPath + ": PERFORM " + performAt + " (impact " + perform + "), RECOVERY " + recoveryAt);
		}
	}

	private static float marker(JsonObject clip, String instruction) {
		for (Map.Entry<String, JsonElement> marker : clip.getAsJsonObject("timeline").entrySet()) {
			if (marker.getValue().isJsonPrimitive() && instruction.equals(marker.getValue().getAsString().replace(" ", ""))) {
				return Float.parseFloat(marker.getKey());
			}
		}
		return Float.NaN;
	}

	// the key where the striking bone's fastest key-to-key rotation ends: the strike lands there
	private static float fastestMoveEnd(GameTestHelper helper, HeavyClip heavy, JsonObject clip) {
		List<Map.Entry<Float, float[]>> keys = rotationKeys(helper, clip, heavy.clip() + " of " + heavy.asset(), heavy.bone());
		float end = Float.NaN;
		double fastest = -1;
		for (int i = 1; i < keys.size(); i++) {
			float[] from = keys.get(i - 1).getValue();
			float[] to = keys.get(i).getValue();
			double angle = Math.sqrt(square(to[0] - from[0]) + square(to[1] - from[1]) + square(to[2] - from[2]));
			double rate = angle / (keys.get(i).getKey() - keys.get(i - 1).getKey());
			if (rate > fastest) {
				fastest = rate;
				end = keys.get(i).getKey();
			}
		}
		return end;
	}

	// the bone's linearly interpolated rotation at a clip time (the checked keys carry no easing)
	private static float[] rotationAt(List<Map.Entry<Float, float[]>> keys, float time) {
		if (time <= keys.get(0).getKey()) {
			return keys.get(0).getValue();
		}
		for (int i = 1; i < keys.size(); i++) {
			float t1 = keys.get(i).getKey();
			if (time <= t1) {
				float t0 = keys.get(i - 1).getKey();
				float f = (time - t0) / (t1 - t0);
				float[] a = keys.get(i - 1).getValue();
				float[] b = keys.get(i).getValue();
				return new float[] { a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f };
			}
		}
		return keys.get(keys.size() - 1).getValue();
	}

	/*
	 * 1.16's Java-model heavies kept the impact pose over the first half of the recovery before returning to the idle
	 * (HumanoidStandModel's heavy, Crazy Diamond's heavy punch): their clips' striking bone stays within a few degrees of
	 * its impact pose from RECOVERY to the recovery's middle.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theJavaModelHeaviesHoldTheirImpactToTheRecoverysMiddleAsIn116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, JsonObject> files = new HashMap<>();
		for (HeldImpactClip held : HELD_IMPACT_CLIPS) {
			JsonObject clip = files.computeIfAbsent(held.asset(), asset -> readJson(helper, asset))
					.getAsJsonObject("animations").getAsJsonObject(held.clip());
			helper.assertTrue(clip != null && clip.has("timeline") && clip.has("bones"), held.asset() + " lost " + held.clip());
			List<Map.Entry<Float, float[]>> keys = rotationKeys(helper, clip, held.clip() + " of " + held.asset(), held.bone());
			float recoveryAt = marker(clip, "phase=RECOVERY;");
			float middle = (recoveryAt + clip.get("animation_length").getAsFloat()) / 2;
			float[] impact = rotationAt(keys, held.impactPoseAt());
			if (!near(rotationAt(keys, recoveryAt), impact, 10) || !near(rotationAt(keys, middle), impact, 10)) {
				wrong.add(held.clip() + " of " + held.asset() + ": " + held.bone() + " impact pose " + Arrays.toString(impact)
						+ ", at RECOVERY " + recoveryAt + " " + Arrays.toString(rotationAt(keys, recoveryAt))
						+ ", at the recovery's middle " + middle + " " + Arrays.toString(rotationAt(keys, middle)));
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16's Java-model heavies held the impact pose to the middle of the recovery: " + wrong);
		helper.succeed();
	}

	/*
	 * 1.16's Java models hung the head from the model root beside the body, so a pose's head angles were root-space
	 * angles and a pose without a head left it on the look; the port's head hangs under body_rot, body, torso and
	 * torso_bend. A clip built from those poses counter-rotates its head keys by that chain: the composed rotation of the
	 * chain and the head is 1.16's head (within a degree), and head_rot adds the look exactly where 1.16's head followed
	 * it.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theHeadsOf116PosesKeepTheirRootSpaceAnglesUnderThePortsBodyChain(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, JsonObject> files = new HashMap<>();
		for (HeadPose pose : HEAD_POSES) {
			JsonObject clip = files.computeIfAbsent(pose.asset(), asset -> readJson(helper, asset))
					.getAsJsonObject("animations").getAsJsonObject(pose.clip());
			helper.assertTrue(clip != null && clip.has("timeline") && clip.has("bones"), pose.asset() + " lost " + pose.clip());
			float time = clipTime(clip, pose.at());
			double[][] composed = rotationMatrix(new float[3]);
			for (String bone : HEAD_CHAIN) {
				composed = multiply(composed, rotationMatrix(channelAt(clip, bone, time)));
			}
			boolean look = pose.head() == null;
			double off = angleBetween(composed, rotationMatrix(look ? new float[3] : pose.head()));
			Boolean lookAdded = headRotLooks(clip, time);
			if (off > 1 || lookAdded == null || lookAdded != look) {
				wrong.add(pose.clip() + " of " + pose.asset() + " at " + pose.at() + " (" + time + "): head "
						+ String.format("%.1f", off) + " degrees off 1.16's " + (look ? "look" : Arrays.toString(pose.head()))
						+ ", head_rot " + (lookAdded == null ? "half on the look" : lookAdded ? "on the look" : "off the look"));
			}
		}
		helper.assertTrue(wrong.isEmpty(), "A 1.16 pose's head kept its root-space angles, or the look where the pose had no head: " + wrong);
		helper.succeed();
	}

	/*
	 * 1.16's Star Platinum was drawn by GeckoStandAnimator, which played star_platinum.animation.json's heavyPunch for
	 * StandPose HEAVY_ATTACK, its grab-free heavy punch included: the backswing at 0.55 (easeInQuad), the snap to the
	 * impact at 0.6 (easeInCubic) on PERFORM, RECOVERY a tick later, the impact held to 1.0 and the return by 1.35. Star
	 * Platinum's heavy punch plays heavy_punch, which carries those keys, and its grab heavy punch has no clip of its own
	 * (AnimationSet's grab_heavy_punch alias takes heavy_punch).
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void starPlatinumsHeavyPunchPlays116sHeavyPunchClip(GameTestHelper helper) {
		StandType starPlatinum = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		for (String abilityName : new String[] { "heavy_punch", "grab_heavy_punch" }) {
			helper.assertTrue(starPlatinum.getBaseMoveset().getAbility(abilityName) instanceof EntityActionAbility ability
					&& abilityName.equals(ability.getEntityAnim(null).name()),
					"setup: Star Platinum's " + abilityName + " does not play " + abilityName);
		}
		JsonObject anims = readJson(helper, SP_ANIMS).getAsJsonObject("animations");
		JsonObject clip = anims.getAsJsonObject("heavy_punch");
		List<String> wrong = new ArrayList<>();
		if (anims.has("grab_heavy_punch")) {
			wrong.add("the file has a grab_heavy_punch clip of its own");
		}
		if (marker(clip, "phase=PERFORM;") != 0.6F || marker(clip, "phase=RECOVERY;") != 0.65F
				|| clip.get("animation_length").getAsFloat() != 1.35F) {
			wrong.add("markers " + clip.get("timeline") + ", length " + clip.get("animation_length"));
		}
		for (Key116 key : SP_HEAVY_PUNCH_116) {
			JsonObject bone = clip.getAsJsonObject("bones").getAsJsonObject(key.bone());
			JsonElement found = null;
			if (bone != null && bone.has("rotation") && bone.get("rotation").isJsonObject()) {
				for (Map.Entry<String, JsonElement> entry : bone.getAsJsonObject("rotation").entrySet()) {
					if (!entry.getKey().equals("vector") && Math.abs(Float.parseFloat(entry.getKey()) - key.time()) < 0.00005F) {
						found = entry.getValue();
					}
				}
			}
			String easing = found != null && found.isJsonObject() && found.getAsJsonObject().has("easing")
					? found.getAsJsonObject().get("easing").getAsString() : "linear";
			if (found == null || !near(numbers(found), key.vector()) || !easing.equals(key.easing())) {
				wrong.add(key.bone() + " at " + key.time() + ": " + found + " where 1.16 had " + Arrays.toString(key.vector())
						+ " " + key.easing());
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16's Star Platinum played its JSON heavyPunch for the heavy punch: " + wrong);
		helper.succeed();
	}

	/*
	 * 1.16's Crazy Diamond spun into its heavy punch: the windup's eight poses turn the body by the shortest path from
	 * each to the next, one continuous 412.5-degree clockwise turn (382.5 to -30) that ends on pose8, the impact, at
	 * the end of the windup; the perform keeps pose8 (PosedActionAnimation's rigid filler) and the recovery holds it to
	 * its middle before the idle. The clip shows each pose at its fraction of the windup, turns body_rot through more
	 * than 400 degrees over the windup, holds pose8 from PERFORM to the recovery's middle and ends on the idle clip.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void crazyDiamondsHeavyPunchSpinsThroughItsWindupAsIn116(GameTestHelper helper) {
		Ability ability = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond")).getBaseMoveset().getAbility("heavy_punch");
		helper.assertTrue(ability instanceof EntityActionAbility entityAbility && "heavy_punch".equals(entityAbility.getEntityAnim(null).name())
				&& storedPhase(helper, entityAbility, ActionPhase.PERFORM) == 1,
				"setup: Crazy Diamond's heavy_punch does not play heavy_punch with a one-tick perform");
		JsonObject anims = readJson(helper, CD_ANIMS).getAsJsonObject("animations");
		JsonObject clip = anims.getAsJsonObject("heavy_punch");
		JsonObject idle = anims.getAsJsonObject("idle");
		float performAt = marker(clip, "phase=PERFORM;");
		float recoveryAt = marker(clip, "phase=RECOVERY;");
		float end = clip.get("animation_length").getAsFloat();
		List<String> wrong = new ArrayList<>();
		if (!(recoveryAt > performAt && recoveryAt - performAt <= CLIP_TICK + 0.0002F)) {
			wrong.add("PERFORM " + performAt + ", RECOVERY " + recoveryAt);
		}
		for (int i = 0; i < CD_HEAVY_POSES.length; i++) {
			for (int b = 0; b < CD_HEAVY_BONES.length; b++) {
				float[] pose = CD_HEAVY_POSES[i][b];
				List<Float> times = new ArrayList<>(List.of(CD_HEAVY_FRACTIONS[i] * performAt));
				if (i == CD_HEAVY_POSES.length - 1) {
					times.addAll(List.of(recoveryAt, (recoveryAt + end) / 2));
				}
				for (float time : times) {
					float[] at = channelAt(clip, CD_HEAVY_BONES[b], time);
					if (angleBetween(rotationMatrix(at), rotationMatrix(pose)) > 0.5) {
						wrong.add(CD_HEAVY_BONES[b] + " at " + time + ": " + Arrays.toString(at) + " where pose" + (i + 1) + " had "
								+ Arrays.toString(pose));
					}
				}
			}
		}
		float turned = 0;
		List<Float> keys = new ArrayList<>();
		for (Map.Entry<String, JsonElement> key : clip.getAsJsonObject("bones").getAsJsonObject("body_rot").getAsJsonObject("rotation").entrySet()) {
			keys.add(Float.parseFloat(key.getKey()));
		}
		keys.sort(null);
		for (int i = 1; i < keys.size() && keys.get(i) <= performAt + 0.0001F; i++) {
			// two keys one float apart re-base the yaw by whole turns: no clip time lies between them
			if (keys.get(i) != Math.nextUp(keys.get(i - 1))) {
				turned += channelAt(clip, "body_rot", keys.get(i))[1] - channelAt(clip, "body_rot", keys.get(i - 1))[1];
			}
		}
		if (turned > -400) {
			wrong.add("body_rot turned " + turned + " degrees over the windup, where 1.16 turned the body -412.5");
		}
		for (String bone : new String[] { "body_rot", "torso", "head", "right_arm", "right_arm_bend", "right_leg" }) {
			if (!near(channelAt(clip, bone, end), channelAt(idle, bone, 0))) {
				wrong.add(bone + " ends on " + Arrays.toString(channelAt(clip, bone, end)) + ", the idle has "
						+ Arrays.toString(channelAt(idle, bone, 0)));
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16's Crazy Diamond turned through its eight heavy punch poses over the windup and"
				+ " held pose8 to the recovery's middle: " + wrong);
		helper.succeed();
	}

	/*
	 * 1.16's humanoid heavy (HumanoidStandModel.heavyAttackAnim, the default heavy of every Java-model humanoid Stand,
	 * Magician's Red's among them) wound up from heavyStart to heavyBackswing over 95% of the windup, eased sw * sw, and
	 * snapped to heavyImpact in its last 5%, eased sw * sw * sw; the perform kept the impact and the recovery held it to
	 * its middle (max(2(pr - 1) + 1, 0)), then returned linearly. The clips follow those curves within half a degree.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theHumanoidHeaviesWindUpAndSnapAs116sHeavyAttackAnim(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, JsonObject> files = new HashMap<>();
		float[] windup = { 0, 0.25F, 0.5F, 0.75F, 0.9F, 0.95F, 0.96F, 0.97F, 0.99F, 1 };
		for (HumanoidHeavyClip heavy : HUMANOID_HEAVY_CLIPS) {
			JsonObject clip = files.computeIfAbsent(heavy.asset(), asset -> readJson(helper, asset))
					.getAsJsonObject("animations").getAsJsonObject(heavy.clip());
			helper.assertTrue(clip != null && clip.has("timeline") && clip.has("bones"), heavy.asset() + " lost " + heavy.clip());
			float performAt = marker(clip, "phase=PERFORM;");
			float recoveryAt = marker(clip, "phase=RECOVERY;");
			float end = clip.get("animation_length").getAsFloat();
			for (int b = 0; b < HUMANOID_HEAVY_BONES.length; b++) {
				String bone = HUMANOID_HEAVY_BONES[b] != null ? HUMANOID_HEAVY_BONES[b] : heavy.upperPartBone();
				float[] start = HUMANOID_HEAVY_POSES[0][b], backswing = HUMANOID_HEAVY_POSES[1][b], impact = HUMANOID_HEAVY_POSES[2][b];
				List<Map.Entry<Float, float[]>> expected = new ArrayList<>();
				for (float f : windup) {
					float[] pose = f < 0.95F ? lerp(start, backswing, (f / 0.95F) * (f / 0.95F))
							: lerp(backswing, impact, (float) Math.pow((f - 0.95F) / 0.05F, 3));
					expected.add(Map.entry(f * performAt, pose));
				}
				float[] last = channelAt(clip, bone, end);
				expected.add(Map.entry(recoveryAt, impact));
				expected.add(Map.entry(recoveryAt + (end - recoveryAt) * 0.25F, impact));
				expected.add(Map.entry(recoveryAt + (end - recoveryAt) * 0.75F, lerp(impact, last, 0.5F)));
				for (Map.Entry<Float, float[]> at : expected) {
					float[] found = channelAt(clip, bone, at.getKey());
					if (!near(found, at.getValue(), 0.5F)) {
						wrong.add(heavy.clip() + " of " + heavy.asset() + " " + bone + " at " + at.getKey() + ": "
								+ Arrays.toString(found) + " where 1.16 had " + Arrays.toString(at.getValue()));
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16's humanoid heavy wound up over 95% of the windup and snapped to the impact in its"
				+ " last 5%: " + wrong);
		helper.succeed();
	}

	private static float[] lerp(float[] a, float[] b, float f) {
		return new float[] { a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f };
	}

	private static float clipTime(JsonObject clip, String at) {
		float performAt = marker(clip, "phase=PERFORM;");
		float recoveryAt = marker(clip, "phase=RECOVERY;");
		return switch (at) {
			case "PERFORM" -> performAt;
			case "RECOVERY" -> recoveryAt;
			case "FIFTH" -> performAt + (recoveryAt - performAt) / 5;
			case "MIDDLE" -> (recoveryAt + clip.get("animation_length").getAsFloat()) / 2;
			default -> at.startsWith("W") ? Float.parseFloat(at.substring(1)) * performAt : Float.parseFloat(at);
		};
	}

	// a bone's rotation at a clip time as the port samples it (linear, easeInQuad or easeInCubic towards a key), the
	// look queries read as 0; a bone the clip leaves out is 0
	private static float[] channelAt(JsonObject clip, String boneName, float time) {
		JsonObject bone = clip.getAsJsonObject("bones").getAsJsonObject(boneName);
		if (bone == null || !bone.has("rotation")) {
			return new float[3];
		}
		JsonElement rotation = bone.get("rotation");
		if (rotation.isJsonArray() || rotation.getAsJsonObject().has("vector")) {
			return numbers(rotation);
		}
		List<Map.Entry<Float, JsonElement>> keys = new ArrayList<>();
		for (Map.Entry<String, JsonElement> key : rotation.getAsJsonObject().entrySet()) {
			keys.add(Map.entry(Float.parseFloat(key.getKey()), key.getValue()));
		}
		keys.sort(Map.Entry.comparingByKey());
		if (time <= keys.get(0).getKey()) {
			return numbers(keys.get(0).getValue());
		}
		for (int i = 1; i < keys.size(); i++) {
			float t1 = keys.get(i).getKey();
			if (time <= t1) {
				float t0 = keys.get(i - 1).getKey();
				float f = (time - t0) / (t1 - t0);
				JsonElement to = keys.get(i).getValue();
				String easing = to.isJsonObject() && to.getAsJsonObject().has("easing")
						? to.getAsJsonObject().get("easing").getAsString() : "linear";
				f = switch (easing) {
					case "easeInQuad" -> f * f;
					case "easeInCubic" -> f * f * f;
					default -> f;
				};
				float[] a = numbers(keys.get(i - 1).getValue());
				float[] b = numbers(to);
				return new float[] { a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f };
			}
		}
		return numbers(keys.get(keys.size() - 1).getValue());
	}

	private static float[] numbers(JsonElement key) {
		JsonArray vector = vectorOf(key);
		float[] out = new float[3];
		for (int i = 0; i < 3; i++) {
			JsonElement value = vector.get(i);
			out[i] = value.getAsJsonPrimitive().isNumber() ? value.getAsFloat() : 0;
		}
		return out;
	}

	private static JsonArray vectorOf(JsonElement key) {
		if (key.isJsonObject()) {
			key = key.getAsJsonObject().has("vector") ? key.getAsJsonObject().get("vector") : key.getAsJsonObject().get("post");
		}
		return key.getAsJsonArray();
	}

	private static boolean isLook(JsonElement key) {
		return vectorOf(key).get(1).getAsJsonPrimitive().isString()
				&& vectorOf(key).get(1).getAsString().contains("query.head_y_rotation");
	}

	// whether head_rot adds the look at a clip time: true, false, or null between a look key and a key without it
	private static Boolean headRotLooks(JsonObject clip, float time) {
		JsonObject bone = clip.getAsJsonObject("bones").getAsJsonObject("head_rot");
		if (bone == null || !bone.has("rotation")) {
			return false;
		}
		JsonElement rotation = bone.get("rotation");
		if (rotation.isJsonArray() || rotation.getAsJsonObject().has("vector")) {
			return isLook(rotation);
		}
		List<Map.Entry<Float, JsonElement>> keys = new ArrayList<>();
		for (Map.Entry<String, JsonElement> key : rotation.getAsJsonObject().entrySet()) {
			keys.add(Map.entry(Float.parseFloat(key.getKey()), key.getValue()));
		}
		keys.sort(Map.Entry.comparingByKey());
		for (int i = 0; i < keys.size(); i++) {
			if (Math.abs(keys.get(i).getKey() - time) < 0.00005F) {
				return isLook(keys.get(i).getValue());
			}
			if (keys.get(i).getKey() > time) {
				boolean next = isLook(keys.get(i).getValue());
				return i == 0 || isLook(keys.get(i - 1).getValue()) == next ? next : null;
			}
		}
		return isLook(keys.get(keys.size() - 1).getValue());
	}

	// a ModelPart's rotation, Rz * Ry * Rx, from degrees
	private static double[][] rotationMatrix(float[] degrees) {
		double x = Math.toRadians(degrees[0]), y = Math.toRadians(degrees[1]), z = Math.toRadians(degrees[2]);
		double[][] rx = { { 1, 0, 0 }, { 0, Math.cos(x), -Math.sin(x) }, { 0, Math.sin(x), Math.cos(x) } };
		double[][] ry = { { Math.cos(y), 0, Math.sin(y) }, { 0, 1, 0 }, { -Math.sin(y), 0, Math.cos(y) } };
		double[][] rz = { { Math.cos(z), -Math.sin(z), 0 }, { Math.sin(z), Math.cos(z), 0 }, { 0, 0, 1 } };
		return multiply(rz, multiply(ry, rx));
	}

	private static double[][] multiply(double[][] a, double[][] b) {
		double[][] out = new double[3][3];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				for (int k = 0; k < 3; k++) {
					out[i][j] += a[i][k] * b[k][j];
				}
			}
		}
		return out;
	}

	// the angle in degrees of the rotation taking a to b
	private static double angleBetween(double[][] a, double[][] b) {
		double trace = 0;
		for (int i = 0; i < 3; i++) {
			for (int k = 0; k < 3; k++) {
				trace += a[k][i] * b[k][i];
			}
		}
		return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (trace - 1) / 2))));
	}

	private static boolean near(float[] a, float[] b) {
		return near(a, b, 0.01F);
	}

	private static boolean near(float[] a, float[] b, float degrees) {
		for (int i = 0; i < 3; i++) {
			if (Math.abs(a[i] - b[i]) > degrees) {
				return false;
			}
		}
		return true;
	}

	private static List<Map.Entry<Float, float[]>> rotationKeys(GameTestHelper helper, JsonObject clip, String clipName,
			String boneName) {
		JsonObject bone = clip.getAsJsonObject("bones").getAsJsonObject(boneName);
		helper.assertTrue(bone != null && bone.has("rotation") && bone.get("rotation").isJsonObject(),
				"setup: " + clipName + " has no keyed " + boneName + " rotation");
		List<Map.Entry<Float, float[]>> keys = new ArrayList<>();
		for (Map.Entry<String, JsonElement> key : bone.getAsJsonObject("rotation").entrySet()) {
			JsonElement value = key.getValue();
			if (value.isJsonObject()) {
				value = value.getAsJsonObject().has("vector") ? value.getAsJsonObject().get("vector") : value.getAsJsonObject().get("post");
			}
			JsonArray vector = value.getAsJsonArray();
			keys.add(Map.entry(Float.parseFloat(key.getKey()),
					new float[] { vector.get(0).getAsFloat(), vector.get(1).getAsFloat(), vector.get(2).getAsFloat() }));
		}
		keys.sort(Map.Entry.comparingByKey());
		return keys;
	}

	private static double square(float value) {
		return (double) value * value;
	}

	private static JsonObject readJson(GameTestHelper helper, String assetPath) {
		try (InputStream in = HeavyPunchPerform116GameTests.class.getResourceAsStream("/assets/jojo_ripples/" + assetPath)) {
			helper.assertTrue(in != null, "Missing asset " + assetPath);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not read " + assetPath, e);
		}
	}

	private enum Case {
		ADDON(ADDON_HEAVY, false, "an add-on's heavy punch"),
		GRAB_HEAVY("grab_heavy_punch", true, "the grab heavy punch"),
		GRAB_UPPERCUT("grab_uppercut", true, "the grab uppercut");

		final String ability;
		final boolean grab;
		final String description;

		Case(String ability, boolean grab, String description) {
			this.ability = ability;
			this.grab = grab;
			this.description = description;
		}
	}

	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final Case kase;
		private final Player user;
		private final StandType type;
		private StandPower power;
		private StandEntity stand;
		private Cow cow;

		private Scene(GameTestHelper helper, Case kase) {
			this.helper = helper;
			this.kase = kase;
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			try {
				helper.assertTrue(type != null, "Missing Stand type star_platinum");
				Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
				user.getAbilities().invulnerable = false;
				user.setNoGravity(true);
				user.noPhysics = true;
				user.moveTo(origin.x, origin.y, origin.z, 0, 0);
				helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: the Stand user was not added");
				power = PowerClass.STAND.attachGet(user);
				helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
						== StandPowerTransitions.Status.APPLIED, "setup: Star Platinum was not granted");
				power.setResolveLevel(2);
				helper.assertTrue(type.summon(user, power), "setup: Star Platinum was not summoned");
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null && stand.getUser() == user, "setup: the summoned Stand is missing");
				stand.summonLockTicks = 0;
				stand.gradualSummonWeaknessTicks = 0;
				stand.setCanFollowUser(false);
				stand.setNoGravity(true);
				stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
				stand.setYHeadRot(0);
				user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);
				user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16);
				user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);
				user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);
				user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);
				StandEntity.updateStandStatAttributes(stand, user);
				power.setStamina(power.getMaxStamina());
				stand.updateStrengthMultipliers();
				user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;

				cow = EntityType.COW.create(helper.getLevel());
				helper.assertTrue(cow != null, "setup: no cow");
				cow.setNoAi(true);
				cow.setNoGravity(true);
				cow.noPhysics = true;
				cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
				cow.setHealth(200);
				cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
				cow.moveTo(origin.x, origin.y, origin.z + 1.7, 0, 0);
				helper.assertTrue(helper.getLevel().addFreshEntity(cow), "setup: the cow was not added");
				ActionTarget aim = new ActionTarget(cow);
				LivingComponentAction.getComponent(user).entityAim.setTarget(aim);
				LivingComponentAction.getComponent(stand).entityAim.setTarget(aim);
				user.lookAt(EntityAnchorArgument.Anchor.EYES, aim.getCenterPos());
				user.setYHeadRot(user.getYRot());
				stand.lookAt(EntityAnchorArgument.Anchor.EYES, aim.getCenterPos());
				stand.setYHeadRot(stand.getYRot());
			}
			catch (RuntimeException | Error e) {
				close();
				throw e;
			}
		}

		private void run(List<String> wrong) {
			EntityActionAbility ability;
			if (kase == Case.ADDON) {
				Ability bare = ModStandAbilities.HEAVY_PUNCH.get().createInstance(
						new AbilityId(PowerClass.STAND, type.getId(), ADDON_HEAVY));
				power.getMoveset().abilities.put(ADDON_HEAVY, bare);
				ability = (EntityActionAbility) bare;
			}
			else {
				stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(cow);
				helper.assertTrue(LivingComponentGrab.getEntityGrabbedBy(stand) == cow, "setup: the Stand does not hold the cow");
				ability = (EntityActionAbility) power.getAbility(kase.ability);
			}
			helper.assertTrue(ability instanceof StandEntityHeavyPunchAbility && power.getAbility(kase.ability) == ability,
					"setup: " + kase.description + " is not a heavy punch of the moveset");
			stand.setFinisherMeter(0);
			double speed = stand.getAttackSpeed();
			int windup = StandStatFormulas.getHeavyAttackWindup(speed, 0);
			int recovery = StandStatFormulas.getHeavyAttackRecovery(speed, 0);

			// as a press sets it (EntityActionAbility.setOrBufferAction)
			EntityActionInstance action = ability.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			action.setStartedByClick(true);
			LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
			standAction.setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(standAction.getAction() == action && action.getPhase() == ActionPhase.WINDUP
					&& action.phasesLength.getFloat(ActionPhase.WINDUP) == windup
					&& action.phasesLength.getFloat(ActionPhase.RECOVERY) == recovery,
					"setup: " + kase.description + " did not start with StandStatFormulas' windup " + windup + " and recovery "
							+ recovery + ": " + action.getPhase() + " " + action.phasesLength);

			EntityActionType punch = (EntityActionType) power.getAbility("punch");
			float health = cow.getHealth();
			int hitTick = -1;
			int hurtTick = -1;
			int recoveryTick = -1;
			int endTick = -1;
			boolean freeAfterHit = false;
			for (int tick = 1; tick <= windup + 6 + recovery + 5 && endTick < 0; tick++) {
				standAction.tick();
				if (hitTick < 0 && action.punchedTarget != null) {
					hitTick = tick;
					freeAfterHit = !action.isOver() && action.canBeCancelledInto(punch);
				}
				if (hurtTick < 0 && cow.getHealth() < health) {
					hurtTick = tick;
				}
				if (recoveryTick < 0 && action.getPhase() == ActionPhase.RECOVERY) {
					recoveryTick = tick;
				}
				if (standAction.getAction() != action) {
					endTick = tick;
				}
			}
			helper.assertTrue(hitTick == windup + 1 && hurtTick == hitTick && action.punchedTarget.getMainEntity() == cow,
					"setup: " + kase.description + " did not hit the cow on its perform's first tick, " + (windup + 1) + ": hit "
							+ hitTick + ", hurt " + hurtTick + ", struck " + action.punchedTarget);
			if (recoveryTick != hitTick || endTick != hitTick + recovery) {
				wrong.add(kase.description + " hit on tick " + hitTick + " and was in its recovery from tick " + recoveryTick
						+ " and over on tick " + endTick + ", where 1.16 recovered from tick " + hitTick + " and ended on tick "
						+ (hitTick + recovery));
			}
			if (kase == Case.GRAB_UPPERCUT && !freeAfterHit) {
				wrong.add(kase.description + ", a finisher variation, took no other press at the end of its hit tick (phase "
						+ (recoveryTick == hitTick ? ActionPhase.RECOVERY : ActionPhase.PERFORM) + "), where 1.16's free"
						+ " recovery (isFreeRecovery: isFinisher) began there");
			}
		}

		@Override
		public void close() {
			if (stand != null) {
				stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null);
				LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			}
			if (power != null && power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			if (stand != null && !stand.isRemoved()) {
				stand.discard();
			}
			if (cow != null) {
				cow.discard();
			}
			user.discard();
		}
	}
}
