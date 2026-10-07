package rotp.core.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import rotp.core.core.JojoMod;
import rotp.core.init.ModItems;
import rotp.core.mechanics.standarrow.StandArrowItem;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A mod that widens every ArrowItem's stack limit on the way into Item's constructor (it calls
 * {@code properties.stacksTo(9999)} on whatever the arrow passes up) must not break the Stand Arrows, which have
 * durability: Item's constructor rejects a stackable item with durability.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandArrowUnstackableGameTests {
	private static final String VANILLA_FAILURE = "Item cannot have both durability and be stackable";

	private StandArrowUnstackableGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void widenedArrowStackLimitKeepsStandArrowValid(GameTestHelper helper) {
		// control: the same foreign call on a plain durability item is the reported registration failure
		Object plain = build(widen(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).durability(25)));
		helper.assertTrue(plain instanceof IllegalStateException failure && VANILLA_FAILURE.equals(failure.getMessage()),
				"Fixture: widening a plain durability item did not fail as vanilla does: " + plain);

		for (int durability : new int[] { 25, 250 }) {
			Object built = build(widen(StandArrowItem.properties(Rarity.RARE, durability)));
			helper.assertTrue(built instanceof DataComponentMap,
					"A widened arrow stack limit fails the Stand Arrow's registration: " + built);
			DataComponentMap components = (DataComponentMap) built;
			helper.assertTrue(components.getOrDefault(DataComponents.MAX_STACK_SIZE, 0) == 1,
					"The Stand Arrow became stackable: " + components.get(DataComponents.MAX_STACK_SIZE));
			helper.assertTrue(Integer.valueOf(durability).equals(components.get(DataComponents.MAX_DAMAGE))
					&& Integer.valueOf(0).equals(components.get(DataComponents.DAMAGE)),
					"The Stand Arrow lost its durability " + durability + ": " + components.get(DataComponents.MAX_DAMAGE));
			helper.assertTrue(components.get(DataComponents.RARITY) == Rarity.RARE, "The Stand Arrow lost its rarity");
		}

		for (Item item : new Item[] { ModItems.STAND_ARROW.get(), ModItems.STAND_ARROW_BEETLE.get(),
				ModItems.STAND_ARROW_METEORITE.get() }) {
			ItemStack stack = new ItemStack(item);
			helper.assertTrue(item.getDefaultMaxStackSize() == 1 && stack.getMaxStackSize() == 1 && stack.isDamageableItem()
					&& item.components().has(DataComponents.MAX_DAMAGE) && stack.isEnchantable(),
					"The registered " + item + " is not an unstackable, damageable, enchantable arrow");
		}
		helper.assertTrue(ModItems.STAND_ARROW.get().components().get(DataComponents.RARITY) == Rarity.RARE
				&& ModItems.STAND_ARROW_BEETLE.get().components().get(DataComponents.RARITY) == Rarity.EPIC
				&& ModItems.STAND_ARROW_METEORITE.get().components().get(DataComponents.RARITY) == Rarity.UNCOMMON
				&& Integer.valueOf(25).equals(ModItems.STAND_ARROW.get().components().get(DataComponents.MAX_DAMAGE))
				&& Integer.valueOf(250).equals(ModItems.STAND_ARROW_BEETLE.get().components().get(DataComponents.MAX_DAMAGE))
				&& Integer.valueOf(25).equals(ModItems.STAND_ARROW_METEORITE.get().components().get(DataComponents.MAX_DAMAGE)),
				"A registered Stand Arrow changed its rarity or default durability");
		helper.succeed();
	}

	// what the foreign constructor hook does, and the direct component form of the same change
	private static Item.Properties widen(Item.Properties properties) {
		return properties.stacksTo(9999).component(DataComponents.MAX_STACK_SIZE, 64);
	}

	// Item's constructor step: the built components, or the failure it throws
	private static Object build(Item.Properties properties) {
		try {
			Method method = Item.Properties.class.getDeclaredMethod("buildAndValidateComponents");
			method.setAccessible(true);
			return method.invoke(properties);
		}
		catch (InvocationTargetException e) {
			return e.getCause();
		}
		catch (ReflectiveOperationException | RuntimeException e) {
			throw new IllegalStateException("Could not run Item.Properties.buildAndValidateComponents", e);
		}
	}
}
