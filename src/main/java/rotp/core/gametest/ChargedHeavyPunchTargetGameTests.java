package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility.StandEntityChargedHeavy;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Port-own charged heavy punch (heavy_charged): at perform start the Stand locks onto the crosshair target
 * and stops in front of it instead of lunging 2 blocks past it; the hit also finds a target standing
 * between the user and the lunged Stand (clip from the user's view, not only from the Stand's eye).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChargedHeavyPunchTargetGameTests {
	private ChargedHeavyPunchTargetGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void chargedHeavyLocksOntoTargetInFront(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player user = FakePlayerFactory.get(level, new GameProfile(
				UUID.fromString("7c1e0b48-0000-4000-8000-0000c148a001"), "ChargedHeavyLock"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
		StandPower power = null;
		Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		List<Mob> spawned = new ArrayList<>();
		try {
			power = setUp(helper, user, standType, userPos);
			StandEntity stand = power.getSummonedStandEntity();
			LivingComponentAction component = LivingComponentAction.getComponent(stand);

			// the synced crosshair target is beside the user's view line (precision aim)
			Mob side = spawnHusk(helper, spawned, userPos.add(0.8D, 0.0D, 1.2D));
			component.entityAim.setTarget(new ActionTarget(side));
			Observed aimed = chargedHeavy(helper, power, user, stand, side, null, "aimed beside the view line");
			helper.assertTrue(aimed.hurt, "Charged heavy punch missed the aimed target" + aimed.state);
			helper.assertTrue(aimed.offsetZ < 1.0D, "Stand lunged past the aimed target" + aimed.state);
			helper.assertTrue(aimed.rotationTarget == side, "Stand was not kept aimed at the aimed target" + aimed.state);
			side.discard();

			// no synced aim: the target straight ahead in the user's view is locked
			Mob ahead = spawnHusk(helper, spawned, userPos.add(0.0D, 0.0D, 1.2D));
			component.entityAim.setTarget(ActionTarget.EMPTY);
			Observed viewed = chargedHeavy(helper, power, user, stand, ahead, null, "straight ahead, no synced aim");
			helper.assertTrue(viewed.hurt, "Charged heavy punch missed the target right in front" + viewed.state);
			helper.assertTrue(viewed.offsetZ < 1.0D, "Stand lunged past the target right in front" + viewed.state);
			helper.succeed();
		}
		finally {
			tearDown(user, standType, power, spawned);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void chargedHeavyHitsTargetBetweenUserAndStand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player user = FakePlayerFactory.get(level, new GameProfile(
				UUID.fromString("7c1e0b48-0000-4000-8000-0000c148a002"), "ChargedHeavyStepIn"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
		StandPower power = null;
		Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		List<Mob> spawned = new ArrayList<>();
		try {
			power = setUp(helper, user, standType, userPos);
			StandEntity stand = power.getSummonedStandEntity();
			LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);

			// nothing in front at perform start; the target steps in between user and lunged Stand
			Mob stepper = spawnHusk(helper, spawned, userPos.add(0.0D, 0.0D, -3.0D));
			Vec3 stepIn = userPos.add(0.0D, 0.0D, 1.2D);
			Observed o = chargedHeavy(helper, power, user, stand, stepper,
					() -> stepper.moveTo(stepIn.x, stepIn.y, stepIn.z), "stepped in during the swing");
			helper.assertTrue(o.offsetZ >= 1.9D, "Stand did not lunge 2 blocks ahead with no target" + o.state);
			helper.assertTrue(o.standAheadZ >= 1.7D, "Stand is not ahead of the stepped-in target" + o.state);
			helper.assertTrue(o.hurt, "Charged heavy punch missed the target between user and Stand" + o.state);
			helper.succeed();
		}
		finally {
			tearDown(user, standType, power, spawned);
		}
	}

	private static final class Observed {
		double offsetZ = Double.NaN;
		double standAheadZ = Double.NaN;
		@Nullable Entity rotationTarget;
		boolean hurt;
		String state = "";
	}

	private static StandPower setUp(GameTestHelper helper, Player user, StandType standType, Vec3 userPos) {
		helper.assertTrue(standType != null, "Missing Gold Experience Stand type");
		// yaw 0 looks along +z, pitch 0 is level
		user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
		user.setYHeadRot(0.0F);
		user.setYBodyRot(0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add charged heavy punch player");
		StandPower power = PowerClass.STAND.attachGet(user);
		StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
		helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
				"Could not grant Gold Experience: " + inserted.status());
		helper.assertTrue(standType.summon(user, power), "Could not summon Gold Experience");
		StandEntity stand = power.getSummonedStandEntity();
		helper.assertTrue(stand != null, "Summoned Gold Experience entity is missing");
		// only StandEntity.tick counts the summon lock down, and it skips every action tick
		stand.summonLockTicks = 0;
		return power;
	}

	private static void tearDown(Player user, @Nullable StandType standType, @Nullable StandPower power, List<Mob> spawned) {
		if (power != null && power.isSummoned() && standType != null) {
			standType.forceUnsummon(user, power);
		}
		spawned.forEach(Entity::discard);
		user.discard();
	}

	// runs heavy_charged released at once; the Stand is moved to its offset every tick like StandEntity.tick does
	private static Observed chargedHeavy(GameTestHelper helper, StandPower power, Player user, StandEntity stand,
			Mob target, @Nullable Runnable onPerformStart, String label) {
		LivingComponentAction component = LivingComponentAction.getComponent(stand);
		component.setAction(null, user, SyncType.NO_SYNC);
		stand.updatePosition(user);
		power.setStamina(power.getMaxStamina());
		Ability ability = power.getAbility("heavy_charged");
		helper.assertTrue(ability instanceof EntityActionType, "Gold Experience heavy_charged is not an entity action");
		EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		helper.assertTrue(action instanceof StandEntityChargedHeavy, "heavy_charged did not start a charged heavy punch");
		component.setAction(action, user, SyncType.NO_SYNC);
		action.onKeyRelease(user);
		Observed o = new Observed();
		float health = target.getHealth();
		for (int tick = 0; tick < 80 && component.getAction() == action && target.getHealth() >= health; tick++) {
			component.tick();
			stand.updatePosition(user);
			if (Double.isNaN(o.offsetZ) && action.getPhase() == ActionPhase.PERFORM && action.getPhaseTick() >= 1) {
				helper.assertTrue(action.punchedTarget == null, "Charged heavy perform ended in its first tick [" + label + "]");
				o.offsetZ = stand.offsetFromUser.getRelativeOffset().z;
				o.standAheadZ = stand.getZ() - user.getZ();
				o.rotationTarget = action.standRotationTarget != null ? action.standRotationTarget.getMainEntity() : null;
				if (onPerformStart != null) {
					onPerformStart.run();
				}
			}
		}
		o.hurt = target.getHealth() < health;
		o.state = " [" + label + ": phase=" + action.getPhase() + " phaseTick=" + action.getPhaseTick()
				+ " current=" + (component.getAction() == action) + " punched=" + action.punchedTarget
				+ " offsetZ=" + o.offsetZ + " standAheadZ=" + o.standAheadZ + " rotation=" + o.rotationTarget
				+ " standPos=" + stand.position() + " userPos=" + user.position() + " targetPos=" + target.position()
				+ " reach=" + stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)
				+ " stamina=" + power.getStamina() + " health=" + health + "->" + target.getHealth() + "]";
		return o;
	}

	private static Mob spawnHusk(GameTestHelper helper, List<Mob> spawned, Vec3 position) {
		// a husk does not burn in daylight, so only the punch can lower its health
		Mob mob = EntityType.HUSK.create(helper.getLevel());
		helper.assertTrue(mob != null, "Could not create a husk");
		mob.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
		mob.setHealth(500.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Could not add a husk");
		spawned.add(mob);
		return mob;
	}
}
