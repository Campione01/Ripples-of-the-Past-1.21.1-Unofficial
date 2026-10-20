package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.gametest.StandTaskTargetOffsetGameTests.Scene;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility.StandEntityChargedHeavy;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.impl.stands.silverchariot.SilverChariotDashAttackAbility.DashStrike;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility.TheWorldTSPunch;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.util.functions.MathUtil;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.punch: before each punch, heavy punch and barrage hit, and each strike of the time-stop punch and of
 * Silver Chariot's dash, which struck through it too, StandEntity.aimWithThisOrUser turned the Stand toward what it aimed
 * at (MCUtil.rotateTowards), on the server.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandStrikeTurnGameTests {
	private static final double EPS = 1.0E-3D;

	private StandStrikeTurnGameTests() {}

	/*
	 * The turn went toward an entity's eyes, the yaw and the pitch each by at most attackSpeed / 16 x 18 degrees, the head
	 * onto the new yaw, before the hit: toward a cow in the Stand's reach, which the strike then hit, and toward one beyond
	 * it, which the punch missed.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void eachStrikeTurnsTheStandTowardItsAimByAtMostItsAttackSpeedBeforeTheHitAsIn116(GameTestHelper helper) {
		String[][] rows = { { "star_platinum", "punch" }, { "star_platinum", "heavy_punch" }, { "star_platinum", "barrage" },
				{ "the_world", "ts_punch" }, { "silver_chariot", "dash_attack" }, { "star_platinum", "punch at a cow beyond its reach" },
				{ "star_platinum", "heavy_charged" } };
		for (String[] row : rows) {
			StandTaskTargetOffsetGameTests.withStand(helper, row[0], scene -> strike(helper, scene, row[0], row[1]));
		}
		helper.succeed();
	}

	private static void strike(GameTestHelper helper, Scene scene, String standId, String row) {
		StandEntity stand = scene.stand();
		Player user = scene.user();
		StandPower power = scene.power();
		boolean beyond = row.startsWith("punch at");
		boolean dash = row.equals("dash_attack");
		power.setResolveLevel(power.getMaxResolveLevel());
		power.setStamina(power.getMaxStamina());
		stand.summonLockTicks = 0;
		stand.gradualSummonWeaknessTicks = 0;
		stand.updatePosition(user);
		Cow[] cowRef = { null };
		if (!dash) {
			Cow cow = scene.cow(new Vec3(0.0D, 0.0D, beyond ? 4.5D : 1.6D));
			cowRef[0] = cow;
			user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getEyePosition());
			user.yRotO = user.getYRot();
			user.xRotO = user.getXRot();
			user.setYHeadRot(user.getYRot());
			stand.updatePosition(user);
			LivingComponentAction.getComponent(user).entityAim.setTarget(beyond ? ActionTarget.EMPTY : new ActionTarget(cow));
		}
		float[] atHit = { Float.NaN, Float.NaN };
		EntityActionType type = strikeType(helper, scene, beyond ? "punch" : row);
		EntityActionInstance action = switch (row) {
			case "heavy_punch" -> new StandEntityHeavyPunch(type) {
				@Override
				protected void hitEntity(ActionTarget target, Level level, StandEntity performer, DamageSource dmgSource,
						float dmgAmount, float explRadius) {
					noteStrike(atHit, performer);
					super.hitEntity(target, level, performer, dmgSource, dmgAmount, explRadius);
				}
			};
			// the port's charged heavy punch turns at its strike as the heavy punch it is modelled on (owner, wave 21)
			case "heavy_charged" -> new StandEntityChargedHeavy(type) {
				@Override
				protected void hitEntity(ActionTarget target, Level level, StandEntity performer, DamageSource dmgSource,
						float dmgAmount) {
					noteStrike(atHit, performer);
					super.hitEntity(target, level, performer, dmgSource, dmgAmount);
				}
			};
			case "barrage" -> new StandEntityBarrage(type) {
				@Override
				protected void hitEntity(ActionTarget target, Level level, StandEntity performer) {
					noteStrike(atHit, performer);
					super.hitEntity(target, level, performer);
				}
			};
			case "ts_punch" -> new TheWorldTSPunch(type) {
				@Override
				protected ActionTarget getPunchTarget(StandEntity performer) {
					return new ActionTarget(cowRef[0]);
				}

				@Override
				protected void hitEntity(ActionTarget target, Level level, StandEntity performer, DamageSource dmgSource,
						float dmgAmount, float explRadius) {
					noteStrike(atHit, performer);
					super.hitEntity(target, level, performer, dmgSource, dmgAmount, explRadius);
				}
			};
			case "dash_attack" -> type.createActionObj();
			default -> new StandEntityPunch(type) {
				@Override
				protected void hitEntity(ActionTarget target, Level level, StandEntity performer) {
					noteStrike(atHit, performer);
					super.hitEntity(target, level, performer);
				}
			};
		};
		type.initActionFromConfig(action, helper.getLevel(), user, stand);
		action.setStartingPhase();
		LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
		helper.assertTrue(stand.getCurStandAction() == action && (!dash || action instanceof DashStrike),
				"setup: the " + row + " is not the " + standId + "'s action");
		if (dash) {
			// once the dash has put the Stand in front of its user: on the Stand's look south, its middle at the Stand's
			// eyes, so that the dash's ray meets it
			stand.setYRot(0.0F);
			stand.setXRot(0.0F);
			stand.setYHeadRot(0.0F);
			cowRef[0] = scene.cow(new Vec3(stand.getX(), stand.getEyeY() - 0.7D, stand.getZ() + 2.0D).subtract(user.position()));
		}
		Cow cow = cowRef[0];

		Vec3 toEyes = cow.getEyePosition().subtract(stand.getEyePosition());
		float aimYaw = MathUtil.yRotDegFromVec(toEyes);
		float aimPitch = MathUtil.xRotDegFromVec(toEyes);
		if (!dash) {
			stand.setYRot(aimYaw + 60.0F);
			stand.setXRot(aimPitch + 40.0F);
			stand.setYHeadRot(aimYaw + 60.0F);
		}
		float yaw0 = stand.getYRot();
		float pitch0 = stand.getXRot();
		float max = (float) stand.getAttackSpeed() / 16.0F * 18.0F;
		helper.assertTrue(max > 0.0F && (dash || max < 40.0F), "setup: the " + standId + " turns by up to " + max + " degrees");
		float yaw = yaw0 + Mth.clamp(Mth.degreesDifference(yaw0, aimYaw), -max, max);
		float pitch = pitch0 + Mth.clamp(Mth.degreesDifference(pitch0, aimPitch), -max, max);
		helper.assertTrue(Math.abs(Mth.degreesDifference(yaw0, yaw)) + Math.abs(pitch - pitch0) > 1.0F,
				"setup: the " + standId + " already faces the cow");

		switch (row) {
			case "barrage" -> {
				action.setPhaseStart(ActionPhase.PERFORM);
				action.actionTick();
			}
			case "dash_attack" -> dashTargets(action, stand);
			// its strike comes at the end of its lunge
			case "heavy_charged" -> {
				action.actionPerformStart();
				action.actionPerformEnd();
			}
			default -> action.actionPerformStart();
		}
		String what = "a " + standId + "'s " + row + " (yaw " + yaw0 + ", pitch " + pitch0 + ", turning by up to " + max
				+ " degrees)";
		if (beyond) {
			helper.assertTrue(Float.isNaN(atHit[0]) && cow.getHealth() == cow.getMaxHealth(), "setup: " + what + " hit the cow");
		}
		else if (!dash) {
			helper.assertTrue(!Float.isNaN(atHit[0]), "setup: " + what + " did not hit the cow");
			helper.assertTrue(Math.abs(Mth.degreesDifference(atHit[0], yaw)) < EPS && Math.abs(atHit[1] - pitch) < EPS,
					"1.16 turned " + what + " toward the cow's eyes before the hit, to yaw " + yaw + ", pitch " + pitch
							+ "; it struck at yaw " + atHit[0] + ", pitch " + atHit[1]);
		}
		else {
			helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "setup: " + what + " did not hit the cow");
		}
		helper.assertTrue(Math.abs(Mth.degreesDifference(stand.getYRot(), yaw)) < EPS && Math.abs(stand.getXRot() - pitch) < EPS
				&& Math.abs(Mth.degreesDifference(stand.getYHeadRot(), yaw)) < EPS, "1.16 turned " + what + " toward the eyes of a cow "
						+ (beyond ? "beyond its reach, which it missed," : "it struck") + " to yaw " + yaw + ", pitch " + pitch
						+ " and its head onto that yaw; it is at yaw " + stand.getYRot() + ", pitch " + stand.getXRot() + ", head "
						+ stand.getYHeadRot());
		LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
	}

	/*
	 * 1.16 StandEntity.punch set each dash strike's target as the task's (setTaskTarget), but StandEntityTask.keepTarget kept
	 * none for the dash: checkRangeAndTarget asked standKeepsTarget, false for SilverChariotDashAttack, and its
	 * checkStandTarget was always NEGATIVE. So the task held no target and StandEntityAction.rotateStand left a Stand that
	 * moves by itself as its strike turned it, wherever the entity it struck went next.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theDashHoldsNoTargetAfterItsStrikeAndKeepsItsStrikeTurnAsIn116(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
			StandEntity stand = scene.stand();
			Player user = scene.user();
			StandPower power = scene.power();
			power.setResolveLevel(power.getMaxResolveLevel());
			power.setStamina(power.getMaxStamina());
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.updatePosition(user);
			EntityActionType type = strikeType(helper, scene, "dash_attack");
			EntityActionInstance dash = type.createActionObj();
			type.initActionFromConfig(dash, helper.getLevel(), user, stand);
			dash.setStartingPhase();
			LivingComponentAction.getComponent(stand).setAction(dash, user, SyncType.NO_SYNC);
			helper.assertTrue(stand.getCurStandAction() == dash && dash instanceof DashStrike && !stand.isFollowingUser(),
					"setup: the dash is not the Silver Chariot's action, or its Stand still follows its user");
			stand.setYRot(0.0F);
			stand.setXRot(0.0F);
			stand.setYHeadRot(0.0F);
			Cow cow = scene.cow(new Vec3(stand.getX() + 0.5D, stand.getEyeY() - 0.7D, stand.getZ() + 2.0D).subtract(user.position()));
			dashTargets(dash, stand);
			float yaw = stand.getYRot();
			float pitch = stand.getXRot();
			helper.assertTrue(cow.getHealth() < cow.getMaxHealth() && Math.abs(yaw) > EPS,
					"setup: the dash did not strike the cow, or did not turn toward it: yaw " + yaw);
			helper.assertTrue(dash.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY,
					"1.16's dash task held no target after its strike; it holds " + dash.getTaskTarget(helper.getLevel()));

			cow.moveTo(cow.getX() + 3.0D, cow.getY() + 1.0D, cow.getZ(), 0.0F, 0.0F);
			stand.updatePosition(user);
			helper.assertTrue(Math.abs(Mth.degreesDifference(stand.getYRot(), yaw)) < EPS && Math.abs(stand.getXRot() - pitch) < EPS,
					"1.16 left the dashing Stand as its strike turned it (yaw " + yaw + ", pitch " + pitch + ") once the cow it struck"
							+ " moved; it is at yaw " + stand.getYRot() + ", pitch " + stand.getXRot());
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
		});
		helper.succeed();
	}

	/*
	 * 1.16 SilverChariotDashAttack called StandEntity.punch on every tick it moved forward, with each entity its ray met or,
	 * when the ray met nothing in the air, with that miss, and on its first tick after that with its task's target, which
	 * the dash never kept. punch with no target aimed again (aimWithThisOrUser: the user's precision ray at the user's
	 * reach, else the Stand's own), turned the Stand toward what that met by at most attackSpeed / 16 x 18 degrees and
	 * struck it if it lay in the Stand's reach. Rows: a forward tick whose ray meets nothing, the user aiming at a cow off
	 * the dash line in the Stand's reach, and at one beyond it but within the user's; the tick after the forward half,
	 * the user's aim moved from the cow the dash started at to another one.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theDashAimsAgainThroughItsUserWhenItsRayMeetsNothingAsIn116(GameTestHelper helper) {
		for (String row : new String[] { "forward, in reach", "forward, beyond reach", "after the forward half" }) {
			StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
				StandEntity stand = scene.stand();
				Player user = scene.user();
				StandPower power = scene.power();
				Level level = helper.getLevel();
				boolean secondHalf = row.startsWith("after");
				boolean inReach = !row.endsWith("beyond reach");
				power.setResolveLevel(power.getMaxResolveLevel());
				power.setStamina(power.getMaxStamina());
				stand.summonLockTicks = 0;
				stand.gradualSummonWeaknessTicks = 0;
				stand.updatePosition(user);
				EntityActionType type = strikeType(helper, scene, "dash_attack");
				Cow started = null;
				if (secondHalf) {
					// the dash starts at a cow its Stand's aim holds, in its reach, well off the user's later aim
					started = scene.cow(new Vec3(stand.getX() - 3.0D, stand.getEyeY() - 0.7D, stand.getZ() + 0.5D)
							.subtract(user.position()));
					LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(started));
				}
				EntityActionInstance dash = type.createActionObj();
				type.initActionFromConfig(dash, level, user, stand);
				dash.setStartingPhase();
				LivingComponentAction.getComponent(stand).setAction(dash, user, SyncType.NO_SYNC);
				helper.assertTrue(stand.getCurStandAction() == dash && dash instanceof DashStrike
						&& dash.getPhase() == ActionPhase.PERFORM, "setup: the dash is not the Silver Chariot's action in PERFORM");
				if (secondHalf) {
					int tick = (int) (dash.getCurPhaseLength() / 2.0F) + 1;
					dash.setPhase(ActionPhase.PERFORM, tick);
					helper.assertTrue(dash.getPhase() == ActionPhase.PERFORM && dash.getPhaseRatio() > 0.5F,
							"setup: the dash is not past the forward half of its PERFORM: tick " + tick + " of " + dash.getCurPhaseLength());
				}
				// dashing south, two blocks ahead of its user; the cow beside its path, clear of the dash ray's precision boxes
				Vec3 at = user.position().add(0.0D, stand.getY() - user.getY(), 2.0D);
				stand.setPos(at.x, at.y, at.z);
				stand.setYRot(0.0F);
				stand.setXRot(0.0F);
				stand.setYHeadRot(0.0F);
				Vec3 cowFromStand = inReach ? new Vec3(2.9D, 0.0D, 0.3D) : new Vec3(4.3D, 0.0D, 0.5D);
				Cow cow = scene.cow(new Vec3(stand.getX(), stand.getEyeY() - 0.7D, stand.getZ()).add(cowFromStand)
						.subtract(user.position()));
				user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getEyePosition());
				user.yRotO = user.getYRot();
				user.xRotO = user.getXRot();
				user.setYHeadRot(user.getYRot());

				double reach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
				List<ActionTarget> onRay = HitResultUtil.clipMultipleTargets(stand, reach, StandPrecisionRay.canTarget(stand), 0.25D,
						stand.getPrecision());
				helper.assertTrue(onRay.isEmpty(), "setup: the dash's ray meets " + onRay + " (" + row + ", the Stand's eyes at "
						+ stand.getEyePosition() + ", its user at " + user.position() + ")");
				helper.assertTrue(StandEntityPunchAbility.userPrecisionRayTarget(stand, user).getEntity() == cow,
						"setup: the user's precision ray does not meet the cow");
				helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(cow)).isEmpty(level) != inReach,
						"setup: the cow is " + (inReach ? "not " : "") + "in the Stand's reach " + reach);
				Vec3 toEyes = cow.getEyePosition().subtract(stand.getEyePosition());
				float aimYaw = MathUtil.yRotDegFromVec(toEyes);
				float aimPitch = MathUtil.xRotDegFromVec(toEyes);
				float max = (float) stand.getAttackSpeed() / 16.0F * 18.0F;
				float yaw = Mth.clamp(Mth.degreesDifference(0.0F, aimYaw), -max, max);
				float pitch = Mth.clamp(aimPitch, -max, max);
				helper.assertTrue(max > 0.0F && Math.abs(Mth.degreesDifference(0.0F, aimYaw)) > max + 1.0F,
						"setup: the Stand turns by up to " + max + " degrees and faces the cow within that: yaw " + aimYaw);

				stand.setDeltaMovement(secondHalf ? new Vec3(0.0D, 0.0D, 0.5D) : Vec3.ZERO);
				dash.actionTick();
				String what = "a silver_chariot's dash, " + row + ", whose ray met nothing while its user aimed at a cow "
						+ (inReach ? "in" : "beyond") + " its reach (yaw 0, pitch 0, turning by up to " + max + " degrees)";
				helper.assertTrue(Math.abs(Mth.degreesDifference(stand.getYRot(), yaw)) < EPS && Math.abs(stand.getXRot() - pitch) < EPS
						&& Math.abs(Mth.degreesDifference(stand.getYHeadRot(), yaw)) < EPS, "1.16 aimed " + what + " again through its"
								+ " user and turned it toward the cow's eyes, to yaw " + yaw + ", pitch " + pitch + "; it is at yaw "
								+ stand.getYRot() + ", pitch " + stand.getXRot() + ", head " + stand.getYHeadRot());
				helper.assertTrue((cow.getHealth() < cow.getMaxHealth()) == inReach, "1.16 " + (inReach ? "struck" : "missed")
						+ " the cow of " + what + ": its health is " + cow.getHealth() + " of " + cow.getMaxHealth());
				if (started != null) {
					helper.assertTrue(started.getHealth() == started.getMaxHealth(), "1.16 aimed " + what + " afresh, not at the"
							+ " cow it started at: that one was struck");
				}
				LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16's dash ray took any entity but the Stand, its user and the Stand's own projectiles (StandEntity.canTarget), so
	 * StandEntity.punch turned the Stand toward an entity the Stand may not harm on that ray as toward any other, and only
	 * the strike's own harm check (StandEntity.attackEntity, canHarm) left it unhurt; with an entity on the ray the forward
	 * tick did not aim again through the user. Rows: a cow and a player on the user's team without friendly fire, on the
	 * dash ray beside its middle, while the user aims at another cow off the dash line in the Stand's reach.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theDashTurnsTowardAnEntityItMayNotHarmOnItsRayAndLeavesItUnhurtAsIn116(GameTestHelper helper) {
		for (String row : new String[] { "a cow on its user's team", "a player on its user's team" }) {
			StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
				StandEntity stand = scene.stand();
				Player user = scene.user();
				StandPower power = scene.power();
				Level level = helper.getLevel();
				power.setResolveLevel(power.getMaxResolveLevel());
				power.setStamina(power.getMaxStamina());
				stand.summonLockTicks = 0;
				stand.gradualSummonWeaknessTicks = 0;
				stand.updatePosition(user);
				EntityActionType type = strikeType(helper, scene, "dash_attack");
				EntityActionInstance dash = type.createActionObj();
				type.initActionFromConfig(dash, level, user, stand);
				dash.setStartingPhase();
				LivingComponentAction.getComponent(stand).setAction(dash, user, SyncType.NO_SYNC);
				helper.assertTrue(stand.getCurStandAction() == dash && dash instanceof DashStrike
						&& dash.getPhase() == ActionPhase.PERFORM, "setup: the dash is not the Silver Chariot's action in PERFORM");
				// dashing south, two blocks ahead of its user
				Vec3 at = user.position().add(0.0D, stand.getY() - user.getY(), 2.0D);
				stand.setPos(at.x, at.y, at.z);
				stand.setYRot(0.0F);
				stand.setXRot(0.0F);
				stand.setYHeadRot(0.0F);
				PlayerTeam team = null;
				try {
					LivingEntity spared;
					if (row.startsWith("a player")) {
						Player teammate = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "dash-teammate")) {
							@Override
							public boolean isSpectator() {
								return false;
							}

							@Override
							public boolean isCreative() {
								return false;
							}
						};
						teammate.setNoGravity(true);
						helper.assertTrue(level.addFreshEntity(teammate), "setup: could not add the teammate player");
						scene.spawned().add(teammate);
						spared = teammate;
					}
					else {
						spared = scene.cow(Vec3.ZERO);
					}
					team = level.getScoreboard().addPlayerTeam("dash_teammate_" + user.getId());
					team.setAllowFriendlyFire(false);
					level.getScoreboard().addPlayerToTeam(user.getScoreboardName(), team);
					level.getScoreboard().addPlayerToTeam(spared.getScoreboardName(), team);
					// on the Stand's look south, its middle at the Stand's eyes, half a block west of the dash line (the aimed cow
					// is east of it)
					spared.moveTo(stand.getX() - 0.5D, stand.getEyeY() - spared.getBbHeight() / 2.0D, stand.getZ() + 2.0D, 0.0F, 0.0F);
					Cow aimed = scene.cow(new Vec3(stand.getX(), stand.getEyeY() - 0.7D, stand.getZ()).add(2.9D, 0.0D, 0.3D)
							.subtract(user.position()));
					user.lookAt(EntityAnchorArgument.Anchor.EYES, aimed.getEyePosition());
					user.yRotO = user.getYRot();
					user.xRotO = user.getXRot();
					user.setYHeadRot(user.getYRot());

					double reach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
					List<ActionTarget> onRay = HitResultUtil.clipMultipleTargets(stand, reach, StandPrecisionRay.canTarget(stand), 0.25D,
							stand.getPrecision());
					helper.assertTrue(!stand.canAttackEntity(spared) && stand.canAttackEntity(aimed),
							"setup: the Stand may harm " + row + ", or may not harm the cow its user aims at");
					helper.assertTrue(onRay.size() == 1 && onRay.get(0).getEntity() == spared, "setup: the dash's ray meets " + onRay
							+ ", not only " + row);
					helper.assertTrue(StandEntityPunchAbility.userPrecisionRayTarget(stand, user).getEntity() == aimed
							&& !StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(aimed)).isEmpty(level),
							"setup: the user's precision ray does not meet the cow it aims at, or that cow is not in the Stand's reach");
					float max = (float) stand.getAttackSpeed() / 16.0F * 18.0F;
					Vec3 toSpared = spared.getEyePosition().subtract(stand.getEyePosition());
					float yaw = Mth.clamp(Mth.degreesDifference(0.0F, MathUtil.yRotDegFromVec(toSpared)), -max, max);
					float pitch = Mth.clamp(MathUtil.xRotDegFromVec(toSpared), -max, max);
					Vec3 toAimed = aimed.getEyePosition().subtract(stand.getEyePosition());
					float aimedYaw = Mth.clamp(Mth.degreesDifference(0.0F, MathUtil.yRotDegFromVec(toAimed)), -max, max);
					helper.assertTrue(Math.abs(yaw) > 1.0F && Math.abs(Mth.degreesDifference(yaw, aimedYaw)) > 1.0F,
							"setup: the turn toward " + row + " (yaw " + yaw + ") does not tell it from no turn or from the turn toward"
									+ " the aimed cow (yaw " + aimedYaw + ")");

					stand.setDeltaMovement(Vec3.ZERO);
					dash.actionTick();
					String what = "a silver_chariot's dash whose ray met only " + row + ", while its user aimed at a cow off the dash"
							+ " line in its reach (yaw 0, pitch 0, turning by up to " + max + " degrees)";
					helper.assertTrue(Math.abs(Mth.degreesDifference(stand.getYRot(), yaw)) < EPS && Math.abs(stand.getXRot() - pitch) < EPS
							&& Math.abs(Mth.degreesDifference(stand.getYHeadRot(), yaw)) < EPS, "1.16 turned " + what + " toward the eyes of "
									+ row + ", to yaw " + yaw + ", pitch " + pitch + ", as it struck it; it is at yaw " + stand.getYRot()
									+ ", pitch " + stand.getXRot() + ", head " + stand.getYHeadRot());
					helper.assertTrue(spared.getHealth() == spared.getMaxHealth() && aimed.getHealth() == aimed.getMaxHealth(),
							"1.16 struck " + row + " unhurt and did not aim " + what + " again through its user: " + row + " has "
									+ spared.getHealth() + " of " + spared.getMaxHealth() + ", the aimed cow " + aimed.getHealth() + " of "
									+ aimed.getMaxHealth());
				}
				finally {
					if (team != null) {
						level.getScoreboard().removePlayerTeam(team);
					}
					LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
				}
			});
		}
		helper.succeed();
	}

	private static void noteStrike(float[] atHit, StandEntity stand) {
		if (Float.isNaN(atHit[0])) {
			atHit[0] = stand.getYRot();
			atHit[1] = stand.getXRot();
		}
	}

	private static EntityActionType strikeType(GameTestHelper helper, Scene scene, String name) {
		Ability ability = scene.power().getAbility(name);
		if (!(ability instanceof EntityActionType)) {
			scene.power().getCurTypeData()._setSkillUnlocked(name, true, false);
			ability = scene.power().getAbility(name);
		}
		helper.assertTrue(ability instanceof EntityActionType, "Missing registered " + name);
		return (EntityActionType) ability;
	}

	// the dash's strikes on the targets its ray meets while it moves forward
	private static void dashTargets(EntityActionInstance dash, StandEntity stand) {
		try {
			Method hit = DashStrike.class.getDeclaredMethod("hitDashTargets", Level.class, StandEntity.class);
			hit.setAccessible(true);
			hit.invoke(dash, stand.level(), stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not run the dash's ray hit", e);
		}
	}
}
