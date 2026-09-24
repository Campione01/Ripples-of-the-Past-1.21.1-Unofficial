package rotp.core.gametest;

import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoKnockbackOnBlocking;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandUserGuard;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 NoKnockbackOnBlocking.cancelHurtSound: a user whose hit the guarding Stand blocked, in whole or in part, makes
 * no hurt sound that tick (LivingEntityMixin.playHurtSound; the user's own client is told by KnockbackResTickPacket).
 * 1.16 resolveOnHurtEvent (HIGHEST) counted the hit for the attacker's Resolve before blockDamage (HIGH) cut it.
 * The user stands facing south (+Z), its guarding Stand half a block in front.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandGuardHurtSoundGameTests {
	private static final float EPS = 1.0E-3F;

	private StandGuardHurtSoundGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void blockedHitMakesNoHurtSound(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		HurtSounds sounds = new HurtSounds(helper, f.user);
		try {
			f.holdGuard();
			float durability = (float) f.stand.getDurability();
			float before = f.user.getHealth();
			f.user.hurt(helper.getLevel().damageSources().mobAttack(f.zombieAt(0, 3)), durability / 2 + 3);
			helper.assertTrue(f.user.getHealth() < before, "The partly blocked hit must still land");
			helper.assertTrue(NoKnockbackOnBlocking.cancelHurtSound(f.user) && !NoKnockbackOnBlocking.cancelHurtSound(f.stand),
					"The blocked hit must silence the user for a tick, and never the Stand");
			helper.assertTrue(sounds.count == 0, "1.16 cancelHurtSound: the blocked user cried out " + sounds.count + " time(s)");
		}
		catch (RuntimeException | AssertionError error) {
			sounds.close();
			f.close();
			throw error;
		}
		// the user's next tick ends it; a hit from behind then cries out as ever
		helper.runAfterDelay(2, () -> {
			try {
				helper.assertTrue(!NoKnockbackOnBlocking.hasOneTickKbRes(f.user), "The one-tick resistance outlived the tick");
				f.resetUser();
				f.face();
				f.user.hurt(helper.getLevel().damageSources().mobAttack(f.zombieAt(0, -3)), 2);
				helper.assertTrue(sounds.count == 1, "An unblocked hit must make one hurt sound: " + sounds.count);
			}
			finally {
				sounds.close();
				f.close();
			}
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void resolveCountsTheHitBeforeTheGuardCut(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		Player attacker = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		StandPower attackerPower = null;
		try {
			attacker.moveTo(f.pos.x, f.pos.y, f.pos.z + 3, 180, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(attacker), "Could not add the attacker");
			attackerPower = PowerClass.STAND.attachGet(attacker);
			helper.assertTrue(StandPowerTransitions.insert(attackerPower, new StandInstance(f.type)).status()
					== StandPowerTransitions.Status.APPLIED && f.type.summon(attacker, attackerPower),
					"Could not give the attacker a summoned Star Platinum");
			helper.assertTrue(attackerPower.usesResolve(), "The attacker's Stand has no Resolve");

			f.holdGuard();
			float durability = (float) f.stand.getDurability();
			float amount = durability / 2 + 2;
			float resolveBefore = attackerPower.resolveCounter.getResolveValue();
			float before = f.user.getHealth();
			f.user.hurt(helper.getLevel().damageSources().playerAttack(attacker), amount);
			float taken = before - f.user.getHealth();
			helper.assertTrue(Math.abs(taken - StandUserGuard.userHitAfterGuard(amount, durability)) < EPS,
					"The guard did not cut the fist: took " + taken + " of " + amount);
			// a Stand user's own hit while the Stand is out: half the hit, 1 against another ordinary Stand
			float gained = attackerPower.resolveCounter.getResolveValue() - resolveBefore;
			helper.assertTrue(Math.abs(gained - amount * 0.5F) < EPS,
					"Resolve must count the hit before the guard cut it: gained " + gained + " instead of " + amount * 0.5F);
		}
		finally {
			if (attackerPower != null && attackerPower.isSummoned()) {
				f.type.forceUnsummon(attacker, attackerPower);
			}
			attacker.discard();
			f.close();
		}
		helper.succeed();
	}

	// the hurt sounds a player makes at its own position (Player.playSound goes through Level.playSound)
	private static final class HurtSounds {
		final Player player;
		int count;
		final Consumer<PlayLevelSoundEvent.AtPosition> listener;

		HurtSounds(GameTestHelper helper, Player player) {
			this.player = player;
			listener = event -> {
				Holder<SoundEvent> sound = event.getSound();
				if (event.getLevel() == helper.getLevel() && sound != null && sound.value() == SoundEvents.PLAYER_HURT
						&& event.getPosition().distanceToSqr(player.position()) < 0.01) {
					count++;
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayLevelSoundEvent.AtPosition.class, listener);
		}

		void close() {
			NeoForge.EVENT_BUS.unregister(listener);
		}
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
			Ability ability = power.getAbility("guard");
			helper.assertTrue(ability instanceof EntityActionType, "Star Platinum's guard is missing");
			EntityActionType actionType = (EntityActionType) ability;
			EntityActionInstance action = actionType.createActionObj();
			actionType.initActionFromConfig(action, helper.getLevel(), user, stand);
			action.phasesLength.put(ActionPhase.BUTTON_CHARGE, 0F);
			action.phasesLength.put(ActionPhase.WINDUP, 0F);
			action.phasesLength.put(ActionPhase.PERFORM, 200F);
			action.setStartingPhase();
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(stand.isStandBlocking() && stand.isFollowingUser(), "The held guard is not blocking");
			face();
		}

		Zombie zombieAt(double dx, double dz) {
			Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
			helper.assertTrue(zombie != null, "Could not create the attacker");
			zombie.moveTo(pos.x + dx, pos.y, pos.z + dz, 0, 0);
			return zombie;
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
