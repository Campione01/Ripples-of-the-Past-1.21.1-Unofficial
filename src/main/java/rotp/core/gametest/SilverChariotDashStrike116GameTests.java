package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.gametest.StandTaskTargetOffsetGameTests.Scene;
import rotp.core.impl.stands.silverchariot.SilverChariotDashAttackAbility.DashStrike;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.MathUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 SilverChariotDashAttack.standTickPerform punched every entity its ray met through StandEntity.punch, living or not:
 * StandEntity.attackTarget gave a non-living one its punchEntity (the dash's HeavyPunchInstance, impact sound null),
 * StandEntityPunch.doHit hurt it through attackEntity's canHarm and set the dash's knockback as its motion whether it was
 * hurt or not (knockbackTarget). SilverChariotEntity.attackTarget, run by every punch of the dash, a punch that struck
 * nothing included, deflected each projectile in reach flying at the Stand within its precision cone once the stored
 * Resolve level was 4 (canDeflectProjectiles), and attackOrDeflect deflected a projectile the punch struck. Each row runs
 * one forward tick of the dash's ray with the Stand facing south and its user looking up.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SilverChariotDashStrike116GameTests {
	private static final String BATCH = "silver_chariot_dash_strike_116";

	private SilverChariotDashStrike116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
	public static void theDashStrikesABoatOnItsPathAsIn116(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
			EntityActionInstance dash = dash(helper, scene, 4);
			StandEntity stand = scene.stand();
			Boat boat = EntityType.BOAT.create(helper.getLevel());
			helper.assertTrue(boat != null, "setup: could not create the boat");
			boat.setNoGravity(true);
			boat.moveTo(stand.getX(), stand.getEyeY() - boat.getBbHeight() / 2.0D, stand.getZ() + 2.0D, 0.0F, 0.0F);
			add(helper, scene, boat);
			dashTargets(dash, stand);
			helper.assertTrue((boat.isRemoved() || boat.getDamage() > 0.0F) && dash.punchedTarget != null
					&& dash.punchedTarget.getType() == ActionTarget.TargetType.ENTITY && dash.punchedTarget.getEntity() == boat,
					"1.16's dash punched a boat its ray met (StandEntity.attackTarget, attackEntity, hurt): the boat is removed "
							+ boat.isRemoved() + " with damage " + boat.getDamage() + ", the dash struck " + dash.punchedTarget);
			LivingComponentAction.getComponent(stand).setAction(null, scene.user(), SyncType.NO_SYNC);
		});
		helper.succeed();
	}

	// primed TNT takes no damage from a hit, and 1.16's knockbackTarget still set the dash's knockback as its motion
	@GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
	public static void theDashKnocksAsideAPrimedTntItStrikesAsIn116(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
			EntityActionInstance dash = dash(helper, scene, 4);
			StandEntity stand = scene.stand();
			PrimedTnt tnt = EntityType.TNT.create(helper.getLevel());
			helper.assertTrue(tnt != null, "setup: could not create the primed TNT");
			tnt.setFuse(200);
			tnt.setNoGravity(true);
			tnt.moveTo(stand.getX(), stand.getEyeY() - tnt.getBbHeight() / 2.0D, stand.getZ() + 2.0D, 0.0F, 0.0F);
			add(helper, scene, tnt);
			dashTargets(dash, stand);
			Vec3 motion = tnt.getDeltaMovement();
			boolean fast = stand.getAttackSpeed() >= 24;
			double sideways = Math.toDegrees(Math.acos(Mth.clamp(motion.z / Math.max(motion.horizontalDistance(), 1.0E-9D), -1.0D, 1.0D)));
			boolean pushed = fast
					? Math.abs(motion.y - 0.125D) < 1.0E-6D && motion.horizontalDistanceSqr() < 1.0E-12D
					: Math.abs(motion.horizontalDistance() - 0.75D) < 1.0E-6D && Math.abs(motion.y) < 1.0E-9D
							&& sideways >= 60.0D - 1.0E-3D && sideways <= 90.0D + 1.0E-3D;
			helper.assertTrue(!tnt.isRemoved() && pushed && dash.punchedTarget != null && dash.punchedTarget.getEntity() == tnt,
					"1.16's dash set its knockback as the motion of a primed TNT it struck (attack speed " + stand.getAttackSpeed()
							+ ": " + (fast ? "0.125 up" : "0.75 a tick, 60 to 90 degrees off the dash line") + "): it moves " + motion
							+ " (" + sideways + " degrees off the line), the dash struck " + dash.punchedTarget);
			LivingComponentAction.getComponent(stand).setAction(null, scene.user(), SyncType.NO_SYNC);
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
	public static void theDashDeflectsAnArrowFlyingAtItOnlyAtResolveFourAsIn116(GameTestHelper helper) {
		String[] rows = { "Resolve 4, nothing struck", "Resolve 4, a boat struck", "Resolve 3, nothing struck" };
		for (String row : rows) {
			StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
				int resolve = row.startsWith("Resolve 4") ? 4 : 3;
				EntityActionInstance dash = dash(helper, scene, resolve);
				StandEntity stand = scene.stand();
				helper.assertTrue(scene.power().getResolveLevel() == resolve && stand.isSilverChariotRapierVisible(),
						"setup: Resolve " + scene.power().getResolveLevel() + ", rapier out " + stand.isSilverChariotRapierVisible());
				Boat boat = null;
				if (row.endsWith("a boat struck")) {
					boat = EntityType.BOAT.create(helper.getLevel());
					helper.assertTrue(boat != null, "setup: could not create the boat");
					boat.setNoGravity(true);
					boat.moveTo(stand.getX() + 0.5D, stand.getEyeY() - boat.getBbHeight() / 2.0D, stand.getZ() + 2.0D, 0.0F, 0.0F);
					add(helper, scene, boat);
				}
				Arrow arrow = EntityType.ARROW.create(helper.getLevel());
				helper.assertTrue(arrow != null, "setup: could not create the arrow");
				arrow.setNoGravity(true);
				arrow.moveTo(stand.getX() - 1.0D, stand.getEyeY(), stand.getZ() + 2.0D, 180.0F, 0.0F);
				Vec3 flight = new Vec3(0.0D, 0.0D, -1.0D);
				arrow.setDeltaMovement(flight);
				add(helper, scene, arrow);
				dashTargets(dash, stand);
				boolean struckBoat = boat != null && dash.punchedTarget != null && dash.punchedTarget.getEntity() == boat;
				helper.assertTrue(boat == null ? dash.punchedTarget == null : struckBoat,
						"setup (" + row + "): the dash struck " + dash.punchedTarget);
				Vec3 motion = arrow.getDeltaMovement();
				boolean deflected = motion.z > 0.9D;
				helper.assertTrue(deflected == (resolve >= 4) && (deflected || motion.equals(flight)),
						"1.16's dash punch deflected a projectile flying at Silver Chariot in front of it only at Resolve 4"
								+ " (SilverChariotEntity.attackTarget, canDeflectProjectiles), " + row + ": the arrow flying north"
								+ " at the Stand now moves " + motion);
				LivingComponentAction.getComponent(stand).setAction(null, scene.user(), SyncType.NO_SYNC);
			});
		}
		helper.succeed();
	}

	/*
	 * Below attack speed 24 1.16's punchEntity took the side from the Stand's yaw after StandEntity.punch had turned it
	 * toward the target, at most attackSpeed / 16 * 18 degrees (15.75 at 14): a target within that turn went to the Stand's
	 * left (east, the Stand facing south), one beyond it to its own side, 60 to 90 degrees off the line to it.
	 */
	@GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
	public static void theDashPushesAStruckEntityToTheSide116TookFromTheTurnedStand(GameTestHelper helper) {
		// east of the dash line, ahead of the Stand, the side 1.16 pushed it to (1 east, -1 west)
		double[][] rows = { { 0.4D, 2.0D, 1.0D }, { -0.4D, 2.0D, 1.0D }, { 0.5D, 1.0D, 1.0D }, { -0.5D, 1.0D, -1.0D } };
		for (String kind : new String[] { "zombie", "primed TNT" }) {
			for (double[] row : rows) {
				StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
					EntityActionInstance dash = dash(helper, scene, 4);
					StandEntity stand = scene.stand();
					attackSpeed(helper, stand, 14.0D);
					Entity target;
					if (kind.equals("zombie")) {
						Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
						helper.assertTrue(zombie != null, "setup: could not create the zombie");
						zombie.setNoAi(true);
						zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0D);
						zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
						zombie.setHealth(100.0F);
						target = zombie;
					}
					else {
						PrimedTnt tnt = EntityType.TNT.create(helper.getLevel());
						helper.assertTrue(tnt != null, "setup: could not create the primed TNT");
						tnt.setFuse(200);
						target = tnt;
					}
					target.setNoGravity(true);
					target.moveTo(stand.getX() + row[0], stand.getEyeY() - target.getBbHeight() / 2.0D, stand.getZ() + row[1], 0.0F, 0.0F);
					add(helper, scene, target);
					Vec3 offset = target.position().subtract(stand.position());
					float yawToTarget = MathUtil.yRotDegFromVec(offset);
					Vec3 toTarget = offset.multiply(1.0D, 0.0D, 1.0D).normalize();
					List<Push> pushes = pushes(target, () -> dashTargets(dash, stand));
					String where = kind + " " + row[0] + " east and " + row[1] + " ahead";
					helper.assertTrue(dash.punchedTarget != null && dash.punchedTarget.getEntity() == target,
							"setup (" + where + "): the dash struck " + dash.punchedTarget);
					boolean withinTurn = Math.abs(row[0]) < 0.45D;
					float turnedOff = Mth.wrapDegrees(stand.getYRot() - yawToTarget);
					helper.assertTrue(withinTurn ? turnedOff == 0.0F : Math.abs(Math.abs(stand.getYRot()) - 15.75F) < 1.0E-3F,
							"setup (" + where + "): the strike turned the Stand to yaw " + stand.getYRot() + ", " + turnedOff
									+ " degrees from the target");
					List<Vec3> sides = new ArrayList<>();
					if (target instanceof LivingEntity) {
						for (Push push : pushes) {
							if (Math.abs(push.strength() - 0.75D) < 1.0E-6D) {
								sides.add(push.direction());
							}
						}
					}
					else {
						// Vec3.yRot's table sine and cosine may sit one step apart for a negative angle, as 1.16's did
						Vec3 motion = target.getDeltaMovement();
						helper.assertTrue(Math.abs(motion.horizontalDistance() - 0.75D) < 1.0E-4D && Math.abs(motion.y) < 1.0E-9D,
								"1.16's dash set 0.75 a tick aside as the motion of the " + where + ": it moves " + motion);
						sides.add(motion.normalize());
					}
					helper.assertTrue(!sides.isEmpty(), "1.16's dash pushed the " + where + " aside 0.75: pushes " + pushes);
					for (Vec3 side : sides) {
						double east = toTarget.z * side.x - toTarget.x * side.z;
						double off = Math.toDegrees(Math.acos(Mth.clamp(toTarget.dot(side), -1.0D, 1.0D)));
						helper.assertTrue(Math.signum(east) == row[2] && off >= 60.0D - 1.0E-3D && off <= 90.0D + 1.0E-3D,
								"1.16's dash pushed the " + where + " " + (row[2] > 0 ? "east" : "west") + " of the line to it, 60 to 90"
										+ " degrees off it (the side from the Stand's yaw after the strike's turn, "
										+ (withinTurn ? "within the turn: the Stand's left" : "beyond it: its own side") + "): it went "
										+ (east > 0 ? "east" : "west") + ", " + off + " degrees off, " + side);
					}
					LivingComponentAction.getComponent(stand).setAction(null, scene.user(), SyncType.NO_SYNC);
				});
			}
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandEntityPunch.doAttack pushed a hurt living target once (the dash's addKnockback 1.5: 0.75 aside), then
	 * knockbackTarget pushed every target the strike admitted once more, hurt or not, both along the yaw punchEntity drew
	 * once; LivingEntity.knockback halves the motion it finds, so a hurt cow ends 1.125 aside. Its Stand invulnerability
	 * then gave it no damage on the later forward ticks, so it was not struck again.
	 */
	@GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
	public static void aHurtLivingTargetIsPushedTwiceAlongOneSideAsIn116(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
			EntityActionInstance dash = dash(helper, scene, 4);
			StandEntity stand = scene.stand();
			attackSpeed(helper, stand, 14.0D);
			Cow cow = cow(helper, scene, stand);
			List<Push> pushes = pushes(cow, () -> dashTargets(dash, stand));
			List<Push> aside = aside(pushes);
			helper.assertTrue(cow.getHealth() < cow.getMaxHealth() && dash.punchedTarget != null && dash.punchedTarget.getEntity() == cow,
					"setup: the dash did not hurt the cow: health " + cow.getHealth() + ", struck " + dash.punchedTarget);
			helper.assertTrue(aside.size() == 2 && aside.get(0).direction().dot(aside.get(1).direction()) > 1.0D - 1.0E-9D,
					"1.16's dash pushed a cow it hurt 0.75 aside twice along one yaw (doAttack's push of a hurt target, then"
							+ " knockbackTarget's): pushes " + pushes);
			Vec3 side = aside.get(0).direction();
			Vec3 expected = aside.get(0).motionBefore().scale(0.25D).add(side.scale(1.125D));
			Vec3 motion = cow.getDeltaMovement();
			helper.assertTrue(motion.distanceTo(expected) < 1.0E-6D,
					"1.16's two pushes left the hurt cow 1.125 aside (" + motion.dot(side) + " along the push): it moves " + motion
							+ ", expected " + expected);
			List<Push> later = pushes(cow, () -> dashTargets(dash, stand));
			helper.assertTrue(aside(later).isEmpty(),
					"1.16's Stand invulnerability spared the hurt cow on the dash's next forward tick: pushes " + later);
			LivingComponentAction.getComponent(stand).setAction(null, scene.user(), SyncType.NO_SYNC);
		});
		helper.succeed();
	}

	// a raised shield: not hurt, so knockbackTarget's push only, the shield worn, and struck again on the next forward tick
	@GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
	public static void aPlayerBlockingTheDashIsPushedAsideOnEveryForwardTickAsIn116(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
			EntityActionInstance dash = dash(helper, scene, 4);
			StandEntity stand = scene.stand();
			attackSpeed(helper, stand, 14.0D);
			Blocker blocker = blocker(helper, scene, stand);
			ItemStack shield = blocker.getUseItem();
			for (int tick = 1; tick <= 2; tick++) {
				float health = blocker.getHealth();
				int worn = shield.getDamageValue();
				List<Push> pushes = pushes(blocker, () -> dashTargets(dash, stand));
				List<Push> aside = aside(pushes);
				helper.assertTrue(blocker.getHealth() == health && blocker.isBlocking() && dash.punchedTarget != null
						&& dash.punchedTarget.getEntity() == blocker,
						"setup, forward tick " + tick + ": the shield did not block the dash: health " + blocker.getHealth()
								+ ", blocking " + blocker.isBlocking() + ", struck " + dash.punchedTarget);
				helper.assertTrue(aside.size() == 1,
						"1.16's knockbackTarget pushed a player whose shield blocked the dash 0.75 aside once, forward tick "
								+ tick + ": pushes " + pushes);
				Vec3 expected = aside.get(0).motionBefore().scale(0.5D).add(aside.get(0).direction().scale(0.75D));
				helper.assertTrue(blocker.getDeltaMovement().distanceTo(expected) < 1.0E-6D,
						"1.16's push moved the blocking player 0.75 aside, forward tick " + tick + ": it moves "
								+ blocker.getDeltaMovement() + ", expected " + expected);
				helper.assertTrue(tick > 1 || shield.getDamageValue() > worn,
						"1.16's blocked strike wore the shield: damage " + worn + " -> " + shield.getDamageValue());
			}
			LivingComponentAction.getComponent(stand).setAction(null, scene.user(), SyncType.NO_SYNC);
		});
		helper.succeed();
	}

	// from attack speed 24 the dash's punch had addKnockback 0.25 at pitch -90: each push a 0.125 lift
	@GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
	public static void theFastDashLiftsAHurtTargetTwiceAndABlockingOneOnceAsIn116(GameTestHelper helper) {
		for (String row : new String[] { "hurt cow", "blocking player" }) {
			StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
				EntityActionInstance dash = dash(helper, scene, 4);
				StandEntity stand = scene.stand();
				attackSpeed(helper, stand, 30.0D);
				LivingEntity target = row.equals("hurt cow") ? cow(helper, scene, stand) : blocker(helper, scene, stand);
				float health = target.getHealth();
				dashTargets(dash, stand);
				boolean hurt = target.getHealth() < health;
				helper.assertTrue(hurt == row.equals("hurt cow") && dash.punchedTarget != null && dash.punchedTarget.getEntity() == target,
						"setup (" + row + "): hurt " + hurt + ", struck " + dash.punchedTarget);
				double lift = hurt ? 0.25D : 0.125D;
				helper.assertTrue(Math.abs(target.getDeltaMovement().y - lift) < 1.0E-6D,
						"1.16's fast dash lifted the " + row + " " + lift + " (0.125 a push): it moves " + target.getDeltaMovement());
				// 1.16 DamageUtil.upwardsKnockback did not mark it, so a blocking player's own client was not sent the lift
				helper.assertTrue(hurt || !target.hurtMarked, "1.16's lift of a player whose shield blocked the fast dash was not"
						+ " marked for its client: hurtMarked " + target.hurtMarked);
				LivingComponentAction.getComponent(stand).setAction(null, scene.user(), SyncType.NO_SYNC);
			});
		}
		helper.succeed();
	}

	private static Cow cow(GameTestHelper helper, Scene scene, StandEntity stand) {
		Cow cow = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(cow != null, "setup: could not create the cow");
		cow.setNoAi(true);
		cow.setNoGravity(true);
		cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0D);
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
		cow.setHealth(100.0F);
		cow.moveTo(stand.getX(), stand.getEyeY() - cow.getBbHeight() / 2.0D, stand.getZ() + 2.0D, 0.0F, 0.0F);
		add(helper, scene, cow);
		return cow;
	}

	// a survival player 2 blocks south of the Stand, facing it with a raised shield
	private static Blocker blocker(GameTestHelper helper, Scene scene, StandEntity stand) {
		Blocker blocker = new Blocker(helper.getLevel());
		blocker.setNoGravity(true);
		blocker.moveTo(stand.getX(), stand.getEyeY() - blocker.getBbHeight() / 2.0D, stand.getZ() + 2.0D, 180.0F, 0.0F);
		blocker.setYHeadRot(180.0F);
		blocker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		add(helper, scene, blocker);
		blocker.raiseShield(helper);
		return blocker;
	}

	private static List<Push> aside(List<Push> pushes) {
		return pushes.stream().filter(push -> Math.abs(push.strength() - 0.75D) < 1.0E-6D).toList();
	}

	private static final class Blocker extends Player {
		Blocker(Level level) {
			super(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "dash-blocker"));
		}

		@Override
		public boolean isSpectator() {
			return false;
		}

		@Override
		public boolean isCreative() {
			return false;
		}

		// 8 item use steps as LivingEntity.tick runs them; vanilla isBlocking needs 5
		void raiseShield(GameTestHelper helper) {
			startUsingItem(InteractionHand.OFF_HAND);
			for (int i = 0; i < 8 && isUsingItem(); i++) {
				updateUsingItem(getUseItem());
			}
			helper.assertTrue(isBlocking(), "setup: the player's shield is not raised");
		}
	}

	private record Push(double strength, Vec3 direction, Vec3 motionBefore) {}

	// the pushes LivingEntity.knockback is asked for on the target while the run lasts, each with the motion it found
	private static List<Push> pushes(Entity target, Runnable run) {
		List<Push> pushes = new ArrayList<>();
		Consumer<LivingKnockBackEvent> listener = event -> {
			if (event.getEntity() == target) {
				pushes.add(new Push(event.getOriginalStrength(),
						new Vec3(-event.getOriginalRatioX(), 0.0D, -event.getOriginalRatioZ()).normalize(), target.getDeltaMovement()));
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingKnockBackEvent.class, listener);
		try {
			run.run();
		}
		finally {
			NeoForge.EVENT_BUS.unregister(listener);
		}
		return pushes;
	}

	private static void attackSpeed(GameTestHelper helper, StandEntity stand, double speed) {
		AttributeInstance attribute = stand.getAttribute(Attributes.ATTACK_SPEED);
		for (int i = 0; i < 4 && Math.abs(stand.getAttackSpeed() - speed) > 1.0E-6D; i++) {
			attribute.setBaseValue(attribute.getBaseValue() * speed / stand.getAttackSpeed());
		}
		helper.assertTrue(Math.abs(stand.getAttackSpeed() - speed) < 1.0E-4D,
				"setup: the Stand's attack speed is " + stand.getAttackSpeed() + ", not " + speed);
	}

	// the dash set on the following Silver Chariot, turned south, its user looking straight up so its aim meets nothing
	private static EntityActionInstance dash(GameTestHelper helper, Scene scene, int resolve) {
		StandEntity stand = scene.stand();
		Player user = scene.user();
		StandPower power = scene.power();
		power.setResolveLevel(resolve);
		power.setStamina(power.getMaxStamina());
		stand.summonLockTicks = 0;
		stand.gradualSummonWeaknessTicks = 0;
		stand.updatePosition(user);
		Ability ability = power.getAbility("dash_attack");
		if (!(ability instanceof EntityActionType)) {
			power.getCurTypeData()._setSkillUnlocked("dash_attack", true, false);
			ability = power.getAbility("dash_attack");
		}
		helper.assertTrue(ability instanceof EntityActionType, "Missing registered dash_attack");
		EntityActionType type = (EntityActionType) ability;
		EntityActionInstance dash = type.createActionObj();
		type.initActionFromConfig(dash, helper.getLevel(), user, stand);
		dash.setStartingPhase();
		LivingComponentAction.getComponent(stand).setAction(dash, user, SyncType.NO_SYNC);
		helper.assertTrue(stand.getCurStandAction() == dash && dash instanceof DashStrike,
				"setup: the dash is not the Silver Chariot's action");
		stand.setYRot(0.0F);
		stand.setXRot(0.0F);
		stand.setYHeadRot(0.0F);
		user.setXRot(-90.0F);
		dash.punchedTarget = null;
		return dash;
	}

	private static void add(GameTestHelper helper, Scene scene, Entity entity) {
		helper.assertTrue(helper.getLevel().addFreshEntity(entity), "setup: could not add " + entity);
		scene.spawned().add(entity);
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
