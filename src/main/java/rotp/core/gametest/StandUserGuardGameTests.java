package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoKnockbackOnBlocking;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandLinkDamageSource;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.entity.StandUserGuard;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GameplayEventHandler.standBlockUserAttack / blockDamage: a Stand guarding in front of its user also guards the
 * hits aimed at the user. A hit the Stand cannot take (a zombie, an arrow) loses half the Stand's durability, with no
 * stagger; one it can take (a Stand's) goes to the Stand, which blocks it once; an explosion is cut by angle.
 * 1.16 StandEntity.standDamageResistance: a Stand busy with any task blocks half of a hit it is not guarding.
 * The user stands at the test position facing south (+Z), its Stand half a block in front, facing the same way.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandUserGuardGameTests {
	private static final float EPS = 1.0E-3F;

	private StandUserGuardGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void zombieHitFromFrontLosesHalfTheDurability(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			f.holdGuard();
			float durability = (float) f.stand.getDurability();
			DamageSource source = helper.getLevel().damageSources().mobAttack(f.zombieAt(0, 3));
			float amount = f.scaled(source, durability / 2 + 3);
			float taken = f.hitUser(source, durability / 2 + 3);
			helper.assertTrue(taken > 0 && taken < amount && close(taken, StandUserGuard.userHitAfterGuard(amount, durability)),
					"A frontal zombie hit must lose half the guard's durability: took " + taken + " of " + amount);
			helper.assertTrue(NoKnockbackOnBlocking.hasOneTickKbRes(f.user) && NoKnockbackOnBlocking.hasOneTickKbRes(f.stand),
					"A blocked hit must leave the user and the Stand unstaggered for a tick");
			helper.assertTrue(f.user.getDeltaMovement().horizontalDistanceSqr() < EPS,
					"The blocked hit knocked the user back: " + f.user.getDeltaMovement());
			helper.assertTrue(close(f.user.lastHurt, amount), "1.16 kept the whole hit as lastHurt: " + f.user.lastHurt);
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void smallHitFromFrontIsBlockedWhole(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			f.holdGuard();
			DamageSource source = helper.getLevel().damageSources().mobAttack(f.zombieAt(0, 3));
			float before = f.user.getHealth();
			boolean landed = f.user.hurt(source, 2);
			helper.assertTrue(f.user.getHealth() == before, "A hit under half the durability must deal nothing");
			// 1.16 cancelled it in LivingHurtEvent, so the hit still landed: hurt cooldown, no stagger
			helper.assertTrue(landed && f.user.invulnerableTime > 10, "The blocked hit must still count as landed");
			helper.assertTrue(NoKnockbackOnBlocking.hasOneTickKbRes(f.user)
					&& f.user.getDeltaMovement().horizontalDistanceSqr() < EPS, "The blocked hit staggered the user");
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void zombieHitFromBehindIsNotBlocked(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			f.holdGuard();
			DamageSource source = helper.getLevel().damageSources().mobAttack(f.zombieAt(0, -3));
			float amount = f.scaled(source, 4);
			float taken = f.hitUser(source, 4);
			helper.assertTrue(close(taken, amount), "A hit from behind the guard must land whole: took " + taken);
			helper.assertTrue(!NoKnockbackOnBlocking.hasOneTickKbRes(f.user), "A hit from behind was treated as blocked");
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void noGuardNoBlock(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			DamageSource source = helper.getLevel().damageSources().mobAttack(f.zombieAt(0, 3));
			float amount = f.scaled(source, 4);
			float taken = f.hitUser(source, 4);
			helper.assertTrue(close(taken, amount), "An idle Stand must not guard its user: took " + taken);
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void arrowFromFrontIsBlockedFromBehindIsNot(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			f.holdGuard();
			float durability = (float) f.stand.getDurability();
			float front = f.hitUser(helper.getLevel().damageSources().arrow(f.arrowAt(0, 2), null), durability / 2 + 2);
			helper.assertTrue(close(front, 2), "A frontal arrow must lose half the guard's durability: took " + front);
			f.resetUser();
			float behind = f.hitUser(helper.getLevel().damageSources().arrow(f.arrowAt(0, -2), null), 3);
			helper.assertTrue(close(behind, 3), "An arrow from behind must land whole: took " + behind);
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void explosionInFrontIsCutByAngle(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			Vec3 center = f.user.position().add(0, 1, 2);
			float unguarded = f.explode(center);
			helper.assertTrue(unguarded > 0, "The control explosion dealt nothing");
			f.resetUser();
			f.holdGuard();
			float guarded = f.explode(center);
			double cos = center.subtract(f.user.position()).normalize().dot(f.stand.getLookAngle());
			float expected = unguarded * StandUserGuard.explosionMultiplier(cos, f.stand.getDurability());
			helper.assertTrue(expected < unguarded && close(guarded, expected),
					"A frontal explosion must be cut by angle: " + guarded + " instead of " + expected + " of " + unguarded);
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void explosionBehindIsNotCut(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			Vec3 center = f.user.position().add(0, 1, -2);
			float unguarded = f.explode(center);
			f.resetUser();
			f.holdGuard();
			float guarded = f.explode(center);
			helper.assertTrue(unguarded > 0 && close(guarded, unguarded),
					"An explosion behind the guard must land whole: " + guarded + " of " + unguarded);
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standHitOnUserGoesToTheGuardOnce(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			f.holdGuard();
			float amount = 6;
			DamageSource source = DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_ATTACK, f.zombieAt(0, 3));
			float before = f.user.getHealth();
			boolean landedOnUser = f.user.hurt(source, amount);
			float taken = before - f.user.getHealth();
			helper.assertTrue(!landedOnUser && f.stand.isCurrentAttackBlocked(),
					"A Stand's hit aimed at the user must go to the guarding Stand");
			// the Stand's own block, not also half its durability on the user
			float expected = f.standBlocked(amount, 1);
			helper.assertTrue(taken > 0 && close(taken, expected),
					"The user must take the Stand's blocked hit once: took " + taken + " instead of " + expected);

			f.resetUser();
			float linked = f.hitUser(new StandLinkDamageSource(helper.getLevel(), f.stand,
					helper.getLevel().damageSources().mobAttack(f.zombieAt(0, 3))), 3);
			helper.assertTrue(close(linked, 3), "The health link must not be blocked a second time: took " + linked);
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void busyStandBlocksHalfOfAnUnguardedHit(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			float amount = 4;
			float idle = f.hitStandFrom(0, -2, amount);
			helper.assertTrue(close(idle, f.standBlocked(amount, 0)), "An idle Stand hit from behind: " + idle);

			f.holdGuard();
			float guardBehind = f.hitStandFrom(0, -2, amount);
			helper.assertTrue(close(guardBehind, f.standBlocked(amount, 0.5F)),
					"A guard hit from behind blocks half (1.16 task multiplier): " + guardBehind
					+ " instead of " + f.standBlocked(amount, 0.5F));

			f.setStandAction("punch", 200, 10);
			helper.assertTrue(f.stand.getCurStandAction() != null && !f.stand.isStandBlocking(),
					"The Stand is not busy winding up a punch");
			f.face();
			float punching = f.hitStandFrom(0, 2, amount);
			helper.assertTrue(close(punching, f.standBlocked(amount, 0.5F)),
					"A punching Stand blocks half of a hit (1.16 task multiplier): " + punching);
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	// 1.16 StandEntity.getDurability: a vampire user high on blood doubles the Stand's durability, and so the guard
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void vampireHighOnBloodDoublesTheGuard(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			float base = (float) f.stand.getDurability();
			PlayerPower playerPower = PowerClass.PLAYER_POWER.attachGet(f.user);
			playerPower.setPowerType(ModPlayerPowers.VAMPIRISM.get());
			var blood = VampirismState.get(f.user).blood();
			blood.setCurrent(0.0F);
			helper.assertTrue(close((float) f.stand.getDurability(), base), "A vampire low on blood changed the durability");
			blood.setCurrent(blood.max());
			helper.assertTrue(close((float) f.stand.getDurability(), base * 2),
					"A vampire high on blood must double the durability: " + f.stand.getDurability() + " of " + base);

			f.holdGuard();
			DamageSource source = helper.getLevel().damageSources().mobAttack(f.zombieAt(0, 3));
			float amount = f.scaled(source, base + 3);
			float taken = f.hitUser(source, base + 3);
			helper.assertTrue(close(taken, StandUserGuard.userHitAfterGuard(amount, base * 2)),
					"The doubled durability must guard the user: took " + taken + " of " + amount);
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	// 1.16 blockDamage cancelled a hit the guard blocked whole, so no damage handler saw it; the port marks it for them
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void blockedWholeHitIsMarkedForDamageHandlers(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		List<Boolean> marks = new ArrayList<>();
		Consumer<LivingDamageEvent.Pre> probe = event -> {
			if (event.getEntity() == f.user) {
				marks.add(StandUserGuard.blockedWhole(event.getContainer()));
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDamageEvent.Pre.class, probe);
		try {
			f.holdGuard();
			float durability = (float) f.stand.getDurability();
			helper.assertTrue(durability >= 4, "The guard is too weak for the test: " + durability);
			DamageSource source = helper.getLevel().damageSources().mobAttack(f.zombieAt(0, 3));
			f.hitUser(source, 2);
			helper.assertTrue(f.user.invulnerableTime > 10, "The blocked hit gave no hurt cooldown");
			// in the hurt cooldown only the excess over lastHurt is cut, here to nothing
			f.hitUser(source, 3);
			f.resetUser();
			f.hitUser(source, durability / 2 + 3);
		}
		finally {
			NeoForge.EVENT_BUS.unregister(probe);
			f.close();
		}
		helper.assertTrue(marks.equals(List.of(true, true, false)),
				"Blocked whole, blocked whole in the cooldown, cut: marked " + marks);
		helper.succeed();
	}

	// NeoForge left a cancelled hit's damage container on the stack, so a later knockback took its modifiers
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void redirectedHitLeavesNoStaleKnockback(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			f.holdGuard();
			DamageSource source = DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_ATTACK, f.zombieAt(0, 3));
			// The World's heavy punch
			((DamageSourceModified) source).jojo_ripples$modifyKnockback(6, 1);
			helper.assertTrue(!f.user.hurt(source, 6) && f.stand.isCurrentAttackBlocked(),
					"A Stand's hit aimed at the user must go to the guarding Stand");
			helper.assertTrue(f.user.damageContainers.isEmpty(),
					"The redirected hit left " + f.user.damageContainers.size() + " damage containers on the user");
			f.resetUser();
			// past the guard's tick without stagger
			f.user.getAttribute(Attributes.KNOCKBACK_RESISTANCE).removeModifier(NoKnockbackOnBlocking.ONE_TICK_KB_RES_ID);
			// a sprint hit's knockback, which Player.attack gives after the hit
			f.user.knockback(0.5, 0, -1);
			Vec3 motion = f.user.getDeltaMovement();
			helper.assertTrue(Math.abs(motion.x) < EPS && Math.abs(motion.z - 0.5) < EPS,
					"A later knockback took the guarded heavy punch's +6: " + motion);
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	// A hit the guard passes to the Stand reaches the user's knockback through the Stand's, changed by the hit once
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void partlyBlockedRedirectKnocksTheUserBackOnce(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			Vec3 redirected = f.guardCrashKnockback(true);
			Vec3 onStand = f.guardCrashKnockback(false);
			helper.assertTrue(onStand.lengthSqr() > EPS, "The Stand's hit did not move its user: " + onStand);
			helper.assertTrue(redirected.distanceTo(onStand) < EPS,
					"The hit on the user knocked it back as " + redirected + ", the same hit on the Stand as " + onStand);
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	private static boolean close(float a, float b) {
		return Math.abs(a - b) < EPS;
	}

	private static final class Fixture {
		final GameTestHelper helper;
		final Player user;
		final StandType type;
		final StandPower power;
		final StandEntity stand;
		final Vec3 pos;

		Fixture(GameTestHelper helper) {
			this.helper = helper;
			// the factory FakePlayer is invulnerable, and so would its Stand be
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			user.setYHeadRot(0);
			user.getAbilities().invulnerable = false;
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "The summoned Stand is missing");
			power.setStamina(power.getMaxStamina());
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			face();
			helper.assertTrue(stand.getCurStandAction() == null && !stand.isManuallyControlled()
					&& stand.isFollowingUser() && stand.getArmorValue() == 0, "The Stand is not idle by its user");
			resetUser();
		}

		// half a block in front of the user, facing south as the user does
		void face() {
			stand.moveTo(pos.x, pos.y, pos.z + 0.5, 0, 0);
			stand.setYHeadRot(0);
			stand.yRotO = 0;
			stand.yHeadRotO = 0;
		}

		// the Stand's guard, held as a user holds it
		void holdGuard() {
			setStandAction("guard", 0, 200);
			helper.assertTrue(stand.isStandBlocking() && stand.isFollowingUser(), "The held guard is not blocking");
			face();
		}

		void setStandAction(String name, float windupTicks, float performTicks) {
			Ability ability = power.getAbility(name);
			helper.assertTrue(ability instanceof EntityActionType, "Star Platinum's " + name + " is missing");
			EntityActionType actionType = (EntityActionType) ability;
			EntityActionInstance action = actionType.createActionObj();
			actionType.initActionFromConfig(action, helper.getLevel(), user, stand);
			action.phasesLength.put(ActionPhase.BUTTON_CHARGE, 0F);
			action.phasesLength.put(ActionPhase.WINDUP, windupTicks);
			action.phasesLength.put(ActionPhase.PERFORM, performTicks);
			action.setStartingPhase();
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
		}

		Zombie zombieAt(double dx, double dz) {
			Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
			helper.assertTrue(zombie != null, "Could not create the attacker");
			zombie.moveTo(pos.x + dx, pos.y, pos.z + dz, 0, 0);
			return zombie;
		}

		Arrow arrowAt(double dx, double dz) {
			Arrow arrow = EntityType.ARROW.create(helper.getLevel());
			helper.assertTrue(arrow != null, "Could not create the arrow");
			arrow.moveTo(pos.x + dx, pos.y + 1, pos.z + dz, 0, 0);
			return arrow;
		}

		// what Player.hurt passes on after difficulty scaling
		float scaled(DamageSource source, float amount) {
			return source.type().scaling().getScalingFunction().scaleDamage(
					source, user, amount, helper.getLevel().getDifficulty());
		}

		float hitUser(DamageSource source, float amount) {
			float before = user.getHealth();
			user.hurt(source, amount);
			return before - user.getHealth();
		}

		float hitStandFrom(double dx, double dz, float amount) {
			resetUser();
			stand.invulnerableTime = 0;
			float before = user.getHealth();
			stand.hurt(DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_ATTACK,
					zombieAt(stand.getX() - pos.x + dx, stand.getZ() - pos.z + dz)), amount);
			return before - user.getHealth();
		}

		float explode(Vec3 center) {
			float before = user.getHealth();
			helper.getLevel().explode(null, center.x, center.y, center.z, 2.0F, Level.ExplosionInteraction.NONE);
			float taken = before - user.getHealth();
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			user.setDeltaMovement(Vec3.ZERO);
			face();
			return taken;
		}

		// StandEntity.standDamageResistance for an unarmored user
		float standBlocked(float amount, float blockedRatio) {
			return amount * (1 - StandStatFormulas.getPhysicalResistance(
					stand.getDurability(), stand.getAttackDamage(), blockedRatio, amount));
		}

		// an angled Stand hit (an uppercut) on a guard with no stamina: the guard crashes and passes the knockback on
		Vec3 guardCrashKnockback(boolean aimedAtUser) {
			stand.removeAllEffects();
			stand.invulnerableTime = 0;
			stand.setDeltaMovement(Vec3.ZERO);
			holdGuard();
			resetUser();
			power.setStamina(0);
			DamageSource source = DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_ATTACK, zombieAt(0, 3));
			((DamageSourceModified) source).jojo_ripples$modifyKnockback(1, 1);
			((DamageSourceModified) source).jojo_ripples$knockbackXRot(-30);
			if (aimedAtUser) {
				helper.assertTrue(!user.hurt(source, 4), "A Stand's hit aimed at the user must go to the guarding Stand");
			}
			else {
				stand.hurt(source, 4);
			}
			helper.assertTrue(!stand.isStandBlocking(), "The guard with no stamina did not crash");
			helper.assertTrue(user.damageContainers.isEmpty(), "A damage container was left on the user");
			return user.getDeltaMovement();
		}

		void resetUser() {
			user.setHealth(user.getMaxHealth());
			user.invulnerableTime = 0;
			user.lastHurt = 0;
			user.setDeltaMovement(Vec3.ZERO);
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			user.setYHeadRot(0);
		}

		void close() {
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
