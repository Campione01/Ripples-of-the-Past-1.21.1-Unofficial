package rotp.core.gametest;

import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.impl.powers.hamon.entity.HamonZoomPunchEntity;
import rotp.core.impl.stands.starplatinum.SPStarFingerEntity;

/**
 * 1.16.5 OwnerBoundProjectileEntity.hurtTarget: unless shouldHurtThroughInvulTicks() is overridden, the hit is a
 * plain target.hurt, which the target's hurt cooldown refuses or cuts to the excess over its last hit. The Star
 * Finger (4.5 damage, no override) stands for every such projectile. Each projectile is put on the target and
 * ticked once by hand, so its own tick finds and deals the hit.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OwnerBoundProjectileHurtCooldownGameTests {
	private static final float EPS = 1.0E-4F;

	private OwnerBoundProjectileHurtCooldownGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void starFingerOutsideTheCooldownDealsItsFullDamage(GameTestHelper helper) {
		play(helper, (owner, cow) -> {
			float damage = starFingerDamage();
			hit(helper, new SPStarFingerEntity(owner, helper.getLevel()), cow);
			helper.assertTrue(near(cow.getHealth(), 100.0F - damage) && near(cow.lastHurt, damage) && cow.invulnerableTime == 20,
					"a Star Finger hit on a fresh target: health " + cow.getHealth() + " (expected " + (100.0F - damage)
							+ "), last hit " + cow.lastHurt + ", cooldown " + cow.invulnerableTime);
		});
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hurtCooldownRefusesAStarFingerNoStrongerThanTheLastHit(GameTestHelper helper) {
		play(helper, (owner, cow) -> {
			float last = starFingerDamage() + 1.5F;
			prime(helper, cow, last);
			hit(helper, new SPStarFingerEntity(owner, helper.getLevel()), cow);
			helper.assertTrue(near(cow.getHealth(), 100.0F - last) && near(cow.lastHurt, last) && cow.invulnerableTime == 20,
					"1.16: the hurt cooldown refuses a Star Finger hit no stronger than the last hit (" + last + "); health "
							+ (100.0F - last) + " -> " + cow.getHealth() + ", last hit " + cow.lastHurt + ", cooldown " + cow.invulnerableTime);
		});
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hurtCooldownCutsAStrongerStarFingerToTheExcess(GameTestHelper helper) {
		play(helper, (owner, cow) -> {
			float damage = starFingerDamage();
			float last = damage - 1.5F;
			prime(helper, cow, last);
			cow.invulnerableTime = 15;
			hit(helper, new SPStarFingerEntity(owner, helper.getLevel()), cow);
			helper.assertTrue(near(cow.getHealth(), 100.0F - damage) && near(cow.lastHurt, damage) && cow.invulnerableTime == 15,
					"1.16: inside the hurt cooldown a stronger Star Finger hit deals only its excess over the last hit (" + last
							+ ") and leaves the cooldown running; health " + (100.0F - last) + " -> " + cow.getHealth() + " (expected "
							+ (100.0F - damage) + "), last hit " + cow.lastHurt + ", cooldown " + cow.invulnerableTime);
		});
	}

	/**
	 * 1.16 stored the raw amount as the last hit before any reduction in LivingHurtEvent, so a second equal hit was
	 * refused even on a target whose damage is cut (Hamon protection). 1.21 stores the amount after the cut.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void secondStarFingerIsRefusedOnATargetWhoseIncomingDamageIsCut(GameTestHelper helper) {
		play(helper, (owner, cow) -> {
			float damage = starFingerDamage();
			int[] incoming = { 0 };
			Consumer<LivingIncomingDamageEvent> halve = event -> {
				if (event.getEntity() == cow && event.getSource().getDirectEntity() instanceof SPStarFingerEntity) {
					incoming[0]++;
					event.setAmount(event.getAmount() * 0.5F);
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingIncomingDamageEvent.class, halve);
			try {
				hit(helper, new SPStarFingerEntity(owner, helper.getLevel()), cow);
				helper.assertTrue(incoming[0] == 1 && near(cow.getHealth(), 100.0F - damage * 0.5F),
						"setup: the first Star Finger hit was not cut in half; health " + cow.getHealth() + ", hits " + incoming[0]);
				hit(helper, new SPStarFingerEntity(owner, helper.getLevel()), cow);
				helper.assertTrue(incoming[0] == 1 && near(cow.getHealth(), 100.0F - damage * 0.5F) && near(cow.lastHurt, damage),
						"1.16: a second equal Star Finger hit inside the cooldown is refused although the first was cut; hits that"
								+ " reached the target: " + incoming[0] + ", health " + cow.getHealth() + " (expected "
								+ (100.0F - damage * 0.5F) + "), last hit " + cow.lastHurt + " (expected " + damage + ")");
			}
			finally {
				NeoForge.EVENT_BUS.unregister(halve);
			}
		});
	}

	// A source that already respects the cooldown is left to the vanilla rule; cutting it here too would drop the hit.
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void zoomPunchInsideTheCooldownStillDealsItsExcess(GameTestHelper helper) {
		play(helper, (owner, cow) -> {
			float damage = (float) owner.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
			float last = damage * 0.5F;
			prime(helper, cow, last);
			HamonZoomPunchEntity punch = new HamonZoomPunchEntity(owner, helper.getLevel()).setSpeed(0.5F).setDuration(14);
			hit(helper, punch, cow);
			helper.assertTrue(damage > 0.0F && near(cow.getHealth(), 100.0F - damage) && near(cow.lastHurt, damage),
					"a Zoom Punch (" + damage + ") inside the cooldown of a weaker hit (" + last + ") deals its excess; health "
							+ (100.0F - last) + " -> " + cow.getHealth() + ", last hit " + cow.lastHurt);
		});
	}

	private interface Play {
		void run(ServerPlayer owner, Cow cow);
	}

	private static void play(GameTestHelper helper, Play play) {
		ServerPlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "OwnerBoundHurt"));
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 0)));
		owner.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		owner.setYHeadRot(0.0F);
		owner.yBodyRot = 0.0F;
		owner.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(owner), "Could not add the owner");
		Cow cow = helper.spawn(EntityType.COW, new Vec3(1.5D, 2.0D, 3.5D));
		cow.setNoAi(true);
		cow.setNoGravity(true);
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
		cow.setHealth(100.0F);
		try {
			play.run(owner, cow);
			helper.succeed();
		}
		finally {
			for (Entity entity : helper.getLevel().getEntitiesOfClass(OwnerBoundProjectileEntity.class, cow.getBoundingBox().inflate(8.0D),
					projectile -> projectile.getOwner() == owner)) {
				entity.discard();
			}
			cow.discard();
			owner.discard();
		}
	}

	private static float starFingerDamage() {
		return 4.5F * JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get().floatValue();
	}

	private static void prime(GameTestHelper helper, Cow cow, float amount) {
		helper.assertTrue(cow.hurt(helper.getLevel().damageSources().generic(), amount) && near(cow.getHealth(), 100.0F - amount)
				&& near(cow.lastHurt, amount) && cow.invulnerableTime == 20, "setup: the first hit did not start the hurt cooldown");
	}

	// The projectile's own tick traces from its owner to its position, which is put on the target.
	private static void hit(GameTestHelper helper, OwnerBoundProjectileEntity projectile, Cow cow) {
		Vec3 center = cow.getBoundingBox().getCenter();
		projectile.setPos(center.x, center.y, center.z);
		projectile.setDeltaMovement(Vec3.ZERO);
		helper.assertTrue(helper.getLevel().addFreshEntity(projectile), "Could not add the projectile");
		projectile.tickCount = 0;
		projectile.tick();
	}

	private static boolean near(float actual, float expected) {
		return Math.abs(actual - expected) < EPS;
	}
}
