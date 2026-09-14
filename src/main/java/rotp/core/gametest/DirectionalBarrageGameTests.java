package rotp.core.gametest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
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
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DirectionalBarrageGameTests {
	private DirectionalBarrageGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void heldEmptyBarrageKeepsActionAndPhaseClock(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "held_empty", true)) {
			f.lookAt(f.user.getEyePosition().add(0, 2, -3));
			EntityActionInstance action = f.start("barrage");
			int id = action.id;
			long generation = action.networkGeneration();
			float phaseTick = action.getPhaseTick();
			int oldDuration = StandStatFormulas.getBarrageMaxDuration(f.stand.getDurability());
			f.tick(oldDuration + 40);
			helper.assertTrue(f.component.getAction() == action && action.id == id
					&& action.networkGeneration() == generation, "Held empty barrage replaced its action");
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM
					&& action.getPhaseTick() > phaseTick + oldDuration,
					"Held empty barrage ended or clamped its animation phase clock");
			f.assertUnlocked(action);
			action.onKeyRelease(f.user);
			helper.assertTrue(action.getPhase() == ActionPhase.RECOVERY || action.isOver(),
					"Releasing held directional barrage did not leave PERFORM");
			f.tick(30);
			helper.assertTrue(f.component.getAction() == null, "Released barrage did not finish recovery");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void directionalBarrageSweepsWithoutRetainingTargets(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "sweep", true)) {
			Cow first = f.cow(0, 0, 2);
			Cow second = f.cow(1.9, 0, 1.5);
			Cow side = f.cow(-1.7, 0, 1.6);
			Cow behind = f.cow(0, 0, -1.5);
			f.lookAt(first.getBoundingBox().getCenter());
			f.component.entityAim.setTarget(new ActionTarget(side));
			EntityActionInstance action = f.start("barrage");
			long generation = action.networkGeneration();
			float secondHealth = second.getHealth();
			f.hit(first);
			helper.assertTrue(second.getHealth() == secondHealth, "Initial barrage hit a different forward entity");
			float firstHealth = first.getHealth();
			f.lookAt(second.getBoundingBox().getCenter());
			f.hit(second);
			helper.assertTrue(first.getHealth() == firstHealth, "Swept barrage retained its previous live target");
			second.setHealth(0);
			f.tick(1);
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.PERFORM,
					"Death of the currently aimed target ended directional barrage");
			f.lookAt(f.user.getEyePosition().add(0, 3, 0));
			secondHealth = second.getHealth();
			f.tick(12);
			helper.assertTrue(second.getHealth() == secondHealth && side.getHealth() == side.getMaxHealth()
					&& behind.getHealth() == behind.getMaxHealth(), "Empty directional ray hit a retained or off-axis entity");
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.PERFORM
					&& action.networkGeneration() == generation, "Miss or target death restarted or ended barrage");
			f.assertUnlocked(action);
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void directionalBarrageRespectsNearestHitWallAndRange(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "occlusion", true)) {
			Cow nearest = f.cow(0, 0, 1.4);
			Cow farther = f.cow(0, 0, 2.7);
			Cow outsideRange = f.cow(0, 0, 8);
			f.lookAt(farther.getBoundingBox().getCenter());
			EntityActionInstance action = f.start("barrage");
			helper.assertTrue(f.clip(action).getMainEntity() == nearest, "Strict barrage ray skipped the nearest entity");
			f.hit(nearest);
			helper.assertTrue(farther.getHealth() == farther.getMaxHealth(), "Barrage added full damage to a second intersection");
			nearest.discard();
			BlockPos wall = BlockPos.containing(f.origin.add(0, 1, 1));
			f.block(wall, Blocks.BEDROCK.defaultBlockState());
			f.block(wall.above(), Blocks.BEDROCK.defaultBlockState());
			helper.assertTrue(f.clip(action).getType() == ActionTarget.TargetType.BLOCK,
					"Directional ray did not stop at the blocking surface");
			f.tick(12);
			helper.assertTrue(farther.getHealth() == farther.getMaxHealth(), "Directional barrage damaged through a wall");
			f.block(wall, Blocks.AIR.defaultBlockState());
			f.block(wall.above(), Blocks.AIR.defaultBlockState());
			farther.discard();
			f.lookAt(outsideRange.getBoundingBox().getCenter());
			f.tick(12);
			helper.assertTrue(outsideRange.getHealth() == outsideRange.getMaxHealth(), "Directional barrage exceeded its range");
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.PERFORM,
					"Blocked or out-of-range barrage ended instead of continuing swings");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void directionalBarragePaysOnceAndStopsBeforeUnpaidHit(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "stamina", false)) {
			helper.assertTrue(!f.power.isStaminaInfinite(), "Finite-stamina fixture is unexpectedly infinite");
			Cow target = f.cow(0, 0, 1.5);
			f.lookAt(target.getBoundingBox().getCenter());
			EntityActionInstance action = f.start("barrage");
			f.power.setStamina(10);
			float gain = f.power.getStaminaTickGain();
			f.tick(1);
			float expected = Math.max(0, Math.min(f.power.getMaxStamina(), 10 + gain - 4));
			helper.assertTrue(Math.abs(f.power.getStamina() - expected) < 0.001F,
					"Directional barrage did not charge exactly once: expected=" + expected + ", actual=" + f.power.getStamina());
			f.power.setStamina(3);
			float health = target.getHealth();
			f.component.tick();
			helper.assertTrue(target.getHealth() == health, "Insufficient stamina allowed a free barrage hit");
			helper.assertTrue(action.getPhase() != ActionPhase.PERFORM && f.stand.barrageHits == 0,
					"Exhausted barrage did not leave PERFORM and clear its hit count");
			helper.assertTrue(f.power.getStamina() == 0, "Failed standard stamina payment did not exhaust stamina");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void legacyLockAndGrabBarrageKeepTheirLifecycle(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "legacy_grab", true)) {
			Cow target = f.cow(0, 0, 1.5);
			f.settings.standAttackTargetLock = true;
			f.component.entityAim.setTarget(new ActionTarget(target));
			f.lookAt(f.user.getEyePosition().add(0, 2, -3));
			EntityActionInstance locked = f.start("barrage");
			int oldDuration = StandStatFormulas.getBarrageMaxDuration(f.stand.getDurability());
			helper.assertTrue(!StandEntityBarrageAbility.isDirectionalBarrage(f.stand, locked)
					&& locked.phasesLength.getFloat(ActionPhase.PERFORM) == oldDuration,
					"Legacy locked barrage duration or mode changed");
			f.hit(target);
			f.tick(oldDuration + 30);
			helper.assertTrue(f.component.getAction() == null, "Legacy locked barrage lost its finite duration");

			Cow grabbed = f.cow(0, 0, 1.2);
			f.settings.standAttackTargetLock = false;
			f.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(grabbed);
			f.component.entityAim.setTarget(new ActionTarget(grabbed));
			EntityActionInstance grab = f.start("grab_barrage");
			helper.assertTrue(!StandEntityBarrageAbility.isDirectionalBarrage(f.stand, grab)
					&& grab.phasesLength.getFloat(ActionPhase.PERFORM) == oldDuration,
					"Grab barrage incorrectly entered continuous directional mode");
			f.component.entityAim.setTarget(ActionTarget.EMPTY);
			f.hit(grabbed);
			helper.succeed();
		}
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final StandType standType;
		private final StandPower power;
		private final StandEntity stand;
		private final LivingComponentAction component;
		private final PlayerClientBroadcastedSettings settings;
		private final Vec3 origin;
		private final List<Cow> targets = new ArrayList<>();
		private final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();

		private Fixture(GameTestHelper helper, String name, boolean infiniteStamina) {
			this.helper = helper;
			String profileName = "DirBarrage_" + name;
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
					UUID.nameUUIDFromBytes(profileName.getBytes(StandardCharsets.US_ASCII)), profileName));
			origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z);
			user.getAbilities().instabuild = infiniteStamina;
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add directional-barrage player");
			power = PowerClass.STAND.attachGet(user);
			settings = user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
			settings.standAttackTargetLock = false;
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(standType != null, "Missing Star Platinum Stand type");
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant directional-barrage Stand");
			helper.assertTrue(standType.summon(user, power), "Could not summon directional-barrage Stand");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Missing directional-barrage Stand entity");
			stand.moveTo(origin.x, origin.y, origin.z);
			component = LivingComponentAction.getComponent(stand);
			power.setStamina(power.getMaxStamina());
		}

		private EntityActionInstance start(String name) {
			Ability ability = power.getAbility(name);
			helper.assertTrue(ability instanceof EntityActionType, "Missing barrage action " + name);
			EntityActionInstance action = ((EntityActionType) ability)
					.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			return action;
		}

		private Cow cow(double x, double y, double z) {
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Could not create barrage target");
			Vec3 pos = origin.add(x, y, z);
			cow.moveTo(pos.x, pos.y, pos.z);
			cow.setNoAi(true);
			cow.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add barrage target");
			targets.add(cow);
			return cow;
		}

		private void lookAt(Vec3 position) {
			Vec3 delta = position.subtract(user.getEyePosition());
			float yaw = (float) -Math.toDegrees(Math.atan2(delta.x, delta.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
			user.setYRot(yaw);
			user.setYHeadRot(yaw);
			user.setXRot(pitch);
			user.yRotO = yaw;
			user.xRotO = pitch;
		}

		private ActionTarget clip(EntityActionInstance action) {
			return StandEntityBarrageAbility.clipDirectionalBarrageTarget(stand, action, 1.0F);
		}

		private void tick(int count) {
			for (int i = 0; i < count; i++) {
				stand.tickCount++;
				component.tick();
				power.tick();
			}
		}

		private void hit(Cow target) {
			float health = target.getHealth();
			for (int tick = 0; tick < 12 && target.getHealth() >= health; tick++) {
				tick(1);
			}
			helper.assertTrue(target.getHealth() < health, "Barrage did not damage the expected current target");
		}

		private void assertUnlocked(EntityActionInstance action) {
			helper.assertTrue(action.standRotationTarget == null && action.aimAs == AimingEntity.CAMERA_ENTITY,
					"Directional barrage retained an entity rotation lock");
		}

		private void block(BlockPos position, BlockState state) {
			originalBlocks.putIfAbsent(position, helper.getLevel().getBlockState(position));
			helper.getLevel().setBlockAndUpdate(position, state);
		}

		@Override
		public void close() {
			if (power.isSummoned()) {
				standType.forceUnsummon(user, power);
			}
			for (Cow target : targets) target.discard();
			for (Map.Entry<BlockPos, BlockState> block : originalBlocks.entrySet()) {
				helper.getLevel().setBlockAndUpdate(block.getKey(), block.getValue());
			}
			user.discard();
		}
	}
}
