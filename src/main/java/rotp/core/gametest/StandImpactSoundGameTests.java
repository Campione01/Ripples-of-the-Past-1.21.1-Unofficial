package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.network.s2c.TrBarrageHitSoundPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.attackTarget / IPunch.playImpactSound: a punch's impact sound plays after the hit, for an entity
 * only when the hit landed (targetWasHit), always for a non-living target, and for a barrage also on a tick a
 * barrage clash parried. A hit a target is immune to stays silent. Own batch: the sound listener and packets.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandImpactSoundGameTests {
	private static final String BATCH = "stand_impact_sound";

	private StandImpactSoundGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void lightPunchImpactSoundsOnlyOnLandedHits(GameTestHelper helper) {
		verifyPunch(helper, "punch", ModSoundEvents.STAR_PLATINUM_PUNCH_LIGHT.get());
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void heavyPunchImpactSoundsOnlyOnLandedHits(GameTestHelper helper) {
		verifyPunch(helper, "heavy_punch", ModSoundEvents.STAR_PLATINUM_PUNCH_HEAVY.get());
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void barrageHitSoundsOnlyOnLandedOrParriedHits(GameTestHelper helper) {
		Scene scene = new Scene(helper, "impact_barrage", true);
		Scene defender = null;
		try {
			Cow immune = scene.cow();
			immune.setInvulnerable(true);
			int[] refused = scene.barrage(immune, null);
			helper.assertTrue(refused[0] > 0 && refused[1] == 0,
					"1.16 sent no barrage hit sound for hits that did not land: " + refused[1] + " of " + refused[0]
							+ " hit ticks on an invulnerable cow sounded");
			immune.discard();

			Cow cow = scene.cow();
			int[] landed = scene.barrage(cow, null);
			helper.assertTrue(landed[0] > 0 && landed[1] == landed[0] && cow.getHealth() < cow.getMaxHealth(),
					"Barrage hits on a cow sounded on " + landed[1] + " of " + landed[0] + " hit ticks");
			cow.discard();

			Boat boat = scene.boat();
			int[] nonLiving = scene.barrage(boat, null);
			helper.assertTrue(nonLiving[0] > 0 && nonLiving[1] == nonLiving[0],
					"1.16 always sounded barrage hits on a non-living target: " + nonLiving[1] + " of " + nonLiving[0]);
			boat.discard();

			defender = new Scene(helper, "impact_barrage_parry", false);
			StandEntity parrying = defender.stand;
			parrying.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 1.5, 180, 0);
			parrying.setYHeadRot(180);
			parrying.yHeadRotO = 180;
			boolean[] clashed = {false};
			int[] parried = scene.barrage(parrying, () -> {
				// set by the previous tick's parry; the barrage's end clears it
				clashed[0] |= parrying.barrageClashOpponent().filter(entity -> entity == scene.stand).isPresent();
				parrying.resetBarrageParry();
				parrying.setBarrageHitsThisTick(999);
			});
			helper.assertTrue(clashed[0], "Fixture: the target Stand did not parry the barrage");
			helper.assertTrue(parried[0] > 0 && parried[1] == parried[0],
					"1.16 sounded barrage ticks a clash parried: " + parried[1] + " of " + parried[0]);
			helper.succeed();
		}
		finally {
			if (defender != null) defender.close();
			scene.close();
		}
	}

	private static void verifyPunch(GameTestHelper helper, String abilityName, SoundEvent impact) {
		Scene scene = new Scene(helper, "impact_" + abilityName, false);
		try {
			Cow immune = scene.cow();
			immune.setInvulnerable(true);
			int sounds = scene.strike(abilityName, immune, impact);
			helper.assertTrue(immune.getHealth() == immune.getMaxHealth(), "Fixture: the invulnerable cow was hurt");
			helper.assertTrue(sounds == 0, "1.16 played no " + abilityName + " impact sound for a hit that did not land, "
					+ "the port played " + sounds);
			immune.discard();

			Cow cow = scene.cow();
			sounds = scene.strike(abilityName, cow, impact);
			helper.assertTrue(cow.getHealth() < cow.getMaxHealth() && sounds == 1,
					"A landed " + abilityName + " played " + sounds + " impact sounds");
			cow.discard();

			Boat boat = scene.boat();
			sounds = scene.strike(abilityName, boat, impact);
			helper.assertTrue(sounds == 1, "1.16 always sounded a " + abilityName + " on a non-living target, the port played "
					+ sounds);
			helper.succeed();
		}
		finally {
			scene.close();
		}
	}

	private static final class Scene {
		final GameTestHelper helper;
		final FakePlayer user;
		final RecordingConnection connection;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		final Vec3 origin;
		final List<Entity> entities = new ArrayList<>();
		final List<Runnable> cleanup = new ArrayList<>();
		SoundEvent counted;
		int sounds;

		Scene(GameTestHelper helper, String name, boolean record) {
			this.helper = helper;
			ServerLevel level = helper.getLevel();
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
			connection = record ? new RecordingConnection(user) : null;
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Stand user");
			entities.add(user);
			if (connection != null) {
				// the owner's own chunk counts as sent, so it tracks the Stand once it is added
				connection.chunkSender.dropChunk(user, user.chunkPosition());
			}
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "Missing Star Platinum entity");
			stand.summonLockTicks = 0;
			stand.setNoGravity(true);
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
			stand.setYHeadRot(0);
			// the aimed target stays the action's target
			user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
			Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
				if (counted != null && event.getLevel() == level && event.getSound() != null
						&& event.getSound().value() == counted) {
					sounds++;
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayLevelSoundEvent.AtPosition.class, listener);
			cleanup.add(() -> NeoForge.EVENT_BUS.unregister(listener));
		}

		Cow cow() {
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Could not create a cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
			cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
			cow.setHealth(500);
			return add(cow);
		}

		Boat boat() {
			Boat boat = EntityType.BOAT.create(helper.getLevel());
			helper.assertTrue(boat != null, "Could not create a boat");
			boat.setNoGravity(true);
			boat.setInvulnerable(true);
			return add(boat);
		}

		private <T extends Entity> T add(T entity) {
			entity.moveTo(origin.x, origin.y, origin.z + 1.5, 180, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(entity), "Could not add " + entity);
			entities.add(entity);
			return entity;
		}

		EntityActionInstance start(String abilityName, Entity target) {
			LivingComponentAction component = LivingComponentAction.getComponent(stand);
			component.setAction(null, user, SyncType.NO_SYNC);
			component.entityAim.setTarget(new ActionTarget(target));
			// the crosshair a client sends: 1.16 kept no pressed entity that is not living (StandEntityAction.checkStandTarget),
			// and the strike aimed again through the user's aim, so only that aim brings a punch to the boat
			LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(target));
			Ability ability = power.getAbility(abilityName);
			helper.assertTrue(ability instanceof EntityActionType, "Missing action " + abilityName);
			power.setStamina(power.getMaxStamina());
			EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			return action;
		}

		/** One punch's perform on the target; the number of impact sounds it made. */
		int strike(String abilityName, Entity target, SoundEvent impact) {
			EntityActionInstance action = start(abilityName, target);
			counted = impact;
			sounds = 0;
			action.actionPerformStart();
			counted = null;
			helper.assertTrue(action.punchedTarget != null && action.punchedTarget.getMainEntity() == target,
					"Fixture: the " + abilityName + " hit " + action.punchedTarget + " instead of " + target);
			return sounds;
		}

		/** Ten barrage ticks on the target: {hit ticks, ticks that sent a hit sound}. */
		int[] barrage(Entity target, Runnable beforeTick) {
			EntityActionInstance action = start("barrage", target);
			LivingComponentAction component = LivingComponentAction.getComponent(stand);
			connection.packets.clear();
			for (int tick = 0; tick < 10; tick++) {
				if (beforeTick != null) beforeTick.run();
				component.tick();
			}
			helper.assertTrue(component.getAction() == action && action.punchedTarget != null
					&& action.punchedTarget.getMainEntity() == target, "Fixture: the barrage hit " + action.punchedTarget);
			component.setAction(null, user, SyncType.NO_SYNC);
			int[] counts = new int[2];
			for (Packet<?> packet : connection.packets) {
				if (packet instanceof ClientboundCustomPayloadPacket custom
						&& custom.payload() instanceof TrBarrageHitSoundPacket hitSound
						&& hitSound.standEntityId() == stand.getId()) {
					counts[0]++;
					if (hitSound.hit()) counts[1]++;
				}
			}
			return counts;
		}

		void close() {
			cleanup.forEach(Runnable::run);
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			if (power.isSummoned()) type.forceUnsummon(user, power);
			entities.forEach(Entity::discard);
		}
	}

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
