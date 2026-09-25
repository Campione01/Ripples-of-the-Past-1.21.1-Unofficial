package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.init.ModItems;
import rotp.core.mechanics.standarrow.StandArrowEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandArrowEntity.onHitEntity set dealtDamage before target.hurt, blocked or not. findHitEntity then
 * returned null, so the arrow hit no more entities, and tick started the Loyalty return right away.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandArrowLoyaltyGameTests {
	private StandArrowLoyaltyGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void arrowStopsHittingEntitiesAfterAHit(GameTestHelper helper) {
		List<Entity> created = new ArrayList<>();
		try {
			LivingEntity shooter = create(helper, EntityType.ZOMBIE, created);
			Cow cow = helper.spawn(EntityType.COW, new Vec3(1.5, 2.0, 1.5));
			created.add(cow);
			cow.setNoAi(true);

			// blocked hit
			cow.setInvulnerable(true);
			StandArrowEntity arrow = arrow(helper, shooter, 0, created);
			helper.assertTrue(findHit(arrow) != null, "A fresh arrow should find the cow on its path");
			float before = cow.getHealth();
			hit(arrow, cow);
			helper.assertTrue(cow.getHealth() == before, "The invulnerable cow should block the hit");
			helper.assertTrue(findHit(arrow) == null, "1.16: a blocked hit still stops further entity hits");

			// landed hit
			cow.setInvulnerable(false);
			arrow = arrow(helper, shooter, 0, created);
			helper.assertTrue(findHit(arrow) != null, "A fresh arrow should find the cow on its path");
			hit(arrow, cow);
			helper.assertTrue(cow.isAlive() && cow.getHealth() < before, "The hit should land on the cow");
			helper.assertTrue(findHit(arrow) == null, "1.16: an arrow that hit an entity hits no more entities");
		}
		finally {
			created.forEach(Entity::discard);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void loyaltyArrowReturnsRightAfterAHit(GameTestHelper helper) {
		List<Entity> created = new ArrayList<>();
		try {
			LivingEntity shooter = create(helper, EntityType.ZOMBIE, created);
			LivingEntity cow = create(helper, EntityType.COW, created);
			StandArrowEntity arrow = arrow(helper, shooter, 3, created);
			helper.assertTrue(arrow.getLoyaltyLevel() == 3, "Loyalty III should reach the arrow; got " + arrow.getLoyaltyLevel());
			helper.assertTrue(!arrow.isNoPhysics(), "A fresh arrow should not be returning yet");
			hit(arrow, cow);
			arrow.tick();
			helper.assertTrue(!arrow.isRemoved(), "The arrow should still exist after the hit");
			helper.assertTrue(arrow.isNoPhysics(), "1.16: a Loyalty arrow starts returning on the tick after an entity hit");
		}
		finally {
			created.forEach(Entity::discard);
		}
		helper.succeed();
	}

	private static LivingEntity create(GameTestHelper helper, EntityType<?> type, List<Entity> created) {
		Entity entity = type.create(helper.getLevel());
		helper.assertTrue(entity instanceof LivingEntity, "Could not create " + type);
		entity.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)));
		created.add(entity);
		return (LivingEntity) entity;
	}

	// meteorite arrow flying +x toward the cow, 2 damage per hit
	private static StandArrowEntity arrow(GameTestHelper helper, Entity shooter, int loyalty, List<Entity> created) {
		Vec3 pos = helper.absoluteVec(new Vec3(0.5, 2.5, 1.5));
		ItemStack stack = new ItemStack(ModItems.STAND_ARROW_METEORITE.get());
		if (loyalty > 0) {
			stack.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
					.getHolderOrThrow(Enchantments.LOYALTY), loyalty);
		}
		StandArrowEntity arrow = new StandArrowEntity(helper.getLevel(), pos.x, pos.y, pos.z, stack, null);
		created.add(arrow);
		arrow.setOwner(shooter);
		arrow.setBaseDamage(2.0);
		arrow.setCritArrow(false);
		arrow.setDeltaMovement(1.0, 0.0, 0.0);
		return arrow;
	}

	private static EntityHitResult findHit(StandArrowEntity arrow) {
		try {
			Method find = StandArrowEntity.class.getDeclaredMethod("findHitEntity", Vec3.class, Vec3.class);
			find.setAccessible(true);
			Vec3 from = arrow.position();
			return (EntityHitResult) find.invoke(arrow, from, from.add(2.0, 0.0, 0.0));
		}
		catch (ReflectiveOperationException error) {
			throw new IllegalStateException("Could not call StandArrowEntity.findHitEntity", error);
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
}
