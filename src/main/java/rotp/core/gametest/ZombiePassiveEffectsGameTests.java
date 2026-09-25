package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GameplayEventHandler.cancelPotionRemoval kept the hidden passive effects of every non-Stand power,
 * so a Zombie kept its Health Boost and Night Vision through milk, totems and effect clearing.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZombiePassiveEffectsGameTests {
	private ZombiePassiveEffectsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void milkKeepsHiddenZombiePassives(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		player.moveTo(origin.x, origin.y, origin.z);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the zombie passive test player");
		try {
			PowerClass.PLAYER_POWER.attachGet(player).setPowerType(ModPlayerPowers.ZOMBIE.get());
			// 1.16 ZombiePowerType: Health Boost at difficulty * 2, Night Vision at 0
			int healthBoost = helper.getLevel().getDifficulty().getId() * 2;
			assertHidden(helper, player, MobEffects.HEALTH_BOOST, healthBoost, "the new zombie");
			assertHidden(helper, player, MobEffects.NIGHT_VISION, 0, "the new zombie");

			player.addEffect(new MobEffectInstance(MobEffects.LUCK, 600, 0));
			player.removeEffectsCuredBy(EffectCures.MILK);
			assertHidden(helper, player, MobEffects.HEALTH_BOOST, healthBoost, "after milk");
			assertHidden(helper, player, MobEffects.NIGHT_VISION, 0, "after milk");
			helper.assertTrue(!player.hasEffect(MobEffects.LUCK), "milk no longer removes an ordinary effect");

			player.removeEffectsCuredBy(EffectCures.PROTECTED_BY_TOTEM);
			assertHidden(helper, player, MobEffects.HEALTH_BOOST, healthBoost, "after a totem");
			player.removeAllEffects();
			assertHidden(helper, player, MobEffects.NIGHT_VISION, 0, "after clearing effects");

			PowerClass.PLAYER_POWER.attachGet(player).setPowerType(null);
			helper.assertTrue(!player.hasEffect(MobEffects.NIGHT_VISION) && !player.hasEffect(MobEffects.HEALTH_BOOST),
					"losing the zombie power no longer removes its hidden passives");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	private static void assertHidden(GameTestHelper helper, Player player, Holder<MobEffect> effect, int amplifier, String when) {
		MobEffectInstance instance = player.getEffect(effect);
		helper.assertTrue(instance != null && !instance.isVisible() && !instance.showIcon() && instance.getAmplifier() == amplifier,
				"hidden zombie " + effect.getRegisteredName() + " " + amplifier + " missing " + when
				+ " (1.16 cancelPotionRemoval kept it), got " + instance);
	}
}
