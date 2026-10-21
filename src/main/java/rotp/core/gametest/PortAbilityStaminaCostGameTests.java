package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.entity_projectile.ThrownNuggetBearingEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The port's own Stand moves, which 1.16 did not have (the grab, the grab throw, the charged heavy punch, the Bearing
 * Shot), take their stamina by their own code as every ability does. Ability#getStaminaCost(Power) reports what a use of
 * each takes (a charged or held one: what it takes when it goes off), as the core's 1.16 moves report 1.16's costs
 * (AbilityStaminaCost116GameTests), so a Stand-bearing mob's pre-check (1.16 rotp_mwp StandAI.isActionAvailable:
 * getStaminaCost no more than the stamina) leaves them out on a bar that cannot pay them, as it does the rest. The grab
 * release takes nothing and reports nothing.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PortAbilityStaminaCostGameTests {
	private static final short KEY = 23;
	private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
			JojoMod.resLoc(PortAbilityStaminaCostGameTests.class.getSimpleName().toLowerCase(Locale.ROOT)));

	private PortAbilityStaminaCostGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void thePortsOwnStandMovesReportTheStaminaAUseOfThemTakes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		helper.assertTrue(type != null, "setup: no Star Platinum");
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		AABB area = new AABB(origin, origin).inflate(32.0D);
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		user.setNoGravity(true);
		// user and Stand look at the sky, where the charged heavy punch and the shot meet nothing
		user.moveTo(origin.x, origin.y, origin.z, 0.0F, -90.0F);
		helper.assertTrue(level.addFreshEntity(user), "setup: could not add the Stand user");
		StandPower power = PowerClass.STAND.attachGet(user);
		StandEntity stand = null;
		Cow cow = null;
		List<String> wrong = new ArrayList<>();
		try {
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(user, power), "setup: could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "setup: the summoned Stand has no user");
			// only StandEntity.tick, which the test does not run, counts these down
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.setCanFollowUser(false);
			stand.setNoGravity(true);
			stand.moveTo(origin.x, origin.y, origin.z, 0.0F, -90.0F);
			helper.assertTrue(power.usesStamina() && !power.isStaminaInfinite() && power.getMaxStamina() > 100.0F,
					"setup: the user's stamina is not finite or too small: " + power.getMaxStamina());
			PowerSkillUnlocks.Result unlocked = UNLOCKS.forceUnlock(power, "grab_throw");
			helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED || unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
					"setup: could not unlock the grab throw: " + unlocked);
			LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
			LivingComponentGrab standGrab = stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get());

			EntityActionInstance charged = use(helper, user, power, standAction, "heavy_charged", ActionPhase.WINDUP, wrong);
			helper.assertTrue(charged != null && charged.punchedTarget != null, "setup: the charged heavy punch did not go off");

			stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_NUGGET, 2));
			int bearings = bearings(level, area).size();
			use(helper, user, power, standAction, "bearing_shot", ActionPhase.PERFORM, wrong);
			helper.assertTrue(bearings(level, area).size() == bearings + 1, "setup: the Bearing Shot shot no bearing");
			bearings(level, area).forEach(ThrownNuggetBearingEntity::discard);
			stand.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

			cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "setup: could not create the cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(origin.x, origin.y, origin.z + 1.2D, 180.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(cow), "setup: could not add the cow");
			Vec3 cowCenter = cow.getBoundingBox().getCenter();
			user.lookAt(EntityAnchorArgument.Anchor.EYES, cowCenter);
			user.setYHeadRot(user.getYRot());
			stand.lookAt(EntityAnchorArgument.Anchor.EYES, cowCenter);
			stand.setYHeadRot(stand.getYRot());
			LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(cow));
			standAction.entityAim.setTarget(new ActionTarget(cow));

			use(helper, user, power, standAction, "grab", null, wrong);
			helper.assertTrue(standGrab.getGrabbedEntity() == cow, "setup: the grab did not take the cow: "
					+ standGrab.getGrabbedEntity());
			use(helper, user, power, standAction, "grab_release", null, wrong);
			helper.assertTrue(standGrab.getGrabbedEntity() == null, "setup: the grab release did not let the cow go");

			standGrab.setGrabTarget(cow);
			helper.assertTrue(standGrab.getGrabbedEntity() == cow, "setup: could not grab the cow again");
			use(helper, user, power, standAction, "grab_throw", ActionPhase.WINDUP, wrong);
			helper.assertTrue(standGrab.getGrabbedEntity() == null && cow.getDeltaMovement().lengthSqr() > 1.0D,
					"setup: the grab throw did not throw the cow: " + cow.getDeltaMovement());
		}
		finally {
			AbilityInput.keyRelease(KEY, user);
			if (stand != null) {
				stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null);
				LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			}
			bearings(level, area).forEach(ThrownNuggetBearingEntity::discard);
			if (cow != null) {
				cow.discard();
			}
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
		helper.assertTrue(wrong.isEmpty(), "the port's own Stand moves report the stamina a use of them takes, as the"
				+ " 1.16 moves report 1.16's, for a Stand-bearing mob's pre-check (1.16 StandAI.isActionAvailable): "
				+ String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * Presses the ability by a key as the server takes a player's press (its conditions first, as a tick computes them:
	 * the test runs in one tick, past the power's per-tick cache), lets go of the key at once for a click and as a held one
	 * reaches heldPhase, runs its action out, and notes it when the stamina the use took is not what the ability reports.
	 */
	@Nullable
	private static EntityActionInstance use(GameTestHelper helper, Player user, StandPower power,
			LivingComponentAction standAction, String name, @Nullable ActionPhase heldPhase, List<String> wrong) {
		Ability ability = power.getAbility(name);
		helper.assertTrue(ability != null, "setup: Star Platinum has no " + name);
		InputMethod method = heldPhase != null ? InputMethod.HOLD : InputMethod.CLICK;
		AvailableAbilities available = new AvailableAbilities();
		available.update(power, power.getMoveset());
		var selected = available.getContextVariationContainer(ability);
		helper.assertTrue(selected != null && selected.ability == ability && AbilityInput.withConditionCheck(selected, user, method),
				"setup: " + name + " cannot be pressed: " + (selected != null ? describe(selected.conditionCheck) : "not in the moveset"));
		power.setStamina(power.getMaxStamina());
		float before = power.getStamina();
		BufferingState pressed = BufferingState.clickOnly();
		AbilityInput.keyPress(KEY, ability, user, null, method, 0, pressed, ability.getAbilityId());
		helper.assertTrue(pressed.isActionSuccess, "setup: the press of " + name + " did not go through");
		if (heldPhase == null) {
			AbilityInput.keyRelease(KEY, user);
		}
		EntityActionInstance action = null;
		if (ability instanceof EntityActionType) {
			action = standAction.getAction();
			helper.assertTrue(action != null && action.ability == ability, "setup: the press of " + name
					+ " started no action of it: " + action);
			for (int tick = 0; tick < 200 && !action.isOver(); tick++) {
				if (heldPhase != null && action.getPhase() == heldPhase && AbilityInput.isHeldByKey(user, action)) {
					AbilityInput.keyRelease(KEY, user);
				}
				else {
					standAction.tick();
				}
			}
			helper.assertTrue(action.isOver(), "setup: " + name + " did not run out: phase " + action.getPhase()
					+ " tick " + action.getPhaseTick());
		}
		AbilityInput.keyRelease(KEY, user);
		float took = before - power.getStamina();
		float reported = ability.getStaminaCost(power);
		if (Math.abs(took - reported) > 1.0E-3F) {
			wrong.add(name + " reports " + reported + ", a use of it took " + took);
		}
		return action;
	}

	private static String describe(ConditionCheck check) {
		return check.isPositive() ? "positive" : check.getWarning() != null ? check.getWarning().getString() : "negative";
	}

	private static List<ThrownNuggetBearingEntity> bearings(ServerLevel level, AABB area) {
		return level.getEntitiesOfClass(ThrownNuggetBearingEntity.class, area);
	}
}
