package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.MolotovEntity;
import rotp.core.init.ModItems;

/**
 * 1.16 MolotovEntity.onHitEntity dealt its direct hit as a plain IN_FIRE hurt of 2: inside the target's hurt cooldown
 * the hit was refused, or cut to its excess over the last hit, and the cooldown was not restarted. The port's
 * projectile fire type carries minecraft:bypasses_cooldown, so a plain hurt with it would land in full and restart the
 * cooldown. The target is set on fire for 10 seconds either way.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MolotovHurtCooldownGameTests {
	private static final float EPS = 1.0E-4F;

	private MolotovHurtCooldownGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void molotovHitInsideTheHurtCooldownIsRefused(GameTestHelper helper) {
		play(helper, (molotov, cow) -> {
			prime(helper, cow, 5.0F);
			cow.invulnerableTime = 15;
			hit(molotov, cow);
			helper.assertTrue(near(cow.getHealth(), 95.0F) && cow.invulnerableTime == 15 && near(cow.lastHurt, 5.0F),
					"1.16: the hurt cooldown refuses a Molotov hit (2) not stronger than the last hit (5) and keeps the cooldown (15);"
							+ " health " + cow.getHealth() + ", cooldown " + cow.invulnerableTime + ", last hit " + cow.lastHurt);
			helper.assertTrue(cow.getRemainingFireTicks() >= 200,
					"1.16: the Molotov still sets the target on fire for 10 seconds; fire ticks " + cow.getRemainingFireTicks());
		});
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void molotovHitInsideTheHurtCooldownDealsOnlyItsExcess(GameTestHelper helper) {
		play(helper, (molotov, cow) -> {
			prime(helper, cow, 1.0F);
			cow.invulnerableTime = 15;
			hit(molotov, cow);
			helper.assertTrue(near(cow.getHealth(), 98.0F) && cow.invulnerableTime == 15 && near(cow.lastHurt, 2.0F),
					"1.16: inside the hurt cooldown a Molotov hit deals only its excess over the last hit (2 - 1) and keeps the"
							+ " cooldown (15); health " + cow.getHealth() + ", cooldown " + cow.invulnerableTime + ", last hit "
							+ cow.lastHurt);
		});
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void molotovHitOutsideACooldownLandsAndStartsOne(GameTestHelper helper) {
		play(helper, (molotov, cow) -> {
			hit(molotov, cow);
			helper.assertTrue(near(cow.getHealth(), 98.0F) && cow.invulnerableTime == 20 && near(cow.lastHurt, 2.0F)
					&& cow.getRemainingFireTicks() >= 200,
					"1.16: outside a cooldown the Molotov hit deals 2, starts the cooldown and sets the target on fire; health "
							+ cow.getHealth() + ", cooldown " + cow.invulnerableTime + ", last hit " + cow.lastHurt + ", fire ticks "
							+ cow.getRemainingFireTicks());
		});
	}

	private interface Play {
		void run(MolotovEntity molotov, Cow cow);
	}

	private static void play(GameTestHelper helper, Play play) {
		ServerPlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "MolotovHurtCd"));
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 5, 0)));
		owner.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		owner.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(owner), "Could not add the owner");
		// high enough that the splash finds no block to put fire on
		Cow cow = helper.spawn(EntityType.COW, new Vec3(1.5D, 5.0D, 3.5D));
		cow.setNoAi(true);
		cow.setNoGravity(true);
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
		cow.setHealth(100.0F);
		MolotovEntity molotov = new MolotovEntity(helper.getLevel(), owner, new ItemStack(ModItems.MOLOTOV.get()));
		molotov.setPos(cow.getX(), cow.getY() + 0.5D, cow.getZ() - 0.6D);
		try {
			play.run(molotov, cow);
			helper.succeed();
		}
		finally {
			molotov.discard();
			cow.discard();
			owner.discard();
		}
	}

	private static void prime(GameTestHelper helper, Cow cow, float amount) {
		helper.assertTrue(cow.hurt(helper.getLevel().damageSources().generic(), amount) && near(cow.getHealth(), 100.0F - amount)
				&& near(cow.lastHurt, amount) && cow.invulnerableTime == 20, "setup: the first hit did not start the hurt cooldown");
	}

	private static void hit(MolotovEntity molotov, Cow cow) {
		try {
			Method method = MolotovEntity.class.getDeclaredMethod("onHitEntity", EntityHitResult.class);
			method.setAccessible(true);
			method.invoke(molotov, new EntityHitResult(cow));
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean near(float actual, float expected) {
		return Math.abs(actual - expected) < EPS;
	}
}
