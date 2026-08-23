package com.github.standobyte.jojoimpl.stands._entitybase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

public final class StandLightPunchCompatibilitySmokeTest {
	private StandLightPunchCompatibilitySmokeTest() {}

	public static void run() {
		testSinglePunchAlternatesAnimationSides();
		testExplicitComboAndFixedHandStayUnchanged();
		testPunchHandRoundTripIsDeterministic();
		testProductionGuards();
	}

	private static void testSinglePunchAlternatesAnimationSides() {
		ActionAnimIdentifier base = ActionAnimIdentifier.getOrCreate("addon_punch", false);
		ActionAnimIdentifier left = StandEntityPunchAbility.selectHandedAnimation(
				base, true, HumanoidArm.LEFT);
		ActionAnimIdentifier right = StandEntityPunchAbility.selectHandedAnimation(
				base, true, HumanoidArm.RIGHT);
		check("addon_punch_left".equals(left.name())
				&& "addon_punch_right".equals(right.name())
				&& !left.equals(right),
				"a single addon light punch must select distinct left/right animation IDs");
	}

	private static void testExplicitComboAndFixedHandStayUnchanged() {
		ActionAnimIdentifier explicitCombo = ActionAnimIdentifier.getOrCreate("punch2", false);
		check(StandEntityPunchAbility.selectHandedAnimation(
				explicitCombo, false, HumanoidArm.LEFT) == explicitCombo,
				"an explicit punch2/3/4 animation must remain unchanged");

		ActionAnimIdentifier rapier = ActionAnimIdentifier.getOrCreate("light_attack", false);
		check(StandEntityPunchAbility.selectHandedAnimation(
				rapier, false, HumanoidArm.RIGHT) == rapier,
				"a fixed main-hand rapier attack must remain on its authored animation");
	}

	private static void testPunchHandRoundTripIsDeterministic() {
		StandEntityPunch server = new StandEntityPunch(null);
		server.punchingHand = InteractionHand.OFF_HAND;
		server.handedAnimation = true;
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		try {
			server.toBuf(buffer);
			StandEntityPunch client = new StandEntityPunch(null);
			client.fromBuf(buffer);
			check(client.getPunchingHand() == InteractionHand.OFF_HAND
					&& client.usesHandedAnimation(),
					"the observer must receive the authoritative punch hand and fallback mode");
		}
		finally {
			buffer.release();
		}
	}

	private static void testProductionGuards() {
		Path root = Path.of(System.getProperty("user.dir"));
		String punch = read(root.resolve(
				"src/main/java/com/github/standobyte/jojoimpl/stands/_entitybase/"
						+ "StandEntityPunchAbility.java"));
		check(punch.contains("standPower.getMoveset().getAbility(baseName + \"2\") == null")
				&& punch.contains("usageGroup != AbilityUsageGroup.COMBAT")
				&& punch.contains("anim.index() > 0"),
				"the compatibility fallback must exclude explicit and non-light punch chains");
		check(punch.contains("stand.canAlternatePunchHands()")
				&& punch.contains("stand.applySyncedPunchingHand(punchingHand)"),
				"rapier locking and client hand application must remain explicit");

		String animations = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/entityanim/AnimationSet.java"));
		check(animations.contains("RotpAnimDefinition anim = getNamedAnim(animId);")
				&& animations.contains("getImplicitHandedAnim(animId.name(), animId.index())")
				&& animations.contains("AnimationMirror.mirror(anim.boneAnimations, 0, Float.MAX_VALUE)"),
				"implicit mirroring must run only after an explicit handed animation lookup misses");

		String stand = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/powersystem/standpower/entity/StandEntity.java"));
		check(stand.contains("return !usesSilverChariotRapierHand();")
				&& stand.contains("swingingArm = getCurrentPunchingHand();")
				&& stand.contains("entityData.set(SWING_OFF_HAND, hand == InteractionHand.MAIN_HAND)"),
				"the synced next-hand bit must resolve to the same current hand on server and client");
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
