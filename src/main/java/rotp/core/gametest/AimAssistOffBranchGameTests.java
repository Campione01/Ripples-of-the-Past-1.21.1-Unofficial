package rotp.core.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.hierophant.HierophantEmeraldSplashAbility.EmeraldSplashShot;
import rotp.core.impl.stands.hierophant.HierophantGreenEntity;
import rotp.core.impl.stands.magiciansred.MagiciansRedCrossfireHurricaneAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
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
import rotp.core.subsystems.target.HitResultUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The owner's aim assist Stand setting (2026-10-15), off: the Stand moves that cast a precision ray of their own take
 * what the crosshair meets, by the entities' own boxes. With it on they keep 1.16's precision boxes, which take a mob
 * beside the crosshair before the one on it. Own batches: the rays reach several blocks.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AimAssistOffBranchGameTests {
	private static final String BATCH = "aim_assist_off_branch";

	private AimAssistOffBranchGameTests() {}

	// 1.16 HierophantGreenEmeraldSplash.aimForBarriers cast JojoModUtil.rayTrace with inflate 0 and precision 0 from the
	// user's eyes, or the Stand's under manual control. The net's aim never took the Stand's precision boxes, so the mob on
	// the crosshair is its aim with the aim assist on and off, and the mob beside the crosshair is never.
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "_1")
	public static void emeraldSplashBarrierAimTakesTheCrosshairMobWithTheAimAssistOnAndOff(GameTestHelper helper) {
		barrierNetAimTakesTheCrosshairMob(helper, false);
		barrierNetAimTakesTheCrosshairMob(helper, true);
		helper.succeed();
	}

	private static void barrierNetAimTakesTheCrosshairMob(GameTestHelper helper, boolean standAims) {
		String position = standAims ? "the Stand's eyes under manual control" : "the user's eyes";
		try (Scene scene = new Scene(helper, "hierophant_green", standAims)) {
			Cow beside = scene.cow(1.2D, 2.0D);
			Cow crosshair = scene.cow(0.0D, 4.0D);
			helper.assertTrue(scene.stand instanceof HierophantGreenEntity, "Fixture: the Stand is not Hierophant Green");
			HierophantGreenEntity hierophant = (HierophantGreenEntity) scene.stand;
			LivingEntity aiming = standAims ? scene.stand : scene.user;
			if (standAims) {
				scene.stand.setManuallyControlled(true);
			}
			Vec3 eyes = aiming.getEyePosition();
			Vec3 end = eyes.add(aiming.getLookAngle().scale(hierophant.getMaxRange()));
			ActionTarget precise = HitResultUtil.clip(eyes, aiming.getLookAngle(), hierophant.getMaxRange(), hierophant.getMaxRange(),
					helper.getLevel(), entity -> entity instanceof LivingEntity, aiming, hierophant.getPrecision(), false);
			helper.assertTrue(crosshair.getBoundingBox().clip(eyes, end).isPresent() && beside.getBoundingBox().clip(eyes, end).isEmpty()
					&& precise.getMainEntity() == beside,
					"Fixture: from " + position + " the crosshair does not meet only the cow on it, or the Stand's precision boxes would not"
							+ " take the cow beside it: precision ray " + precise);
			Ability splash = scene.power.getAbility("emerald_splash");
			helper.assertTrue(splash instanceof EntityActionType, "Fixture: no emerald_splash action");
			EntityActionInstance action = ((EntityActionType) splash).initActionOnAbilityUse(helper.getLevel(), scene.user,
					scene.stand, null);
			helper.assertTrue(action instanceof EmeraldSplashShot, "Fixture: emerald_splash started " + action);
			// the action reads its level from the Stand it runs on
			LivingComponentAction.getComponent(scene.stand).setAction(action, scene.user, SyncType.NO_SYNC);

			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				Optional<Vec3> aimPoint = EmeraldSplashShot.barrierAimPoint(hierophant);
				helper.assertTrue(aimPoint.equals(Optional.of(crosshair.getBoundingBox().getCenter())),
						"With the aim assist " + (assist ? "on" : "off") + " the Emerald Splash barrier aim from " + position + " went for "
								+ aimPoint + " instead of the cow on the crosshair at " + crosshair.getBoundingBox().getCenter()
								+ " (the cow beside it is at " + beside.getBoundingBox().getCenter() + "); 1.16 casts the net's ray"
								+ " with precision 0");
			}
		}
	}

	// Magician's Red's Crossfire Hurricane special aims from the Stand's own eyes under remote control
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "_2")
	public static void crossfireRemoteAimWithoutTheAimAssistTakesTheCrosshairMob(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "magicians_red", true)) {
			Cow beside = scene.cow(1.2D, 2.0D);
			Cow crosshair = scene.cow(0.0D, 4.0D);
			Class<?> shot = null;
			for (Class<?> inner : MagiciansRedCrossfireHurricaneAbility.class.getDeclaredClasses()) {
				if (inner.getSimpleName().equals("CrossfireShot")) {
					shot = inner;
				}
			}
			helper.assertTrue(shot != null, "Fixture: no CrossfireShot class");
			Method clipAim = method(helper, shot, "clipAim", Level.class, LivingEntity.class, StandEntity.class);

			Object on = invoke(helper, clipAim, null, helper.getLevel(), scene.stand, scene.stand);
			helper.assertTrue(on instanceof ActionTarget target && target.getMainEntity() == beside,
					"Fixture: with the aim assist on the Stand's own aim does not take the cow beside its crosshair: " + on);
			scene.aimAssist(false);
			Object off = invoke(helper, clipAim, null, helper.getLevel(), scene.stand, scene.stand);
			helper.assertTrue(off instanceof ActionTarget target && target.getMainEntity() == crosshair,
					"With the aim assist off the Crossfire Hurricane's aim from the Stand went for " + off
							+ " instead of the cow on its crosshair");
		}
		helper.succeed();
	}

	private static Method method(GameTestHelper helper, Class<?> owner, String name, Class<?>... parameters) {
		try {
			Method method = owner.getDeclaredMethod(name, parameters);
			method.setAccessible(true);
			return method;
		}
		catch (NoSuchMethodException e) {
			helper.fail("Fixture: " + owner.getSimpleName() + " has no " + name);
			throw new IllegalStateException(e);
		}
	}

	private static Object invoke(GameTestHelper helper, Method method, Object on, Object... args) {
		try {
			return method.invoke(on, args);
		}
		catch (IllegalAccessException | InvocationTargetException e) {
			throw new IllegalStateException("Fixture: " + method.getName() + " failed", e);
		}
	}

	private static final class Scene implements AutoCloseable {
		final GameTestHelper helper;
		final Player user;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		final Vec3 origin;
		final List<Entity> entities = new ArrayList<>();

		// the aiming one of the two stands at the origin, the other a block and a half behind it
		Scene(GameTestHelper helper, String standName, boolean standAims) {
			this.helper = helper;
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			user.setYHeadRot(0);
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
			stand.moveTo(origin.x, origin.y, origin.z - (standAims ? 0.0D : 1.5D), 0, 0);
			stand.setYHeadRot(0);
			if (standAims) {
				user.moveTo(origin.x, origin.y, origin.z - 1.5D, 0, 0);
			}
			// wide precision boxes, so that the assist reaches the cow beside the crosshair
			stand.getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(16.0D);
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
			aimAssist(true);
		}

		// a cow with its box centred on the eye-level crosshair ray, x blocks to the side of it
		Cow cow(double x, double z) {
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Could not create a cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(origin.x + x, user.getEyeY() - cow.getBbHeight() / 2.0D, origin.z + z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add a cow");
			entities.add(cow);
			return cow;
		}

		void aimAssist(boolean on) {
			user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAimAssist = on;
		}

		@Override
		public void close() {
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			if (power.isSummoned()) type.forceUnsummon(user, power);
			entities.forEach(Entity::discard);
		}
	}
}
