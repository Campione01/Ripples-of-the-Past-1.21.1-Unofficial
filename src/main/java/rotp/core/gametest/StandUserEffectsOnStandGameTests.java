package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.getDamageAfterMagicAbsorb: the user's Resistance also cuts the hits their Stand takes.
 * 1.16 SHARED_EFFECTS_FROM_USER: the user's stun and immobilize are copied onto the summoned Stand, removed with the
 * user's, and copied onto a Stand summoned while they last.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandUserEffectsOnStandGameTests {
	private static final float EPS = 1.0E-3F;

	private StandUserEffectsOnStandGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void userResistanceCutsHitsOnTheStand(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			float amount = 4;
			float plain = f.hitStandFromBehind(amount);
			float expected = amount * (1 - StandStatFormulas.getPhysicalResistance(
					f.stand.getDurability(), f.stand.getAttackDamage(), 0, amount));
			helper.assertTrue(plain > 0 && close(plain, expected), "An idle Stand hit from behind: " + plain + " instead of " + expected);

			f.user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
			float resistanceI = f.hitStandFromBehind(amount);
			helper.assertTrue(close(resistanceI, plain * 0.8F),
					"The user's Resistance I must cut the Stand's hit by a fifth: " + resistanceI + " instead of " + plain * 0.8F);

			f.user.removeEffect(MobEffects.DAMAGE_RESISTANCE);
			f.user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 2));
			float resistanceIII = f.hitStandFromBehind(amount);
			helper.assertTrue(close(resistanceIII, plain * 0.4F),
					"The user's Resistance III must cut the Stand's hit by three fifths: " + resistanceIII);
			helper.assertTrue(!f.stand.hasEffect(MobEffects.DAMAGE_RESISTANCE), "Resistance is not copied onto the Stand");
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void userStunAndImmobilizeReachTheStand(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			f.user.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 100));
			helper.assertTrue(ModStatusEffects.isStunned(f.stand), "A stunned user's summoned Stand must be stunned too");
			f.user.removeEffect(ModStatusEffects.STUN);
			helper.assertTrue(!f.stand.hasEffect(ModStatusEffects.STUN), "The Stand's stun must end with the user's");

			f.user.addEffect(new MobEffectInstance(ModStatusEffects.IMMOBILIZE, 100));
			helper.assertTrue(f.stand.hasEffect(ModStatusEffects.IMMOBILIZE), "An immobilized user's Stand must be immobilized too");
			f.user.removeEffect(ModStatusEffects.IMMOBILIZE);
			helper.assertTrue(!f.stand.hasEffect(ModStatusEffects.IMMOBILIZE), "The Stand's immobilize must end with the user's");

			// the registry holder, as /effect gives it, not the DeferredHolder
			Holder<MobEffect> stunRef = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ModStatusEffects.STUN.get());
			f.user.addEffect(new MobEffectInstance(stunRef, 100));
			helper.assertTrue(f.stand.hasEffect(stunRef), "A stun given through the registry holder must reach the Stand too");
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standSummonedMidStunIsStunned(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			f.type.forceUnsummon(f.user, f.power);
			helper.assertTrue(!f.power.isSummoned(), "Could not unsummon Star Platinum");
			f.user.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 100));
			helper.assertTrue(f.type.summon(f.user, f.power), "Could not summon Star Platinum again");
			StandEntity stand = f.power.getSummonedStandEntity();
			helper.assertTrue(stand != null && ModStatusEffects.isStunned(stand),
					"A Stand summoned while its user is stunned must be stunned too");
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
			helper.assertTrue(stand.getCurStandAction() == null && !stand.isStandBlocking(), "The Stand is not idle");
		}

		// half a block in front of the user, facing south as the user does
		void face() {
			stand.moveTo(pos.x, pos.y, pos.z + 0.5, 0, 0);
			stand.setYHeadRot(0);
			stand.yRotO = 0;
			stand.yHeadRotO = 0;
			stand.setDeltaMovement(Vec3.ZERO);
		}

		// a Stand's hit from behind the idle Stand, so no guard takes it; returns what the user lost
		float hitStandFromBehind(float amount) {
			user.setHealth(user.getMaxHealth());
			user.invulnerableTime = 0;
			user.setDeltaMovement(Vec3.ZERO);
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			face();
			stand.invulnerableTime = 0;
			Zombie attacker = EntityType.ZOMBIE.create(helper.getLevel());
			helper.assertTrue(attacker != null, "Could not create the attacker");
			attacker.moveTo(stand.getX(), stand.getY(), stand.getZ() - 2, 0, 0);
			float before = user.getHealth();
			stand.hurt(DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_ATTACK, attacker), amount);
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
