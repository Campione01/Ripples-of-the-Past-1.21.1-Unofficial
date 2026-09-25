package rotp.core.gametest;

import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.impl.stands.goldexperience.GEProductEffectsEvents;
import rotp.core.impl.stands.goldexperience.GEProductEffectsState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GameplayEventHandler.onMobInteract: milking a flower-fed brown mooshroom that carries Gold Experience
 * product effects gave a suspicious stew with the flower effect plus the product effects, and used the flower
 * effect up, so the next bowl gave plain mushroom stew.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoldExperienceProductEffectsGameTests {
	private GoldExperienceProductEffectsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void flowerFedMooshroomGivesSuspiciousStewWithProductEffects(GameTestHelper helper) {
		MushroomCow cow = helper.spawn(EntityType.MOOSHROOM, new BlockPos(1, 2, 1));
		cow.setVariant(MushroomCow.MushroomType.BROWN);
		GEProductEffectsState.get(cow).setProductEffects(List.of(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1)));
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 1)));
		player.moveTo(origin.x, origin.y, origin.z);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the mooshroom test player");
		try {
			// vanilla flower feeding: a poppy gives the mooshroom a pending night vision stew effect
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POPPY));
			InteractionResult fed = player.interactOn(cow, InteractionHand.MAIN_HAND);
			helper.assertTrue(fed.consumesAction(), "the brown mooshroom did not eat the poppy: " + fed);

			ItemStack first = milkWithBowl(player, cow);
			helper.assertTrue(first.is(Items.SUSPICIOUS_STEW),
					"a flower-fed mooshroom with product effects gave " + first + " instead of suspicious stew (1.16 onMobInteract)");
			SuspiciousStewEffects stewEffects = first.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
			helper.assertTrue(stewEffects != null
					&& stewEffects.effects().stream().anyMatch(entry -> entry.effect() == MobEffects.NIGHT_VISION),
					"the suspicious stew lost the poppy's night vision effect: " + stewEffects);
			assertProductEffects(helper, first, "suspicious stew");

			ItemStack second = milkWithBowl(player, cow);
			helper.assertTrue(second.is(Items.MUSHROOM_STEW),
					"the flower effect was not used up by the first bowl: second bowl gave " + second);
			assertProductEffects(helper, second, "mushroom stew");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	private static ItemStack milkWithBowl(Player player, MushroomCow cow) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL));
		player.interactOn(cow, InteractionHand.MAIN_HAND);
		return player.getMainHandItem();
	}

	private static void assertProductEffects(GameTestHelper helper, ItemStack stew, String what) {
		helper.assertTrue(GEProductEffectsState.getItemEffects(stew).stream()
				.anyMatch(effect -> effect.getEffect() == MobEffects.DAMAGE_RESISTANCE && effect.getAmplifier() == 1),
				"the " + what + " lost the Gold Experience product effect: " + GEProductEffectsState.getItemEffects(stew));
		helper.assertTrue(stew.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
				.getBoolean(GEProductEffectsEvents.MOD_ADDS_EFFECTS_TO_ITEM),
				"the " + what + " is not marked as a Gold Experience product");
	}
}
