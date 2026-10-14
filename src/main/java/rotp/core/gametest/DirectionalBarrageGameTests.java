package rotp.core.gametest;

import java.lang.reflect.Method;
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
import rotp.core.init.ModStatusEffects;
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
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands.silverchariot.SilverChariotState;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

	// the owner's 2026-10-14 rule: the default barrage ends at the 1.16 hold maximum; a miss still does not end it
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void heldEmptyBarrageKeepsActionUntilTheHoldMaximum(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "held_empty", true)) {
			f.lookAt(f.user.getEyePosition().add(0, 2, -3));
			EntityActionInstance action = f.start("barrage");
			helper.assertTrue(StandEntityBarrageAbility.isDirectionalBarrage(f.stand, action),
					"Fixture: the default barrage is not the directional one");
			int id = action.id;
			long generation = action.networkGeneration();
			int maximum = StandStatFormulas.getBarrageMaxDuration(f.stand.getDurability());
			f.tick(maximum);
			helper.assertTrue(f.component.getAction() == action && action.id == id
					&& action.networkGeneration() == generation, "Held empty barrage replaced its action");
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM && action.getPhaseTick() == maximum,
					"Held empty barrage left PERFORM before the 1.16 hold maximum " + maximum + ": phase "
							+ action.getPhase() + " tick " + action.getPhaseTick());
			f.assertUnlocked(action);
			f.tick(1);
			helper.assertTrue(action.getPhase() == ActionPhase.RECOVERY && f.stand.barrageHits == 0,
					"The default barrage held past the 1.16 hold maximum of 20 + 5 x durability = " + maximum
							+ " ticks without Resolve: phase " + action.getPhase() + " tick " + action.getPhaseTick());
			f.tick(30);
			helper.assertTrue(f.component.getAction() == null, "Ended barrage did not finish recovery");
			helper.succeed();
		}
	}

	// 1.16 PowerBaseImpl.tickHeldAction read getHoldDurationMax on every tick, Resolve included
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void resolveLiftsAndRestoresTheBarrageHoldMaximum(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "resolve_hold", true)) {
			f.lookAt(f.user.getEyePosition().add(0, 2, -3));
			EntityActionInstance action = f.start("barrage");
			int maximum = StandStatFormulas.getBarrageMaxDuration(f.stand.getDurability());
			f.tick(maximum - 5);
			f.user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400));
			f.tick(20);
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.PERFORM,
					"Resolve gained mid-barrage did not lift the hold maximum " + maximum + ": phase " + action.getPhase());
			f.user.removeEffect(ModStatusEffects.RESOLVE);
			f.tick(1);
			helper.assertTrue(action.getPhase() == ActionPhase.RECOVERY,
					"Resolve lost past the hold maximum did not end the barrage: phase " + action.getPhase()
							+ " tick " + action.getPhaseTick() + ", maximum " + maximum);
			helper.succeed();
		}
	}

	// 1.16 StandEntityMeleeBarrage.getStandRecoveryTicks, read by StandEntityTask.moveToPhase as RECOVERY starts
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void barrageReadsItsRecoveryWhenRecoveryStarts(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "recovery_entry", true)) {
			f.lookAt(f.user.getEyePosition().add(0, 2, -3));
			EntityActionInstance faster = f.start("barrage");
			float atStart = faster.phasesLength.getFloat(ActionPhase.RECOVERY);
			helper.assertTrue(atStart == StandStatFormulas.getBarrageRecovery(f.stand.getAttackSpeed()) && atStart > 0,
					"Fixture: recovery at the start " + atStart);
			f.tick(3);
			AttributeModifier boost = new AttributeModifier(JojoMod.resLoc("barrage_recovery_entry_test"), 12,
					AttributeModifier.Operation.ADD_VALUE);
			f.stand.getAttribute(Attributes.ATTACK_SPEED).addTransientModifier(boost);
			try {
				int expected = StandStatFormulas.getBarrageRecovery(f.stand.getAttackSpeed());
				helper.assertTrue(expected != atStart, "Fixture: the speed change does not change the recovery");
				faster.onKeyRelease(f.user);
				helper.assertTrue(faster.getPhase() == ActionPhase.RECOVERY
						&& faster.phasesLength.getFloat(ActionPhase.RECOVERY) == expected,
						"1.16 read the barrage recovery from the attack speed as RECOVERY started: expected " + expected
								+ ", got " + faster.phasesLength.getFloat(ActionPhase.RECOVERY) + " (" + atStart + " at the start)");
			}
			finally {
				f.stand.getAttribute(Attributes.ATTACK_SPEED).removeModifier(boost.id());
			}
			f.tick(30);

			EntityActionInstance armsOnly = f.start("barrage");
			helper.assertTrue(armsOnly.phasesLength.getFloat(ActionPhase.RECOVERY) > 0, "Fixture: recovery at the start is 0");
			f.tick(3);
			f.stand.setArmsOnlyMode(true, true);
			armsOnly.onKeyRelease(f.user);
			helper.assertTrue(armsOnly.getPhase() != ActionPhase.PERFORM
					&& armsOnly.phasesLength.getFloat(ActionPhase.RECOVERY) == 0,
					"1.16 gave no barrage recovery to a Stand arms-only as RECOVERY started, got "
							+ armsOnly.phasesLength.getFloat(ActionPhase.RECOVERY));
			helper.succeed();
		}
	}

	// 1.16 StandEntityAction.isCancelable read getStandRecoveryTicks live: a Stand turned arms-only mid-barrage (no
	// recovery) lets another action cancel the barrage during PERFORM
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void barrageCancelDuringPerformReadsTheRecoveryThen(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "recovery_cancel", true)) {
			f.lookAt(f.user.getEyePosition().add(0, 2, -3));
			EntityActionInstance barrage = f.start("barrage");
			EntityActionType heavy = (EntityActionType) f.power.getAbility("heavy_punch");
			f.tick(3);
			helper.assertTrue(barrage.getPhase() == ActionPhase.PERFORM && !barrage.canBeCancelledInto(heavy),
					"Fixture: a barrage with a recovery could be cancelled during PERFORM, phase " + barrage.getPhase());
			f.stand.setArmsOnlyMode(true, true);
			helper.assertTrue(barrage.getPhase() == ActionPhase.PERFORM && barrage.canBeCancelledInto(heavy),
					"1.16 let another action cancel the barrage of a Stand that turned arms-only during PERFORM; the port"
							+ " still read the recovery of " + barrage.phasesLength.getFloat(ActionPhase.RECOVERY) + " set at its start");
			barrage.onKeyRelease(f.user);
			helper.succeed();
		}
	}

	// 1.16 SilverChariotMeleeBarrage.getHoldDurationMax: no limit while the armor is off, read on every tick
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void silverChariotWithoutArmorBarragesWithoutLimit(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "chariot_no_armor", true, "silver_chariot")) {
			SilverChariotState state = SilverChariotState.get(f.user);
			state.setHasArmor(false);
			f.stand.summonLockTicks = 0;
			try {
				f.lookAt(f.user.getEyePosition().add(0, 2, -3));
				EntityActionInstance action = f.start("melee_barrage");
				int maximum = StandStatFormulas.getBarrageMaxDuration(f.stand.getDurability());
				helper.assertTrue(f.holdMaximum(action) == Integer.MAX_VALUE,
						"Silver Chariot without its armor got a hold maximum of " + f.holdMaximum(action));
				f.tick(maximum + 5);
				helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.PERFORM
						&& action.getPhaseTick() == maximum + 5,
						"Silver Chariot without its armor stopped its barrage at " + maximum + " ticks: phase "
								+ action.getPhase() + " tick " + action.getPhaseTick());
				state.setHasArmor(true);
				helper.assertTrue(f.holdMaximum(action) < Integer.MAX_VALUE,
						"Silver Chariot's armor back on did not bring back the hold maximum");
				// the armor also raises the durability the maximum is read from
				for (int tick = 0; tick < 200 && action.getPhase() == ActionPhase.PERFORM; tick++) {
					f.tick(1);
				}
				helper.assertTrue(action.getPhase() != ActionPhase.PERFORM,
						"Silver Chariot's armored barrage held past its maximum " + f.holdMaximum(action)
								+ ": phase tick " + action.getPhaseTick());
			}
			finally {
				state.setHasArmor(true);
			}
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
					&& f.holdMaximum(locked) == oldDuration,
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
					&& f.holdMaximum(grab) == oldDuration,
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
			this(helper, name, infiniteStamina, "star_platinum");
		}

		private Fixture(GameTestHelper helper, String name, boolean infiniteStamina, String standId) {
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
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			helper.assertTrue(standType != null, "Missing Stand type " + standId);
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

		private int holdMaximum(EntityActionInstance action) {
			try {
				Method method = StandEntityBarrage.class.getDeclaredMethod("getHoldDurationMax", StandEntity.class);
				method.setAccessible(true);
				return (Integer) method.invoke(action, stand);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Missing barrage hold maximum", e);
			}
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
			user.removeEffect(ModStatusEffects.RESOLVE);
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
