package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.customobjects.entity_projectile.TommyGunBulletEntity;
import rotp.core.impl.stands.hierophant.HGEmeraldEntity;
import rotp.core.init.ModDamageTypes;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 1.16 LivingEntity.hurt knocked the target away from source.getEntity(), the projectile's owner, wherever the projectile flew.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProjectileKnockbackDirectionGameTests {
	private ProjectileKnockbackDirectionGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void bulletKnocksItsTargetAwayFromTheShooter(GameTestHelper helper) {
		crossingShot(helper, ModDamageTypes.MOD_PROJECTILE, TommyGunBulletEntity::new);
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void standProjectileKnocksItsTargetAwayFromItsOwner(GameTestHelper helper) {
		crossingShot(helper, ModDamageTypes.STAND_PROJECTILE, HGEmeraldEntity::new);
	}

	// The owner stands north of the target; the projectile crosses from the west, at a right angle to the line between
	// them, and takes its one hit tick by hand.
	private static void crossingShot(GameTestHelper helper, ResourceKey<DamageType> type,
			BiFunction<LivingEntity, Level, ModdedProjectileEntity> shot) {
		ServerLevel level = helper.getLevel();
		Player owner = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Pig target = EntityType.PIG.create(level);
		List<Entity> spawned = new ArrayList<>();
		List<Vec3> pushes = new ArrayList<>();
		Consumer<LivingKnockBackEvent> listener = event -> {
			if (event.getEntity() == target && !target.damageContainers.isEmpty()
					&& target.damageContainers.peek().getSource().is(type)) {
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

			ModdedProjectileEntity projectile = shot.apply(owner, level);
			spawned.add(projectile);
			Vec3 centre = target.getBoundingBox().getCenter();
			Vec3 flight = new Vec3(1.0D, 0.0D, 0.0D);
			projectile.setPos(centre.x - 1.0D, centre.y, centre.z);
			projectile.setDeltaMovement(flight.scale(0.8D));
			helper.assertTrue(level.addFreshEntity(projectile), "Could not add the projectile");
			float health = target.getHealth();
			projectile.tickCount = 0;
			projectile.tick();

			helper.assertTrue(projectile.isRemoved() && target.getHealth() < health && pushes.size() == 1,
					"Fixture: the projectile did not land one hit of type " + type.location() + " with one knockback: removed="
							+ projectile.isRemoved() + " health=" + health + "->" + target.getHealth() + " knockbacks=" + pushes.size());
			Vec3 away = new Vec3(target.getX() - owner.getX(), 0.0D, target.getZ() - owner.getZ()).normalize();
			Vec3 push = pushes.get(0);
			Vec3 motion = target.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize();
			helper.assertTrue(push.dot(away) > 0.9D && Math.abs(push.dot(flight)) < 0.1D && motion.dot(away) > 0.9D,
					"1.16: a projectile hit knocks its target away from the projectile's owner, not along the flight line: push="
							+ push + " targetMotion=" + motion + " awayFromOwner=" + away + " flight=" + flight);
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
