package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.getOffsetFromUser and actionOffsetChanged lerped a client's Stand from its previous offset to a new one
 * over 4 ticks only while doOffsetLerp was true, which an add-on Stand could set false in its constructor (the Scary
 * Monsters donor did), so that Stand stood at a new offset on the next tick. The client's lerped offset is
 * StandOffsetFromUser.getAbsoluteOffset(user, true); each row changes the offset and reads it one tick later.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandOffsetLerpSwitchGameTests {
	private static final String BATCH = "stand_offset_lerp_switch";
	private static final double EPS = 1.0E-9D;

	private StandOffsetLerpSwitchGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void aStandWithoutOffsetLerpStandsAtItsNewOffsetOnTheNextTickAsIn116(GameTestHelper helper) {
		for (boolean lerps : new boolean[] { false, true }) {
			StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
				StandEntity stand = scene.stand();
				Player user = scene.user();
				StandOffsetFromUser offset = stand.offsetFromUser;
				helper.assertTrue(offset.lerps(), "setup: a core Stand's offset does not lerp by default");
				if (!lerps) {
					helper.assertTrue(offset.noLerp() == offset && !offset.lerps(), "setup: noLerp did not switch the offset");
				}
				// a tick of its own, so the change takes its start from here
				stand.tickCount++;
				Vec3 before = offset.getAbsoluteOffset(user, false);
				offset.setOffset(new Vec3(1.5D, 0.4D, 2.5D), StandOffsetFromUser.Rotations.HEAD);
				Vec3 after = offset.getAbsoluteOffset(user, false);
				helper.assertTrue(after.distanceTo(before) > 1.0D, "setup: the new offset is too close to the old one");
				stand.tickCount++;
				Vec3 nextTick = offset.getAbsoluteOffset(user, true);
				Vec3 expected = lerps ? before.lerp(after, 1.0D / StandOffsetFromUser.LERP_TIME) : after;
				helper.assertTrue(nextTick.distanceTo(expected) < EPS, (lerps
						? "a Stand whose offset lerps (1.16 doOffsetLerp true) is a quarter of the way to its new offset on the"
								+ " next tick"
						: "1.16 put a Stand with doOffsetLerp false at its new offset on the next tick")
						+ ": expected " + expected + ", it is at " + nextTick + " (from " + before + " to " + after + ")");
				stand.tickCount += StandOffsetFromUser.LERP_TIME - 1;
				helper.assertTrue(offset.getAbsoluteOffset(user, true).distanceTo(after) < EPS,
						"the Stand is not at its new offset " + after + " after " + StandOffsetFromUser.LERP_TIME + " ticks: "
								+ offset.getAbsoluteOffset(user, true));
			});
		}
		helper.succeed();
	}
}
