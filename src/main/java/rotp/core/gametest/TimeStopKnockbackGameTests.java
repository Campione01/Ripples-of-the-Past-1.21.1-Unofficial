package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 left a stopped entity's motion alone and stacked the knockback it took on top of it
 * (GameplayEventHandler.stackKnockbackInstead), so a mob beaten up in stopped time flew once time resumed.
 * Also pins the knockback impact's floor landing and its stuck-entity trigger (1.16 KnockbackCollisionImpact), its
 * collision hook after vanilla collision (1.16 EntityMixin), its save, what it does to what it hits, and
 * knockback3d on a stopped target (1.16 DamageUtil).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopKnockbackGameTests {
	private static int nextStopId = -7_320_000;

	private TimeStopKnockbackGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void knockbackTakenInStoppedTimeFliesWhenTimeResumes(GameTestHelper helper) {
		Cow target = spawnOnFloor(helper, 2);
		Mob attacker = EntityType.ZOMBIE.create(helper.getLevel());
		TimeStopState state = state(helper);
		int stop = nextStopId--;
		try {
			stopTime(helper, state, stop, target);
			Vec3 stoppedAt = target.position();
			// two hits a few stopped ticks apart, both pushing towards -x
			target.knockback(0.15, 1, 0);
			frozenTick(helper, state, target);
			frozenTick(helper, state, target);
			helper.assertTrue(Math.abs(target.getDeltaMovement().x + 0.15) < 1E-6,
					"A stopped mob lost the knockback it took on its next stopped tick: " + target.getDeltaMovement());
			target.knockback(0.15, 1, 0);
			frozenTick(helper, state, target);
			helper.assertTrue(Math.abs(target.getDeltaMovement().x + 0.3) < 1E-6,
					"Knockback in stopped time did not add up: " + target.getDeltaMovement());
			helper.assertTrue(target.position().distanceToSqr(stoppedAt) == 0, "The stopped mob moved");

			// what a heavy punch arms on its target
			KnockbackCollisionImpact impact = KnockbackCollisionImpact.getHandler(target);
			impact.onPunchSetKnockbackImpact(target.getDeltaMovement(), attacker);
			frozenTick(helper, state, target);
			helper.assertTrue(impact.isActive(), "The impact armed in stopped time did not wait for time to resume");

			state.removeInstance(stop);
			state.reconcileFrozenEntity(target);
			helper.assertTrue(Math.abs(target.getDeltaMovement().x + 0.3) < 1E-6,
					"Time resumed without the knockback taken in stopped time: " + target.getDeltaMovement());
			dataTick(target);
			helper.assertTrue(impact.isActive() && impact.getKnockbackImpactStrength() > 0,
					"The impact armed in stopped time ended on the first resumed tick");

			double startX = target.getX();
			for (int tick = 0; tick < 20; tick++) {
				target.tick();
			}
			helper.assertTrue(startX - target.getX() > 0.8,
					"The mob did not fly after time resumed: moved " + (startX - target.getX()));
		}
		finally {
			state.removeInstance(stop);
			target.discard();
			if (attacker != null) attacker.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void uppercutInStoppedTimeKeepsItsAngle(GameTestHelper helper) {
		Cow target = spawnOnFloor(helper, 2);
		Mob attacker = EntityType.ZOMBIE.create(helper.getLevel());
		TimeStopState state = state(helper);
		int stop = nextStopId--;
		try {
			helper.assertTrue(attacker != null, "Could not create the attacker");
			attacker.moveTo(target.getX() + 1, target.getY(), target.getZ(), 0, 0);
			stopTime(helper, state, stop, target);
			DamageSource uppercut = helper.getLevel().damageSources().mobAttack(attacker);
			((DamageSourceModified) uppercut).jojo_ripples$knockbackXRot(-60F);
			helper.assertTrue(target.hurt(uppercut, 1.0F), "The stopped mob was not hit");
			frozenTick(helper, state, target);

			// vanilla hurt knocks back by 0.4: 1.16 split it into 0.4 * cos(60) along the ground
			// (stacked, y up to 0.4) and 0.4 * sin(60) added upwards
			float along = 0.4F * Mth.cos(-60 * Mth.DEG_TO_RAD);
			float up = -0.4F * Mth.sin(-60 * Mth.DEG_TO_RAD);
			Vec3 motion = target.getDeltaMovement();
			helper.assertTrue(Math.abs(motion.x + along) < 1E-4 && Math.abs(motion.z) < 1E-4,
					"The uppercut in stopped time lost its angle along the ground: " + motion);
			helper.assertTrue(Math.abs(motion.y - (Math.min(0.4, along) + up)) < 1E-4,
					"The uppercut in stopped time lost its upward part: " + motion);
			state.removeInstance(stop);
			state.reconcileFrozenEntity(target);
			helper.assertTrue(target.getDeltaMovement().distanceToSqr(motion) < 1E-10,
					"Time resumed without the uppercut: " + target.getDeltaMovement());
		}
		finally {
			state.removeInstance(stop);
			target.discard();
			if (attacker != null) attacker.discard();
		}
		helper.succeed();
	}

	/** 1.16: an impact that lands on the floor just ends, with no wall damage (and no explosion). */
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void knockbackImpactLandingOnTheFloorDealsNoWallDamage(GameTestHelper helper) {
		Cow target = spawnOnFloor(helper, 2);
		Mob attacker = EntityType.ZOMBIE.create(helper.getLevel());
		try {
			target.setPos(target.getX(), target.getY() + 0.5, target.getZ());
			Vec3 landing = new Vec3(0.3, -0.6, 0);
			target.setDeltaMovement(landing);
			KnockbackCollisionImpact impact = KnockbackCollisionImpact.getHandler(target);
			impact.onPunchSetKnockbackImpact(landing, attacker);
			float health = target.getHealth();
			// what KnockbackEntityCollision passes after Entity.collide stopped the fall 0.5 down, on the floor
			impact.collideBreakBlocks(landing, new Vec3(0.3, -0.5, 0), helper.getLevel());
			helper.assertTrue(!impact.isActive(), "Landing on the floor did not end the impact");
			helper.assertTrue(target.getHealth() == health,
					"Landing on the floor dealt wall damage: " + health + " -> " + target.getHealth());
		}
		finally {
			target.discard();
			if (attacker != null) attacker.discard();
		}
		helper.succeed();
	}

	/** 1.16 KnockbackCollisionImpact.tick: a flying entity that did not move since its last tick checks what it presses into. */
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void stuckKnockedBackMobHitsWhatItPressesInto(GameTestHelper helper) {
		Cow flying = spawnOnFloor(helper, 1);
		Cow pressed = spawnOnFloor(helper, 1);
		Mob attacker = EntityType.ZOMBIE.create(helper.getLevel());
		try {
			pressed.setPos(flying.getX() + 1.3, flying.getY(), flying.getZ());
			Vec3 motion = new Vec3(0.4, 0, 0);
			flying.setDeltaMovement(motion);
			KnockbackCollisionImpact impact = KnockbackCollisionImpact.getHandler(flying);
			impact.onPunchSetKnockbackImpact(motion, attacker);
			float health = pressed.getHealth();

			impact.tick();
			flying.setPos(flying.getX() + 0.05, flying.getY(), flying.getZ());
			impact.tick();
			helper.assertTrue(pressed.getHealth() == health, "A moving entity was treated as stuck");
			impact.tick();
			helper.assertTrue(pressed.getHealth() < health,
					"A stuck knocked-back mob did not hit the mob it pressed into");
		}
		finally {
			flying.discard();
			pressed.discard();
			if (attacker != null) attacker.discard();
		}
		helper.succeed();
	}

	/**
	 * 1.16 DamageUtil.knockback3d (Scarlet and Turquoise Blue Overdrive, Divine Sandstorm): a knockback stacked on
	 * a stopped target still got the 3D push on top of the stack.
	 */
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void knockback3dOnAStoppedTargetStillAddsItsPush(GameTestHelper helper) {
		Cow flowing = spawnOnFloor(helper, 1);
		Cow target = spawnOnFloor(helper, 2);
		TimeStopState state = state(helper);
		int stop = nextStopId--;
		try {
			// 0.5 along a direction 30 degrees up, towards +z
			double along = 0.5 * Math.cos(Math.toRadians(30));
			DamageUtil.knockback3d(flowing, 0.5F, -30F, 0F);
			Vec3 push = flowing.getDeltaMovement();
			helper.assertTrue(Math.abs(push.x) < 1E-4 && Math.abs(push.y - 0.25) < 1E-4 && Math.abs(push.z - along) < 1E-4,
					"The 3D push in flowing time drifted: " + push);

			stopTime(helper, state, stop, target);
			DamageUtil.knockback3d(target, 0.5F, -30F, 0F);
			// the stack (z -0.5, y up to 0.4), then the push (z +0.433, y +0.25)
			Vec3 motion = target.getDeltaMovement();
			helper.assertTrue(Math.abs(motion.x) < 1E-4 && Math.abs(motion.y - 0.65) < 1E-4
					&& Math.abs(motion.z - (along - 0.5)) < 1E-4,
					"A stacked knockback3d lost its 3D push: " + motion);
		}
		finally {
			state.removeInstance(stop);
			flowing.discard();
			target.discard();
		}
		helper.succeed();
	}

	/**
	 * 1.16 EntityMixin ran after vanilla collision, and the impact checked blocks only when that collision changed the
	 * move: a flight that skims the floor within the impact's 0.25 margin does not hit it.
	 */
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void knockbackImpactIgnoresBlocksVanillaCollisionMissed(GameTestHelper helper) {
		Cow flying = spawnOnFloor(helper, 1);
		Mob attacker = EntityType.ZOMBIE.create(helper.getLevel());
		try {
			// 0.1 above the floor: the impact's wider box reaches 0.15 short of the next floor block
			Vec3 start = helper.absoluteVec(new Vec3(1.15, 0.1, 2.5));
			flying.moveTo(start.x, start.y, start.z, 0, 0);
			Vec3 motion = new Vec3(0.3, 0, 0);
			KnockbackCollisionImpact impact = KnockbackCollisionImpact.getHandler(flying);
			impact.onPunchSetKnockbackImpact(motion, attacker);
			float health = flying.getHealth();
			flying.move(MoverType.SELF, motion);
			helper.assertTrue(Math.abs(flying.getX() - start.x - 0.3) < 1E-6, "Vanilla collision stopped a free move");
			helper.assertTrue(impact.isActive() && flying.getHealth() == health,
					"The impact hit the floor it only skimmed: health " + health + " -> " + flying.getHealth());
		}
		finally {
			flying.discard();
			if (attacker != null) attacker.discard();
		}
		helper.succeed();
	}

	/**
	 * The collision hook runs from Entity.collide: flying into a wall still hurts and ends the impact. The wall is two
	 * blocks high because 1.16 CollideBlocks.collide never kept the block that first shortened the move, only the
	 * later ones at the same distance, so a lone block was never hit (CollisionHelper keeps that).
	 */
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void knockbackImpactStillHitsAWall(GameTestHelper helper) {
		BlockPos wall = new BlockPos(2, 0, 2);
		Cow flying = spawnOnFloor(helper, 1);
		Mob attacker = EntityType.ZOMBIE.create(helper.getLevel());
		try {
			helper.setBlock(wall, Blocks.STONE.defaultBlockState());
			helper.setBlock(wall.above(), Blocks.STONE.defaultBlockState());
			Vec3 start = helper.absoluteVec(new Vec3(1.0, 0.3, 2.5));
			flying.moveTo(start.x, start.y, start.z, 0, 0);
			Vec3 motion = new Vec3(0.8, 0, 0);
			KnockbackCollisionImpact impact = KnockbackCollisionImpact.getHandler(flying);
			impact.onPunchSetKnockbackImpact(motion, attacker);
			float health = flying.getHealth();
			flying.move(MoverType.SELF, motion);
			helper.assertTrue(flying.getHealth() < health && !impact.isActive(),
					"Flying into a wall did not hurt or did not end the impact: health " + health + " -> " + flying.getHealth());
		}
		finally {
			helper.setBlock(wall.above(), Blocks.AIR.defaultBlockState());
			helper.setBlock(wall, Blocks.AIR.defaultBlockState());
			flying.discard();
			if (attacker != null) attacker.discard();
		}
		helper.succeed();
	}

	/** 1.16 EntityUtilCap saved an armed impact with its entity ("KbImpact"). */
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void armedKnockbackImpactIsSavedWithItsEntity(GameTestHelper helper) {
		String key = JojoMod.resLoc("kb_impact").toString();
		Cow flying = spawnOnFloor(helper, 2);
		Cow idle = spawnOnFloor(helper, 1);
		Cow loaded = EntityType.COW.create(helper.getLevel());
		Mob attacker = EntityType.ZOMBIE.create(helper.getLevel());
		try {
			helper.assertTrue(loaded != null, "Could not create a cow");
			KnockbackCollisionImpact armed = KnockbackCollisionImpact.getHandler(flying)
					.onPunchSetKnockbackImpact(new Vec3(0.6, 0.2, 0), attacker);
			CompoundTag saved = flying.saveWithoutId(new CompoundTag());
			helper.assertTrue(saved.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).contains(key),
					"An armed impact was not saved with its entity");
			loaded.load(saved);
			KnockbackCollisionImpact impact = KnockbackCollisionImpact.getExistingHandler(loaded);
			helper.assertTrue(impact != null && impact.isActive()
					&& Math.abs(impact.getKnockbackImpactStrength() - armed.getKnockbackImpactStrength()) < 1E-9,
					"The impact did not come back armed from NBT");

			KnockbackCollisionImpact.getHandler(idle);
			helper.assertTrue(!idle.saveWithoutId(new CompoundTag()).getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).contains(key),
					"An idle impact was written to NBT");
		}
		finally {
			flying.discard();
			idle.discard();
			if (loaded != null) loaded.discard();
			if (attacker != null) attacker.discard();
		}
		helper.succeed();
	}

	/** 1.16 knocked back what the flying entity hit even when the hurt did not go through. */
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void knockbackImpactPushesWhatItHitsEvenWhenTheHurtFails(GameTestHelper helper) {
		Cow flying = spawnOnFloor(helper, 1);
		Cow hit = spawnOnFloor(helper, 2);
		Mob attacker = EntityType.ZOMBIE.create(helper.getLevel());
		try {
			Vec3 motion = new Vec3(0.4, 0, 0);
			flying.setDeltaMovement(motion);
			HurtRefused impact = new HurtRefused(flying);
			impact.onPunchSetKnockbackImpact(motion, attacker);
			helper.assertTrue(!impact.collideWith(hit, motion), "The test impact's hurt went through");
			helper.assertTrue(hit.getDeltaMovement().x > 0.1,
					"What the impact hit was not knocked back after a failed hurt: " + hit.getDeltaMovement());
		}
		finally {
			flying.discard();
			hit.discard();
			if (attacker != null) attacker.discard();
		}
		helper.succeed();
	}

	/** 1.16 hurt any entity the flying one hit, not only living ones. */
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void knockbackImpactHurtsANonLivingEntityItFliesInto(GameTestHelper helper) {
		Minecart flying = EntityType.MINECART.create(helper.getLevel());
		Minecart hit = EntityType.MINECART.create(helper.getLevel());
		try {
			helper.assertTrue(flying != null && hit != null, "Could not create the minecarts");
			Vec3 start = helper.absoluteVec(new Vec3(1.0, 0.3, 2.5));
			flying.moveTo(start.x, start.y, start.z, 0, 0);
			hit.moveTo(start.x + 1.3, start.y, start.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(flying) && helper.getLevel().addFreshEntity(hit),
					"Could not add the minecarts");
			Vec3 motion = new Vec3(0.4, 0, 0);
			KnockbackCollisionImpact impact = KnockbackCollisionImpact.getHandler(flying);
			impact.onPunchSetKnockbackImpact(motion, null);
			// no block in the way: the entity collisions only
			impact.collideBreakBlocks(motion, motion, helper.getLevel());
			helper.assertTrue(hit.getDamage() > 0, "The minecart flown into took no damage");
		}
		finally {
			if (flying != null) flying.discard();
			if (hit != null) hit.discard();
		}
		helper.succeed();
	}

	/**
	 * 1.16 KnockbackCollisionImpact: flying into a cactus hurts 1 on top of the cactus's own touch. Two cacti high,
	 * so that one of them is kept by the block scan (see knockbackImpactStillHitsAWall).
	 */
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void knockbackImpactIntoACactusHurtsTheOriginalOnePoint(GameTestHelper helper) {
		BlockPos cactus = new BlockPos(2, 0, 2);
		Cow flying = spawnOnFloor(helper, 1);
		Mob attacker = EntityType.ZOMBIE.create(helper.getLevel());
		try {
			helper.setBlock(cactus, Blocks.CACTUS.defaultBlockState());
			helper.setBlock(cactus.above(), Blocks.CACTUS.defaultBlockState());
			Vec3 start = helper.absoluteVec(new Vec3(1.0, 0.3, 2.5));
			flying.moveTo(start.x, start.y, start.z, 0, 0);
			Vec3 motion = new Vec3(0.4, 0, 0);
			KnockbackCollisionImpact impact = KnockbackCollisionImpact.getHandler(flying);
			impact.onPunchSetKnockbackImpact(motion, attacker);
			float health = flying.getHealth();
			// as after vanilla collision stopped the move at the cactus
			impact.collideBreakBlocks(motion, new Vec3(0.2, 0, 0), helper.getLevel());
			float lost = health - flying.getHealth();
			// the cactus's own touch (1), the impact's cactus hit (1) and a little wall damage
			helper.assertTrue(lost > 1.9F && lost < 2.5F, "Flying into a cactus took " + lost + " health, 1.16 took about 2");
		}
		finally {
			helper.setBlock(cactus.above(), Blocks.AIR.defaultBlockState());
			helper.setBlock(cactus, Blocks.AIR.defaultBlockState());
			flying.discard();
			if (attacker != null) attacker.discard();
		}
		helper.succeed();
	}

	// an impact whose hurt never goes through, as against a blocking or immune target
	private static final class HurtRefused extends KnockbackCollisionImpact {
		HurtRefused(Entity entity) {
			super(entity);
		}

		@Override
		protected boolean hurtTarget(Entity target, DamageSource dmgSource, float amount) {
			return false;
		}

		boolean collideWith(LivingEntity target, Vec3 motion) {
			return onCollideWith(target, target, motion);
		}
	}

	// a cow standing on the test floor that cannot walk, so only knockback moves it
	private static Cow spawnOnFloor(GameTestHelper helper, int x) {
		Cow cow = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(cow != null, "Could not create a cow");
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 0, 2)));
		cow.moveTo(pos.x, pos.y, pos.z, 0, 0);
		cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add a cow");
		return cow;
	}

	private static TimeStopState state(GameTestHelper helper) {
		return helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
	}

	private static void stopTime(GameTestHelper helper, TimeStopState state, int id, LivingEntity frozen) {
		helper.assertTrue(state.tryPutInstance(new TimeStopState.Instance(id, 200, 200,
				new ChunkPos(frozen.blockPosition()), 2, -1, "time_stop_knockback_test")),
				"Could not stop time");
		helper.assertTrue(state.shouldFreeze(frozen) && TimeStopState.shouldFreezeOnServer(frozen), "The mob did not freeze");
	}

	// what ServerLevelTimeStopTickMixin does with a stopped entity each tick
	private static void frozenTick(GameTestHelper helper, TimeStopState state, LivingEntity entity) {
		helper.assertTrue(state.interruptTickEarly(entity), "A stopped mob was allowed to tick");
		dataTick(entity);
		state.reconcileFrozenEntity(entity);
	}

	private static void dataTick(LivingEntity entity) {
		entity.getData(ModDataAttachmentTypes.DATA_EVENT_HELPER.get()).onTick();
	}
}
