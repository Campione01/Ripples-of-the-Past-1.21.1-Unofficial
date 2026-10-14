package rotp.core.gametest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BarrageTargetLifecycleGameTests {
	private BarrageTargetLifecycleGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void barrageReleasesDeadTargetAndRetargets(
			GameTestHelper helper) {
		Player user = FakePlayerFactory.get(
				helper.getLevel(), new GameProfile(
						UUID.fromString(
								"6ad5bb00-a65e-43fc-9815-9b5073dace04"),
						"BarrageRetargetUser"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(
				JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		Cow defeated = null;
		Cow replacement = null;
		try {
			helper.assertTrue(standType != null,
					"Missing Star Platinum Stand type");
			Vec3 userPos = Vec3.atCenterOf(
					helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user),
					"Could not add barrage target-lifecycle player");
			power = PowerClass.STAND.attachGet(user);
			user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(
					power, new StandInstance(standType));
			helper.assertTrue(
					inserted.status() == StandPowerTransitions.Status.APPLIED,
					"Could not grant Star Platinum: " + inserted.status());
			helper.assertTrue(standType.summon(user, power),
					"Could not summon Star Platinum");

			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null,
					"Summoned Star Platinum entity is missing");
			stand.moveTo(userPos.x, userPos.y, userPos.z);
			defeated = spawnCow(helper, userPos.add(0.0D, 0.0D, 1.0D));
			replacement = spawnCow(
					helper, userPos.add(0.75D, 0.0D, 1.25D));

			LivingComponentAction standAction =
					LivingComponentAction.getComponent(stand);
			standAction.entityAim.setTarget(new ActionTarget(defeated));
			Ability barrage = power.getAbility("barrage");
			helper.assertTrue(barrage instanceof EntityActionType,
					"Star Platinum barrage is not an entity action");
			EntityActionInstance action = ((EntityActionType) barrage)
					.initActionOnAbilityUse(
							helper.getLevel(), user, stand, null);
			standAction.setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(action.standRotationTarget != null
					&& action.standRotationTarget.getMainEntity() == defeated,
					"Barrage did not acquire its initial live target");

			defeated.setHealth(0.0F);
			helper.assertTrue(!defeated.isAlive() && !defeated.isRemoved(),
					"Defeated target did not remain as a death-animation entity");
			helper.assertTrue(
					!StandEntityAbility.canDefaultTargetEntityForAiming(
							stand, defeated),
					"Default Stand aim still accepts a defeated entity");
			standAction.entityAim.setTarget(ActionTarget.EMPTY);
			standAction.tick();
			helper.assertTrue(action.standRotationTarget == null
					&& action.aimAs == AimingEntity.CAMERA_ENTITY,
					"Held barrage did not release its defeated target");
			helper.assertTrue(standAction.getAction() == action,
					"Releasing the defeated target interrupted the held barrage");

			standAction.entityAim.setTarget(new ActionTarget(replacement));
			ActionTarget freshTarget =
					StandEntityPunchAbility.getFreshPunchTarget(
							stand, ActionTarget.EMPTY);
			helper.assertTrue(freshTarget.getMainEntity() == replacement,
					"Replacement target was rejected before barrage retarget: "
							+ "stand=" + stand.position()
							+ ", replacement=" + replacement.position()
							+ ", aim=" + standAction.entityAim.getTarget());
			float replacementHealth = replacement.getHealth();
			standAction.tick();
			helper.assertTrue(action.standRotationTarget != null
					&& action.standRotationTarget.getMainEntity() == replacement
					&& action.aimAs == AimingEntity.STAND,
					"Held barrage did not bind the replacement target: "
							+ "rotation=" + action.standRotationTarget
							+ ", aimAs=" + action.aimAs
							+ ", phase=" + action.getPhase());
			for (int tick = 1;
					tick < 20 && replacement.getHealth() >= replacementHealth;
					tick++) {
				standAction.tick();
			}
			helper.assertTrue(replacement.getHealth() < replacementHealth,
					"Retargeted barrage did not hit the replacement entity");
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			if (defeated != null) {
				defeated.discard();
			}
			if (replacement != null) {
				replacement.discard();
			}
			user.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void lightPunchFollowsAimWithoutLock(GameTestHelper helper) {
		verifyAimSwitch(helper, "punch", false);
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void heavyPunchFollowsAimWithoutLock(GameTestHelper helper) {
		verifyAimSwitch(helper, "heavy_punch", false);
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void barrageFollowsAimWithoutLock(GameTestHelper helper) {
		verifyAimSwitch(helper, "barrage", false);
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void lightPunchLockRetainsTarget(GameTestHelper helper) {
		verifyAimSwitch(helper, "punch", true);
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void heavyPunchLockRetainsTarget(GameTestHelper helper) {
		verifyAimSwitch(helper, "heavy_punch", true);
	}

	private static void verifyAimSwitch(GameTestHelper helper, String abilityName, boolean locked) {
		String testName = "AimSwitch_" + abilityName + "_" + locked;
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
				UUID.nameUUIDFromBytes(testName.getBytes(StandardCharsets.US_ASCII)), testName));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		Cow initial = null;
		Cow current = null;
		try {
			helper.assertTrue(standType != null, "Missing Star Platinum Stand type");
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add targeting player");
			power = PowerClass.STAND.attachGet(user);
			PlayerClientBroadcastedSettings settings =
					user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
			helper.assertTrue(!new PlayerClientBroadcastedSettings().standAttackTargetLock,
					"Continuous Stand target lock must default to disabled");
			settings.standAttackTargetLock = locked;
			boolean directional = !locked && "barrage".equals(abilityName);
			if (directional) {
				user.getAbilities().instabuild = true;
			}
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(standType.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Stand is missing");
			stand.moveTo(userPos.x, userPos.y, userPos.z);
			initial = spawnCow(helper, userPos.add(0.0D, 0.0D, 1.0D));
			current = spawnCow(helper, userPos.add(directional ? 1.4D : 0.75D, 0.0D, 1.25D));
			if (directional) {
				lookAt(user, initial.getBoundingBox().getCenter());
			}
			LivingComponentAction component = LivingComponentAction.getComponent(stand);
			component.entityAim.setTarget(new ActionTarget(initial));
			Ability ability = power.getAbility(abilityName);
			helper.assertTrue(ability instanceof EntityActionType, "Missing ordinary attack " + abilityName);
			EntityActionInstance action = ((EntityActionType) ability)
					.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			if (!locked) {
				assertNoPersistentLock(helper, action, abilityName);
			}

			component.entityAim.setTarget(new ActionTarget(current));
			if (directional) {
				lookAt(user, current.getBoundingBox().getCenter());
			}
			ActionTarget selected = directional
					? StandEntityBarrageAbility.clipDirectionalBarrageTarget(stand, action, 1.0F)
					: StandEntityPunchAbility.getFreshPunchTarget(stand, new ActionTarget(initial));
			Cow expected = locked ? initial : current;
			Cow untouched = locked ? current : initial;
			helper.assertTrue(selected.getMainEntity() == expected,
					abilityName + " did not obey its target-lock setting when aim changed");
			float expectedHealth = expected.getHealth();
			float untouchedHealth = untouched.getHealth();
			for (int tick = 0; tick < 60 && expected.getHealth() >= expectedHealth
					&& component.getAction() == action; tick++) {
				component.tick();
			}
			helper.assertTrue(expected.getHealth() < expectedHealth, abilityName + " did not damage its aimed target");
			helper.assertTrue(untouched.getHealth() == untouchedHealth, abilityName + " damaged the other entity");

			if (!locked) {
				assertNoPersistentLock(helper, action, abilityName);
				if (!"barrage".equals(abilityName)) {
					component.setAction(null, user, SyncType.NO_SYNC);
					component.entityAim.setTarget(new ActionTarget(initial));
					action = ((EntityActionType) ability)
							.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
					component.setAction(action, user, SyncType.NO_SYNC);
				}
				component.entityAim.setTarget(ActionTarget.EMPTY);
				LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
				if (directional) {
					lookAt(user, user.getEyePosition().add(0, 2, -3));
				}
				ActionTarget emptyAim = directional
						? StandEntityBarrageAbility.clipDirectionalBarrageTarget(stand, action, 1.0F)
						: StandEntityPunchAbility.getFreshPunchTarget(stand, new ActionTarget(initial));
				helper.assertTrue(emptyAim.isEmpty(helper.getLevel()),
						abilityName + " retained an entity after the crosshair moved to empty space");
				float initialHealth = initial.getHealth();
				float currentHealth = current.getHealth();
				for (int tick = 0; tick < 60 && component.getAction() == action; tick++) {
					component.tick();
				}
				helper.assertTrue(initial.getHealth() == initialHealth && current.getHealth() == currentHealth,
						abilityName + " damaged an entity while aiming at empty space");
				assertNoPersistentLock(helper, action, abilityName);
			}
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			if (initial != null) initial.discard();
			if (current != null) current.discard();
			user.discard();
		}
	}

	private static void assertNoPersistentLock(
			GameTestHelper helper, EntityActionInstance action, String abilityName) {
		helper.assertTrue(action.standRotationTarget == null && action.aimAs == AimingEntity.CAMERA_ENTITY,
				abilityName + " kept Stand-direction aiming or a persistent rotation target while lock was disabled");
	}

	private static void lookAt(Player user, Vec3 target) {
		Vec3 delta = target.subtract(user.getEyePosition());
		float yaw = (float) -Math.toDegrees(Math.atan2(delta.x, delta.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
		user.setYRot(yaw);
		user.setYHeadRot(yaw);
		user.setXRot(pitch);
		user.yRotO = yaw;
		user.xRotO = pitch;
	}

	private static Cow spawnCow(GameTestHelper helper, Vec3 position) {
		Cow cow = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(cow != null, "Could not create barrage target cow");
		cow.moveTo(position.x, position.y, position.z);
		cow.setNoAi(true);
		cow.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(cow),
				"Could not add barrage target cow");
		return cow;
	}
}
