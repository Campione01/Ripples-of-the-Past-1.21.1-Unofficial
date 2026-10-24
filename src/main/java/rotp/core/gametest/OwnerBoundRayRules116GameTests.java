package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.DamagingEntity;
import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.customobjects.entity_projectile.SpaceRipperStingyEyesEntity;
import rotp.core.impl.powers.pillarman.PillarmanVeinEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 OwnerBoundProjectileEntity.rayTrace (JojoModUtil.rayTraceMultipleEntities), which Space Ripper Stingy Eyes and the
 * Pillar Man vein, rib and horn took, and which the port's eyes and body parts cast with their own entity search:
 * an entity whose grown box holds the ray's start was hit there, at distance 0, ahead of everything on the ray
 * (clip.orElse(start): AABB.clip finds no face it enters from inside a box, so that is the start); an entity in the
 * projectile's own root vehicle was skipped unless the ray had no length, which never applies to a projectile that rides
 * nothing; a mounted owner's horse was left out by the projectile's canHitEntity (ProjectileEntity: not before the
 * projectile has left its owner and the owner's vehicle, leftOwner) and hit after that like any mob on the ray.
 * Own batches.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OwnerBoundRayRules116GameTests {
	private static final String BATCH = "owner_bound_ray_rules_";

	private OwnerBoundRayRules116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "eyes_point_blank")
	public static void stingyEyesHitAMobTheirRayStartsInFirstAsIn116(GameTestHelper helper) {
		pointBlank(helper, "Space Ripper Stingy Eyes", owner -> new SpaceRipperStingyEyesEntity(helper.getLevel(), owner, true));
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "vein_point_blank")
	public static void pillarManVeinHitsAMobItsRayStartsInFirstAsIn116(GameTestHelper helper) {
		pointBlank(helper, "the Pillar Man vein",
				owner -> new PillarmanVeinEntity(helper.getLevel(), owner, 0.0F, 0.0F, 0.0D, 0.0D, 0.0D));
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "eyes_horse")
	public static void stingyEyesSpareTheMountedOwnersHorseUntilTheyLeaveItAsIn116(GameTestHelper helper) {
		ownHorse(helper, "Space Ripper Stingy Eyes", owner -> new SpaceRipperStingyEyesEntity(helper.getLevel(), owner, true));
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "vein_horse")
	public static void pillarManVeinSparesTheMountedOwnersHorseUntilItLeavesItAsIn116(GameTestHelper helper) {
		ownHorse(helper, "the Pillar Man vein",
				owner -> new PillarmanVeinEntity(helper.getLevel(), owner, 0.0F, 0.0F, 0.0D, 0.0D, 0.0D));
	}

	/*
	 * A cow whose box holds the ray's start (the owner's eyes) and a pig two blocks further on the ray: the cow is hit at
	 * the start, first.
	 */
	private static void pointBlank(GameTestHelper helper, String name, Function<Player, DamagingEntity> make) {
		List<Entity> added = new ArrayList<>();
		try {
			Player owner = owner(helper, added);
			DamagingEntity projectile = make.apply(owner);
			Vec3 start = origin(projectile);
			Vec3 look = owner.getLookAngle();
			Mob cow = mob(helper, added, EntityType.COW, start.add(0.0D, -0.7D, 0.0D));
			Mob pig = mob(helper, added, EntityType.PIG, start.add(look.scale(2.0D)).add(0.0D, -0.45D, 0.0D));
			projectile.setPos(start.add(look.scale(4.0D)));
			projectile.setDeltaMovement(Vec3.ZERO);
			Vec3 end = projectile.position();
			AABB cowBox = cow.getBoundingBox().inflate(cow.getPickRadius() + projectile.getBbWidth() / 2.0D);
			helper.assertTrue(cowBox.contains(start) && cowBox.clip(start, end).isEmpty(), "Fixture: the cow's grown box does"
					+ " not hold the ray's start, or the ray clips it from inside: " + cowBox.clip(start, end));

			HitResult[] hits = rayTrace(helper, projectile);
			helper.assertTrue(hits.length >= 2 && isEntity(hits[0], cow) && hits[0].getLocation().distanceTo(start) < 1.0E-6D
					&& isEntity(hits[1], pig), "1.16's ray of " + name + " hit the cow its start lies in, there and first (distance"
							+ " 0), then the pig on the ray; the port's hits: " + describe(hits, start));
		}
		finally {
			added.forEach(Entity::discard);
		}
		helper.succeed();
	}

	/*
	 * The owner rides a horse, and the ray runs from its eyes down through the horse: while the projectile has not left
	 * its owner the horse is not hit; once it has, the horse is the first hit, as a mob that is not the owner's.
	 */
	private static void ownHorse(GameTestHelper helper, String name, Function<Player, DamagingEntity> make) {
		List<Entity> added = new ArrayList<>();
		try {
			Player owner = owner(helper, added);
			Vec3 feet = owner.position();
			Mob horse = mob(helper, added, EntityType.HORSE, feet);
			helper.assertTrue(owner.startRiding(horse, true) && owner.getVehicle() == horse, "Fixture: the owner does not ride"
					+ " the horse");
			horse.positionRider(owner);
			DamagingEntity projectile = make.apply(owner);
			Vec3 start = origin(projectile);
			AABB horseBox = horse.getBoundingBox();
			Vec3 through = horseBox.getCenter().subtract(start).normalize();
			projectile.setPos(horseBox.getCenter().add(through.scale(3.0D)));
			projectile.setDeltaMovement(Vec3.ZERO);
			Vec3 end = projectile.position();
			AABB grown = horseBox.inflate(horse.getPickRadius() + projectile.getBbWidth() / 2.0D);
			helper.assertTrue(grown.contains(start) || grown.clip(start, end).isPresent(), "Fixture: the ray does not run"
					+ " through the horse");

			HitResult[] before = rayTrace(helper, projectile);
			helper.assertTrue(Arrays.stream(before).noneMatch(hit -> isEntity(hit, horse) || isEntity(hit, owner)),
					"In 1.16 " + name + " did not hit its mounted owner's horse before it had left its owner"
							+ " (ProjectileEntity.canHitEntity); the port's hits: " + describe(before, start));
			setLeftOwner(helper, projectile);
			HitResult[] after = rayTrace(helper, projectile);
			helper.assertTrue(after.length >= 1 && isEntity(after[0], horse), "In 1.16 " + name + " hit its mounted owner's"
					+ " horse on the ray once it had left its owner (the root vehicle it skipped was the projectile's own); the"
					+ " port's hits: " + describe(after, start));
		}
		finally {
			added.forEach(entity -> {
				entity.ejectPassengers();
				entity.discard();
			});
		}
		helper.succeed();
	}

	// a Survival owner floating at a block's bottom centre, looking south
	private static Player owner(GameTestHelper helper, List<Entity> added) {
		Player owner = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		owner.setNoGravity(true);
		Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		owner.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
		owner.setYHeadRot(0.0F);
		owner.setYBodyRot(0.0F);
		owner.yBodyRotO = 0.0F;
		owner.yHeadRotO = 0.0F;
		helper.assertTrue(helper.getLevel().addFreshEntity(owner), "Could not add the owner");
		added.add(owner);
		return owner;
	}

	private static Mob mob(GameTestHelper helper, List<Entity> added, EntityType<? extends Mob> type, Vec3 feet) {
		Mob mob = type.create(helper.getLevel());
		helper.assertTrue(mob != null, "Could not create " + type);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Could not add " + mob);
		added.add(mob);
		return mob;
	}

	// where the projectile's ray starts (1.16 getOriginPoint)
	private static Vec3 origin(DamagingEntity projectile) {
		return projectile instanceof OwnerBoundProjectileEntity bound ? bound.getOriginPoint(1.0F)
				: ((SpaceRipperStingyEyesEntity) projectile).getOriginPoint(1.0F);
	}

	private static boolean isEntity(HitResult hit, Entity entity) {
		return hit instanceof EntityHitResult entityHit && entityHit.getEntity() == entity;
	}

	private static String describe(HitResult[] hits, Vec3 start) {
		List<String> described = new ArrayList<>();
		for (HitResult hit : hits) {
			String what = hit instanceof EntityHitResult entityHit ? entityHit.getEntity().getType().toShortString()
					: hit.getType().toString();
			described.add(what + " at " + String.format("%.3f", hit.getLocation().distanceTo(start)));
		}
		return described.toString();
	}

	private static HitResult[] rayTrace(GameTestHelper helper, DamagingEntity projectile) {
		try {
			Method method = DamagingEntity.class.getDeclaredMethod("rayTrace");
			method.setAccessible(true);
			return (HitResult[]) method.invoke(projectile);
		}
		catch (NoSuchMethodException | IllegalAccessException e) {
			helper.fail("Fixture: DamagingEntity has no rayTrace");
			throw new IllegalStateException(e);
		}
		catch (InvocationTargetException e) {
			throw new IllegalStateException("Fixture: rayTrace failed", e.getCause());
		}
	}

	// as Projectile.tick sets it once no box of the owner's vehicle and its riders is near the projectile
	private static void setLeftOwner(GameTestHelper helper, DamagingEntity projectile) {
		try {
			Field field = Projectile.class.getDeclaredField("leftOwner");
			field.setAccessible(true);
			field.setBoolean(projectile, true);
		}
		catch (ReflectiveOperationException e) {
			helper.fail("Fixture: Projectile.leftOwner is not accessible: " + e);
		}
	}
}
