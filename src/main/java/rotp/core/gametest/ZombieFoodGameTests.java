package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.zombie.ZombieData;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GameplayEventHandler.onFoodEaten: a Zombie-power user eating meat (Food.isMeat) gained
 * nutrition * 10 energy and healed nutrition HP; other food gave nothing.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZombieFoodGameTests {
	private ZombieFoodGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void zombieEatingMeatGainsEnergyAndHeals(GameTestHelper helper) {
		Player player = zombie(helper);
		try {
			ZombieData data = data(player);
			data.setEnergy(player, 0.0F);
			player.setHealth(4.0F);
			// cooked beef: nutrition 8
			eat(player, Items.COOKED_BEEF);
			float expectedEnergy = Math.min(80.0F, data.getMaxEnergy(player));
			helper.assertTrue(Math.abs(data.getEnergy() - expectedEnergy) < 0.001F,
					"a Zombie eating cooked beef did not gain nutrition * 10 energy (1.16 onFoodEaten): energy="
					+ data.getEnergy() + ", expected " + expectedEnergy);
			float expectedHealth = Math.min(12.0F, player.getMaxHealth());
			helper.assertTrue(Math.abs(player.getHealth() - expectedHealth) < 0.001F,
					"a Zombie eating cooked beef did not heal nutrition HP: health=" + player.getHealth()
					+ ", expected " + expectedHealth);
			// rotten flesh counts as meat: nutrition 4
			eat(player, Items.ROTTEN_FLESH);
			float expectedAfterFlesh = Math.min(expectedEnergy + 40.0F, data.getMaxEnergy(player));
			helper.assertTrue(Math.abs(data.getEnergy() - expectedAfterFlesh) < 0.001F,
					"a Zombie eating rotten flesh did not gain energy: energy=" + data.getEnergy()
					+ ", expected " + expectedAfterFlesh);
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void zombieEatingNonMeatGainsNothing(GameTestHelper helper) {
		Player player = zombie(helper);
		try {
			ZombieData data = data(player);
			data.setEnergy(player, 0.0F);
			player.setHealth(4.0F);
			eat(player, Items.BREAD);
			eat(player, Items.COOKED_COD);
			helper.assertTrue(data.getEnergy() == 0.0F,
					"a Zombie gained energy from non-meat food (1.16 needs Food.isMeat): energy=" + data.getEnergy());
			helper.assertTrue(Math.abs(player.getHealth() - 4.0F) < 0.001F,
					"a Zombie healed from non-meat food: health=" + player.getHealth());
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	private static void eat(Player player, Item item) {
		NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, new ItemStack(item), 0, ItemStack.EMPTY));
	}

	private static ZombieData data(Player player) {
		return PlayerPower.getPowerData(player, ModPlayerPowers.ZOMBIE).orElseThrow();
	}

	private static Player zombie(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		player.moveTo(origin.x, origin.y, origin.z);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the zombie food test player");
		PowerClass.PLAYER_POWER.attachGet(player).setPowerType(ModPlayerPowers.ZOMBIE.get());
		return player;
	}
}
