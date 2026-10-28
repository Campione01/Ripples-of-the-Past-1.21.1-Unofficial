package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.client.entityanim.PreFrameEntityAnimCalc.LivingAnimState;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility.StandEntityBlock;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * S06-2: a client crashed drawing Hierophant Green's guard tapped with ticks frozen, its copy of the guard in its perform
 * with no ticks (EntityActionInstance.getAnimPhaseRatio threw on the render thread). 1.16 synced a task as it was when it
 * was sent (StandEntityTask.getPhaseCompletion was plain float math) and never stood in a phase without ticks. A tap's
 * release ends the guard through its recovery of 0 ticks right after its press.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ActionSyncSendTime116GameTests {
	private static final short GUARD_KEY = 51;

	private ActionSyncSendTime116GameTests() {}

	/*
	 * The guard's copy as a write caught mid-release decodes it: its perform, read before the release, with the 0 ticks of
	 * the recovery the release set. Drawn, it is complete, at a frame's start and in its middle, and so is the copy once the
	 * release's phase sync (phase null) leaves the 0 on it; the copy as sent, with its perform's ticks, is not.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aClientCopyInAPhaseWithNoTicksIsDrawnCompleteInsteadOfThrowing(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "GuardWithNoTicks")) {
			EntityActionInstance guard = s.pressGuard();

			EntityActionInstance whole = s.clientCopy(guard);
			LivingAnimState sent = s.pose(whole, 0.5F, "the copy as sent");
			helper.assertTrue(sent.actionPhase == ActionPhase.PERFORM && sent.phaseCompletion >= 0.0F && sent.phaseCompletion < 1.0F,
					"setup: the guard's copy as sent is drawn in its perform, not complete: " + sent.actionPhase + " "
							+ sent.phaseCompletion);

			EntityActionInstance torn = s.tornCopy(guard);
			helper.assertTrue(torn.getPhase() == ActionPhase.PERFORM && torn.getCurPhaseLength() == 0.0F,
					"setup: the torn copy is not in its perform with no ticks: " + torn.getPhase() + " " + torn.getCurPhaseLength());
			for (float partialTick : new float[] { 0.0F, 0.5F }) {
				LivingAnimState pose = s.pose(torn, partialTick, "the copy in its perform with no ticks");
				helper.assertTrue(pose.actionPhase == ActionPhase.PERFORM && pose.phaseCompletion == 1.0F,
						"a phase with no ticks is drawn complete (1), as 1.16 never stood in one: at the partial tick " + partialTick
								+ " the perform with no ticks drew " + pose.actionPhase + " " + pose.phaseCompletion);
			}

			// as the client applies the release's phase sync (ClientEntityActionSyncQueue.applyPhaseTime)
			torn.setPhase(null, 0);
			helper.assertTrue(torn.isOver() && torn.getCurPhaseLength() == 0.0F,
					"setup: the phase sync of the release did not leave the copy over with no ticks: " + torn.getPhase() + " "
							+ torn.getCurPhaseLength());
			for (float partialTick : new float[] { 0.0F, 0.5F }) {
				LivingAnimState pose = s.pose(torn, partialTick, "the copy over with no ticks");
				helper.assertTrue(pose.actionPhase == null && pose.phaseCompletion == 1.0F,
						"an action over with no ticks is drawn complete (1) until the Stand's next tick clears it: at the partial"
								+ " tick " + partialTick + " it drew " + pose.actionPhase + " " + pose.phaseCompletion);
			}
		}
		helper.succeed();
	}

	/** Hierophant Green out for a fake player whose connection keeps what the server sends it, its Stand's packets too. */
	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final FakePlayer user;
		private final RecordingConnection recording;
		private StandType type;
		private StandPower power;
		private StandEntity stand;

		private Scene(GameTestHelper helper, String name) {
			this.helper = helper;
			user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			user.getAbilities().instabuild = false;
			recording = new RecordingConnection(user);
			user.connection = recording;
			try {
				helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
				// chunks a fake player never receives: it tracks its Stand only in chunks it holds
				ChunkPos at = user.chunkPosition();
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						recording.chunkSender.dropChunk(user, new ChunkPos(at.x + dx, at.z + dz));
					}
				}
				type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("hierophant_green"));
				helper.assertTrue(type != null, "Missing Stand type hierophant_green");
				power = PowerClass.STAND.attachGet(user);
				helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
						== StandPowerTransitions.Status.APPLIED, "Could not grant Hierophant Green");
				helper.assertTrue(type.summon(user, power), "Could not summon Hierophant Green");
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "Summoned Hierophant Green is missing");
				// the summon lock skips the action tick, and StandEntity.tick, which counts it down, does not run here
				stand.summonLockTicks = 0;
				power.setStamina(power.getMaxStamina());
			}
			catch (RuntimeException | Error e) {
				close();
				throw e;
			}
		}

		// as ClAbilityInputPacket handles a held press of the guard's key (RMB)
		private EntityActionInstance pressGuard() {
			Ability guard = power.getAbility("guard");
			helper.assertTrue(guard != null, "Missing ability guard");
			AbilityConditionCheck container = resolve(guard);
			helper.assertTrue(container != null && AbilityInput.withConditionCheck(container, user, InputMethod.HOLD),
					"setup: the guard was refused");
			BufferingState state = BufferingState.clickCanBuffer();
			AbilityInput.keyPress(GUARD_KEY, container.ability, user, null, InputMethod.HOLD, 0, state, guard.getAbilityId());
			EntityActionInstance action = LivingComponentAction.getComponent(stand).getAction();
			helper.assertTrue(state.isActionSuccess && action instanceof StandEntityBlock && action.getPhase() == ActionPhase.PERFORM
					&& action.getCurPhaseLength() > 0 && action.phasesLength.getFloat(ActionPhase.RECOVERY) == 0.0F,
					"setup: the held guard is not in its perform with a recovery of 0 ticks: started " + state.isActionSuccess
							+ ", the Stand's action " + action);
			return action;
		}

		// the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
		private AbilityConditionCheck resolve(Ability ability) {
			try {
				Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
				cached.setAccessible(true);
				cached.setBoolean(power, false);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
			}
			return power.updateAvailableMoves().getContextVariationContainer(ability);
		}

		// the action as a client decodes it from the action encoded as it is now, given its Stand as the client's slot does
		private EntityActionInstance clientCopy(EntityActionInstance action) {
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			try {
				EntityActionInstance.encode(buf, action);
				EntityActionInstance copy = EntityActionInstance.decode(helper.getLevel(), buf);
				helper.assertTrue(copy != null && copy.getClass() == action.getClass() && !buf.isReadable(),
						"setup: the action decodes as " + copy);
				Field performer = EntityActionInstance.class.getDeclaredField("performer");
				performer.setAccessible(true);
				performer.set(copy, stand);
				return copy;
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("setup: cannot give the client's copy its Stand", e);
			}
			finally {
				buf.release();
			}
		}

		// the copy of an encode that read the phase before a release and the phase length after it (the recovery's 0)
		private EntityActionInstance tornCopy(EntityActionInstance action) {
			try {
				Field length = EntityActionInstance.class.getDeclaredField("curPhaseLength");
				length.setAccessible(true);
				float kept = length.getFloat(action);
				length.setFloat(action, 0.0F);
				try {
					return clientCopy(action);
				}
				finally {
					length.setFloat(action, kept);
				}
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("setup: cannot reach the action's phase length", e);
			}
		}

		// what PreFrameEntityAnimCalc.getLivingPose takes from the Stand's action on a frame
		private LivingAnimState pose(EntityActionInstance action, float partialTick, String what) {
			LivingAnimState pose = new LivingAnimState();
			try {
				action.extractAnim(pose, stand, partialTick);
			}
			catch (RuntimeException e) {
				helper.fail("the render thread may draw any copy it holds: " + what + " threw " + e + " at the partial tick "
						+ partialTick);
			}
			return pose;
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			if (stand != null) {
				LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
			}
			if (power != null && power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	// a fake player's connection that keeps what the server sends it
	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		final List<Packet<?>> packets = new ArrayList<>();

		RecordingConnection(FakePlayer player) {
			super(player.getServer(), player.connection.getConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
		}

		@Override
		public void send(Packet<?> packet) {
			if (packet instanceof ClientboundBundlePacket bundle) {
				bundle.subPackets().forEach(this::send);
			}
			else {
				packets.add(packet);
			}
		}

		@Override
		public void send(Packet<?> packet, PacketSendListener listener) {
			send(packet);
		}
	}
}
