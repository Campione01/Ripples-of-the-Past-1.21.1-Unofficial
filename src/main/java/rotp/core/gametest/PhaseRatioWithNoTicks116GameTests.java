package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityTask.getPhaseCompletion was plain float math (min(1 - (ticksLeft - partialTick) / ticksTotal, 1)), with
 * no throw for a phase without ticks. The port's EntityActionInstance.getPhaseRatio threw IllegalStateException there,
 * while getAnimPhaseRatio counts such a phase as complete. An action stands in a phase with no ticks on the server while
 * it leaves it (onSetPhase of the next phase, where the charged heavy punch reads the ratio of its button charge) and a
 * client's copy decoded mid-change stands in one until its next tick. Star Platinum's punch on its Stand, put in a
 * perform and a recovery of 0 ticks, read on the server and through the copy a client decodes.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseRatioWithNoTicks116GameTests {

	private PhaseRatioWithNoTicks116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void anActionInAPhaseWithNoTicksIsCompleteInsteadOfThrowingAsIn116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		user.setNoGravity(true);
		user.moveTo(origin.x, origin.y, origin.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
		StandPower power = PowerClass.STAND.attachGet(user);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandEntity stand = null;
		try {
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "Missing the Star Platinum entity");
			stand.summonLockTicks = 0;
			stand.setNoGravity(true);
			Ability punch = power.getAbility("punch");
			helper.assertTrue(punch instanceof EntityActionType, "Fixture: no punch action");
			EntityActionInstance action = ((EntityActionType) punch).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(action instanceof StandEntityPunch && stand.getCurStandAction() == action,
					"Fixture: the punch is not the Stand's action: " + stand.getCurStandAction());

			// control: a phase with ticks keeps its ratio
			standIn(action, ActionPhase.PERFORM, 4.0F, 1);
			expect(helper, wrong, action, ActionPhase.PERFORM, 4.0F, 0.25F, "the server's punch in a perform of 4 ticks at its tick 1");
			expect(helper, wrong, clientCopy(helper, action, stand), ActionPhase.PERFORM, 4.0F, 0.25F,
					"a client's copy of the punch in a perform of 4 ticks at its tick 1");

			for (ActionPhase phase : new ActionPhase[] { ActionPhase.PERFORM, ActionPhase.RECOVERY }) {
				standIn(action, phase, 0.0F, 0);
				expect(helper, wrong, action, phase, 0.0F, 1.0F, "the server's punch in a " + phase + " of 0 ticks");
				expect(helper, wrong, clientCopy(helper, action, stand), phase, 0.0F, 1.0F,
						"a client's copy of the punch in a " + phase + " of 0 ticks");
			}
		}
		finally {
			if (stand != null) {
				LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			}
			if (type != null && power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	private static void expect(GameTestHelper helper, List<String> wrong, EntityActionInstance action, ActionPhase phase,
			float length, float ratio, String row) {
		helper.assertTrue(action.getPhase() == phase && action.getCurPhaseLength() == length,
				"Premise: " + row + " stands in " + action.getPhase() + " of " + action.getCurPhaseLength() + " ticks");
		try {
			float read = action.getPhaseRatio();
			if (read != ratio) {
				wrong.add(row + ": its phase ratio is " + read + ", expected " + ratio
						+ (length == 0.0F ? " (a phase with no ticks counts as complete, as 1.16's plain float math never threw)" : ""));
			}
		}
		catch (RuntimeException e) {
			wrong.add(row + ": its phase ratio threw " + e + "; 1.16's StandEntityTask.getPhaseCompletion never threw, and a"
					+ " phase with no ticks counts as complete (1) as getAnimPhaseRatio counts it");
		}
	}

	// the action's phase as it stands in it: setPhase would move on from a phase with no ticks at once (checkNextPhase)
	private static void standIn(EntityActionInstance action, ActionPhase phase, float length, int tick) {
		action.phasesLength.put(phase, length);
		set(action, "phase", phase);
		set(action, "curPhaseTick", tick);
		set(action, "curPhaseLength", length);
		set(action, "phasePartialTick", 0.0F);
	}

	private static void set(EntityActionInstance action, String name, Object value) {
		try {
			Field field = EntityActionInstance.class.getDeclaredField(name);
			field.setAccessible(true);
			field.set(action, value);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot set the action's " + name, e);
		}
	}

	// the action as the user's client holds it: decoded from the action the server sends it (EntityActionInstance.encode)
	private static EntityActionInstance clientCopy(GameTestHelper helper, EntityActionInstance action, StandEntity stand) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			EntityActionInstance.encode(buf, action);
			EntityActionInstance copy = EntityActionInstance.decode(helper.getLevel(), buf);
			helper.assertTrue(copy != null && copy.getClass() == action.getClass() && !buf.isReadable(),
					"Fixture: the action decodes as " + copy);
			Field performer = EntityActionInstance.class.getDeclaredField("performer");
			performer.setAccessible(true);
			performer.set(copy, stand);
			return copy;
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot give the client's copy its Stand", e);
		}
		finally {
			buf.release();
		}
	}
}
