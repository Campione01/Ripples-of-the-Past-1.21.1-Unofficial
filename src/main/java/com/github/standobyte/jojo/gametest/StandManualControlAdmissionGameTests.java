package com.github.standobyte.jojo.gametest;

import java.util.UUID;

import com.github.standobyte.jojo.api.stand.StandPowerTransitions;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModSpecialActions;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.ability.input.AbilityInput;
import com.github.standobyte.jojo.powersystem.ability.input.ActionInputBuffer.BufferingState;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity.StandFlag;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.EntityComponentController;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityManualControlToggle;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandManualControlAdmissionGameTests {
	private StandManualControlAdmissionGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void acceptedManualControlBindsAndReleases(GameTestHelper helper) {
		try (Fixture fixture = new Fixture(helper.getLevel())) {
			fixture.initialize(helper, false);
			StandEntityManualControlToggle.on(helper.getLevel(), fixture.stand);
			assertControlPair(helper, fixture, true);
			helper.assertTrue(fixture.stand.followingUserIsEnabled(), "Admission must enable Stand following");
			fixture.toggle.onClick(helper.getLevel(), fixture.user, null);
			assertControlPair(helper, fixture, false);
			helper.assertTrue(fixture.stand.followingUserIsEnabled(), "Normal exit must enable following");

			fixture.toggle.onClick(helper.getLevel(), fixture.user, null);
			assertControlPair(helper, fixture, true);
			fixture.user.setShiftKeyDown(true);
			fixture.toggle.onClick(helper.getLevel(), fixture.user, null);
			assertControlPair(helper, fixture, false);
			helper.assertTrue(!fixture.stand.followingUserIsEnabled(), "Shift exit must retain the Stand's position");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void rejectedAdmissionDoesNotBindOrReplaceControl(GameTestHelper helper) {
		try (Fixture fixture = new Fixture(helper.getLevel())) {
			fixture.initialize(helper, true);
			AdmissionStand stand = (AdmissionStand) fixture.stand;
			stand.setCanFollowUser(false);
			StandEntityManualControlToggle.on(helper.getLevel(), stand);
			assertControlPair(helper, fixture, false);
			helper.assertTrue(stand.admissionCalls == 1, "Fixture did not exercise the entity admission rejection");
			helper.assertTrue(!stand.followingUserIsEnabled(), "Rejected admission reset the Stand's keep-position state");

			Cow otherTarget = fixture.createOtherTarget(helper);
			EntityComponentController.setControlTarget(fixture.user, otherTarget, "mob");
			StandEntityManualControlToggle.on(helper.getLevel(), stand);
			helper.assertTrue(!stand.getStandFlag(StandFlag.MANUAL_CONTROL)
					&& EntityComponentController.getCurrentController(stand) == null
					&& !stand.followingUserIsEnabled(),
					"Rejected Stand gained manual control or lost its keep-position state");
			assertOtherControlRetained(helper, fixture, otherTarget);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void staleControllerToggleExitsWithoutReadmission(GameTestHelper helper) {
		try (Fixture fixture = new Fixture(helper.getLevel())) {
			fixture.initialize(helper, true);
			AdmissionStand stand = (AdmissionStand) fixture.stand;
			EntityComponentController.setControlTarget(fixture.user, stand, "stand");
			helper.assertTrue(!stand.isManuallyControlled()
					&& EntityComponentController.getControlTarget(fixture.user) == stand,
					"Fixture did not establish the rejected-admission stale controller pair");
			fixture.toggle.onClick(helper.getLevel(), fixture.user, null);
			assertControlPair(helper, fixture, false);
			helper.assertTrue(stand.admissionCalls == 0 && stand.releaseCalls == 1,
					"Stale exit retried admission or recursively invoked the release setter");

			fixture.toggle.onClick(helper.getLevel(), fixture.user, null);
			assertControlPair(helper, fixture, false);
			helper.assertTrue(stand.admissionCalls == 1 && stand.releaseCalls == 1,
					"A later rejected entry recreated the stale controller or replayed exit");
			StandEntityManualControlToggle.off(helper.getLevel(), stand, false);
			assertControlPair(helper, fixture, false);
			helper.assertTrue(stand.releaseCalls == 2, "Idempotent exit recursively repeated release");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void manualExitDoesNotClearAnotherControlledEntity(GameTestHelper helper) {
		try (Fixture fixture = new Fixture(helper.getLevel())) {
			fixture.initialize(helper, false);
			StandEntityManualControlToggle.on(helper.getLevel(), fixture.stand);
			assertControlPair(helper, fixture, true);
			Cow otherTarget = fixture.createOtherTarget(helper);
			EntityComponentController.setControlTarget(fixture.user, otherTarget, "mob");
			StandEntityManualControlToggle.off(helper.getLevel(), fixture.stand, false);
			helper.assertTrue(!fixture.stand.isManuallyControlled()
					&& EntityComponentController.getCurrentController(fixture.stand) == null,
					"Manual exit did not clear the Stand-side state");
			assertOtherControlRetained(helper, fixture, otherTarget);
			StandEntityManualControlToggle.off(helper.getLevel(), fixture.stand, true);
			assertOtherControlRetained(helper, fixture, otherTarget);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void ordinaryRetractionAcceptsUnsummonInput(GameTestHelper helper) {
		try (Fixture fixture = new Fixture(helper.getLevel())) {
			fixture.initialize(helper, false);
			fixture.stand.moveTo(fixture.user.position().add(5, 0, 0));
			fixture.stand.retract();
			helper.assertTrue(fixture.stand.isBeingRetracted() && !fixture.stand.isManuallyControlled()
					&& !fixture.stand.isCloseToUser() && fixture.stand.getCurStandAction() == null,
					"Fixture did not enter distant ordinary retraction");
			fixture.stand.onUnsummonUserInput();
			assertUnsummonAction(helper, fixture.stand);

			fixture.stand.copyPosition(fixture.user);
			int duration = fixture.stand.getUnsummonDuration();
			for (int tick = 0; tick <= duration && !fixture.stand.isRemoved(); tick++) {
				LivingComponentAction.getComponent(fixture.stand).tick();
			}
			helper.assertTrue(fixture.stand.isRemoved() && fixture.power.getSummonedStandEntity() == null,
					"Accepted ordinary-retraction unsummon did not finish after reaching the user");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void repeatedUnsummonInputDoesNotRestartAction(GameTestHelper helper) {
		try (Fixture fixture = new Fixture(helper.getLevel())) {
			fixture.initialize(helper, false);
			fixture.stand.onUnsummonUserInput();
			assertUnsummonAction(helper, fixture.stand);
			var action = fixture.stand.getCurStandAction();
			LivingComponentAction.getComponent(fixture.stand).tick();
			float phaseTick = action.getPhaseTick();
			helper.assertTrue(phaseTick > 0, "Unsummon action did not advance before repeated input");
			fixture.stand.onUnsummonUserInput();
			helper.assertTrue(fixture.stand.getCurStandAction() == action && action.getPhaseTick() == phaseTick,
					"Repeated input replaced or restarted an existing unsummon action");
			fixture.stand.retract();
			fixture.stand.onUnsummonUserInput();
			helper.assertTrue(fixture.stand.getCurStandAction() == action && action.getPhaseTick() == phaseTick,
					"Retraction state allowed repeated input to restart an existing unsummon action");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void manualRetractionInputStillCancelsUnsummon(GameTestHelper helper) {
		try (Fixture fixture = new Fixture(helper.getLevel())) {
			fixture.initialize(helper, false);
			StandEntityManualControlToggle.on(helper.getLevel(), fixture.stand);
			assertControlPair(helper, fixture, true);
			fixture.stand.moveTo(fixture.user.position().add(5, 0, 0));
			fixture.stand.retractAndUnsummon();
			helper.assertTrue(fixture.stand.isBeingRetracted(), "Manual unsummon did not start retraction");
			assertUnsummonAction(helper, fixture.stand);
			fixture.stand.onUnsummonUserInput();
			helper.assertTrue(!fixture.stand.isBeingRetracted() && fixture.stand.getCurStandAction() == null,
					"Second manual-control input did not cancel retraction and unsummon");
			assertControlPair(helper, fixture, true);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void heldAbilityStillBlocksRetractionUnsummon(GameTestHelper helper) {
		try (Fixture fixture = new Fixture(helper.getLevel())) {
			fixture.initialize(helper, false);
			var barrage = fixture.power.getAbility("barrage");
			helper.assertTrue(barrage != null, "Missing production barrage ability");
			short key = 1;
			var held = AbilityInput.keyPress(key, barrage, fixture.user, null,
					InputMethod.HOLD, 0, BufferingState.clickOnly(), barrage.abilityId);
			var inputState = fixture.user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			var action = fixture.stand.getCurStandAction();
			helper.assertTrue(held != null && held.action != null && inputState.heldKeys.get(key) == held
					&& action != null && action.ability == barrage,
					"Production barrage press did not establish the held-ability fixture");
			fixture.stand.moveTo(fixture.user.position().add(5, 0, 0));
			fixture.stand.retract();
			fixture.stand.onUnsummonUserInput();
			helper.assertTrue(fixture.stand.isBeingRetracted() && fixture.stand.getCurStandAction() == action
					&& inputState.heldKeys.get(key) == held,
					"Unsummon input bypassed the held-ability guard during ordinary retraction");
			helper.assertTrue(AbilityInput.keyReleaseFromNetwork(key, fixture.user, held.generation)
					== AbilityInput.ReleaseResult.RELEASED && inputState.heldKeys.isEmpty(),
					"Production release did not clear the held-ability input");
			fixture.stand.onUnsummonUserInput();
			assertUnsummonAction(helper, fixture.stand);
		}
		helper.succeed();
	}

	private static void assertUnsummonAction(GameTestHelper helper, StandEntity stand) {
		var action = stand.getCurStandAction();
		helper.assertTrue(action != null && action.ability == ModSpecialActions.STAND_UNSUMMON.get(),
				"Expected production unsummon action: retracted=" + stand.isBeingRetracted()
						+ ", manual=" + stand.isManuallyControlled() + ", action=" + action);
	}

	private static void assertControlPair(GameTestHelper helper, Fixture fixture, boolean controlled) {
		Entity target = EntityComponentController.getControlTarget(fixture.user);
		EntityComponentController targetController = EntityComponentController.getCurrentController(fixture.stand);
		helper.assertTrue(fixture.stand.getStandFlag(StandFlag.MANUAL_CONTROL) == controlled
				&& fixture.stand.isManuallyControlled() == controlled,
				"Manual-control flag does not match the expected admission state");
		helper.assertTrue(controlled ? target == fixture.stand : target == null,
				"Owner controller target does not match the manual-control flag");
		helper.assertTrue(controlled
				? targetController != null && targetController.getControllingEntity() == fixture.user
						&& "stand".equals(targetController.controllerType)
				: targetController == null,
				"Target-side controller owner/type does not match the manual-control flag");
	}

	private static void assertOtherControlRetained(GameTestHelper helper, Fixture fixture, Cow otherTarget) {
		EntityComponentController controller = EntityComponentController.getCurrentController(otherTarget);
		helper.assertTrue(EntityComponentController.getControlTarget(fixture.user) == otherTarget
				&& controller != null && controller.getControllingEntity() == fixture.user
				&& "mob".equals(controller.controllerType),
				"Stand admission or exit cleared another entity's control relationship");
	}

	private static final class Fixture implements AutoCloseable {
		final FakePlayer user;
		StandPower power;
		StandEntity stand;
		StandEntityManualControlToggle toggle;
		Cow otherTarget;

		Fixture(ServerLevel level) {
			user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ManualAdmission"));
		}

		void initialize(GameTestHelper helper, boolean rejectsAdmission) {
			Vec3 position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(position.x, position.y, position.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add manual-admission owner");
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant manual-admission Stand");
			stand = rejectsAdmission ? new AdmissionStand(helper.getLevel())
					: ModEntityTypes.HUMANOID_STAND.get().create(helper.getLevel());
			helper.assertTrue(stand != null, "Could not create manual-admission Stand entity");
			stand.withStandType(type);
			stand.copyPosition(user);
			power.setSummonedStand(stand);
			helper.assertTrue(stand.getUser() == user, "Stand owner did not resolve from the actual server level");
			var ability = power.getAbility("manual_control");
			helper.assertTrue(ability instanceof StandEntityManualControlToggle, "Missing production manual-control ability");
			toggle = (StandEntityManualControlToggle) ability;
		}

		Cow createOtherTarget(GameTestHelper helper) {
			otherTarget = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(otherTarget != null, "Could not create unrelated mob-control target");
			return otherTarget;
		}

		@Override
		public void close() {
			if (user.hasData(ModDataAttachmentTypes.CONTROLLER)) {
				user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();
			}
			if (stand != null) {
				StandEntityManualControlToggle.off(user.level(), stand, false);
				if (power != null) power.setSummonedStand(null);
				stand.discard();
			}
			if (otherTarget != null) otherTarget.discard();
			user.discard();
		}
	}

	private static final class AdmissionStand extends StandEntity {
		int admissionCalls;
		int releaseCalls;
		private boolean settingControl;

		AdmissionStand(Level level) {
			super(ModEntityTypes.HUMANOID_STAND.get(), level);
		}

		@Override
		public void setManuallyControlled(boolean value) {
			if (settingControl) throw new AssertionError("Recursive manual-control setter");
			settingControl = true;
			try {
				if (value) {
					admissionCalls++;
					return;
				}
				releaseCalls++;
				super.setManuallyControlled(false);
			}
			finally {
				settingControl = false;
			}
		}
	}
}
