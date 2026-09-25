package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 PowerBaseImpl.checkRequirements refused any action whose performer was stunned (unless it ignoresPerformerStun):
 * the summoned Stand for a Stand action (StandAction.getPerformer), else the user. Add-on plain abilities call
 * Ability.checkPerformerStun for it.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerformerStunGameTests {
	private static final String STUN_KEY = "jojo.message.action_condition.stun";

	private PerformerStunGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void performerStunFollowsSummonedStandElseUser(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		StandEntity stand = null;
		try {
			helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the test player");
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing Stand type star_platinum");
			StandPower power = PowerClass.STAND.attachGet(player);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");

			helper.assertTrue(Ability.checkPerformerStun(power).isPositive(), "An unstunned user was refused");
			// no Stand out: the user performs
			player.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 200));
			assertStun(helper, Ability.checkPerformerStun(power), "a stunned user with no Stand out");
			PlayerPower playerPower = PowerClass.PLAYER_POWER.attachGet(player);
			assertStun(helper, Ability.checkPerformerStun(playerPower), "a stunned user of a non-Stand power");
			player.removeEffect(ModStatusEffects.STUN);
			helper.assertTrue(Ability.checkPerformerStun(power).isPositive(), "The user's cleared stun still refused");

			// Stand out: the Stand performs
			helper.assertTrue(type.summon(player, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "The summoned Stand is missing");
			stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 200));
			helper.assertTrue(ModStatusEffects.isStunned(stand), "The Stand did not take the stun");
			assertStun(helper, Ability.checkPerformerStun(power), "a stunned summoned Stand");
			stand.removeEffect(ModStatusEffects.STUN);
			helper.assertTrue(Ability.checkPerformerStun(power).isPositive(), "The Stand's cleared stun still refused");
		}
		finally {
			if (stand != null) {
				PowerClass.STAND.get(player).setSummonedStand(null);
				stand.discard();
			}
			player.discard();
		}
		helper.succeed();
	}

	private static void assertStun(GameTestHelper helper, ConditionCheck check, String who) {
		Component warning = check.getWarning();
		helper.assertTrue(!check.isPositive() && warning != null
				&& warning.getContents() instanceof TranslatableContents contents
				&& STUN_KEY.equals(contents.getKey()),
				"No stun refusal for " + who + ": " + warning);
	}
}
