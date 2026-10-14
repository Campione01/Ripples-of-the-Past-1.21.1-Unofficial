package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.event.RipplesAbilityKeyPressEvent;
import rotp.core.impl.stands.starplatinum.SPStarFingerEntity;
import rotp.core.impl.stands.starplatinum.StarFingerAbility.StarFingerInstance;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.network.s2c.TrAbilityUsePacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.checkInputBuffer clicked a queued action again (PowerBaseImpl.clickAction) once its Stand could
 * take it, and performAction set the action's cooldown. A Star Finger pressed while Star Platinum is busy starts later
 * with its cooldown, the key press event and the press sent to tracking players; a refused replay starts nothing.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BufferedInputReplayGameTests {
	private static final short KEY = 121;

	private BufferedInputReplayGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void bufferedStarFingerStartsWithItsCooldownEventAndTrackingPress(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		List<Ability> pressed = new ArrayList<>();
		Consumer<RipplesAbilityKeyPressEvent> listener = event -> {
			if (event.getEntity() == scene.user) {
				pressed.add(event.getAbility());
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RipplesAbilityKeyPressEvent.class, listener);
		try {
			scene.start();
			Ability starFinger = scene.power.getAbility("star_finger");
			int cooldown = starFinger.getCooldown(scene.power, -1);
			helper.assertTrue(cooldown == 80, "fixture: Star Finger's Survival cooldown is not 20 + 60 ticks: " + cooldown);

			BufferingState press = scene.press(starFinger);
			helper.assertTrue(press.shouldBuffer && !press.isActionSuccess && scene.stand.getCurStandAction() == scene.blocker,
					"fixture: the press was not buffered behind the running action");
			helper.assertTrue(scene.power.getAbilityCooldown("star_finger") == 0 && pressed.size() == 1,
					"fixture: a buffered press set a cooldown or fired " + pressed.size() + " key press events");

			scene.watchUser();
			for (int i = 0; i < 3; i++) {
				scene.input.tick();
			}
			helper.assertTrue(scene.stand.getCurStandAction() == scene.blocker && pressed.size() == 1 && scene.watcher.presses == 0,
					"while Star Platinum stays busy the buffered press waits: action=" + scene.stand.getCurStandAction()
							+ " events=" + pressed.size() + " tracking presses=" + scene.watcher.presses);

			LivingComponentAction.getComponent(scene.stand).setAction(null, scene.user, SyncType.NO_SYNC);
			scene.input.tick();
			helper.assertTrue(scene.stand.getCurStandAction() instanceof StarFingerInstance,
					"the buffered Star Finger did not start once Star Platinum was free: " + scene.stand.getCurStandAction());
			helper.assertTrue(scene.power.getAbilityCooldown("star_finger") == cooldown,
					"1.16 performAction: the replayed Star Finger starts its " + cooldown + "-tick cooldown, got "
							+ scene.power.getAbilityCooldown("star_finger"));
			helper.assertTrue(pressed.size() == 2 && pressed.get(1) == starFinger,
					"the replay that starts fires the key press event once: " + pressed);
			helper.assertTrue(scene.watcher.presses == 1,
					"the replay that starts reaches tracking players once: " + scene.watcher.presses);

			// the moves (and their condition checks) are cached per tick: ask again on the next one
			helper.runAfterDelay(1, () -> {
				try {
					AbilityConditionCheck again = scene.power.updateAvailableMoves().getContextVariationContainer(starFinger);
					helper.assertTrue(again != null && !AbilityInput.withConditionCheck(again, scene.user, InputMethod.CLICK),
							"a second Star Finger right after the replay must be refused by its cooldown");
					helper.assertTrue(pressed.size() == 2 && scene.watcher.presses == 1,
							"the replay ran twice: events=" + pressed.size() + " tracking presses=" + scene.watcher.presses);
				}
				finally {
					NeoForge.EVENT_BUS.unregister(listener);
					scene.close();
				}
				helper.succeed();
			});
		}
		catch (RuntimeException | Error failure) {
			NeoForge.EVENT_BUS.unregister(listener);
			scene.close();
			throw failure;
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void refusedBufferedStarFingerReplayStartsNothing(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		boolean[] refuse = new boolean[1];
		int[] refused = new int[1];
		Consumer<RipplesAbilityKeyPressEvent> listener = event -> {
			if (refuse[0] && event.getEntity() == scene.user) {
				refused[0]++;
				event.setCanceled(true);
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RipplesAbilityKeyPressEvent.class, listener);
		try {
			scene.start();
			Ability starFinger = scene.power.getAbility("star_finger");
			BufferingState press = scene.press(starFinger);
			helper.assertTrue(press.shouldBuffer && !press.isActionSuccess, "fixture: the press was not buffered");

			// a listener that refuses this ability now (as Heavens Door's forgotten Stand does) refuses the replay
			refuse[0] = true;
			LivingComponentAction.getComponent(scene.stand).setAction(null, scene.user, SyncType.NO_SYNC);
			scene.input.tick();
			scene.input.tick();
			helper.assertTrue(refused[0] == 1, "the replay asks the key press event once and drops the refused press: " + refused[0]);
			helper.assertTrue(scene.stand.getCurStandAction() == null && scene.power.getAbilityCooldown("star_finger") == 0,
					"a refused replay started " + scene.stand.getCurStandAction() + " or set the cooldown "
							+ scene.power.getAbilityCooldown("star_finger"));
		}
		finally {
			NeoForge.EVENT_BUS.unregister(listener);
			scene.close();
		}
		helper.succeed();
	}

	private static final class Scene {
		final GameTestHelper helper;
		final ServerLevel level;
		Player user;
		StandPower power;
		StandType type;
		StandEntity stand;
		EntityActionInstance blocker;
		EntityActionInputState input;
		Watcher watcher;
		ChunkMap.TrackedEntity tracked;
		boolean closed;

		Scene(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel();
		}

		void start() {
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.moveTo(helper.absoluteVec(new Vec3(2.5, 2, 2.5)));
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Star Platinum user");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			power.setResolveLevel(3);
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "The summoned Stand is missing");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			power.setStamina(power.getMaxStamina());
			input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			// a heavy punch cannot be cancelled into Star Finger, so a press buffers behind it
			Ability heavy = power.getAbility("heavy_punch");
			helper.assertTrue(heavy instanceof EntityActionType, "Star Platinum's heavy punch is missing");
			blocker = new EntityActionInstance((EntityActionType) heavy);
			blocker.phasesLength.put(ActionPhase.PERFORM, 200F);
			blocker.setStartingPhase();
			LivingComponentAction.getComponent(stand).setAction(blocker, user, SyncType.NO_SYNC);
		}

		BufferingState press(Ability ability) {
			AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
			helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
					"fixture: Star Finger press not admitted");
			BufferingState state = BufferingState.clickCanBuffer();
			AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.CLICK, 0, state, ability.getAbilityId());
			return state;
		}

		void watchUser() {
			watcher = new Watcher(level);
			tracked = level.getChunkSource().chunkMap.entityMap.get(user.getId());
			helper.assertTrue(tracked != null, "fixture: the user is not tracked");
			tracked.seenBy.add(watcher.connection);
		}

		void close() {
			if (closed) return;
			closed = true;
			if (tracked != null && watcher != null) {
				tracked.seenBy.remove(watcher.connection);
			}
			if (user != null) {
				AbilityInput.keyRelease(KEY, user);
				for (SPStarFingerEntity finger : level.getEntitiesOfClass(SPStarFingerEntity.class,
						new AABB(user.blockPosition()).inflate(16))) {
					finger.discard();
				}
				if (power != null && type != null && power.isSummoned()) {
					type.forceUnsummon(user, power);
				}
				user.discard();
			}
		}
	}

	private static final class Watcher extends FakePlayer {
		int presses;

		Watcher(ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "buffered-replay-watcher"));
			this.connection = new RecordingConnection(level, this);
		}
	}

	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		private final Watcher watcher;

		RecordingConnection(ServerLevel level, Watcher player) {
			super(level.getServer(), openConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
			this.watcher = player;
		}

		// as GameTestHelper.makeMockServerPlayerInLevel: a live channel, so channel lookups do not fail
		private static Connection openConnection() {
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			return connection;
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
			if (watcher == null) return;
			if (packet instanceof ClientboundBundlePacket bundle) {
				bundle.subPackets().forEach(sub -> send(sub, null));
			}
			else if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof TrAbilityUsePacket) {
				watcher.presses++;
			}
		}
	}
}
