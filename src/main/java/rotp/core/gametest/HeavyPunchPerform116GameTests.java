package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
			new HeavyClip(DEFAULT_ANIMS, "heavy_punch", "right_arm_bend", 0.4667F, null, null),
			new HeavyClip(DEFAULT_ANIMS, "finisher_uppercut", "right_arm", 0.45F, null, null),
			new HeavyClip(DEFAULT_ANIMS, "grab_uppercut", "right_arm", 0.5F, null, null),
			new HeavyClip(SP_ANIMS, "heavy_punch", "right_arm_bend", 0.4667F, "star_platinum", "heavy_punch"),
			new HeavyClip(SP_ANIMS, "finisher_uppercut", "right_arm", 0.45F, "star_platinum", "finisher_uppercut"),
			new HeavyClip(SP_ANIMS, "grab_uppercut", "right_arm", 0.5F, "star_platinum", "grab_uppercut"),
			new HeavyClip(TW_ANIMS, "heavy_punch", "right_arm", 0.6F, "the_world", "heavy_punch"),
			new HeavyClip(TW_ANIMS, "kick", "right_leg", 0.5F, "the_world", "kick"),
			new HeavyClip(TW_ANIMS, "ts_punch", "right_arm", 0.3F, "the_world", "ts_punch"),
			new HeavyClip(MR_ANIMS, "heavy_punch", "right_arm_bend", 0.6F, "magicians_red", "heavy_punch"),
			new HeavyClip(MR_ANIMS, "kick", "right_leg_bend", 0.65F, "magicians_red", "kick"),
			new HeavyClip(GE_ANIMS, "heavy_punch", "right_arm", 0.7F, "gold_experience", "heavy_punch"),
			new HeavyClip(GE_ANIMS, "finisher", "right_arm", 0.8F, "gold_experience", "lifeshot_punch"),
			new HeavyClip(CD_ANIMS, "heavy_punch", "right_arm_bend", 0.5417F, "crazy_diamond", "heavy_punch"),
			new HeavyClip(CD_ANIMS, "finisher", "left_arm", 0.4167F, "crazy_diamond", "finisher"),
			new HeavyClip(CD_ANIMS, "grab_heavy_punch", "right_arm", 0.5F, "crazy_diamond", "grab_heavy_punch"),
			new HeavyClip(CD_ANIMS, "grab_finisher", "right_arm", 0.5833F, "crazy_diamond", "grab_finisher"));

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
	 * one-tick perform does the same: the striking bone ends its fastest move (the impact) within a tick of PERFORM and
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
		JsonObject bone = clip.getAsJsonObject("bones").getAsJsonObject(heavy.bone());
		helper.assertTrue(bone != null && bone.has("rotation") && bone.get("rotation").isJsonObject(),
				"setup: " + heavy.clip() + " of " + heavy.asset() + " has no keyed " + heavy.bone() + " rotation");
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
