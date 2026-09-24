package rotp.core.gametest;

import java.lang.reflect.Field;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.input.ActionInputBuffer;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Attachments a stopped entity keeps as they were. 1.16 ticked the knockback impact from the world tick with the
 * entity's own motion, which the stop left alone, and checked a Stand's buffered input in StandEntity.tick, which a
 * stopped entity skipped. The port leaves a stopped entity's motion on it, as 1.16 did, and still ticks its attachments.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrozenAttachmentsGameTests {
	private static int nextStopId = -7_310_000;

	private FrozenAttachmentsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void armedKnockbackImpactSurvivesTimeStop(GameTestHelper helper) {
		Mob target = spawn(helper, EntityType.COW);
		Mob attacker = EntityType.ZOMBIE.create(helper.getLevel());
		TimeStopState state = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
		int stop = nextStopId--;
		try {
			Vec3 flying = new Vec3(0, 0, 0.8);
			target.setDeltaMovement(flying);
			KnockbackCollisionImpact impact = KnockbackCollisionImpact.getHandler(target);
			impact.onPunchSetKnockbackImpact(flying, attacker);
			helper.assertTrue(impact.isActive() && impact.getKnockbackImpactStrength() > 0, "Could not arm the impact");

			stopTime(helper, state, stop, target);
			for (int tick = 0; tick < 3; tick++) {
				frozenTick(helper, state, target);
			}
			helper.assertTrue(impact.isActive() && impact.getKnockbackImpactStrength() > 0,
					"A stopped entity lost its knockback impact while its motion waited for time to resume");

			state.removeInstance(stop);
			state.reconcileFrozenEntity(target);
			helper.assertTrue(target.getDeltaMovement().distanceToSqr(flying) < 1E-10, "Time resumed without the motion");
			dataTick(target);
			helper.assertTrue(impact.isActive() && impact.getKnockbackImpactStrength() > 0,
					"The impact did not carry on after time resumed");
		}
		finally {
			state.removeInstance(stop);
			target.discard();
			if (attacker != null) attacker.discard();
		}
		helper.succeed();
	}

	/**
	 * A Stand user's queued punch waits out a stop and starts after it. That already held before the frozen guard in
	 * EntityActionInputState: ActionInputBuffer keeps a buffer whose move is unusable, and a stopped user's punch is.
	 * The guard adds one case: a move check cached before the stop in the same tick (the key press that queued the punch
	 * makes one) must not start the punch when the input attachment ticks ahead of Power.tick's cache reset.
	 */
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void stoppedStandUserKeepsQueuedPunchUntilTimeResumes(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		TimeStopState state = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
		int stop = nextStopId--;
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		try {
			Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "The summoned Stand is missing");
			power.setStamina(power.getMaxStamina());
			power.getCurTypeData()._lockedAbilities.remove("punch");
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);

			EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			input.inputBuffer.bufferClickInput(new AbilityId(PowerClass.STAND, type.getId(), "punch"));
			// a fresh tick's move check, made before the stop
			power.tick();
			helper.assertTrue(power.updateAvailableMoves().inMovesetAndCanBeUsed.containsKey("punch")
					&& stand.getCurStandAction() == null, "The idle Stand's punch is not usable before the stop");

			stopTime(helper, state, stop, user);
			// first stopped tick, the input attachment ahead of Power.tick (DataEventListeners ticks in identity-hash order)
			helper.assertTrue(state.interruptTickEarly(user), "A stopped user was allowed to tick");
			input.tick();
			helper.assertTrue(buffered(input) != null && stand.getCurStandAction() == null,
					"A move check from before the stop started the queued punch on a stopped user");
			dataTick(user);
			state.reconcileFrozenEntity(user);
			for (int tick = 0; tick < 3; tick++) {
				frozenTick(helper, state, user);
			}
			helper.assertTrue(buffered(input) != null && stand.getCurStandAction() == null,
					"A stopped user's queued punch was dropped or started before time resumed");

			state.removeInstance(stop);
			state.reconcileFrozenEntity(user);
			dataTick(user);
			EntityActionInstance action = stand.getCurStandAction();
			helper.assertTrue(buffered(input) == null && action != null,
					"The queued punch did not start after time resumed: " + action);
		}
		finally {
			state.removeInstance(stop);
			if (power != null && power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
		helper.succeed();
	}

	private static <T extends Mob> T spawn(GameTestHelper helper, EntityType<T> type) {
		T mob = type.create(helper.getLevel());
		helper.assertTrue(mob != null, "Could not create " + type);
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		mob.moveTo(pos.x, pos.y, pos.z, 0, 0);
		mob.setNoAi(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Could not add " + type);
		return mob;
	}

	private static void stopTime(GameTestHelper helper, TimeStopState state, int id, LivingEntity frozen) {
		helper.assertTrue(state.tryPutInstance(new TimeStopState.Instance(id, 200, 200,
				new ChunkPos(frozen.blockPosition()), 2, -1, "frozen_attachments_test")),
				"Could not stop time");
		helper.assertTrue(state.shouldFreeze(frozen) && TimeStopState.shouldFreezeOnServer(frozen), "The entity did not freeze");
	}

	// what ServerLevelTimeStopTickMixin does with a stopped entity each tick
	private static void frozenTick(GameTestHelper helper, TimeStopState state, LivingEntity entity) {
		helper.assertTrue(state.interruptTickEarly(entity), "A stopped entity was allowed to tick");
		dataTick(entity);
		state.reconcileFrozenEntity(entity);
	}

	private static void dataTick(LivingEntity entity) {
		entity.getData(ModDataAttachmentTypes.DATA_EVENT_HELPER.get()).onTick();
	}

	private static Object buffered(EntityActionInputState input) {
		try {
			Field field = ActionInputBuffer.class.getDeclaredField("buffered");
			field.setAccessible(true);
			return field.get(input.inputBuffer);
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Could not read the buffered input", error);
		}
	}
}
