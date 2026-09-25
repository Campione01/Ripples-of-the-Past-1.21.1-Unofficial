package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.impl.stands.hierophant.HGEmeraldEntity;
import rotp.core.impl.stands.magiciansred.MRCrossfireHurricaneEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ModStatusEffects.INTEGRATED_STAND + GameplayEventHandler: an attacker holding integrated_stand hurts Stands
 * with its own melee and projectile attacks (Stone Free and Ticket to Ride users get it every tick).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IntegratedStandDamageGameTests {

	private IntegratedStandDamageGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void integratedStandLetsOwnAttacksHurtStands(GameTestHelper helper) {
		helper.assertTrue(JojoMod.resLoc("integrated_stand").equals(
				BuiltInRegistries.MOB_EFFECT.getKey(ModStatusEffects.INTEGRATED_STAND.get())),
				"The effect must be registered as jojo_ripples:integrated_stand, as 1.16 /effect named it");
		Fixture f = new Fixture(helper);
		try {
			Zombie attacker = EntityType.ZOMBIE.create(helper.getLevel());
			helper.assertTrue(attacker != null, "Could not create the attacker");
			attacker.moveTo(f.stand.getX(), f.stand.getY(), f.stand.getZ() - 2, 0, 0);
			Arrow arrow = EntityType.ARROW.create(helper.getLevel());
			helper.assertTrue(arrow != null, "Could not create the arrow");
			arrow.moveTo(f.stand.getX(), f.stand.getY() + 1, f.stand.getZ() - 1, 0, 0);
			arrow.setOwner(attacker);
			DamageSource melee = helper.getLevel().damageSources().mobAttack(attacker);
			DamageSource shot = helper.getLevel().damageSources().arrow(arrow, attacker);

			helper.assertTrue(f.stand.isInvulnerableTo(melee) && !DamageUtil.canHurtStands(melee),
					"Without integrated_stand a plain melee hit must not reach a Stand");
			helper.assertTrue(f.hit(melee) == 0, "Without integrated_stand the Stand's user must lose no health");

			attacker.addEffect(new MobEffectInstance(ModStatusEffects.INTEGRATED_STAND, 10, 0, false, false, false));
			helper.assertTrue(DamageUtil.canHurtStands(melee) && !f.stand.isInvulnerableTo(melee),
					"An attacker with integrated_stand must be able to hit a Stand in melee");
			float lost = f.hit(melee);
			helper.assertTrue(lost > 0, "The melee hit of an integrated_stand holder must hurt the Stand's user: " + lost);
			helper.assertTrue(DamageUtil.canHurtStands(shot) && !f.stand.isInvulnerableTo(shot),
					"The arrow of an integrated_stand holder must be able to hit a Stand");

			attacker.removeEffect(ModStatusEffects.INTEGRATED_STAND);
			helper.assertTrue(f.stand.isInvulnerableTo(melee), "The Stand must be immune again once the effect ends");

			// the registry holder, as /effect gives it
			Holder<MobEffect> ref = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ModStatusEffects.INTEGRATED_STAND.get());
			attacker.addEffect(new MobEffectInstance(ref, 10));
			helper.assertTrue(!f.stand.isInvulnerableTo(melee), "integrated_stand given through /effect must work too");
			attacker.discard();
			arrow.discard();
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	// 1.16 DamagingEntity took only IStandDamageSource: integrated_stand melee or arrows must not break Stand projectiles
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void integratedStandCannotBreakStandProjectiles(GameTestHelper helper) {
		Level level = helper.getLevel();
		Zombie attacker = EntityType.ZOMBIE.create(level);
		Zombie shooter = EntityType.ZOMBIE.create(level);
		Arrow arrow = EntityType.ARROW.create(level);
		helper.assertTrue(attacker != null && shooter != null && arrow != null, "Could not create the test entities");
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		shooter.moveTo(pos.x, pos.y, pos.z, 0, 0);
		attacker.moveTo(pos.x, pos.y, pos.z + 3, 180, 0);
		arrow.moveTo(pos.x, pos.y + 1, pos.z + 2, 180, 0);
		arrow.setOwner(attacker);
		attacker.addEffect(new MobEffectInstance(ModStatusEffects.INTEGRATED_STAND, 100, 0, false, false, false));
		DamageSource melee = level.damageSources().mobAttack(attacker);
		DamageSource shot = level.damageSources().arrow(arrow, attacker);
		helper.assertTrue(DamageUtil.canHurtStands(melee) && DamageUtil.canHurtStands(shot),
				"integrated_stand must still let its holder's hits reach Stands");
		helper.assertTrue(!DamageUtil.canHurtStandProjectiles(melee) && !DamageUtil.canHurtStandProjectiles(shot),
				"integrated_stand must not count as Stand damage for Stand projectiles");
		for (int kind = 0; kind < 2; kind++) {
			for (DamageSource source : new DamageSource[] { melee, shot }) {
				ModdedProjectileEntity projectile = standProjectile(kind, shooter, level);
				String what = projectile.getType().getDescriptionId() + " hit by " + source.getMsgId();
				helper.assertTrue(projectile.isInvulnerableTo(source),
						"An integrated_stand holder's hit must not reach a Stand projectile: " + what);
				boolean hurt = projectile.hurt(source, 4);
				helper.assertTrue(!hurt && !projectile.isRemoved(),
						"An integrated_stand holder's hit must not break a Stand projectile: " + what);
				projectile.discard();
			}
			// Stand damage still breaks it
			ModdedProjectileEntity control = standProjectile(kind, shooter, level);
			boolean hurt = control.hurt(DamageUtil.make(level, ModDamageTypes.STAND_ATTACK, attacker), 4);
			helper.assertTrue(hurt && control.isRemoved(),
					"A Stand attack must still break " + control.getType().getDescriptionId());
		}
		attacker.discard();
		shooter.discard();
		arrow.discard();
		helper.succeed();
	}

	private static ModdedProjectileEntity standProjectile(int kind, Zombie shooter, Level level) {
		return kind == 0 ? new HGEmeraldEntity(shooter, level) : new MRCrossfireHurricaneEntity(shooter, level);
	}

	private static final class Fixture {
		final Player user;
		final StandType type;
		final StandPower power;
		final StandEntity stand;
		final Vec3 pos;

		Fixture(GameTestHelper helper) {
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
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			face();
		}

		// half a block in front of the user, facing south as the user does; the attacker stands behind it
		void face() {
			stand.moveTo(pos.x, pos.y, pos.z + 0.5, 0, 0);
			stand.setYHeadRot(0);
			stand.yRotO = 0;
			stand.yHeadRotO = 0;
			stand.setDeltaMovement(Vec3.ZERO);
		}

		// hurts the Stand for 4; returns what its user lost
		float hit(DamageSource source) {
			user.setHealth(user.getMaxHealth());
			user.invulnerableTime = 0;
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			face();
			stand.invulnerableTime = 0;
			float before = user.getHealth();
			stand.hurt(source, 4);
			return before - user.getHealth();
		}

		void close() {
			user.removeAllEffects();
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
