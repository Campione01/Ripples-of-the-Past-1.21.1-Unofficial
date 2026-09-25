package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.HeavyPunchExplosion;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityDamageSource scaled a Stand's hit with difficulty when its user was a non-player mob, and
 * StandEntityPunch.doHit wore a blocking player's shield by half a blocked hit under 3 (vanilla wears none there),
 * for the punch's main target only: its sweep targets (doAttack) and the heavy punch explosion (hurtTarget) wore none.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandHitScalingAndShieldWearGameTests {
	private StandHitScalingAndShieldWearGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void mobStandHitScalesWithDifficulty(GameTestHelper helper) {
		StandType type = starPlatinum(helper);
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "Could not create the zombie");
		zombie.setNoAi(true);
		zombie.moveTo(pos.x, pos.y, pos.z, 0, 0);
		zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400));
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Could not add the zombie");
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		player.moveTo(pos.x, pos.y, pos.z + 3, 180, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the player");
		StandPower mobPower = PowerClass.STAND.attachGet(zombie);
		StandPower playerPower = PowerClass.STAND.attachGet(player);
		try {
			StandEntity mobStand = summon(helper, type, zombie, mobPower);
			StandEntity playerStand = summon(helper, type, player, playerPower);

			// the punch source, as makePunchDamageSource builds it
			helper.assertTrue(punch(helper, mobStand, mobPower).scalesWithDifficulty(),
					"1.16: a mob user's Stand punch scales with difficulty");
			helper.assertFalse(punch(helper, playerStand, playerPower).scalesWithDifficulty(),
					"1.16: a player's Stand punch does not scale with difficulty");
			// a Stand hit built without the power still reads the Stand's user
			helper.assertTrue(DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_ATTACK, mobStand).scalesWithDifficulty(),
					"1.16: a mob user's Stand hit scales with difficulty");
			helper.assertFalse(DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_ATTACK, playerStand).scalesWithDifficulty(),
					"1.16: a player's Stand hit does not scale with difficulty");
			// 1.16 checked the direct attacker, so a Stand projectile never scaled
			Snowball projectile = EntityType.SNOWBALL.create(helper.getLevel());
			helper.assertTrue(projectile != null, "Could not create the projectile");
			DamageSource shot = DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_PROJECTILE, projectile, zombie);
			((DamageSourceModified) shot).jojo_ripples$setStandPower(mobPower);
			helper.assertFalse(shot.scalesWithDifficulty(), "1.16: a mob user's Stand projectile does not scale with difficulty");
		}
		finally {
			unsummon(type, zombie, mobPower);
			unsummon(type, player, playerPower);
			zombie.discard();
			player.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void blockedWeakStandHitWearsShield(GameTestHelper helper) {
		StandType type = starPlatinum(helper);
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		Player attacker = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		attacker.moveTo(pos.x, pos.y, pos.z + 3, 180, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(attacker), "Could not add the attacker");
		ShieldBlocker blocker = new ShieldBlocker(helper.getLevel());
		StandPower power = PowerClass.STAND.attachGet(attacker);
		try {
			StandEntity stand = summon(helper, type, attacker, power);
			blocker.moveTo(pos.x, pos.y, pos.z, 0, 0);
			blocker.setYHeadRot(0);
			blocker.getAbilities().invulnerable = false;
			blocker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
			helper.assertTrue(helper.getLevel().addFreshEntity(blocker), "Could not add the blocker");
			blocker.raiseShield(helper);
			ItemStack shield = blocker.getOffhandItem();
			float health = blocker.getHealth();
			// under 3: 1.16 wore half the hit (2 * 0.5 = exactly 1)
			int worn = hit(helper, blocker, stand, pos, shield, 2.0F);
			helper.assertTrue(worn == 1, "1.16: a blocked Stand hit for 2 wears the shield by 1, wore " + worn);
			helper.assertTrue(blocker.getHealth() == health, "The shield did not take the hit");
			// 3 or more is vanilla's alone: 1 + floor(4)
			worn = hit(helper, blocker, stand, pos, shield, 4.0F);
			helper.assertTrue(worn == 5, "A blocked Stand hit for 4 wears the shield by vanilla's 5 only, wore " + worn);
		}
		finally {
			unsummon(type, attacker, power);
			attacker.discard();
			blocker.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void blockedSweepAndExplosionHitsWearNoShield(GameTestHelper helper) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
		helper.assertTrue(type != null, "Missing registered The World");
		Level level = helper.getLevel();
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		Player attacker = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		attacker.moveTo(pos.x, pos.y, pos.z + 4, 180, 0);
		helper.assertTrue(level.addFreshEntity(attacker), "Could not add the attacker");
		ShieldBlocker blocker = new ShieldBlocker(level);
		Zombie zombie = EntityType.ZOMBIE.create(level);
		StandPower power = PowerClass.STAND.attachGet(attacker);
		try {
			helper.assertTrue(zombie != null, "Could not create the zombie");
			StandEntity stand = summon(helper, type, attacker, power);
			// the kick's main target, with the blocker inside its 0.5 sweep box
			zombie.setNoAi(true);
			zombie.moveTo(pos.x, pos.y, pos.z, 0, 0);
			zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400));
			helper.assertTrue(level.addFreshEntity(zombie), "Could not add the zombie");
			blocker.moveTo(pos.x + 1.0D, pos.y, pos.z, 0, 0);
			blocker.setYHeadRot(0);
			blocker.getAbilities().invulnerable = false;
			blocker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
			helper.assertTrue(level.addFreshEntity(blocker), "Could not add the blocker");
			blocker.raiseShield(helper);
			ItemStack shield = blocker.getOffhandItem();
			float health = blocker.getHealth();

			// heavy punch explosion for 2 in front of the blocker
			placeSweepScene(blocker, zombie, stand, pos);
			int before = shield.getDamageValue();
			Vec3 center = stand.position().add(0, 1, 0);
			HeavyPunchExplosion explosion = new HeavyPunchExplosion(level, stand,
					new ActionTarget(BlockPos.containing(center), Direction.SOUTH), stand.getLookAngle(),
					DamageUtil.make(level, ModDamageTypes.STAND_ATTACK, stand),
					center.x, center.y, center.z, 2.0F, false, Explosion.BlockInteraction.KEEP)
					.aoeDamage(2.0F);
			explosion.entityNoDamage(stand).entityNoDamage(attacker).entityNoDamage(zombie);
			explosion.explode();
			helper.assertTrue(blocker.getHealth() == health, "The shield did not take the explosion");
			int worn = shield.getDamageValue() - before;
			helper.assertTrue(worn == 0, "1.16: a blocked heavy punch explosion for 2 wears no shield, wore " + worn);

			// The World's kick: its main target is hurt, the sweep for 4 * 0.5 = 2 is blocked
			placeSweepScene(blocker, zombie, stand, pos);
			before = shield.getDamageValue();
			float zombieHealth = zombie.getHealth();
			kickHit(helper, zombie, stand, power, 4.0F);
			helper.assertTrue(zombie.getHealth() < zombieHealth, "The kick did not hurt its main target");
			helper.assertTrue(blocker.getHealth() == health, "The shield did not take the kick's sweep");
			worn = shield.getDamageValue() - before;
			helper.assertTrue(worn == 0, "1.16: a blocked kick sweep for 2 wears no shield, wore " + worn);
		}
		finally {
			unsummon(type, attacker, power);
			attacker.discard();
			blocker.discard();
			if (zombie != null) zombie.discard();
		}
		helper.succeed();
	}

	/**
	 * GameTestPlayers' survival mock that raises its shield within the test's own tick. Waiting server ticks for it
	 * was flaky: a neighbouring test's time stop froze the blocker, so its item use never reached the 5 blocking ticks.
	 */
	private static final class ShieldBlocker extends Player {
		ShieldBlocker(Level level) {
			super(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "test-mock-player"));
		}

		@Override
		public boolean isSpectator() {
			return false;
		}

		@Override
		public boolean isCreative() {
			return false;
		}

		// 8 item use steps as LivingEntity.tick runs them; vanilla isBlocking needs 5
		void raiseShield(GameTestHelper helper) {
			startUsingItem(InteractionHand.OFF_HAND);
			for (int i = 0; i < 8 && isUsingItem(); i++) {
				updateUsingItem(getUseItem());
			}
			helper.assertTrue(isBlocking(), "The player's shield is not raised");
		}
	}

	// the Stand south of the zombie, facing it; the blocker east of the zombie facing south
	private static void placeSweepScene(Player blocker, Zombie zombie, StandEntity stand, Vec3 pos) {
		blocker.moveTo(pos.x + 1.0D, pos.y, pos.z, 0, 0);
		blocker.setYHeadRot(0);
		blocker.setDeltaMovement(Vec3.ZERO);
		blocker.invulnerableTime = 0;
		zombie.moveTo(pos.x, pos.y, pos.z, 0, 0);
		zombie.setDeltaMovement(Vec3.ZERO);
		zombie.invulnerableTime = 0;
		stand.moveTo(pos.x, pos.y, pos.z + 1.5D, 180, 0);
	}

	// runs The World kick's hitEntity on the target
	private static void kickHit(GameTestHelper helper, LivingEntity target, StandEntity stand, StandPower power, float damage) {
		Ability ability = power.getMoveset().getAbility("kick");
		helper.assertTrue(ability instanceof EntityActionType, "Missing entity action kick");
		Level level = helper.getLevel();
		EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(level, power.getUser(), stand, null);
		helper.assertTrue(action instanceof StandEntityHeavyPunch, "kick is not a heavy punch");
		try {
			Field performer = EntityActionInstance.class.getDeclaredField("performer");
			performer.setAccessible(true);
			performer.set(action, stand);
			Method hitEntity = StandEntityHeavyPunch.class.getDeclaredMethod("hitEntity", ActionTarget.class, Level.class,
					StandEntity.class, DamageSource.class, float.class, float.class);
			hitEntity.setAccessible(true);
			hitEntity.invoke(action, new ActionTarget(target), level, stand,
					DamageUtil.make(level, ModDamageTypes.STAND_ATTACK, stand), damage, 0.0F);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not run the kick's hitEntity", e);
		}
	}

	// the Stand hits from in front of the blocker, facing south; the shield wear it caused
	private static int hit(GameTestHelper helper, Player blocker, StandEntity stand, Vec3 pos, ItemStack shield, float amount) {
		blocker.moveTo(pos.x, pos.y, pos.z, 0, 0);
		blocker.setYHeadRot(0);
		blocker.invulnerableTime = 0;
		stand.moveTo(pos.x, pos.y, pos.z + 1.5D, 180, 0);
		int before = shield.getDamageValue();
		boolean hurt = EntityActionInstance.standEntityAttack(stand, blocker,
				DamageUtil.make(blocker.level(), ModDamageTypes.STAND_ATTACK, stand), amount);
		helper.assertFalse(hurt, "The shield did not block the Stand hit for " + amount);
		return shield.getDamageValue() - before;
	}

	private static DamageSource punch(GameTestHelper helper, StandEntity stand, StandPower power) {
		DamageSource source = new DamageSource(DamageUtil.type(helper.getLevel(), ModDamageTypes.STAND_ATTACK), stand);
		((DamageSourceModified) source).jojo_ripples$setStandPower(power);
		return source;
	}

	private static StandType starPlatinum(GameTestHelper helper) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		helper.assertTrue(type != null, "Missing registered Star Platinum");
		return type;
	}

	private static StandEntity summon(GameTestHelper helper, StandType type, LivingEntity user, StandPower power) {
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED && type.summon(user, power),
				"Could not give " + user.getName().getString() + " a summoned Star Platinum");
		StandEntity stand = power.getSummonedStandEntity();
		helper.assertTrue(stand != null && stand.getUser() == user, "The summoned Stand is missing its user");
		return stand;
	}

	private static void unsummon(StandType type, LivingEntity user, StandPower power) {
		if (power.isSummoned()) {
			type.forceUnsummon(user, power);
		}
	}
}
