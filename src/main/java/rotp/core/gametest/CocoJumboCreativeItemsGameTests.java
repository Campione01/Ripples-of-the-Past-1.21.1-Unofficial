package rotp.core.gametest;

import java.util.Collection;
import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.mrpresident.CocoJumboTurtleEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ModItems: COCO_JUMBO_SPAWN_EGG (0xE7E7E7 / 0x00AFAF), MR_PRESIDENT_KEY and
 * MR_PRESIDENT_MASTER_KEY were all in MAIN_TAB.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CocoJumboCreativeItemsGameTests {
	private CocoJumboCreativeItemsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void cocoJumboSpawnEggSpawnsTheTurtle(GameTestHelper helper) {
		Item item = ModItems.COCO_JUMBO_SPAWN_EGG.get();
		helper.assertTrue(item instanceof SpawnEggItem, "coco_jumbo_spawn_egg is not a spawn egg: " + item);
		SpawnEggItem egg = (SpawnEggItem) item;
		helper.assertTrue(egg.getColor(0) == 0xE7E7E7 && egg.getColor(1) == 0x00AFAF,
				"Coco Jumbo egg colors differ from 1.16: " + Integer.toHexString(egg.getColor(0))
				+ " / " + Integer.toHexString(egg.getColor(1)));
		helper.assertTrue(SpawnEggItem.byId(ModEntityTypes.COCO_JUMBO_TURTLE.get()) == egg,
				"Coco Jumbo turtle has no spawn egg");

		BlockPos floorRel = new BlockPos(1, 1, 1);
		helper.setBlock(floorRel, Blocks.STONE);
		BlockPos floor = helper.absolutePos(floorRel);
		AABB area = new AABB(floor.above()).inflate(2);
		helper.getLevel().getEntities(ModEntityTypes.COCO_JUMBO_TURTLE.get(), area, e -> true).forEach(e -> e.discard());

		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		List<CocoJumboTurtleEntity> spawned = List.of();
		try {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(egg));
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
			egg.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
			spawned = helper.getLevel().getEntities(ModEntityTypes.COCO_JUMBO_TURTLE.get(), area, e -> true);
			helper.assertTrue(spawned.size() == 1, "the Coco Jumbo spawn egg spawned " + spawned.size() + " turtles");
			helper.assertTrue(player.getMainHandItem().isEmpty(), "a survival player kept the used spawn egg");
			helper.succeed();
		}
		finally {
			spawned.forEach(e -> e.discard());
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void mainTabHasCocoJumboEggAndKeys(GameTestHelper helper) {
		CreativeModeTab tab = ModItems.MAIN_TAB.get();
		tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
				helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess()));
		Collection<ItemStack> items = tab.getDisplayItems();
		helper.assertTrue(!items.isEmpty(), "the main creative tab built no items");
		for (Item item : List.of(ModItems.COCO_JUMBO_SPAWN_EGG.get(),
				ModItems.MR_PRESIDENT_KEY.get(), ModItems.MR_PRESIDENT_MASTER_KEY.get())) {
			helper.assertTrue(items.stream().anyMatch(stack -> stack.is(item)),
					"the main creative tab lacks " + item + " (1.16 MAIN_TAB)");
		}
		helper.succeed();
	}
}
