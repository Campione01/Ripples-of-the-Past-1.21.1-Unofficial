package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.compat.v1_21_4.missingmethods._EntitySelector;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.CrosshairRule;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 EntityStandType.clientHitResult (:122-150), a client's mouse target with its Stand out: the vanilla pick, else the
 * precision ray from the camera entity, else a miss; no ray from the Stand's eyes. Only the server's strike aimed again
 * from the Stand's eyes when its user's precision ray met nothing (StandEntity.aimWithThisOrUser). The port's client Stand
 * aim (StandEntityPunchAbility.clientStandAim, what ClientsideAim keeps, the marker shows and a press carries) fell back
 * to the Stand's own ray, and the idle Stand turns its look to its aimed entity (the owner's D-core-94), so once the aim
 * had met a cow the Stand's look kept that cow as the aim at the sky and with a cow beyond the user's reach on the
 * crosshair (client item S12-1). Each core Stand, summoned for a Survival user, turned to a cow four blocks ahead by its
 * idle look; a second cow 12 blocks to the user's left, a third 4 blocks to its right. Own batch: the rays reach several
 * blocks and must not meet a neighbouring test's mobs.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClientStandAimFromItsUser116GameTests {
	private static final String[] CORE_STANDS = { "star_platinum", "the_world", "crazy_diamond", "gold_experience",
			"magicians_red", "silver_chariot", "hierophant_green" };

	private ClientStandAimFromItsUser116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "client_stand_aim_from_its_user_116")
	public static void anIdleStandsLookDoesNotKeepAMobAsItsClientAimAsIn116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (String standName : CORE_STANDS) {
			try (Scene scene = new Scene(helper, standName)) {
				Cow near = scene.cow(0.0D, 4.0D);
				Cow far = scene.cow(12.0D, 0.0D);
				Cow aside = scene.cow(-4.0D, 0.0D);

				// the crosshair rests on the near cow: past the Survival vanilla pick, the user's precision ray takes it,
				// the server stores that aim and the idle Stand turns its look to it
				scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, near.getBoundingBox().getCenter());
				ActionTarget first = scene.clientStandAim();
				helper.assertTrue(first.getType() == TargetType.ENTITY && first.getEntity() == near, "Premise (" + standName
						+ "): with the crosshair on the cow 4 blocks ahead the client's Stand aim is " + first);
				LivingComponentAction.getComponent(scene.stand).entityAim.setTargetFromClient(first, helper.getLevel());
				boolean looked = scene.stand.lookAtCurTarget(scene.stand.rotO);
				Vec3 standEyes = scene.stand.getEyePosition();
				helper.assertTrue(looked && near.getBoundingBox().clip(standEyes, standEyes.add(scene.stand.getLookAngle().scale(5.0D)))
						.isPresent(), "Premise (" + standName + "): the idle Stand did not turn its look to the aimed cow");

				scene.user.setXRot(-90.0F);
				scene.user.xRotO = -90.0F;
				scene.expectNoStandEyeAim("its user looking straight up", near, null, wrong);

				scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, far.getBoundingBox().getCenter());
				scene.expectNoStandEyeAim("its user looking at a cow 12 blocks away", near, null, wrong);

				scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, aside.getBoundingBox().getCenter());
				scene.expectNoStandEyeAim("its user looking at a cow 4 blocks away", near, aside, wrong);
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	private static final class Scene implements AutoCloseable {
		final GameTestHelper helper;
		final String standName;
		final Player user;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		final Vec3 origin;
		final List<Entity> entities = new ArrayList<>();

		Scene(GameTestHelper helper, String standName) {
			this.helper = helper;
			this.standName = standName;
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.noPhysics = true;
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			user.setYHeadRot(0);
			user.yBodyRot = 0;
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			entities.add(user);
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standName));
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standName);
			helper.assertTrue(type.summon(user, power), "Could not summon " + standName);
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "Missing the " + standName + " entity");
			stand.setNoGravity(true);
			// behind its user's left shoulder, as an idle Stand stands
			stand.moveTo(origin.x + 0.5D, origin.y, origin.z - 0.5D, 0, 0);
			stand.setYHeadRot(0);
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
			helper.assertTrue(StandEntityPunchAbility.aimAssist(user) && !stand.isManuallyControlled()
					&& !stand.aimsWithoutUser() && stand.getCurStandAction() == null,
					"Fixture: " + standName + "'s user has no aim assist, or the Stand aims by itself or runs an action");
		}

		Cow cow(double x, double z) {
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Could not create a cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.noPhysics = true;
			cow.moveTo(origin.x + x, origin.y, origin.z + z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add a cow");
			entities.add(cow);
			return cow;
		}

		// GameRenderer.pick on the user's client; the Stand standing by its user is not pickable there
		ActionTarget vanillaPick() {
			Predicate<Entity> pickable = entity -> entity != stand && _EntitySelector.CAN_BE_PICKED.test(entity);
			return StandEntityPunchAbility.crosshairPick(stand, user, 1.0F, pickable);
		}

		ActionTarget clientStandAim() {
			return StandEntityPunchAbility.clientStandAim(stand, user, user, vanillaPick());
		}

		/**
		 * The Stand still looks at {@code looked}; the client's Stand aim must be the user's own aim, {@code expected}, or
		 * nothing. Where the user's precision ray meets nothing, the server's strike still aims again from the Stand's eyes
		 * and takes the cow it looks at, as 1.16's aimWithThisOrUser did.
		 */
		void expectNoStandEyeAim(String row, Cow looked, Cow expected, List<String> wrong) {
			ActionTarget pick = vanillaPick();
			ActionTarget userRay = StandEntityPunchAbility.userPrecisionRayTarget(stand, user,
					entity -> StandEntityPunchAbility.canUserAimTarget(stand, entity));
			Vec3 standEyes = stand.getEyePosition();
			boolean stillLooks = looked.getBoundingBox().clip(standEyes, standEyes.add(stand.getLookAngle().scale(5.0D))).isPresent();
			helper.assertTrue(pick.getType() == TargetType.EMPTY && stillLooks, "Premise (" + standName + ", " + row
					+ "): the vanilla pick is " + pick + ", the Stand looks at the near cow " + stillLooks);
			if (expected == null) {
				ActionTarget strike = StandEntityPunchAbility.strikeAim(stand, ActionTarget.EMPTY, CrosshairRule.BLOCK_OR_ENTITY);
				helper.assertTrue(userRay.getType() == TargetType.EMPTY, "Premise (" + standName + ", " + row
						+ "): the user's precision ray meets " + userRay);
				if (strike.getType() != TargetType.ENTITY || strike.getEntity() != looked) {
					wrong.add(standName + ", " + row + ": the server's strike aims again at " + strike + "; 1.16's"
							+ " aimWithThisOrUser fell back to the Stand's own precision ray, which meets the cow it looks at");
				}
			}
			ActionTarget aim = clientStandAim();
			boolean right = expected == null ? aim.getType() == TargetType.EMPTY
					: aim.getType() == TargetType.ENTITY && aim.getEntity() == expected;
			if (!right) {
				wrong.add(standName + " turned to the cow 4 blocks ahead, " + row + ": the client's Stand aim is "
						+ (aim.getEntity() == looked ? "the cow the Stand looks at" : aim.toString()) + "; 1.16's mouse target"
						+ " (EntityStandType.clientHitResult) is " + (expected == null ? "a miss, with no ray from the Stand's eyes"
								: "the cow the user's precision ray meets"));
			}
		}

		@Override
		public void close() {
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			if (power.isSummoned()) type.forceUnsummon(user, power);
			entities.forEach(Entity::discard);
		}
	}
}
