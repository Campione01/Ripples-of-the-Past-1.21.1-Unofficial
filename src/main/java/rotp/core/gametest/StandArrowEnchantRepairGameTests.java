package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.standarrow.StandArrowEntity;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandArrowItem: Meteoric Ingots repair the arrow on an anvil (isValidRepairItem), the enchanting table offers
 * Loyalty and Sharpness (canApplyAtEnchantingTable), and StandArrowEntity.getBaseDamage adds the arrow item's own
 * Sharpness bonus (level 5: +3).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandArrowEnchantRepairGameTests {
	private static final double EPS = 1.0E-4;

	private StandArrowEnchantRepairGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void meteoricIngotRepairsStandArrowOnAnvil(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		ItemStack arrow = new ItemStack(ModItems.STAND_ARROW_METEORITE.get());
		helper.assertTrue(arrow.getMaxDamage() == 25, "stand_arrow_meteorite durability is 25; got " + arrow.getMaxDamage());
		arrow.setDamageValue(20);

		ItemStack repaired = anvil(player, arrow, new ItemStack(ModItems.METEORIC_INGOT.get()));
		// one ingot repairs a quarter of 25 (6): 20 -> 14
		helper.assertTrue(repaired.is(ModItems.STAND_ARROW_METEORITE.get()) && repaired.getDamageValue() == 14,
				"1.16: a Meteoric Ingot repairs the arrow from 20 to 14 damage; got " + repaired + " damage "
						+ repaired.getDamageValue());

		ItemStack notRepaired = anvil(player, arrow, new ItemStack(Items.IRON_INGOT));
		helper.assertTrue(notRepaired.isEmpty(), "An iron ingot must not repair the arrow; got " + notRepaired);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void enchantingTableOffersLoyaltyAndSharpness(GameTestHelper helper) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		ItemStack arrow = new ItemStack(ModItems.STAND_ARROW_METEORITE.get());
		helper.assertTrue(arrow.isEnchantable(), "The crafted arrow must be enchantable at the table");

		List<EnchantmentInstance> offers = EnchantmentHelper.getAvailableEnchantmentResults(30, arrow,
				registry.getOrCreateTag(EnchantmentTags.IN_ENCHANTING_TABLE).stream());
		helper.assertTrue(offers.stream().anyMatch(e -> e.enchantment.is(Enchantments.SHARPNESS)),
				"1.16: the table offers Sharpness for the Stand Arrow; offers " + names(offers));
		helper.assertTrue(offers.stream().anyMatch(e -> e.enchantment.is(Enchantments.LOYALTY)),
				"1.16: the table offers Loyalty for the Stand Arrow; offers " + names(offers));
		helper.assertTrue(offers.stream().noneMatch(e -> e.enchantment.is(Enchantments.EFFICIENCY)),
				"The table must not offer Efficiency for the Stand Arrow; offers " + names(offers));
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void sharpnessBookAppliesToStandArrowOnAnvil(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Holder<Enchantment> sharpness = holder(helper, Enchantments.SHARPNESS);
		ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(sharpness, 5));

		ItemStack result = anvil(player, new ItemStack(ModItems.STAND_ARROW.get()), book);
		helper.assertTrue(result.is(ModItems.STAND_ARROW.get())
				&& EnchantmentHelper.getItemEnchantmentLevel(sharpness, result) == 5,
				"1.16: a Sharpness V book applies to the Stand Arrow on an anvil; got " + result);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void arrowSharpnessAddsToBaseDamage(GameTestHelper helper) {
		ItemStack sharp = new ItemStack(ModItems.STAND_ARROW.get());
		sharp.enchant(holder(helper, Enchantments.SHARPNESS), 5);
		// real hit of a bow-fired arrow: the weapon step must keep the arrow's own bonus
		// 1.16 getDamageBonus for Sharpness V: 1 + 4 * 0.5 = 3, so base 2 -> 5
		float plainLost = hitCow(helper, new ItemStack(ModItems.STAND_ARROW.get()), new ItemStack(Items.BOW));
		float sharpLost = hitCow(helper, sharp, new ItemStack(Items.BOW));
		helper.assertTrue(Math.abs(plainLost - 2.0F) < EPS, "A plain Stand Arrow shot from a bow deals 2; lost " + plainLost);
		helper.assertTrue(Math.abs(sharpLost - 5.0F) < EPS,
				"1.16: a Sharpness V Stand Arrow shot from a bow deals 5; lost " + sharpLost);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void sharpStandArrowHitsHarder(GameTestHelper helper) {
		ItemStack sharp = new ItemStack(ModItems.STAND_ARROW.get());
		sharp.enchant(holder(helper, Enchantments.SHARPNESS), 5);
		// real hit at speed 1, base 2: plain ceil(2) = 2, Sharpness V ceil(2 + 3) = 5
		float plainLost = hitCow(helper, new ItemStack(ModItems.STAND_ARROW.get()));
		float sharpLost = hitCow(helper, sharp);
		helper.assertTrue(Math.abs(plainLost - 2.0F) < EPS, "A plain Stand Arrow deals 2; lost " + plainLost);
		helper.assertTrue(Math.abs(sharpLost - 5.0F) < EPS, "1.16: a Sharpness V Stand Arrow deals 5; lost " + sharpLost);
		helper.succeed();
	}

	// 1.16 StandArrowItem extends ArrowItem: a fully drawn bow fires a StandArrowEntity, not a vanilla Arrow
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void bowFiresStandArrowEntity(GameTestHelper helper) {
		Player player = armedShooter(helper, new ItemStack(Items.BOW), new ItemStack(ModItems.STAND_ARROW.get()));
		ItemStack bow = player.getMainHandItem();
		// 20 ticks of draw: full power
		bow.releaseUsing(helper.getLevel(), player, bow.getUseDuration(player) - 20);
		checkFiredStandArrow(helper, player, ModItems.STAND_ARROW.get());
		helper.succeed();
	}

	// same through a crossbow: a full charge loads the Stand Arrow, the next use fires it
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void crossbowFiresStandArrowEntity(GameTestHelper helper) {
		Player player = armedShooter(helper, new ItemStack(Items.CROSSBOW), new ItemStack(ModItems.STAND_ARROW_METEORITE.get()));
		ItemStack crossbow = player.getMainHandItem();
		crossbow.releaseUsing(helper.getLevel(), player, 0);
		helper.assertTrue(CrossbowItem.isCharged(crossbow), "The crossbow did not load the Stand Arrow");
		crossbow.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		checkFiredStandArrow(helper, player, ModItems.STAND_ARROW_METEORITE.get());
		helper.succeed();
	}

	// ArrowItem.createArrow callers without a weapon (add-on stand throws pass an empty stack) must not crash
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void standArrowCreateArrowWithoutWeapon(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		helper.assertTrue(ModItems.STAND_ARROW_BEETLE.get() instanceof ArrowItem, "1.16: the Stand Arrow is an ArrowItem");
		ArrowItem item = (ArrowItem) ModItems.STAND_ARROW_BEETLE.get();
		AbstractArrow arrow = item.createArrow(helper.getLevel(), new ItemStack(item), player, ItemStack.EMPTY);
		helper.assertTrue(arrow instanceof StandArrowEntity && arrow.getWeaponItem() == null,
				"An empty weapon stack must give a weaponless StandArrowEntity; got " + arrow);
		arrow.discard();
		helper.succeed();
	}

	// survival mock player holding the weapon, the Stand Arrow in the off hand
	private static Player armedShooter(GameTestHelper helper, ItemStack weapon, ItemStack arrow) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		player.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)));
		player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		player.setItemInHand(InteractionHand.OFF_HAND, arrow);
		return player;
	}

	// exactly one StandArrowEntity spawned, the arrow was used up, and its hit pierces a turtle
	private static void checkFiredStandArrow(GameTestHelper helper, Player player, Item arrowItem) {
		List<AbstractArrow> fired = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(4.0));
		LivingEntity turtle = (LivingEntity) ModEntityTypes.COCO_JUMBO_TURTLE.get().create(helper.getLevel());
		try {
			helper.assertTrue(fired.size() == 1 && fired.get(0) instanceof StandArrowEntity,
					"1.16: the weapon fires one StandArrowEntity, not a vanilla Arrow; got " + fired);
			StandArrowEntity arrow = (StandArrowEntity) fired.get(0);
			helper.assertTrue(arrow.getPickupItemStackOrigin().is(arrowItem),
					"The fired arrow must carry the shot Stand Arrow; got " + arrow.getPickupItemStackOrigin());
			helper.assertTrue(player.getOffhandItem().isEmpty(),
					"1.16: a Stand Arrow is used up when fired; off hand still has " + player.getOffhandItem());
			helper.assertTrue(turtle != null, "Could not create a Coco Jumbo turtle");
			turtle.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
			hit(arrow, turtle);
			helper.assertTrue(turtle.hasEffect(ModStatusEffects.STAND_VIRUS),
					"1.16: a bow or crossbow Stand Arrow hit runs onPiercedByArrow (Stand virus on the turtle)");
		}
		finally {
			fired.forEach(AbstractArrow::discard);
			if (turtle != null) {
				turtle.discard();
			}
		}
	}

	private static void hit(StandArrowEntity arrow, LivingEntity target) {
		try {
			Method onHit = StandArrowEntity.class.getDeclaredMethod("onHitEntity", EntityHitResult.class);
			onHit.setAccessible(true);
			onHit.invoke(arrow, new EntityHitResult(target));
		}
		catch (ReflectiveOperationException error) {
			throw new IllegalStateException("Could not call StandArrowEntity.onHitEntity", error);
		}
	}

	private static ItemStack anvil(Player player, ItemStack left, ItemStack right) {
		AnvilMenu menu = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(0).set(left.copy());
		menu.getSlot(1).set(right.copy());
		menu.createResult();
		return menu.getSlot(2).getItem().copy();
	}

	// fires StandArrowEntity.onHitEntity on a fresh cow, no shooter, no crit
	private static float hitCow(GameTestHelper helper, ItemStack arrowStack) {
		return hitCow(helper, arrowStack, null);
	}

	// same, with the weapon the arrow was fired from (null: dispenser or thrown)
	private static float hitCow(GameTestHelper helper, ItemStack arrowStack, ItemStack weapon) {
		LivingEntity cow = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(cow != null, "Could not create a cow");
		cow.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)));
		Vec3 pos = helper.absoluteVec(new Vec3(0.5, 2.5, 1.5));
		StandArrowEntity arrow = new StandArrowEntity(helper.getLevel(), pos.x, pos.y, pos.z, arrowStack, weapon);
		arrow.setBaseDamage(2.0);
		arrow.setCritArrow(false);
		arrow.setDeltaMovement(1.0, 0.0, 0.0);
		float before = cow.getHealth();
		try {
			Method onHit = StandArrowEntity.class.getDeclaredMethod("onHitEntity", EntityHitResult.class);
			onHit.setAccessible(true);
			onHit.invoke(arrow, new EntityHitResult(cow));
		}
		catch (ReflectiveOperationException error) {
			throw new IllegalStateException("Could not call StandArrowEntity.onHitEntity", error);
		}
		finally {
			arrow.discard();
			cow.discard();
		}
		return before - cow.getHealth();
	}

	private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
	}

	private static String names(List<EnchantmentInstance> offers) {
		StringBuilder sb = new StringBuilder("[");
		for (EnchantmentInstance e : offers) {
			sb.append(e.enchantment.getRegisteredName()).append(' ').append(e.level).append(", ");
		}
		return sb.append(']').toString();
	}
}
