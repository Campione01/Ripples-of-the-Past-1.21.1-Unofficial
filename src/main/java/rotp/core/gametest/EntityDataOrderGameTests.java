package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import rotp.core.JojoModLivingVariables;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.entityattachment.DataEventListeners;
import rotp.core.entityattachment.PlayerVoiceLineData;
import rotp.core.entityattachment.SynchronizableEntityData;
import rotp.core.entityattachment.SynchronizablePlayerData;
import rotp.core.entityattachment.TickingEntityData;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectsMap;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonHypnosisState;
import rotp.core.impl.powers.hamon.ProjectileHamonChargeState;
import rotp.core.impl.stands.goldexperience.GELifeshotState;
import rotp.core.impl.stands.goldexperience.GEStuckObjectsState;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformState;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.mechanics.clothes.EntityClothesInventory;
import rotp.core.mechanics.coffin.PlayerCoffinSleepData;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.entity_externalcontainer.PlayerExternalContainers;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.entity_possessionv2.LivingComponentPossession;
import rotp.core.subsystems.entity_puppetcontrol.EntityComponentController;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * DataEventListeners visits an entity's attached data in one fixed order, whatever order the data was attached in.
 * The tick order is the one 1.16 ticked the same things in:
 * the level's entity tick moved a possessing player to its camera entity (vanilla ServerPlayerEntity.tick);
 * GameplayEventHandler.onPlayerTick START then ran PlayerUtilCap.tick (voice lines, the Life Shot knockback packet,
 * animalAgeCd, the coffin sleep timer, the continuous action), INonStandPower.tick and IStandPower.tick, and
 * PowerBaseImpl.tick ran tickHeldAction before tickCooldown: an action ticks before its power;
 * StandEntity.tick replayed the queued input against the state its user's power tick had left;
 * onLivingTick ran LivingUtilCap.tick (dying body, stuck knives, hypnosis), vanilla then ticked the Stand virus effect;
 * onWorldTick END ran EntityUtilCap.tick (the knockback impact), then the projectile and the entity Hamon charge.
 * Sync and clone visit the powers first, as ForgeBusEventSubscriber did (non-Stand, Stand, then the utility data).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EntityDataOrderGameTests {
	private record Data(Class<?> type, Supplier<? extends AttachmentType<?>> attachment) {}

	private static final Data PLAYER_POWER = new Data(PlayerPower.class, ModDataAttachmentTypes.PLAYER_POWER);
	private static final Data STAND_POWER = new Data(StandPower.class, ModDataAttachmentTypes.STAND_POWER);
	private static final Data SERVER_PLAYER_ONLY = new Data(PlayerExternalContainers.class, ModDataAttachmentTypes.EXTERNAL_CONTAINERS);
	private static final Data PROJECTILE_ONLY = new Data(ProjectileHamonChargeState.class, ModDataAttachmentTypes.PROJECTILE_HAMON_CHARGE);

	private static final List<Data> TICK_ORDER = List.of(
			new Data(LivingComponentPossession.class, ModDataAttachmentTypes.ENTITY_POSSESSION),
			new Data(PlayerVoiceLineData.class, ModDataAttachmentTypes.PLAYER_VOICE_LINES),
			new Data(GELifeshotState.class, ModDataAttachmentTypes.GE_LIFESHOT_STATE),
			new Data(GoldExperienceLifeformState.class, ModDataAttachmentTypes.GE_LIFEFORM_STATE),
			new Data(PlayerCoffinSleepData.class, ModDataAttachmentTypes.PLAYER_COFFIN_SLEEP),
			new Data(LivingComponentAction.class, ModDataAttachmentTypes.LIVING_ACTION),
			PLAYER_POWER,
			STAND_POWER,
			new Data(EntityActionInputState.class, ModDataAttachmentTypes.ENTITY_ABILITY_INPUT),
			new Data(JojoModLivingVariables.class, ModDataAttachmentTypes.LIVING_VARS),
			new Data(GEStuckObjectsState.class, ModDataAttachmentTypes.GE_STUCK_OBJECTS_STATE),
			new Data(HamonHypnosisState.class, ModDataAttachmentTypes.HAMON_HYPNOSIS_STATE),
			new Data(EntityCustomEffectsMap.class, ModDataAttachmentTypes.ENTITY_CUSTOM_EFFECTS),
			new Data(EntityComponentController.class, ModDataAttachmentTypes.CONTROLLER),
			new Data(LivingComponentGrab.class, ModDataAttachmentTypes.LIVING_GRAB),
			new Data(EntityClothesInventory.class, ModDataAttachmentTypes.HUMANOID_CLOTHES),
			SERVER_PLAYER_ONLY,
			new Data(KnockbackCollisionImpact.class, ModDataAttachmentTypes.KB_IMPACT),
			PROJECTILE_ONLY,
			new Data(EntityHamonChargeState.class, ModDataAttachmentTypes.HAMON_CHARGE));

	private static final List<Data> SYNC_ORDER = syncOrder();

	private EntityDataOrderGameTests() {}

	private static List<Data> syncOrder() {
		List<Data> order = new ArrayList<>(List.of(PLAYER_POWER, STAND_POWER,
				new Data(PlayerClientBroadcastedSettings.class, ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS)));
		for (Data data : TICK_ORDER) {
			if (data != PLAYER_POWER && data != STAND_POWER) {
				order.add(data);
			}
		}
		return List.copyOf(order);
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void playerDataTicksInDonorOrderWhateverTheAttachOrder(GameTestHelper helper) {
		List<String> unplaced = unplaced();
		helper.assertTrue(unplaced.isEmpty(), "The expected order has no place for the attached data " + unplaced);

		List<Data> playerData = new ArrayList<>(TICK_ORDER);
		playerData.remove(SERVER_PLAYER_ONLY);
		playerData.remove(PROJECTILE_ONLY);
		List<Data> reversed = new ArrayList<>(playerData);
		Collections.reverse(reversed);
		Player attachedInOrder = player(helper);
		Player attachedInReverse = player(helper);
		try {
			attach(attachedInOrder, playerData);
			attach(attachedInReverse, reversed);
			helper.assertTrue(helper.getLevel().addFreshEntity(attachedInOrder) && helper.getLevel().addFreshEntity(attachedInReverse),
					"Could not add the players");
		}
		catch (RuntimeException | Error error) {
			attachedInOrder.discard();
			attachedInReverse.discard();
			throw error;
		}
		// the level ticks the players: EventHandler.onEntityTick -> DataEventListeners.onTick
		helper.runAfterDelay(3, () -> {
			try {
				List<Class<?>> expected = types(playerData);
				helper.assertTrue(attachedInOrder.tickCount > 0 && attachedInReverse.tickCount > 0, "The level did not tick the players");
				helper.assertTrue(visited(attachedInOrder, "ticking").equals(expected),
						"Data attached in the 1.16 order ticks as " + names(visited(attachedInOrder, "ticking")) + ", 1.16: " + names(expected));
				helper.assertTrue(visited(attachedInReverse, "ticking").equals(expected),
						"Data attached in the reverse order ticks as " + names(visited(attachedInReverse, "ticking")) + ", 1.16: " + names(expected));
				helper.succeed();
			}
			finally {
				attachedInOrder.discard();
				attachedInReverse.discard();
			}
		});
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void serverPlayerAndProjectileDataTickInDonorOrder(GameTestHelper helper) {
		ServerPlayer serverPlayer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DataOrderContainers"));
		Arrow arrow = EntityType.ARROW.create(helper.getLevel());
		try {
			helper.assertTrue(arrow != null, "Could not create the arrow");
			List<Data> serverPlayerData = new ArrayList<>(TICK_ORDER);
			serverPlayerData.remove(PROJECTILE_ONLY);
			List<Data> projectileData = TICK_ORDER.stream().filter(data -> data == PROJECTILE_ONLY
					|| data.type() == EntityCustomEffectsMap.class || data.type() == KnockbackCollisionImpact.class
					|| data.type() == EntityHamonChargeState.class).toList();
			helper.assertTrue(projectileData.size() == 4, "The projectile data is not the expected set");

			attachReversed(serverPlayer, serverPlayerData);
			attachReversed(arrow, projectileData);
			listeners(serverPlayer).onTick();
			listeners(arrow).onTick();

			helper.assertTrue(visited(serverPlayer, "ticking").equals(types(serverPlayerData)), "A server player's data ticks as "
					+ names(visited(serverPlayer, "ticking")) + ", 1.16: " + names(types(serverPlayerData)));
			helper.assertTrue(visited(arrow, "ticking").equals(types(projectileData)), "A projectile's data ticks as "
					+ names(visited(arrow, "ticking")) + ", 1.16: " + names(types(projectileData)));
			helper.succeed();
		}
		finally {
			serverPlayer.discard();
			if (arrow != null) arrow.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void unlistedDataTicksAfterTheListedInClassNameOrder(GameTestHelper helper) {
		Player player = player(helper);
		try {
			JojoModLivingVariables vars = JojoModLivingVariables.get(player);
			List<String> visits = new ArrayList<>();
			Supplier<String> seen = () -> " knives=" + vars.knivesThrewTicks;
			for (Probe probe : List.of(new ProbeC(visits, seen), new ProbeA(visits, seen), new ProbeD(visits, seen), new ProbeB(visits, seen))) {
				probe.addTicking(player);
			}
			vars.knivesThrewTicks = 10;
			listeners(player).onTick();
			// JojoModLivingVariables.tick counts the timer down: a probe that ticks after it reads 9
			List<String> expected = List.of("tick ProbeA knives=9", "tick ProbeB knives=9", "tick ProbeC knives=9", "tick ProbeD knives=9");
			helper.assertTrue(visits.equals(expected), "Data with no place in the order ticked as " + visits
					+ ", expected after the listed data and by class name: " + expected);
			visits.clear();
			vars.knivesThrewTicks = 10;
			listeners(player).onTick();
			helper.assertTrue(visits.equals(expected), "The second tick visited " + visits + ", expected " + expected);
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void syncAndCloneVisitThePowersFirstThenTheTickOrder(GameTestHelper helper) {
		Player player = player(helper);
		try {
			List<Data> playerData = new ArrayList<>(SYNC_ORDER);
			playerData.remove(SERVER_PLAYER_ONLY);
			playerData.remove(PROJECTILE_ONLY);
			attachReversed(player, playerData);
			List<Class<?>> tracking = types(playerData).stream().filter(SynchronizableEntityData.class::isAssignableFrom).toList();
			List<Class<?>> toPlayer = types(playerData).stream().filter(SynchronizablePlayerData.class::isAssignableFrom).toList();
			helper.assertTrue(tracking.size() == 13 && toPlayer.size() == 9 && tracking.get(0) == PlayerPower.class
					&& tracking.get(1) == StandPower.class && tracking.get(2) == PlayerClientBroadcastedSettings.class,
					"The synced data is not the expected set: " + names(tracking) + " / " + names(toPlayer));
			helper.assertTrue(visited(player, "entityDataSync").equals(tracking), "Data syncs to a tracking player as "
					+ names(visited(player, "entityDataSync")) + ", expected the powers first, then the tick order: " + names(tracking));
			helper.assertTrue(visited(player, "playerDataSync").equals(toPlayer), "Data syncs to its player and clones as "
					+ names(visited(player, "playerDataSync")) + ", expected the powers first, then the tick order: " + names(toPlayer));

			// data with no place in the order: by class name, through the real entry points
			DataEventListeners listeners = new DataEventListeners(null);
			List<String> visits = new ArrayList<>();
			for (Probe probe : List.of(new ProbeC(visits, () -> ""), new ProbeA(visits, () -> ""), new ProbeD(visits, () -> ""),
					new ProbeB(visits, () -> ""))) {
				listeners.addPlayerDataSync(probe);
			}
			listeners.onTracking(null);
			listeners.onSyncToPlayer(null);
			listeners.onClone(null, false);
			List<String> expected = new ArrayList<>();
			for (String entryPoint : List.of("tracking", "player", "clone")) {
				for (String probe : List.of("ProbeA", "ProbeB", "ProbeC", "ProbeD")) {
					expected.add(entryPoint + " " + probe);
				}
			}
			helper.assertTrue(visits.equals(expected), "Unlisted data was synced and cloned as " + visits + ", expected by class name: " + expected);
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	private static Player player(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		player.setNoGravity(true);
		return player;
	}

	private static void attach(Entity entity, List<Data> data) {
		for (Data each : data) {
			attachOne(entity, each.attachment().get(), each.type());
		}
	}

	private static void attachReversed(Entity entity, List<Data> data) {
		List<Data> reversed = new ArrayList<>(data);
		Collections.reverse(reversed);
		attach(entity, reversed);
	}

	private static <T> void attachOne(Entity entity, AttachmentType<T> attachment, Class<?> type) {
		T data = entity.getData(attachment);
		if (!type.isInstance(data)) {
			throw new AssertionError(type.getSimpleName() + " was not attached to " + entity);
		}
	}

	private static DataEventListeners listeners(Entity entity) {
		return entity.getData(ModDataAttachmentTypes.DATA_EVENT_HELPER.get());
	}

	// the classes of the attached data in the order DataEventListeners visits them
	@SuppressWarnings("unchecked")
	private static List<Class<?>> visited(Entity entity, String field) {
		try {
			Field map = DataEventListeners.class.getDeclaredField(field);
			map.setAccessible(true);
			return new ArrayList<>(((Map<Class<?>, ?>) map.get(listeners(entity))).keySet());
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Could not read DataEventListeners." + field, error);
		}
	}

	private static List<Class<?>> types(List<Data> data) {
		return data.stream().<Class<?>>map(Data::type).toList();
	}

	private static List<String> names(List<Class<?>> types) {
		return types.stream().map(Class::getSimpleName).toList();
	}

	// attachment data of ModDataAttachmentTypes that ticks or syncs and is missing from the expected orders
	private static List<String> unplaced() {
		List<String> unplaced = new ArrayList<>();
		for (Field field : ModDataAttachmentTypes.class.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers()) || !(field.getGenericType() instanceof ParameterizedType supplier)
					|| !(supplier.getActualTypeArguments()[0] instanceof ParameterizedType attachment)) {
				continue;
			}
			Type data = attachment.getActualTypeArguments()[0];
			Class<?> type = data instanceof ParameterizedType generic ? (Class<?>) generic.getRawType() : data instanceof Class<?> plain ? plain : null;
			if (type == null) {
				continue;
			}
			if (TickingEntityData.class.isAssignableFrom(type) && !types(TICK_ORDER).contains(type)
					|| SynchronizableEntityData.class.isAssignableFrom(type) && !types(SYNC_ORDER).contains(type)) {
				unplaced.add(field.getName());
			}
		}
		return unplaced;
	}

	private abstract static class Probe implements TickingEntityData, SynchronizablePlayerData {
		private final List<String> visits;
		private final Supplier<String> seen;

		private Probe(List<String> visits, Supplier<String> seen) {
			this.visits = visits;
			this.seen = seen;
		}

		private void visit(String entryPoint) {
			visits.add(entryPoint + " " + getClass().getSimpleName() + seen.get());
		}

		@Override
		public void tick() {
			visit("tick");
		}

		@Override
		public void syncToTracking(ServerPlayer trackingPlayer) {
			visit("tracking");
		}

		@Override
		public void syncToPlayer(ServerPlayer entityAsPlayer) {
			visit("player");
		}

		@Override
		public void onPlayerClone(Player newPlayer, boolean wasDeath) {
			visit("clone");
		}
	}

	private static final class ProbeA extends Probe {
		private ProbeA(List<String> visits, Supplier<String> seen) {
			super(visits, seen);
		}
	}

	private static final class ProbeB extends Probe {
		private ProbeB(List<String> visits, Supplier<String> seen) {
			super(visits, seen);
		}
	}

	private static final class ProbeC extends Probe {
		private ProbeC(List<String> visits, Supplier<String> seen) {
			super(visits, seen);
		}
	}

	private static final class ProbeD extends Probe {
		private ProbeD(List<String> visits, Supplier<String> seen) {
			super(visits, seen);
		}
	}
}
