package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.init.ModItems;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Knife impacts as in 1.16 ItemProjectileEntity: failed hits bounce back, flying knives collide.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KnifeImpactGameTests {
	private KnifeImpactGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void failedHitBouncesKnifeBack(GameTestHelper helper) {
		List<Entity> spawned = new ArrayList<>();
		try {
			Pig target = spawnPig(helper, spawned, new Vec3(1.5D, 3.0D, 0.5D));
			target.setInvulnerable(true);
			Pig marker = spawnPig(helper, spawned, new Vec3(4.0D, 3.0D, 0.5D));
			float targetHealth = target.getHealth();
			float markerHealth = marker.getHealth();

			KnifeEntity knife = throwKnife(helper, spawned, new Vec3(0.5D, 3.3D, 0.5D), 2.0D, null);
			knife.tick();
			helper.assertTrue(!knife.isRemoved(), "A fast knife was removed after a failed hit");
			helper.assertTrue(knife.getDeltaMovement().x < 0.0D,
					"Knife did not bounce off a target that ignored the hit: motion=" + knife.getDeltaMovement());
			helper.assertTrue(Math.abs(knife.getDeltaMovement().x) < 0.25D,
					"Bounced knife kept too much speed: motion=" + knife.getDeltaMovement());
			for (int i = 0; i < 5 && !knife.isRemoved(); i++) {
				knife.tick();
			}
			helper.assertTrue(target.getHealth() == targetHealth, "Invulnerable target lost health");
			helper.assertTrue(marker.getHealth() == markerHealth && marker.hurtTime == 0,
					"Knife flew through the target and hit the mob behind it");
			helper.assertTrue(knife.getX() < target.getBoundingBox().minX,
					"Knife ended up past the target: x=" + knife.getX());
			helper.succeed();
		}
		finally {
			discardAll(spawned);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void stalledKnifeDropsItemOnFailedHit(GameTestHelper helper) {
		List<Entity> spawned = new ArrayList<>();
		try {
			Pig target = spawnPig(helper, spawned, new Vec3(1.5D, 3.0D, 0.5D));
			target.setInvulnerable(true);
			// start just before the target's 0.3-inflated hit box, crawling into it
			Vec3 start = new Vec3(target.getBoundingBox().minX - 0.3D - 5.0E-5D, target.getY() + 0.3D, target.getZ());
			KnifeEntity knife = throwKnifeAbsolute(helper, spawned, start, 1.0E-4D, null);
			knife.pickup = AbstractArrow.Pickup.ALLOWED;
			knife.tick();
			List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
					knife.getBoundingBox().inflate(2.0D), item -> item.getItem().is(ModItems.KNIFE.get()));
			spawned.addAll(drops);
			helper.assertTrue(knife.isRemoved(), "Stalled knife was not removed after a failed hit");
			helper.assertTrue(drops.size() == 1, "Stalled knife did not drop its item: drops=" + drops.size());
			helper.succeed();
		}
		finally {
			discardAll(spawned);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void flyingKnivesCollideExceptSameOwner(GameTestHelper helper) {
		List<Entity> spawned = new ArrayList<>();
		Pig ownerA = EntityType.PIG.create(helper.getLevel());
		Pig ownerB = EntityType.PIG.create(helper.getLevel());
		try {
			helper.assertTrue(ownerA != null && ownerB != null, "Could not create knife owners");
			Vec3 from = new Vec3(0.5D, 3.3D, 0.5D);
			Vec3 mid = new Vec3(1.5D, 3.3D, 0.5D);

			// another thrower's knife in the air stops the knife
			KnifeEntity other = throwKnife(helper, spawned, mid, 0.0D, ownerB);
			KnifeEntity knife = throwKnife(helper, spawned, from, 2.0D, ownerA);
			knife.tick();
			helper.assertTrue(knife.getDeltaMovement().x < 0.0D,
					"Knife flew through another thrower's flying knife: motion=" + knife.getDeltaMovement());
			helper.assertTrue(!other.isRemoved(), "Hit knife was removed");
			knife.discard();

			// 1.16 isPickable is unconditional: another thrower's stuck knife stops it too
			other.inGround = true;
			KnifeEntity pastGround = throwKnife(helper, spawned, from, 2.0D, ownerA);
			pastGround.tick();
			helper.assertTrue(pastGround.getDeltaMovement().x < 0.0D,
					"Knife flew through another thrower's knife stuck in the ground: motion=" + pastGround.getDeltaMovement());
			helper.assertTrue(!other.isRemoved(), "Hit stuck knife was removed");
			pastGround.discard();
			other.discard();

			// one thrower's volley does not collide with itself
			KnifeEntity own = throwKnife(helper, spawned, mid, 0.0D, ownerA);
			KnifeEntity volley = throwKnife(helper, spawned, from, 2.0D, ownerA);
			volley.tick();
			helper.assertTrue(volley.getDeltaMovement().x > 0.0D && volley.getX() > own.getX(),
					"Knife collided with a knife from the same thrower: motion=" + volley.getDeltaMovement());
			helper.succeed();
		}
		finally {
			discardAll(spawned);
		}
	}

	// 1.16 ItemProjectileEntity: a burning knife sets its target on fire for 5 s
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void burningKnifeSetsTargetOnFire(GameTestHelper helper) {
		List<Entity> spawned = new ArrayList<>();
		try {
			Pig target = spawnPig(helper, spawned, new Vec3(1.5D, 3.0D, 0.5D));
			helper.assertTrue(target.getRemainingFireTicks() <= 0, "Test pig starts on fire");
			KnifeEntity knife = throwKnife(helper, spawned, new Vec3(0.5D, 3.3D, 0.5D), 2.0D, null);
			knife.igniteForSeconds(5.0F);
			helper.assertTrue(knife.isOnFire(), "Test knife is not on fire");
			knife.tick();
			helper.assertTrue(knife.isRemoved(), "Burning knife did not hit the pig");
			helper.assertTrue(target.getRemainingFireTicks() > 0,
					"Burning knife did not set the pig on fire: fireTicks=" + target.getRemainingFireTicks());
			helper.succeed();
		}
		finally {
			discardAll(spawned);
		}
	}

	// 1.16: a failed hit gives the target its old fire ticks back
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void burningKnifeFailedHitKeepsTargetFire(GameTestHelper helper) {
		List<Entity> spawned = new ArrayList<>();
		try {
			Pig target = spawnPig(helper, spawned, new Vec3(1.5D, 3.0D, 0.5D));
			target.setInvulnerable(true);
			target.setRemainingFireTicks(7);
			KnifeEntity knife = throwKnife(helper, spawned, new Vec3(0.5D, 3.3D, 0.5D), 2.0D, null);
			knife.igniteForSeconds(5.0F);
			helper.assertTrue(knife.isOnFire(), "Test knife is not on fire");
			knife.tick();
			helper.assertTrue(!knife.isRemoved() && knife.getDeltaMovement().x < 0.0D,
					"Burning knife did not bounce off the invulnerable pig: motion=" + knife.getDeltaMovement());
			helper.assertTrue(target.getRemainingFireTicks() == 7,
					"Failed hit changed the pig's fire ticks: fireTicks=" + target.getRemainingFireTicks());
			helper.succeed();
		}
		finally {
			discardAll(spawned);
		}
	}

	private static Pig spawnPig(GameTestHelper helper, List<Entity> spawned, Vec3 relPos) {
		Pig pig = helper.spawn(EntityType.PIG, relPos);
		spawned.add(pig);
		pig.setNoAi(true);
		pig.setNoGravity(true);
		pig.setDeltaMovement(Vec3.ZERO);
		return pig;
	}

	private static KnifeEntity throwKnife(GameTestHelper helper, List<Entity> spawned, Vec3 relPos,
			double speedX, @Nullable Entity owner) {
		return throwKnifeAbsolute(helper, spawned, helper.absoluteVec(relPos), speedX, owner);
	}

	private static KnifeEntity throwKnifeAbsolute(GameTestHelper helper, List<Entity> spawned, Vec3 pos,
			double speedX, @Nullable Entity owner) {
		KnifeEntity knife = new KnifeEntity(helper.getLevel(), pos.x, pos.y, pos.z, new ItemStack(ModItems.KNIFE.get()));
		if (owner != null) {
			knife.setOwner(owner);
		}
		knife.setNoGravity(true);
		knife.setDeltaMovement(speedX, 0.0D, 0.0D);
		helper.assertTrue(helper.getLevel().addFreshEntity(knife), "Could not add test knife");
		spawned.add(knife);
		return knife;
	}

	private static void discardAll(List<Entity> spawned) {
		for (Entity entity : spawned) {
			if (!entity.isRemoved()) {
				entity.discard();
			}
		}
	}
}
