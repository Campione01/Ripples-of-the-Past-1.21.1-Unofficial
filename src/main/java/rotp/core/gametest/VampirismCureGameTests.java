package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.vampirism.VampirismData;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GameplayEventHandler: onFoodEaten started the vampirism cure from an Enchanted Golden Apple eaten
 * under Weakness V, onWakeUp finished a complete cure, and cancelPotionRemoval kept the hidden vampire
 * passive effects from milk and effect clearing.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampirismCureGameTests {
	private VampirismCureGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void enchantedGoldenAppleStartsCure(GameTestHelper helper) {
		Player player = vampire(helper);
		try {
			VampirismData data = data(player);
			eat(player, Items.ENCHANTED_GOLDEN_APPLE);
			helper.assertTrue(!data.isBeingCured(), "an Enchanted Golden Apple without Weakness V started the cure");
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 4));
			eat(player, Items.GOLDEN_APPLE);
			helper.assertTrue(!data.isBeingCured(), "a plain Golden Apple started the vampirism cure");
			eat(player, Items.ENCHANTED_GOLDEN_APPLE);
			helper.assertTrue(data.getCuringTicks() == 1,
					"eating an Enchanted Golden Apple under Weakness V did not start the cure (1.16 onFoodEaten): ticks="
					+ data.getCuringTicks());
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void wakingUpFinishesCompleteCure(GameTestHelper helper) {
		Player player = vampire(helper);
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
			VampirismData data = data(player);
			data.setCuringTicks(player, 1);
			NeoForge.EVENT_BUS.post(new PlayerWakeUpEvent(player, false, true));
			helper.assertTrue(power.getPowerType() == ModPlayerPowers.VAMPIRISM.get(),
					"waking up in the middle of the cure removed vampirism");
			data.setCuringTicks(player, Integer.MAX_VALUE);
			helper.assertTrue(data.isCuringComplete(player), "the cure did not reach its end");
			NeoForge.EVENT_BUS.post(new PlayerWakeUpEvent(player, false, true));
			helper.assertTrue(power.getPowerType() == null,
					"waking up with the cure complete kept vampirism (1.16 onWakeUp -> finishCuringOnWakingUp): "
					+ power.getPowerType());
			helper.assertTrue(player.getFoodData().getFoodLevel() == 1,
					"the cured player's food level is " + player.getFoodData().getFoodLevel() + ", 1.16 set it to 1");
			helper.assertTrue(!player.hasEffect(MobEffects.NIGHT_VISION), "the cured player kept the hidden vampire night vision");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void milkKeepsHiddenVampirePassives(GameTestHelper helper) {
		Player player = vampire(helper);
		try {
			MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);
			helper.assertTrue(nightVision != null && !nightVision.isVisible() && !nightVision.showIcon(),
					"the new vampire has no hidden night vision");
			player.addEffect(new MobEffectInstance(MobEffects.LUCK, 600, 0));
			player.removeEffectsCuredBy(EffectCures.MILK);
			helper.assertTrue(player.hasEffect(MobEffects.NIGHT_VISION),
					"milk removed the hidden vampire night vision (1.16 cancelPotionRemoval kept it)");
			helper.assertTrue(!player.hasEffect(MobEffects.LUCK), "milk no longer removes an ordinary effect");
			player.removeAllEffects();
			helper.assertTrue(player.hasEffect(MobEffects.NIGHT_VISION), "clearing effects removed the hidden vampire night vision");
			PowerClass.PLAYER_POWER.attachGet(player).setPowerType(null);
			helper.assertTrue(!player.hasEffect(MobEffects.NIGHT_VISION), "losing vampirism no longer removes its hidden night vision");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	private static void eat(Player player, net.minecraft.world.item.Item item) {
		NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, new ItemStack(item), 0, ItemStack.EMPTY));
	}

	private static VampirismData data(Player player) {
		return PlayerPower.getPowerData(player, ModPlayerPowers.VAMPIRISM).orElseThrow();
	}

	private static Player vampire(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		player.moveTo(origin.x, origin.y, origin.z);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the vampire cure test player");
		PowerClass.PLAYER_POWER.attachGet(player).setPowerType(ModPlayerPowers.VAMPIRISM.get());
		return player;
	}
}
