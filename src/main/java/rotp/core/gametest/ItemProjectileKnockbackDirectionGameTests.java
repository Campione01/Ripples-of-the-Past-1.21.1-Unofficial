package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.BladeHatEntity;
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.init.ModItems;
import rotp.core.mechanics.standarrow.StandArrowEntity;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 1.16 ItemProjectileEntity and StandArrowEntity hit with DamageSource.arrow(projectile, thrower), and hurt() knocked
// the target away from source.getEntity(), the thrower, wherever the projectile flew.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemProjectileKnockbackDirectionGameTests {
	private static final Vec3 EAST = new Vec3(1.0D, 0.0D, 0.0D);
	private static final Vec3 SOUTH = new Vec3(0.0D, 0.0D, 1.0D);

	private ItemProjectileKnockbackDirectionGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void knifeKnocksItsTargetAwayFromTheThrower(GameTestHelper helper) {
		hit(helper, "Knife", EAST, 0, (owner, level) -> new KnifeEntity(level, owner, new ItemStack(ModItems.KNIFE.get())));
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void standArrowKnocksItsTargetAwayFromTheThrower(GameTestHelper helper) {
		hit(helper, "Stand Arrow", EAST, 0,
				(owner, level) -> new StandArrowEntity(owner, level, new ItemStack(ModItems.STAND_ARROW.get()), null));
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void bladeHatKnocksItsTargetAwayFromTheThrower(GameTestHelper helper) {
		hit(helper, "Blade Hat", EAST, 0, (owner, level) -> new BladeHatEntity(level, owner, new ItemStack(ModItems.BLADE_HAT.get())));
	}

	// thrown past the target and 31 ticks old, the hat turns round by itself and hits on its way back to the thrower
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void returningBladeHatKnocksItsTargetAwayFromTheThrower(GameTestHelper helper) {
		hit(helper, "returning Blade Hat", SOUTH, 31,
				(owner, level) -> new BladeHatEntity(level, owner, new ItemStack(ModItems.BLADE_HAT.get())));
	}

	/*
	 * The thrower stands north of the target. A projectile flying east starts west of the target and crosses the line
	 * between them at a right angle; one flying south starts south of the target, so it only hits after turning back.
	 * The projectile takes its ticks by hand.
	 */
	private static void hit(GameTestHelper helper, String name, Vec3 flight, int age,
			BiFunction<LivingEntity, Level, AbstractArrow> thrown) {
		ServerLevel level = helper.getLevel();
		Player owner = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Pig target = EntityType.PIG.create(level);
		List<Entity> spawned = new ArrayList<>();
		List<Vec3> pushes = new ArrayList<>();
		Consumer<LivingKnockBackEvent> listener = event -> {
			if (event.getEntity() == target && !target.damageContainers.isEmpty()
					&& target.damageContainers.peek().getSource().is(DamageTypes.ARROW)) {
				pushes.add(new Vec3(-event.getRatioX(), 0.0D, -event.getRatioZ()).normalize());
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, listener);
		try {
			helper.assertTrue(target != null, "Could not create the target");
			Vec3 ownerPos = helper.absoluteVec(new Vec3(0.5D, 3.0D, 0.5D));
			owner.setNoGravity(true);
			owner.moveTo(ownerPos.x, ownerPos.y, ownerPos.z, 0.0F, 0.0F);
			target.setNoAi(true);
			target.setNoGravity(true);
			target.moveTo(ownerPos.x, ownerPos.y, ownerPos.z + 4.0D, 0.0F, 0.0F);
			spawned.add(owner);
			spawned.add(target);
			helper.assertTrue(level.addFreshEntity(owner) && level.addFreshEntity(target), "Could not add the knockback actors");

			AbstractArrow projectile = thrown.apply(owner, level);
			spawned.add(projectile);
			Vec3 centre = target.getBoundingBox().getCenter();
			boolean turnsBack = flight == SOUTH;
			Vec3 start = turnsBack ? centre.add(SOUTH) : centre.subtract(flight);
			projectile.setPos(start.x, start.y, start.z);
			projectile.setNoGravity(true);
			projectile.setDeltaMovement(flight.scale(0.8D));
			helper.assertTrue(level.addFreshEntity(projectile), "Could not add the projectile");
			float health = target.getHealth();
			projectile.tickCount = age;
			Vec3 hitFlight = flight;
			for (int tick = 0; tick < 6 && pushes.isEmpty(); tick++) {
				hitFlight = projectile.getDeltaMovement().normalize();
				projectile.tick();
			}

			helper.assertTrue(target.getHealth() < health && pushes.size() == 1,
					"Fixture: the " + name + " did not land one arrow hit with one knockback: health=" + health + "->"
							+ target.getHealth() + " knockbacks=" + pushes.size() + " projectile=" + projectile.position());
			Vec3 away = new Vec3(target.getX() - owner.getX(), 0.0D, target.getZ() - owner.getZ()).normalize();
			helper.assertTrue(hitFlight.dot(turnsBack ? away.reverse() : flight) > 0.99D,
					"Fixture: the " + name + " did not hit on the planned flight line: flight=" + hitFlight);
			Vec3 push = pushes.get(0);
			Vec3 motion = target.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize();
			helper.assertTrue(push.dot(away) > 0.9D && motion.dot(away) > 0.9D,
					"1.16: a " + name + " hit knocks its target away from the thrower, not along the flight line: push="
							+ push + " targetMotion=" + motion + " awayFromThrower=" + away + " flight=" + hitFlight);
			helper.succeed();
		}
		finally {
			NeoForge.EVENT_BUS.unregister(listener);
			for (Entity entity : spawned) {
				if (!entity.isRemoved()) {
					entity.discard();
				}
			}
		}
	}
}
