package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.HamonProjectileShieldEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonProjectileShieldEntity turned back only projectiles crossing the shield plane toward its user and not
 * about to hit a block. Each one cost speed * 20 energy and gave Control points; with no energy the shield broke.
 * The shield went away as soon as the user stopped holding Projectile Shield. It kept the facing it was raised with,
 * centered on the user's mid-height 2 blocks ahead, and each projectile reaching it made sparks where it hit.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonProjectileShieldGameTests {
	private static final short KEY = 9;

	private HamonProjectileShieldGameTests() {}

	// A neighbouring test's time stop over the user pauses the held shield action: these run in a batch that stops no time.

	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.NO_TIME_STOP)
	public static void shieldTurnsBackOnlyIncomingProjectilesForEnergy(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			HamonProjectileShieldEntity shield = f.holdShield();
			Vec3 c = shield.position();
			Snowball incoming = f.snowball(c.add(0.0D, 0.0D, 0.5D), new Vec3(0.0D, 0.0D, -2.0D));
			Snowball away = f.snowball(c.add(1.0D, 0.0D, 0.5D), new Vec3(0.0D, 0.0D, 1.5D));
			Snowball behind = f.snowball(c.add(-1.0D, 0.0D, -0.5D), new Vec3(0.0D, 0.0D, 2.0D));
			Snowball outside = f.snowball(c.add(5.0D, 0.0D, 0.5D), new Vec3(0.0D, 0.0D, -2.0D));
			// A block right behind the plane: 1.16 left a projectile about to hit a block alone.
			f.placeStone(BlockPos.containing(c.x - 3.0D, c.y, c.z + 0.8D));
			Snowball intoBlock = f.snowball(c.add(-3.0D, 0.0D, 1.8D), new Vec3(0.0D, 0.0D, -2.5D));
			float energyBefore = f.hamon.getEnergy();
			int controlBefore = f.hamon.getHamonControlPoints();

			shield.tick();
			Vec3 turned = incoming.getDeltaMovement();
			helper.assertTrue(turned.z > 1.99D && Math.abs(turned.length() - 2.0D) < 0.01D,
					"the incoming projectile was not turned back at full speed: " + turned);
			helper.assertTrue(incoming.getZ() > c.z + 2.4D, "1.16 moved the deflected projectile once: z=" + (incoming.getZ() - c.z));
			helper.assertTrue(away.getDeltaMovement().z == 1.5D, "a projectile flying away was deflected: " + away.getDeltaMovement());
			helper.assertTrue(behind.getDeltaMovement().z == 2.0D, "a projectile behind the shield was deflected: " + behind.getDeltaMovement());
			helper.assertTrue(outside.getDeltaMovement().z == -2.0D, "a projectile beside the shield was deflected: " + outside.getDeltaMovement());
			helper.assertTrue(intoBlock.getDeltaMovement().z == -2.5D, "a projectile about to hit a block was deflected: " + intoBlock.getDeltaMovement());
			helper.assertTrue(Math.abs(f.hamon.getEnergy() - (energyBefore - 40.0F)) < 0.01F,
					"one deflection at speed 2 must cost 40 energy: " + energyBefore + " -> " + f.hamon.getEnergy());

			shield.tick();
			helper.assertTrue(incoming.getDeltaMovement().z > 1.99D, "the deflected projectile was turned back again: " + incoming.getDeltaMovement());
			helper.assertTrue(Math.abs(f.hamon.getEnergy() - (energyBefore - 40.0F)) < 0.01F, "a second tick charged again: " + f.hamon.getEnergy());

			// speed 40 costs 800 energy, over one Control point (750 energy each)
			f.snowball(c.add(0.0D, 0.0D, 0.5D), new Vec3(0.0D, 0.0D, -40.0D));
			shield.tick();
			helper.assertTrue(f.hamon.getHamonControlPoints() > controlBefore,
					"a deflection gave no Control points: " + controlBefore + " -> " + f.hamon.getHamonControlPoints());
			helper.assertTrue(shield.isAlive(), "the shield broke although it had energy");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.NO_TIME_STOP)
	public static void shieldBreaksWithoutEnergyUntilTheNextPress(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			HamonProjectileShieldEntity shield = f.holdShield();
			Vec3 c = shield.position();
			f.hamon.setEnergy(0.0F);
			f.hamon.setBreathStability(0.0F);
			Snowball incoming = f.snowball(c.add(0.0D, 0.0D, 0.5D), new Vec3(0.0D, 0.0D, -2.0D));

			shield.tick();
			helper.assertTrue(shield.isRemoved(), "the shield did not break with no energy");
			helper.assertTrue(incoming.getDeltaMovement().z == -2.0D, "the shield deflected with no energy: " + incoming.getDeltaMovement());

			f.hamon.setBreathStability(f.hamon.getMaxBreathStability());
			f.hamon.setEnergy(f.hamon.getMaxEnergy());
			f.user.setAirSupply(f.user.getMaxAirSupply());
			f.tick(2);
			helper.assertTrue(f.action.getPhase() == ActionPhase.PERFORM, "the hold ended: phase=" + f.action.getPhase());
			helper.assertTrue(f.findShield() == null, "a broken shield came back during the same hold");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.NO_TIME_STOP)
	public static void shieldGoesAwayWhenTheHoldEnds(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			HamonProjectileShieldEntity shield = f.holdShield();
			AbilityInput.keyRelease(KEY, f.user);
			helper.assertTrue(f.action.getPhase() != ActionPhase.PERFORM, "releasing did not end the hold: phase=" + f.action.getPhase());
			shield.tick();
			helper.assertTrue(shield.isRemoved(), "the shield stayed after the hold ended");
			helper.succeed();
		}
	}

	// 1.16 startedHolding copied the user's facing once; after that the shield only followed the user's position
	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.NO_TIME_STOP)
	public static void shieldKeepsTheFacingItWasRaisedWith(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			f.user.setYRot(-90.0F);
			f.user.setXRot(0.0F);
			f.user.setYHeadRot(-90.0F);
			HamonProjectileShieldEntity shield = f.holdShield();
			helper.assertTrue(shield.getYRot() == -90.0F && shield.getXRot() == 0.0F,
					"the shield did not take the facing it was raised with: " + shield.getYRot() + "/" + shield.getXRot());

			// the user steps aside and looks west and up
			f.user.setPos(f.user.getX(), f.user.getY(), f.user.getZ() + 1.0D);
			f.user.setYRot(90.0F);
			f.user.setXRot(-30.0F);
			f.user.setYHeadRot(90.0F);
			f.tick(1);
			f.hamon.setEnergy(f.hamon.getMaxEnergy());
			Vec3 mid = new Vec3(f.user.getX(), f.user.getY(0.5D), f.user.getZ());
			Vec3 look = Vec3.directionFromRotation(-30.0F, 90.0F);
			Snowball raisedSide = f.snowball(mid.add(2.5D, 0.0D, 0.0D), new Vec3(-2.0D, 0.0D, 0.0D));
			Snowball lookSide = f.snowball(mid.add(look.scale(2.5D)), look.scale(-2.0D));
			shield.tick();
			helper.assertTrue(shield.getYRot() == -90.0F && shield.getXRot() == 0.0F,
					"the shield turned with its user's view: " + shield.getYRot() + "/" + shield.getXRot());
			helper.assertTrue(shield.position().distanceTo(mid.add(2.0D, 0.0D, 0.0D)) < 1.0E-6D,
					"the shield center is not mid-height + 2 along its own facing: " + shield.position().subtract(mid));
			helper.assertTrue(raisedSide.getDeltaMovement().x > 1.99D,
					"a projectile crossing the raised plane was not deflected: " + raisedSide.getDeltaMovement());
			helper.assertTrue(lookSide.getDeltaMovement().distanceTo(look.scale(-2.0D)) < 1.0E-6D,
					"the shield covered the user's new view direction: " + lookSide.getDeltaMovement());
			helper.succeed();
		}
	}

	// 1.16: each projectile reaching the plane made a spark burst with a crackle where it hit, even with no energy left
	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.NO_TIME_STOP)
	public static void shieldSparksWhereAProjectileHitsIt(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper); SparkSounds sparks = new SparkSounds(helper)) {
			HamonProjectileShieldEntity shield = f.holdShield();
			sparks.at.clear();
			Vec3 c = shield.position();
			Vec3 firstHit = c.add(1.3D, 0.7D, 0.0D);
			f.snowball(firstHit.add(0.0D, 0.0D, 0.5D), new Vec3(0.0D, 0.0D, -2.0D));
			shield.tick();
			helper.assertTrue(sparks.at.size() == 1 && sparks.at.get(0).distanceTo(firstHit) < 1.0E-6D,
					"a deflection made no sparks where the projectile hit the shield: " + sparks.at + " vs " + firstHit);

			f.hamon.setEnergy(0.0F);
			f.hamon.setBreathStability(0.0F);
			Vec3 secondHit = c.add(-2.0D, 1.0D, 0.0D);
			f.snowball(secondHit.add(0.0D, 0.0D, 0.5D), new Vec3(0.0D, 0.0D, -2.0D));
			shield.tick();
			helper.assertTrue(shield.isRemoved(), "the shield did not break with no energy");
			helper.assertTrue(sparks.at.size() == 2 && sparks.at.get(1).distanceTo(secondHit) < 1.0E-6D,
					"a projectile that broke the shield made no sparks: " + sparks.at + " vs " + secondHit);
			helper.succeed();
		}
	}

	// server spark sounds; HamonUtil.emitHamonSparkParticles plays one with each spark burst
	private static final class SparkSounds implements AutoCloseable {
		private final List<Vec3> at = new ArrayList<>();
		private final Consumer<PlayLevelSoundEvent.AtPosition> listener;

		private SparkSounds(GameTestHelper helper) {
			listener = event -> {
				Holder<SoundEvent> sound = event.getSound();
				if (event.getLevel() == helper.getLevel() && sound != null && sound.value() == ModSoundEvents.HAMON_SPARK.get()) {
					at.add(event.getPosition());
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayLevelSoundEvent.AtPosition.class, listener);
		}

		@Override
		public void close() {
			NeoForge.EVENT_BUS.unregister(listener);
		}
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final PlayerPower power;
		private final HamonData hamon;
		private final LivingComponentAction component;
		private final List<Snowball> snowballs = new ArrayList<>();
		private BlockPos stone;
		private EntityActionInstance action;

		private Fixture(GameTestHelper helper) {
			this.helper = helper;
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Projectile Shield test player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.PROJECTILE_SHIELD.get());
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			component = LivingComponentAction.getComponent(user);
		}

		private HamonProjectileShieldEntity holdShield() {
			Ability found = power.getAbility("projectile_shield");
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered projectile_shield");
			action = ((EntityActionAbility) found).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, PowerClass.PLAYER_POWER, action));
			// 1.16 holdType: the shield rose on the press, so one tick is enough
			tick(1);
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM,
					"Projectile Shield must hold from the press with no windup: phase=" + action.getPhase());
			HamonProjectileShieldEntity shield = findShield();
			helper.assertTrue(shield != null, "the first held Projectile Shield tick made no shield");
			hamon.setEnergy(hamon.getMaxEnergy());
			return shield;
		}

		private HamonProjectileShieldEntity findShield() {
			return helper.getLevel().getEntitiesOfClass(HamonProjectileShieldEntity.class, user.getBoundingBox().inflate(8.0D),
					shield -> shield.isAlive() && shield.getOwnerEntity() == user).stream().findFirst().orElse(null);
		}

		private Snowball snowball(Vec3 pos, Vec3 motion) {
			Snowball snowball = new Snowball(helper.getLevel(), pos.x, pos.y, pos.z);
			snowball.setDeltaMovement(motion);
			helper.assertTrue(helper.getLevel().addFreshEntity(snowball), "Could not add a test snowball");
			snowballs.add(snowball);
			return snowball;
		}

		private void placeStone(BlockPos pos) {
			stone = pos;
			helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		}

		private void tick(int count) {
			for (int tick = 0; tick < count; tick++) {
				user.tickCount++;
				component.tick();
			}
		}

		@Override
		public void close() {
			snowballs.forEach(Snowball::discard);
			if (stone != null) {
				helper.getLevel().setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());
			}
			HamonProjectileShieldEntity shield = findShield();
			if (shield != null) {
				shield.discard();
			}
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			component.setAction(null, SyncType.NO_SYNC);
			user.removeAllEffects();
			user.discard();
		}
	}
}
