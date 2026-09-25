package rotp.core.gametest;

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
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandUserGuard;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 resolveOnHurtEvent counted Resolve in LivingHurtEvent, which vanilla hurt() reaches only past a shield (amount
 * 0) and, in the hurt cooldown, only for a hit bigger than lastHurt (the excess). The attacker is a player with a
 * summoned Star Platinum (half the hit counts); it stands 3 blocks south of the target, facing it.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResolveHurtTimingGameTests {
	private static final float EPS = 1.0E-3F;

	private ResolveHurtTimingGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void shieldBlockedHitGivesNoResolve(GameTestHelper helper) {
		Attacker a = new Attacker(helper);
		Zombie zombie;
		try {
			zombie = a.zombie();
			zombie.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
			zombie.startUsingItem(InteractionHand.OFF_HAND);
		}
		catch (RuntimeException | AssertionError error) {
			a.close();
			throw error;
		}
		// a raised shield blocks after 5 ticks of use
		helper.runAfterDelay(8, () -> {
			try {
				a.faceZombie(zombie);
				helper.assertTrue(zombie.isBlocking(), "The zombie's shield is not raised");
				float before = zombie.getHealth();
				float gained = a.hit(zombie, 6);
				helper.assertTrue(zombie.getHealth() == before, "The shield did not take the hit");
				helper.assertTrue(Math.abs(gained) < EPS, "1.16: a hit the shield took whole gives no Resolve, gained " + gained);

				// the same hit past the lowered shield counts
				zombie.stopUsingItem();
				a.faceZombie(zombie);
				gained = a.hit(zombie, 6);
				helper.assertTrue(zombie.getHealth() < before, "The unblocked hit did not land");
				helper.assertTrue(Math.abs(gained - 3) < EPS, "An unblocked hit for 6 must give 3 Resolve, gained " + gained);
			}
			finally {
				zombie.discard();
				a.close();
			}
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void hurtCooldownCountsOnlyTheExcess(GameTestHelper helper) {
		Attacker a = new Attacker(helper);
		try {
			Zombie zombie = a.zombie();
			a.faceZombie(zombie);
			zombie.invulnerableTime = 20;
			zombie.lastHurt = 10;
			float before = zombie.getHealth();
			float gained = a.hit(zombie, 8);
			helper.assertTrue(zombie.getHealth() == before, "A cooldown hit no bigger than lastHurt must not land");
			helper.assertTrue(Math.abs(gained) < EPS, "1.16: a cooldown hit no bigger than lastHurt gives no Resolve, gained " + gained);

			gained = a.hit(zombie, 14);
			helper.assertTrue(zombie.getHealth() < before, "The bigger cooldown hit did not land");
			helper.assertTrue(Math.abs(gained - 2) < EPS,
					"1.16: a cooldown hit for 14 over lastHurt 10 counts its excess 4 (2 Resolve), gained " + gained);
			zombie.discard();
		}
		finally {
			a.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void guardCutInsideTheCooldownStillCountsTheExcess(GameTestHelper helper) {
		Attacker a = new Attacker(helper);
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		StandPower userPower = null;
		try {
			// the guarded user stands where the zombie would, facing the attacker, its Stand half a block in front
			user.moveTo(a.targetPos.x, a.targetPos.y, a.targetPos.z, 0, 0);
			user.setYHeadRot(0);
			user.getAbilities().invulnerable = false;
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			userPower = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(userPower, new StandInstance(a.type)).status()
					== StandPowerTransitions.Status.APPLIED && a.type.summon(user, userPower),
					"Could not give the user a summoned Star Platinum");
			StandEntity stand = userPower.getSummonedStandEntity();
			helper.assertTrue(stand != null, "The user's Stand is missing");
			userPower.setStamina(userPower.getMaxStamina());
			holdGuard(helper, userPower, user, stand, a.targetPos);
			float durability = (float) stand.getDurability();
			helper.assertTrue(durability > 0, "The guard has no durability");

			user.invulnerableTime = 20;
			user.lastHurt = 2;
			float amount = durability / 2 + 6;
			float excess = amount - 2;
			float before = user.getHealth();
			float gained = a.hit(user, amount);
			float taken = before - user.getHealth();
			helper.assertTrue(Math.abs(taken - StandUserGuard.userHitAfterGuard(excess, durability)) < EPS,
					"The guard did not cut the excess: took " + taken + " of " + excess);
			// 1.16 counted the cooldown excess before blockDamage cut it
			helper.assertTrue(Math.abs(gained - excess * 0.5F) < EPS,
					"Resolve must count the cooldown excess before the guard cut: gained " + gained + " instead of " + excess * 0.5F);
		}
		finally {
			if (userPower != null && userPower.isSummoned()) {
				a.type.forceUnsummon(user, userPower);
			}
			user.discard();
			a.close();
		}
		helper.succeed();
	}

	// the Stand's guard, held as a user holds it, the Stand facing south in front of the user
	private static void holdGuard(GameTestHelper helper, StandPower power, Player user, StandEntity stand, Vec3 pos) {
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
		stand.moveTo(pos.x, pos.y, pos.z + 0.5, 0, 0);
		stand.setYHeadRot(0);
		stand.yRotO = 0;
		stand.yHeadRotO = 0;
	}

	private static final class Attacker {
		final GameTestHelper helper;
		final Player player;
		final StandType type;
		final StandPower power;
		final Vec3 targetPos;

		Attacker(GameTestHelper helper) {
			this.helper = helper;
			targetPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			player.moveTo(targetPos.x, targetPos.y, targetPos.z + 3, 180, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the attacker");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			power = PowerClass.STAND.attachGet(player);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(player, power),
					"Could not give the attacker a summoned Star Platinum");
			helper.assertTrue(power.usesResolve(), "The attacker's Stand has no Resolve");
		}

		// a zombie without AI where the target stands, kept from burning
		Zombie zombie() {
			Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
			helper.assertTrue(zombie != null, "Could not create the zombie");
			zombie.setNoAi(true);
			zombie.moveTo(targetPos.x, targetPos.y, targetPos.z, 0, 0);
			zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400));
			helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Could not add the zombie");
			return zombie;
		}

		// the zombie faces south, toward the attacker, out of any hurt cooldown
		void faceZombie(Zombie zombie) {
			zombie.moveTo(targetPos.x, targetPos.y, targetPos.z, 0, 0);
			zombie.setYHeadRot(0);
			zombie.setDeltaMovement(Vec3.ZERO);
			zombie.invulnerableTime = 0;
			zombie.lastHurt = 0;
			player.moveTo(targetPos.x, targetPos.y, targetPos.z + 3, 180, 0);
		}

		// the Resolve the attacker's fist gives
		float hit(LivingEntity target, float amount) {
			float before = power.resolveCounter.getResolveValue();
			target.hurt(helper.getLevel().damageSources().playerAttack(player), amount);
			return power.resolveCounter.getResolveValue() - before;
		}

		void close() {
			if (power.isSummoned()) {
				type.forceUnsummon(player, power);
			}
			player.discard();
		}
	}
}
