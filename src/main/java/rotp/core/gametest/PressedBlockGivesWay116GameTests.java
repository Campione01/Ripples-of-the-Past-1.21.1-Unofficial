package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.impl.stands.crazydiamond.CrazyDAnchorMakeAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDHealAbility.HealingAction;
import rotp.core.impl.stands.hierophant.HierophantBarrierAbility;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
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
import rotp.core.subsystems.target.ActionTarget.TargetType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityAction.overrideVanillaMouseTarget, which PowerBaseImpl.checkTarget ran on the target a Stand entity
 * action was pressed at: for an action whose target requirement took an entity, a pressed block without a collision shape
 * (tall grass, a torch) gave way to a collision ray from the performer, the Stand while it is out, as far as its reach. A
 * mob behind such a block is the target, else the block behind it, else nothing; an action that takes only a block keeps
 * the block. The press carries the client's aim, its vanilla pick, which the scenes set as the press packet stores it.
 * The Stand's eyes are level with its user's, just past the front of the user's box, so its ray runs along the user's
 * crosshair line and does not start inside the user (1.16's ray met the user then; see the scene). Own batches.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PressedBlockGivesWay116GameTests {
	private static final String BATCH = "pressed_block_gives_way_";
	private static final short KEY = 41;

	private PressedBlockGivesWay116GameTests() {}

	/*
	 * A light punch pressed at tall grass with a cow behind it: 1.16 took the cow (the Stand's collision ray passed the
	 * grass), kept it and struck it. Without the aim assist D-core-92's strict crosshair takes the grass's place and meets
	 * the cow too.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "punch_cow")
	public static void punchPressedAtTallGrassStrikesTheCowBehindAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "star_platinum", 0)) {
			BlockPos grass = scene.tallGrassAhead();
			Cow cow = scene.cow(2.0D, false);
			scene.assertInStandReach(cow);
			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				scene.pressAt(new ActionTarget(grass, Direction.NORTH));
				EntityActionInstance punch = scene.start("punch");
				ActionTarget pressed = punch.getTaskTarget(scene.level);
				helper.assertTrue(pressed.getType() == TargetType.ENTITY && pressed.getEntity() == cow, "1.16's punch pressed at"
						+ " tall grass took the cow behind it (overrideVanillaMouseTarget); the port's took " + describe(pressed)
						+ scene.mode() + ", the grass at " + grass);
				ActionTarget struck = scene.strike(punch);
				helper.assertTrue(struck.getType() == TargetType.ENTITY && struck.getEntity() == cow, "1.16's punch pressed at"
						+ " tall grass struck the cow behind it; the port's struck " + describe(struck) + scene.mode());
				scene.stop();
			}
		}
		helper.succeed();
	}

	/*
	 * Grass underfoot, stone under it, nothing else: 1.16's collision ray from the Stand passed the grass and met the stone,
	 * which the punch kept and struck. Without the aim assist the strict crosshair meets the grass first and keeps it.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "punch_ground")
	public static void punchPressedAtGrassUnderfootStrikesTheGroundAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "star_platinum", 90)) {
			BlockPos[] grassOnStone = scene.grassUnderfoot();
			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				BlockPos expected = assist ? grassOnStone[1] : grassOnStone[0];
				scene.pressAt(new ActionTarget(grassOnStone[0], Direction.UP));
				EntityActionInstance punch = scene.start("punch");
				ActionTarget pressed = punch.getTaskTarget(scene.level);
				helper.assertTrue(isBlock(pressed, expected), "The punch pressed at grass underfoot took " + describe(pressed)
						+ scene.mode() + "; 1.16 took the stone under it with the aim assist, the strict crosshair the grass ("
						+ grassOnStone[0] + " on " + grassOnStone[1] + ")");
				ActionTarget struck = scene.strike(punch);
				helper.assertTrue(isBlock(struck, expected), "The punch pressed at grass underfoot struck " + describe(struck)
						+ scene.mode() + ", not " + expected);
				scene.stop();
			}
		}
		helper.succeed();
	}

	/*
	 * Tall grass with nothing behind it in the Stand's reach: 1.16's collision ray met nothing, so the punch kept no
	 * target and its strike aimed again, meeting the grass by its outline. Without the aim assist the strict crosshair
	 * keeps the grass at once.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "punch_air")
	public static void punchPressedAtGrassWithNothingBehindAimsAgainAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "star_platinum", 0)) {
			BlockPos grass = scene.tallGrassAhead();
			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				scene.pressAt(new ActionTarget(grass, Direction.NORTH));
				EntityActionInstance punch = scene.start("punch");
				ActionTarget pressed = punch.getTaskTarget(scene.level);
				helper.assertTrue(assist ? pressed.getType() == TargetType.EMPTY : isBlock(pressed, grass), "The punch pressed"
						+ " at tall grass with nothing behind kept " + describe(pressed) + scene.mode() + "; 1.16 kept nothing"
						+ " with the aim assist, the strict crosshair the grass at " + grass);
				ActionTarget struck = scene.strike(punch);
				helper.assertTrue(isBlock(struck, grass), "1.16's punch struck the grass when its press kept nothing; the"
						+ " port's struck " + describe(struck) + scene.mode() + ", the grass at " + grass);
				scene.stop();
			}
		}
		helper.succeed();
	}

	/*
	 * Crazy Diamond's Heal (TargetRequirement.ENTITY) pressed at a torch with a cow behind it: 1.16 healed the cow. The
	 * rule is not an aim assist: it holds with the assist off too.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "heal")
	public static void healPressedAtATorchHealsTheCowBehindAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "crazy_diamond", 0)) {
			BlockPos torch = scene.torchInTheAir();
			Cow cow = scene.cow(2.0D, false);
			scene.assertInStandReach(cow);
			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				scene.pressAt(new ActionTarget(torch, Direction.NORTH));
				ConditionCheck check = scene.ability("heal").checkSpecificConditions(scene.power);
				helper.assertTrue(check.isPositive(), "1.16's Heal pressed at a torch with a cow behind it went off; the port's"
						+ " refused it (" + why(check) + ")" + scene.mode() + ", the torch at " + torch);
				EntityActionInstance heal = scene.start("heal");
				ActionTarget pressed = heal.getTaskTarget(scene.level);
				helper.assertTrue(heal instanceof HealingAction && pressed.getType() == TargetType.ENTITY
						&& pressed.getEntity() == cow, "1.16's Heal pressed at a torch took the cow behind it; the port's took "
								+ describe(pressed) + scene.mode());
				cow.setHealth(cow.getMaxHealth() - 4.0F);
				float before = cow.getHealth();
				((HealingAction) heal).restoreTarget(pressed, scene.stand);
				helper.assertTrue(cow.getHealth() > before, "Heal pressed at a torch did not heal the cow behind it: "
						+ before + " -> " + cow.getHealth() + scene.mode());
				scene.stop();
			}
		}
		helper.succeed();
	}

	/*
	 * Crazy Diamond's Revert (CrazyDiamondPreviousState, TargetRequirement.NONE) pressed at a torch with a snow golem behind
	 * it, at the Resolve level that lets it revert one: 1.16 took the golem, not the off-hand item (none here).
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "revert")
	public static void revertPressedAtATorchTakesTheSnowGolemBehindAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "crazy_diamond", 0)) {
			scene.power.setResolveLevel(3);
			helper.assertTrue(scene.power.getResolveLevel() >= 3, "Fixture: Crazy Diamond's Resolve level is "
					+ scene.power.getResolveLevel());
			BlockPos torch = scene.torchInTheAir();
			LivingEntity golem = scene.mob(EntityType.SNOW_GOLEM, 2.0D);
			scene.assertInStandReach(golem);
			scene.pressAt(new ActionTarget(torch, Direction.NORTH));
			ConditionCheck check = scene.ability("revert_state").checkSpecificConditions(scene.power);
			helper.assertTrue(check.isPositive(), "1.16's Revert pressed at a torch with a snow golem behind it went off on the"
					+ " golem; the port's refused it (" + why(check) + "), the torch at " + torch);
			EntityActionInstance revert = scene.start("revert_state");
			ActionTarget pressed = revert.getTaskTarget(scene.level);
			helper.assertTrue(pressed.getType() == TargetType.ENTITY && pressed.getEntity() == golem, "1.16's Revert pressed at"
					+ " a torch took the snow golem behind it; the port's took " + describe(pressed));
		}
		helper.succeed();
	}

	/*
	 * Gold Experience's Bone Meal (TargetRequirement.ANY) pressed at a torch with a calf behind it: 1.16 aged the calf.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "bone_meal")
	public static void boneMealPressedAtATorchTakesTheCalfBehindAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "gold_experience", 0)) {
			BlockPos torch = scene.torchInTheAir();
			Cow calf = scene.cow(2.0D, true);
			scene.assertInStandReach(calf);
			scene.pressAt(new ActionTarget(torch, Direction.NORTH));
			ConditionCheck check = scene.ability("bone_meal").checkSpecificConditions(scene.power);
			helper.assertTrue(check.isPositive(), "1.16's Bone Meal pressed at a torch with a calf behind it went off; the"
					+ " port's refused it (" + why(check) + "), the torch at " + torch);
			EntityActionInstance boneMeal = scene.start("bone_meal");
			ActionTarget pressed = boneMeal.getTaskTarget(scene.level);
			helper.assertTrue(pressed.getType() == TargetType.ENTITY && pressed.getEntity() == calf, "1.16's Bone Meal pressed"
					+ " at a torch took the calf behind it; the port's took " + describe(pressed));
		}
		helper.succeed();
	}

	/*
	 * Bone Meal pressed at a sapling with nothing behind it in the Stand's reach: the Stand's collision ray met nothing,
	 * and TargetRequirement.ANY refused no target, without a message (PowerBaseImpl.checkTarget).
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "bone_meal_refused")
	public static void boneMealPressedAtASaplingWithNothingBehindIsRefusedAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "gold_experience", 0)) {
			BlockPos sapling = scene.saplingInTheAir();
			scene.pressAt(new ActionTarget(sapling, Direction.NORTH));
			ConditionCheck check = scene.ability("bone_meal").checkSpecificConditions(scene.power);
			helper.assertTrue(!check.isPositive() && check.getWarning() == null, "1.16's Bone Meal pressed at a sapling with"
					+ " nothing behind it in the Stand's reach was refused without a message; the port's answered "
					+ (check.isPositive() ? "accepted" : why(check)) + ", the sapling at " + sapling);
		}
		helper.succeed();
	}

	/*
	 * Bone Meal pressed at a sapling on a grass block, looking down: the Stand's collision ray passed the sapling and met
	 * the grass block, which Bone Meal took (an IGrowable) and its task held.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "bone_meal_ground")
	public static void boneMealPressedAtASaplingUnderfootTakesTheGrassBlockAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "gold_experience", 90)) {
			BlockPos[] saplingOnGrass = scene.saplingUnderfoot();
			scene.pressAt(new ActionTarget(saplingOnGrass[0], Direction.UP));
			ConditionCheck check = scene.ability("bone_meal").checkSpecificConditions(scene.power);
			helper.assertTrue(check.isPositive(), "1.16's Bone Meal pressed at a sapling on a grass block went off on the"
					+ " grass block; the port's refused it (" + why(check) + ")");
			EntityActionInstance boneMeal = scene.start("bone_meal");
			ActionTarget pressed = boneMeal.getTaskTarget(scene.level);
			helper.assertTrue(isBlock(pressed, saplingOnGrass[1]), "1.16's Bone Meal pressed at a sapling took the grass"
					+ " block under it, which the Stand's collision ray met; the port's took " + describe(pressed)
					+ ", the sapling at " + saplingOnGrass[0]);
		}
		helper.succeed();
	}

	/*
	 * Gold Experience's Heal Other slot (the shift variation of Heal) pressed at a torch with a wounded cow behind it:
	 * 1.16 resolved the slot with the raw vanilla pick before any override (ActionsOverlayGui.resolveVisibleActionInSlot),
	 * and GoldExperienceHealOther.replaceAction made every target that is not an entity Healing Item, which the click
	 * packet carried. At the cow itself the slot stays Heal Other, whose task holds the cow.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "heal_other")
	public static void healOtherSlotPressedAtATorchIsHealingItemAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "gold_experience", 0)) {
			scene.unlock("heal", "heal_other", "healing_item");
			BlockPos torch = scene.torchInTheAir();
			Cow cow = scene.cow(2.0D, false);
			scene.assertInStandReach(cow);
			cow.setHealth(cow.getMaxHealth() - 4.0F);
			scene.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 8));
			scene.pressAt(new ActionTarget(torch, Direction.NORTH));
			Ability atTorch = scene.slot("heal_other");
			helper.assertTrue(atTorch != null && "healing_item".equals(atTorch.name()), "1.16's Heal Other slot pressed at a"
					+ " torch with a wounded cow behind it was Healing Item (replaceAction on the raw pick); the port's is "
					+ (atTorch != null ? atTorch.name() : "empty") + ", the torch at " + torch);
			scene.pressAt(new ActionTarget(cow));
			Ability atCow = scene.slot("heal_other");
			helper.assertTrue(atCow != null && "heal_other".equals(atCow.name()), "1.16's Heal Other slot pressed at a wounded"
					+ " cow was Heal Other; the port's is " + (atCow != null ? atCow.name() : "empty"));
			EntityActionInstance healOther = scene.start(atCow);
			ActionTarget pressed = healOther.getTaskTarget(scene.level);
			helper.assertTrue(pressed.getType() == TargetType.ENTITY && pressed.getEntity() == cow, "1.16's Heal Other pressed"
					+ " at a wounded cow held the cow; the port's holds " + describe(pressed));
		}
		helper.succeed();
	}

	/*
	 * An action that takes its press from the Stand's synced aim (EntityActionInstance.captureActionTargetFromAim):
	 * Hierophant Green's string attack (standKeepsTarget true) pressed at a torch with a cow behind it took the cow.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "string")
	public static void stringAttackPressedAtATorchTakesTheCowBehindAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "hierophant_green", 0)) {
			BlockPos torch = scene.torchInTheAir();
			Cow cow = scene.cow(2.0D, false);
			scene.assertInStandReach(cow);
			scene.pressAt(new ActionTarget(torch, Direction.NORTH));
			EntityActionInstance string = scene.start("string_attack");
			ActionTarget pressed = string.getTaskTarget(scene.level);
			ActionTarget turnedTo = string.standRotationTarget != null ? string.standRotationTarget : ActionTarget.EMPTY;
			helper.assertTrue(pressed.getType() == TargetType.ENTITY && pressed.getEntity() == cow
					&& turnedTo.getEntity() == cow, "1.16's string attack pressed at a torch took the cow behind it; the port's"
							+ " took " + describe(pressed) + " and turned the Stand to " + describe(turnedTo));
		}
		helper.succeed();
	}

	/*
	 * Actions that take only a block (TargetRequirement.BLOCK) kept the pressed torch with a cow behind it: Hierophant
	 * Green's Barrier and Crazy Diamond's block anchor (CrazyDiamondBlockCheckpointMake).
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "barrier")
	public static void barrierStillTakesTheTorchAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "hierophant_green", 0)) {
			BlockPos torch = scene.torchInTheAir();
			Cow cow = scene.cow(2.0D, false);
			scene.assertInStandReach(cow);
			ActionTarget pressedAt = new ActionTarget(torch, Direction.NORTH);
			scene.pressAt(pressedAt);
			ActionTarget barrier = HierophantBarrierAbility.getCurrentBlockTarget(scene.user, scene.stand, scene.level);
			helper.assertTrue(isBlock(barrier, torch), "1.16's Barrier kept the torch it was pressed at; the port's took "
					+ describe(barrier) + ", the torch at " + torch);
			ActionTarget overridden = scene.ability(HierophantBarrierAbility.class).overridePressedTarget(scene.user, pressedAt);
			helper.assertTrue(isBlock(overridden, torch), "The Barrier's press rule let the torch give way to "
					+ describe(overridden));
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "anchor")
	public static void blockAnchorStillTakesTheTorchAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "crazy_diamond", 0)) {
			BlockPos torch = scene.torchInTheAir();
			Cow cow = scene.cow(2.0D, false);
			scene.assertInStandReach(cow);
			ActionTarget pressedAt = new ActionTarget(torch, Direction.NORTH);
			scene.pressAt(pressedAt);
			Method find = method(helper, CrazyDAnchorMakeAbility.class, "findAnchorMakeTarget", LivingEntity.class);
			Object anchor = invoke(find, null, scene.user);
			helper.assertTrue(anchor instanceof ActionTarget aim && isBlock(aim, torch), "1.16's block anchor kept the torch it"
					+ " was pressed at; the port's took " + (anchor instanceof ActionTarget aim ? describe(aim) : anchor)
					+ ", the torch at " + torch);
			ActionTarget overridden = scene.ability(CrazyDAnchorMakeAbility.class).overridePressedTarget(scene.user, pressedAt);
			helper.assertTrue(isBlock(overridden, torch), "The block anchor's press rule let the torch give way to "
					+ describe(overridden));
		}
		helper.succeed();
	}

	/*
	 * 1.16's TimeStop was a StandAction, not a StandEntityAction: no overrideVanillaMouseTarget of its own, so a press of
	 * it kept a pressed torch. The port marks such ports (performsInUsersStandPowerSlot), as add-ons mark theirs.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "stand_action")
	public static void aPortOfAStandActionKeepsTheTorchAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "the_world", 0)) {
			BlockPos torch = scene.torchInTheAir();
			Cow cow = scene.cow(2.0D, false);
			scene.assertInStandReach(cow);
			ActionTarget pressedAt = new ActionTarget(torch, Direction.NORTH);
			TimeStopAbility timeStop = scene.ability(TimeStopAbility.class);
			ActionTarget overridden = timeStop.overridePressedTarget(scene.user, pressedAt);
			helper.assertTrue(timeStop.performsInUsersStandPowerSlot() && isBlock(overridden, torch), "1.16's time stop, a"
					+ " StandAction, kept the torch it was pressed at; the port's press rule let it give way to "
					+ describe(overridden));
		}
		helper.succeed();
	}

	/*
	 * The owner's wall rule (D-core-89) on the ray a pressed block gives way to: tall grass, a stone wall right behind it
	 * and a cow behind the wall, all in the Stand's reach. The punch, Heal and the string attack (each of the three ways a
	 * press reaches the rule) do not take the cow; with walls hiding nothing the same ray would.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "wall_punch")
	public static void punchPressedAtGrassBeforeAWallLeavesTheCowBehindIt(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "star_platinum", 0)) {
			BlockPos grass = scene.tallGrassAhead();
			Cow cow = scene.cowBehindAWall(grass);
			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				scene.pressAt(new ActionTarget(grass, Direction.NORTH));
				EntityActionInstance punch = scene.start("punch");
				ActionTarget pressed = punch.getTaskTarget(scene.level);
				helper.assertTrue(pressed.getEntity() != cow, "The punch pressed at tall grass took the cow behind the stone wall"
						+ " behind the grass" + scene.mode() + "; walls hide entities (D-core-89)");
				scene.stop();
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "wall_heal")
	public static void healPressedAtGrassBeforeAWallLeavesTheCowBehindIt(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "crazy_diamond", 0)) {
			BlockPos grass = scene.tallGrassAhead();
			Cow cow = scene.cowBehindAWall(grass);
			cow.setHealth(cow.getMaxHealth() - 4.0F);
			scene.pressAt(new ActionTarget(grass, Direction.NORTH));
			ConditionCheck check = scene.ability("heal").checkSpecificConditions(scene.power);
			EntityActionInstance heal = scene.start("heal");
			ActionTarget pressed = heal.getTaskTarget(scene.level);
			helper.assertTrue(pressed.getEntity() != cow, "Heal pressed at tall grass took the cow behind the stone wall behind"
					+ " the grass (its check: " + (check.isPositive() ? "accepted" : why(check)) + "); walls hide entities"
					+ " (D-core-89)");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "wall_string")
	public static void stringAttackPressedAtGrassBeforeAWallLeavesTheCowBehindIt(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "hierophant_green", 0)) {
			BlockPos grass = scene.tallGrassAhead();
			Cow cow = scene.cowBehindAWall(grass);
			scene.pressAt(new ActionTarget(grass, Direction.NORTH));
			EntityActionInstance string = scene.start("string_attack");
			ActionTarget pressed = string.getTaskTarget(scene.level);
			helper.assertTrue(pressed.getEntity() != cow && (string.standRotationTarget == null
					|| string.standRotationTarget.getEntity() != cow), "The string attack pressed at tall grass took the cow behind"
							+ " the stone wall behind the grass; walls hide entities (D-core-89)");
		}
		helper.succeed();
	}

	/*
	 * A manually controlled Stand (1.16: the camera, whose pick the press carried, and the performer the override's ray
	 * started from, StandAction.getPerformer) three blocks beside its user, which looks the other way: pressed at tall
	 * grass in front of the Stand with a cow behind it on the Stand's ray, the punch takes the cow and strikes it. Without
	 * the aim assist the Stand's own crosshair line meets it too.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "manual")
	public static void aManuallyControlledStandsPunchAtGrassTakesTheCowOnItsRayAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "star_platinum", 0)) {
			scene.stand.moveTo(scene.origin.x + 3.0D, scene.user.getEyeY() - scene.stand.getEyeHeight(), scene.origin.z, 0, 0);
			scene.stand.setYHeadRot(0);
			scene.stand.setManuallyControlled(true);
			scene.user.setYRot(180.0F);
			scene.user.setYHeadRot(180.0F);
			helper.assertTrue(scene.stand.aimsWithoutUser(), "Fixture: the Stand is not manually controlled");
			BlockPos grass = scene.tallGrassAt(BlockPos.containing(scene.stand.getEyePosition()).south());
			Cow cow = scene.cow(scene.stand.getEyePosition().add(0.0D, 0.0D, 2.0D), false);
			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				scene.pressAt(new ActionTarget(grass, Direction.NORTH));
				EntityActionInstance punch = scene.start("punch");
				ActionTarget pressed = punch.getTaskTarget(scene.level);
				helper.assertTrue(pressed.getEntity() == cow, "1.16's manually controlled Stand's punch pressed at tall grass"
						+ " took the cow behind it on the Stand's ray; the port's took " + describe(pressed) + scene.mode());
				ActionTarget struck = scene.strike(punch);
				helper.assertTrue(struck.getEntity() == cow, "1.16's manually controlled Stand's punch struck the cow; the port's"
						+ " struck " + describe(struck) + scene.mode());
				scene.stop();
			}
		}
		helper.succeed();
	}

	/*
	 * The Stand where it idles beside its user (its own offset, its user's rotation) and a cow on its user's crosshair
	 * line behind tall grass: 1.16's override ray ran from the Stand's eyes, parallel to the user's look, beside the cow,
	 * so it met nothing and the punch kept no target. Without the aim assist the strict crosshair, which runs from the
	 * user's eyes, takes the cow at once.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "idle_offset")
	public static void punchPressedAtGrassWithTheStandAtItsIdleOffsetKeepsNothingAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "star_platinum", 0)) {
			scene.stand.setCanFollowUser(true);
			scene.stand.updatePosition(scene.user);
			Vec3 offset = scene.stand.position().subtract(scene.user.position());
			helper.assertTrue(scene.stand.isFollowingUser() && Math.abs(offset.x) > 0.5D
					&& Math.abs(scene.stand.getYRot() - scene.user.getYRot()) < 1.0E-3F, "Fixture: the Stand is not at its idle"
							+ " offset beside its user, looking the same way: offset " + offset);
			BlockPos grass = scene.tallGrassAhead();
			Cow cow = scene.cow(2.0D, false);
			Vec3 standEyes = scene.stand.getEyePosition();
			Vec3 standRayEnd = standEyes.add(scene.stand.getLookAngle().scale(
					scene.stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)));
			Vec3 userEyes = scene.user.getEyePosition();
			helper.assertTrue(cow.getBoundingBox().clip(standEyes, standRayEnd).isEmpty()
					&& cow.getBoundingBox().clip(userEyes, userEyes.add(scene.user.getLookAngle().scale(5.0D))).isPresent(),
					"Fixture: the cow is not on the user's crosshair line beside the Stand's ray (offset " + offset + ")");
			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				scene.pressAt(new ActionTarget(grass, Direction.NORTH));
				EntityActionInstance punch = scene.start("punch");
				ActionTarget pressed = punch.getTaskTarget(scene.level);
				helper.assertTrue(assist ? pressed.getType() == TargetType.EMPTY : pressed.getEntity() == cow, "The punch pressed"
						+ " at tall grass with the Stand at its idle offset took " + describe(pressed) + scene.mode() + "; 1.16's"
						+ " ray from the Stand passed beside the cow and kept nothing, the strict crosshair takes the cow");
				scene.stop();
			}
		}
		helper.succeed();
	}

	/*
	 * Crazy Diamond's misshaping punch, a 1.16 StandEntityActionModifier with no overrideVanillaMouseTarget: its follow-up
	 * on the running finisher took only an entity under the crosshair. Pressed at a torch with the finisher's target, a
	 * player, right behind it on the Stand's ray (which Heal would take), the key is refused; at the player it is not.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "misshaping")
	public static void misshapingPressedAtATorchKeepsNoEntityAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "crazy_diamond", 0)) {
			scene.power.setResolveLevel(4);
			scene.unlock("heavy_punch", "finisher", "disfiguring_punch");
			scene.stand.summonLockTicks = 0;
			BlockPos torch = scene.torchInTheAir();
			Player enemy = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			enemy.setNoGravity(true);
			enemy.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 2.5D, 180.0F, 0.0F);
			helper.assertTrue(scene.level.addFreshEntity(enemy), "Could not add the other player");
			scene.entities.add(enemy);
			Ability heavy = scene.ability("heavy_punch");
			Ability finisher = scene.ability("finisher");
			Ability misshaping = scene.ability("disfiguring_punch");
			scene.pressAt(new ActionTarget(enemy));
			scene.power.setStamina(scene.power.getMaxStamina());
			scene.stand.setFinisherMeter(1.0F);
			AbilityConditionCheck pressed = scene.press(heavy);
			EntityActionInstance running = LivingComponentAction.getComponent(scene.stand).getAction();
			helper.assertTrue(pressed != null && pressed.ability == finisher && running instanceof StandEntityHeavyPunch
					&& running.getTaskTarget(scene.level).getEntity() == enemy, "Fixture: a full meter did not start the finisher"
							+ " at the other player: " + (pressed != null ? pressed.ability.name() : "nothing") + ", " + running);
			ActionTarget atTorch = new ActionTarget(torch, Direction.NORTH);
			helper.assertTrue(StandEntityAbility.pastBlockWithoutCollision(scene.user, atTorch, true).getEntity() == enemy,
					"Fixture: the Stand's ray past the torch does not meet the other player");
			scene.pressAt(atTorch);
			AbilityConditionCheck key = scene.resolve(heavy);
			helper.assertTrue(key != null && key.ability == misshaping && !key.conditionCheck.isPositive(), "1.16's misshape"
					+ " follow-up pressed at a torch with the finisher's target behind it was refused (a modifier: the torch"
					+ " stayed the target); the port's heavy key is " + (key != null ? key.ability.name() + (key.conditionCheck
							.isPositive() ? " (usable)" : " (refused)") : "nothing"));
			scene.pressAt(new ActionTarget(enemy));
			key = scene.resolve(heavy);
			helper.assertTrue(key != null && key.ability == misshaping && key.conditionCheck.isPositive(), "Fixture: the misshape"
					+ " follow-up at the finisher's target itself is not usable: "
					+ (key != null ? key.ability.name() : "nothing"));
		}
		helper.succeed();
	}

	private static String why(ConditionCheck check) {
		return check.getWarning() != null ? check.getWarning().getString() : "no message";
	}

	private static boolean isBlock(@Nullable ActionTarget target, BlockPos pos) {
		return target != null && target.getType() == TargetType.BLOCK && pos.equals(target.getBlockPos());
	}

	private static String describe(@Nullable ActionTarget target) {
		if (target == null) {
			return "nothing";
		}
		return switch (target.getType()) {
			case ENTITY -> String.valueOf(target.getEntity());
			case BLOCK -> "block " + target.getBlockPos();
			default -> "nothing";
		};
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

	private static Object invoke(Method method, @Nullable Object on, Object... args) {
		try {
			return method.invoke(on, args);
		}
		catch (IllegalAccessException | InvocationTargetException e) {
			throw new IllegalStateException("Fixture: " + method.getName() + " failed", e);
		}
	}

	private static final class Scene implements AutoCloseable {
		final GameTestHelper helper;
		final ServerLevel level;
		final Player user;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		final Vec3 origin;
		final BlockPos feet;
		final List<Entity> entities = new ArrayList<>();
		final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		private boolean assist = true;

		// a Survival user floating at the origin, looking south (pitch 0) or down (90); its Stand's eyes level with the
		// user's, 0.35 south, past the user's box (an arms-only Stand's, 0.15 ahead, start inside it), looking the same way
		Scene(GameTestHelper helper, String standName, float pitch) {
			this.helper = helper;
			level = helper.getLevel();
			feet = helper.absolutePos(new BlockPos(1, 3, 1));
			origin = Vec3.atBottomCenterOf(feet);
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0, pitch);
			user.setYHeadRot(0);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Stand user");
			entities.add(user);
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standName));
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standName);
			helper.assertTrue(type.summon(user, power), "Could not summon " + standName);
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "Missing the " + standName + " entity");
			entities.add(stand);
			stand.setCanFollowUser(false);
			stand.setNoGravity(true);
			stand.moveTo(origin.x, user.getEyeY() - stand.getEyeHeight(), origin.z + 0.35D, 0, pitch);
			stand.setYHeadRot(0);
			power.setStamina(power.getMaxStamina());
			pressAt(ActionTarget.EMPTY);
			aimAssist(true);
		}

		void aimAssist(boolean on) {
			assist = on;
			user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAimAssist = on;
		}

		String mode() {
			return " (aim assist " + (assist ? "on" : "off") + ")";
		}

		// the client's vanilla pick as the press packet stores it: the user's aim, and the Stand's, which takes it when
		// it is a block
		void pressAt(ActionTarget aim) {
			LivingComponentAction.getComponent(user).entityAim.setTarget(aim);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(aim);
		}

		Ability ability(String name) {
			Ability ability = power.getAbility(name);
			helper.assertTrue(ability != null, "Fixture: " + type + " has no " + name);
			return ability;
		}

		<T extends StandEntityAbility> T ability(Class<T> abilityClass) {
			for (Ability ability : power.getMoveset().abilities.values()) {
				if (abilityClass.isInstance(ability)) {
					return abilityClass.cast(ability);
				}
			}
			helper.fail("Fixture: " + type + " has no " + abilityClass.getSimpleName());
			throw new IllegalStateException();
		}

		// as a player who took the skills and reached their Resolve level has them
		void unlock(String... names) {
			for (String name : names) {
				Ability ability = ability(name);
				power.getCurTypeData()._lockedAbilities.remove(name);
				int resolve = ability.getRequiredResolveLevel(power);
				if (resolve > power.getResolveLevel()) {
					power.setResolveLevel(resolve);
				}
				helper.assertTrue(ability.isAbilityUnlocked(power), "Fixture: " + name + " is still locked");
			}
		}

		// what a press of the slot resolves to, on the HUD and in the press packet's handler (AvailableAbilities)
		@Nullable
		Ability slot(String name) {
			AvailableAbilities available = new AvailableAbilities();
			available.update(power, power.getMoveset());
			return available.getContextVariation(name);
		}

		// the action the press starts on the Stand
		EntityActionInstance start(String name) {
			return start(ability(name));
		}

		EntityActionInstance start(Ability ability) {
			String name = ability.name();
			helper.assertTrue(ability instanceof EntityActionType, "Fixture: " + name + " is not an action");
			EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(level, user, stand, null);
			helper.assertTrue(action != null, "Fixture: " + name + " made no action");
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(LivingComponentAction.getCurEntityAction(stand) == action, "Fixture: " + name + " did not start");
			return action;
		}

		void stop() {
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
		}

		// the target the punch strikes now (StandEntityPunch.getPunchTarget, which 1.16's StandEntity.punch aimed)
		ActionTarget strike(EntityActionInstance punch) {
			helper.assertTrue(punch instanceof StandEntityPunch, "Fixture: not a punch: " + punch);
			Object struck = invoke(method(helper, StandEntityPunch.class, "getPunchTarget", StandEntity.class), punch, stand);
			return struck instanceof ActionTarget target ? target : ActionTarget.EMPTY;
		}

		void assertInStandReach(Entity entity) {
			helper.assertTrue(!StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(entity)).isEmpty(level),
					"Fixture: " + entity + " is out of the Stand's reach");
		}

		// tall grass, its lower half on the crosshair line a block ahead, kept up without neighbour updates
		BlockPos tallGrassAhead() {
			return tallGrassAt(BlockPos.containing(user.getEyePosition()).south());
		}

		BlockPos tallGrassAt(BlockPos lower) {
			int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
			block(lower, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), flags);
			block(lower.above(), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER),
					flags);
			helper.assertTrue(level.getBlockState(lower).getCollisionShape(level, lower).isEmpty()
					&& !level.getBlockState(lower).getShape(level, lower).isEmpty(), "Fixture: the tall grass has a collision"
							+ " shape or no outline");
			return lower;
		}

		// a wall torch on the crosshair line with nothing behind it, kept up without neighbour updates
		BlockPos torchInTheAir() {
			BlockPos torch = BlockPos.containing(user.getEyePosition()).south();
			block(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH),
					Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			return torch;
		}

		// an oak sapling on the crosshair line with nothing behind it, kept up without neighbour updates
		BlockPos saplingInTheAir() {
			BlockPos sapling = BlockPos.containing(user.getEyePosition()).south();
			block(sapling, Blocks.OAK_SAPLING.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			return sapling;
		}

		// an oak sapling in the user's feet block, a grass block under it: {sapling, grass block}
		BlockPos[] saplingUnderfoot() {
			BlockPos grass = feet.below();
			block(grass, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
			block(feet, Blocks.OAK_SAPLING.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			return new BlockPos[] { feet, grass };
		}

		// grass in the user's feet block, stone under it: {grass, stone}
		BlockPos[] grassUnderfoot() {
			BlockPos stone = feet.below();
			block(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
			block(feet, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			return new BlockPos[] { feet, stone };
		}

		// a cow, or a calf, with its box centred on the crosshair line, z blocks south of the eyes
		Cow cow(double z, boolean baby) {
			Cow cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "Could not create a cow");
			if (baby) {
				cow.setAge(-24000);
			}
			place(cow, z);
			return cow;
		}

		// a cow with its box centred on the point
		Cow cow(Vec3 at, boolean baby) {
			Cow cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "Could not create a cow");
			if (baby) {
				cow.setAge(-24000);
			}
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(at.x, at.y - cow.getBbHeight() / 2.0D, at.z, 0, 0);
			helper.assertTrue(level.addFreshEntity(cow), "Could not add " + cow);
			entities.add(cow);
			return cow;
		}

		// a stone wall right behind the grass (three blocks high) and a cow behind it on the crosshair line, which the
		// Stand's ray would take if walls hid no entity
		Cow cowBehindAWall(BlockPos grass) {
			BlockPos wall = grass.south();
			for (BlockPos stone : new BlockPos[] { wall.below(), wall, wall.above() }) {
				block(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
			Cow cow = cow(3.0D, false);
			ActionTarget unwalled = StandEntityAbility.pastBlockWithoutCollision(user, new ActionTarget(grass, Direction.NORTH),
					false);
			helper.assertTrue(unwalled.getEntity() == cow, "Fixture: with walls hiding nothing the Stand's ray past the grass"
					+ " does not meet the cow behind the wall: " + describe(unwalled));
			return cow;
		}

		// what the slot resolves to, its condition checked, as the server does on a press (Power.updateAvailableMoves,
		// which caches a tick's moves the test does not tick)
		@Nullable
		AbilityConditionCheck resolve(Ability base) {
			try {
				Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
				cached.setAccessible(true);
				cached.setBoolean(power, false);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Fixture: Power.cachedMovesThisTick is not reachable", e);
			}
			return power.updateAvailableMoves().getContextVariationContainer(base);
		}

		// as ClAbilityInputPacket handles a click: the slot's resolved ability, its condition check, the press and release
		@Nullable
		AbilityConditionCheck press(Ability base) {
			AbilityConditionCheck resolved = resolve(base);
			if (resolved != null && AbilityInput.withConditionCheck(resolved, user, InputMethod.CLICK)) {
				AbilityInput.keyPress(KEY, resolved.ability, user, null, InputMethod.CLICK, 0.0F, BufferingState.clickCanBuffer(),
						base.getAbilityId());
				AbilityInput.keyRelease(KEY, user);
			}
			return resolved;
		}

		LivingEntity mob(EntityType<? extends Mob> entityType, double z) {
			Mob mob = entityType.create(level);
			helper.assertTrue(mob != null, "Could not create " + entityType);
			place(mob, z);
			return mob;
		}

		private void place(Mob mob, double z) {
			mob.setNoAi(true);
			mob.setNoGravity(true);
			mob.moveTo(origin.x, user.getEyeY() - mob.getBbHeight() / 2.0D, origin.z + z, 0, 0);
			helper.assertTrue(level.addFreshEntity(mob), "Could not add " + mob);
			entities.add(mob);
		}

		void block(BlockPos pos, BlockState state, int flags) {
			blocks.putIfAbsent(pos, level.getBlockState(pos));
			level.setBlock(pos, state, flags);
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			stop();
			pressAt(ActionTarget.EMPTY);
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			entities.forEach(Entity::discard);
			List<BlockPos> placed = new ArrayList<>(blocks.keySet());
			for (int i = placed.size() - 1; i >= 0; i--) {
				level.setBlock(placed.get(i), blocks.get(placed.get(i)), Block.UPDATE_CLIENTS);
			}
		}
	}
}
