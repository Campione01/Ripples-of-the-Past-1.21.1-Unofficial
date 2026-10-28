package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.event.RipplesAbilityKeyPressEvent;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands.hierophant.HierophantEmeraldSplashAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.reflection.CommonReflection;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityAction.checkTaskCancelling -> canClickDuringTask: a Stand action pressed again while it was its Stand's
 * task was refused, with no message and not queued, unless it was chainable (StandEntityLightAttack,
 * HierophantGreenEmeraldSplash, SilverChariotDashAttack); a chainable one cancelled its task where canBeCanceled allowed
 * it (its recovery) and was queued before that. The port's input buffer (owner decision N2) keeps the presses an action
 * holds back; an action that was not chainable refuses its own press in every phase, as 1.16 did.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OwnAbilityPressDuringTask116GameTests {
	private static final short KEY = 23;
	private static final short OTHER_KEY = 24;

	private OwnAbilityPressDuringTask116GameTests() {}

	/*
	 * 1.16 StandEntityAction.checkTaskCancelling asked canClickDuringTask only of an action that was not the power's held
	 * one, so the slot of the action its key still held was drawn usable (ActionsOverlayGui from checkRequirements), and
	 * PowerBaseImpl.onClickAction refused a second press of it (getHeldAction() == action), silently and unqueued; once
	 * the key was let go, the task going on in its recovery refused it through checkRequirements and greyed the slot.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theHeldActionsOwnSlotIsDrawnUsableWhileItsKeyHoldsItAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "OwnPressHeldZoom", "star_platinum", 4)) {
			String name = "enhanced_eyesight";
			Ability zoom = s.power.getAbility(name);
			EntityActionInstance held = s.hold(name);
			helper.assertTrue(held.getPhase() == ActionPhase.PERFORM && AbilityInput.getHeldAction(s.user, PowerClass.STAND) == held,
					"setup: the Zoom is not held by its key: " + held.getPhase() + ", the held action "
							+ AbilityInput.getHeldAction(s.user, PowerClass.STAND));
			AbilityConditionCheck whileHeld = s.resolve(name);
			helper.assertTrue(whileHeld != null && whileHeld.ability == zoom && !whileHeld.conditionCheck.isPositive()
					&& whileHeld.conditionCheck.getWarning() == null && whileHeld.drawnCondition(s.user).isPositive(),
					"1.16 drew the slot of the Zoom its key holds usable and refused its second press silently: its check "
							+ describe(whileHeld) + ", drawn " + (whileHeld != null && whileHeld.drawnCondition(s.user).isPositive()
									? "usable" : "unusable"));
			int messages = s.user.actionBar.size();
			BufferingState again = s.press(name, InputMethod.HOLD, OTHER_KEY, true);
			helper.assertTrue(!again.isActionSuccess && !again.shouldBuffer && s.standAction.getAction() == held
					&& held.getPhase() == ActionPhase.PERFORM && AbilityInput.getHeldAction(s.user, PowerClass.STAND) == held
					&& s.user.actionBar.size() == messages,
					"1.16 refused the held Zoom pressed again by another key (PowerBaseImpl.onClickAction), silently and"
							+ " unqueued: went off " + again.isActionSuccess + ", buffered " + again.shouldBuffer + ", the Stand's"
							+ " action " + s.standAction.getAction() + " in " + held.getPhase() + ", messages " + s.user.actionBar);

			// a recovery, as an add-on's held action has, for the task to go on after the release
			held.phasesLength.put(ActionPhase.RECOVERY, 10.0F);
			AbilityInput.keyRelease(KEY, s.user);
			helper.assertTrue(held.getPhase() == ActionPhase.RECOVERY && !held.isOver() && s.standAction.getAction() == held
					&& AbilityInput.getHeldAction(s.user, PowerClass.STAND) == null,
					"setup: the Zoom let go is not in its recovery: " + held.getPhase());
			AbilityConditionCheck released = s.resolve(name);
			helper.assertTrue(released != null && released.ability == zoom && !released.conditionCheck.isPositive()
					&& released.conditionCheck.getWarning() == null && !released.drawnCondition(s.user).isPositive(),
					"1.16 drew the Zoom's slot unusable in its recovery once its key was let go (checkRequirements): its check "
							+ describe(released) + ", drawn " + (released != null && released.drawnCondition(s.user).isPositive()
									? "usable" : "unusable"));
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandActionThatWasNotChainableRefusesItsOwnPressDuringItsTaskAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "OwnPressStarPlatinum", "star_platinum", 4)) {
			s.unlock("inhale");
			s.refusedDuringItself("inhale", InputMethod.HOLD, action -> {});
			s.refusedDuringItself("enhanced_eyesight", InputMethod.HOLD, action -> {});

			s.user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400, 0));
			s.refusedDuringItself("barrage", InputMethod.HOLD, EntityActionInstance::startRecovery);
			s.user.removeEffect(ModStatusEffects.RESOLVE);

			// the finisher variation's free recovery (1.16 StandEntityHeavyAttack.isFreeRecovery: isFinisher) takes any
			// action but itself
			s.unlock("finisher_uppercut");
			s.stand.setFinisherMeter(1.0F);
			s.refusedDuringItself("finisher_uppercut", InputMethod.CLICK, EntityActionInstance::startRecovery);

			// in its windup the heavy punch holds its own press back as any other: refused all the same
			s.stand.setFinisherMeter(0.0F);
			s.refusedWhereHeldBack("heavy_punch", InputMethod.CLICK, action -> {});
			helper.succeed();
		}
	}

	/*
	 * 1.16 StandEntityAction.canClickDuringTask refused the task's own action pressed again in every phase, its recovery
	 * included, also where the task queued every other press (canBeCanceled false); so the queue never performed it after
	 * the task. The input buffer (owner decision N2) keeps the presses of other abilities, and the own press of an action
	 * that was chainable or does not say it was not.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void anActionThatWasNotChainableRefusesItsOwnPressWhereItHoldsPressesBackAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "OwnPressHeldBack", "star_platinum", 4)) {
			s.stand.setFinisherMeter(0.0F);
			s.refusedWhereHeldBack("heavy_punch", InputMethod.CLICK, action -> action.setPhase(ActionPhase.PERFORM, 0));
			s.refusedWhereHeldBack("heavy_punch", InputMethod.CLICK, EntityActionInstance::startRecovery);
			s.refusedWhereHeldBack("barrage", InputMethod.HOLD, EntityActionInstance::startRecovery);

			Ability chainable = new ChainableHeavyPunchAbility(ModStandAbilities.HEAVY_PUNCH.get(),
					new AbilityId(PowerClass.STAND, s.standType.getId(), CHAINABLE_HEAVY));
			s.power.getMoveset().abilities.put(CHAINABLE_HEAVY, chainable);
			helper.assertTrue(s.power.getAbility(CHAINABLE_HEAVY) == chainable && chainable.isAbilityUnlocked(s.power),
					"setup: the test heavy punch is not an unlocked moveset ability");
			EntityActionInstance running = s.start(CHAINABLE_HEAVY);
			running.startRecovery();
			helper.assertTrue(!running.isOver() && !running.canBeCancelledInto(running.ability) && !running.refusesOwnAbilityPressedAgain(),
					"setup: the test heavy punch does not hold its own press back in its recovery: " + running.getPhase());
			AbilityConditionCheck resolved = s.resolve(CHAINABLE_HEAVY);
			helper.assertTrue(resolved != null && resolved.ability == chainable && resolved.conditionCheck.isPositive(),
					"an action that does not refuse its own press keeps it in its recovery for the input buffer: " + describe(resolved));
			BufferingState buffered = s.press(CHAINABLE_HEAVY, InputMethod.CLICK);
			helper.assertTrue(buffered.shouldBuffer && !buffered.isActionSuccess && s.standAction.getAction() == running,
					"the own press of an action that does not refuse it was not buffered in its recovery: buffered "
							+ buffered.shouldBuffer + ", started " + buffered.isActionSuccess + ", action " + s.standAction.getAction());
			EntityActionInstance replayed = s.endAndReplay(CHAINABLE_HEAVY);
			helper.assertTrue(replayed != null && replayed != running && replayed.ability == chainable,
					"the buffered own press of an action that does not refuse it did not go off once the action ended: the Stand's"
							+ " action " + replayed);
			helper.succeed();
		}
	}

	/*
	 * The Stand's action as its user's client holds it: decoded from the sync, it is of the power type's base-moveset
	 * instance of its ability (EntityActionType#encodeAbility), not of the user's own one that the HUD's slots hold. 1.16's
	 * client asked canClickDuringTask of its synced task (StandEntity CURRENT_TASK) as the server did, so its HUD
	 * (ActionsOverlayGui from checkRequirements) drew the running heavy punch greyed in every phase and the barrage greyed
	 * in its recovery once let go, and the barrage its key still held usable.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theClientsCopyOfTheRunningActionGreysItsOwnSlotAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "OwnPressClientCopy", "star_platinum", 4)) {
			s.stand.setFinisherMeter(0.0F);
			EntityActionInstance heavy = s.startAsClient("heavy_punch");
			s.refusedAsClient("heavy_punch", heavy, false);
			heavy.setPhase(ActionPhase.PERFORM, 0);
			s.refusedAsClient("heavy_punch", heavy, false);
			heavy.startRecovery();
			s.refusedAsClient("heavy_punch", heavy, false);

			EntityActionInstance released = s.startAsClient("barrage");
			released.startRecovery();
			s.refusedAsClient("barrage", released, false);

			s.refusedAsClient("barrage", s.holdAsClient("barrage"), true);
			helper.succeed();
		}
	}

	/*
	 * 1.16 drew the slot of the Zoom or Inhale its key held lit (checkTaskCancelling skipped the held action), also on the
	 * client, which now refuses the running action's own slot as the server does: held, it is refused as pressed again and
	 * drawn usable.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theClientsCopyOfAHeldZoomOrInhaleKeepsItsSlotLitAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "OwnPressClientHeld", "star_platinum", 4)) {
			s.unlock("inhale");
			s.refusedAsClient("enhanced_eyesight", s.holdAsClient("enhanced_eyesight"), true);
			s.refusedAsClient("inhale", s.holdAsClient("inhale"), true);
			helper.succeed();
		}
	}

	private static final String CHAINABLE_HEAVY = "own_press_chainable_heavy";

	private static final class ChainableHeavyPunchAbility extends StandEntityHeavyPunchAbility {
		private ChainableHeavyPunchAbility(AbilityType<?> abilityType, AbilityId abilityId) {
			super(abilityType, abilityId);
			createActionObj = ChainableHeavyPunch::new;
		}
	}

	private static final class ChainableHeavyPunch extends StandEntityHeavyPunch {
		private ChainableHeavyPunch(EntityActionType ability) {
			super(ability);
		}

		@Override
		public boolean refusesOwnAbilityPressedAgain() {
			return false;
		}
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void crazyDiamondsRepairHealAndRevertRefuseTheirOwnPressDuringTheirTaskAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "OwnPressCrazyDiamond", "crazy_diamond", 4)) {
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.setDamageValue(sword.getMaxDamage() - 10);
			s.user.setItemInHand(InteractionHand.OFF_HAND, sword);
			s.unlock("repair_item");
			s.refusedDuringItself("repair_item", InputMethod.HOLD, action -> {});
			s.user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

			Mob cow = s.mob(EntityType.COW, 2.0D);
			cow.setHealth(cow.getMaxHealth() - 6.0F);
			s.aim(cow);
			s.unlock("heal");
			s.refusedDuringItself("heal", InputMethod.HOLD, action -> {});
			cow.discard();

			Creeper creeper = (Creeper) s.mob(EntityType.CREEPER, 2.0D);
			CommonReflection.setCreeperPowered(creeper, true);
			creeper.setHealth(creeper.getMaxHealth() - 6.0F);
			s.aim(creeper);
			s.unlock("revert_state");
			s.refusedDuringItself("revert_state", InputMethod.HOLD, action -> {});
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theEmeraldSplashTakesItsOwnPressOnlyInItsRecoveryAsIn116(GameTestHelper helper) {
		// below Resolve 3 and with no Resolve effect the splash has its windup and recovery (noPhases)
		try (Scene s = new Scene(helper, "OwnPressEmeraldSplash", "hierophant_green", 2)) {
			s.unlock("emerald_splash");
			Ability splash = s.power.getAbility("emerald_splash");
			EntityActionInstance first = s.start("emerald_splash");
			for (int tick = 0; tick < 60 && first.getPhase() != ActionPhase.PERFORM && !first.isOver(); tick++) {
				s.standAction.tick();
			}
			helper.assertTrue(first.getPhase() == ActionPhase.PERFORM && s.standAction.getAction() == first,
					"setup: the emerald splash did not reach its PERFORM: " + first.getPhase());
			AbilityConditionCheck inPerform = s.resolve("emerald_splash");
			helper.assertTrue(inPerform != null && inPerform.ability == splash && inPerform.conditionCheck.isPositive(),
					"1.16 took the chainable emerald splash pressed during itself: " + describe(inPerform));
			BufferingState queued = s.press("emerald_splash", InputMethod.HOLD);
			helper.assertTrue(queued.shouldBuffer && s.standAction.getAction() == first,
					"1.16 queued the emerald splash pressed again in its PERFORM (isCancelable only in RECOVERY): buffered "
							+ queued.shouldBuffer + ", the Stand's action " + s.standAction.getAction() + " (was " + first + ")");

			first.startRecovery();
			AbilityConditionCheck inRecovery = s.resolve("emerald_splash");
			helper.assertTrue(inRecovery != null && inRecovery.ability == splash && inRecovery.conditionCheck.isPositive(),
					"1.16 took the chainable emerald splash pressed in its recovery: " + describe(inRecovery));
			BufferingState chained = s.press("emerald_splash", InputMethod.HOLD);
			EntityActionInstance second = s.standAction.getAction();
			helper.assertTrue(chained.isActionSuccess && second != null && second != first && second.ability == splash,
					"1.16 restarted the emerald splash pressed in its recovery (chainable): success " + chained.isActionSuccess
							+ ", the Stand's action " + second);
			helper.succeed();
		}
	}

	/*
	 * 1.16 StandEntityHeavyAttack.isFreeRecovery was isFinisher(): only the finisher variation's recovery let another
	 * action replace it at once (StandEntityAction.canBeCanceled), whatever the finisher meter (a variation the missing-part
	 * rule chose could run at any meter); a plain heavy punch's recovery held a press back until it ended, even at a
	 * finisher's strength (its finisher variation locked).
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void onlyTheFinisherVariationHasAFreeRecoveryAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "HeavyFreeRecovery", "star_platinum", 4)) {
			EntityActionAbility punch = (EntityActionAbility) s.power.getAbility("punch");
			s.power.getCurTypeData()._lockedAbilities.add("finisher_uppercut");
			s.stand.setFinisherMeter(1.0F);
			EntityActionInstance heavy = s.start("heavy_punch");
			heavy.startRecovery();
			helper.assertTrue(heavy.getPhase() == ActionPhase.RECOVERY && heavy.ability == s.power.getAbility("heavy_punch")
					&& ((Ability) heavy.ability).isStandFinisherOf == null, "setup: the plain heavy punch is not in its recovery");
			BufferingState held = s.press("punch", InputMethod.CLICK);
			helper.assertTrue(held.shouldBuffer && !held.isActionSuccess && s.standAction.getAction() == heavy,
					"1.16 held a punch pressed in the recovery of a plain heavy punch at a full meter back (no free recovery): buffered "
							+ held.shouldBuffer + ", started " + held.isActionSuccess + ", the Stand's action " + s.standAction.getAction());
			s.standAction.setAction(null, s.user, SyncType.NO_SYNC);

			s.unlock("finisher_uppercut");
			for (float meter : new float[] { 1.0F, 0.0F }) {
				s.power.setStamina(s.power.getMaxStamina());
				s.stand.setFinisherMeter(meter);
				EntityActionInstance uppercut = s.start("finisher_uppercut");
				uppercut.startRecovery();
				helper.assertTrue(uppercut.getPhase() == ActionPhase.RECOVERY && uppercut.canBeCancelledInto(punch),
						"1.16 let any other action replace the uppercut (the finisher variation) in its recovery at a finisher meter of "
								+ meter + " (isFreeRecovery: isFinisher): phase " + uppercut.getPhase() + ", cancelled into a punch "
								+ uppercut.canBeCancelledInto(punch));
				BufferingState replaced = s.press("punch", InputMethod.CLICK);
				EntityActionInstance next = s.standAction.getAction();
				helper.assertTrue(replaced.isActionSuccess && next != null && next != uppercut && next.ability == punch,
						"1.16 started a punch pressed in the uppercut's recovery at once (meter " + meter + "): started "
								+ replaced.isActionSuccess + ", the Stand's action " + next);
				s.standAction.setAction(null, s.user, SyncType.NO_SYNC);
			}
			helper.succeed();
		}
	}

	/*
	 * 1.16 StandEntityHeavyAttack was not chainable (StandEntityAction.canClickDuringTask): a heavy attack whose recovery
	 * let other actions through still refused its own press. The core's free recoveries are finisher variations, which
	 * StandFinisherCheck.refuseRunningFinisher refuses too; an add-on heavy punch with a free recovery of its own that is
	 * not a finisher variation has only StandEntityHeavyPunch.refusesOwnAbilityPressedAgain.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aHeavyPunchWhoseOwnRecoveryIsFreeRefusesItsOwnPressAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "OwnPressFreeRecoveryHeavy", "star_platinum", 4)) {
			Ability heavy = new FreeRecoveryHeavyPunchAbility(ModStandAbilities.HEAVY_PUNCH.get(),
					new AbilityId(PowerClass.STAND, s.standType.getId(), FREE_RECOVERY_HEAVY));
			s.power.getMoveset().abilities.put(FREE_RECOVERY_HEAVY, heavy);
			helper.assertTrue(s.power.getAbility(FREE_RECOVERY_HEAVY) == heavy && heavy.isAbilityUnlocked(s.power)
					&& heavy.isStandFinisherOf == null, "setup: the test heavy punch is not an unlocked moveset ability, or is a finisher");
			s.refusedDuringItself(FREE_RECOVERY_HEAVY, InputMethod.CLICK, EntityActionInstance::startRecovery);
			helper.succeed();
		}
	}

	private static final String FREE_RECOVERY_HEAVY = "own_press_free_recovery_heavy";

	// an add-on's heavy punch whose recovery takes any press at once, without being a finisher variation
	private static final class FreeRecoveryHeavyPunchAbility extends StandEntityHeavyPunchAbility {
		private FreeRecoveryHeavyPunchAbility(AbilityType<?> abilityType, AbilityId abilityId) {
			super(abilityType, abilityId);
			createActionObj = FreeRecoveryHeavyPunch::new;
		}
	}

	private static final class FreeRecoveryHeavyPunch extends StandEntityHeavyPunch {
		private FreeRecoveryHeavyPunch(EntityActionType ability) {
			super(ability);
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return getPhase() == ActionPhase.RECOVERY;
		}
	}

	/*
	 * A Stand-bearing mob's AI picks its press before it lets go of its held barrage, so the press reaches the recovery
	 * the release started. 1.16 StandAI clicked through PowerBaseImpl: checkTaskCancelling refused the barrage clicked
	 * during its own task (canClickDuringTask, not chainable), also in the recovery a Resolve effect freed for any other
	 * action.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aMobsBarrageRefusesItsOwnPressInItsResolveRecoveryAsIn116(GameTestHelper helper) {
		Mob zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "setup: could not create the zombie");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "setup: could not add the zombie");
		StandPower power = PowerClass.STAND.attachGet(zombie);
		try {
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: could not give the zombie Star Platinum");
			power.setResolveLevel(4);
			helper.assertTrue(type.summon(zombie, power), "setup: could not summon the zombie's Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "setup: the zombie's Stand is missing");
			stand.summonLockTicks = 0;
			LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
			zombie.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400, 0));
			Ability barrage = power.getAbility("barrage");
			power.setStamina(power.getMaxStamina());
			HeldInputEntry held = AbilityInput.keyPressMob(barrage, zombie, null, InputMethod.HOLD);
			EntityActionInstance running = standAction.getAction();
			helper.assertTrue(held != null && running != null && running.ability == barrage && running.getPhase() == ActionPhase.PERFORM,
					"setup: the zombie's barrage is not held: " + running);
			AbilityInput.keyRelease(held.keyId, zombie);
			helper.assertTrue(running.getPhase() == ActionPhase.RECOVERY && !running.isOver() && running.canBeCancelledInto(running.ability),
					"setup: the barrage let go is not in a recovery its own press could cancel: " + running.getPhase());

			HeldInputEntry again = AbilityInput.keyPressMob(barrage, zombie, null, InputMethod.HOLD);
			helper.assertTrue(again == null && standAction.getAction() == running && !running.isOver(), "1.16 refused a mob's"
					+ " barrage pressed again in the recovery its release started (canClickDuringTask): the Stand's action "
					+ standAction.getAction() + " (was " + running + "), held " + again);

			AbilityInput.keyPressMob(power.getAbility("punch"), zombie, null, InputMethod.CLICK);
			EntityActionInstance punch = standAction.getAction();
			helper.assertTrue(punch != null && punch != running && punch.ability == power.getAbility("punch"), "setup: the"
					+ " Resolve-free recovery did not take the zombie's punch: the Stand's action " + punch);
		}
		finally {
			zombie.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			if (power.isSummoned() && type != null) {
				type.forceUnsummon(zombie, power);
			}
			zombie.discard();
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandAI clicked through checkTaskCancelling, which refused the running action's own click (canClickDuringTask,
	 * not chainable) before canBeCanceled could queue it, also where the task held every other click back: the heavy punch
	 * in its windup, the barrage in a recovery without Resolve. A mob's press is never buffered (keyPressMob), so a press
	 * let through there starts nothing either, but it reaches the ability: its key-press event and the press replayed to
	 * the clients tracking the mob.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aMobsHeavyPunchAndBarrageRefuseTheirOwnPressWhereTheyHoldPressesBackAsIn116(GameTestHelper helper) {
		Mob zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "setup: could not create the zombie");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "setup: could not add the zombie");
		StandPower power = PowerClass.STAND.attachGet(zombie);
		EntityActionInputState input = zombie.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		List<Ability> pressesReached = new ArrayList<>();
		Consumer<RipplesAbilityKeyPressEvent> listener = event -> {
			if (event.getEntity() == zombie) {
				pressesReached.add(event.getAbility());
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RipplesAbilityKeyPressEvent.class, listener);
		try {
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: could not give the zombie Star Platinum");
			power.setResolveLevel(4);
			helper.assertTrue(type.summon(zombie, power), "setup: could not summon the zombie's Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "setup: the zombie's Stand is missing");
			stand.summonLockTicks = 0;
			stand.setFinisherMeter(0.0F);
			LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
			helper.assertTrue(!zombie.hasEffect(ModStatusEffects.RESOLVE), "setup: the zombie has a Resolve effect");

			Ability heavy = power.getAbility("heavy_punch");
			power.setStamina(power.getMaxStamina());
			AbilityInput.keyPressMob(heavy, zombie, null, InputMethod.CLICK);
			EntityActionInstance punching = standAction.getAction();
			helper.assertTrue(punching != null && punching.ability == heavy && punching.getPhase() == ActionPhase.WINDUP,
					"setup: the zombie's heavy punch is not in its windup: " + punching);
			mobPressRefusedWhereHeldBack(helper, zombie, standAction, punching, InputMethod.CLICK, input, pressesReached);

			Ability barrage = power.getAbility("barrage");
			power.setStamina(power.getMaxStamina());
			HeldInputEntry held = AbilityInput.keyPressMob(barrage, zombie, null, InputMethod.HOLD);
			EntityActionInstance barraging = standAction.getAction();
			helper.assertTrue(held != null && barraging != null && barraging.ability == barrage
					&& barraging.getPhase() == ActionPhase.PERFORM, "setup: the zombie's barrage is not held: " + barraging);
			AbilityInput.keyRelease(held.keyId, zombie);
			helper.assertTrue(barraging.getPhase() == ActionPhase.RECOVERY,
					"setup: the zombie's barrage let go is not in its recovery: " + barraging.getPhase());
			mobPressRefusedWhereHeldBack(helper, zombie, standAction, barraging, InputMethod.HOLD, input, pressesReached);
		}
		finally {
			NeoForge.EVENT_BUS.unregister(listener);
			input.heldKeys.clear();
			if (power.isSummoned() && type != null) {
				type.forceUnsummon(zombie, power);
			}
			zombie.discard();
		}
		helper.succeed();
	}

	private static void mobPressRefusedWhereHeldBack(GameTestHelper helper, Mob zombie, LivingComponentAction standAction,
			EntityActionInstance running, InputMethod inputMethod, EntityActionInputState input, List<Ability> pressesReached) {
		Ability ability = (Ability) running.ability;
		ActionPhase phase = running.getPhase();
		helper.assertTrue(!running.isOver() && !running.canBeCancelledInto(running.ability) && running.refusesOwnAbilityPressedAgain(),
				"setup: the zombie's " + ability.name() + " does not hold its own press back in its " + phase);
		pressesReached.clear();
		int heldKeys = input.heldKeys.size();
		HeldInputEntry again = AbilityInput.keyPressMob(ability, zombie, null, inputMethod);
		helper.assertTrue(again == null && pressesReached.isEmpty() && standAction.getAction() == running
				&& running.getPhase() == phase && input.heldKeys.size() == heldKeys && bufferedPress(input) == null,
				"1.16 refused a mob's " + ability.name() + " pressed again in its own " + phase + " (canClickDuringTask), also where"
						+ " the task held every other press back: held " + again + ", the press reached " + pressesReached
						+ ", the Stand's action " + standAction.getAction() + " (was " + running + ") in " + running.getPhase()
						+ ", buffered " + bufferedPress(input));
		standAction.setAction(null, zombie, SyncType.NO_SYNC);
		input.inputBuffer.tickInputBuffer(input);
		EntityActionInstance after = standAction.getAction();
		helper.assertTrue(after == null && pressesReached.isEmpty(), "1.16 never performed a mob's " + ability.name()
				+ " pressed again in its own " + phase + " once it ended: the Stand's action " + after + ", presses " + pressesReached);
	}

	@Nullable
	private static Object bufferedPress(EntityActionInputState input) {
		try {
			Field buffered = ActionInputBuffer.class.getDeclaredField("buffered");
			buffered.setAccessible(true);
			return buffered.get(input.inputBuffer);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("ActionInputBuffer.buffered is not reachable", e);
		}
	}

	/*
	 * 1.16 HierophantGreenEmeraldSplash.isCancelable allowed a cancel only in RECOVERY, for any action pressed, the other
	 * splash variant (hierophant_green_es_concentrated, a SHIFT variation in 1.16, its own slot here) included: pressed
	 * in the windup or perform it was queued (the input buffer here) and replaced the splash in its recovery.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theEmeraldSplashTakesItsOtherVariantOnlyInItsRecoveryAsIn116(GameTestHelper helper) {
		// below Resolve 3 and with no Resolve effect the splash has its windup and recovery (noPhases)
		try (Scene s = new Scene(helper, "VariantPressEmeraldSplash", "hierophant_green", 2)) {
			s.unlock("emerald_splash");
			s.unlock("emerald_splash_concentrated");
			s.power.getCurTypeData().setAbilityLearningProgressPoints(HierophantEmeraldSplashAbility.EMERALD_SPLASH_LEARNING_ABILITY,
					HierophantEmeraldSplashAbility.EMERALD_SPLASH_MAX_TRAINING, HierophantEmeraldSplashAbility.EMERALD_SPLASH_MAX_TRAINING,
					s.power);
			helper.assertTrue(HierophantEmeraldSplashAbility.isEmeraldSplashFullyTrained(s.power),
					"setup: Emerald Splash is not fully trained, so the concentrated splash is hidden");
			for (String[] variants : new String[][] { { "emerald_splash", "emerald_splash_concentrated" },
					{ "emerald_splash_concentrated", "emerald_splash" } }) {
				for (ActionPhase phase : new ActionPhase[] { ActionPhase.WINDUP, ActionPhase.PERFORM }) {
					s.power.setStamina(s.power.getMaxStamina());
					EntityActionInstance running = s.start(variants[0]);
					for (int tick = 0; tick < 60 && running.getPhase() != phase && !running.isOver(); tick++) {
						s.standAction.tick();
					}
					helper.assertTrue(running.getPhase() == phase && s.standAction.getAction() == running,
							"setup: " + variants[0] + " did not reach its " + phase + ": " + running.getPhase());
					AbilityConditionCheck other = s.resolve(variants[1]);
					helper.assertTrue(other != null && other.ability == s.power.getAbility(variants[1]) && other.conditionCheck.isPositive(),
							"setup: " + variants[1] + " cannot be pressed during " + variants[0] + ": " + describe(other));
					BufferingState queued = s.press(variants[1], InputMethod.HOLD);
					helper.assertTrue(queued.shouldBuffer && !queued.isActionSuccess && s.standAction.getAction() == running,
							"1.16 queued " + variants[1] + " pressed in the " + phase + " of " + variants[0] + " (isCancelable only in"
									+ " RECOVERY): buffered " + queued.shouldBuffer + ", started " + queued.isActionSuccess
									+ ", the Stand's action " + s.standAction.getAction() + " (was " + running + ")");
					s.user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
					s.standAction.setAction(null, s.user, SyncType.NO_SYNC);
				}
				s.power.setStamina(s.power.getMaxStamina());
				EntityActionInstance running = s.start(variants[0]);
				running.startRecovery();
				BufferingState replaced = s.press(variants[1], InputMethod.HOLD);
				EntityActionInstance next = s.standAction.getAction();
				helper.assertTrue(replaced.isActionSuccess && next != null && next != running
						&& next.ability == s.power.getAbility(variants[1]), "1.16 let " + variants[1] + " pressed in the recovery of "
								+ variants[0] + " replace it: started " + replaced.isActionSuccess + ", the Stand's action " + next);
				s.standAction.setAction(null, s.user, SyncType.NO_SYNC);
			}
			helper.succeed();
		}
	}

	private static String describe(AbilityConditionCheck container) {
		return container == null ? "nothing" : container.ability.name() + " (" + (container.conditionCheck.isPositive()
				? "usable" : "refused" + (container.conditionCheck.getWarning() != null ? " with a message" : "")) + ")";
	}

	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final MessagePlayer user;
		private final StandType standType;
		private final Vec3 origin;
		private final List<Entity> spawned = new ArrayList<>();
		private StandPower power;
		private StandEntity stand;
		private LivingComponentAction standAction;

		private Scene(GameTestHelper helper, String name, String standId, int resolveLevel) {
			this.helper = helper;
			user = new MessagePlayer(helper.getLevel(), name);
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			try {
				helper.assertTrue(standType != null, "Missing Stand type " + standId);
				user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
				user.setNoGravity(true);
				user.getAbilities().instabuild = false;
				helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
				power = PowerClass.STAND.attachGet(user);
				helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
						== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
				power.setResolveLevel(resolveLevel);
				helper.assertTrue(standType.summon(user, power), "Could not summon " + standId);
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "Summoned " + standId + " is missing");
				standAction = LivingComponentAction.getComponent(stand);
				// the summon lock skips the action tick, and StandEntity.tick, which counts it down, does not run here
				stand.summonLockTicks = 0;
				power.setStamina(power.getMaxStamina());
			}
			catch (RuntimeException | Error e) {
				close();
				throw e;
			}
		}

		// as a player who can press it has it; the scene does not run the user's tick
		private void unlock(String name) {
			power.getCurTypeData()._lockedAbilities.remove(name);
			helper.assertTrue(power.getAbility(name).isAbilityUnlocked(power), "setup: " + name + " is still locked");
		}

		private Mob mob(EntityType<? extends Mob> type, double ahead) {
			Mob mob = type.create(helper.getLevel());
			helper.assertTrue(mob != null, "Could not create " + type);
			mob.setNoAi(true);
			mob.setNoGravity(true);
			mob.moveTo(origin.x, origin.y, origin.z + ahead, 180.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Could not add " + type);
			spawned.add(mob);
			return mob;
		}

		private void aim(Entity target) {
			LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(target));
		}

		// set on the Stand as a press sets it
		private EntityActionInstance start(String name) {
			Ability ability = power.getAbility(name);
			helper.assertTrue(ability instanceof EntityActionAbility, "Missing Stand ability " + name);
			EntityActionInstance action = ((EntityActionAbility) ability).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			helper.assertTrue(action != null, "setup: " + name + " made no action");
			standAction.setAction(null, user, SyncType.NO_SYNC);
			standAction.setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(standAction.getAction() == action, "setup: " + name + " is not the Stand's action");
			return action;
		}

		// set on the Stand as its user's client holds it, of the power type's base-moveset ability
		private EntityActionInstance startAsClient(String name) {
			Ability own = power.getAbility(name);
			Ability base = power.getPowerType().getBaseMoveset().getAbility(name);
			helper.assertTrue(base instanceof EntityActionAbility && own != null && base != own
							&& base.getAbilityId().equals(own.getAbilityId()),
					"setup: the power type's base-moveset " + name + " is not another instance of the user's own: " + base);
			power.setStamina(power.getMaxStamina());
			EntityActionInstance action = ((EntityActionAbility) base).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			helper.assertTrue(action != null && action.ability == base, "setup: " + name + " made no action of its base ability");
			standAction.setAction(null, user, SyncType.NO_SYNC);
			standAction.setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(standAction.getAction() == action, "setup: " + name + " is not the Stand's action");
			return action;
		}

		// in its perform and held, as a client goes by the phase and the server by the key
		private EntityActionInstance holdAsClient(String name) {
			EntityActionInstance action = startAsClient(name);
			if (action.getPhase() != ActionPhase.PERFORM) {
				action.setPhase(ActionPhase.PERFORM, 0);
			}
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.put(KEY,
					new HeldInputEntry(KEY, PowerClass.STAND, action));
			helper.assertTrue(AbilityInput.getHeldAction(user, PowerClass.STAND) == action,
					"setup: the " + name + " is not the held action: " + AbilityInput.getHeldAction(user, PowerClass.STAND));
			return action;
		}

		// the user's own slot of the running action, refused silently and drawn greyed, or drawn usable while held
		private void refusedAsClient(String name, EntityActionInstance running, boolean held) {
			Ability own = power.getAbility(name);
			AbilityConditionCheck resolved = resolve(name);
			boolean drawnUsable = resolved != null && resolved.drawnCondition(user).isPositive();
			ConditionCheck expected = held ? ConditionCheck.NEGATIVE_OWN_HELD_PRESSED_AGAIN : ConditionCheck.NEGATIVE;
			helper.assertTrue(resolved != null && resolved.ability == own && resolved.conditionCheck == expected
					&& drawnUsable == held,
					"1.16 " + (held ? "drew the slot of the " + name + " its key holds usable and refused its press"
							: "drew the slot of the running " + name + " greyed and refused its press") + " in its "
							+ running.getPhase() + ", also on its user's client, whose copy of the action is of the base-moveset "
							+ name + ": its check " + describe(resolved) + (resolved != null && resolved.conditionCheck
									== ConditionCheck.NEGATIVE_OWN_HELD_PRESSED_AGAIN ? " (own held)" : "") + ", drawn "
							+ (drawnUsable ? "usable" : "greyed"));
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.remove(KEY);
		}

		/*
		 * The ability, usable while the Stand is idle, runs and is put where it would let its own press cancel it; that
		 * press is then refused with no message, and a press leaves the running action as it was.
		 */
		private void refusedDuringItself(String name, InputMethod inputMethod, Consumer<EntityActionInstance> phase) {
			standAction.setAction(null, user, SyncType.NO_SYNC);
			power.setStamina(power.getMaxStamina());
			Ability ability = power.getAbility(name);
			AbilityConditionCheck idle = resolve(name);
			helper.assertTrue(idle != null && idle.ability == ability && idle.conditionCheck.isPositive(),
					"setup: " + name + " cannot be used with the Stand idle: " + describe(idle));
			EntityActionInstance running = start(name);
			phase.accept(running);
			helper.assertTrue(!running.isOver() && running.canBeCancelledInto(running.ability),
					"setup: the running " + name + " would not let its own press cancel it (phase " + running.getPhase() + ")");
			AbilityConditionCheck resolved = resolve(name);
			helper.assertTrue(resolved != null && resolved.ability == ability && !resolved.conditionCheck.isPositive()
					&& resolved.conditionCheck.getWarning() == null,
					"1.16 refused " + name + " pressed again during its own task (not chainable), silently, phase "
							+ running.getPhase() + ": " + describe(resolved));
			int messages = user.actionBar.size();
			BufferingState pressed = press(name, inputMethod);
			helper.assertTrue(standAction.getAction() == running && !pressed.shouldBuffer && !pressed.isActionSuccess,
					"1.16 refused " + name + " pressed again during its own task, unqueued: the Stand's action is "
							+ standAction.getAction() + " (was " + running + "), buffered " + pressed.shouldBuffer);
			helper.assertTrue(user.actionBar.size() == messages,
					"1.16 refused " + name + " pressed again during its own task with no message: " + user.actionBar);
			standAction.setAction(null, user, SyncType.NO_SYNC);
		}

		/*
		 * The ability runs and is put where it holds its own press back, as every other press; that press is refused with
		 * no message and not buffered, and nothing goes off once the action has ended. A held press keeps its key down, so
		 * a held press the buffer kept would still be there to go off.
		 */
		private void refusedWhereHeldBack(String name, InputMethod inputMethod, Consumer<EntityActionInstance> phase) {
			standAction.setAction(null, user, SyncType.NO_SYNC);
			power.setStamina(power.getMaxStamina());
			Ability ability = power.getAbility(name);
			EntityActionInstance running = start(name);
			phase.accept(running);
			helper.assertTrue(!running.isOver() && !running.canBeCancelledInto(running.ability) && running.refusesOwnAbilityPressedAgain(),
					"setup: the running " + name + " does not hold its own press back (phase " + running.getPhase() + ")");
			AbilityConditionCheck resolved = resolve(name);
			helper.assertTrue(resolved != null && resolved.ability == ability && !resolved.conditionCheck.isPositive()
					&& resolved.conditionCheck.getWarning() == null,
					"1.16 refused " + name + " pressed again during its own task (not chainable), silently, also where the task"
							+ " queued other presses, phase " + running.getPhase() + ": " + describe(resolved));
			int messages = user.actionBar.size();
			BufferingState pressed = press(name, inputMethod, KEY, inputMethod != InputMethod.HOLD);
			helper.assertTrue(standAction.getAction() == running && !pressed.shouldBuffer && !pressed.isActionSuccess
					&& user.actionBar.size() == messages,
					"1.16 refused " + name + " pressed again in its own " + running.getPhase() + ", silently and unqueued: the"
							+ " Stand's action is " + standAction.getAction() + " (was " + running + "), buffered " + pressed.shouldBuffer
							+ ", messages " + user.actionBar);
			EntityActionInstance after = endAndReplay(name);
			helper.assertTrue(after == null, "1.16 never performed " + name + " pressed again in its own " + running.getPhase()
					+ " once it ended: the Stand's action " + after);
			AbilityInput.keyRelease(KEY, user);
			standAction.setAction(null, user, SyncType.NO_SYNC);
		}

		// the running action ends and the user's input buffer has its tick: the Stand's action after it
		@Nullable
		private EntityActionInstance endAndReplay(String name) {
			standAction.setAction(null, user, SyncType.NO_SYNC);
			power.setStamina(power.getMaxStamina());
			resolve(name);
			EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			input.inputBuffer.tickInputBuffer(input);
			return standAction.getAction();
		}

		// as ClAbilityInputPacket handles a press: the key's resolved ability, its condition check, then the press
		private BufferingState press(String name, InputMethod inputMethod) {
			return press(name, inputMethod, KEY, true);
		}

		private BufferingState press(String name, InputMethod inputMethod, short key, boolean release) {
			BufferingState state = BufferingState.clickCanBuffer();
			Ability base = power.getAbility(name);
			AbilityConditionCheck container = resolve(name);
			if (container != null && AbilityInput.withConditionCheck(container, user, inputMethod)) {
				AbilityInput.keyPress(key, container.ability, user, null, inputMethod, 0, state, base.getAbilityId());
				if (release) {
					AbilityInput.keyRelease(key, user);
				}
			}
			return state;
		}

		// pressed by KEY and kept held
		private EntityActionInstance hold(String name) {
			power.setStamina(power.getMaxStamina());
			BufferingState state = press(name, InputMethod.HOLD, KEY, false);
			EntityActionInstance action = standAction.getAction();
			helper.assertTrue(state.isActionSuccess && action != null && action.ability == power.getAbility(name),
					"setup: " + name + " did not start held: started " + state.isActionSuccess + ", the Stand's action " + action);
			return action;
		}

		// the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
		private AbilityConditionCheck resolve(String name) {
			try {
				Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
				cached.setAccessible(true);
				cached.setBoolean(power, false);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
			}
			return power.updateAvailableMoves().getContextVariationContainer(power.getAbility(name));
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			spawned.forEach(Entity::discard);
			user.discard();
		}
	}

	private static final class MessagePlayer extends FakePlayer {
		private final List<Component> actionBar = new ArrayList<>();

		private MessagePlayer(ServerLevel level, String name) {
			super(level, new GameProfile(UUID.randomUUID(), name));
		}

		@Override
		public void displayClientMessage(Component message, boolean actionBar) {
			if (actionBar) {
				this.actionBar.add(message);
			}
		}
	}
}
