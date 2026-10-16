package rotp.core.gametest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import rotp.core.api.stand.StandDefaultRotationRules;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The add-on hook for the default rotation of a following Stand whose user is not a player (StandDefaultRotationRules):
 * 1.16's Mobs with Powers replaced StandEntity.defaultRotation for a mob user on both sides with a mixin (050
 * StandEntityMixin.defaultRotation), which an add-on of the port may not use. A probe rule takes the rotation over for the
 * users the test names.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandDefaultRotationRuleGameTests {
	private static final double EPS = 1.0E-4D;
	private static final float KEEP = Float.NaN;
	private static final float FAIL = Float.POSITIVE_INFINITY;
	// user -> the yaw the probe turns its Stand to, KEEP to leave it as it is, FAIL to throw
	private static final Map<UUID, Float> RULED = new ConcurrentHashMap<>();
	private static final AtomicBoolean REGISTERED = new AtomicBoolean();

	private StandDefaultRotationRuleGameTests() {}

	private static void ensureProbeRegistered() {
		if (REGISTERED.compareAndSet(false, true)) {
			StandDefaultRotationRules.register(JojoMod.resLoc("gametest_stand_default_rotation"), (stand, user) -> {
				Float yaw = RULED.get(user.getUUID());
				if (yaw == null) {
					return false;
				}
				if (yaw == FAIL) {
					throw new IllegalStateException("the test's failing default rotation rule");
				}
				if (!yaw.isNaN()) {
					stand.setYRot(yaw);
					stand.setXRot(0.0F);
				}
				return true;
			});
		}
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void anAddOnRuleTakesOverTheDefaultRotationOfAMobUsersFollowingStand(GameTestHelper helper) {
		ensureProbeRegistered();
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 12, 2)));
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(type != null && zombie != null, "setup: could not create the zombie or find Star Platinum");
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.moveTo(origin.x, origin.y, origin.z, 90.0F, 20.0F);
		zombie.setYHeadRot(100.0F);
		zombie.yBodyRot = 80.0F;
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "setup: could not add the zombie");
		Cow cow = null;
		StandPower power = PowerClass.STAND.attachGet(zombie);
		try {
			RULED.put(zombie.getUUID(), KEEP);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(zombie, power), "setup: could not give the zombie Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == zombie && stand.isFollowingUser(),
					"setup: the zombie's Stand is missing or does not follow it");
			stand.setNoGravity(true);
			assertRotation(helper, stand, 90.0F, 20.0F, "A Stand summoned for a mob whose Stand a rule keeps took the mob's"
					+ " rotation as it was summoned (EntityStandType.summon)");
			helper.assertTrue(Math.abs(Mth.wrapDegrees(stand.yHeadRot - 100.0F)) < EPS
					&& Math.abs(Mth.wrapDegrees(stand.yBodyRot - 80.0F)) < EPS, "A Stand summoned for a mob whose Stand a"
							+ " rule keeps took the mob's head 100 and body 80 as it was summoned (copyStandUserRotation): head "
							+ stand.yHeadRot + ", body " + stand.yBodyRot);

			turn(stand, 30.0F, 5.0F);
			stand.updatePosition(zombie);
			assertRotation(helper, stand, 30.0F, 5.0F, "A rule that keeps a mob's following Stand as it is left it at its own"
					+ " rotation, where the core copies the mob's");
			helper.assertTrue(Math.abs(Mth.wrapDegrees(stand.yBodyRot - 30.0F)) < EPS, "A rule that keeps a mob's following"
					+ " Stand as it is left its body at 30, where the core copies the mob's: it is at " + stand.yBodyRot);

			RULED.put(zombie.getUUID(), 45.0F);
			stand.updatePosition(zombie);
			assertRotation(helper, stand, 45.0F, 0.0F, "A rule that turns a mob's following Stand turned it");
			stand.tick();
			assertRotation(helper, stand, 45.0F, 0.0F, "A rule that turns a mob's following Stand turned it on its tick");
			helper.assertTrue(Math.abs(Mth.wrapDegrees(stand.yHeadRot - 45.0F)) < EPS, "The head of a Stand a rule turned"
					+ " went on its yaw 45 as usual (1.16 defaultRotation): it is at " + stand.yHeadRot);

			cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "setup: could not create the cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(origin.x + 3.0D, origin.y, origin.z, 0.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "setup: could not add the cow");
			TargetKeepingProbe probe = new TargetKeepingProbe(ModStandAbilities.HG_STRING_ATTACK.get(),
					new AbilityId(PowerClass.STAND, JojoMod.resLoc("star_platinum"), "default_rotation_probe"));
			EntityActionInstance action = probe.initActionOnAbilityUse(helper.getLevel(), zombie, stand, null);
			LivingComponentAction actions = LivingComponentAction.getComponent(stand);
			actions.setAction(action, zombie, SyncType.NO_SYNC);
			stand.setTaskTarget(new ActionTarget(cow));
			helper.assertTrue(action.getTaskTarget(helper.getLevel()).getEntity() == cow, "setup: the probe action holds "
					+ action.getTaskTarget(helper.getLevel()));
			stand.updatePosition(zombie);
			Vec3 toCow = cow.getEyePosition().subtract(stand.getEyePosition()).normalize();
			helper.assertTrue(stand.getLookAngle().dot(toCow) > 0.9999D, "The task's target still turned a mob's following"
					+ " Stand after a rule turned it: it looks along " + stand.getLookAngle() + " instead of " + toCow);
			actions.setAction(null, zombie, SyncType.NO_SYNC);

			RULED.put(zombie.getUUID(), FAIL);
			turn(stand, 30.0F, 5.0F);
			stand.updatePosition(zombie);
			assertRotation(helper, stand, 90.0F, 20.0F, "A failing rule left the copy of the mob's rotation to the core");

			RULED.remove(zombie.getUUID());
			turn(stand, 30.0F, 5.0F);
			stand.updatePosition(zombie);
			assertRotation(helper, stand, 90.0F, 20.0F, "With no rule taking it over, a mob's following Stand copied the"
					+ " mob's rotation");
		}
		finally {
			RULED.remove(zombie.getUUID());
			if (power.isSummoned()) {
				type.forceUnsummon(zombie, power);
			}
			if (cow != null) {
				cow.discard();
			}
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void noRuleIsAskedForAPlayersStand(GameTestHelper helper) {
		ensureProbeRegistered();
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			Player user = scene.user();
			RULED.put(user.getUUID(), 45.0F);
			try {
				StandTaskTargetOffsetGameTests.turn(user, 70.0F, 70.0F);
				turn(scene.stand(), 30.0F, 5.0F);
				scene.stand().updatePosition(user);
				assertRotation(helper, scene.stand(), 70.0F, 0.0F, "A player's following Stand copied the player's rotation"
						+ " whatever the rules say");
			}
			finally {
				RULED.remove(user.getUUID());
			}
		});
		helper.succeed();
	}

	private static void turn(StandEntity stand, float yaw, float pitch) {
		stand.setYRot(yaw);
		stand.setXRot(pitch);
		stand.setYHeadRot(yaw);
		stand.yBodyRot = yaw;
	}

	private static void assertRotation(GameTestHelper helper, StandEntity stand, float yaw, float pitch, String claim) {
		helper.assertTrue(Math.abs(Mth.wrapDegrees(stand.getYRot() - yaw)) < EPS && Math.abs(stand.getXRot() - pitch) < EPS,
				claim + ": yaw " + yaw + ", pitch " + pitch + " expected, got " + stand.getYRot() + ", " + stand.getXRot());
	}

	// an action whose task keeps an entity, as String Attack's does
	private static final class TargetKeepingProbe extends StandEntityAbility {
		TargetKeepingProbe(AbilityType<?> abilityType, AbilityId abilityId) {
			super(abilityType, abilityId, EntityActionInstance::new);
			setDefaultPhaseLength(ActionPhase.PERFORM, 40);
		}

		@Override
		protected boolean keepsTaskTargetType(StandEntity standEntity, ActionTarget target) {
			return target.getType() == ActionTarget.TargetType.ENTITY && target.getEntity() instanceof LivingEntity;
		}
	}
}
