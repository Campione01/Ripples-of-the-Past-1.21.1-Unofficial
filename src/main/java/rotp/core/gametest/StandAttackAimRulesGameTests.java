package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.compat.v1_21_4.missingmethods._EntitySelector;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.entityattachment.syncheddata.SynchedDataExtended;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility.StandEntityChargedHeavy;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility.TheWorldTSPunch;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionTargetPacket;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.AimingEntity;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

/**
 * The aim assist per attack, as 1.16 kept or refused the target an attack was pressed at: the light punch keeps the
 * block or the mob it was pressed at (StandEntityLightAttack.standKeepsTarget), the heavy punch only a mob it can attack
 * (StandEntityHeavyAttack.standKeepsTarget), the barrage nothing (StandEntityMeleeBarrage.checkStandTarget); a pressed
 * mob the Stand cannot attack is refused (PowerBaseImpl.checkTarget, StandEntity.canAttack). What an attack does not
 * keep, StandEntity.punch aims again with the precision ray (aimWithThisOrUser, precisionRayTrace), whose boxes take a
 * mob within about 1.9 blocks of the eyes before the ground. Without the aim assist every attack strikes what the
 * crosshair meets. Each test has its own batch: the rays reach past the test area.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAttackAimRulesGameTests {
	private static final String BATCH = "stand_attack_aim_rules_";
	private static final short KEY = 177;
	private static final String HEAVY_ATTACK_PUNCH = "heavy_attack_punch_probe";

	private StandAttackAimRulesGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_heavy")
	public static void heavyPunchAtTheGroundHitsTheMobInThePrecisionBoxesAsIn116(GameTestHelper helper) {
		atTheGround(helper, "heavy_punch", InputMethod.CLICK, true);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_barrage")
	public static void barrageAtTheGroundHitsTheMobInThePrecisionBoxesAsIn116(GameTestHelper helper) {
		atTheGround(helper, "barrage", InputMethod.HOLD, true);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_punch")
	public static void punchAtTheGroundStrikesTheBlockItWasPressedAtAsIn116(GameTestHelper helper) {
		atTheGround(helper, "punch", InputMethod.CLICK, true);
	}

	// the charged heavy punch follows the heavy punch's rule (StandEntityHeavyAttack.standKeepsTarget kept only an entity),
	// released as a client's key release ends its charge
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_charged")
	public static void chargedHeavyPunchAtTheGroundHitsTheMobInThePrecisionBoxesAsIn116(GameTestHelper helper) {
		atTheGround(helper, "heavy_charged", InputMethod.HOLD, true);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_charged_off")
	public static void chargedHeavyPunchWithoutAimAssistStrikesTheGroundOnTheCrosshair(GameTestHelper helper) {
		atTheGround(helper, "heavy_charged", InputMethod.HOLD, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_heavy_off")
	public static void heavyPunchWithoutAimAssistStrikesTheGroundOnTheCrosshair(GameTestHelper helper) {
		atTheGround(helper, "heavy_punch", InputMethod.CLICK, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_barrage_off")
	public static void barrageWithoutAimAssistStrikesTheGroundOnTheCrosshair(GameTestHelper helper) {
		atTheGround(helper, "barrage", InputMethod.HOLD, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_punch_off")
	public static void punchWithoutAimAssistStrikesTheGroundOnTheCrosshair(GameTestHelper helper) {
		atTheGround(helper, "punch", InputMethod.CLICK, false);
	}

	// the crosshair on the ground 2 blocks ahead, a cow beside the crosshair inside Star Platinum's precision boxes. For
	// the charged heavy punch the far side of the ground 1 block ahead: its perform starts with the Stand at its idle place
	// behind its user, from whose eyes the ground 2 blocks ahead is out of reach, so that no rule would decide anything there
	private static void atTheGround(GameTestHelper helper, String name, InputMethod method, boolean aimAssist) {
		Scene scene = new Scene(helper, aimAssist);
		boolean charged = name.equals("heavy_charged");
		BlockPos ground = charged ? scene.groundStrip().north() : scene.groundStrip();
		Cow cow = scene.besideTheCrosshair();
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.upFromBottomCenterOf(ground, 1).add(0, 0, charged ? 0.4 : 0));
		helper.assertTrue(scene.vanillaPick().getType() == ActionTarget.TargetType.BLOCK
				&& ground.equals(scene.vanillaPick().getBlockPos()), "Fixture: the crosshair does not meet the ground at " + ground
				+ ": " + scene.vanillaPick());
		helper.assertTrue(StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user).getMainEntity() == cow,
				"Fixture: the precision ray does not take the cow before the ground");
		boolean mobFirst = aimAssist && !name.equals("punch");
		String how = name + (aimAssist ? "" : " without the aim assist");
		float health = cow.getHealth();
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = name.equals("heavy_charged") ? scene.release(name) : scene.press(name, method);
				helper.assertTrue(action[0] instanceof StandEntityPunch || action[0] instanceof StandEntityHeavyPunch
						|| action[0] instanceof StandEntityChargedHeavy
						|| StandEntityBarrageAbility.isDirectionalBarrage(scene.stand, action[0]),
						"Fixture: the " + name + " input started " + action[0]);
				if (mobFirst && name.equals("heavy_punch")) {
					// 1.16 StandEntityHeavyAttack.standKeepsTarget kept only an entity: the pressed ground was refused
					ActionTarget saved = savedTarget(action[0]);
					helper.assertTrue((saved == null || saved.getType() != ActionTarget.TargetType.BLOCK)
							&& (action[0].standRotationTarget == null
									|| action[0].standRotationTarget.getType() != ActionTarget.TargetType.BLOCK),
							"The " + how + " pressed at the ground saved " + saved + " and turns the Stand to "
									+ action[0].standRotationTarget + "; 1.16 kept no block for a heavy punch");
				}
				return false;
			}
			if (tick < 3) {
				return false;
			}
			if (mobFirst && charged && action[0].getPhase() != null
					&& action[0].getPhase().ordinal() < ActionPhase.PERFORM.ordinal()) {
				var standAim = LivingComponentAction.getAim(scene.stand);
				ActionTarget synced = standAim != null ? standAim.getTarget() : ActionTarget.EMPTY;
				ActionTarget reached = StandEntityPunchAbility.validatePunchTarget(scene.stand, synced.copy());
				helper.assertTrue(reached.getType() == ActionTarget.TargetType.BLOCK && ground.equals(reached.getBlockPos()),
						"Fixture: before the perform the Stand's synced aim " + synced + " is not the ground within its reach");
			}
			if (mobFirst && charged && action[0].getPhase() == ActionPhase.PERFORM) {
				// the lunge goes at the target its perform start took: the ground in the Stand's aim is dropped
				ActionTarget lunge = savedTarget(action[0]);
				helper.assertTrue(lunge == null || lunge.getType() != ActionTarget.TargetType.BLOCK, "The " + how
						+ " lunges at " + lunge + " instead of the cow in the precision boxes; 1.16 kept no block for a heavy punch");
			}
			if (tick == 5 && mobFirst && !name.equals("heavy_charged")) {
				// 1.16 placed the Stand from the task target, empty here (the barrage kept none, the heavy punch no block):
				// front3dOffset took the user's look ray, which meets nothing within its reach, so the farthest offset,
				// not a step in toward the cow the precision ray takes
				Vec3 offset = action[0].getSyncedStandOffset();
				double farthest = name.equals("barrage") ? Math.min(1.5, scene.stand.getEffectiveRange())
						: Math.min(2, scene.stand.getMaxRange());
				helper.assertTrue(farthest > 0.6 && offset != null && Math.abs(offset.z - farthest) < 0.01, "The " + how
						+ " placed the Stand at " + offset + " toward the cow it aims at; 1.16 stood along its user's look,"
						+ " at the farthest " + farthest);
			}
			ActionTarget struck = action[0].punchedTarget;
			boolean blockStruck = struck != null && struck.getType() == ActionTarget.TargetType.BLOCK;
			if (mobFirst) {
				helper.assertTrue(!blockStruck && scene.isStone(ground), "The " + how + " struck " + struck
						+ " instead of the cow in the precision boxes, which 1.16's precision ray took before the ground");
				return cow.getHealth() < health && tick > 15;
			}
			if (struck == null || struck.getType() == ActionTarget.TargetType.EMPTY) {
				helper.assertTrue(cow.getHealth() >= health, "The " + how + " hurt the cow beside the crosshair before it struck");
				return false;
			}
			helper.assertTrue(blockStruck && ground.equals(struck.getBlockPos()), "The " + how + " struck " + struck
					+ " instead of the ground on the crosshair" + (aimAssist ? ", the block 1.16's light punch kept from its press" : ""));
			return true;
		}, 60, () -> "The " + how + (mobFirst ? " never hit the cow in the precision boxes" : " never struck the ground"));
	}

	// the user's own wolf on the ground on the crosshair with the ground right behind it: 1.16 refused the pressed wolf
	// and aimed again with the precision ray, which skips it, so the cow beside is hit, never the ground
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "wolf_punch")
	public static void punchAtTheUsersOwnWolfOnTheGroundHitsTheMobBesideAsIn116(GameTestHelper helper) {
		atOwnWolfOnTheGround(helper, "punch", InputMethod.CLICK);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "wolf_heavy")
	public static void heavyPunchAtTheUsersOwnWolfOnTheGroundHitsTheMobBesideAsIn116(GameTestHelper helper) {
		atOwnWolfOnTheGround(helper, "heavy_punch", InputMethod.CLICK);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "wolf_barrage")
	public static void barrageAtTheUsersOwnWolfOnTheGroundHitsTheMobBesideAsIn116(GameTestHelper helper) {
		atOwnWolfOnTheGround(helper, "barrage", InputMethod.HOLD);
	}

	private static void atOwnWolfOnTheGround(GameTestHelper helper, String name, InputMethod method) {
		Scene scene = new Scene(helper, true);
		BlockPos ground = scene.groundStrip();
		Cow cow = scene.besideTheCrosshair();
		Wolf wolf = scene.ownWolf(Vec3.upFromBottomCenterOf(ground, 1));
		// low on its front, so that the crosshair ray goes on into the ground under it
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, wolf.position().add(0, 0.15, -0.25));
		helper.assertTrue(scene.vanillaPick().getMainEntity() == wolf, "Fixture: the vanilla pick is " + scene.vanillaPick());
		ActionTarget behind = StandEntityPunchAbility.crosshairPick(scene.stand, scene.user, 1,
				entity -> entity != scene.stand && StandEntityPunchAbility.canStandHit(scene.stand, entity));
		helper.assertTrue(behind.getType() == ActionTarget.TargetType.BLOCK && ground.equals(behind.getBlockPos()),
				"Fixture: past the wolf the crosshair does not meet the ground under it: " + behind);
		helper.assertTrue(StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user).getMainEntity() == cow,
				"Fixture: the precision ray does not take the cow beside");
		float wolfHealth = wolf.getHealth();
		float health = cow.getHealth();
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				ActionTarget aim = scene.sendClientAims();
				helper.assertTrue(aim.getMainEntity() == wolf, "Fixture: a client's Stand aim, the marker, is " + aim);
				action[0] = scene.press(name, method);
				helper.assertTrue(action[0] != null, "Fixture: the " + name + " input started nothing");
				helper.assertTrue(action[0].standRotationTarget == null || action[0].standRotationTarget.getMainEntity() != wolf,
						"The " + name + " turned the Stand to the user's own wolf it was pressed at, which 1.16 refused");
				if (!name.equals("barrage")) {
					// with no pressed target the Stand stands off at its farthest, nothing being within 2 blocks on the look;
					// stepping up to the wolf 2 blocks ahead would leave it within a block of its user
					Vec3 offset = action[0].getSyncedStandOffset();
					helper.assertTrue(offset != null && offset.z > 1.0, "The " + name + " stepped the Stand up to the"
							+ " user's own wolf it was pressed at, which 1.16 refused: offset " + offset);
				}
				return false;
			}
			if (tick < 3) {
				return false;
			}
			ActionTarget struck = action[0].punchedTarget;
			helper.assertTrue(wolf.getHealth() >= wolfHealth, "The " + name + " hurt the user's own wolf");
			helper.assertTrue((struck == null || struck.getType() != ActionTarget.TargetType.BLOCK) && scene.isStone(ground),
					"The " + name + " struck " + struck + ", the ground behind the user's own wolf; 1.16's precision ray"
							+ " took the cow beside once the wolf was refused");
			return cow.getHealth() < health && tick > 15;
		}, 60, () -> "The " + name + " at the user's own wolf on the ground never hit the cow beside it");
	}

	// keep attack target on: the locked barrage keeps the target it was pressed at, but 1.16 refused a pressed entity the
	// Stand cannot attack (PowerBaseImpl.checkTarget, StandEntity.canAttack), so the user's own wolf is neither saved nor
	// turned to
	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH + "wolf_locked_barrage")
	public static void lockedBarragePressedAtTheUsersOwnWolfKeepsNothingAsIn116(GameTestHelper helper) {
		Scene scene = new Scene(helper, true);
		scene.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
		Wolf wolf = scene.ownWolf(scene.origin.add(0, 0.6, 2.0));
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, wolf.getBoundingBox().getCenter());
		helper.assertTrue(scene.vanillaPick().getMainEntity() == wolf, "Fixture: the vanilla pick is " + scene.vanillaPick());
		float health = wolf.getHealth();
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = scene.press("barrage", InputMethod.HOLD);
				helper.assertTrue(action[0] instanceof StandEntityBarrageAbility.StandEntityBarrage
						&& !StandEntityBarrageAbility.isDirectionalBarrage(scene.stand, action[0]),
						"Fixture: the barrage input started " + action[0] + ", not the locked barrage");
				ActionTarget saved = savedTarget(action[0]);
				helper.assertTrue((saved == null || saved.getMainEntity() != wolf) && (action[0].standRotationTarget == null
						|| action[0].standRotationTarget.getMainEntity() != wolf), "The locked barrage pressed at the user's own"
								+ " wolf saved " + saved + " and turns the Stand to " + action[0].standRotationTarget
								+ "; 1.16 refused a pressed entity the Stand cannot attack");
				return false;
			}
			if (tick < 3) {
				return false;
			}
			helper.assertTrue(wolf.getHealth() >= health, "The locked barrage hurt the user's own wolf");
			return tick > 12;
		}, 30, () -> "The locked barrage test timed out");
	}

	// 1.16 kept the mob a light or heavy punch was pressed at as the task target while it stayed in range
	// (StandEntityTask.keepTarget, standKeepsTarget of StandEntityLightAttack and StandEntityHeavyAttack), turned the Stand
	// to it (StandEntityTask.rotateStand) and struck it while the Stand could reach it (StandEntity.aimWithThisOrUser), not
	// what the crosshair met by then. "Keep attack target" is off. Small precision boxes, so that only the kept target
	// finds the mob once it is off the crosshair, and ground behind it: a strike that aimed again would take the ground
	// the crosshair meets then, never the Stand's own eye ray, which faces the pressed mob.
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "kept_mob_punch")
	public static void punchStrikesTheMobItWasPressedAtThatStepsAsideAsIn116(GameTestHelper helper) {
		pressedMobStepsAside(helper, "punch");
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "kept_mob_heavy")
	public static void heavyPunchStrikesTheMobItWasPressedAtThatStepsAsideAsIn116(GameTestHelper helper) {
		pressedMobStepsAside(helper, "heavy_punch");
	}

	// a punch standing for a 1.16 heavy attack (StandEntityPunchAbility.heavyAttack: King Crimson's Ground Punch and
	// Desperate Eyejab) keeps the mob it was pressed at as StandEntityHeavyAttack.standKeepsTarget did
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "kept_mob_heavy_attack_punch")
	public static void heavyAttackPunchStrikesTheMobItWasPressedAtThatStepsAsideAsIn116(GameTestHelper helper) {
		pressedMobStepsAside(helper, HEAVY_ATTACK_PUNCH);
	}

	private static void pressedMobStepsAside(GameTestHelper helper, String name) {
		Scene scene = new Scene(helper, true);
		scene.precision(0);
		scene.groundStrip();
		Cow cow = scene.cow(0, 2.0);
		// low on the cow, so that past it the crosshair meets the ground strip
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.position().add(0, 0.4, 0));
		helper.assertTrue(scene.vanillaPick().getMainEntity() == cow, "Fixture: the vanilla pick is " + scene.vanillaPick());
		float health = cow.getHealth();
		TargetWatcher watcher = scene.watchStand();
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = name.equals(HEAVY_ATTACK_PUNCH) ? scene.startHeavyAttackPunch() : scene.press(name, InputMethod.CLICK);
				helper.assertTrue((action[0] instanceof StandEntityPunch || action[0] instanceof StandEntityHeavyPunch)
						&& action[0].punchedTarget == null, "Fixture: the " + name + " input started " + action[0]
								+ ", or it struck at once");
				// a step aside within the Stand's reach, off the crosshair; the client sends its aims again
				cow.moveTo(cow.getX() + 0.9, cow.getY(), cow.getZ(), 0, 0);
				scene.sendClientAims();
				helper.assertTrue(scene.vanillaPick().getType() == ActionTarget.TargetType.BLOCK
						&& StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user).getType()
								== ActionTarget.TargetType.BLOCK,
						"Fixture: the user's aims meet " + scene.vanillaPick() + " and "
								+ StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user)
								+ ", not the ground behind the cow that stepped aside");
				return false;
			}
			if (tick < 3) {
				return false;
			}
			ActionTarget struck = action[0].punchedTarget;
			if (struck == null) {
				ActionTarget turnedTo = action[0].standRotationTarget;
				helper.assertTrue(turnedTo != null && turnedTo.getMainEntity() == cow, "The " + name + " turns the Stand to "
						+ turnedTo + " instead of the cow it was pressed at; 1.16 turned the Stand to its task target");
				return false;
			}
			helper.assertTrue(struck.getMainEntity() == cow && cow.getHealth() < health, "The " + name + " struck " + struck
					+ " instead of the cow it was pressed at, which stepped aside within the Stand's reach (Stand at "
					+ scene.stand.position() + ", cow at " + cow.position() + "); 1.16 struck the task target the press kept");
			List<TrEntityActionTargetPacket> sent = watcher.sentFor(action[0]);
			helper.assertTrue(sent.isEmpty(), "The " + name + " that struck the cow it kept sent tracking clients " + sent
					+ "; 1.16's StandEntity.setTaskTarget sent nothing when the strike's target was the task target");
			return true;
		}, 60, () -> "The " + name + " never struck");
	}

	// 1.16 StandEntityTask.tick kept the task target while it stayed within the action's range (keepTarget,
	// Action.checkRangeAndTarget: 8 blocks, 4 unseen), and StandEntity.punch struck it if it was in reach at the strike:
	// the mob a heavy punch was pressed at leaves the Stand's reach for two ticks of the windup and comes back, off the
	// crosshair, and is struck. Only the server judged it, and it sent tracking clients the task target only when it
	// changed (StandEntity.setTaskTarget, TrStandTaskTargetPacket): nothing here
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "kept_mob_heavy_leaves_reach")
	public static void heavyPunchStrikesTheMobItWasPressedAtThatLeftTheStandsReachInTheWindupAsIn116(GameTestHelper helper) {
		pressedMobLeavesInTheWindup(helper, "heavy_punch", 4.0, true);
	}

	// beyond the action's range 1.16 dropped the task target for good: the Stand no longer turns to the mob, and the strike
	// aims again at what the crosshair meets, though the mob is back within reach. Tracking clients were sent the empty
	// task target, so their Stand stopped turning to the mob too
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "kept_mob_heavy_leaves_range")
	public static void heavyPunchDropsTheMobItWasPressedAtThatLeftTheActionsRangeInTheWindupAsIn116(GameTestHelper helper) {
		pressedMobLeavesInTheWindup(helper, "heavy_punch", 12.0, false);
	}

	// the same task rule on the punch action, through the long windup of a punch standing for a 1.16 heavy attack (King
	// Crimson's Ground Punch and Desperate Eyejab). Its cry check reads the target the press kept as it is, as
	// StandEntityLightAttack.getSounds read the task target, and never aims again or saves in its place
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "kept_mob_heavy_attack_punch_leaves_reach")
	public static void heavyAttackPunchStrikesTheMobItWasPressedAtThatLeftTheStandsReachInTheWindupAsIn116(
			GameTestHelper helper) {
		pressedMobLeavesInTheWindup(helper, HEAVY_ATTACK_PUNCH, 4.0, true);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "kept_mob_heavy_attack_punch_leaves_range")
	public static void heavyAttackPunchDropsTheMobItWasPressedAtThatLeftTheActionsRangeInTheWindupAsIn116(
			GameTestHelper helper) {
		pressedMobLeavesInTheWindup(helper, HEAVY_ATTACK_PUNCH, 12.0, false);
	}

	private static void pressedMobLeavesInTheWindup(GameTestHelper helper, String name, double away, boolean staysInRange) {
		Scene scene = new Scene(helper, true);
		scene.precision(0);
		scene.groundStrip();
		Cow cow = scene.cow(0, 2.0);
		Vec3 pressedAt = cow.position();
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.position().add(0, 0.4, 0));
		helper.assertTrue(scene.vanillaPick().getMainEntity() == cow, "Fixture: the vanilla pick is " + scene.vanillaPick());
		String how = "The " + name + " whose pressed cow left " + (staysInRange ? "the Stand's reach" : "the action's range")
				+ " in the windup";
		float health = cow.getHealth();
		TargetWatcher watcher = scene.watchStand();
		EntityActionInstance[] action = new EntityActionInstance[1];
		EntityActionInstance[] client = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = name.equals(HEAVY_ATTACK_PUNCH) ? scene.startHeavyAttackPunch() : scene.press(name, InputMethod.CLICK);
				helper.assertTrue((action[0] instanceof StandEntityPunch || action[0] instanceof StandEntityHeavyPunch)
						&& action[0].punchedTarget == null, "Fixture: the " + name + " input started " + action[0]
								+ ", or it struck at once");
				client[0] = clientCopy(action[0], scene.stand);
				ActionTarget shown = StandEntityPunchAbility.keptStrikeTarget(scene.stand, client[0]);
				helper.assertTrue(shown != null && shown.getMainEntity() == cow, "Fixture: a client's copy of the " + name
						+ " shows " + shown + ", not the cow it was pressed at");
				return false;
			}
			if (tick < 3) {
				return false;
			}
			if (tick == 5) {
				cow.moveTo(pressedAt.x, pressedAt.y, pressedAt.z + away, 0, 0);
				ActionTarget atCow = new ActionTarget(cow);
				helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(scene.stand, atCow).isEmpty(helper.getLevel())
						&& ActionTargetRange.isTargetWithinRange(scene.stand, atCow, helper.getLevel()) == staysInRange,
						"Fixture: the cow " + away + " blocks further is within the Stand's reach, or "
								+ (staysInRange ? "out of" : "within") + " the action's range");
				if (staysInRange && action[0] instanceof StandEntityPunch) {
					helper.assertTrue(!scene.stand.isArmsOnlyMode() && scene.stand.getFinisherMeter() <= 0,
							"Fixture: the Stand is arms-only or has finisher, so the punch would cry at nothing");
					boolean cries = playsStandCry(action[0], scene.stand);
					ActionTarget saved = savedTarget(action[0]);
					ActionTarget turnedTo = action[0].standRotationTarget;
					helper.assertTrue(cries && saved != null && saved.getMainEntity() == cow && turnedTo != null
							&& turnedTo.getMainEntity() == cow, how + ": checking its cry " + (cries ? "plays it" : "plays none")
									+ ", and it then keeps " + saved + " and turns the Stand to " + turnedTo + "; 1.16 cried at the"
									+ " task target the press kept, read as it was (StandEntityLightAttack.getSounds)");
				}
			}
			if (tick == 7) {
				helper.assertTrue(action[0].punchedTarget == null && action[0].getPhase() == ActionPhase.WINDUP,
						"Fixture: the " + name + " struck, or left its windup, while the cow was away");
				ActionTarget turnedTo = action[0].standRotationTarget;
				List<TrEntityActionTargetPacket> sent = watcher.sentFor(action[0]);
				if (staysInRange) {
					helper.assertTrue(turnedTo != null && turnedTo.getMainEntity() == cow, how + " turns the Stand to "
							+ turnedTo + "; 1.16 kept its task target while it was within the action's range");
					helper.assertTrue(sent.isEmpty(), how + " sent tracking clients " + sent + " while it kept the cow; 1.16"
							+ " sent the task target only when it changed (StandEntity.setTaskTarget)");
				}
				else {
					helper.assertTrue(turnedTo == null || turnedTo.getMainEntity() != cow, how + " still turns the Stand to"
							+ " it; 1.16 dropped the task target out of the action's range (StandEntityTask.tick)");
					helper.assertTrue(sent.size() == 1 && sent.get(0).keptTarget() == null && sent.get(0).rotationTarget() == null
							&& sent.get(0).aimAs() == AimingEntity.CAMERA_ENTITY, how + " sent tracking clients " + sent
									+ "; 1.16 sent them the emptied task target once (StandEntity.setTaskTarget,"
									+ " TrStandTaskTargetPacket)");
					TrEntityActionTargetPacket drop = sent.get(0);
					client[0].setTargetsFromServer(drop.keptTarget(), drop.rotationTarget(), drop.aimAs(), drop.taskTarget());
					ActionTarget shown = StandEntityPunchAbility.keptStrikeTarget(scene.stand, client[0]);
					helper.assertTrue(shown == null && client[0].standRotationTarget == null && savedTarget(client[0]) == null
							&& client[0].aimAs == AimingEntity.CAMERA_ENTITY, "A client that took what the server sent still"
									+ " shows " + shown + ", turns the Stand to " + client[0].standRotationTarget + " and aims as "
									+ client[0].aimAs + "; 1.16 clients took the emptied task target");
				}
				// back within reach, off the crosshair, which meets the ground behind it
				cow.moveTo(pressedAt.x + 0.9, pressedAt.y, pressedAt.z, 0, 0);
				scene.sendClientAims();
				helper.assertTrue(scene.vanillaPick().getType() == ActionTarget.TargetType.BLOCK,
						"Fixture: the user's crosshair meets " + scene.vanillaPick() + ", not the ground behind the cow");
				return false;
			}
			ActionTarget struck = action[0].punchedTarget;
			if (struck == null) {
				return false;
			}
			if (staysInRange) {
				helper.assertTrue(struck.getMainEntity() == cow && cow.getHealth() < health, how + " and came back struck "
						+ struck + " (Stand at " + scene.stand.position() + ", cow at " + cow.position() + "); 1.16 kept"
						+ " the task target within the action's range and struck it in reach");
			}
			else {
				helper.assertTrue(struck.getMainEntity() != cow, how + " and came back struck it; 1.16 had dropped it and"
						+ " aimed again at the strike");
			}
			return true;
		}, 60, () -> "The " + name + " never struck");
	}

	// the red aim marker (the owner's opt-in of 2026-09-13) shows what a punch keeps while it winds up and strikes, the
	// target 1.16's task held and turned the Stand to (StandAimMarker draws StandEntityPunchAbility.keptStrikeTarget). From
	// the recovery on the punch aims as its user's camera again, so a client's Stand aim and the marker follow the user's
	// crosshair, what the next press takes (1.16 EntityStandType.clientHitResult)
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "marker_kept_punch")
	public static void aimMarkerShowsThePunchsKeptTargetThenTheUsersAimFromItsRecovery(GameTestHelper helper) {
		markerOverAPunch(helper, "punch", true);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "marker_kept_heavy")
	public static void aimMarkerShowsTheHeavyPunchsKeptTargetThenTheUsersAimFromItsRecovery(GameTestHelper helper) {
		markerOverAPunch(helper, "heavy_punch", true);
	}

	// a heavy punch pressed at the ground keeps nothing: through its windup the marker shows the user's aim, which the
	// strike takes
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "marker_nothing_kept_heavy")
	public static void aimMarkerFollowsTheUsersAimThroughAHeavyPunchThatKeepsNothing(GameTestHelper helper) {
		markerOverAPunch(helper, "heavy_punch", false);
	}

	private static void markerOverAPunch(GameTestHelper helper, String name, boolean atCow) {
		Scene scene = new Scene(helper, true);
		scene.precision(0);
		BlockPos ground = scene.groundStrip();
		Cow cow = scene.cow(0, 2.0);
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, atCow ? cow.getBoundingBox().getCenter()
				: Vec3.upFromBottomCenterOf(ground.north(), 1));
		helper.assertTrue(atCow ? scene.vanillaPick().getMainEntity() == cow
				: scene.vanillaPick().getType() == ActionTarget.TargetType.BLOCK, "Fixture: the vanilla pick is "
						+ scene.vanillaPick());
		String how = "The aim marker over the " + name + (atCow ? " pressed at a cow" : " pressed at the ground");
		boolean[] seen = new boolean[3];
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick < 3) {
				return false;
			}
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = scene.press(name, InputMethod.CLICK);
				helper.assertTrue(action[0] instanceof StandEntityPunch || action[0] instanceof StandEntityHeavyPunch,
						"Fixture: the " + name + " input started " + action[0]);
			}
			ActionPhase phase = action[0].getPhase();
			ActionTarget marker = StandEntityPunchAbility.keptStrikeTarget(scene.stand, action[0]);
			if (phase == ActionPhase.WINDUP || phase == ActionPhase.PERFORM) {
				seen[phase == ActionPhase.WINDUP ? 0 : 1] = true;
				if (atCow) {
					helper.assertTrue(marker != null && marker.getMainEntity() == cow, how + " shows " + marker + " in its "
							+ phase + " instead of the cow it keeps");
				}
				else {
					helper.assertTrue(marker == null && action[0].aimAs == AimingEntity.CAMERA_ENTITY, how + " shows "
							+ marker + " with the Stand aiming as " + action[0].aimAs + " in its " + phase + "; it keeps nothing,"
							+ " so the marker shows the user's aim, which its strike takes");
					return phase == ActionPhase.PERFORM;
				}
				return false;
			}
			if (phase == ActionPhase.RECOVERY) {
				seen[2] = true;
				helper.assertTrue(marker == null && action[0].aimAs == AimingEntity.CAMERA_ENTITY, how + " shows " + marker
						+ " with the Stand aiming as " + action[0].aimAs + " in its recovery; from there it follows the user's"
						+ " crosshair, what the next press takes");
				helper.assertTrue(seen[0] && seen[1], "Fixture: the " + name + " was not seen winding up and striking");
				return true;
			}
			return false;
		}, 60, () -> how + " never reached " + (atCow ? "the recovery" : "the strike"));
	}

	// 1.16 kept the block a light punch was pressed at too (StandEntityLightAttack.standKeepsTarget): its user turns from
	// the ground to a cow before the swing, and the punch strikes the ground
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "kept_block_punch")
	public static void punchStrikesTheBlockItWasPressedAtAfterItsUserTurnsAsIn116(GameTestHelper helper) {
		Scene scene = new Scene(helper, true);
		BlockPos ground = scene.groundStrip();
		Cow cow = scene.cow(-1.0, 1.6);
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.upFromBottomCenterOf(ground, 1));
		helper.assertTrue(scene.vanillaPick().getType() == ActionTarget.TargetType.BLOCK
				&& ground.equals(scene.vanillaPick().getBlockPos()), "Fixture: the crosshair does not meet the ground at "
						+ ground + ": " + scene.vanillaPick());
		float health = cow.getHealth();
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = scene.press("punch", InputMethod.CLICK);
				helper.assertTrue(action[0] instanceof StandEntityPunch && action[0].punchedTarget == null,
						"Fixture: the punch input started " + action[0] + ", or it struck at once");
				scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
				scene.sendClientAims();
				helper.assertTrue(scene.vanillaPick().getMainEntity() == cow, "Fixture: the turned user's vanilla pick is "
						+ scene.vanillaPick());
				return false;
			}
			if (tick < 3) {
				return false;
			}
			helper.assertTrue(cow.getHealth() >= health, "The punch pressed at the ground hurt the cow its user turned to;"
					+ " 1.16 struck the block the press kept");
			ActionTarget struck = action[0].punchedTarget;
			if (struck == null) {
				ActionTarget turnedTo = action[0].standRotationTarget;
				helper.assertTrue(turnedTo != null && turnedTo.getType() == ActionTarget.TargetType.BLOCK
						&& ground.equals(turnedTo.getBlockPos()), "The punch turns the Stand to " + turnedTo
								+ " instead of the ground it was pressed at; 1.16 turned the Stand to its task target");
				return false;
			}
			helper.assertTrue(struck.getType() == ActionTarget.TargetType.BLOCK && ground.equals(struck.getBlockPos()),
					"The punch pressed at the ground struck " + struck + " after its user turned; 1.16 struck the block"
							+ " the press kept");
			return tick > 15;
		}, 60, () -> "The punch pressed at the ground never struck it");
	}

	// 1.16 StandEntityHeavyAttack.standKeepsTarget kept only an entity, whatever the aim: a heavy punch pressed at the
	// ground (the crosshair's pick without the aim assist) kept no target, so the Stand did not turn to the ground and
	// StandEntity.punch aimed again at the strike: the cow its user turns to in the windup is struck
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_then_cow_heavy_off")
	public static void heavyPunchWithoutAimAssistPressedAtTheGroundStrikesTheCowItsUserTurnsToAsIn116(GameTestHelper helper) {
		groundThenCow(helper, false, scene -> scene.press("heavy_punch", InputMethod.CLICK),
				"heavy punch without the aim assist");
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_then_cow_heavy_attack_punch")
	public static void heavyAttackPunchPressedAtTheGroundStrikesTheCowItsUserTurnsToAsIn116(GameTestHelper helper) {
		groundThenCow(helper, true, Scene::startHeavyAttackPunch, "punch standing for a 1.16 heavy attack");
	}

	// Only the server aimed the strike (StandEntity.punch) and sent tracking clients its target once it changed
	// (setTaskTarget, TrStandTaskTargetPacket); a client struck at the task target as the server had sent it. So the
	// copy a client holds strikes at nothing of its own, and turns the Stand to the cow once the server's target comes.
	private static void groundThenCow(GameTestHelper helper, boolean aimAssist, Function<Scene, EntityActionInstance> start,
			String how) {
		Scene scene = new Scene(helper, aimAssist);
		scene.precision(0);
		BlockPos ground = scene.groundStrip();
		Cow cow = scene.cow(-1.0, 1.6);
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.upFromBottomCenterOf(ground, 1));
		helper.assertTrue(scene.vanillaPick().getType() == ActionTarget.TargetType.BLOCK
				&& ground.equals(scene.vanillaPick().getBlockPos()), "Fixture: the crosshair does not meet the ground at "
						+ ground + ": " + scene.vanillaPick());
		float health = cow.getHealth();
		TargetWatcher watcher = scene.watchStand();
		EntityActionInstance[] action = new EntityActionInstance[1];
		List<ClientStrike> clients = new ArrayList<>();
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = start.apply(scene);
				helper.assertTrue((action[0] instanceof StandEntityPunch || action[0] instanceof StandEntityHeavyPunch)
						&& action[0].punchedTarget == null, "Fixture: the " + how + " started " + action[0]
								+ ", or it struck at once");
				ActionTarget saved = savedTarget(action[0]);
				ActionTarget turnedTo = action[0].standRotationTarget;
				helper.assertTrue((saved == null || saved.getType() != ActionTarget.TargetType.BLOCK)
						&& (turnedTo == null || turnedTo.getType() != ActionTarget.TargetType.BLOCK), "The " + how
								+ " pressed at the ground saved " + saved + " and turns the Stand to " + turnedTo
								+ "; 1.16 kept no block for a heavy attack (StandEntityHeavyAttack.standKeepsTarget)");
				scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
				scene.sendClientAims();
				helper.assertTrue(scene.vanillaPick().getMainEntity() == cow, "Fixture: the turned user's vanilla pick is "
						+ scene.vanillaPick());
				return false;
			}
			if (tick < 3) {
				return false;
			}
			ActionTarget struck = action[0].punchedTarget;
			List<TrEntityActionTargetPacket> sent = watcher.sentFor(action[0]);
			if (struck == null) {
				ActionTarget turnedTo = action[0].standRotationTarget;
				helper.assertTrue(turnedTo == null || turnedTo.getType() != ActionTarget.TargetType.BLOCK, "The " + how
						+ " turns the Stand to " + turnedTo + ", the ground it was pressed at; 1.16 kept no block for a heavy"
						+ " attack");
				helper.assertTrue(sent.isEmpty(), "The " + how + " sent tracking clients " + sent + " before it struck,"
						+ " keeping and dropping nothing");
				// what a client holds of the action until the server strikes
				clients.clear();
				clients.addAll(clientCopies(action[0], scene.stand));
				return false;
			}
			helper.assertTrue(struck.getMainEntity() == cow && cow.getHealth() < health && scene.isStone(ground), "The "
					+ how + " pressed at the ground struck " + struck + " after its user turned to the cow (Stand at "
					+ scene.stand.position() + ", cow at " + cow.position() + "); 1.16 kept no block for a heavy attack and"
					+ " aimed again at the strike (StandEntity.punch)");
			helper.assertTrue(sent.size() == 1 && entityOf(helper, sent.get(0).keptTarget()) == cow
					&& entityOf(helper, sent.get(0).rotationTarget()) == cow && sent.get(0).aimAs() == action[0].aimAs,
					"The " + how + " that struck the cow sent tracking clients " + sent + "; 1.16 sent them the strike's"
							+ " target once, as it changed the task target (StandEntity.setTaskTarget, TrStandTaskTargetPacket)");
			TrEntityActionTargetPacket received = received(helper, sent.get(0));
			helper.assertTrue(!clients.isEmpty(), "Fixture: no client copy of the " + how + " was taken before it struck");
			for (ClientStrike client : clients) {
				EntityActionInstance copy = (EntityActionInstance) client;
				String who = "A client's " + copy.getClass().getSimpleName() + " of the " + how;
				ActionTarget aimed = client.strikeTarget(scene.stand);
				ActionTarget saved = savedTarget(copy);
				helper.assertTrue(aimed.isEmpty(helper.getLevel()) && saved == null && copy.standRotationTarget == null,
						who + " strikes at " + aimed + " of its own, then keeps " + saved + " and turns the Stand to "
								+ copy.standRotationTarget + "; a 1.16 client struck at the task target the server had sent,"
								+ " none here (StandEntity.punch aimed on the server only)");
				copy.setTargetsFromServer(received.keptTarget(), received.rotationTarget(), received.aimAs(), received.taskTarget());
				setPhase(copy, ActionPhase.PERFORM);
				ActionTarget shown = StandEntityPunchAbility.keptStrikeTarget(scene.stand, copy);
				ActionTarget held = client.strikeTarget(scene.stand);
				helper.assertTrue(shown != null && shown.getMainEntity() == cow && held.getMainEntity() == cow
						&& copy.standRotationTarget != null && copy.standRotationTarget.getMainEntity() == cow,
						who + " that took what the server sent shows " + shown + ", holds " + held + " and turns the Stand to "
								+ copy.standRotationTarget + " instead of the cow the server struck");
			}
			return true;
		}, 60, () -> "The " + how + " pressed at the ground never struck");
	}

	// 1.16 StandEntityAction.checkStandTarget kept only a living entity the Stand could attack: a punch or heavy punch
	// pressed at a boat had no task target (PowerBaseImpl.checkTarget emptied it, StandEntityTask.keepTarget refused it),
	// so the Stand did not turn to the boat and StandEntity.punch aimed again when it struck: the cow its user turns to
	// before the swing is struck
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "boat_then_cow_punch")
	public static void punchPressedAtABoatStrikesTheCowItsUserTurnsToAsIn116(GameTestHelper helper) {
		pressedAtThenCow(helper, "punch", InputMethod.CLICK, EntityType.BOAT, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "boat_then_cow_heavy")
	public static void heavyPunchPressedAtABoatStrikesTheCowItsUserTurnsToAsIn116(GameTestHelper helper) {
		pressedAtThenCow(helper, "heavy_punch", InputMethod.CLICK, EntityType.BOAT, false);
	}

	// keep attack target on: the locked barrage keeps the target it was pressed at (the owner's setting), but no entity
	// 1.16 would not have kept as a task target, so its hits go where its user aims
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "boat_then_cow_locked_barrage")
	public static void lockedBarragePressedAtABoatStrikesTheCowItsUserTurnsTo(GameTestHelper helper) {
		pressedAtThenCow(helper, "barrage", InputMethod.HOLD, EntityType.BOAT, false);
	}

	// an armor stand is a living entity the Stand can attack (canStandTargetEntity, StandEntity.canAttack): 1.16 kept it,
	// and the punch strikes it after its user turns to the cow
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "armor_stand_then_cow_punch")
	public static void punchStrikesTheArmorStandItWasPressedAtAfterItsUserTurnsAsIn116(GameTestHelper helper) {
		pressedAtThenCow(helper, "punch", InputMethod.CLICK, EntityType.ARMOR_STAND, true);
	}

	private static void pressedAtThenCow(GameTestHelper helper, String name, InputMethod method, EntityType<?> type,
			boolean keptAsIn116) {
		Scene scene = new Scene(helper, true);
		boolean barrage = name.equals("barrage");
		if (barrage) {
			scene.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
		}
		scene.precision(0);
		Cow cow = scene.cow(-1.0, 1.6);
		Entity pressed = scene.entity(type, 0.8, 2.0);
		String what = pressed.getType().getDescription().getString();
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, pressed.getBoundingBox().getCenter());
		helper.assertTrue(scene.vanillaPick().getMainEntity() == pressed
				&& StandEntityPunchAbility.canStandHit(scene.stand, pressed), "Fixture: the vanilla pick is "
						+ scene.vanillaPick() + ", or the Stand cannot hit the " + what);
		String how = "The " + (barrage ? "locked barrage" : name) + " pressed at the " + what;
		float health = cow.getHealth();
		TargetWatcher watcher = scene.watchStand();
		EntityActionInstance[] action = new EntityActionInstance[1];
		List<ClientStrike> clients = new ArrayList<>();
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = scene.press(name, method);
				helper.assertTrue((action[0] instanceof StandEntityPunch || action[0] instanceof StandEntityHeavyPunch
						|| action[0] instanceof StandEntityBarrage
								&& !StandEntityBarrageAbility.isDirectionalBarrage(scene.stand, action[0]))
						&& action[0].punchedTarget == null, "Fixture: the " + name + " input started " + action[0]
								+ ", or it struck at once");
				ActionTarget saved = savedTarget(action[0]);
				ActionTarget turnedTo = action[0].standRotationTarget;
				boolean kept = saved != null && saved.getMainEntity() == pressed;
				boolean turned = turnedTo != null && turnedTo.getMainEntity() == pressed;
				helper.assertTrue(kept == keptAsIn116 && turned == keptAsIn116, how + " saved " + saved + " and turns the Stand"
						+ " to " + turnedTo + (keptAsIn116 ? "; 1.16 kept a living entity the Stand could attack as the task target"
								: "; 1.16 kept only a living entity as the task target (StandEntityAction.checkStandTarget)"));
				// what a client holds of the locked barrage as the server sent it
				clients.addAll(clientCopies(action[0], scene.stand));
				scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
				scene.sendClientAims();
				helper.assertTrue(scene.vanillaPick().getMainEntity() == cow, "Fixture: the turned user's vanilla pick is "
						+ scene.vanillaPick());
				return false;
			}
			if (tick < 3) {
				return false;
			}
			ActionTarget struck = action[0].punchedTarget;
			List<TrEntityActionTargetPacket> sent = watcher.sentFor(action[0]);
			if (struck == null) {
				helper.assertTrue(barrage || sent.isEmpty(), how + " sent tracking clients " + sent + " before it struck,"
						+ " dropping nothing");
				return false;
			}
			if (keptAsIn116) {
				helper.assertTrue(struck.getMainEntity() == pressed && cow.getHealth() >= health, how + " struck " + struck
						+ " after its user turned to the cow; 1.16 struck the living entity the press kept");
				helper.assertTrue(sent.isEmpty(), how + " struck it and sent tracking clients " + sent + "; 1.16's"
						+ " StandEntity.setTaskTarget sent nothing when the strike's target was the task target");
				return true;
			}
			helper.assertTrue(struck.getMainEntity() != pressed, how + " struck it after its user turned to the cow; 1.16 kept"
					+ " no entity that is not living and aimed again at the strike (StandEntity.punch)");
			helper.assertTrue(struck.getMainEntity() != cow || sent.size() == 1
					&& entityOf(helper, sent.get(0).keptTarget()) == cow
					&& entityOf(helper, sent.get(0).rotationTarget()) == cow && sent.get(0).aimAs() == action[0].aimAs,
					how + " that struck the cow its user turned to sent tracking clients " + sent + "; 1.16 sent them the"
							+ " target the server took, once (StandEntity.setTaskTarget, TrStandTaskTargetPacket)");
			if (barrage) {
				// a client holds what the server sent: nothing of the boat, then the cow the server sent
				TrEntityActionTargetPacket received = sent.isEmpty() ? null : received(helper, sent.get(0));
				for (ClientStrike client : clients) {
					EntityActionInstance copy = (EntityActionInstance) client;
					ActionTarget aimed = client.strikeTarget(scene.stand);
					ActionTarget saved = savedTarget(copy);
					helper.assertTrue(aimed.isEmpty(helper.getLevel()) && saved == null, "A client's copy of the locked"
							+ " barrage strikes at " + aimed + " of its own and keeps " + saved + "; the target the barrage"
							+ " keeps is the server's, as a 1.16 task target was");
					if (received != null) {
						copy.setTargetsFromServer(received.keptTarget(), received.rotationTarget(), received.aimAs(), received.taskTarget());
						ActionTarget held = client.strikeTarget(scene.stand);
						helper.assertTrue(held.getMainEntity() == cow, "A client's copy of the locked barrage that took what"
								+ " the server sent holds " + held + " instead of the cow");
					}
				}
				clients.clear();
				return struck.getMainEntity() == cow && cow.getHealth() < health && tick > 12;
			}
			helper.assertTrue(struck.getMainEntity() == cow && cow.getHealth() < health, how + " struck " + struck
					+ " after its user turned to the cow (Stand at " + scene.stand.position() + ", cow at " + cow.position()
					+ "); 1.16 aimed again at the strike and struck what its user aimed at");
			return true;
		}, 60, () -> how + " never struck the cow its user turned to");
	}

	// 1.16 StandEntityTask.keepTarget kept a dying task target until it was removed or had lain dead for 20 ticks, and
	// StandEntity.punch struck it in reach (aimWithThisOrUser), though no hit hurt it any more: the punch pressed at a
	// dying cow turns the Stand to it and strikes it, not the ground its user's crosshair meets past it
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "dying_cow_punch")
	public static void punchPressedAtADyingCowStrikesItAsIn116(GameTestHelper helper) {
		Scene scene = new Scene(helper, true);
		scene.precision(0);
		BlockPos ground = scene.groundStrip();
		Cow cow = scene.cow(0, 2.0);
		// low on the cow, so that past it the crosshair meets the ground strip
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.position().add(0, 0.4, 0));
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 2) {
				cow.setHealth(0);
				return false;
			}
			if (tick == 3) {
				helper.assertTrue(cow.isDeadOrDying() && !cow.isRemoved() && cow.deathTime < 20
						&& scene.vanillaPick().getMainEntity() == cow, "Fixture: the cow on the crosshair (" + scene.vanillaPick()
								+ ") is not dying: health " + cow.getHealth() + ", " + cow.deathTime + " ticks dead");
				scene.sendClientAims();
				action[0] = scene.press("punch", InputMethod.CLICK);
				helper.assertTrue(action[0] instanceof StandEntityPunch && action[0].punchedTarget == null,
						"Fixture: the punch input started " + action[0] + ", or it struck at once");
				ActionTarget saved = savedTarget(action[0]);
				ActionTarget turnedTo = action[0].standRotationTarget;
				helper.assertTrue(entityOf(helper, saved) == cow && entityOf(helper, turnedTo) == cow, "The punch pressed at"
						+ " a dying cow saved " + saved + " and turns the Stand to " + turnedTo + "; 1.16 kept a dying task"
						+ " target until it was removed or had lain dead for 20 ticks (StandEntityTask.keepTarget)");
				return false;
			}
			if (tick < 3) {
				return false;
			}
			ActionTarget struck = action[0].punchedTarget;
			if (struck == null) {
				return false;
			}
			helper.assertTrue(struck.getMainEntity() == cow && scene.isStone(ground), "The punch pressed at a dying cow"
					+ " struck " + struck + " and the ground behind it " + (scene.isStone(ground) ? "stands" : "broke")
					+ "; 1.16 struck the dying task target in reach (StandEntity.aimWithThisOrUser), which no hit hurt any"
					+ " more");
			return true;
		}, 60, () -> "The punch pressed at a dying cow never struck");
	}

	// the same through a heavy punch's windup: the mob it was pressed at dies on the way and stays its target, the Stand
	// placed from it (StandEntityHeavyAttack.getOffsetFromUser), until the strike; one dead for 20 ticks would be dropped
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "dying_cow_heavy")
	public static void heavyPunchKeepsTheMobItWasPressedAtThatDiesInTheWindupAsIn116(GameTestHelper helper) {
		Scene scene = new Scene(helper, true);
		scene.precision(0);
		BlockPos ground = scene.groundStrip();
		Cow cow = scene.cow(0, 2.0);
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.position().add(0, 0.4, 0));
		helper.assertTrue(scene.vanillaPick().getMainEntity() == cow, "Fixture: the vanilla pick is " + scene.vanillaPick());
		String how = "The heavy punch whose pressed cow died in its windup";
		double farthest = Math.min(2, scene.stand.getMaxRange());
		TargetWatcher watcher = scene.watchStand();
		EntityActionInstance[] action = new EntityActionInstance[1];
		Vec3[] placed = new Vec3[1];
		boolean[] keptDying = new boolean[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = scene.press("heavy_punch", InputMethod.CLICK);
				helper.assertTrue(action[0] instanceof StandEntityHeavyPunch && action[0].punchedTarget == null
						&& entityOf(helper, savedTarget(action[0])) == cow, "Fixture: the heavy punch input started "
								+ action[0] + ", struck at once or keeps " + savedTarget(action[0]));
				return false;
			}
			if (tick < 3) {
				return false;
			}
			if (tick == 4) {
				placed[0] = action[0].getSyncedStandOffset();
				helper.assertTrue(placed[0] != null && Math.abs(placed[0].z - farthest) > 0.1, "Fixture: the heavy punch"
						+ " placed the Stand at " + placed[0] + ", as along its user's look, not from the cow");
				cow.setHealth(0);
				return false;
			}
			ActionTarget struck = action[0].punchedTarget;
			if (struck == null) {
				// the strike comes with the first action tick of the PERFORM the windup's last tick entered
				helper.assertTrue(cow.isDeadOrDying() && !cow.isRemoved() && (action[0].getPhase() == ActionPhase.WINDUP
						|| action[0].getPhase() == ActionPhase.PERFORM), "Fixture: at tick " + tick + " the cow has health "
								+ cow.getHealth() + ", " + cow.deathTime + " ticks dead, removed " + cow.isRemoved()
								+ ", and the heavy punch is in its " + action[0].getPhase() + " without striking");
				ActionTarget saved = savedTarget(action[0]);
				ActionTarget turnedTo = action[0].standRotationTarget;
				List<TrEntityActionTargetPacket> sent = watcher.sentFor(action[0]);
				helper.assertTrue(entityOf(helper, saved) == cow && entityOf(helper, turnedTo) == cow && sent.isEmpty(),
						how + " keeps " + saved + ", turns the Stand to " + turnedTo + " and sent tracking clients " + sent
								+ "; 1.16 kept a dying task target until it was removed or had lain dead for 20 ticks"
								+ " (StandEntityTask.keepTarget)");
				Vec3 offset = action[0].getSyncedStandOffset();
				helper.assertTrue(offset != null && offset.distanceTo(placed[0]) < 0.01, how + " placed the Stand at "
						+ offset + " instead of from the cow, at " + placed[0] + "; 1.16 placed it from the task target");
				int deathTime = cow.deathTime;
				ActionTarget atCow = new ActionTarget(cow);
				boolean kept = StandEntityPunchAbility.keepsTargetInRange(scene.stand, atCow,
						StandEntityPunchAbility.CrosshairRule.ENTITY);
				cow.deathTime = 20;
				boolean keptAt20 = StandEntityPunchAbility.keepsTargetInRange(scene.stand, atCow,
						StandEntityPunchAbility.CrosshairRule.ENTITY);
				cow.deathTime = deathTime;
				helper.assertTrue(kept && !keptAt20, "A heavy punch's windup " + (kept ? "keeps" : "drops") + " a cow dead"
						+ " for " + deathTime + " ticks and " + (keptAt20 ? "keeps" : "drops") + " one dead for 20; 1.16 kept"
						+ " a dying task target until it had lain dead for 20 ticks (StandEntityTask.keepTarget)");
				keptDying[0] = true;
				return false;
			}
			helper.assertTrue(keptDying[0], "Fixture: the heavy punch struck before a windup tick with the cow dying");
			helper.assertTrue(struck.getMainEntity() == cow && scene.isStone(ground), how + " struck " + struck + " and the"
					+ " ground behind it " + (scene.isStone(ground) ? "stands" : "broke") + "; 1.16 struck the dying task"
					+ " target in reach (StandEntity.aimWithThisOrUser)");
			return true;
		}, 60, () -> how + " never struck");
	}

	// without the aim assist a press whose client sent no crosshair reads the crosshair line on the server, a stand-in for
	// the vanilla pick a client sends, which keeps a dying cow (StandEntityTask.keepTarget): so does the line, not the
	// ground behind it
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "dying_cow_punch_off_unsent")
	public static void punchWithoutAimAssistAndNoSentCrosshairKeepsADyingCow(GameTestHelper helper) {
		Scene scene = new Scene(helper, false);
		scene.precision(0);
		scene.groundStrip();
		Cow cow = scene.cow(0, 2.0);
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.position().add(0, 0.4, 0));
		scene.run(tick -> {
			if (tick == 2) {
				cow.setHealth(0);
				return false;
			}
			if (tick < 3) {
				return false;
			}
			var sent = LivingComponentAction.getAim(scene.user);
			helper.assertTrue(cow.isDeadOrDying() && !cow.isRemoved() && cow.deathTime < 20
					&& (sent == null || sent.getTarget().isEmpty(helper.getLevel())), "Fixture: the cow is not dying ("
							+ cow.getHealth() + ", " + cow.deathTime + " ticks dead), or a crosshair was sent");
			EntityActionInstance action = scene.press("punch", InputMethod.CLICK);
			helper.assertTrue(action instanceof StandEntityPunch && action.punchedTarget == null,
					"Fixture: the punch input started " + action + ", or it struck at once");
			ActionTarget saved = savedTarget(action);
			ActionTarget turnedTo = action.standRotationTarget;
			helper.assertTrue(entityOf(helper, saved) == cow && entityOf(helper, turnedTo) == cow, "The punch pressed without"
					+ " the aim assist and no crosshair sent at a dying cow saved " + saved + " and turns the Stand to " + turnedTo
					+ "; the crosshair a client sends keeps a dying cow, as 1.16's task kept it (StandEntityTask.keepTarget)");
			return true;
		}, 10, () -> "The punch was never pressed");
	}

	// keep attack target on: unlike a punch, the locked barrage lets go of the cow it was pressed at on the first tick after
	// it dies and strikes the next cow on the crosshair: 1.16's barrage kept no entity target
	// (StandEntityMeleeBarrage.checkStandTarget) and each hit's precision ray skipped a dead mob (StandEntity.canTarget);
	// the owner reported a held barrage that stops dealing damage once it kills its target as a bug (2026-06-25)
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "dying_cow_locked_barrage")
	public static void lockedBarrageLetsGoOfTheCowItWasPressedAtOnceItDiesAndStrikesTheNextOne(GameTestHelper helper) {
		Scene scene = new Scene(helper, true);
		scene.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
		scene.precision(0);
		Cow cow = scene.cow(0, 2.0);
		Cow next = scene.cow(0, 2.4);
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.position().add(0, 0.4, 0));
		EntityActionInstance[] action = new EntityActionInstance[1];
		int[] diedAt = { -1 };
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = scene.press("barrage", InputMethod.HOLD);
				helper.assertTrue(action[0] instanceof StandEntityBarrage && entityOf(helper, savedTarget(action[0])) == cow,
						"Fixture: the barrage input started " + action[0] + ", keeping " + (action[0] != null ? savedTarget(action[0])
								: null));
				return false;
			}
			if (tick < 3) {
				return false;
			}
			helper.assertTrue(!action[0].isOver(), "Fixture: the locked barrage ended at tick " + tick);
			if (diedAt[0] < 0) {
				if (action[0].punchedTarget != null && action[0].punchedTarget.getMainEntity() == cow) {
					helper.assertTrue(next.getHealth() == 500, "Fixture: the cow behind was struck before the first one died");
					cow.setHealth(0);
					diedAt[0] = tick;
				}
				return false;
			}
			helper.assertTrue(cow.isDeadOrDying() && !cow.isRemoved() && cow.deathTime < 20, "Fixture: the cow is not dying: "
					+ cow.getHealth() + ", " + cow.deathTime + " ticks dead");
			ActionTarget saved = savedTarget(action[0]);
			ActionTarget turnedTo = action[0].standRotationTarget;
			helper.assertTrue(entityOf(helper, saved) != cow && entityOf(helper, turnedTo) != cow,
					"The locked barrage " + (tick - diedAt[0]) + " ticks after its cow died keeps " + saved + " and turns the"
							+ " Stand to " + turnedTo + "; it drops a dying target at once and aims again, as 1.16's barrage did");
			if (next.getHealth() < 500) {
				return true;
			}
			helper.assertTrue(tick < diedAt[0] + 10, "The locked barrage struck nothing of the cow behind on the crosshair in "
					+ (tick - diedAt[0]) + " ticks after the first one died (keeping " + saved + ", turned to " + turnedTo + ")");
			return false;
		}, 60, () -> "The locked barrage never struck the cow");
	}

	// 1.16 StandEntity.punch made the strike's target the task target (setTaskTarget, StandEntityTask.setTarget and
	// keepTarget) and sent it to tracking clients only when it changed. The cow a punch was pressed at steps out of the
	// Stand's reach but stays within the action's range, so it is kept through the windup and the strike aims again, at
	// the block its user aims at: the light attack took the block as its task target (StandEntityLightAttack
	// .standKeepsTarget) and the Stand turned to it; the heavy attack refused it (StandEntityHeavyAttack.standKeepsTarget),
	// so its task target stayed the cow and nothing was sent. Bedrock, so that the struck block stays to be turned to.
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "strike_block_punch")
	public static void punchWhoseKeptMobLeftReachTurnsToTheBlockItStrikesAsIn116(GameTestHelper helper) {
		keptMobOutOfReachAtTheStrike(helper, "punch", scene -> scene.press("punch", InputMethod.CLICK), false,
				StrikeMeets.BLOCK, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "strike_block_heavy")
	public static void heavyPunchWhoseKeptMobLeftReachStaysOnItThoughItStrikesABlockAsIn116(GameTestHelper helper) {
		keptMobOutOfReachAtTheStrike(helper, "heavy punch", scene -> scene.press("heavy_punch", InputMethod.CLICK), true,
				StrikeMeets.BLOCK, false);
	}

	// nothing in reach at the strike: StandEntity.punch set the empty task target, which every attack kept and which was
	// sent, and the Stand turned along its user's look again (StandEntityTask.rotateStand, StandEntity.defaultRotation)
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "strike_nothing_punch")
	public static void punchThatStrikesNothingDropsItsTargetAndTurnsAlongTheUsersLookAsIn116(GameTestHelper helper) {
		keptMobOutOfReachAtTheStrike(helper, "punch", scene -> scene.press("punch", InputMethod.CLICK), false,
				StrikeMeets.NOTHING, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "strike_nothing_heavy")
	public static void heavyPunchThatStrikesNothingDropsItsTargetAndTurnsAlongTheUsersLookAsIn116(GameTestHelper helper) {
		keptMobOutOfReachAtTheStrike(helper, "heavy punch", scene -> scene.press("heavy_punch", InputMethod.CLICK), true,
				StrikeMeets.NOTHING, false);
	}

	// a struck entity became the task target only if the task kept it (StandEntityTask.setTarget, keepTarget,
	// StandEntityAction.checkStandTarget): a boat is not living, so the light and the heavy attack alike left their task
	// target the cow, the Stand turned to it, and sent nothing
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "strike_boat_punch")
	public static void punchWhoseKeptMobLeftReachStaysOnItThoughItStrikesABoatAsIn116(GameTestHelper helper) {
		keptMobOutOfReachAtTheStrike(helper, "punch", scene -> scene.press("punch", InputMethod.CLICK), false,
				StrikeMeets.BOAT, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "strike_boat_heavy")
	public static void heavyPunchWhoseKeptMobLeftReachStaysOnItThoughItStrikesABoatAsIn116(GameTestHelper helper) {
		keptMobOutOfReachAtTheStrike(helper, "heavy punch", scene -> scene.press("heavy_punch", InputMethod.CLICK), true,
				StrikeMeets.BOAT, false);
	}

	// 1.16's StandEntityTask.tick ran keepTarget on every server tick of the task, not only through its windup: the block a
	// light punch struck and broke is air the next tick (Action.checkRangeAndTarget), so the task target was dropped and
	// the empty one sent (setTaskTarget), and the Stand turned along its user's look
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "strike_breaks_block_punch")
	public static void punchDropsTheBlockItStruckAndBrokeTheNextTickAsIn116(GameTestHelper helper) {
		keptMobOutOfReachAtTheStrike(helper, "punch", scene -> scene.press("punch", InputMethod.CLICK), false,
				StrikeMeets.BREAKABLE_BLOCK, false);
	}

	// the same rule for a punch whose action strikes in an actionPerformStart of its own, as King Crimson's strikes
	// (AbstractKingCrimsonStrikeAbility.KingCrimsonStrikeAction) do: Chop, Impale and Ground Slam as 1.16 light attacks,
	// Ground Punch and Desperate Eyejab as heavy ones (heavyAttack)
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "strike_block_own_strike")
	public static void punchStrikingInItsOwnPerformTurnsToTheBlockItStrikesAsIn116(GameTestHelper helper) {
		keptMobOutOfReachAtTheStrike(helper, "punch striking in its own perform", scene -> scene.startOwnStrike(false),
				false, StrikeMeets.BLOCK, true);
	}

	// the action's own line hands the aim back after a struck block, which the server sends; the task target stays the cow
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "strike_block_own_heavy_strike")
	public static void heavyAttackPunchStrikingInItsOwnPerformStaysOnItsKeptMobThoughItStrikesABlockAsIn116(
			GameTestHelper helper) {
		keptMobOutOfReachAtTheStrike(helper, "heavy-attack punch striking in its own perform",
				scene -> scene.startOwnStrike(true), true, StrikeMeets.BLOCK, true);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "strike_nothing_own_heavy_strike")
	public static void heavyAttackPunchStrikingInItsOwnPerformThatStrikesNothingDropsItsTargetAsIn116(
			GameTestHelper helper) {
		keptMobOutOfReachAtTheStrike(helper, "heavy-attack punch striking in its own perform",
				scene -> scene.startOwnStrike(true), true, StrikeMeets.NOTHING, true);
	}

	// the action's own line turns the Stand to an entity it struck, and King Crimson's strikes hit a boat too; the task
	// refused the boat, so the Stand stays on the cow and nothing is sent
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "strike_boat_own_strike")
	public static void punchStrikingInItsOwnPerformStaysOnItsKeptMobThoughItStrikesABoatAsIn116(GameTestHelper helper) {
		keptMobOutOfReachAtTheStrike(helper, "punch striking in its own perform", scene -> scene.startOwnStrike(false),
				false, StrikeMeets.BOAT, true);
	}

	// an action whose getPunchTarget is its own and does not call the core's (Red Hot Chili Pepper's lock and counter
	// targets, Magician's Red's kick at its red bind, Reworked's blinks) strikes through the core's actionPerformStart,
	// which then sets the targets by the same task rule, judged before the hit as 1.16 set the task target before it
	// attacked (StandEntity.punch)
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "own_target_block_punch")
	public static void punchWithATargetOfItsOwnTurnsToTheBlockItStrikesAsIn116(GameTestHelper helper) {
		keptMobOutOfReachAtTheStrike(helper, "punch with a target of its own", scene -> scene.startOwnTarget(false),
				false, StrikeMeets.BLOCK, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "own_target_breaks_block_punch")
	public static void punchWithATargetOfItsOwnTakesTheBlockItBreaksThenDropsItAsIn116(GameTestHelper helper) {
		keptMobOutOfReachAtTheStrike(helper, "punch with a target of its own", scene -> scene.startOwnTarget(false),
				false, StrikeMeets.BREAKABLE_BLOCK, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "own_target_nothing_punch")
	public static void punchWithATargetOfItsOwnThatStrikesNothingDropsItsTargetAsIn116(GameTestHelper helper) {
		keptMobOutOfReachAtTheStrike(helper, "punch with a target of its own", scene -> scene.startOwnTarget(false),
				false, StrikeMeets.NOTHING, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "own_target_nothing_heavy")
	public static void heavyPunchWithATargetOfItsOwnThatStrikesNothingDropsItsTargetAsIn116(GameTestHelper helper) {
		keptMobOutOfReachAtTheStrike(helper, "heavy punch with a target of its own", scene -> scene.startOwnTarget(true),
				true, StrikeMeets.NOTHING, false);
	}

	// the keepTarget of every server tick (StandEntityTask.tick) went on after the strike: the cow a punch kept and struck
	// steps beyond the action's range (8 blocks) in the punch's PERFORM, or the heavy punch's RECOVERY, which follows its
	// strike, and is dropped the next tick, the empty task target sent once
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "kept_mob_punch_leaves_range_after_strike")
	public static void punchDropsTheMobItStruckThatLeftTheActionsRangeInItsPerformAsIn116(GameTestHelper helper) {
		struckMobLeavesTheRange(helper, "punch", ActionPhase.PERFORM);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "kept_mob_heavy_leaves_range_after_strike")
	public static void heavyPunchDropsTheMobItStruckThatLeftTheActionsRangeInItsRecoveryAsIn116(GameTestHelper helper) {
		struckMobLeavesTheRange(helper, "heavy_punch", ActionPhase.RECOVERY);
	}

	private static void struckMobLeavesTheRange(GameTestHelper helper, String name, ActionPhase leavesIn) {
		Scene scene = new Scene(helper, true);
		scene.precision(0);
		scene.groundStrip(Blocks.BEDROCK.defaultBlockState());
		Cow cow = scene.cow(0, 2.0);
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.position().add(0, 0.4, 0));
		helper.assertTrue(scene.vanillaPick().getMainEntity() == cow, "Fixture: the vanilla pick is " + scene.vanillaPick());
		String how = "The " + name + " whose struck cow left the action's range in its " + leavesIn;
		TargetWatcher watcher = scene.watchStand();
		EntityActionInstance[] action = new EntityActionInstance[1];
		List<ClientStrike> clients = new ArrayList<>();
		boolean[] left = new boolean[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = scene.press(name, InputMethod.CLICK);
				helper.assertTrue((action[0] instanceof StandEntityPunch || action[0] instanceof StandEntityHeavyPunch)
						&& action[0].punchedTarget == null && entityOf(helper, savedTarget(action[0])) == cow,
						"Fixture: the " + name + " input started " + action[0] + ", struck at once, or keeps "
								+ savedTarget(action[0]));
				return false;
			}
			if (tick < 3) {
				return false;
			}
			ActionTarget struck = action[0].punchedTarget;
			List<TrEntityActionTargetPacket> sent = watcher.sentFor(action[0]);
			if (struck == null) {
				return false;
			}
			if (!left[0]) {
				helper.assertTrue(struck.getMainEntity() == cow && entityOf(helper, savedTarget(action[0])) == cow
						&& entityOf(helper, action[0].standRotationTarget) == cow && sent.isEmpty()
						&& action[0].getPhase() == leavesIn, "Fixture: the " + name + " struck " + struck + ", keeps "
								+ savedTarget(action[0]) + ", turns the Stand to " + action[0].standRotationTarget + ", sent "
								+ sent + " and is in its " + action[0].getPhase() + " instead of its " + leavesIn);
				clients.addAll(clientCopies(action[0], scene.stand));
				cow.moveTo(cow.getX(), cow.getY(), cow.getZ() + 12.0, 0, 0);
				helper.assertTrue(!ActionTargetRange.isTargetWithinRange(scene.stand, new ActionTarget(cow), helper.getLevel()),
						"Fixture: the cow 12 blocks further is within the action's range");
				left[0] = true;
				return false;
			}
			helper.assertTrue(action[0].getPhase() == leavesIn, "Fixture: the " + name + " left its " + leavesIn + " before the"
					+ " next tick: " + action[0].getPhase());
			ActionTarget saved = savedTarget(action[0]);
			ActionTarget turnedTo = action[0].standRotationTarget;
			helper.assertTrue(saved == null && turnedTo == null && action[0].aimAs == AimingEntity.CAMERA_ENTITY
					&& sent.size() == 1 && sent.get(0).keptTarget() == null && sent.get(0).rotationTarget() == null
					&& sent.get(0).aimAs() == AimingEntity.CAMERA_ENTITY, how + " keeps " + saved + ", turns the Stand to "
							+ turnedTo + ", aims as " + action[0].aimAs + " and sent tracking clients " + sent + "; 1.16"
							+ " checked the task target on every tick of the task (StandEntityTask.tick, keepTarget), dropped it"
							+ " out of the action's range (Action.checkRangeAndTarget) and sent the empty one once");
			TrEntityActionTargetPacket drop = received(helper, sent.get(0));
			for (ClientStrike client : clients) {
				EntityActionInstance copy = (EntityActionInstance) client;
				copy.setTargetsFromServer(drop.keptTarget(), drop.rotationTarget(), drop.aimAs(), drop.taskTarget());
				helper.assertTrue(copy.standRotationTarget == null && savedTarget(copy) == null && client.strikeTarget(
						scene.stand).isEmpty(helper.getLevel()), "A client's " + copy.getClass().getSimpleName() + " that took"
								+ " what the server sent turns the Stand to " + copy.standRotationTarget + " and keeps "
								+ savedTarget(copy) + " instead of nothing");
			}
			return true;
		}, 60, () -> "The " + name + " never struck the cow, or the test ended at its strike");
	}

	// what the strike of keptMobOutOfReachAtTheStrike meets: bedrock that stays, glass the strike breaks, nothing (the user
	// looks up), or a boat in front of the cow
	private enum StrikeMeets {
		BLOCK, BREAKABLE_BLOCK, NOTHING, BOAT
	}

	private static void keptMobOutOfReachAtTheStrike(GameTestHelper helper, String name,
			Function<Scene, EntityActionInstance> start, boolean heavy, StrikeMeets meets, boolean ownStrike) {
		Scene scene = new Scene(helper, true);
		scene.precision(0);
		boolean atBlock = meets == StrikeMeets.BLOCK || meets == StrikeMeets.BREAKABLE_BLOCK;
		scene.groundStrip((meets == StrikeMeets.BREAKABLE_BLOCK ? Blocks.GLASS : Blocks.BEDROCK).defaultBlockState());
		Cow cow = scene.cow(0, 2.0);
		// low on the cow, so that past it the crosshair meets the ground strip
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.position().add(0, 0.4, 0));
		helper.assertTrue(scene.vanillaPick().getMainEntity() == cow, "Fixture: the vanilla pick is " + scene.vanillaPick());
		String how = "The " + name + " whose kept cow left the Stand's reach and that " + switch (meets) {
			case BLOCK -> "struck the block its user aims at";
			case BREAKABLE_BLOCK -> "struck and broke the glass its user aims at";
			case NOTHING -> "struck nothing";
			case BOAT -> "struck the boat its user aims at";
		};
		Level level = helper.getLevel();
		TargetWatcher watcher = scene.watchStand();
		EntityActionInstance[] action = new EntityActionInstance[1];
		List<ClientStrike> clients = new ArrayList<>();
		ActionTarget[] struckAt = new ActionTarget[1];
		Entity[] boat = new Entity[1];
		int[] ticksAfterStrike = new int[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = start.apply(scene);
				helper.assertTrue((action[0] instanceof StandEntityPunch || action[0] instanceof StandEntityHeavyPunch)
						&& action[0].punchedTarget == null && entityOf(helper, savedTarget(action[0])) == cow
						&& entityOf(helper, action[0].standRotationTarget) == cow, "Fixture: the " + name + " input started "
								+ action[0] + ", struck at once, or keeps " + savedTarget(action[0]) + " and turns the Stand to "
								+ action[0].standRotationTarget + " instead of the cow");
				// out of the Stand's reach, within the action's range (8 blocks)
				cow.moveTo(cow.getX(), cow.getY(), cow.getZ() + 4.0, 0, 0);
				if (meets == StrikeMeets.NOTHING) {
					scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, scene.user.getEyePosition().add(0, 10, 0.01));
				}
				else if (meets == StrikeMeets.BOAT) {
					boat[0] = scene.entity(EntityType.BOAT, 0, 2.0);
					scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, boat[0].getBoundingBox().getCenter());
				}
				scene.sendClientAims();
				boolean meetsIt = switch (meets) {
					case BLOCK, BREAKABLE_BLOCK -> scene.vanillaPick().getType() == ActionTarget.TargetType.BLOCK;
					case NOTHING -> scene.vanillaPick().isEmpty(level);
					case BOAT -> scene.vanillaPick().getMainEntity() == boat[0]
							&& StandEntityPunchAbility.canStandHit(scene.stand, boat[0]);
				};
				helper.assertTrue(meetsIt, "Fixture: the user's crosshair meets " + scene.vanillaPick());
				return false;
			}
			if (tick < 3) {
				return false;
			}
			ActionTarget struck = action[0].punchedTarget;
			List<TrEntityActionTargetPacket> sent = watcher.sentFor(action[0]);
			if (struck == null) {
				helper.assertTrue(sent.isEmpty(), how + " sent tracking clients " + sent + " before it struck, keeping the cow");
				clients.clear();
				clients.addAll(clientCopies(action[0], scene.stand));
				return false;
			}
			if (struckAt[0] != null && meets == StrikeMeets.BREAKABLE_BLOCK && ++ticksAfterStrike[0] == 1) {
				ActionTarget saved = savedTarget(action[0]);
				ActionTarget turnedTo = action[0].standRotationTarget;
				helper.assertTrue(saved == null && turnedTo == null && action[0].aimAs == AimingEntity.CAMERA_ENTITY
						&& sent.size() == 2 && sent.get(1).keptTarget() == null && sent.get(1).rotationTarget() == null
						&& sent.get(1).aimAs() == AimingEntity.CAMERA_ENTITY, how + " keeps " + saved + ", turns the Stand to "
								+ turnedTo + " and aims as " + action[0].aimAs + " the tick after, in its " + action[0].getPhase()
								+ ", and sent tracking clients " + sent + "; 1.16 checked the task target on every tick"
								+ " (StandEntityTask.tick, keepTarget), dropped the broken block (Action.checkRangeAndTarget:"
								+ " air) and sent the empty task target once (setTaskTarget)");
				TrEntityActionTargetPacket drop = received(helper, sent.get(1));
				for (ClientStrike client : clients) {
					EntityActionInstance copy = (EntityActionInstance) client;
					copy.setTargetsFromServer(drop.keptTarget(), drop.rotationTarget(), drop.aimAs(), drop.taskTarget());
					setPhase(copy, ActionPhase.PERFORM);
					ActionTarget shown = StandEntityPunchAbility.keptStrikeTarget(scene.stand, copy);
					helper.assertTrue(shown == null && copy.standRotationTarget == null && savedTarget(copy) == null,
							"A client's " + copy.getClass().getSimpleName() + " that took what the server sent shows " + shown
									+ " and turns the Stand to " + copy.standRotationTarget + " instead of nothing");
				}
				return false;
			}
			if (struckAt[0] != null) {
				// a tick on, what the Stand faces
				if (meets == StrikeMeets.BLOCK || meets == StrikeMeets.BOAT) {
					boolean facesCow = heavy || meets == StrikeMeets.BOAT;
					Vec3 faced = facesCow ? scene.stand.getPosToLookAt(new ActionTarget(cow))
							: Vec3.atCenterOf(struckAt[0].getBlockPos());
					double off = degreesOff(scene.stand, faced);
					helper.assertTrue(off < 3, how + " faces " + off + " degrees off the " + (facesCow ? "cow" : "block it struck")
							+ "; 1.16 turned the Stand to its task target (StandEntityTask.rotateStand)");
				}
				else {
					float yaw = Mth.wrapDegrees(scene.stand.getYRot() - scene.user.getYRot());
					float pitch = scene.stand.getXRot() - scene.user.getXRot();
					helper.assertTrue(Math.abs(yaw) < 0.5F && Math.abs(pitch) < 0.5F, how + " turns the Stand " + yaw
							+ " degrees aside and " + pitch + " degrees up or down from its user's look; with no task target"
							+ " 1.16 turned it along its user's look (StandEntity.defaultRotation)");
				}
				return true;
			}
			struckAt[0] = struck;
			ActionTarget atCow = new ActionTarget(cow);
			helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(scene.stand, atCow).isEmpty(level)
					&& ActionTargetRange.isTargetWithinRange(scene.stand, atCow, level), "Fixture: at the strike the cow is"
							+ " within the Stand's reach or out of the action's range (Stand at " + scene.stand.position()
							+ ", cow at " + cow.position() + ")");
			boolean struckIt = switch (meets) {
				case BLOCK -> struck.getType() == ActionTarget.TargetType.BLOCK;
				case BREAKABLE_BLOCK -> struck.getType() == ActionTarget.TargetType.BLOCK
						&& level.getBlockState(struck.getBlockPos()).isAir();
				case NOTHING -> struck.getType() == ActionTarget.TargetType.EMPTY;
				case BOAT -> struck.getMainEntity() == boat[0];
			};
			helper.assertTrue(struckIt, "Fixture: " + how + " struck " + struck + (struck.getType() == ActionTarget.TargetType.BLOCK
					? ", where " + level.getBlockState(struck.getBlockPos()) + " is" : ""));
			ActionTarget saved = savedTarget(action[0]);
			ActionTarget turnedTo = action[0].standRotationTarget;
			String state = how + " keeps " + saved + ", turns the Stand to " + turnedTo + ", aims as " + action[0].aimAs
					+ " in its " + action[0].getPhase() + " and sent tracking clients " + sent;
			if (atBlock && heavy || meets == StrikeMeets.BOAT) {
				// an action's own line that hands the aim back after a block is sent with the targets, which must still be
				// the cow
				boolean sentOnlyTheCow = sent.stream().allMatch(packet -> entityOf(helper, packet.keptTarget()) == cow
						&& entityOf(helper, packet.rotationTarget()) == cow);
				helper.assertTrue(entityOf(helper, saved) == cow && entityOf(helper, turnedTo) == cow
						&& (ownStrike && meets != StrikeMeets.BOAT ? sentOnlyTheCow : sent.isEmpty()), state
								+ (meets == StrikeMeets.BOAT ? "; 1.16 refused the struck boat as the task target, an entity that"
										+ " is not living (StandEntityTask.setTarget, keepTarget, StandEntityAction.checkStandTarget),"
										+ " which stayed the cow, and sent nothing"
										: "; 1.16's heavy attack refused the struck block as its task target"
												+ " (StandEntityHeavyAttack.standKeepsTarget), which stayed the cow, and sent nothing"));
				for (ClientStrike client : clients) {
					EntityActionInstance copy = (EntityActionInstance) client;
					ActionTarget held = client.strikeTarget(scene.stand);
					helper.assertTrue(held.getMainEntity() == cow && entityOf(helper, copy.standRotationTarget) == cow,
							"A client's " + copy.getClass().getSimpleName() + " holds " + held + " and turns the Stand to "
									+ copy.standRotationTarget + " instead of the cow the server kept");
				}
				return false;
			}
			if (atBlock) {
				helper.assertTrue(saved != null && saved.sameTarget(struck) && turnedTo != null && turnedTo.sameTarget(struck)
						&& action[0].aimAs == AimingEntity.CAMERA_ENTITY && sent.size() == 1
						&& struck.sameTarget(sent.get(0).keptTarget()) && struck.sameTarget(sent.get(0).rotationTarget())
						&& sent.get(0).aimAs() == AimingEntity.CAMERA_ENTITY, state + "; 1.16's light attack took the struck"
								+ " block as its task target (StandEntityLightAttack.standKeepsTarget), turned the Stand to it and"
								+ " sent it once");
			}
			else {
				helper.assertTrue(saved == null && turnedTo == null && action[0].aimAs == AimingEntity.CAMERA_ENTITY
						&& sent.size() == 1 && sent.get(0).keptTarget() == null && sent.get(0).rotationTarget() == null
						&& sent.get(0).aimAs() == AimingEntity.CAMERA_ENTITY, state + "; 1.16 set the empty task target,"
								+ " which every attack kept, and sent it once (StandEntity.punch, setTaskTarget)");
			}
			TrEntityActionTargetPacket received = received(helper, sent.get(0));
			helper.assertTrue(!clients.isEmpty(), "Fixture: no client copy of the " + name + " was taken before it struck");
			for (ClientStrike client : clients) {
				EntityActionInstance copy = (EntityActionInstance) client;
				copy.setTargetsFromServer(received.keptTarget(), received.rotationTarget(), received.aimAs(), received.taskTarget());
				setPhase(copy, ActionPhase.PERFORM);
				ActionTarget shown = StandEntityPunchAbility.keptStrikeTarget(scene.stand, copy);
				// the marker shows no block that broke
				boolean showsStruck = meets == StrikeMeets.BREAKABLE_BLOCK ? shown == null : shown != null && shown.sameTarget(struck);
				helper.assertTrue(atBlock ? showsStruck && copy.standRotationTarget != null
						&& copy.standRotationTarget.sameTarget(struck) : shown == null && copy.standRotationTarget == null
								&& savedTarget(copy) == null, "A client's " + copy.getClass().getSimpleName() + " that took what"
										+ " the server sent shows " + shown + " and turns the Stand to " + copy.standRotationTarget
										+ " instead of " + (atBlock ? "the block the server struck" : "nothing"));
			}
			return false;
		}, 60, () -> "The " + name + " never struck, or the test ended at its strike");
	}

	// how far the Stand's look is from the point
	private static double degreesOff(StandEntity stand, Vec3 point) {
		Vec3 toPoint = point.subtract(stand.getEyePosition()).normalize();
		return Math.toDegrees(Math.acos(Mth.clamp(stand.getLookAngle().dot(toPoint), -1.0, 1.0)));
	}

	// a punch that stands for a 1.16 heavy attack, as an add-on's (King Crimson's Ground Punch) declares it
	private static final class HeavyAttackPunchProbe extends StandEntityPunchAbility {
		HeavyAttackPunchProbe(AbilityType<?> abilityType, AbilityId abilityId) {
			super(abilityType, abilityId);
		}

		@Override
		protected boolean heavyAttack() {
			return true;
		}

		// 1.16 StandEntityHeavyAttack.getStandWindupTicks, as King Crimson's strikes set it
		@Override
		public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity standUser,
				LivingEntity standEntity) {
			super.initActionFromConfig(action, level, standUser, standEntity);
			if (!level.isClientSide() && standEntity instanceof StandEntity stand) {
				action.phasesLength.put(ActionPhase.WINDUP,
						StandStatFormulas.getHeavyAttackWindup(stand.getAttackSpeed(), stand.getFinisherMeter()));
			}
		}
	}

	// a punch whose action strikes in an actionPerformStart of its own as King Crimson's do: it takes getPunchTarget's target,
	// then turns the Stand to an entity it struck and otherwise hands the aim back
	private static final class OwnStrikeProbe extends StandEntityPunchAbility {
		private final boolean heavyAttack;

		OwnStrikeProbe(AbilityType<?> abilityType, AbilityId abilityId, boolean heavyAttack) {
			super(abilityType, abilityId, OwnStrike::new);
			this.heavyAttack = heavyAttack;
		}

		@Override
		protected boolean heavyAttack() {
			return heavyAttack;
		}
	}

	private static final class OwnStrike extends StandEntityPunch {
		OwnStrike(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void actionPerformStart() {
			if (!(performer instanceof StandEntity stand)) {
				return;
			}
			ActionTarget target = getPunchTarget(stand);
			if (!stand.level().isClientSide()) {
				punchedTarget = target;
			}
			if (target.getType() == ActionTarget.TargetType.ENTITY) {
				standRotationTarget = target;
			}
			else {
				aimAs = AimingEntity.CAMERA_ENTITY;
			}
		}
	}

	// punches whose getPunchTarget is their own and never calls the core's, as an add-on's lock target: a fresh aim that
	// keeps nothing; they strike through the core's actionPerformStart
	private static final class OwnTargetProbe extends StandEntityPunchAbility {
		OwnTargetProbe(AbilityType<?> abilityType, AbilityId abilityId) {
			super(abilityType, abilityId, OwnTarget::new);
		}
	}

	private static final class OwnTarget extends StandEntityPunch {
		OwnTarget(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected ActionTarget getPunchTarget(StandEntity stand) {
			return StandEntityPunchAbility.getFreshPunchTarget(stand, ActionTarget.EMPTY, false,
					StandEntityPunchAbility.CrosshairRule.BLOCK_OR_ENTITY);
		}
	}

	private static final class OwnTargetHeavyProbe extends StandEntityHeavyPunchAbility {
		OwnTargetHeavyProbe(AbilityType<?> abilityType, AbilityId abilityId) {
			super(abilityType, abilityId);
		}

		@Override
		public EntityActionInstance createActionObj() {
			return new OwnTargetHeavy(this);
		}
	}

	private static final class OwnTargetHeavy extends StandEntityHeavyPunch {
		OwnTargetHeavy(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected ActionTarget getPunchTarget(StandEntity stand) {
			return StandEntityPunchAbility.getFreshPunchTarget(stand, ActionTarget.EMPTY, false,
					StandEntityPunchAbility.CrosshairRule.ENTITY);
		}
	}

	// the grab (a port move) takes only an entity the Stand can attack (StandEntityGrab picks with canStandHit); one that
	// becomes the user's own while held is spared by the grab's punches, as 1.16's StandEntity.attackEntity refused any
	// main target the Stand could not harm
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "grab_punch")
	public static void grabPunchSparesAHeldWolfThatBecameTheUsersOwn(GameTestHelper helper) {
		heldWolfBecameOwn(helper, "punch");
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "grab_heavy")
	public static void grabHeavyPunchSparesAHeldWolfThatBecameTheUsersOwn(GameTestHelper helper) {
		heldWolfBecameOwn(helper, "heavy_punch");
	}

	private static void heldWolfBecameOwn(GameTestHelper helper, String name) {
		Scene scene = new Scene(helper, true);
		LivingEntity[] owner = new LivingEntity[1];
		Wolf wolf = scene.wolf(scene.origin.add(0, 1.0, 1.5), () -> owner[0]);
		helper.assertTrue(StandEntityPunchAbility.canStandHit(scene.stand, wolf), "Fixture: the Stand cannot attack the wild wolf");
		float health = wolf.getHealth();
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(wolf);
				owner[0] = scene.user;
				wolf.setTame(true, false);
				wolf.setOwnerUUID(scene.user.getUUID());
				helper.assertTrue(!StandEntityPunchAbility.canStandHit(scene.stand, wolf),
						"Fixture: the Stand can still attack the wolf tamed by its user");
				action[0] = scene.press(name, InputMethod.CLICK);
				helper.assertTrue(action[0] != null && action[0].ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB,
						"Fixture: the " + name + " input started " + action[0] + ", not its grab variation");
				return false;
			}
			if (tick < 3) {
				return false;
			}
			helper.assertTrue(wolf.getHealth() >= health, "The grab " + name + " hurt the held wolf that became the user's"
					+ " own; 1.16's StandEntity.attackEntity spared a main target the Stand could not harm");
			ActionTarget struck = action[0].punchedTarget;
			return struck != null && struck.getMainEntity() == wolf && tick > 20;
		}, 60, () -> "The grab " + name + " never swung at the held wolf");
	}

	// 1.16 StandEntityMeleeBarrage.checkStandTarget refused every pressed target, so under manual control each hit aimed
	// again from the Stand's own eyes (StandEntity.punch, aimWithThisOrUser skipping the user, precisionRayTrace(this,
	// getAimDistance)): pressed at one cow, the barrage hits the other once the Stand turns to it. Without the aim assist
	// the Stand's own crosshair ray decides the same way. With "keep attack target" (a port setting) it stays on the first.
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "manual_barrage")
	public static void manuallyControlledBarrageAimsEveryHitFromTheStandsEyesAsIn116(GameTestHelper helper) {
		manualBarrage(helper, true, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "manual_barrage_off")
	public static void manuallyControlledBarrageWithoutAimAssistAimsEveryHitWithTheStandsCrosshair(GameTestHelper helper) {
		manualBarrage(helper, false, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "manual_barrage_lock")
	public static void manuallyControlledBarrageWithKeepAttackTargetStaysOnItsPressedTarget(GameTestHelper helper) {
		manualBarrage(helper, true, true);
	}

	private static void manualBarrage(GameTestHelper helper, boolean aimAssist, boolean keepTarget) {
		Scene scene = new Scene(helper, aimAssist);
		scene.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = keepTarget;
		// small precision boxes, so that only the cow the Stand faces is on its rays, with the assist on too
		scene.precision(0);
		Vec3 standPos = scene.origin.add(0, 0, 1);
		Cow first = scene.cow(-1.2, 2.2);
		Cow second = scene.cow(1.2, 2.2);
		scene.stand.setManuallyControlled(true);
		helper.assertTrue(scene.stand.isManuallyControlled() && scene.stand.aimsWithoutUser(),
				"Fixture: the Stand is not manually controlled");
		String how = (keepTarget ? "locked " : "") + "barrage of a manually controlled Stand"
				+ (aimAssist ? "" : " without the aim assist");
		float firstHealth = first.getHealth();
		float secondHealth = second.getHealth();
		float[] firstAfterTurn = new float[1];
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			Cow facing = tick < 14 ? first : second;
			scene.standAt(standPos, facing);
			if (tick == 3) {
				for (Cow cow : List.of(first, second)) {
					scene.standAt(standPos, cow);
					ActionTarget aim = StandEntityPunchAbility.aimWithThisOrUser(scene.stand, scene.user, 1,
							entity -> entity != scene.stand && StandEntityPunchAbility.canStandHit(scene.stand, entity),
							StandEntityPunchAbility.CrosshairRule.NONE, true);
					helper.assertTrue(aim.getMainEntity() == cow, "Fixture: the Stand's own aim, facing " + cow
							+ ", took " + aim);
				}
				scene.standAt(standPos, first);
				// the Stand aim a client syncs under manual control
				LivingComponentAction.getComponent(scene.stand).entityAim.setTargetFromClient(new ActionTarget(first),
						helper.getLevel());
				action[0] = scene.press("barrage", InputMethod.HOLD);
				helper.assertTrue(action[0] instanceof StandEntityBarrageAbility.StandEntityBarrage,
						"Fixture: the barrage input started " + action[0]);
				return false;
			}
			if (tick < 3) {
				return false;
			}
			if (tick == 13) {
				helper.assertTrue(first.getHealth() < firstHealth && second.getHealth() >= secondHealth,
						"The " + how + " pressed at the cow it faces did not hit it alone: " + first.getHealth() + " and "
								+ second.getHealth() + " of " + firstHealth);
			}
			if (tick == 14) {
				firstAfterTurn[0] = first.getHealth();
			}
			if (tick < 30) {
				return false;
			}
			if (keepTarget) {
				helper.assertTrue(second.getHealth() >= secondHealth && first.getHealth() < firstAfterTurn[0],
						"The " + how + " left the cow it was pressed at when the Stand turned to the other: the first at "
								+ first.getHealth() + " (" + firstAfterTurn[0] + " at the turn), the other at " + second.getHealth());
			}
			else {
				helper.assertTrue(first.getHealth() >= firstAfterTurn[0] && second.getHealth() < secondHealth,
						"The " + how + " kept hitting the cow it was pressed at after the Stand turned to the other: the first"
								+ " at " + first.getHealth() + " (" + firstAfterTurn[0] + " at the turn), the other at "
								+ second.getHealth() + "; 1.16 aimed every hit again from the Stand's eyes");
			}
			return true;
		}, 40, () -> "The " + how + " test timed out");
	}

	// 1.16 refused every pressed barrage target whoever the user (StandEntityMeleeBarrage.checkStandTarget), so a mob user's
	// barrage aimed each hit again from the mob's eyes (StandEntity.punch, aimWithThisOrUser with the mob as the user):
	// pressed at one cow, it hits the other once the mob looks at it. A mob has no "keep attack target" setting.
	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH + "mob_barrage")
	public static void mobUsersBarrageAimsEveryHitFromTheMobsEyesAsIn116(GameTestHelper helper) {
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(type != null && zombie != null, "Fixture: could not create the zombie or find Star Platinum");
		List<Entity> entities = new ArrayList<>(List.of(zombie));
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400));
		zombie.moveTo(origin.x, origin.y, origin.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Fixture: could not add the zombie");
		StandPower power = PowerClass.STAND.attachGet(zombie);
		try {
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(zombie, power), "Fixture: could not give the zombie Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Fixture: the zombie's Stand is missing");
			stand.setNoGravity(true);
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
			// small precision boxes, so that only the cow the zombie looks at is on its ray
			stand.getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(0);
			Cow first = mobBarrageCow(helper, entities, origin.add(-1.0, 0, 1.8));
			Cow second = mobBarrageCow(helper, entities, origin.add(1.0, 0, 1.8));
			EntityActionType barrage = (EntityActionType) power.getAbility("barrage");
			helper.assertTrue(barrage != null, "Fixture: Star Platinum has no barrage");
			zombie.lookAt(EntityAnchorArgument.Anchor.EYES, first.getBoundingBox().getCenter());
			LivingComponentAction.getComponent(zombie).entityAim.setTarget(new ActionTarget(first));
			EntityActionInstance action = barrage.initActionOnAbilityUse(helper.getLevel(), zombie, stand, null);
			LivingComponentAction standActions = LivingComponentAction.getComponent(stand);
			standActions.setAction(action, zombie, SyncType.NO_SYNC);
			float firstHealth = first.getHealth();
			for (int tick = 0; tick < 12 && first.getHealth() >= firstHealth; tick++) {
				stand.tickCount++;
				standActions.tick();
			}
			helper.assertTrue(first.getHealth() < firstHealth && action.punchedTarget != null
					&& action.punchedTarget.getMainEntity() == first, "Fixture: the zombie's barrage pressed at the cow it looks at"
							+ " did not hit it: " + action.punchedTarget);
			zombie.lookAt(EntityAnchorArgument.Anchor.EYES, second.getBoundingBox().getCenter());
			float secondHealth = second.getHealth();
			for (int tick = 0; tick < 12 && second.getHealth() >= secondHealth; tick++) {
				stand.tickCount++;
				standActions.tick();
			}
			helper.assertTrue(second.getHealth() < secondHealth && action.punchedTarget != null
					&& action.punchedTarget.getMainEntity() == second, "The zombie's barrage stayed on " + action.punchedTarget
							+ ", the cow it was pressed at, after the zombie looked at the other; 1.16 aimed every hit again from"
							+ " the mob's eyes");
			helper.assertTrue(!StandEntityPunchAbility.shouldRetainPunchTarget(stand, barrage),
					"A mob user's barrage keeps the target it was pressed at; 1.16 aimed every hit again");
			standActions.setAction(null, zombie, SyncType.NO_SYNC);
		}
		finally {
			if (power.isSummoned()) {
				type.forceUnsummon(zombie, power);
			}
			entities.forEach(Entity::discard);
		}
		helper.succeed();
	}

	// 1.16 StandEntityMeleeBarrage.checkStandTarget let only a barrage-clash opponent become the task target, when a hit's
	// aim met it (StandEntity.punch 1788-1796); aimWithThisOrUser then kept it while in reach (StandEntity 1676-1678),
	// wherever the user looked, and getOffsetFromUser placed the Stand from it; out of reach, the barrage aimed again
	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH + "clash_lock")
	public static void defaultBarrageStaysOnItsBarrageClashOpponentAsIn116(GameTestHelper helper) {
		barrageClash(helper, ClashCase.OPPONENT_FIRST);
	}

	// the user aims at a cow from the start of the clash: no hit meets the opposing Stand, so 1.16's task target stayed
	// empty (checkStandTarget refused the cow) and every hit aimed again and struck the cow
	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH + "clash_aim")
	public static void defaultBarrageInABarrageClashHitsWhatItsUserAimsAtUntilAHitMeetsTheOpposingStandAsIn116(
			GameTestHelper helper) {
		barrageClash(helper, ClashCase.AIM_FIRST);
	}

	// StandEntity.punch set the task target before the hit, and checkStandTarget took the opposing Stand only while it was
	// the clash opponent: a hit that met it before the clash began set nothing, so once the clash begins the barrage still
	// strikes where its user aims until a hit meets the opposing Stand during the clash
	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH + "clash_late")
	public static void defaultBarrageDoesNotHoldAnOpposingStandItsHitMetBeforeTheClashAsIn116(GameTestHelper helper) {
		barrageClash(helper, ClashCase.HIT_BEFORE_CLASH);
	}

	private enum ClashCase {
		OPPONENT_FIRST,
		AIM_FIRST,
		HIT_BEFORE_CLASH
	}

	private static void barrageClash(GameTestHelper helper, ClashCase clashCase) {
		Scene scene = new Scene(helper, true);
		scene.precision(0);
		Player rival = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		rival.setNoGravity(true);
		rival.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
		rival.setHealth(500);
		rival.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 4, 180, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(rival), "Fixture: could not add the rival Stand user");
		scene.entities.add(rival);
		StandPower rivalPower = PowerClass.STAND.attachGet(rival);
		try {
			helper.assertTrue(StandPowerTransitions.insert(rivalPower, new StandInstance(scene.type)).status()
					== StandPowerTransitions.Status.APPLIED && scene.type.summon(rival, rivalPower),
					"Fixture: could not give the rival Star Platinum");
			StandEntity opponent = rivalPower.getSummonedStandEntity();
			helper.assertTrue(opponent != null, "Fixture: the rival's Stand is missing");
			opponent.setNoGravity(true);
			opponent.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 2, 180, 0);
			Cow cow = scene.cow(1.6, 0.3);
			Entity aimed = clashCase != ClashCase.AIM_FIRST ? opponent : cow;
			scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, aimed.getBoundingBox().getCenter());
			EntityActionType barrage = (EntityActionType) scene.power.getAbility("barrage");
			EntityActionInstance action = barrage.initActionOnAbilityUse(helper.getLevel(), scene.user, scene.stand, null);
			LivingComponentAction standActions = LivingComponentAction.getComponent(scene.stand);
			standActions.setAction(action, scene.user, SyncType.NO_SYNC);
			helper.assertTrue(StandEntityBarrageAbility.isDirectionalBarrage(scene.stand, action)
					&& StandEntityBarrageAbility.clipDirectionalBarrageTarget(scene.stand, action, 1).getMainEntity() == aimed,
					"Fixture: the default barrage does not aim at " + aimed + ", where the user looks");
			Method clash = StandEntity.class.getDeclaredMethod("setBarrageClashOpponent", Entity.class);
			clash.setAccessible(true);
			// as when each Stand has parried the other (StandEntity.barrageClashParryPunches)
			Runnable startClash = () -> {
				try {
					clash.invoke(scene.stand, opponent);
					clash.invoke(opponent, scene.stand);
				}
				catch (ReflectiveOperationException e) {
					throw new IllegalStateException("Fixture: could not start the barrage clash", e);
				}
				helper.assertTrue(scene.stand.barrageClashOpponent().orElse(null) == opponent
						&& opponent.barrageClashOpponent().orElse(null) == scene.stand,
						"Fixture: the two Stands are not in a barrage clash");
			};
			if (clashCase == ClashCase.HIT_BEFORE_CLASH) {
				hitBeforeClash(helper, scene, action, opponent, cow, startClash);
			}
			else if (clashCase == ClashCase.AIM_FIRST) {
				startClash.run();
				float health = cow.getHealth();
				for (int tick = 0; tick < 6; tick++) {
					scene.stand.tickCount++;
					standActions.tick();
				}
				helper.assertTrue(action.punchedTarget != null && action.punchedTarget.getMainEntity() == cow
						&& cow.getHealth() < health, "The default barrage in a barrage clash struck " + action.punchedTarget
								+ " instead of the cow its user aims at; 1.16 took the opposing Stand as its target only once"
								+ " a hit met it");
				ActionTarget clientHeld = clientHeldClashOpponent(helper, scene.stand, action);
				helper.assertTrue(clientHeld.isEmpty(helper.getLevel()), "Clients hold " + clientHeld + " for the default"
						+ " barrage in a barrage clash no hit of which met the opposing Stand, so the Stand turns away from"
						+ " the cow its user aims at; 1.16 sent clients the task target, still empty");
			}
			else {
				startClash.run();
				opponentFirst(helper, scene, action, opponent, cow);
			}
			standActions.setAction(null, scene.user, SyncType.NO_SYNC);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: could not start the barrage clash", e);
		}
		finally {
			scene.stand.barrageClashStopped();
			if (rivalPower.isSummoned()) {
				scene.type.forceUnsummon(rival, rivalPower);
			}
			scene.close();
		}
		helper.succeed();
	}

	// the user looks at the opposing Stand until a hit meets it, the clash begins, and the user turns to the cow
	private static void hitBeforeClash(GameTestHelper helper, Scene scene, EntityActionInstance action, StandEntity opponent,
			Cow cow, Runnable startClash) {
		LivingComponentAction standActions = LivingComponentAction.getComponent(scene.stand);
		helper.assertTrue(scene.stand.barrageClashOpponent().isEmpty() && opponent.barrageClashOpponent().isEmpty(),
				"Fixture: the two Stands are in a barrage clash before the first hit");
		for (int tick = 0; tick < 6 && action.punchedTarget == null; tick++) {
			scene.stand.tickCount++;
			standActions.tick();
		}
		helper.assertTrue(action.punchedTarget != null && action.punchedTarget.getMainEntity() == opponent
				&& scene.stand.barrageClashOpponent().isEmpty(), "Fixture: the barrage at the opposing Stand its user looks at"
						+ " struck " + action.punchedTarget + ", or a clash began: " + scene.stand.barrageClashOpponent());
		startClash.run();
		float health = cow.getHealth();
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
		helper.assertTrue(StandEntityPunchAbility.aimWithThisOrUser(scene.stand, scene.user, 1,
				entity -> entity != scene.stand && StandEntityPunchAbility.canStandHit(scene.stand, entity),
				StandEntityPunchAbility.CrosshairRule.NONE, true).getMainEntity() == cow,
				"Fixture: the user's aim does not take the cow once turned to it");
		ActionTarget held = StandEntityBarrageAbility.clipDirectionalBarrageTarget(scene.stand, action, 1);
		ActionTarget clientHeld = clientHeldClashOpponent(helper, scene.stand, action);
		helper.assertTrue(held.getMainEntity() == cow && clientHeld.isEmpty(helper.getLevel()), "Once the barrage clash"
				+ " began, the default barrage whose last hit met the opposing Stand before the clash aims at " + held
				+ " on the server and holds " + clientHeld + " on clients instead of the cow its user aims at; 1.16 took the"
				+ " opposing Stand as its target only by a hit thrown during the clash");
		for (int tick = 0; tick < 6; tick++) {
			scene.stand.tickCount++;
			standActions.tick();
		}
		helper.assertTrue(action.punchedTarget != null && action.punchedTarget.getMainEntity() == cow
				&& cow.getHealth() < health, "Once the barrage clash began, the default barrage whose hit had met the opposing"
						+ " Stand before the clash struck " + action.punchedTarget + " instead of the cow its user aims at;"
						+ " 1.16 took the opposing Stand as its target only by a hit thrown during the clash");
	}

	// the user looks at the opposing Stand until a hit meets it, then turns to the cow
	private static void opponentFirst(GameTestHelper helper, Scene scene, EntityActionInstance action, StandEntity opponent,
			Cow cow) {
		LivingComponentAction standActions = LivingComponentAction.getComponent(scene.stand);
		float health = cow.getHealth();
		double nearest = Math.min(0.5, scene.stand.getEffectiveRange());
		double farthest = Math.min(1.5, scene.stand.getEffectiveRange());
		double fromOpponent = Mth.clamp(opponent.position().subtract(scene.user.position()).horizontalDistance()
				- (1.0 + opponent.getBbWidth() / 2.0), nearest, farthest);
		helper.assertTrue(Math.abs(fromOpponent - farthest) > 0.1, "Fixture: the Stand placed from the opposing Stand"
				+ " stands at " + fromOpponent + ", as along the user's look");
		for (int tick = 0; tick < 6 && action.punchedTarget == null; tick++) {
			// the Stand's tick: its position update (the barrage's offset), then its action's tick
			scene.stand.updatePosition(scene.user);
			scene.stand.tickCount++;
			standActions.tick();
		}
		helper.assertTrue(action.punchedTarget != null && action.punchedTarget.getMainEntity() == opponent,
				"Fixture: the barrage at the opposing Stand its user looks at struck " + action.punchedTarget);
		assertClientsHold(helper, scene, action, opponent, "once a hit met it");
		// that tick placed the Stand before its first hit, while 1.16's task target was still empty
		Vec3 offset = action.getSyncedStandOffset();
		helper.assertTrue(offset != null && Math.abs(offset.z - farthest) < 0.01, "The default barrage in a barrage clash"
				+ " placed the Stand at " + offset + " from the opposing Stand before a hit met it; 1.16 stood along its"
				+ " user's look, at the farthest " + farthest);
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
		helper.assertTrue(StandEntityPunchAbility.aimWithThisOrUser(scene.stand, scene.user, 1,
				entity -> entity != scene.stand && StandEntityPunchAbility.canStandHit(scene.stand, entity),
				StandEntityPunchAbility.CrosshairRule.NONE, true).getMainEntity() == cow,
				"Fixture: the user's aim does not take the cow once turned to it");
		for (int tick = 0; tick < 6; tick++) {
			scene.stand.updatePosition(scene.user);
			scene.stand.tickCount++;
			standActions.tick();
		}
		offset = action.getSyncedStandOffset();
		helper.assertTrue(action.punchedTarget != null && action.punchedTarget.getMainEntity() == opponent
				&& cow.getHealth() >= health, "The default barrage in a barrage clash struck " + action.punchedTarget
						+ " where its user looks instead of the opposing Stand a hit had met; 1.16 kept it as its target");
		helper.assertTrue(offset != null && Math.abs(offset.z - fromOpponent) < 0.01, "The default barrage on its"
				+ " clash opponent placed the Stand at " + offset + "; 1.16 placed it from its target, at " + fromOpponent);
		assertClientsHold(helper, scene, action, opponent, "while its user looks at the cow");
		opponent.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 8, 180, 0);
		for (int tick = 0; tick < 6; tick++) {
			scene.stand.updatePosition(scene.user);
			scene.stand.tickCount++;
			standActions.tick();
		}
		helper.assertTrue(action.punchedTarget != null && action.punchedTarget.getMainEntity() == cow,
				"The default barrage kept its clash opponent out of reach: it struck " + action.punchedTarget);
		ActionTarget clientHeld = clientHeldClashOpponent(helper, scene.stand, action);
		helper.assertTrue(clientHeld.isEmpty(helper.getLevel()), "Clients still hold " + clientHeld + " for the default"
				+ " barrage whose clash opponent is out of reach and whose hits strike the cow");
	}

	// the server turns the Stand to the opposing Stand it holds; every client must turn it there too
	private static void assertClientsHold(GameTestHelper helper, Scene scene, EntityActionInstance action,
			StandEntity opponent, String when) {
		ActionTarget serverHeld = StandEntityBarrageAbility.clipDirectionalBarrageTarget(scene.stand, action, 1);
		helper.assertTrue(serverHeld.getMainEntity() == opponent, "Fixture: the server does not hold the opposing Stand "
				+ when + ": " + serverHeld);
		ActionTarget clientHeld = clientHeldClashOpponent(helper, scene.stand, action);
		helper.assertTrue(clientHeld.getMainEntity() == opponent, "Clients hold " + clientHeld + " for the default barrage"
				+ " in a barrage clash " + when + ", where the server holds the opposing Stand, so the Stand turns to its"
				+ " user's aim on every client; 1.16 sent clients the task target (StandEntity.setTaskTarget,"
				+ " TrStandTaskTargetPacket)");
	}

	// what a client decides from: a barrage with only the synced data the server sends with the action
	// (SyncActionInstanceData.onStartedTracking), without the server's punchedTarget
	private static ActionTarget clientHeldClashOpponent(GameTestHelper helper, StandEntity stand, EntityActionInstance action) {
		SynchedDataExtended sent = action.synchedData.getDataSyncher();
		helper.assertTrue(sent != null, "The default barrage sends clients no synced data");
		StandEntityBarrage client = new StandEntityBarrage(action.ability);
		try {
			java.lang.reflect.Field performer = EntityActionInstance.class.getDeclaredField("performer");
			performer.setAccessible(true);
			performer.set(client, stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot give the client copy its Stand", e);
		}
		List<SynchedEntityData.DataValue<?>> values = sent.syncOnStartedTracking();
		if (values != null) {
			client.synchedData.getDataSyncher().assignValues(values);
		}
		return client.heldClashOpponentAsSynced(stand);
	}

	// the target the action saved from its press (EntityActionInstance.actionTargetSnapshot)
	static ActionTarget savedTarget(EntityActionInstance action) {
		try {
			java.lang.reflect.Field field = EntityActionInstance.class.getDeclaredField("actionTargetSnapshot");
			field.setAccessible(true);
			return (ActionTarget) field.get(action);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot read the action's saved target", e);
		}
	}

	// the punch's check whether its cry plays, which clients run on its first tick (StandEntityPunch.shouldPlayStandCrySound)
	private static boolean playsStandCry(EntityActionInstance action, StandEntity stand) {
		try {
			Method check = StandEntityPunch.class.getDeclaredMethod("shouldPlayStandCrySound", Level.class, StandEntity.class);
			check.setAccessible(true);
			return (boolean) check.invoke(action, stand.level(), stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot run the punch's cry check", e);
		}
	}

	// what a client holds of an action the server sent it (EntityActionInstance.encode: its phase, the target it keeps and
	// the one its Stand turns to, and so how the Stand aims)
	private static EntityActionInstance clientCopy(EntityActionInstance action, StandEntity stand) {
		EntityActionInstance copy = ((EntityActionAbility) action.ability).createActionObj();
		try {
			setActionField(copy, "performer", stand);
			setActionField(copy, "phase", action.getPhase());
			ActionTarget saved = savedTarget(action);
			setActionField(copy, "actionTargetSnapshot", saved != null ? saved.copy() : null);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot copy the action as a client holds it", e);
		}
		copy.standRotationTarget = action.standRotationTarget != null
				? action.standRotationTarget.copy().resolveEntityId(stand.level()) : null;
		copy.aimAs = action.aimAs;
		return copy;
	}

	private static void setActionField(EntityActionInstance action, String name, Object value)
			throws ReflectiveOperationException {
		java.lang.reflect.Field field = EntityActionInstance.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(action, value);
	}

	private static void setPhase(EntityActionInstance action, ActionPhase phase) {
		try {
			setActionField(action, "phase", phase);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot set the phase of a client's copy", e);
		}
	}

	// the copies a client holds of the action, which take their targets from the server: a heavy punch also as the
	// time-stop punch, whose strike target is its own
	private static List<ClientStrike> clientCopies(EntityActionInstance action, StandEntity stand) {
		List<ClientStrike> copies = new ArrayList<>();
		if (action instanceof StandEntityHeavyPunch) {
			copies.add(clientSide(new ClientHeavyPunch(action.ability), action, stand));
			copies.add(clientSide(new ClientTimeStopPunch(action.ability), action, stand));
		}
		else if (action instanceof StandEntityPunch) {
			copies.add(clientSide(new ClientPunch(action.ability), action, stand));
		}
		else if (action instanceof StandEntityBarrage) {
			copies.add(clientSide(new ClientBarrage(action.ability), action, stand));
		}
		return copies;
	}

	private static <T extends EntityActionInstance & ClientStrike> T clientSide(T copy, EntityActionInstance action,
			StandEntity stand) {
		try {
			setActionField(copy, "performer", stand);
			setActionField(copy, "phase", action.getPhase());
			ActionTarget saved = savedTarget(action);
			setActionField(copy, "actionTargetSnapshot", saved != null ? saved.copy() : null);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot copy the action as a client holds it", e);
		}
		copy.id = action.id;
		copy.standRotationTarget = action.standRotationTarget != null
				? action.standRotationTarget.copy().resolveEntityId(stand.level()) : null;
		copy.aimAs = action.aimAs;
		return copy;
	}

	@Nullable
	private static Entity entityOf(GameTestHelper helper, @Nullable ActionTarget target) {
		return target != null ? target.copy().resolveEntityId(helper.getLevel()).getMainEntity() : null;
	}

	// the packet as a client decodes it
	private static TrEntityActionTargetPacket received(GameTestHelper helper, TrEntityActionTargetPacket packet) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(),
				ConnectionType.NEOFORGE);
		try {
			TrEntityActionTargetPacket.Handler.STREAM_CODEC.encode(buf, packet);
			return TrEntityActionTargetPacket.Handler.STREAM_CODEC.decode(buf);
		}
		finally {
			buf.release();
		}
	}

	// an action as a client holds it (EntityActionInstance.takesTargetsFromServer)
	private interface ClientStrike {
		// what the action strikes now (getPunchTarget)
		ActionTarget strikeTarget(StandEntity stand);
	}

	private static final class ClientPunch extends StandEntityPunch implements ClientStrike {
		ClientPunch(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected boolean takesTargetsFromServer() {
			return true;
		}

		@Override
		public ActionTarget strikeTarget(StandEntity stand) {
			return getPunchTarget(stand);
		}
	}

	private static final class ClientHeavyPunch extends StandEntityHeavyPunch implements ClientStrike {
		ClientHeavyPunch(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected boolean takesTargetsFromServer() {
			return true;
		}

		@Override
		public ActionTarget strikeTarget(StandEntity stand) {
			return getPunchTarget(stand);
		}
	}

	private static final class ClientTimeStopPunch extends TheWorldTSPunch implements ClientStrike {
		ClientTimeStopPunch(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected boolean takesTargetsFromServer() {
			return true;
		}

		@Override
		public ActionTarget strikeTarget(StandEntity stand) {
			return getPunchTarget(stand);
		}
	}

	private static final class ClientBarrage extends StandEntityBarrage implements ClientStrike {
		ClientBarrage(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected boolean takesTargetsFromServer() {
			return true;
		}

		@Override
		public ActionTarget strikeTarget(StandEntity stand) {
			return getPunchTarget(stand);
		}
	}

	// a client tracking the Stand: what the server sends it of the targets of the Stand's action
	static final class TargetWatcher extends FakePlayer {
		final List<TrEntityActionTargetPacket> targets = new ArrayList<>();
		@Nullable private ChunkMap.TrackedEntity tracked;

		TargetWatcher(ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "action-target-watcher"));
			this.connection = new TargetRecordingConnection(level, this);
		}

		List<TrEntityActionTargetPacket> sentFor(EntityActionInstance action) {
			return targets.stream().filter(packet -> packet.actionGeneration() == action.networkGeneration()
					&& packet.actionId() == action.id).toList();
		}

		// tracks the entity as a client would, until untrack
		void track(GameTestHelper helper, Entity entity) {
			tracked = helper.getLevel().getChunkSource().chunkMap.entityMap.get(entity.getId());
			helper.assertTrue(tracked != null, "Fixture: " + entity + " is not tracked");
			tracked.seenBy.add(connection);
		}

		void untrack() {
			if (tracked != null) {
				tracked.seenBy.remove(connection);
				tracked = null;
			}
		}
	}

	private static final class TargetRecordingConnection extends ServerGamePacketListenerImpl {
		private final TargetWatcher watcher;

		TargetRecordingConnection(ServerLevel level, TargetWatcher watcher) {
			super(level.getServer(), openConnection(), watcher,
					CommonListenerCookie.createInitial(watcher.getGameProfile(), false));
			this.watcher = watcher;
		}

		// as GameTestHelper.makeMockServerPlayerInLevel: a live channel, so channel lookups do not fail
		private static Connection openConnection() {
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			return connection;
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
			if (watcher == null) return;
			if (packet instanceof ClientboundBundlePacket bundle) {
				bundle.subPackets().forEach(sub -> send(sub, null));
			}
			else if (packet instanceof ClientboundCustomPayloadPacket custom
					&& custom.payload() instanceof TrEntityActionTargetPacket target) {
				watcher.targets.add(target);
			}
		}
	}

	private static Cow mobBarrageCow(GameTestHelper helper, List<Entity> entities, Vec3 pos) {
		Cow cow = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(cow != null, "Fixture: could not create a cow");
		cow.setNoAi(true);
		cow.setNoGravity(true);
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
		cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
		cow.setHealth(500);
		cow.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Fixture: could not add a cow");
		entities.add(cow);
		return cow;
	}

	private interface Step {
		boolean run(int tick);
	}

	private record PlacedBlock(BlockPos pos, BlockState previous) {}

	static final class Scene {
		final GameTestHelper helper;
		final Player user;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		final Vec3 origin;
		final List<Entity> entities = new ArrayList<>();
		final List<PlacedBlock> placedBlocks = new ArrayList<>();
		@Nullable TargetWatcher watcher;
		@Nullable ChunkMap.TrackedEntity trackedStand;
		int ticks;
		boolean finished;
		boolean closed;

		Scene(GameTestHelper helper, boolean aimAssist) {
			this.helper = helper;
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.noPhysics = true;
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			user.setYHeadRot(0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			entities.add(user);
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "Missing Star Platinum entity");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.setNoGravity(true);
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
			user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16);
			StandEntity.updateStandStatAttributes(stand, user);
			power.setStamina(power.getMaxStamina());
			stand.updateStrengthMultipliers();
			var settings = user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
			settings.standAttackTargetLock = false;
			settings.standAimAssist = aimAssist;
		}

		// stone at the user's feet level from 1 to 3 blocks ahead; returns the one 2 blocks ahead
		BlockPos groundStrip() {
			return groundStrip(Blocks.STONE.defaultBlockState());
		}

		BlockPos groundStrip(BlockState state) {
			BlockPos feet = BlockPos.containing(origin.x, origin.y - 0.5, origin.z);
			for (int z = 1; z <= 3; z++) {
				block(feet.south(z), state);
			}
			return feet.south(2);
		}

		void precision(double precision) {
			user.getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(precision);
			StandEntity.updateStandStatAttributes(stand, user);
			stand.getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(precision);
			helper.assertTrue(stand.getPrecision() == precision, "Fixture: the Stand's precision is " + stand.getPrecision());
		}

		// the Stand at the given place, its eyes turned to the middle of the entity, as its user turns it under manual control
		void standAt(Vec3 pos, Entity facing) {
			stand.moveTo(pos.x, pos.y, pos.z, stand.getYRot(), stand.getXRot());
			stand.setDeltaMovement(Vec3.ZERO);
			stand.lookAt(EntityAnchorArgument.Anchor.EYES, facing.getBoundingBox().getCenter());
		}

		boolean isStone(BlockPos pos) {
			return helper.getLevel().getBlockState(pos).is(Blocks.STONE);
		}

		void block(BlockPos pos, BlockState state) {
			placedBlocks.add(new PlacedBlock(pos, helper.getLevel().getBlockState(pos)));
			helper.getLevel().setBlockAndUpdate(pos, state);
		}

		Cow cow(double x, double z) {
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Could not create a cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.noPhysics = true;
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
			cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
			cow.setHealth(500);
			cow.moveTo(origin.x + x, origin.y, origin.z + z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add a cow");
			entities.add(cow);
			return cow;
		}

		// a client that tracks the Stand
		TargetWatcher watchStand() {
			watcher = new TargetWatcher(helper.getLevel());
			trackedStand = helper.getLevel().getChunkSource().chunkMap.entityMap.get(stand.getId());
			helper.assertTrue(trackedStand != null, "Fixture: the Stand is not tracked");
			trackedStand.seenBy.add(watcher.connection);
			return watcher;
		}

		<T extends Entity> T entity(EntityType<T> entityType, double x, double z) {
			T entity = entityType.create(helper.getLevel());
			helper.assertTrue(entity != null, "Could not create " + entityType);
			entity.setNoGravity(true);
			entity.moveTo(origin.x + x, origin.y, origin.z + z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(entity), "Could not add " + entityType);
			entities.add(entity);
			return entity;
		}

		// within Star Platinum's precision boxes of the user's eyes, off the crosshair
		Cow besideTheCrosshair() {
			Cow cow = cow(1.2, 0.8);
			helper.assertTrue(cow.getBoundingBox().distanceToSqr(user.getEyePosition()) < 1.9 * 1.9,
					"Fixture: the cow is not within 1.9 blocks of the user's eyes");
			return cow;
		}

		// the user's own tame wolf; the Stand cannot attack it
		Wolf ownWolf(Vec3 pos) {
			Wolf wolf = wolf(pos, () -> user);
			wolf.setTame(true, false);
			wolf.setOwnerUUID(user.getUUID());
			helper.assertTrue(wolf.getOwner() == user && !StandEntityPunchAbility.canStandHit(stand, wolf),
					"Fixture: the wolf is not the user's own, or the Stand can attack it");
			return wolf;
		}

		Wolf wolf(Vec3 pos, Supplier<LivingEntity> owner) {
			Wolf wolf = new Wolf(EntityType.WOLF, helper.getLevel()) {
				// the level finds a real owner, a server player, by its UUID; this mock user is not one
				@Override
				public LivingEntity getOwner() {
					return owner.get();
				}
			};
			wolf.setNoAi(true);
			wolf.setNoGravity(true);
			wolf.noPhysics = true;
			wolf.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
			wolf.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
			wolf.setHealth(500);
			wolf.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(wolf), "Could not add a wolf");
			entities.add(wolf);
			return wolf;
		}

		// what the vanilla pick meets; the Stand standing on its user here is no part of a real view
		Predicate<Entity> pickable() {
			return entity -> entity != stand && _EntitySelector.CAN_BE_PICKED.test(entity);
		}

		ActionTarget vanillaPick() {
			return StandEntityPunchAbility.crosshairPick(stand, user, 1, pickable());
		}

		// the aims a client sends, its vanilla pick and its Stand aim, through the packet handlers' own step
		ActionTarget sendClientAims() {
			ActionTarget pick = vanillaPick();
			ActionTarget standAim = StandEntityPunchAbility.userStandAim(stand, user, pick,
					entity -> StandEntityAbility.canDefaultTargetEntityForAiming(stand, entity));
			LivingComponentAction.getComponent(user).entityAim.setTargetFromClient(pick, helper.getLevel());
			LivingComponentAction.getComponent(stand).entityAim.setTargetFromClient(standAim, helper.getLevel());
			return standAim;
		}

		EntityActionInstance press(String name, InputMethod method) {
			Ability base = power.getAbility(name);
			var selected = power.updateAvailableMoves().getContextVariationContainer(base);
			helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, method),
					"The " + name + " input was not admitted");
			BufferingState buffer = BufferingState.clickOnly();
			var held = AbilityInput.keyPress(KEY, selected.ability, user, null, method, 0, buffer, base.getAbilityId());
			helper.assertTrue(buffer.isActionSuccess, "The " + name + " key press failed");
			return held != null && held.action instanceof EntityActionInstance a ? a : stand.getCurStandAction();
		}

		// started on the Stand as its key's press starts a punch, its target from the aims the client sent
		EntityActionInstance startHeavyAttackPunch() {
			HeavyAttackPunchProbe probe = new HeavyAttackPunchProbe(ModStandAbilities.PUNCH.get(),
					new AbilityId(PowerClass.STAND, type.getId(), HEAVY_ATTACK_PUNCH));
			EntityActionInstance action = probe.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(stand.getCurStandAction() == action, "Fixture: the punch standing for a heavy attack did not"
					+ " start on the Stand");
			return action;
		}

		// started on the Stand as its key's press starts a punch, its target from the aims the client sent
		EntityActionInstance startOwnStrike(boolean heavyAttack) {
			OwnStrikeProbe probe = new OwnStrikeProbe(ModStandAbilities.PUNCH.get(),
					new AbilityId(PowerClass.STAND, type.getId(), "own_strike_probe"), heavyAttack);
			EntityActionInstance action = probe.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(stand.getCurStandAction() == action, "Fixture: the punch striking in its own perform did not"
					+ " start on the Stand");
			return action;
		}

		// started on the Stand as its key's press starts a punch or heavy punch, its target from the aims the client sent
		EntityActionInstance startOwnTarget(boolean heavy) {
			EntityActionType probe = heavy
					? new OwnTargetHeavyProbe(ModStandAbilities.HEAVY_PUNCH.get(),
							new AbilityId(PowerClass.STAND, type.getId(), "own_target_heavy_probe"))
					: new OwnTargetProbe(ModStandAbilities.PUNCH.get(),
							new AbilityId(PowerClass.STAND, type.getId(), "own_target_probe"));
			EntityActionInstance action = probe.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(stand.getCurStandAction() == action, "Fixture: the " + (heavy ? "heavy punch" : "punch")
					+ " with a target of its own did not start on the Stand");
			return action;
		}

		// a held move started on the Stand and its key released, as StandSkillWallsGameTests drives the charged heavy punch
		EntityActionInstance release(String name) {
			EntityActionInstance action = ((EntityActionType) power.getAbility(name))
					.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(stand.getCurStandAction() == action, "Fixture: the " + name + " did not start on the Stand");
			action.onKeyRelease(user);
			return action;
		}

		void run(Step step, int lastTick, java.util.function.Supplier<String> timeoutMessage) {
			helper.onEachTick(() -> {
				if (finished) return;
				try {
					if (step.run(++ticks)) {
						finished = true;
						close();
						helper.succeed();
					}
					else if (ticks >= lastTick) {
						finished = true;
						close();
						helper.fail(timeoutMessage.get());
					}
				}
				catch (RuntimeException | Error error) {
					finished = true;
					close();
					throw error;
				}
			});
		}

		void close() {
			if (closed) return;
			closed = true;
			if (trackedStand != null && watcher != null) {
				trackedStand.seenBy.remove(watcher.connection);
			}
			AbilityInput.keyRelease(KEY, user);
			if (power.isSummoned()) type.forceUnsummon(user, power);
			entities.forEach(Entity::discard);
			for (int i = placedBlocks.size() - 1; i >= 0; i--) {
				helper.getLevel().setBlockAndUpdate(placedBlocks.get(i).pos(), placedBlocks.get(i).previous());
			}
			placedBlocks.clear();
		}
	}
}
