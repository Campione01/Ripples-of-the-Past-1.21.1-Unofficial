package com.github.standobyte.jojo.gametest;

import java.util.UUID;

import com.github.standobyte.jojo.api.stand.StandPowerTransitions;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.SyncType;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.AimingEntity;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityPunchAbility;
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
