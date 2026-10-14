package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.DamagingEntity;
import rotp.core.impl.stands.magiciansred.MRCrossfireHurricaneEntity;
import rotp.core.impl.stands.magiciansred.MRFireballEntity;

/**
 * 1.16 DamagingEntity.hurtTarget dealt its hit through DamageUtil.hurtThroughInvulTicks, and the small Crossfire
 * Hurricane blast's source bypassed the cooldown of a target already in it the same way: the hit ignores the target's
 * hurt cooldown and leaves the cooldown and the last hit as they were. A plain hurt with minecraft:bypasses_cooldown
 * would start a new 20-tick cooldown, which refuses the next ordinary hit.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DamagingEntityHurtCooldownGameTests {
	private static final float EPS = 1.0E-4F;

	private DamagingEntityHurtCooldownGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void fireballHitStartsNoHurtCooldown(GameTestHelper helper) {
		play(helper, (owner, cow) -> {
			MRFireballEntity fireball = new MRFireballEntity(owner, helper.getLevel());
			helper.assertTrue(hurtTarget(fireball, cow, owner), "setup: the fireball did not hurt the cow");
			float afterFireball = cow.getHealth();
			helper.assertTrue(afterFireball < 100.0F && cow.invulnerableTime == 0 && near(cow.lastHurt, 0.0F),
					"1.16: a Magician's Red fireball hit leaves the target's hurt cooldown as it was (none); health "
							+ afterFireball + ", cooldown " + cow.invulnerableTime + ", last hit " + cow.lastHurt);
			helper.assertTrue(cow.hurt(helper.getLevel().damageSources().generic(), 1.0F) && near(cow.getHealth(), afterFireball - 1.0F),
					"1.16: an ordinary hit right after a fireball hit lands in full; health " + afterFireball + " -> " + cow.getHealth());
		});
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void fireballHitInsideACooldownKeepsIt(GameTestHelper helper) {
		play(helper, (owner, cow) -> {
			prime(helper, cow, 10.0F);
			cow.invulnerableTime = 15;
			MRFireballEntity fireball = new MRFireballEntity(owner, helper.getLevel());
			helper.assertTrue(hurtTarget(fireball, cow, owner), "1.16: a fireball hit gets through the hurt cooldown");
			helper.assertTrue(cow.getHealth() < 90.0F && cow.invulnerableTime == 15 && near(cow.lastHurt, 10.0F),
					"1.16: a fireball hit inside the hurt cooldown keeps the cooldown (15) and the last hit (10); health "
							+ cow.getHealth() + ", cooldown " + cow.invulnerableTime + ", last hit " + cow.lastHurt);
		});
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void smallCrossfireBlastKeepsAnExistingCooldown(GameTestHelper helper) {
		play(helper, (owner, cow) -> {
			prime(helper, cow, 10.0F);
			cow.invulnerableTime = 15;
			blast(helper, owner, cow, true);
			helper.assertTrue(cow.getHealth() < 90.0F && cow.invulnerableTime == 15 && near(cow.lastHurt, 10.0F),
					"1.16: the small Crossfire Hurricane blast on a target in its hurt cooldown keeps the cooldown (15) and the"
							+ " last hit (10); health " + cow.getHealth() + ", cooldown " + cow.invulnerableTime + ", last hit " + cow.lastHurt);
		});
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void smallCrossfireBlastOnAFreshTargetStartsTheCooldown(GameTestHelper helper) {
		play(helper, (owner, cow) -> {
			blast(helper, owner, cow, true);
			helper.assertTrue(cow.getHealth() < 100.0F && cow.invulnerableTime == 20 && cow.lastHurt > 0.0F,
					"1.16: outside a cooldown the small blast was a plain hit that starts one; health " + cow.getHealth()
							+ ", cooldown " + cow.invulnerableTime + ", last hit " + cow.lastHurt);
		});
	}

	private interface Play {
		void run(ServerPlayer owner, Cow cow);
	}

	private static void play(GameTestHelper helper, Play play) {
		ServerPlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DamagingHurtCd"));
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 0)));
		owner.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		owner.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(owner), "Could not add the owner");
		Cow cow = helper.spawn(EntityType.COW, new Vec3(1.5D, 2.0D, 4.5D));
		cow.setNoAi(true);
		cow.setNoGravity(true);
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
		cow.setHealth(100.0F);
		try {
			play.run(owner, cow);
			helper.succeed();
		}
		finally {
			for (Entity entity : helper.getLevel().getEntitiesOfClass(DamagingEntity.class, cow.getBoundingBox().inflate(8.0D))) {
				entity.discard();
			}
			cow.discard();
			owner.discard();
		}
	}

	private static void prime(GameTestHelper helper, Cow cow, float amount) {
		helper.assertTrue(cow.hurt(helper.getLevel().damageSources().generic(), amount) && near(cow.getHealth(), 100.0F - amount)
				&& near(cow.lastHurt, amount) && cow.invulnerableTime == 20, "setup: the first hit did not start the hurt cooldown");
	}

	private static boolean hurtTarget(DamagingEntity projectile, Entity target, LivingEntity owner) {
		try {
			Method method = DamagingEntity.class.getDeclaredMethod("hurtTarget", Entity.class, LivingEntity.class);
			method.setAccessible(true);
			return (boolean) method.invoke(projectile, target, owner);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void blast(GameTestHelper helper, ServerPlayer owner, Cow cow, boolean special) {
		MRCrossfireHurricaneEntity crossfire = new MRCrossfireHurricaneEntity(special, owner, helper.getLevel());
		Vec3 eyes = cow.getEyePosition();
		crossfire.setPos(eyes.x + 0.3D, eyes.y, eyes.z);
		try {
			Method explode = MRCrossfireHurricaneEntity.class.getDeclaredMethod("explode");
			explode.setAccessible(true);
			explode.invoke(crossfire);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean near(float actual, float expected) {
		return Math.abs(actual - expected) < EPS;
	}
}
