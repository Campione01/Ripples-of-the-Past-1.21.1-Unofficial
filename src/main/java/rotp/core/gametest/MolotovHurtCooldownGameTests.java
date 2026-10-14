package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.MolotovEntity;
import rotp.core.init.ModItems;

/**
 * 1.16 MolotovEntity.onHitEntity dealt its direct hit as a plain IN_FIRE hurt of 2: inside the target's hurt cooldown
 * the hit was refused, or cut to its excess over the last hit without knockback, red flash or hurt sound, and the
 * cooldown was not restarted. A damage type in minecraft:bypasses_cooldown would land in full and restart the cooldown,
 * and re-hurting with one for the excess counts as a fresh hit. The target is set on fire for 10 seconds either way.
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
	public static void molotovExcessHitInsideTheHurtCooldownGivesNoKnockbackFlashOrSound(GameTestHelper helper) {
		play(helper, (molotov, cow) -> {
			prime(helper, cow, 1.0F);
			cow.invulnerableTime = 15;
			calm(cow);
			Feedback feedback = Feedback.listen(cow);
			try {
				hit(molotov, cow);
			}
			finally {
				feedback.close();
			}
			helper.assertTrue(near(cow.getHealth(), 98.0F),
					"setup: the Molotov hit inside the cooldown did not deal its excess (2 - 1); health " + cow.getHealth());
			List<String> wrong = new ArrayList<>();
			if (!feedback.knockbacks.isEmpty() || cow.hurtMarked || cow.getDeltaMovement().lengthSqr() != 0.0D) {
				wrong.add("knocked back (knockbacks " + feedback.knockbacks + ", velocity sent " + cow.hurtMarked + ", velocity "
						+ cow.getDeltaMovement() + ")");
			}
			if (cow.hurtTime != 0) {
				wrong.add("flashed red (hurt time " + cow.hurtTime + ")");
			}
			if (feedback.hurtSounds != 0) {
				wrong.add("played the hurt sound " + feedback.hurtSounds + " time(s)");
			}
			helper.assertTrue(wrong.isEmpty(),
					"1.16: a hit inside the hurt cooldown gives no knockback, red flash or hurt sound, but the target " + wrong);
		});
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void molotovHitOutsideACooldownLandsAndStartsOne(GameTestHelper helper) {
		play(helper, (molotov, cow) -> {
			Feedback feedback = Feedback.listen(cow);
			try {
				hit(molotov, cow);
			}
			finally {
				feedback.close();
			}
			helper.assertTrue(near(cow.getHealth(), 98.0F) && cow.invulnerableTime == 20 && near(cow.lastHurt, 2.0F)
					&& cow.getRemainingFireTicks() >= 200,
					"1.16: outside a cooldown the Molotov hit deals 2, starts the cooldown and sets the target on fire; health "
							+ cow.getHealth() + ", cooldown " + cow.invulnerableTime + ", last hit " + cow.lastHurt + ", fire ticks "
							+ cow.getRemainingFireTicks());
			helper.assertTrue(feedback.knockbacks.size() == 1 && cow.hurtMarked && cow.hurtTime == 10 && feedback.hurtSounds == 1,
					"1.16: outside a cooldown the Molotov hit knocks the target back, flashes it red and plays its hurt sound;"
							+ " knockbacks " + feedback.knockbacks + ", velocity sent " + cow.hurtMarked + ", hurt time " + cow.hurtTime
							+ ", hurt sounds " + feedback.hurtSounds);
			Entity killer = cow.getLastDamageSource() != null ? cow.getLastDamageSource().getEntity() : null;
			helper.assertTrue(killer == molotov.getOwner() && cow.getLastDamageSource().is(DamageTypeTags.IS_FIRE),
					"the hit is a fire hit credited to the thrower; source " + cow.getLastDamageSource() + ", attacker " + killer);
		});
	}

	private interface Play {
		void run(MolotovEntity molotov, Cow cow);
	}

	/** The cow's knockback events and hurt sounds while a hit is dealt. */
	private static final class Feedback {
		final List<Float> knockbacks = new ArrayList<>();
		int hurtSounds;
		private final List<Consumer<? extends Event>> listeners = new ArrayList<>();

		static Feedback listen(Cow cow) {
			Feedback feedback = new Feedback();
			Consumer<LivingKnockBackEvent> knockback = event -> {
				if (event.getEntity() == cow) {
					feedback.knockbacks.add(event.getStrength());
				}
			};
			Consumer<PlayLevelSoundEvent.AtPosition> sound = event -> {
				if (event.getLevel() == cow.level() && event.getSound() != null
						&& event.getSound().value() == SoundEvents.COW_HURT && event.getPosition().distanceToSqr(cow.position()) < 1.0E-4D) {
					feedback.hurtSounds++;
				}
			};
			feedback.listeners.add(knockback);
			feedback.listeners.add(sound);
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingKnockBackEvent.class, knockback);
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, PlayLevelSoundEvent.AtPosition.class, sound);
			return feedback;
		}

		void close() {
			for (Object listener : listeners) {
				NeoForge.EVENT_BUS.unregister(listener);
			}
		}
	}

	// the priming hit's own knockback, flash and velocity are gone, as some ticks into the cooldown
	private static void calm(Cow cow) {
		cow.setDeltaMovement(Vec3.ZERO);
		cow.hurtMarked = false;
		cow.hurtTime = 0;
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
