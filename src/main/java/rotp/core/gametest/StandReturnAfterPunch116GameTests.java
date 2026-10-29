package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.clearTask and updatePosition: a task that ended with its Stand 2 or more blocks from its user
 * (isCloseToUser: distanceToSqr below 4) retracted it (standRetractsAfterTask), the Stand moving toward its idle spot at its
 * movement speed (StandStatFormulas.getMovementSpeed: 0.1 + 0.05 x speed) until it was within 2 blocks, then following its
 * idle offset again; a client glided a changed offset over 4 ticks from the old one (offsetLerpMaxTicks), whatever the
 * server did meanwhile.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandReturnAfterPunch116GameTests {

	private StandReturnAfterPunch116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 120)
	public static void aStandTwoBlocksOutAfterAPunchAtNothingIsPulledBackAtItsMovementSpeedAsIn116(GameTestHelper helper) {
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		user.setNoGravity(true);
		user.noPhysics = true;
		user.moveTo(origin.x, origin.y, origin.z, 0, 0);
		user.setYHeadRot(0);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: the Stand user was not added");
		StandPower power = PowerClass.STAND.attachGet(user);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
		helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "setup: Magician's Red was not granted");
		helper.assertTrue(type.summon(user, power), "setup: Magician's Red was not summoned");
		StandEntity stand = power.getSummonedStandEntity();
		helper.assertTrue(stand != null, "setup: the summoned Stand is missing");
		stand.summonLockTicks = 0;
		stand.gradualSummonWeaknessTicks = 0;
		power.setStamina(power.getMaxStamina());
		LivingComponentAction.getComponent(user).entityAim.setTargetFromClient(ActionTarget.EMPTY, helper.getLevel());
		double speed = stand.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(Math.abs(speed - StandStatFormulas.getMovementSpeed(stand.getAttackSpeed())) < 1.0E-6,
				"setup: the Stand's movement speed " + speed + " is not 0.1 + 0.05 x " + stand.getAttackSpeed());

		int[] ticks = { 0 };
		int[] ended = { -1 };
		EntityActionInstance[] punch = { null };
		Vec3[] last = { null };
		boolean[] wasRetracted = { false };
		List<String> wrong = new ArrayList<>();
		List<String> rows = new ArrayList<>();
		boolean[] done = { false };
		helper.onEachTick(() -> {
			if (done[0]) return;
			try {
				ticks[0]++;
				if (ticks[0] == 5) {
					punch[0] = ((EntityActionType) power.getAbility("punch")).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
					punch[0].setStartedByClick(true);
					LivingComponentAction.getComponent(stand).setAction(punch[0], user, SyncType.NO_SYNC);
					return;
				}
				if (punch[0] == null) return;
				Vec3 pos = stand.position();
				double dist = Math.sqrt(stand.distanceToSqr(user));
				if (ended[0] < 0) {
					if (stand.getCurStandAction() == punch[0]) {
						last[0] = pos;
						return;
					}
					ended[0] = ticks[0];
					if (last[0].distanceTo(user.position()) < 2 || !stand.isBeingRetracted()) {
						wrong.add("ended " + last[0].distanceTo(user.position()) + " blocks out, retracted " + stand.isBeingRetracted());
					}
				}
				int after = ticks[0] - ended[0];
				rows.add("+" + after + " " + pos.subtract(user.position()) + " retracted " + stand.isBeingRetracted()
						+ " lerp " + stand.offsetFromUser.getLerpAmount());
				if (last[0] != null && wasRetracted[0] && pos.distanceTo(last[0]) > speed + 1.0E-6) {
					wrong.add("+" + after + ": a retracted Stand moved " + pos.distanceTo(last[0]) + " in a tick");
				}
				wasRetracted[0] = stand.isBeingRetracted();
				// a client's offset glide, from the punch's offset to the idle one, a quarter at a time
				float lerp = stand.offsetFromUser.getLerpAmount();
				if (after <= StandOffsetFromUser.LERP_TIME && Math.abs(lerp - (float) after / StandOffsetFromUser.LERP_TIME) > 1.0E-6) {
					wrong.add("+" + after + ": the offset glide is at " + lerp);
				}
				last[0] = pos;
				if (after == 8) {
					done[0] = true;
					Vec3 idle = stand.offsetFromUser.getPosition(user);
					if (stand.isBeingRetracted() || pos.distanceTo(idle) > 1.0E-6 || dist >= 2) {
						wrong.add("+8: not back at its idle spot " + idle + " but at " + pos + ", retracted " + stand.isBeingRetracted());
					}
					if (rows.stream().filter(row -> row.contains("retracted true")).count() < 2) {
						wrong.add("the retraction did not last the ticks a pull from 2 blocks at " + speed + " takes");
					}
					type.forceUnsummon(user, power);
					user.discard();
					helper.assertTrue(wrong.isEmpty(), "1.16 pulled a Stand ended 2 blocks out back at its movement speed ("
							+ speed + ") until it was within 2 blocks, then put it on its idle offset, a client gliding the"
							+ " offset over 4 ticks: " + wrong + "; ticks " + rows);
					helper.succeed();
				}
			}
			catch (RuntimeException | Error e) {
				done[0] = true;
				if (power.isSummoned()) type.forceUnsummon(user, power);
				user.discard();
				throw e;
			}
		});
	}
}
