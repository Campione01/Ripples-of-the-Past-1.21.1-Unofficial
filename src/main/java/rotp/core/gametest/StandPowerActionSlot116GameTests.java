package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferedInputEntry;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionInstancePacket;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 kept one held action per power (PowerBaseImpl.heldActionData), each ticked, synced and released on its own, and no
 * press of one power looked at the other power's held action: a Stand action its user performed and a non-Stand action
 * both ran. The port's user performs The World's time stop itself at a stored Resolve of 3 or more with The World not out
 * (TheWorldTimeStop did not summon it): it now lives in the user's Stand-power slot (LivingComponentAction.StandPowerSlot),
 * beside the user's own slot, which keeps Hamon's and Vampirism's actions, so neither press waits behind the other nor ends
 * it (S548 rows 6-8, and the give-way Freeze meeting a Stand press, D2); each power keeps its own input buffer, and the
 * user's client gets each action with its slot.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandPowerActionSlot116GameTests {
	private static final short NON_STAND_KEY = 61;
	private static final short TIME_STOP_KEY = 62;
	private static final short NON_STAND_OTHER_KEY = 63;
	private static final short STAND_OTHER_KEY = 64;

	private StandPowerActionSlot116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void theWorldsUnsummonedTimeStopAndHamonBreathRunTogetherInEitherOrderAsIn116(GameTestHelper helper) {
		// the breath first, then the time stop (row 7)
		try (Scene s = new Scene(helper, "SlotBreathThenStop", ModPlayerPowers.HAMON.get())) {
			s.hamon();
			EntityActionInstance breath = s.hold(s.power, "hamon_breath", NON_STAND_KEY);
			EntityActionInstance stop = s.pressTimeStop("the held Hamon Breath", breath);
			s.bothRun("the breath, then the time stop", breath, stop);

			// a full charge whose start fails clears the time stop's own slot (TimeStopAction.actionPerformStart), not the breath's
			Consumer<TimeStopLifecycleEvent.PreStart> cancel = event -> {
				if (event.getInstance().userId() == s.user.getId()) {
					event.setCanceled(true);
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, TimeStopLifecycleEvent.PreStart.class, cancel);
			int ticks = 0;
			try {
				for (; ticks < 40 && s.standPowerSlot().getAction() == stop; ticks++) {
					s.own().tick();
					s.standPowerSlot().tick();
				}
			}
			finally {
				NeoForge.EVENT_BUS.unregister(cancel);
			}
			helper.assertTrue(s.standPowerSlot().getAction() == null && s.own().getAction() == breath && !breath.isOver()
					&& breath.getPhase() == ActionPhase.PERFORM,
					"a time stop whose start failed after " + ticks + " ticks leaves its own slot, and the held breath goes on: the"
							+ " Stand-power action " + s.standPowerSlot().getAction() + ", the user's own action "
							+ s.own().getAction() + " in " + breath.getPhase());
		}
		// the time stop first, then the breath (row 6), and each power's press waits in its own buffer
		try (Scene s = new Scene(helper, "SlotStopThenBreath", ModPlayerPowers.HAMON.get())) {
			s.hamon();
			s.aim(s.cow(1.5D));
			EntityActionInstance stop = s.hold(s.standPower, "time_stop", TIME_STOP_KEY);
			helper.assertTrue(stop.getPerformer() == s.user && stop.getPhase() == ActionPhase.BUTTON_CHARGE,
					"setup: The World's time stop at Resolve 4, not out, is not charging on its user: " + stop.getPhase()
							+ ", performer " + stop.getPerformer());
			BufferingState breathPress = s.press(s.power, "hamon_breath", InputMethod.HOLD, NON_STAND_KEY);
			EntityActionInstance breath = s.own().getAction();
			helper.assertTrue(breathPress.isActionSuccess && !breathPress.shouldBuffer && breath != null
					&& breath.ability == s.power.getAbility("hamon_breath"),
					"1.16 started Hamon Breath at once while the time stop charged (one held action per power): started "
							+ breathPress.isActionSuccess + ", buffered " + breathPress.shouldBuffer + ", the user's own action "
							+ breath + ", its Stand-power action " + s.standPowerSlot().getAction());
			s.bothRun("the time stop, then the breath", breath, stop);

			BufferingState overdrive = s.press(s.power, "hamon_overdrive", InputMethod.CLICK, NON_STAND_OTHER_KEY);
			BufferingState stopAgain = s.press(s.standPower, "time_stop", InputMethod.HOLD, STAND_OTHER_KEY);
			BufferedInputEntry playerBuffered = buffered(s.input().inputBuffer(PowerClass.PLAYER_POWER));
			BufferedInputEntry standBuffered = buffered(s.input().inputBuffer(PowerClass.STAND));
			helper.assertTrue(overdrive.shouldBuffer && stopAgain.shouldBuffer && playerBuffered != null
					&& "hamon_overdrive".equals(playerBuffered.baseAbilityId().nameInMoveset()) && standBuffered != null
					&& "time_stop".equals(standBuffered.baseAbilityId().nameInMoveset()),
					"1.16 kept the presses of each power apart: a Hamon Overdrive held back by the breath and a time stop held"
							+ " back by the time stop each wait in their power's buffer: the non-Stand buffer " + playerBuffered
							+ ", the Stand's " + standBuffered);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void theWorldsUnsummonedTimeStopAndVampireFreezeRunTogetherInEitherOrderAsIn116(GameTestHelper helper) {
		// the Freeze first: the Freeze gives way to every Vampirism press, but a Stand press goes to its own slot (rows 8, 13)
		try (Scene s = new Scene(helper, "SlotFreezeThenStop", ModPlayerPowers.VAMPIRISM.get())) {
			s.vampire();
			EntityActionInstance freeze = s.hold(s.power, "vampirism_freeze", NON_STAND_KEY);
			EntityActionInstance stop = s.pressTimeStop("the held Vampire Freeze", freeze);
			s.bothRun("the Freeze, then the time stop", freeze, stop);
		}
		// the time stop first, then the Freeze (row 6)
		try (Scene s = new Scene(helper, "SlotStopThenFreeze", ModPlayerPowers.VAMPIRISM.get())) {
			s.vampire();
			EntityActionInstance stop = s.hold(s.standPower, "time_stop", TIME_STOP_KEY);
			BufferingState freezePress = s.press(s.power, "vampirism_freeze", InputMethod.HOLD, NON_STAND_KEY);
			EntityActionInstance freeze = s.own().getAction();
			helper.assertTrue(freezePress.isActionSuccess && !freezePress.shouldBuffer && freeze != null
					&& freeze.ability == s.power.getAbility("vampirism_freeze"),
					"1.16 started the Freeze at once while the time stop charged: started " + freezePress.isActionSuccess
							+ ", buffered " + freezePress.shouldBuffer + ", the user's own action " + freeze);
			s.bothRun("the time stop, then the Freeze", freeze, stop);
		}
		helper.succeed();
	}

	@Nullable
	private static BufferedInputEntry buffered(ActionInputBuffer buffer) {
		try {
			Field field = ActionInputBuffer.class.getDeclaredField("buffered");
			field.setAccessible(true);
			return (BufferedInputEntry) field.get(buffer);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("ActionInputBuffer.buffered is not reachable", e);
		}
	}

	/** A player with a non-Stand power and The World at Resolve 4, not out, looking south, its connection recorded. */
	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final FakePlayer user;
		private final RecordingConnection recording;
		private final PlayerPower power;
		private final StandPower standPower;
		private final Vec3 origin;
		private final List<Entity> spawned = new ArrayList<>();

		private Scene(GameTestHelper helper, String name, PlayerPowerType<?> type) {
			this.helper = helper;
			user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.setNoGravity(true);
			user.getAbilities().instabuild = false;
			user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			recording = new RecordingConnection(user);
			user.connection = recording;
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the two-power player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(type);
			StandType theWorld = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
			helper.assertTrue(theWorld != null, "Missing Stand type the_world");
			standPower = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(standPower, new StandInstance(theWorld)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant The World");
			standPower.setResolveLevel(4);
			standPower.getCurTypeData()._lockedAbilities.remove("time_stop");
			standPower.setStamina(standPower.getMaxStamina());
			helper.assertTrue(!standPower.isSummoned() && standPower.getAbility("time_stop").isAbilityUnlocked(standPower),
					"setup: The World is out or its time stop is locked");
		}

		private void hamon() {
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.OVERDRIVE.get());
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
		}

		private void vampire() {
			VampirismData data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
			data.setVampireFullPower(true, user);
			VampirismState.get(user).blood().setCurrent(100.0F);
			data.setBloodLevel(VampirismState.get(user).blood().current());
		}

		private Cow cow(double ahead) {
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Could not create the cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(origin.x, origin.y, origin.z + ahead, 180.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add the cow");
			spawned.add(cow);
			return cow;
		}

		private void aim(Entity target) {
			own().entityAim.setTarget(new ActionTarget(target));
		}

		private LivingComponentAction own() {
			return LivingComponentAction.getComponent(user);
		}

		private LivingComponentAction standPowerSlot() {
			return LivingComponentAction.getComponent(user, PowerClass.STAND);
		}

		private EntityActionInputState input() {
			return user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		}

		// pressed as a held key and kept held; the action is the one of its power's slot
		private EntityActionInstance hold(Power<?> pressed, String name, short key) {
			BufferingState state = press(pressed, name, InputMethod.HOLD, key);
			EntityActionInstance action = LivingComponentAction.getCurEntityAction(user, pressed.getPowerClass());
			helper.assertTrue(state.isActionSuccess && action != null && action.ability == pressed.getAbility(name),
					"setup: " + name + " is not held: started " + state.isActionSuccess + ", buffered " + state.shouldBuffer
							+ ", the slot's action " + action);
			return action;
		}

		// the time stop pressed while the non-Stand power holds an action: it charges at once in the Stand-power slot
		private EntityActionInstance pressTimeStop(String during, EntityActionInstance nonStand) {
			BufferingState state = press(standPower, "time_stop", InputMethod.HOLD, TIME_STOP_KEY);
			EntityActionInstance stop = standPowerSlot().getAction();
			helper.assertTrue(state.isActionSuccess && !state.shouldBuffer && stop != null
					&& stop.ability == standPower.getAbility("time_stop") && stop.getPhase() == ActionPhase.BUTTON_CHARGE
					&& stop.getPerformer() == user && !nonStand.isOver() && own().getAction() == nonStand,
					"1.16 started The World's time stop charging at once during " + during + ", which went on (one held action"
							+ " per power): started " + state.isActionSuccess + ", buffered " + state.shouldBuffer
							+ ", the user's Stand-power action " + stop + ", its own action " + own().getAction() + ", the "
							+ during + " over " + nonStand.isOver());
			return stop;
		}

		// both slots tick in 1.16's order and both actions go on; the user's client gets each with its slot
		private void bothRun(String order, EntityActionInstance nonStand, EntityActionInstance stop) {
			ActionPhase nonStandPhase = nonStand.getPhase();
			float chargeBefore = stop.getPhaseTick();
			for (int tick = 0; tick < 3; tick++) {
				own().tick();
				standPowerSlot().tick();
			}
			helper.assertTrue(own().getAction() == nonStand && nonStand.getPhase() == nonStandPhase && !nonStand.isOver()
					&& standPowerSlot().getAction() == stop && stop.getPhase() == ActionPhase.BUTTON_CHARGE
					&& stop.getPhaseTick() == chargeBefore + 3
					&& AbilityInput.getHeldAction(user, PowerClass.STAND) == stop
					&& AbilityInput.getHeldAction(user, PowerClass.PLAYER_POWER) == nonStand,
					"1.16 ticked both powers' held actions (" + order + "): the user's own action " + own().getAction() + " in "
							+ nonStand.getPhase() + ", the Stand-power action " + standPowerSlot().getAction() + " in "
							+ stop.getPhase() + " tick " + stop.getPhaseTick() + " (from " + chargeBefore + ")");
			EntityActionInstance clientStop = clientCopyOf(stop, LivingComponentAction.STAND_POWER_SLOT);
			EntityActionInstance clientNonStand = clientCopyOf(nonStand, LivingComponentAction.MAIN_SLOT);
			helper.assertTrue(((EntityActionAbility) stop.ability).isHeldSincePress(clientStop)
					&& ((EntityActionAbility) nonStand.ability).isHeldSincePress(clientNonStand),
					"the user's client holds both copies as held (" + order + "): the time stop " + clientStop.getPhase()
							+ ", the other " + clientNonStand.getPhase());
		}

		// the copy of the action the user's client got with the given slot (the last such packet for this user)
		private EntityActionInstance clientCopyOf(EntityActionInstance action, int slot) {
			TrEntityActionInstancePacket last = null;
			for (Packet<?> packet : recording.packets) {
				if (packet instanceof ClientboundCustomPayloadPacket custom
						&& custom.payload() instanceof TrEntityActionInstancePacket sent
						&& field(sent, "performerId", Integer.class) == user.getId()
						&& field(sent, "slot", Integer.class) == slot) {
					last = sent;
				}
			}
			// the action as the packet encoded it when it was sent
			byte[] sentData = last != null ? field(last, "actionData", byte[].class) : null;
			helper.assertTrue(sentData != null, "the user's client got no action for slot " + slot);
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(sentData),
					helper.getLevel().registryAccess());
			try {
				EntityActionInstance copy = EntityActionInstance.decode(helper.getLevel(), buf);
				helper.assertTrue(copy != null && copy.getClass() == action.getClass() && copy.id == action.id
						&& !buf.isReadable(), "the user's client got for slot " + slot + " " + copy + ", not " + action
								+ " (the action's own slot " + action.actionSlot() + ")");
				return copy;
			}
			finally {
				buf.release();
			}
		}

		private static <T> T field(Object owner, String name, Class<T> type) {
			try {
				Field field = owner.getClass().getDeclaredField(name);
				field.setAccessible(true);
				return type.cast(field.get(owner));
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("cannot read " + name, e);
			}
		}

		// as ClAbilityInputPacket handles a press: the key's resolved ability, its condition check, then the press
		private BufferingState press(Power<?> pressed, String name, InputMethod inputMethod, short key) {
			BufferingState state = BufferingState.clickCanBuffer();
			Ability base = pressed.getAbility(name);
			helper.assertTrue(base != null, "Missing ability " + name);
			AbilityConditionCheck container = resolve(pressed, name);
			if (container != null && AbilityInput.withConditionCheck(container, user, inputMethod)) {
				AbilityInput.keyPress(key, container.ability, user, null, inputMethod, 0, state, base.getAbilityId());
			}
			return state;
		}

		private AbilityConditionCheck resolve(Power<?> resolved, String name) {
			try {
				Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
				cached.setAccessible(true);
				cached.setBoolean(resolved, false);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
			}
			return resolved.updateAvailableMoves().getContextVariationContainer(resolved.getAbility(name));
		}

		@Override
		public void close() {
			input().heldKeys.clear();
			own().entityAim.setTarget(ActionTarget.EMPTY);
			own().setAction(null, SyncType.NO_SYNC);
			standPowerSlot().setAction(null, SyncType.NO_SYNC);
			helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get()).removeInstance(user.getId());
			user.removeEffect(ModStatusEffects.TIME_STOP);
			user.removeAllEffects();
			spawned.forEach(Entity::discard);
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
