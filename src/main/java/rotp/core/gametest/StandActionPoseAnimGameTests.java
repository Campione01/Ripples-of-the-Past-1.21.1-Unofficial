package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.client.entityanim.AnimationSet;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.crazydiamond.CrazyDBlockBulletAbility;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 Stand action poses the port lost: Crazy Diamond's handed Block Bullet pose (its mirrored clip only
 * keeps _left/_right keys), its handed repair reach with the arms-only user pose copy, and Silver Chariot's
 * crossed-arms guard without the rapier.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandActionPoseAnimGameTests {
	private StandActionPoseAnimGameTests() {}

	private static final String CD_ANIMS =
			"stand_skins/crazy_diamond/assets/jojo_ripples/animations/crazy_diamond.animation.json";
	private static final String SC_ANIMS =
			"stand_skins/silver_chariot/assets/jojo_ripples/animations/silver_chariot.animation.json";

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void crazyDiamondBlockBulletRequestsUserHandClip(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "crazy_diamond", "CDBlockBulletPose")) {
			StandEntity stand = f.summon();
			f.user.setMainArm(HumanoidArm.RIGHT);
			ActionAnimIdentifier got = f.anim("block_bullet");
			helper.assertTrue("block_bullet_right".equals(got.name()),
					"Right-handed Block Bullet did not request block_bullet_right" + f.state(got));
			f.user.setMainArm(HumanoidArm.LEFT);
			got = f.anim("block_bullet");
			helper.assertTrue("block_bullet_left".equals(got.name()),
					"Left-handed Block Bullet did not request block_bullet_left" + f.state(got));
			f.user.setMainArm(HumanoidArm.RIGHT);
			helper.assertTrue(CrazyDBlockBulletAbility.blockBulletAnim(HumanoidArm.RIGHT).name().equals("block_bullet_right"),
					"Block Bullet handed id helper is not keyed by the user's main arm");

			// a cleared rapier flag on another Stand must not swap its guard clip
			stand.setSilverChariotRapierVisible(false);
			got = f.anim("guard");
			helper.assertTrue("block".equals(got.name()),
					"A non-Silver Chariot guard switched to the rapier-less clip" + f.state(got));
			stand.setSilverChariotRapierVisible(true);

			JsonObject anims = readJson(helper, CD_ANIMS).getAsJsonObject("animations");
			JsonObject clip = anims.getAsJsonObject("block_bullet");
			helper.assertTrue(clip != null, "crazy_diamond block_bullet clip is missing");
			helper.assertTrue("mirror.default = LEFT;".equals(clip.getAsJsonObject("timeline").get("0.0").getAsString()),
					"block_bullet must be authored as the left-handed pose so _right mirrors it like 1.16");
			JsonObject bones = clip.getAsJsonObject("bones");
			helper.assertTrue(vec(bones, "left_arm").get(0).getAsFloat() < -80
					&& vec(bones, "right_arm").get(0).getAsFloat() > -80,
					"block_bullet no longer raises the left arm in its authored pose");
			for (String xrot : new String[] { "left_arm_xrot", "right_arm_xrot" }) {
				String pitch = bones.has(xrot) ? vec(bones, xrot).get(0).getAsString() : "";
				helper.assertTrue(pitch.contains("query.head_x_rotation") && pitch.contains("60"),
						"block_bullet " + xrot + " lost the 1.16 pitch follow capped at 60 degrees");
			}
			helper.assertTrue(anims.getAsJsonObject("blockBullet").getAsJsonObject("timeline").toString()
					.contains("mirror.default = LEFT;"), "blockBullet mirror side differs from block_bullet");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void silverChariotGuardWithoutRapierUsesCrossedArms(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "silver_chariot", "SCNoRapierGuard")) {
			StandEntity stand = f.summon();
			ActionAnimIdentifier got = f.anim("guard");
			helper.assertTrue(stand.isSilverChariotRapierVisible() && "block".equals(got.name()),
					"Silver Chariot with the rapier must keep its rapier guard clip" + f.state(got));
			stand.setSilverChariotRapierVisible(false);
			got = f.anim("guard");
			helper.assertTrue("no_rapier_block".equals(got.name()),
					"Silver Chariot without the rapier did not request no_rapier_block" + f.state(got));
			stand.setSilverChariotRapierVisible(true);

			JsonObject anims = readJson(helper, SC_ANIMS).getAsJsonObject("animations");
			JsonObject clip = anims.getAsJsonObject("no_rapier_block");
			helper.assertTrue(clip != null, "silver_chariot no_rapier_block clip is missing");
			JsonObject bones = clip.getAsJsonObject("bones");
			helper.assertTrue(!bones.has("rapier")
					&& vec(bones, "left_arm").toString().equals("[-90,0,45]")
					&& vec(bones, "right_arm").toString().equals("[-90,0,-45]")
					&& vec(bones, "left_arm_bend").get(2).getAsFloat() == 60
					&& vec(bones, "right_arm_bend").get(2).getAsFloat() == -60,
					"no_rapier_block is not the humanoid crossed-arms guard");
			helper.assertTrue(anims.getAsJsonObject("block").getAsJsonObject("bones").has("rapier"),
					"The rapier guard clip lost its rapier pose");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void mirroredClipBaseNameFallsBackToRightHand(GameTestHelper helper) {
		Set<String> both = Set.of("block_bullet_left", "block_bullet_right");
		helper.assertTrue("block_bullet_right".equals(AnimationSet.mirroredBaseFallback("block_bullet", both::contains)),
				"An unsuffixed request of a mirrored clip must play its right-hand variant");
		helper.assertTrue("bow_shoot_left".equals(AnimationSet.mirroredBaseFallback("bow_shoot", Set.of("bow_shoot_left")::contains)),
				"A clip with only a left-hand key must still resolve");
		helper.assertTrue(AnimationSet.mirroredBaseFallback("punch", Set.of("punch")::contains) == null
				&& AnimationSet.mirroredBaseFallback("punch_left", Set.of("punch_left_right")::contains) == null,
				"Unmirrored or already handed names must not be rewritten");
		helper.succeed();
	}

	// 1.16 standPose was per TS punch: Diego's took the knockback punch pose ("kick"), The World's stays ts_punch
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void tsPunchClipFollowsItsAddOnOption(GameTestHelper helper) {
		StandType theWorld = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
		helper.assertTrue(theWorld != null, "Missing Stand type the_world");
		Ability own = theWorld.getBaseMoveset().getAbility("ts_punch");
		helper.assertTrue(own instanceof TheWorldTSPunchAbility tw && "ts_punch".equals(tw.getEntityAnim(null).name()),
				"The World's own TS punch must keep its ts_punch clip");
		TheWorldTSPunchAbility punch = new TheWorldTSPunchAbility(ModStandAbilities.TW_TS_PUNCH.get(),
				new AbilityId(PowerClass.STAND, JojoMod.resLoc("ts_punch_clip_gametest"), "ts_punch"));
		helper.assertTrue(punch.setEntityAnim("kick") == punch, "setEntityAnim must chain");
		ActionAnimIdentifier kick = punch.getEntityAnim(null);
		helper.assertTrue("kick".equals(kick.name()) && !kick.isIdle(),
				"An add-on TS punch must play the clip it set, got " + kick.name());
		punch.setEntityAnim(null);
		helper.assertTrue("ts_punch".equals(punch.getEntityAnim(null).name()),
				"A null clip must restore ts_punch");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void crazyDiamondRepairReachFollowsUserArm(GameTestHelper helper) {
		// 1.16 itemFixRotations is the right-handed pose, mirrored for a left-handed user
		JsonObject anims = readJson(helper, CD_ANIMS).getAsJsonObject("animations");
		for (String name : new String[] { "repair_item", "itemFix", "uncraft" }) {
			JsonObject clip = anims.getAsJsonObject(name);
			helper.assertTrue(clip != null && clip.has("timeline")
					&& clip.getAsJsonObject("timeline").toString().contains("mirror.default = RIGHT;"),
					name + " must be tagged as the right-handed pose so left-handed users get the mirror");
			JsonObject bones = clip.getAsJsonObject("bones");
			helper.assertTrue(vec(bones, "right_arm").get(0).getAsFloat() < vec(bones, "left_arm").get(0).getAsFloat()
					&& vec(bones, "body_rot").get(1).getAsFloat() > 0,
					name + " authored pose is no longer the right-handed reach");
		}

		Set<String> mirrored = Set.of("itemFix_left", "itemFix_right");
		helper.assertTrue("itemFix_left".equals(AnimationSet.userSideKey("itemFix", HumanoidArm.LEFT, mirrored::contains))
				&& "itemFix_right".equals(AnimationSet.userSideKey("itemFix", HumanoidArm.RIGHT, mirrored::contains)),
				"A mirrored clip requested by its base name must play the user's main-arm side");
		helper.assertTrue(AnimationSet.userSideKey("itemFix", HumanoidArm.LEFT, Set.of("itemFix", "itemFix_left")::contains) == null
				&& AnimationSet.userSideKey("itemFix_right", HumanoidArm.LEFT, mirrored::contains) == null,
				"A skin's own base clip or an already handed id must not be rewritten");

		for (String name : new String[] { "repair_item", "itemFix_left", "uncraft_right", "block_bullet_right", "blockBullet" }) {
			helper.assertTrue(AnimationSet.copiesUserPoseInArmsOnly(name),
					name + " must copy the user's pose in arms-only mode like 1.16 CopyBipedUserPose");
		}
		helper.assertTrue(!AnimationSet.copiesUserPoseInArmsOnly("punch") && !AnimationSet.copiesUserPoseInArmsOnly("barrage_right"),
				"Arms-only punches must keep their own clips");
		helper.succeed();
	}

	private static JsonArray vec(JsonObject bones, String bone) {
		return bones.getAsJsonObject(bone).getAsJsonObject("rotation").getAsJsonArray("vector");
	}

	private static JsonObject readJson(GameTestHelper helper, String assetPath) {
		try (InputStream in = StandActionPoseAnimGameTests.class.getResourceAsStream("/assets/jojo_ripples/" + assetPath)) {
			helper.assertTrue(in != null, "Missing asset " + assetPath);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + assetPath, e);
		}
	}

	private static final class Fixture implements AutoCloseable {
		final GameTestHelper helper;
		final Player user;
		final StandType standType;
		StandPower power;
		StandEntity stand;

		Fixture(GameTestHelper helper, String standId, String name) {
			this.helper = helper;
			this.user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			this.standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
		}

		StandEntity summon() {
			helper.assertTrue(standType != null, "Missing Stand type");
			user.getAbilities().instabuild = false;
			Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
			user.moveTo(pos.x, pos.y, pos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant the Stand");
			power.setResolveLevel(power.getMaxResolveLevel());
			helper.assertTrue(standType.summon(user, power), "Could not summon the Stand");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Stand is missing");
			return stand;
		}

		ActionAnimIdentifier anim(String abilityName) {
			Ability ability = power.getAbility(abilityName);
			helper.assertTrue(ability instanceof EntityActionType, abilityName + " is not an entity action");
			EntityActionInstance action = ((EntityActionType) ability)
					.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			// the performer is only assigned once the action is set on the Stand, as in play
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(action.getPerformer() == stand, abilityName + " action was not set on the Stand (performer "
					+ action.getPerformer() + ", current action " + LivingComponentAction.getCurEntityAction(stand) + ")");
			ActionAnimIdentifier anim = action.getEntityAnim();
			helper.assertTrue(anim != null, abilityName + " has no action animation");
			return anim;
		}

		// the state getEntityAnim reads, for failure messages
		String state(ActionAnimIdentifier got) {
			LivingEntity standUser = stand.getUser();
			return " (got " + got + ", user main arm " + user.getMainArm() + ", Stand user "
					+ (standUser == user ? "fixture player" : String.valueOf(standUser)) + " arm "
					+ (standUser != null ? standUser.getMainArm() : null) + ", Stand " + stand.getStandType()
					+ ", rapier visible " + stand.isSilverChariotRapierVisible() + ")";
		}

		@Override
		public void close() {
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
