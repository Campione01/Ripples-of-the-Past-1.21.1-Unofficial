package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.init.ModItems;
import rotp.core.mechanics.standarrow.StandArrowEntity;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandArrowEntity wear: onHitEntity called arrowItem.hurtAndBreak(1, shooter) before target.hurt, blocked or
 * not, and doPostHurtEffects added one more point when the target survived and the shooter was not creative. A
 * creative shooter was exempt; the target's game mode never mattered. The crafted arrow's durability came from the
 * arrowDurability config (1.16 StandArrowItem.getMaxDamage).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandArrowWearGameTests {
	private StandArrowWearGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void survivingTargetWearsTheArrowTwice(GameTestHelper helper) {
		List<Entity> created = new ArrayList<>();
		try {
			LivingEntity shooter = create(helper, EntityType.ZOMBIE, created);
			LivingEntity cow = create(helper, EntityType.COW, created);
			StandArrowEntity arrow = arrow(helper, shooter, 0, created);
			hit(arrow, cow);
			helper.assertTrue(cow.isAlive(), "The cow should survive the 2 damage");
			int wear = arrow.getPickupItem().getDamageValue();
			helper.assertTrue(wear == 2, "1.16: a hit on a surviving target wears 2; wore " + wear);
		}
		finally {
			created.forEach(Entity::discard);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void killedOrBlockedTargetWearsTheArrowOnce(GameTestHelper helper) {
		List<Entity> created = new ArrayList<>();
		try {
			LivingEntity shooter = create(helper, EntityType.ZOMBIE, created);

			LivingEntity bat = create(helper, EntityType.BAT, created);
			bat.setHealth(1.0F);
			StandArrowEntity arrow = arrow(helper, shooter, 0, created);
			hit(arrow, bat);
			helper.assertTrue(!bat.isAlive(), "The bat should die from the 2 damage");
			int wear = arrow.getPickupItem().getDamageValue();
			helper.assertTrue(wear == 1, "1.16: a killing hit wears 1 (before the hit only); wore " + wear);

			LivingEntity cow = create(helper, EntityType.COW, created);
			cow.setInvulnerable(true);
			float before = cow.getHealth();
			arrow = arrow(helper, shooter, 0, created);
			hit(arrow, cow);
			helper.assertTrue(cow.getHealth() == before, "The invulnerable cow should block the hit");
			wear = arrow.getPickupItem().getDamageValue();
			helper.assertTrue(wear == 1, "1.16: a blocked hit still wears 1; wore " + wear);
		}
		finally {
			created.forEach(Entity::discard);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void creativeShooterDoesNotWearTheArrow(GameTestHelper helper) {
		List<Entity> created = new ArrayList<>();
		try {
			Player shooter = GameTestPlayers.makeServerMockPlayer(helper, GameType.CREATIVE);
			shooter.getAbilities().instabuild = true;
			created.add(shooter);
			LivingEntity cow = create(helper, EntityType.COW, created);
			StandArrowEntity arrow = arrow(helper, shooter, 0, created);
			hit(arrow, cow);
			helper.assertTrue(cow.isAlive(), "The cow should survive the 2 damage");
			int wear = arrow.getPickupItem().getDamageValue();
			helper.assertTrue(wear == 0, "1.16: a creative shooter's arrow does not wear; wore " + wear);
		}
		finally {
			created.forEach(Entity::discard);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void arrowBrokenBeforeTheHitBreaksOnce(GameTestHelper helper) {
		List<Entity> created = new ArrayList<>();
		try {
			LivingEntity shooter = create(helper, EntityType.ZOMBIE, created);
			LivingEntity cow = create(helper, EntityType.COW, created);
			ItemStack stack = new ItemStack(ModItems.STAND_ARROW_METEORITE.get());
			StandArrowEntity arrow = arrow(helper, shooter, stack.getMaxDamage() - 1, created);
			AABB around = arrow.getBoundingBox().inflate(3.0);
			hit(arrow, cow);
			List<ItemEntity> shards = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around,
					item -> item.getItem().is(ModItems.STAND_ARROW_SHARD.get()));
			created.addAll(shards);
			helper.assertTrue(arrow.isRemoved(), "1.16: the last point, worn before the hit, breaks the arrow");
			helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "The broken arrow's hit should still land");
			helper.assertTrue(shards.size() == 3, "The arrow should break once (3 shards); found " + shards.size());
			// the pierce after the break still sees the meteorite arrow, not a default Stand Arrow
			helper.assertTrue(arrow.getPickupItem().is(ModItems.STAND_ARROW_METEORITE.get()),
					"The broken arrow should keep its own item; got " + arrow.getPickupItem());
		}
		finally {
			created.forEach(Entity::discard);
		}
		helper.succeed();
	}

	// config is set and restored inside this call, so other tests never see it
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void meteoriteArrowDurabilityFollowsConfig(GameTestHelper helper) {
		ModConfigSpec.ConfigValue<Integer> durability = JojoModConfig.COMMON_SPEC.getValues()
				.get(List.of("Stand settings", "arrowDurability"));
		int previous = durability.get();
		List<Entity> created = new ArrayList<>();
		try {
			durability.set(40);
			ItemStack stack = new ItemStack(ModItems.STAND_ARROW_METEORITE.get());
			helper.assertTrue(stack.isDamageableItem() && stack.getMaxDamage() == 40,
					"arrowDurability 40 must give the Meteorite Arrow 40 durability; got " + stack.getMaxDamage());
			// the client draws the durability bar from its synced copy
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			new JojoModConfig.Common.SyncedValues(JojoModConfig.getCommonConfigInstance(false)).writeToBuf(buf);
			JojoModConfig.applySyncedConfig(new JojoModConfig.Common.SyncedValues(buf));
			int client = JojoModConfig.getCommonConfigInstance(true).arrowDurability.get();
			helper.assertTrue(client == 40, "arrowDurability did not reach the client copy; got " + client);

			// durability 3: 2 points already worn, the hit's point breaks the arrow
			durability.set(3);
			LivingEntity shooter = create(helper, EntityType.ZOMBIE, created);
			LivingEntity cow = create(helper, EntityType.COW, created);
			StandArrowEntity arrow = arrow(helper, shooter, 2, created);
			AABB around = arrow.getBoundingBox().inflate(3.0);
			hit(arrow, cow);
			created.addAll(helper.getLevel().getEntitiesOfClass(ItemEntity.class, around,
					item -> item.getItem().is(ModItems.STAND_ARROW_SHARD.get())));
			helper.assertTrue(arrow.isRemoved(), "arrowDurability 3: the third worn point must break the arrow");
		}
		finally {
			durability.set(previous);
			JojoModConfig.resetSyncedConfig();
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

	// meteorite arrow: 25 durability, 2 damage per hit
	private static StandArrowEntity arrow(GameTestHelper helper, Entity shooter, int damageValue, List<Entity> created) {
		Vec3 pos = helper.absoluteVec(new Vec3(0.5, 2.5, 1.5));
		ItemStack stack = new ItemStack(ModItems.STAND_ARROW_METEORITE.get());
		stack.setDamageValue(damageValue);
		StandArrowEntity arrow = new StandArrowEntity(helper.getLevel(), pos.x, pos.y, pos.z, stack, null);
		created.add(arrow);
		arrow.setOwner(shooter);
		arrow.setBaseDamage(2.0);
		arrow.setCritArrow(false);
		arrow.setDeltaMovement(1.0, 0.0, 0.0);
		return arrow;
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
