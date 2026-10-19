package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.stands.crazydiamond.CrazyDAngeloRockPunchEffect;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModItemDataComponents;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.itemtracking.OriginalItemPosComponent;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.reflection.CommonReflection;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 PowerBaseImpl.tickHeldAction re-ran checkRequirements on every tick a key held an action, against the target
 * under the crosshair: the target (its kind, Action.checkRangeAndTarget's 8 blocks, 4 without a line of sight, and the
 * action's own checkTarget), then the action's conditions. A failed result ended the hold with its message, unless it
 * let the hold go on (continueHold: a hold-only action's target), which only skipped that tick.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeldActionRecheck116GameTests {
	private static final short KEY = 13;
	private static final String CONDITION = "jojo.message.action_condition.";

	private HeldActionRecheck116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void hypnosisChargeEndsWhenItsTargetIsPastItsRange(GameTestHelper helper) {
		try (PowerScene s = new PowerScene(helper, "HeldHypnosisRange", ModPlayerPowers.HAMON.get())) {
			HamonData hamon = PlayerPower.getPowerData(s.user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.HYPNOSIS.get());
			helper.assertTrue(hamon.isSkillLearned(ModHamonSkills.HYPNOSIS.get()), "setup: Hypnosis is not learned");
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			Mob near = s.mob(EntityType.WOLF, 2.0D);
			Mob far = s.mob(EntityType.WOLF, 12.0D);
			s.aim(near);
			EntityActionInstance charge = s.start("hypnosis");
			s.tick(3);
			helper.assertTrue(charge.getPhase() == ActionPhase.WINDUP && !charge.isOver(),
					"setup: Hypnosis is not charging at the wolf 2 blocks away: phase " + charge.getPhase());
			s.aim(far);
			s.tick(1);
			helper.assertTrue(charge.isOver(),
					"1.16 Action.checkRangeAndTarget ended the held Hypnosis once the wolf under the crosshair was past 8 blocks:"
							+ " phase " + charge.getPhase());
			helper.assertTrue(s.user.keys().contains(CONDITION + "target_too_far"),
					"1.16 sent target_too_far as the hold ended: messages " + s.user.keys());
			helper.succeed();
		}
	}

	// PillarmanBladeBarrage.checkHeldItems failed on a held tick: the hold ended with "hand".
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void bladeBarrageEndedByAFilledHandSaysWhy(GameTestHelper helper) {
		try (PowerScene s = new PowerScene(helper, "HeldBladeBarrageHand", ModPlayerPowers.PILLAR_MAN.get())) {
			s.pillarman(2, PillarmanMode.LIGHT);
			EntityActionInstance barrage = s.start("pillarman_blade_barrage");
			s.tick(2);
			helper.assertTrue(barrage.getPhase() == ActionPhase.PERFORM && !barrage.isOver(),
					"setup: the Blade Barrage is not held: phase " + barrage.getPhase());
			s.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			s.tick(1);
			helper.assertTrue(barrage.isOver(), "a filled main hand did not end the held Blade Barrage: phase " + barrage.getPhase());
			helper.assertTrue(s.user.keys().contains(CONDITION + "hand"),
					"1.16 sent the failed check's message as the hold ended: messages " + s.user.keys());
			helper.succeed();
		}
	}

	// PillarmanAbsorption is hold-only with an ENTITY target: no target in reach let the hold go on before the conditions.
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void absorptionWaitsWithoutATargetAndEndsOnAFilledHandWithOne(GameTestHelper helper) {
		try (PowerScene s = new PowerScene(helper, "HeldAbsorptionHand", ModPlayerPowers.PILLAR_MAN.get())) {
			s.pillarman(2, PillarmanMode.NONE);
			Mob cow = s.mob(EntityType.COW, 1.5D);
			s.aim(cow);
			EntityActionInstance absorption = s.start("pillarman_absorption");
			s.tick(2);
			helper.assertTrue(absorption.getPhase() == ActionPhase.PERFORM && !absorption.isOver(),
					"setup: the Absorption is not held: phase " + absorption.getPhase());
			s.aim(null);
			s.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			float health = cow.getHealth();
			s.tick(3);
			helper.assertTrue(absorption.getPhase() == ActionPhase.PERFORM && !absorption.isOver() && cow.getHealth() == health,
					"1.16 kept the Absorption held, absorbing nothing, while no target was in reach, whatever the hand held: phase "
							+ absorption.getPhase() + ", cow health " + health + " -> " + cow.getHealth());
			s.aim(cow);
			s.tick(1);
			helper.assertTrue(absorption.isOver() && cow.getHealth() == health,
					"1.16 ended the held Absorption on a filled hand once a target was back in reach: phase " + absorption.getPhase()
							+ ", cow health " + health + " -> " + cow.getHealth());
			helper.assertTrue(s.user.keys().contains(CONDITION + "hand"), "1.16 said why: messages " + s.user.keys());
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void hideInEntityChargeEndsWhenItsTargetIsPastItsRange(GameTestHelper helper) {
		try (PowerScene s = new PowerScene(helper, "HeldHideInEntityRange", ModPlayerPowers.PILLAR_MAN.get())) {
			s.pillarman(2, PillarmanMode.NONE);
			Mob near = s.mob(EntityType.COW, 2.0D);
			Mob far = s.mob(EntityType.COW, 12.0D);
			s.aim(near);
			EntityActionInstance charge = s.start("pillarman_hide_in_entity");
			s.tick(3);
			helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE && !charge.isOver(),
					"setup: Hide in Entity is not charging at the cow 2 blocks away: phase " + charge.getPhase());
			s.aim(far);
			s.tick(1);
			helper.assertTrue(charge.isOver(),
					"1.16 ended the Hide in Entity charge once the cow under the crosshair was past 8 blocks: phase " + charge.getPhase());
			helper.assertTrue(s.user.keys().contains(CONDITION + "target_too_far"), "1.16 said why: messages " + s.user.keys());
			helper.succeed();
		}
	}

	// VampirismSpaceRipperStingyEyes held its charge (holdToFire 20, continueHolding): NonStandAction.checkEnergy ran each tick.
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void stingyEyesChargeEndsUnfiredWhenTheBloodRunsShort(GameTestHelper helper) {
		try (PowerScene s = new PowerScene(helper, "HeldStingyEyesBlood", ModPlayerPowers.VAMPIRISM.get())) {
			s.vampire(500.0F);
			EntityActionInstance charge = s.start("vampirism_space_ripper_stingy_eyes");
			s.tick(3);
			helper.assertTrue(charge.getPhase() == ActionPhase.WINDUP && !charge.isOver(),
					"setup: Space Ripper Stingy Eyes is not charging: phase " + charge.getPhase());
			s.setBlood(10.0F);
			s.tick(1);
			helper.assertTrue(charge.isOver(),
					"1.16 ended the held Space Ripper Stingy Eyes once the blood was short of a shot's 20: phase " + charge.getPhase());
			helper.assertTrue(s.user.keys().contains(CONDITION + "no_energy_vampirism"), "1.16 said why: messages " + s.user.keys());
			helper.succeed();
		}
	}

	// VampirismBloodGift (holdToFire 60) ended the hold on a failed check with that check's message.
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void bloodGiftEndedByAFilledHandSaysWhy(GameTestHelper helper) {
		try (PowerScene s = new PowerScene(helper, "HeldBloodGiftHand", ModPlayerPowers.VAMPIRISM.get())) {
			s.vampire(500.0F);
			RecordingPlayer recipient = s.player("HeldBloodGiftRecipient", 1.5D);
			recipient.setHealth(6.0F);
			s.aim(recipient);
			EntityActionInstance gift = s.start("vampirism_blood_gift");
			s.tick(3);
			helper.assertTrue(gift.getPhase() == ActionPhase.WINDUP && !gift.isOver(),
					"setup: the Blood Gift is not charging at the weak player: phase " + gift.getPhase());
			s.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			s.tick(1);
			helper.assertTrue(gift.isOver(), "a filled main hand did not end the held Blood Gift: phase " + gift.getPhase());
			helper.assertTrue(s.user.keys().contains(CONDITION + "hand"), "1.16 said why: messages " + s.user.keys());
			helper.succeed();
		}
	}

	// StarPlatinumInhale (holdType 80, partsRequired MAIN_BODY): a held tick without the body stopped the hold.
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void inhaleEndsWhenTheStandLosesItsBody(GameTestHelper helper) {
		try (StandScene s = new StandScene(helper, "HeldInhaleBody", "star_platinum")) {
			EntityActionInstance inhale = s.start("inhale");
			s.tick(3);
			helper.assertTrue(inhale.getPhase() == ActionPhase.PERFORM, "setup: the Inhale is not held: phase " + inhale.getPhase());
			s.instance.removePart(StandPart.MAIN_BODY);
			s.tick(1);
			helper.assertTrue(inhale.isOver() || inhale.getPhase() == ActionPhase.RECOVERY,
					"1.16 ended the held Inhale once the Stand lost its body: phase " + inhale.getPhase());
			helper.assertTrue(s.user.keys().contains(CONDITION + "no_stand_part.main_body"),
					"1.16 said why: messages " + s.user.keys());
			helper.succeed();
		}
	}

	// CrazyDiamondRepairItem.checkSpecificConditions ran on every held tick: an item that needs no repair ended it with no_repair.
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void repairEndedByAnOffHandItemThatNeedsNoRepairSaysWhy(GameTestHelper helper) {
		try (StandScene s = new StandScene(helper, "HeldRepairItem", "crazy_diamond")) {
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.setDamageValue(sword.getMaxDamage() - 10);
			s.user.setItemInHand(InteractionHand.OFF_HAND, sword);
			EntityActionInstance repair = s.startUntilPerform("repair_item");
			s.tick(1);
			helper.assertTrue(repair.getPhase() == ActionPhase.PERFORM && !repair.isOver(),
					"setup: the Repair is not held on the damaged sword: phase " + repair.getPhase());
			s.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
			s.tick(1);
			helper.assertTrue(repair.isOver() || repair.getPhase() == ActionPhase.RECOVERY,
					"1.16 ended the held Repair once the off hand held an item that needs no repair: phase " + repair.getPhase());
			helper.assertTrue(s.user.keys().contains(CONDITION + "no_repair"), "1.16 said why: messages " + s.user.keys());
			helper.succeed();
		}
	}

	/*
	 * CrazyDiamondPreviousState: an entity it reverts under the crosshair kept the hold whatever the off hand held; without
	 * one, PowerBaseImpl.checkTarget emptied the target and checkSpecificConditions asked for a convertible off-hand item.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void revertOnAnEntityHoldsWithAnEmptyOffHandAndEndsWhenTheEntityCannotBeReverted(GameTestHelper helper) {
		try (StandScene s = new StandScene(helper, "HeldRevertState", "crazy_diamond")) {
			Creeper creeper = (Creeper) s.mob(EntityType.CREEPER, 2.0D);
			CommonReflection.setCreeperPowered(creeper, true);
			// hurt, so the Revert heals it and never uncharges it by chance
			creeper.setHealth(creeper.getMaxHealth() - 6.0F);
			s.aim(creeper);
			s.unlock("revert_state");
			EntityActionInstance revert = s.startUntilPerform("revert_state");
			s.tick(3);
			helper.assertTrue(revert.getPhase() == ActionPhase.PERFORM && !revert.isOver(),
					"1.16 kept the held Revert on the charged creeper with nothing in the off hand: phase " + revert.getPhase()
							+ ", messages " + s.user.keys());
			CommonReflection.setCreeperPowered(creeper, false);
			s.tick(1);
			helper.assertTrue(revert.isOver() || revert.getPhase() == ActionPhase.RECOVERY,
					"1.16 ended the held Revert once the creeper under the crosshair could no longer be reverted and the off"
							+ " hand was empty: phase " + revert.getPhase());
			helper.assertTrue(s.user.keys().contains(CONDITION + "item_offhand"), "1.16 said why: messages " + s.user.keys());
			helper.succeed();
		}
	}

	// CrazyDiamondRestoreTerrain.checkSpecificConditions: the Angelo Rock effect on the user ended the hold, no message.
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void restoreTerrainEndsOnceAngeloRockIsUnderWay(GameTestHelper helper) {
		try (StandScene s = new StandScene(helper, "HeldRestoreTerrain", "crazy_diamond")) {
			EntityActionInstance restore = s.startUntilPerform("restore_terrain");
			s.tick(2);
			helper.assertTrue(restore.getPhase() == ActionPhase.PERFORM && !restore.isOver(),
					"setup: Restore Terrain is not held with no block to restore: phase " + restore.getPhase());
			CrazyDAngeloRockPunchEffect angelo = s.power.userStandEffects.getOrCreateEffect(
					ModStandAbilities.EFFECT_CD_PUNCH_ANGELO_ROCK.get());
			try {
				s.tick(1);
				helper.assertTrue(restore.isOver() || restore.getPhase() == ActionPhase.RECOVERY,
						"1.16 ended the held Restore Terrain once the Angelo Rock effect was on its user: phase " + restore.getPhase());
			}
			finally {
				s.power.userStandEffects.removeEffect(angelo);
			}
			helper.succeed();
		}
	}

	// CrazyDiamondBlockCheckpointMove.checkSpecificConditions: an anchor no longer in hand ended the hold with item_block_origin.
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void anchorMoveEndedByTheAnchorLeavingTheHandSaysWhy(GameTestHelper helper) {
		try (StandScene s = new StandScene(helper, "HeldAnchorMove", "crazy_diamond")) {
			ItemStack anchor = new ItemStack(Items.STONE);
			anchor.set(ModItemDataComponents.ORIGINAL_POS, new OriginalItemPosComponent(
					BlockPos.containing(s.origin).offset(0, 0, 20), helper.getLevel().dimension()));
			s.user.setItemInHand(InteractionHand.MAIN_HAND, anchor);
			EntityActionInstance move = s.startUntilPerform("block_anchor");
			s.tick(1);
			helper.assertTrue(move.getPhase() == ActionPhase.PERFORM && !move.isOver(),
					"setup: the anchor move is not held toward the anchor 20 blocks away: phase " + move.getPhase());
			s.user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			s.tick(1);
			helper.assertTrue(move.isOver() || move.getPhase() == ActionPhase.RECOVERY,
					"1.16 ended the held anchor move once the anchor left the hand: phase " + move.getPhase());
			helper.assertTrue(s.user.keys().contains(CONDITION + "item_block_origin"), "1.16 said why: messages " + s.user.keys());
			helper.succeed();
		}
	}

	// PowerBaseImpl.checkRequirements asked isUnlocked last on every held tick: a barrage whose unlock is taken ends with
	// not_unlocked
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aHeldActionNoLongerUnlockedEndsWithNotUnlockedAsIn116(GameTestHelper helper) {
		try (StandScene s = new StandScene(helper, "HeldBarrageUnlock", "star_platinum")) {
			EntityActionInstance barrage = s.startUntilPerform("barrage");
			s.tick(2);
			helper.assertTrue(barrage.getPhase() == ActionPhase.PERFORM && !barrage.isOver(),
					"setup: the barrage is not held: phase " + barrage.getPhase() + ", messages " + s.user.keys());
			s.power.getCurTypeData()._lockedAbilities.add("barrage");
			try {
				helper.assertTrue(!s.ability("barrage").isAbilityUnlocked(s.power), "setup: the barrage is still unlocked");
				s.tick(1);
				helper.assertTrue(barrage.isOver() || barrage.getPhase() == ActionPhase.RECOVERY,
						"1.16 ended the held barrage once it was no longer unlocked: phase " + barrage.getPhase());
				helper.assertTrue(s.user.keys().contains(CONDITION + "not_unlocked"), "1.16 said why: messages " + s.user.keys());
			}
			finally {
				s.power.getCurTypeData()._lockedAbilities.remove("barrage");
			}
			helper.succeed();
		}
	}

	// isUnlocked came after the action's own conditions: a repair that needs no repair and is locked says no_repair
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theHeldUnlockIsAskedAfterTheActionsOwnConditionsAsIn116(GameTestHelper helper) {
		try (StandScene s = new StandScene(helper, "HeldRepairUnlock", "crazy_diamond")) {
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.setDamageValue(sword.getMaxDamage() - 10);
			s.user.setItemInHand(InteractionHand.OFF_HAND, sword);
			EntityActionInstance repair = s.startUntilPerform("repair_item");
			s.tick(1);
			helper.assertTrue(repair.getPhase() == ActionPhase.PERFORM && !repair.isOver(),
					"setup: the Repair is not held on the damaged sword: phase " + repair.getPhase());
			s.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
			s.power.getCurTypeData()._lockedAbilities.add("repair_item");
			try {
				s.tick(1);
				helper.assertTrue(repair.isOver() || repair.getPhase() == ActionPhase.RECOVERY,
						"setup: the held Repair did not end: phase " + repair.getPhase());
				helper.assertTrue(s.user.keys().contains(CONDITION + "no_repair") && !s.user.keys().contains(CONDITION + "not_unlocked"),
						"1.16 PowerBaseImpl.checkRequirements asked the action's conditions before isUnlocked: messages " + s.user.keys());
			}
			finally {
				s.power.getCurTypeData()._lockedAbilities.remove("repair_item");
			}
			helper.succeed();
		}
	}

	// StandEntityAction.checkConditions ran checkStandConditions on every held tick: the guard's own Stand conditions (a
	// Stand holding an entity it grabbed may not guard) end the held guard, without a message
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aHeldStandActionEndsOnceItsStandConditionsFailAsIn116(GameTestHelper helper) {
		try (StandScene s = new StandScene(helper, "HeldGuardStandConditions", "star_platinum")) {
			Mob cow = s.mob(EntityType.COW, 1.5D);
			EntityActionInstance guard = s.startUntilPerform("guard");
			s.tick(2);
			helper.assertTrue(guard.getPhase() == ActionPhase.PERFORM && !guard.isOver(),
					"setup: the guard is not held: phase " + guard.getPhase());
			LivingComponentGrab standGrab = s.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
			standGrab.setGrabTarget(cow);
			try {
				helper.assertTrue(LivingComponentGrab.getEntityGrabbedBy(s.stand) == cow, "setup: the Stand did not grab the cow");
				s.tick(1);
				helper.assertTrue(guard.isOver() || guard.getPhase() == ActionPhase.RECOVERY,
						"1.16 asked the Stand's conditions again on every held tick, so the held guard ends once its Stand holds a"
								+ " grabbed entity, which its press refuses: phase " + guard.getPhase());
				helper.assertTrue(s.user.keys().isEmpty(), "the guard's Stand condition has no message: messages " + s.user.keys());
			}
			finally {
				standGrab.setGrabTarget(null);
			}
			helper.succeed();
		}
	}

	// StandEntityMeleeBarrage.checkStandConditions (canAttackMelee, no message) came before StandAction's parts
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aHeldBarragesStandConditionsComeBeforeItsPartsAsIn116(GameTestHelper helper) {
		try (StandScene s = new StandScene(helper, "HeldBarrageConditionOrder", "star_platinum")) {
			EntityActionInstance barrage = s.startUntilPerform("barrage");
			s.tick(2);
			helper.assertTrue(barrage.getPhase() == ActionPhase.PERFORM && !barrage.isOver(),
					"setup: the barrage is not held: phase " + barrage.getPhase());
			AttributeInstance reach = s.stand.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
			double baseReach = reach.getBaseValue();
			reach.setBaseValue(0);
			s.instance.removePart(StandPart.ARMS);
			try {
				helper.assertTrue(!s.stand.canAttackMelee(), "setup: the Stand can still attack in melee");
				s.tick(1);
				helper.assertTrue(barrage.isOver() || barrage.getPhase() == ActionPhase.RECOVERY,
						"setup: the held barrage did not end: phase " + barrage.getPhase());
				helper.assertTrue(s.user.keys().isEmpty(),
						"1.16 StandEntityAction.checkConditions asked checkStandConditions (canAttackMelee, no message) before"
								+ " StandAction.checkConditions (the arms): messages " + s.user.keys());
			}
			finally {
				reach.setBaseValue(baseReach);
				s.instance.addPart(StandPart.ARMS);
			}
			helper.succeed();
		}
	}

	/*
	 * PowerBaseImpl.tickHeldAction ended a hold at its length with stopHeldAction(true), which checked the requirements once
	 * more and, when they failed, fired nothing and said nothing. Each charge here is run to its end at once, as a mob's
	 * held press is (stand-bearing-creatures' fireHeldAction).
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aFullLengthChargeIsCheckedOnceMoreBeforeItFiresAsIn116(GameTestHelper helper) {
		try (StandScene s = new StandScene(helper, "FullChargeRecheck", "magicians_red")) {
			s.unlock("crossfire_hurricane");
			EntityActionInstance fires = s.start("crossfire_hurricane");
			s.tick(3);
			helper.assertTrue(fires.getPhase() == ActionPhase.BUTTON_CHARGE, "setup: Crossfire Hurricane is not charging: phase "
					+ fires.getPhase());
			s.runPhaseToItsEnd(fires);
			helper.assertTrue(!fires.isOver() && fires.getPhase() != ActionPhase.BUTTON_CHARGE,
					"setup: a full charge that passes its checks did not go on to fire: phase " + fires.getPhase());

			EntityActionInstance dropped = s.start("crossfire_hurricane");
			s.tick(3);
			s.instance.removePart(StandPart.MAIN_BODY);
			float stamina = s.power.getStamina();
			s.runPhaseToItsEnd(dropped);
			helper.assertTrue(dropped.isOver(),
					"1.16 stopHeldAction(true) fired nothing once the Stand had lost its body at the end of the charge: phase "
							+ dropped.getPhase());
			s.tick(2);
			helper.assertTrue(s.user.keys().isEmpty() && s.power.getStamina() == stamina,
					"the dropped full charge said nothing and spent nothing: messages " + s.user.keys() + ", stamina " + stamina
							+ " -> " + s.power.getStamina());

			// no key holds it (set by code): not checked, as on its held ticks
			EntityActionInstance unheld = s.start("crossfire_hurricane");
			s.user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			s.runPhaseToItsEnd(unheld);
			helper.assertTrue(!unheld.isOver() && unheld.getPhase() != ActionPhase.BUTTON_CHARGE,
					"a charge no key holds is not rechecked at its end: phase " + unheld.getPhase());
			helper.succeed();
		}
	}

	// a hold-only action at its length ended through stopHeldAction(true)'s holdOnly branch, which rechecked nothing
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aHoldOnlyActionAtItsLengthEndsWithoutTheFireRecheckAsIn116(GameTestHelper helper) {
		try (StandScene s = new StandScene(helper, "HoldOnlyAtLength", "star_platinum")) {
			EntityActionInstance inhale = s.startUntilPerform("inhale");
			inhale.phasesLength.put(ActionPhase.RECOVERY, 10.0F);
			s.instance.removePart(StandPart.MAIN_BODY);
			s.runPhaseToItsEnd(inhale);
			helper.assertTrue(inhale.getPhase() == ActionPhase.RECOVERY,
					"1.16 ended a hold-only action at its length into its recovery without the fire's recheck: phase "
							+ inhale.getPhase());
			helper.succeed();
		}
	}

	// a charge that goes on into a phase the key still holds does not fire, so 1.16 checked it on its next held tick
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aChargeThatGoesOnHeldIsCheckedOnItsNextHeldTickAsIn116(GameTestHelper helper) {
		try (StandScene s = new StandScene(helper, "ChargeGoesOnHeld", "star_platinum")) {
			EntityActionInstance charge = s.start("heavy_charged");
			s.tick(1);
			helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE, "setup: the charged heavy punch is not charging: phase "
					+ charge.getPhase());
			s.stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
			try {
				s.runPhaseToItsEnd(charge);
				helper.assertTrue(charge.getPhase() == ActionPhase.WINDUP,
						"the end of the charge led into the held windup, which fires nothing: phase " + charge.getPhase());
				s.tick(1);
				helper.assertTrue(charge.isOver() && s.user.keys().contains(CONDITION + "stun"),
						"its next held tick ended it with the check's message: phase " + charge.getPhase() + ", messages "
								+ s.user.keys());
			}
			finally {
				s.stand.removeEffect(ModStatusEffects.STUN);
			}
			helper.succeed();
		}
	}

	// 1.16 held an action from its press: a windup played before a held perform is rechecked too
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aWindupBeforeAHeldPerformIsRecheckedAsIn116(GameTestHelper helper) {
		try (PowerScene s = new PowerScene(helper, "HeldDetectorWindup", ModPlayerPowers.HAMON.get())) {
			HamonData hamon = PlayerPower.getPowerData(s.user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.DETECTOR.get());
			helper.assertTrue(hamon.isSkillLearned(ModHamonSkills.DETECTOR.get()), "setup: the Detector is not learned");
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			EntityActionInstance detector = s.start("hamon_detector");
			s.tick(2);
			helper.assertTrue(detector.getPhase() == ActionPhase.WINDUP && !detector.isOver(),
					"setup: the Detector is not in its windup: phase " + detector.getPhase());
			s.user.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
			s.tick(1);
			helper.assertTrue(detector.isOver(),
					"1.16 checked the held Detector from its press on, so a stun in its windup ended it: phase " + detector.getPhase());
			helper.assertTrue(s.user.keys().contains(CONDITION + "stun"), "1.16 said why: messages " + s.user.keys());
			helper.succeed();
		}
	}

	/** A server player that keeps the action bar messages it is sent. */
	private static final class RecordingPlayer extends FakePlayer {
		private final List<Component> actionBar = new ArrayList<>();

		private RecordingPlayer(ServerLevel level, String name) {
			super(level, new GameProfile(UUID.randomUUID(), name));
		}

		@Override
		public void displayClientMessage(Component message, boolean actionBar) {
			if (actionBar) {
				this.actionBar.add(message);
			}
		}

		private List<String> keys() {
			return actionBar.stream()
					.map(message -> message.getContents() instanceof TranslatableContents contents
							? contents.getKey() : message.getString())
					.toList();
		}
	}

	/** A Stand user looking south (+z) with no gravity, its Stand summoned, and the mobs it aims at. */
	private static final class StandScene implements AutoCloseable {
		private final GameTestHelper helper;
		private final RecordingPlayer user;
		private final StandType standType;
		private final Vec3 origin;
		private final List<Entity> spawned = new ArrayList<>();
		private StandPower power;
		private StandInstance instance;
		private StandEntity stand;
		private LivingComponentAction standAction;

		private StandScene(GameTestHelper helper, String name, String standId) {
			this.helper = helper;
			user = new RecordingPlayer(helper.getLevel(), name);
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			try {
				helper.assertTrue(standType != null, "Missing Stand type " + standId);
				user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
				user.setYHeadRot(0.0F);
				user.setNoGravity(true);
				helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
				power = PowerClass.STAND.attachGet(user);
				StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
				helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
						"Could not grant " + standId + ": " + inserted.status());
				instance = power.getStandInstance().orElse(null);
				helper.assertTrue(instance != null, "Granted " + standId + " has no Stand instance");
				power.setResolveLevel(4);
				helper.assertTrue(standType.summon(user, power), "Could not summon " + standId);
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "Summoned " + standId + " entity is missing");
				standAction = LivingComponentAction.getComponent(stand);
				// The summon lock skips the action tick, and StandEntity.tick, which counts it down, does not run here.
				stand.summonLockTicks = 0;
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

		// as a player who can press it has it; the scene does not run the user's tick, whose Resolve rule unlocks skills
		private void unlock(String name) {
			power.getCurTypeData()._lockedAbilities.remove(name);
			helper.assertTrue(ability(name).isAbilityUnlocked(power), "setup: " + name + " is still locked");
		}

		// set on the Stand as a press sets it and held by its key
		private EntityActionInstance start(String name) {
			standAction.setAction(null, user, SyncType.NO_SYNC);
			EntityActionInstance action = ability(name).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			standAction.setAction(action, user, SyncType.NO_SYNC);
			EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, PowerClass.STAND, action));
			return action;
		}

		private EntityActionInstance startUntilPerform(String name) {
			EntityActionInstance action = start(name);
			for (int tick = 0; tick < 40 && action.getPhase() != ActionPhase.PERFORM && !action.isOver(); tick++) {
				standAction.tick();
			}
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM && !action.isOver(),
					"setup: the held " + name + " did not reach PERFORM: phase " + action.getPhase() + ", messages " + user.keys());
			return action;
		}

		private void tick(int count) {
			for (int tick = 0; tick < count; tick++) {
				standAction.tick();
			}
		}

		// the current phase run to its length at once, as stand-bearing-creatures' fireHeldAction runs a mob's charge
		private void runPhaseToItsEnd(EntityActionInstance action) {
			action.setPhase(action.getPhase(), Mth.ceil(action.getCurPhaseLength()));
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
			LivingComponentAction.getComponent(user).entityAim.setTarget(target != null ? new ActionTarget(target) : ActionTarget.EMPTY);
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			if (instance != null) {
				for (StandPart part : StandPart.values()) {
					instance.addPart(part);
				}
			}
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			spawned.forEach(Entity::discard);
			user.discard();
		}
	}

	/** A player with a non-Stand power, looking south (+z) with no gravity, and the mobs it aims at. */
	private static final class PowerScene implements AutoCloseable {
		private final GameTestHelper helper;
		private final RecordingPlayer user;
		private final PlayerPower power;
		private final LivingComponentAction component;
		private final Vec3 origin;
		private final List<Entity> spawned = new ArrayList<>();

		private PowerScene(GameTestHelper helper, String name, PlayerPowerType<?> type) {
			this.helper = helper;
			user = new RecordingPlayer(helper.getLevel(), name);
			origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the held-check player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(type);
			component = LivingComponentAction.getComponent(user);
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

		private void pillarman(int stage, PillarmanMode mode) {
			PillarmanData data = PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
			data.setEvolutionStage(stage, user);
			data.setMode(mode, user);
			data.setEnergy(user, 100.0F);
			helper.assertTrue(!data.isStoneFormEnabled(), "setup: Pillar Man stone form is on");
		}

		private void vampire(float blood) {
			VampirismData data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
			data.setVampireFullPower(true, user);
			setBlood(blood);
			helper.assertTrue(data.getCuringStage(user) == 0 && data.hasBlood(user, blood),
					"setup: the vampire is being cured or has not got its blood: stage " + data.getCuringStage(user));
		}

		private void setBlood(float blood) {
			VampirismData data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
			VampirismState.get(user).blood().setCurrent(blood);
			data.setBloodLevel(VampirismState.get(user).blood().current());
		}

		// a player with no power yet, facing the user
		private RecordingPlayer player(String name, double ahead) {
			RecordingPlayer other = new RecordingPlayer(helper.getLevel(), name);
			other.moveTo(origin.x, origin.y, origin.z + ahead, 180.0F, 0.0F);
			other.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(other), "Could not add " + name);
			spawned.add(other);
			PowerClass.PLAYER_POWER.attachGet(other);
			return other;
		}

		private void aim(Entity target) {
			component.entityAim.setTarget(target != null ? new ActionTarget(target) : ActionTarget.EMPTY);
		}

		private EntityActionAbility ability(String name) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered " + name);
			return (EntityActionAbility) found;
		}

		// set as a press sets it and held by its key
		private EntityActionInstance start(String name) {
			EntityActionInstance action = ability(name).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, PowerClass.PLAYER_POWER, action));
			return action;
		}

		private void tick(int count) {
			for (int tick = 0; tick < count; tick++) {
				user.tickCount++;
				component.tick();
			}
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			component.entityAim.setTarget(ActionTarget.EMPTY);
			component.setAction(null, SyncType.NO_SYNC);
			user.removeAllEffects();
			spawned.forEach(Entity::discard);
			user.discard();
		}
	}
}
