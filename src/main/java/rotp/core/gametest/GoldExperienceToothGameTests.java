package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.ObjectEntity;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands.goldexperience.GETransformationEntity;
import rotp.core.impl.stands.goldexperience.GoldExperienceHeavyPunchAbility.ToothKnockingHeavyPunch;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformState;
import rotp.core.impl.stands.goldexperience.GoldExperienceToothLifeformAbility;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GoldExperienceHeavyPunch.ToothKnockingHeavyPunch: every hurting heavy punch on a species from
 * GoldExperienceToothLifeform.getToothObject knocks out a tooth (no chance, no cooldown), other species never.
 * 1.16 GoldExperienceToothLifeform left its cooldown commented out: the tooth lifeform costs no Create Lifeform cooldown.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoldExperienceToothGameTests {
	private GoldExperienceToothGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void geHeavyPunchKnocksToothOnEveryHurtingHitOfToothedTarget(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player user = FakePlayerFactory.get(level, new GameProfile(
				UUID.fromString("7c1e0b48-0000-4000-8000-00000000a148"), "GoldExperienceToothPunch"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
		StandPower power = null;
		Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		AABB area = new AABB(userPos, userPos).inflate(4.0D);
		List<Mob> spawned = new ArrayList<>();
		try {
			helper.assertTrue(standType != null, "Missing Gold Experience Stand type");
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(level.addFreshEntity(user), "Could not add Gold Experience tooth-punch player");
			power = grantAndSummon(helper, user, standType);
			StandEntity stand = power.getSummonedStandEntity();
			Vec3 targetPos = userPos.add(0.0D, 0.0D, 1.0D);

			// every hurting hit on a fresh toothed target knocks out its tooth
			for (int i = 0; i < 6; i++) {
				Mob cow = spawnTarget(helper, spawned, EntityType.COW, targetPos);
				ToothKnockingHeavyPunch punch = heavyPunch(helper, power, user, stand, userPos, cow, "fresh cow " + (i + 1));
				helper.assertTrue(countTeeth(level, area, cow) == 1,
						"Hurting heavy punch " + (i + 1) + " on a cow knocked out " + countTeeth(level, area, cow) + " teeth, expected 1");
				helper.assertTrue(punch.canAcceptToothLifeformFollowup(),
						"Tooth lifeform follow-up was not offered after the cow's tooth was knocked out");
				cow.discard();
			}

			// no per-target cooldown: the same target loses a tooth on every hurting hit
			Mob toughCow = spawnTarget(helper, spawned, EntityType.COW, targetPos);
			toughCow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
			toughCow.setHealth(500.0F);
			for (int hit = 1; hit <= 3; hit++) {
				toughCow.invulnerableTime = 0;
				toughCow.tickCount += 40;
				heavyPunch(helper, power, user, stand, userPos, toughCow, "same cow hit " + hit);
				helper.assertTrue(countTeeth(level, area, toughCow) == hit,
						"Hurting heavy punch " + hit + " on the same cow left " + countTeeth(level, area, toughCow) + " teeth, expected " + hit);
			}
			toughCow.discard();

			// species outside the 1.16 list never lose a tooth
			for (int i = 0; i < 3; i++) {
				Mob creeper = spawnTarget(helper, spawned, EntityType.CREEPER, targetPos);
				ToothKnockingHeavyPunch punch = heavyPunch(helper, power, user, stand, userPos, creeper, "creeper " + (i + 1));
				helper.assertTrue(countTeeth(level, area, creeper) == 0, "A heavy punch knocked a tooth out of a creeper");
				helper.assertTrue(!punch.canAcceptToothLifeformFollowup(),
						"Tooth lifeform follow-up was offered after punching a creeper");
				creeper.discard();
			}
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			discardAll(level, area, spawned);
			user.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void geToothLifeformLeavesCreateLifeformOffCooldown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player user = FakePlayerFactory.get(level, new GameProfile(
				UUID.fromString("7c1e0b48-0000-4000-8000-00000000a149"), "GoldExperienceToothLifeform"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
		StandPower power = null;
		Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		AABB area = new AABB(userPos, userPos).inflate(4.0D);
		List<Mob> spawned = new ArrayList<>();
		try {
			helper.assertTrue(standType != null, "Missing Gold Experience Stand type");
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(level.addFreshEntity(user), "Could not add Gold Experience tooth-lifeform player");
			power = grantAndSummon(helper, user, standType);
			helper.assertTrue(!power.isUserCreative(), "The tooth-lifeform user must not be creative");

			Mob cow = spawnTarget(helper, spawned, EntityType.COW, userPos.add(0.0D, 0.0D, 1.0D));
			GoldExperienceLifeformState lifeforms = GoldExperienceLifeformState.get(user);
			lifeforms.learnLifeformsForEntity(cow, level);
			String lifeformId = lifeforms.selectedOrFirstMetId(level);
			helper.assertTrue(lifeformId != null, "Gold Experience did not learn the cow lifeform");

			ObjectEntity tooth = new ObjectEntity(level, ObjectEntity.Type.TOOTH);
			tooth.setOwner(cow.getUUID());
			tooth.setPos(cow.getX(), cow.getEyeY(), cow.getZ());
			helper.assertTrue(level.addFreshEntity(tooth), "Could not add the knocked-out tooth");
			helper.assertTrue(power.getAbilityCooldown(GoldExperienceToothLifeformAbility.CREATE_LIFEFORM_ABILITY) == 0,
					"Create Lifeform started on cooldown");

			helper.assertTrue(GoldExperienceToothLifeformAbility.tryTransformToothObject(level, power, user, cow, tooth, lifeformId),
					"The tooth did not start turning into a lifeform");
			int cooldown = power.getAbilityCooldown(GoldExperienceToothLifeformAbility.CREATE_LIFEFORM_ABILITY);
			helper.assertTrue(cooldown == 0, "Tooth lifeform put Create Lifeform on a " + cooldown + "-tick cooldown");
			List<GETransformationEntity> transformations = level.getEntitiesOfClass(GETransformationEntity.class, area);
			helper.assertTrue(!transformations.isEmpty(), "The tooth transformation entity is missing");
			for (GETransformationEntity transformation : transformations) {
				helper.assertTrue(transformation.actionCooldown == 0,
						"Tooth transformation recorded a " + transformation.actionCooldown + "-tick cooldown for Revert to refund");
			}
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			discardAll(level, area, spawned);
			user.discard();
		}
	}

	private static StandPower grantAndSummon(GameTestHelper helper, Player user, StandType standType) {
		StandPower power = PowerClass.STAND.attachGet(user);
		StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
		helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
				"Could not grant Gold Experience: " + inserted.status());
		helper.assertTrue(standType.summon(user, power), "Could not summon Gold Experience");
		StandEntity stand = power.getSummonedStandEntity();
		helper.assertTrue(stand != null, "Summoned Gold Experience entity is missing");
		// Only StandEntity.tick counts the summon lock down and the test never runs it;
		// GE (speed 14) gets 3 lock ticks, and the lock skips every action tick.
		stand.summonLockTicks = 0;
		return power;
	}

	private static ToothKnockingHeavyPunch heavyPunch(GameTestHelper helper, StandPower power, Player user,
			StandEntity stand, Vec3 standPos, LivingEntity target, String label) {
		LivingComponentAction component = LivingComponentAction.getComponent(stand);
		component.setAction(null, user, SyncType.NO_SYNC);
		stand.moveTo(standPos.x, standPos.y, standPos.z);
		// a new Stand starts with no stamina; a real user punches with some
		power.setStamina(power.getMaxStamina());
		lookAt(user, target.getBoundingBox().getCenter());
		lookAt(stand, target.getBoundingBox().getCenter());
		component.entityAim.setTarget(new ActionTarget(target));
		Ability ability = power.getAbility("heavy_punch");
		helper.assertTrue(ability instanceof EntityActionType, "Gold Experience heavy punch is not an entity action");
		EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		helper.assertTrue(action instanceof ToothKnockingHeavyPunch, "Gold Experience heavy punch is not the tooth-knocking punch");
		component.setAction(action, user, SyncType.NO_SYNC);
		float health = target.getHealth();
		for (int tick = 0; tick < 60 && target.getHealth() >= health && component.getAction() == action; tick++) {
			component.tick();
		}
		helper.assertTrue(target.getHealth() < health, "Gold Experience heavy punch did not hurt the " + target.getType()
				+ " [" + label + ": phase=" + action.getPhase() + " phaseTick=" + action.getPhaseTick()
				+ " current=" + (component.getAction() == action) + " over=" + action.isOver()
				+ " punched=" + action.punchedTarget
				+ " fresh=" + StandEntityPunchAbility.getFreshPunchTarget(stand, ActionTarget.EMPTY)
				+ " aim=" + component.entityAim.getTarget()
				+ " summonLock=" + stand.summonLockTicks + " standStunned=" + ModStatusEffects.isStunned(stand)
				+ " stamina=" + power.getStamina()
				+ " standPos=" + stand.position() + " targetPos=" + target.position()
				+ " reach=" + stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)
				+ " health=" + health + "->" + target.getHealth() + " alive=" + target.isAlive()
				+ " invul=" + target.invulnerableTime + " targetTick=" + target.tickCount + "]");
		return (ToothKnockingHeavyPunch) action;
	}

	private static void lookAt(LivingEntity entity, Vec3 target) {
		Vec3 delta = target.subtract(entity.getEyePosition());
		float yaw = (float) -Math.toDegrees(Math.atan2(delta.x, delta.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
		entity.setYRot(yaw);
		entity.setYHeadRot(yaw);
		entity.setXRot(pitch);
		entity.yRotO = yaw;
		entity.xRotO = pitch;
	}

	private static Mob spawnTarget(GameTestHelper helper, List<Mob> spawned, EntityType<? extends Mob> type, Vec3 position) {
		Mob mob = type.create(helper.getLevel());
		helper.assertTrue(mob != null, "Could not create " + type);
		mob.moveTo(position.x, position.y, position.z);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Could not add " + type);
		spawned.add(mob);
		return mob;
	}

	private static int countTeeth(ServerLevel level, AABB area, Entity owner) {
		return level.getEntitiesOfClass(ObjectEntity.class, area,
				tooth -> tooth.getObjectType() == ObjectEntity.Type.TOOTH && owner.getUUID().equals(tooth.getOwner())).size();
	}

	private static void discardAll(ServerLevel level, AABB area, List<Mob> spawned) {
		spawned.forEach(Entity::discard);
		for (Entity entity : level.getEntitiesOfClass(Entity.class, area,
				entity -> entity instanceof ObjectEntity || entity instanceof GETransformationEntity)) {
			entity.discard();
		}
	}
}
