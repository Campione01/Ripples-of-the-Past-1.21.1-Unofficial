package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.HamonSendoOverdriveEntity;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 PowerBaseImpl.tickHeldAction re-ran checkRequirements on every tick of a held action (the key still down):
 * a failed check (a filled hand, no soap, a stun, a lost Stand part) ended the hold with stopHeldAction(false), which fired nothing
 * except Sendo Overdrive's wave. StandEntityMeleeBarrage.stopOnHeavyAttack let a heavy attack stop a barrage, and
 * Action.onPerform reset a player's attack strength for a swingHand technique.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeldConditionRecheckGameTests {
	private static final short KEY = 7;

	private HeldConditionRecheckGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void filledHandDropsSyoChargeAndRefunds(GameTestHelper helper) {
		try (HamonFixture f = new HamonFixture(helper, "HeldSyoHand", ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE.get())) {
			float energy = f.hamon.getEnergy();
			EntityActionInstance action = f.start("sunlight_yellow_overdrive", true);
			f.tick(12);
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.WINDUP,
					"S.Y.O. is not charging");
			helper.assertTrue(f.hamon.getEnergy() < energy, "S.Y.O. charge spent no energy");
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			f.tick(1);
			helper.assertTrue(action.isOver() && f.component.getAction() == null,
					"a filled main hand did not end the held S.Y.O. charge");
			assertClose(helper, f.hamon.getEnergy(), energy, "the dropped S.Y.O. charge did not refund its energy");

			// An action no key holds (set by code, as the auto-guard is) is not re-checked.
			EntityActionInstance unheld = f.start("sunlight_yellow_overdrive", false);
			f.tick(3);
			helper.assertTrue(f.component.getAction() == unheld && unheld.getPhase() == ActionPhase.WINDUP,
					"an action no key holds was stopped by the held check");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void stunDropsHeldHamonCharge(GameTestHelper helper) {
		try (HamonFixture f = new HamonFixture(helper, "HeldSyoStun", ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE.get())) {
			EntityActionInstance action = f.start("sunlight_yellow_overdrive", true);
			f.tick(5);
			helper.assertTrue(action.getPhase() == ActionPhase.WINDUP, "S.Y.O. is not charging");
			f.user.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
			f.tick(1);
			helper.assertTrue(action.isOver() && f.component.getAction() == null,
					"a stun did not end the held S.Y.O. charge");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void lostSoapDropsBubbleBarrierCharge(GameTestHelper helper) {
		try (HamonFixture f = new HamonFixture(helper, "HeldBarrierSoap", ModHamonSkills.BUBBLE_BARRIER.get())) {
			f.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SOAP.get()));
			EntityActionInstance action = f.start("bubble_barrier", true);
			f.tick(5);
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.WINDUP,
					"the Bubble Barrier is not charging");
			f.user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			f.tick(1);
			helper.assertTrue(action.isOver() && f.component.getAction() == null,
					"losing the soap did not end the held Bubble Barrier charge");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void filledHandEndsHamonHealing(GameTestHelper helper) {
		try (HamonFixture f = new HamonFixture(helper, "HeldHealingHand", ModHamonSkills.HEALING.get())) {
			EntityActionAbility healing = f.ability("hamon_healing");
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			helper.assertTrue(!healing.checkSpecificConditions(f.power).isPositive(),
					"1.16 Hamon Healing needed a free main hand to start");
			f.user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			helper.assertTrue(healing.checkSpecificConditions(f.power).isPositive(), "Hamon Healing cannot start");
			EntityActionInstance action = f.start("hamon_healing", true);
			f.tick(5);
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.PERFORM,
					"Hamon Healing is not being held");
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			f.tick(1);
			helper.assertTrue(action.isOver() && f.component.getAction() == null,
					"a filled main hand did not end the held Hamon Healing");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void failedCheckStillSendsSendoWave(GameTestHelper helper) {
		try (HamonFixture f = new HamonFixture(helper, "HeldSendoHand", ModHamonSkills.SENDO_OVERDRIVE.get())) {
			for (int y = 2; y <= 6; y++) {
				helper.setBlock(new BlockPos(2, y, 5), Blocks.STONE.defaultBlockState());
			}
			AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12.0D);
			int wavesBefore = helper.getLevel().getEntitiesOfClass(HamonSendoOverdriveEntity.class, area).size();
			EntityActionInstance action = f.start("sendo_overdrive", true);
			f.tick(5);
			helper.assertTrue(action.getPhase() == ActionPhase.WINDUP, "Sendo Overdrive is not charging");
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			f.tick(1);
			helper.assertTrue(action.isOver() || action.getPhase() == ActionPhase.PERFORM,
					"a failed check did not end the Sendo Overdrive hold");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(HamonSendoOverdriveEntity.class, area).size() > wavesBefore,
					"1.16 Sendo Overdrive sent its wave on any stop of the hold, a failed check included");
			helper.succeed();
		}
	}

	// 1.16 checkConditions refused a user frozen in someone else's stopped time (!canUpdate), and stopHeldAction(true)
	// ran it on release: a charge released while frozen is dropped, and nothing fires when time resumes.
	@GameTest(template = "empty", timeoutTicks = 80, batch = GameTestBatches.TIME_STOP)
	public static void releaseWhileFrozenInStoppedTimeDropsCharge(GameTestHelper helper) {
		TimeStopState timeStops = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
		try (HamonFixture f = new HamonFixture(helper, "HeldSyoFrozen", ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE.get())) {
			EntityActionAbility syo = f.ability("sunlight_yellow_overdrive");
			int stopId = -(f.user.getId() * 4 + 3);
			try {
				EntityActionInstance action = chargeSyo(helper, f, syo);
				// A stop owned by no one: the user is not its stopper.
				helper.assertTrue(timeStops.tryPutInstance(new TimeStopState.Instance(stopId, 200, 200,
						new ChunkPos(f.user.blockPosition()), 1, -1, "held_release_frozen_test"))
						&& timeStops.shouldFreeze(f.user), "the user did not freeze in the stopped time");
				helper.assertTrue(!syo.canFireReleasedHold(action),
						"the release recheck let a user frozen in stopped time fire");
				releaseKey(f.user, action);
				helper.assertTrue(action.isOver(),
						"a charge released while frozen in stopped time was not dropped: phase=" + action.getPhase());
				// The paused component clears it while time is still stopped.
				f.tick(1);
				helper.assertTrue(f.component.getAction() == null, "the dropped charge was not cleared while frozen");
				// Time resumes: the dropped charge stays dropped.
				timeStops.removeInstance(stopId);
				f.tick(5);
				helper.assertTrue(action.isOver() && f.component.getAction() == null,
						"the charge released while frozen fired after time resumed");
				// Control: the same release out of stopped time punches.
				f.hamon.setEnergy(f.hamon.getMaxEnergy());
				EntityActionInstance control = chargeSyo(helper, f, syo);
				releaseKey(f.user, control);
				helper.assertTrue(control.getPhase() == ActionPhase.PERFORM,
						"the S.Y.O. release did not punch: phase=" + control.getPhase());
			}
			finally {
				// Removed before the level ticks, so no neighbouring test is frozen.
				timeStops.removeInstance(stopId);
			}
			helper.succeed();
		}
	}

	// S.Y.O. held by its key, past its 10-tick minimum charge and short of its 40-tick full charge
	private static EntityActionInstance chargeSyo(GameTestHelper helper, HamonFixture f, EntityActionAbility syo) {
		EntityActionInstance action = f.start("sunlight_yellow_overdrive", true);
		for (int tick = 0; tick < 30 && !(action.getPhase() == ActionPhase.WINDUP && action.getPhaseTick() >= 12); tick++) {
			f.tick(1);
		}
		helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.WINDUP
				&& action.getPhaseTick() >= 12 && syo.canFireReleasedHold(action),
				"S.Y.O. is not charged past its minimum: phase=" + action.getPhase() + " phaseTick=" + action.getPhaseTick());
		return action;
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void swingHandTechniqueResetsAttackStrength(GameTestHelper helper) {
		try (HamonFixture f = new HamonFixture(helper, "SwingHandCutter", ModHamonSkills.HAMON_CUTTER.get())) {
			helper.assertTrue(f.ability("hamon_cutter").resetsAttackStrengthOnPerform(),
					"Hamon Cutter (1.16 swingHand) must reset the attack strength");
			helper.assertTrue(!f.ability("hamon_overdrive").resetsAttackStrengthOnPerform(),
					"Hamon Overdrive punches with the user (1.16 withUserPunch) and must not reset it");
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOAP.get()));
			setAttackStrengthTicker(f.user, 100);
			EntityActionInstance action = f.start("hamon_cutter", false);
			for (int tick = 0; tick < 12 && action.getPhase() != ActionPhase.PERFORM && !action.isOver(); tick++) {
				f.tick(1);
			}
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM, "Hamon Cutter did not reach its perform phase");
			helper.assertTrue(getAttackStrengthTicker(f.user) >= 100, "the attack strength was reset before the technique fired");
			// The perform phase's first tick is 1.16's onPerform.
			f.tick(1);
			helper.assertTrue(!action.isOver() && action.getPhase() == ActionPhase.PERFORM, "Hamon Cutter did not perform");
			helper.assertTrue(getAttackStrengthTicker(f.user) <= 1,
					"performing Hamon Cutter did not reset the attack strength");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void heavyAttackAndStunStopStandBarrage(GameTestHelper helper) {
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HeldBarrageStop"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		try {
			helper.assertTrue(standType != null, "Missing Star Platinum Stand type");
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add barrage player");
			power = PowerClass.STAND.attachGet(user);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
			helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
					"Could not grant Star Platinum: " + inserted.status());
			helper.assertTrue(standType.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Star Platinum entity is missing");
			LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
			Ability barrage = power.getAbility("barrage");
			helper.assertTrue(barrage instanceof EntityActionType, "Star Platinum barrage is not an entity action");

			EntityActionInstance hit = startBarrage(helper, (EntityActionType) barrage, user, stand, standAction);
			EntityActionAbility.onHitByHeavyAttack(stand);
			helper.assertTrue(hit.getPhase() == ActionPhase.RECOVERY,
					"a heavy attack that hurt the Stand did not send its barrage into recovery");

			EntityActionInstance stunned = startBarrage(helper, (EntityActionType) barrage, user, stand, standAction);
			holdByKey(user, stunned, PowerClass.STAND);
			stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
			standAction.tick();
			helper.assertTrue(stunned.getPhase() == ActionPhase.RECOVERY,
					"a stun on the Stand did not end the held barrage");
			stand.removeEffect(ModStatusEffects.STUN);
			helper.succeed();
		}
		finally {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	// 1.16 StandAction.checkConditions (partsRequired) ran on every held tick and before a release fired.
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void lostStandArmsEndHeldBarrage(GameTestHelper helper) {
		try (StandFixture f = new StandFixture(helper, "HeldBarrageArms", "star_platinum")) {
			EntityActionAbility barrage = f.ability("barrage");
			EntityActionInstance held = startBarrage(helper, barrage, f.user, f.stand, f.standAction);
			holdByKey(f.user, held, PowerClass.STAND);
			f.standAction.tick();
			helper.assertTrue(held.getPhase() == ActionPhase.PERFORM && barrage.canFireReleasedHold(held),
					"the held barrage did not go on while the Stand had its arms" + f.state(barrage, held));
			f.instance.removePart(StandPart.ARMS);
			helper.assertTrue(!barrage.canFireReleasedHold(held),
					"the release recheck passed after the Stand lost its arms" + f.state(barrage, held));
			f.standAction.tick();
			helper.assertTrue(held.getPhase() == ActionPhase.RECOVERY,
					"losing the Stand's arms did not end the held barrage" + f.state(barrage, held));
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void lostStandBodyDropsCrossfireCharge(GameTestHelper helper) {
		try (StandFixture f = new StandFixture(helper, "HeldCrossfireBody", "magicians_red")) {
			EntityActionAbility crossfire = f.ability("crossfire_hurricane");
			EntityActionInstance charge = crossfire.initActionOnAbilityUse(helper.getLevel(), f.user, f.stand, null);
			f.standAction.setAction(charge, f.user, SyncType.NO_SYNC);
			holdByKey(f.user, charge, PowerClass.STAND);
			f.tick(5);
			// The phase tick shows the charge really ticked (it starts in BUTTON_CHARGE).
			helper.assertTrue(f.standAction.getAction() == charge && charge.getPhase() == ActionPhase.BUTTON_CHARGE
					&& charge.getPhaseTick() > 0, "Crossfire Hurricane is not charging" + f.state(crossfire, charge));
			f.instance.removePart(StandPart.MAIN_BODY);
			f.tick(1);
			helper.assertTrue(charge.isOver() && f.standAction.getAction() == null,
					"losing the Stand's body did not drop the held Crossfire Hurricane charge" + f.state(crossfire, charge));
			// Releasing after the drop fires nothing, nor does the charge end (20 ticks) come.
			charge.onKeyRelease(f.user);
			f.tick(25);
			helper.assertTrue(charge.isOver() && f.standAction.getAction() == null,
					"the dropped Crossfire Hurricane charge still fired" + f.state(crossfire, charge));
			helper.succeed();
		}
	}

	// 1.16 stopHeldAction(true) rechecked before a released hold fired; the port's early time stop release too.
	@GameTest(template = "empty", timeoutTicks = 80, batch = GameTestBatches.TIME_STOP)
	public static void lostStandBodyStopsEarlyTimeStopRelease(GameTestHelper helper) {
		TimeStopState timeStops = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
		try (StandFixture f = new StandFixture(helper, "HeldTimeStopBody", "star_platinum")) {
			EntityActionAbility timeStop = f.ability("time_stop");
			int stopId = f.user.getId();
			try {
				// Released on the tick the body is lost: the per-tick recheck never saw it.
				EntityActionInstance charge = chargeTimeStop(helper, f, timeStop);
				f.instance.removePart(StandPart.MAIN_BODY);
				releaseKey(f.user, charge);
				helper.assertTrue(timeStops.getInstance(stopId).isEmpty() && f.standAction.getAction() == null,
						"an early release without the Stand's body still stopped time" + f.state(timeStop, charge));
				// Control: with the body back the same early release stops time.
				f.instance.addPart(StandPart.MAIN_BODY);
				EntityActionInstance control = chargeTimeStop(helper, f, timeStop);
				releaseKey(f.user, control);
				helper.assertTrue(timeStops.getInstance(stopId).isPresent() && f.standAction.getAction() == null,
						"the early time stop release did not stop time" + f.state(timeStop, control));
			}
			finally {
				// Removed before the level ticks, so no neighbouring test is frozen.
				timeStops.removeInstance(stopId);
				f.user.removeEffect(ModStatusEffects.TIME_STOP);
			}
			helper.succeed();
		}
	}

	// Port-own charged heavy punch under the 1.16 rule: a released hold fires only if it passes the recheck
	// (stopHeldAction(true)), and a failed held check (stopHeldAction(false)) fires nothing.
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void failedRecheckDropsChargedHeavyRelease(GameTestHelper helper) {
		try (StandFixture f = new StandFixture(helper, "HeldChargedHeavy", "star_platinum")) {
			EntityActionAbility heavy = f.ability("heavy_charged");
			// Released in the held windup on the tick the Stand is stunned: the per-tick recheck never saw it.
			EntityActionInstance windup = chargeHeavy(helper, f, heavy, true);
			f.stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
			helper.assertTrue(windup.getPhase() == ActionPhase.WINDUP && !heavy.canFireReleasedHold(windup),
					"the stun did not fail the release recheck" + f.state(heavy, windup));
			releaseKey(f.user, windup);
			helper.assertTrue(windup.isOver(),
					"a windup release that failed the recheck still punched" + f.state(heavy, windup));
			// A stun during the held windup ends the hold without the punch.
			f.stand.removeEffect(ModStatusEffects.STUN);
			EntityActionInstance stunned = chargeHeavy(helper, f, heavy, true);
			f.stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
			f.tick(1);
			helper.assertTrue(stunned.isOver() && f.standAction.getAction() == null,
					"a stun during the held windup still punched" + f.state(heavy, stunned));
			f.stand.removeEffect(ModStatusEffects.STUN);
			// Control: the same windup release punches.
			EntityActionInstance fired = chargeHeavy(helper, f, heavy, true);
			releaseKey(f.user, fired);
			helper.assertTrue(fired.getPhase() == ActionPhase.PERFORM,
					"the windup release did not punch" + f.state(heavy, fired));

			// Released during the button charge, it fires when the charge ends, with the key already up.
			ServerPlayer player = (ServerPlayer) f.user;
			try {
				EntityActionInstance early = chargeHeavy(helper, f, heavy, false);
				releaseKey(f.user, early);
				player.setGameMode(GameType.SPECTATOR);
				tickOutOfButtonCharge(f, early);
				helper.assertTrue(early.isOver() && f.standAction.getAction() == null,
						"a charge released early still punched after the user lost the power" + f.state(heavy, early));
			}
			finally {
				player.setGameMode(GameType.SURVIVAL);
			}
			// Control: the same early release punches when the charge ends.
			EntityActionInstance charged = chargeHeavy(helper, f, heavy, false);
			releaseKey(f.user, charged);
			tickOutOfButtonCharge(f, charged);
			helper.assertTrue(charged.getPhase() == ActionPhase.PERFORM,
					"the early release did not punch when the charge ended" + f.state(heavy, charged));
			helper.succeed();
		}
	}

	// Port-own grab throw under the same rule; its own held condition is the grabbed target.
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void stunOrLostGrabDropsHeldGrabThrow(GameTestHelper helper) {
		try (StandFixture f = new StandFixture(helper, "HeldGrabThrow", "star_platinum")) {
			EntityActionAbility grabThrow = f.ability("grab_throw");
			LivingComponentGrab standGrab = f.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
			Cow target = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(target != null, "Could not create the grab throw target");
			Vec3 targetPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 4)));
			target.moveTo(targetPos.x, targetPos.y, targetPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(target), "Could not add the grab throw target");
			try {
				// Released in the held windup on the tick the Stand is stunned: the per-tick recheck never saw it.
				EntityActionInstance stunned = chargeThrow(helper, f, grabThrow, standGrab, target, true);
				f.stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
				helper.assertTrue(!grabThrow.canFireReleasedHold(stunned),
						"the stun did not fail the grab throw release recheck" + f.state(grabThrow, stunned));
				releaseKey(f.user, stunned);
				helper.assertTrue(stunned.isOver(),
						"a stunned windup release still threw" + f.state(grabThrow, stunned));
				f.stand.removeEffect(ModStatusEffects.STUN);
				helper.assertTrue(standGrab.getGrabbedEntity() == target && target.getDeltaMovement().lengthSqr() < 1.0E-6,
						"the dropped grab throw let go of or moved the target");

				// Released on the tick the grab is lost.
				EntityActionInstance lost = chargeThrow(helper, f, grabThrow, standGrab, target, true);
				standGrab.setGrabTarget(null);
				helper.assertTrue(!grabThrow.canFireReleasedHold(lost),
						"losing the grab target did not fail the release recheck" + f.state(grabThrow, lost));
				releaseKey(f.user, lost);
				helper.assertTrue(lost.isOver(),
						"a windup release without a grab target still threw" + f.state(grabThrow, lost));

				// A grab lost during the held windup ends the hold.
				EntityActionInstance held = chargeThrow(helper, f, grabThrow, standGrab, target, true);
				standGrab.setGrabTarget(null);
				f.tick(1);
				helper.assertTrue(held.isOver() && f.standAction.getAction() == null,
						"losing the grab target did not end the held throw" + f.state(grabThrow, held));

				// Released during the button charge, then the grab is lost before the charge ends.
				EntityActionInstance early = chargeThrow(helper, f, grabThrow, standGrab, target, false);
				releaseKey(f.user, early);
				standGrab.setGrabTarget(null);
				tickPastCharge(f, early);
				helper.assertTrue(early.isOver() && f.standAction.getAction() == null,
						"an early release still threw after the grab target was lost" + f.state(grabThrow, early));
				// Control: the same early release throws when the charge ends.
				EntityActionInstance charged = chargeThrow(helper, f, grabThrow, standGrab, target, false);
				releaseKey(f.user, charged);
				tickPastCharge(f, charged);
				helper.assertTrue(charged.getPhase() == ActionPhase.PERFORM,
						"the early release did not throw when the charge ended" + f.state(grabThrow, charged));

				// Control: a windup release with the target still grabbed throws it.
				EntityActionInstance fired = chargeThrow(helper, f, grabThrow, standGrab, target, true);
				helper.assertTrue(target.getDeltaMovement().lengthSqr() < 1.0E-6, "the target moved before the throw");
				releaseKey(f.user, fired);
				helper.assertTrue(fired.getPhase() == ActionPhase.PERFORM,
						"the windup release did not throw" + f.state(grabThrow, fired));
				for (int tick = 0; tick < 20 && fired.getPhase() == ActionPhase.PERFORM; tick++) {
					f.tick(1);
				}
				helper.assertTrue(standGrab.getGrabbedEntity() == null && target.getDeltaMovement().lengthSqr() > 1,
						"the windup release did not throw the target" + f.state(grabThrow, fired));
			}
			finally {
				standGrab.setGrabTarget(null);
				target.discard();
			}
			helper.succeed();
		}
	}

	// grab_throw with the target grabbed, held by its key, in BUTTON_CHARGE or ticked on into its held WINDUP
	private static EntityActionInstance chargeThrow(GameTestHelper helper, StandFixture f, EntityActionAbility grabThrow,
			LivingComponentGrab standGrab, LivingEntity target, boolean toWindup) {
		f.standAction.setAction(null, f.user, SyncType.NO_SYNC);
		// setGrabTarget drops a target that is already grabbed, even by the same Stand
		if (standGrab.getGrabbedEntity() != target) {
			standGrab.setGrabTarget(target);
		}
		helper.assertTrue(standGrab.getGrabbedEntity() == target, "Could not grab the throw target");
		EntityActionInstance charge = grabThrow.initActionOnAbilityUse(helper.getLevel(), f.user, f.stand, null);
		f.standAction.setAction(charge, f.user, SyncType.NO_SYNC);
		holdByKey(f.user, charge, PowerClass.STAND);
		f.tick(1);
		for (int tick = 0; toWindup && tick < 60 && charge.getPhase() == ActionPhase.BUTTON_CHARGE; tick++) {
			f.tick(1);
		}
		ActionPhase expected = toWindup ? ActionPhase.WINDUP : ActionPhase.BUTTON_CHARGE;
		helper.assertTrue(f.standAction.getAction() == charge && charge.getPhase() == expected,
				"the grab throw is not in " + expected + f.state(grabThrow, charge));
		return charge;
	}

	// past the button charge and the zero-length windup an early release leaves
	private static void tickPastCharge(StandFixture f, EntityActionInstance action) {
		for (int tick = 0; tick < 60 && (action.getPhase() == ActionPhase.BUTTON_CHARGE
				|| action.getPhase() == ActionPhase.WINDUP); tick++) {
			f.tick(1);
		}
	}

	// heavy_charged held by its key, in BUTTON_CHARGE or ticked on into its held WINDUP
	private static EntityActionInstance chargeHeavy(GameTestHelper helper, StandFixture f, EntityActionAbility heavy,
			boolean toWindup) {
		f.standAction.setAction(null, f.user, SyncType.NO_SYNC);
		EntityActionInstance charge = heavy.initActionOnAbilityUse(helper.getLevel(), f.user, f.stand, null);
		f.standAction.setAction(charge, f.user, SyncType.NO_SYNC);
		holdByKey(f.user, charge, PowerClass.STAND);
		f.tick(1);
		for (int tick = 0; toWindup && tick < 60 && charge.getPhase() == ActionPhase.BUTTON_CHARGE; tick++) {
			f.tick(1);
		}
		ActionPhase expected = toWindup ? ActionPhase.WINDUP : ActionPhase.BUTTON_CHARGE;
		helper.assertTrue(f.standAction.getAction() == charge && charge.getPhase() == expected,
				"the charged heavy punch is not in " + expected + f.state(heavy, charge));
		return charge;
	}

	private static void tickOutOfButtonCharge(StandFixture f, EntityActionInstance action) {
		for (int tick = 0; tick < 60 && action.getPhase() == ActionPhase.BUTTON_CHARGE; tick++) {
			f.tick(1);
		}
	}

	private static EntityActionInstance chargeTimeStop(GameTestHelper helper, StandFixture f, EntityActionAbility timeStop) {
		EntityActionInstance charge = timeStop.initActionOnAbilityUse(helper.getLevel(), f.user, f.stand, null);
		f.standAction.setAction(charge, f.user, SyncType.NO_SYNC);
		holdByKey(f.user, charge, PowerClass.STAND);
		f.tick(3);
		helper.assertTrue(f.standAction.getAction() == charge && charge.getPhase() == ActionPhase.BUTTON_CHARGE
				&& charge.getPhaseTick() > 0, "the time stop is not charging" + f.state(timeStop, charge));
		return charge;
	}

	private static void releaseKey(LivingEntity user, EntityActionInstance action) {
		user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
		action.onKeyRelease(user);
	}

	private static EntityActionInstance startBarrage(GameTestHelper helper, EntityActionType barrage,
			Player user, StandEntity stand, LivingComponentAction standAction) {
		EntityActionInstance action = barrage.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		standAction.setAction(action, user, SyncType.NO_SYNC);
		for (int tick = 0; tick < 10 && action.getPhase() != ActionPhase.PERFORM; tick++) {
			standAction.tick();
		}
		helper.assertTrue(standAction.getAction() == action && action.getPhase() == ActionPhase.PERFORM,
				"the barrage did not start");
		return action;
	}

	private static void holdByKey(LivingEntity user, EntityActionInstance action, PowerClass<?> powerClass) {
		EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, powerClass, action));
	}

	private static Field attackStrengthTicker() {
		try {
			Field field = LivingEntity.class.getDeclaredField("attackStrengthTicker");
			field.setAccessible(true);
			return field;
		}
		catch (NoSuchFieldException e) {
			throw new AssertionError("LivingEntity.attackStrengthTicker is missing", e);
		}
	}

	private static void setAttackStrengthTicker(LivingEntity entity, int ticks) {
		try {
			attackStrengthTicker().setInt(entity, ticks);
		}
		catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	private static int getAttackStrengthTicker(LivingEntity entity) {
		try {
			return attackStrengthTicker().getInt(entity);
		}
		catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	private static void assertClose(GameTestHelper helper, float actual, float expected, String message) {
		helper.assertTrue(Math.abs(actual - expected) < 0.01F, message + ": expected=" + expected + ", actual=" + actual);
	}

	private static final class HamonFixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final PlayerPower power;
		private final HamonData hamon;
		private final LivingComponentAction component;

		private HamonFixture(GameTestHelper helper, String name, rotp.core.impl.powers.hamon.HamonSkill skill) {
			this.helper = helper;
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.setPos(origin.x, origin.y, origin.z);
			user.setYRot(0.0F);
			user.setXRot(0.0F);
			user.setYHeadRot(0.0F);
			user.getAbilities().instabuild = false;
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add held-check test player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			// a starting skill (Healing) comes with the power, and learnSkill reports only a change
			hamon.learnSkill(skill);
			helper.assertTrue(hamon.isSkillLearned(skill), "Could not grant " + skill);
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			component = LivingComponentAction.getComponent(user);
		}

		private EntityActionAbility ability(String name) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered " + name);
			return (EntityActionAbility) found;
		}

		private EntityActionInstance start(String name, boolean heldByKey) {
			EntityActionInstance action = ability(name).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			if (heldByKey) {
				holdByKey(user, action, PowerClass.PLAYER_POWER);
			}
			return action;
		}

		private void tick(int count) {
			// Drive the real action lifecycle without entity physics or passive Hamon regeneration.
			for (int tick = 0; tick < count; tick++) {
				user.tickCount++;
				component.tick();
			}
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			component.setAction(null, SyncType.NO_SYNC);
			user.removeAllEffects();
			user.discard();
		}
	}

	private static final class StandFixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final StandType standType;
		private StandPower power;
		private StandInstance instance;
		private StandEntity stand;
		private LivingComponentAction standAction;

		private StandFixture(GameTestHelper helper, String name, String standId) {
			this.helper = helper;
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			try {
				helper.assertTrue(standType != null, "Missing Stand type " + standId);
				Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
				user.moveTo(userPos.x, userPos.y, userPos.z);
				helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add Stand part test player");
				power = PowerClass.STAND.attachGet(user);
				StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
				helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
						"Could not grant " + standId + ": " + inserted.status());
				instance = power.getStandInstance().orElse(null);
				helper.assertTrue(instance != null, "Granted " + standId + " has no Stand instance");
				helper.assertTrue(standType.summon(user, power), "Could not summon " + standId);
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "Summoned " + standId + " entity is missing");
				standAction = LivingComponentAction.getComponent(stand);
				// The summon lock skips the action tick (StandEntity.tick counts it down, which the test does not run):
				// Magician's Red (speed 11) has 7 ticks of it.
				stand.summonLockTicks = 0;
				// A directional barrage pays stamina every tick and stops at 0; a new power starts with none.
				power.setStamina(power.getMaxStamina());
			}
			catch (RuntimeException | Error e) {
				close();
				throw e;
			}
		}

		private EntityActionAbility ability(String name) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found instanceof EntityActionAbility, "Missing Stand ability " + name);
			return (EntityActionAbility) found;
		}

		private void tick(int count) {
			for (int tick = 0; tick < count; tick++) {
				standAction.tick();
			}
		}

		// Failure detail: what the held recheck sees and what would skip it.
		private String state(EntityActionAbility ability, EntityActionInstance action) {
			ConditionCheck check = ability.checkHeldActionConditions(action, power);
			String reason = check.getWarning() != null ? check.getWarning().getString() : "-";
			return " [phase=" + action.getPhase() + " phaseTick=" + action.getPhaseTick()
					+ " current=" + (standAction.getAction() == action)
					+ " parts=" + instance.getAllParts()
					+ " heldCheck=" + check.isPositive() + "/" + reason
					+ " isActionHeld=" + ability.isActionHeld(action)
					+ " heldByKey=" + AbilityInput.isHeldByKey(user, action)
					+ " releaseCheck=" + ability.canFireReleasedHold(action)
					+ " summonLock=" + stand.summonLockTicks
					+ " stunned=" + ModStatusEffects.isStunned(stand)
					+ " stamina=" + power.getStamina() + "]";
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			if (instance != null) {
				for (StandPart part : StandPart.values()) {
					instance.addPart(part);
				}
			}
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
