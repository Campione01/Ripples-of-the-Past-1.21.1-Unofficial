package rotp.core.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 DamageUtil.dealHamonDamage sent any amount to hurt(), zero too (an action at an efficiency of 0, an empty leaves
 * glider): a mob takes such a hit (hurt animation, knockback, true) and a player refuses it.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonZeroAmountGameTests {
	private HamonZeroAmountGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aHamonHitOfZeroLandsOnAMobAndNotOnAPlayer(GameTestHelper helper) {
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HamonZeroUser"));
		Player victim = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HamonZeroVictim"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 2, 0)));
		user.setPos(origin.x, origin.y, origin.z);
		victim.setPos(origin.x + 2.0D, origin.y, origin.z);
		helper.assertTrue(helper.getLevel().addFreshEntity(user) && helper.getLevel().addFreshEntity(victim),
				"setup: could not add the players");
		Pig pig = helper.spawn(EntityType.PIG, new Vec3(2.5D, 2.0D, 3.5D));
		pig.setNoGravity(true);
		try {
			float pigHealth = pig.getHealth();
			boolean pigHit = HamonAbilityHelpers.hamonHurt(pig, user, 0.0F);
			helper.assertTrue(pigHit && pig.hurtTime > 0 && pig.getHealth() == pigHealth,
					"1.16: a Hamon hit of 0 still goes to hurt(), which a mob takes: hit=" + pigHit + " hurtTime="
							+ pig.hurtTime + " health " + pigHealth + " -> " + pig.getHealth());

			boolean playerHit = HamonAbilityHelpers.hamonHurt(victim, user, 0.0F);
			helper.assertTrue(!playerHit && victim.hurtTime == 0,
					"1.16: a player refuses a hurt() of 0: hit=" + playerHit + " hurtTime=" + victim.hurtTime);
			helper.succeed();
		}
		finally {
			pig.discard();
			victim.discard();
			user.discard();
		}
	}
}
