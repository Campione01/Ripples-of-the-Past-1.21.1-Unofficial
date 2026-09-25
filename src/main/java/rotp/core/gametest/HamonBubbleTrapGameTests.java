package rotp.core.gametest;

import java.util.UUID;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.HamonBubbleBarrierEntity;
import rotp.core.impl.powers.hamon.entity.HamonBubbleEntity;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonBubbleBarrierEntity: the trap lasts 100 ticks times the Bubble Barrier efficiency, stuns its captive for
 * that long, deals it 0.002 Hamon damage on 3 of every 5 ticks, pops once the captive is out and frees it from the
 * stun when it goes. 1.16 bubble hits (launcher and barrier) trained Strength by a quarter of the held tick cost.
 * Projectiles are ticked by hand so each step is checked in order.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonBubbleTrapGameTests {
	private HamonBubbleTrapGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void barrierTrapStunsTricklesAndPops(GameTestHelper helper) {
		Player owner = hamonUser(helper, "BubbleTrapOwner");
		Pig pig = target(helper);
		HamonBubbleBarrierEntity barrier = null;
		try {
			HamonData hamon = hamon(owner);
			// A half-stable breath lowers the efficiency, so the trap must come out shorter than the flat 100.
			hamon.setBreathStability(hamon.getMaxBreathStability() / 2F);
			int expected = (int) (100F * hamon.getActionEfficiency(0, true, ModHamonSkills.BUBBLE_BARRIER.get(), owner));
			helper.assertTrue(expected > 0 && expected < 100, "setup: efficiency should shorten the trap, got " + expected);
			barrier = new HamonBubbleBarrierEntity(helper.getLevel(), owner);
			helper.assertTrue(barrier.getBarrierMaxTicks() == expected,
					"the trap length ignores the Bubble Barrier efficiency: expected=" + expected + ", actual=" + barrier.getBarrierMaxTicks());

			float frac = pointsIncFrac(helper, hamon);
			launchAt(helper, barrier, pig, 0);
			helper.assertTrue(pig.getVehicle() == barrier, "the barrier did not trap the pig: " + state(pig, barrier));
			MobEffectInstance stun = pig.getEffect(ModStatusEffects.STUN);
			helper.assertTrue(stun != null && stun.getDuration() == expected,
					"the captive is not stunned for the trap length " + expected + ": stun=" + stun + ", " + state(pig, barrier));
			float fracAfter = pointsIncFrac(helper, hamon);
			helper.assertTrue(fracAfter > frac,
					"a Bubble Barrier hit gave no Hamon Strength training: PointsIncFrac " + frac + " -> " + fracAfter);

			// 7 % 5 % 2 == 0: a trickle tick; 8 % 5 % 2 == 1: no trickle.
			float health = pig.getHealth();
			barrier.tickCount = 7;
			barrier.tick();
			helper.assertTrue(barrier.isAlive() && pig.getVehicle() == barrier, "the barrier ended early: " + state(pig, barrier));
			helper.assertTrue(pig.getHealth() < health,
					"the captive took no Hamon trickle damage: health before=" + health + ", " + state(pig, barrier));
			health = pig.getHealth();
			barrier.tickCount = 8;
			barrier.tick();
			helper.assertTrue(pig.getHealth() == health,
					"the trickle hit on an off tick: health before=" + health + ", " + state(pig, barrier));

			pig.stopRiding();
			barrier.tickCount = 9;
			barrier.tick();
			helper.assertTrue(barrier.isRemoved(), "an empty barrier did not pop: " + state(pig, barrier));
			helper.succeed();
		}
		finally {
			cleanup(barrier, pig, owner);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void timedOutBarrierFreesCaptiveFromStun(GameTestHelper helper) {
		Player owner = hamonUser(helper, "BubbleTrapTimeout");
		Pig pig = target(helper);
		HamonBubbleBarrierEntity barrier = null;
		try {
			HamonData hamon = hamon(owner);
			hamon.setBreathStability(hamon.getMaxBreathStability() / 2F);
			barrier = new HamonBubbleBarrierEntity(helper.getLevel(), owner);
			int max = barrier.getBarrierMaxTicks();
			// The barrier charged and flew for 30 ticks before it trapped; the trap still gets its full length.
			launchAt(helper, barrier, pig, 30);
			helper.assertTrue(pig.getVehicle() == barrier && pig.hasEffect(ModStatusEffects.STUN),
					"the barrier did not trap and stun the pig: " + state(pig, barrier));
			int ticks = 0;
			while (!barrier.isRemoved() && ticks < 200) {
				barrier.tickCount++;
				barrier.tick();
				ticks++;
			}
			helper.assertTrue(barrier.isRemoved(), "the barrier never timed out: " + state(pig, barrier));
			helper.assertTrue(Math.abs(ticks - max) <= 1,
					"the trap did not last its length " + max + ": " + ticks + " ticks, " + state(pig, barrier));
			helper.assertTrue(pig.getVehicle() == null, "the captive is still riding the removed barrier: " + state(pig, barrier));
			helper.assertTrue(!pig.hasEffect(ModStatusEffects.STUN), "the removed barrier left its captive stunned: " + state(pig, barrier));
			helper.succeed();
		}
		finally {
			cleanup(barrier, pig, owner);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void bubbleLauncherHitTrainsStrength(GameTestHelper helper) {
		Player owner = hamonUser(helper, "BubbleLauncherOwner");
		Pig pig = target(helper);
		HamonBubbleEntity bubble = null;
		try {
			HamonData hamon = hamon(owner);
			float frac = pointsIncFrac(helper, hamon);
			float health = pig.getHealth();
			bubble = new HamonBubbleEntity(owner, helper.getLevel());
			launchAt(helper, bubble, pig, 0);
			helper.assertTrue(pig.getHealth() < health,
					"the bubble did not hit the pig: health " + health + " -> " + pig.getHealth() + ", bubble removed=" + bubble.isRemoved());
			float fracAfter = pointsIncFrac(helper, hamon);
			helper.assertTrue(fracAfter > frac,
					"a Bubble Launcher hit gave no Hamon Strength training: PointsIncFrac " + frac + " -> " + fracAfter);
			helper.succeed();
		}
		finally {
			cleanup(bubble, pig, owner);
		}
	}

	// 1.16 hurtTarget returned dealHamonDamage: a hit whose Hamon damage does not land trains nothing.
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void bubbleLauncherHitWithoutDamageTrainsNothing(GameTestHelper helper) {
		Player owner = hamonUser(helper, "BubbleLauncherNoDmg");
		Pig pig = target(helper);
		HamonBubbleEntity bubble = null;
		try {
			HamonData hamon = hamon(owner);
			hamonImmune(helper, owner, pig);
			float frac = pointsIncFrac(helper, hamon);
			float health = pig.getHealth();
			bubble = new HamonBubbleEntity(owner, helper.getLevel());
			launchAt(helper, bubble, pig, 0);
			helper.assertTrue(bubble.isRemoved() && pig.getHealth() == health,
					"setup: the bubble should hit the Hamon-charged pig without hurting it: health " + health + " -> "
							+ pig.getHealth() + ", bubble removed=" + bubble.isRemoved());
			float fracAfter = pointsIncFrac(helper, hamon);
			helper.assertTrue(fracAfter == frac,
					"a Bubble Launcher hit that dealt no damage still trained Strength: PointsIncFrac " + frac + " -> " + fracAfter);
			helper.succeed();
		}
		finally {
			cleanup(bubble, pig, owner);
		}
	}

	// 1.16: no trap, stun or training when the barrier's Hamon damage does not land; a vulnerable pig is then trapped.
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void barrierHitWithoutDamageDoesNotTrap(GameTestHelper helper) {
		Player owner = hamonUser(helper, "BubbleTrapNoDmg");
		Pig pig = target(helper);
		HamonBubbleBarrierEntity barrier = null;
		HamonBubbleBarrierEntity control = null;
		try {
			HamonData hamon = hamon(owner);
			hamonImmune(helper, owner, pig);
			float frac = pointsIncFrac(helper, hamon);
			barrier = new HamonBubbleBarrierEntity(helper.getLevel(), owner);
			launchAt(helper, barrier, pig, 0);
			helper.assertTrue(pig.getVehicle() == null && !pig.hasEffect(ModStatusEffects.STUN),
					"a barrier hit that dealt no damage still trapped or stunned the pig: " + state(pig, barrier));
			float fracAfter = pointsIncFrac(helper, hamon);
			helper.assertTrue(fracAfter == frac,
					"a Bubble Barrier hit that dealt no damage still trained Strength: PointsIncFrac " + frac + " -> " + fracAfter);

			// Control: the same shot at the pig once its charge is gone does trap it.
			barrier.discard();
			EntityHamonChargeState.get(pig).clear();
			control = new HamonBubbleBarrierEntity(helper.getLevel(), owner);
			launchAt(helper, control, pig, 0);
			helper.assertTrue(pig.getVehicle() == control, "setup: the control barrier did not trap the pig: " + state(pig, control));
			helper.succeed();
		}
		finally {
			cleanup(control, barrier, pig, owner);
		}
	}

	private static Player hamonUser(GameTestHelper helper, String name) {
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 2, 0)));
		user.setPos(origin.x, origin.y, origin.z);
		user.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Hamon test player");
		PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
		power.setPowerType(ModPlayerPowers.HAMON.get());
		return user;
	}

	private static HamonData hamon(Player user) {
		HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
		hamon.setBreathStability(hamon.getMaxBreathStability());
		hamon.setEnergy(hamon.getMaxEnergy());
		return hamon;
	}

	// 1.16 dealHamonDamage: a Hamon-charged target takes no Hamon damage. setInvulnerable would stop the hit itself
	// (1.21 canAttack -> canBeSeenAsEnemy skips invulnerable mobs), so the charge lets the hit land without damage.
	private static void hamonImmune(GameTestHelper helper, Player owner, Pig pig) {
		EntityHamonChargeState.get(pig).setHamonCharge(0.0F, 200, null, 0.0F);
		helper.assertTrue(EntityHamonChargeState.get(pig).hasHamonCharge() && owner.canAttack(pig),
				"setup: the Hamon-charged pig should stay a valid bubble target");
	}

	private static Pig target(GameTestHelper helper) {
		Pig pig = helper.spawn(EntityType.PIG, new Vec3(2.5D, 2.0D, 3.5D));
		// STUN refuses a no-AI mob (1.16 StunEffect.isApplicable), so the pig keeps its AI; it never ticks here.
		pig.setNoAi(false);
		pig.setNoGravity(true);
		return pig;
	}

	// Pig and barrier state for failure messages.
	private static String state(Pig pig, HamonBubbleBarrierEntity barrier) {
		return "pig[vehicle=" + pig.getVehicle() + ", noAi=" + pig.isNoAi() + ", health=" + pig.getHealth()
				+ ", effects=" + pig.getActiveEffects() + "] barrier[removed=" + barrier.isRemoved()
				+ ", isVehicle=" + barrier.isVehicle() + ", tickCount=" + barrier.tickCount
				+ ", maxTicks=" + barrier.getBarrierMaxTicks() + "]";
	}

	// Starts the projectile a block short of the target, flying straight into it, and runs one tick.
	private static void launchAt(GameTestHelper helper, ModdedProjectileEntity projectile, Pig target, int tickCount) {
		Vec3 center = target.getBoundingBox().getCenter();
		projectile.setPos(center.x, center.y, center.z - 1.0D);
		projectile.setDeltaMovement(0.0D, 0.0D, 0.8D);
		helper.assertTrue(helper.getLevel().addFreshEntity(projectile), "Could not add the projectile");
		projectile.tickCount = tickCount;
		projectile.tick();
	}

	private static float pointsIncFrac(GameTestHelper helper, HamonData hamon) {
		return hamon.serializeNBT(helper.getLevel().registryAccess()).getFloat("PointsIncFrac");
	}

	private static void cleanup(Entity... entities) {
		for (Entity entity : entities) {
			if (entity != null && !entity.isRemoved()) {
				if (entity instanceof Player player) {
					player.removeAllEffects();
				}
				entity.discard();
			}
		}
	}
}
