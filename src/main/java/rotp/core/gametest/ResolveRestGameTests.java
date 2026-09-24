package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.objects_java.OptionalFloat;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ResolveCounter: Resolve mode takes off 0.6667 of the damage (RESOLVE_DMG_REDUCTION), and a Resolve effect that is
 * updated ends and starts again (LivingEntity.onEffectUpdated re-applied the attribute modifiers), which clears the
 * boosts and fills the value.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResolveRestGameTests {
	private ResolveRestGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void resolveModeTakesTwoThirdsOfTheDamage(GameTestHelper helper) {
		Player user = standUser(helper);
		try {
			StandPower power = StandPower.get(user);
			user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0));
			helper.assertTrue(user.hasEffect(ModStatusEffects.RESOLVE) && power.usesResolve(), "The user is not in Resolve mode");
			user.setHealth(20);
			user.invulnerableTime = 0;
			user.hurt(user.damageSources().generic(), 6.0F);
			float expected = 20 - 6.0F * (1 - ResolveCounter.RESOLVE_DMG_REDUCTION);
			helper.assertTrue(Math.abs(user.getHealth() - expected) < 0.01F,
					"Resolve mode must take 0.6667 of a hit off, as in 1.16: health " + user.getHealth() + ", expected " + expected);
		}
		finally {
			user.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void updatedResolveStartsAgain(GameTestHelper helper) {
		Player user = standUser(helper);
		try {
			StandPower power = StandPower.get(user);
			ResolveCounter counter = power.resolveCounter;
			user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0));
			helper.assertTrue(power.getResolveLevel() == 1, "A level 0 Resolve must raise the resolve level to 1");
			counter.boostAttack = 3;
			counter.hpOnGettingAttacked = OptionalFloat.of(5);
			counter.setResolveValue(power, counter.getMaxResolveValue(power) * 0.3F, 0);

			// a stronger Resolve updates the running one
			user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400, 1));
			helper.assertTrue(counter.boostAttack == 1 && !counter.hpOnGettingAttacked.isPresent(),
					"An updated Resolve must clear the boosts, as 1.16 onResolveEffectEnded did");
			helper.assertTrue(power.getResolveLevel() == 2
					&& counter.getResolveValue() == counter.getMaxResolveValue(power),
					"An updated Resolve must start again: level 2 and a full value");
		}
		finally {
			user.discard();
		}
		helper.succeed();
	}

	private static Player standUser(GameTestHelper helper) {
		// the factory FakePlayer ignores damage
		Player user = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		user.moveTo(pos.x, pos.y, pos.z, 0, 0);
		user.getAbilities().invulnerable = false;
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
		helper.assertTrue(type != null, "Missing registered Magician's Red");
		StandPower power = PowerClass.STAND.attachGet(user);
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant Magician's Red");
		helper.assertTrue(power.usesResolve() && power.getResolveLevel() == 0, "The new Stand does not use resolve from level 0");
		return user;
	}
}
