package com.github.standobyte.jojo.gametest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.github.standobyte.jojo.api.stand.StandPowerTransitions;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.mechanics.KnockbackCollisionImpact;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.SyncType;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojo.subsystems.entity_grab.LivingComponentGrab;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityGrabThrowAbility;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrozenGrabThrowGameTests {

	private FrozenGrabThrowGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void frozenGrabRelocatesAnchorAndPlainReleaseClearsOldMomentum(GameTestHelper helper) {
		for (boolean originalNoAi : new boolean[] {false, true}) {
			try (Fixture f = new Fixture(helper, "anchor_" + originalNoAi)) {
				f.target.setNoAi(originalNoAi);
				f.target.setDeltaMovement(0.4, 0.2, -0.1);
				f.target.fallDistance = 7;
				int stop = f.stop();
				Vec3 original = f.target.position();
				f.grab();
				Vec3 held = f.target.position();
				helper.assertTrue(held.distanceToSqr(original) > 0.1, "Grab did not relocate the frozen target");
				f.frozenTick();
				f.assertPosition(held, "Frozen tick restored the pre-grab anchor");
				f.stand.setPos(f.stand.position().add(1.5, 0.5, 0));
				f.targetGrab.setGrabbedPos();
				held = f.target.position();
				f.state.reconcileFrozenEntity(f.target);
				f.assertPosition(held, "Reconciliation lost the relocated grip anchor");
				f.standGrab.setGrabTarget(null);
				f.frozenTick();
				f.resume(stop);
				f.assertPosition(held, "Plain release snapped back after resume");
				f.assertMotion(Vec3.ZERO, "Plain release resurrected pre-grab momentum");
				helper.assertTrue(f.target.fallDistance == 0 && f.target.isNoAi() == originalNoAi,
						"Grab relocation did not preserve the original AI state or clear fall distance");
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void frozenThrowResumesOnceWithCollisionImpact(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "resume")) {
			int stop = f.stop();
			f.grab();
			Vec3 earlierGrip = f.target.position();
			f.stand.setPos(f.stand.position().add(2, 0, 0));
			Vec3 expectedMotion = f.throwTarget();
			Vec3 release = f.target.position();
			helper.assertTrue(release.distanceToSqr(earlierGrip) > 1,
					"Throw release did not capture the final authoritative grip position");
			f.assertDeferred();
			for (int tick = 0; tick < 3; tick++) {
				f.frozenTick();
				f.assertPosition(release, "Queued throw moved or snapped back while frozen");
				f.assertDeferred();
			}
			f.resume(stop);
			f.assertMotion(expectedMotion, "Resume lost the throw vector");
			f.assertPosition(release, "Resume changed the release position before flight");
			f.assertImpact();
			helper.assertTrue(!f.target.isNoAi(), "Resume lost the target's original AI state");
			f.target.getData(ModDataAttachmentTypes.DATA_EVENT_HELPER.get()).onTick();
			f.assertImpact();
			Vec3 laterMotion = expectedMotion.scale(0.5);
			f.target.setDeltaMovement(laterMotion);
			f.target.hurtMarked = false;
			f.state.reconcileFrozenEntity(f.target);
			f.state.removeInstance(stop);
			f.assertMotion(laterMotion, "Throw callback ran more than once");
			helper.assertTrue(!f.target.hurtMarked, "Duplicate resume resynchronized the old throw");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void normalTimeGrabThrowRemainsImmediate(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "normal")) {
			helper.assertTrue(!f.state.shouldFreeze(f.target), "Normal-time control is frozen");
			f.grab();
			Vec3 release = f.target.position();
			Vec3 expectedMotion = f.throwTarget();
			f.assertMotion(expectedMotion, "Normal-time throw was delayed");
			f.assertPosition(release, "Normal-time throw changed its release position");
			f.assertImpact();
			helper.assertTrue(f.standGrab.getGrabbedEntity() == null && !f.targetGrab.isGrabbed(),
					"Normal-time throw did not release both sides of the grab");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void regrabThenReleaseCancelsQueuedThrow(GameTestHelper helper) {
		for (boolean releaseAgain : new boolean[] {false, true}) {
			try (Fixture f = new Fixture(helper, "regrab_release_" + releaseAgain)) {
				int stop = f.stop();
				f.grab();
				f.throwTarget();
				f.grab();
				if (releaseAgain) f.standGrab.setGrabTarget(null);
				f.resume(stop);
				f.assertMotion(Vec3.ZERO, "Second grab replayed the first throw");
				helper.assertTrue(!f.impact.isActive() && !f.target.hurtMarked,
						"Canceled throw restored collision impact or sent throw motion");
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void regrabKeepsOnlyTheLatestThrow(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "regrab_throw")) {
			int stop = f.stop();
			f.grab();
			Vec3 firstMotion = f.throwTarget();
			f.grab();
			f.stand.setYRot(f.stand.getYRot() + 90);
			Vec3 latestMotion = f.throwTarget();
			helper.assertTrue(firstMotion.distanceToSqr(latestMotion) > 1, "Regrab control used identical throws");
			f.resume(stop);
			f.assertMotion(latestMotion, "Regrab discarded the valid latest throw");
			f.assertImpact();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void deadOrRemovedTargetDoesNotResumeQueuedThrow(GameTestHelper helper) {
		for (boolean remove : new boolean[] {false, true}) {
			try (Fixture f = new Fixture(helper, "invalid_" + remove)) {
				int stop = f.stop();
				f.grab();
				f.throwTarget();
				if (remove) f.target.discard();
				else f.target.setHealth(0);
				f.resume(stop);
				f.assertMotion(Vec3.ZERO, "Dead or removed target received a queued throw");
				helper.assertTrue(!f.impact.isActive() && !f.target.hurtMarked,
						"Dead or removed target received throw impact or motion synchronization");
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void overlappingStopsDeferThrowUntilTheLastResume(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "overlap")) {
			int first = f.stop();
			int second = f.stop();
			f.grab();
			Vec3 expectedMotion = f.throwTarget();
			Vec3 release = f.target.position();
			f.resume(first);
			f.frozenTick();
			f.assertDeferred();
			f.assertPosition(release, "Removing one overlapping stop changed the release anchor");
			f.resume(second);
			f.assertMotion(expectedMotion, "Final overlapping stop did not resume the throw");
			f.assertImpact();
		}
		helper.succeed();
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final StandType standType;
		private final StandPower power;
		private final StandEntity stand;
		private final LivingComponentAction component;
		private final Cow target;
		private final LivingComponentGrab standGrab;
		private final LivingComponentGrab targetGrab;
		private final KnockbackCollisionImpact impact;
		private final TimeStopState state;
		private final List<Integer> stops = new ArrayList<>();
		private int nextStopId;

		private Fixture(GameTestHelper helper, String name) {
			this.helper = helper;
			String profileName = "FrozenGrab_" + name;
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
					UUID.nameUUIDFromBytes(profileName.getBytes(StandardCharsets.US_ASCII)), profileName));
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z);
			user.getAbilities().instabuild = true;
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add frozen-grab player");
			power = PowerClass.STAND.attachGet(user);
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(standType != null, "Missing Star Platinum Stand type");
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant frozen-grab Stand");
			helper.assertTrue(standType.summon(user, power), "Could not summon frozen-grab Stand");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Missing frozen-grab Stand entity");
			stand.moveTo(origin.x, origin.y, origin.z, 35, -15);
			stand.yBodyRot = stand.getYRot();
			component = LivingComponentAction.getComponent(stand);
			target = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(target != null, "Could not create frozen-grab target");
			target.moveTo(origin.x, origin.y, origin.z + 2);
			helper.assertTrue(helper.getLevel().addFreshEntity(target), "Could not add frozen-grab target");
			standGrab = stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
			targetGrab = target.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
			impact = KnockbackCollisionImpact.getHandler(target);
			state = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
			nextStopId = -target.getId() * 4;
			power.setStamina(power.getMaxStamina());
		}

		private int stop() {
			int id = nextStopId--;
			helper.assertTrue(state.tryPutInstance(new TimeStopState.Instance(id, 200, 200,
					new ChunkPos(target.blockPosition()), 2, user.getId(), "frozen_grab_test", user.getId(), 0)),
					"Could not start frozen-grab time stop");
			stops.add(id);
			helper.assertTrue(state.shouldFreeze(target), "Grab victim did not freeze");
			return id;
		}

		private void resume(int id) {
			state.removeInstance(id);
			stops.remove(Integer.valueOf(id));
		}

		private void grab() {
			standGrab.setGrabTarget(target);
			targetGrab.setGrabbedPos();
			helper.assertTrue(targetGrab.isGrabbed(), "Could not grab target");
		}

		private Vec3 throwTarget() {
			Ability ability = power.getAbility("grab_throw");
			helper.assertTrue(ability instanceof EntityActionType, "Missing grab-throw production action");
			EntityActionInstance action = ((EntityActionType) ability)
					.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			helper.assertTrue(action instanceof StandEntityGrabThrowAbility.StandEntityGrabThrow,
					"Grab-throw fixture resolved the wrong production action");
			component.setAction(action, user, SyncType.NO_SYNC);
			Vec3 throwVec = stand.getLookAngle().scale(2);
			target.hurtMarked = false;
			action.actionPerformEnd();
			helper.assertTrue(!targetGrab.isGrabbed() && standGrab.getGrabbedEntity() == null,
					"Production grab throw did not release the target");
			return throwVec;
		}

		private void frozenTick() {
			helper.assertTrue(state.interruptTickEarly(target), "Frozen target gained ticking permission");
			target.getData(ModDataAttachmentTypes.DATA_EVENT_HELPER.get()).onTick();
			state.reconcileFrozenEntity(target);
		}

		private void assertDeferred() {
			assertMotion(Vec3.ZERO, "Throw applied motion before time resumed");
			helper.assertTrue(!target.hurtMarked && !impact.isActive(),
					"Throw armed motion synchronization or collision impact while frozen");
		}

		private void assertImpact() {
			helper.assertTrue(target.hurtMarked && impact.isActive() && impact.getKnockbackImpactStrength() > 0,
					"Resumed throw lost motion synchronization or collision impact");
			float expected = Math.max(Math.min((float) stand.getAttackDamage() * 0.175F, 10) - 0.5F, 0);
			float actual = impact.serializeNBT(helper.getLevel().registryAccess()).getFloat("ExplosionRadius");
			helper.assertTrue(Math.abs(actual - expected) < 0.0001F, "Throw lost its collision explosion metadata");
		}

		private void assertPosition(Vec3 expected, String message) {
			helper.assertTrue(target.position().distanceToSqr(expected) < 1E-10, message);
		}

		private void assertMotion(Vec3 expected, String message) {
			helper.assertTrue(target.getDeltaMovement().distanceToSqr(expected) < 1E-10, message);
		}

		@Override
		public void close() {
			standGrab.setGrabTarget(null);
			target.discard();
			for (int id : stops) state.removeInstance(id);
			if (power.isSummoned()) standType.forceUnsummon(user, power);
			user.discard();
		}
	}
}
