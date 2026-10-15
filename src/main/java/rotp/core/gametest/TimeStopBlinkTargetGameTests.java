package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.init.ModStatusEffects;
import rotp.core.network.s2c.TrDirectEntityPosPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.timestop.TimeStopLearning;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 TimeStopInstant targets: the clicked target, which with the Stand out falls back to the Stand's precision ray
 * (EntityStandType.clientHitResult); else JojoModUtil.rayTrace, which skips spectators and unpickable entities and stops
 * at outline shapes such as grass. Only players who cannot see in stopped time lose the slide of the teleport.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopBlinkTargetGameTests {
	private static final short KEY = 125;
	private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
			JojoMod.resLoc("time_stop_blink_target_gametest"));

	private TimeStopBlinkTargetGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void standOutBlinksToAMobJustOffTheCrosshair(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, 0)) {
			scene.summonStand();
			Zombie zombie = scene.add(EntityType.ZOMBIE.create(scene.level), scene.start.add(0.9, 0, 4));
			Vec3 expected = zombie.position().subtract(scene.user.getLookAngle().scale(zombie.getBbWidth() + scene.user.getBbWidth()));
			scene.blink();
			helper.assertTrue(scene.user.position().distanceTo(expected) < 0.01,
					"with the Stand out, 1.16's precision ray keeps a mob just off the crosshair as the target: landed at "
							+ scene.user.position() + ", expected beside the zombie at " + expected);
		}
		helper.succeed();
	}

	// 1.16 PowerBaseImpl.checkTarget: a press at an entity the server no longer has is a press at nothing, so the blink
	// takes its own look ray and not the Stand's precision ray, which keeps the mob just off the crosshair
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void blinkAtAnEntityTheServerNoLongerHasDoesNotTakeTheMobOffTheCrosshair(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, 0)) {
			scene.summonStand();
			Zombie zombie = scene.add(EntityType.ZOMBIE.create(scene.level), scene.start.add(0.9, 0, 4));
			Vec3 beside = zombie.position().subtract(scene.user.getLookAngle().scale(zombie.getBbWidth() + scene.user.getBbWidth()));
			Zombie gone = scene.add(EntityType.ZOMBIE.create(scene.level), scene.start.add(-3, 0, 2));
			FriendlyByteBuf sent = new FriendlyByteBuf(Unpooled.buffer());
			ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.encode(sent, new ActionTarget(gone));
			gone.discard();
			ActionTargetAim aim = LivingComponentAction.getComponent(scene.user).entityAim;
			aim.setTargetFromClient(ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.decode(sent), scene.level);
			helper.assertTrue(aim.isClientEntityMissing(), "fixture: the server still finds the entity the client aimed at");
			scene.blink();
			helper.assertTrue(scene.user.position().distanceTo(beside) > 0.5 && scene.user.position().z > zombie.getZ(),
					"a blink at an entity the server no longer has took the zombie just off the crosshair: landed at "
							+ scene.user.position() + "; 1.16 empties the press and rays along the look line past it");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void blinkRayStopsAtGrass(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, 90)) {
			BlockPos grass = BlockPos.containing(scene.start).below(4);
			scene.block(grass.below(), Blocks.STONE.defaultBlockState());
			scene.block(grass, Blocks.SHORT_GRASS.defaultBlockState());
			scene.blink();
			helper.assertTrue(scene.user.position().distanceTo(Vec3.atBottomCenterOf(grass.above())) < 0.01,
					"1.16's outline ray stops at the grass and lands above it: landed at " + scene.user.position());
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void blinkRaySkipsSpectators(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, 0)) {
			Player spectator = GameTestPlayers.makeServerMockPlayer(helper, GameType.SPECTATOR);
			scene.add(spectator, scene.start.add(0, 0, 3.5));
			scene.blink();
			helper.assertTrue(scene.user.position().z > scene.start.z + 5,
					"1.16's ray ignores a spectator on the look line: landed at " + scene.user.position());
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void playersWhoSeeInStoppedTimeKeepTheBlinkSlide(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, 0)) {
			Watcher blind = scene.watch("blind-watcher");
			Watcher seer = scene.watch("seeing-watcher");
			seer.addEffect(new MobEffectInstance(ModStatusEffects.TIME_STOP, 200));
			scene.blink();
			helper.assertTrue(blind.directPositions > 0,
					"a player who cannot see in stopped time sees the blink as a jump: " + blind.directPositions);
			helper.assertTrue(seer.directPositions == 0,
					"1.16 left the short slide to a player who sees in stopped time: " + seer.directPositions);
		}
		helper.succeed();
	}

	private static final class Scene implements AutoCloseable {
		final GameTestHelper helper;
		final ServerLevel level;
		final Vec3 start;
		final Player user;
		final StandPower power;
		final StandType type;
		final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		final List<Entity> actors = new ArrayList<>();
		final List<Watcher> watchers = new ArrayList<>();
		ChunkMap.TrackedEntity tracked;

		Scene(GameTestHelper helper, float pitch) {
			this.helper = helper;
			level = helper.getLevel();
			start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 6, 2)));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(start.x, start.y, start.z, 0, pitch);
			user.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.1);
			helper.assertTrue(level.addFreshEntity(user), "fixture: user not added");
			actors.add(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "fixture: Star Platinum not granted");
			power.setResolveLevel(4);
			var unlocked = UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
			helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED
					|| unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED, "fixture: time stop not unlocked");
			power.getCurTypeData().setAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP, 95,
					TimeStopLearning.getMaxTrainingPoints(power), power);
			power.setStamina(power.getMaxStamina());
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
		}

		void summonStand() {
			helper.assertTrue(type.summon(user, power), "fixture: Star Platinum not summoned");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "fixture: no summoned Stand");
			actors.add(stand);
			stand.setCanFollowUser(false);
			stand.setNoGravity(true);
			stand.moveTo(start.x - 1, start.y, start.z, 0, 0);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
		}

		<T extends Entity> T add(T entity, Vec3 pos) {
			helper.assertTrue(entity != null, "fixture: entity not created");
			entity.setNoGravity(true);
			if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
			entity.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(level.addFreshEntity(entity), "fixture: entity not added");
			actors.add(entity);
			return entity;
		}

		Watcher watch(String name) {
			Watcher watcher = new Watcher(level, name);
			if (tracked == null) {
				tracked = level.getChunkSource().chunkMap.entityMap.get(user.getId());
				helper.assertTrue(tracked != null, "fixture: the user is not tracked");
			}
			tracked.seenBy.add(watcher.connection);
			watchers.add(watcher);
			return watcher;
		}

		void blink() {
			TimeStopBlinkAbility blink = (TimeStopBlinkAbility) power.getAbility("time_stop_blink");
			var selected = power.updateAvailableMoves().getContextVariationContainer(blink);
			helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
					"fixture: blink not admitted");
			BufferingState press = BufferingState.clickOnly();
			AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.CLICK, 0, press, blink.getAbilityId());
			helper.assertTrue(press.isActionSuccess, "fixture: the blink did not happen");
		}

		void block(BlockPos pos, BlockState state) {
			blocks.putIfAbsent(pos, level.getBlockState(pos));
			level.setBlock(pos, state, 3);
		}

		@Override
		public void close() {
			for (Watcher watcher : watchers) {
				if (tracked != null) tracked.seenBy.remove(watcher.connection);
			}
			AbilityInput.keyRelease(KEY, user);
			if (power.isSummoned()) type.forceUnsummon(user, power);
			actors.forEach(Entity::discard);
			blocks.forEach((pos, state) -> level.setBlock(pos, state, 3));
		}
	}

	private static final class Watcher extends FakePlayer {
		int directPositions;

		Watcher(ServerLevel level, String name) {
			super(level, new GameProfile(UUID.randomUUID(), name));
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
			else if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof TrDirectEntityPosPacket) {
				watcher.directPositions++;
			}
		}
	}
}
