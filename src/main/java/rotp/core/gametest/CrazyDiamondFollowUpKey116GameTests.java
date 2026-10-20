package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * 1.16 ModStandsInit :641-662: Crazy Diamond's leave-object follow-up belonged to the base heavy punch
 * (CRAZY_DIAMOND_HEAVY_PUNCH.attackRecoveryFollowup(CRAZY_DIAMOND_LEAVE_OBJECT)) and the three misshape follow-ups to its
 * finisher variation (CRAZY_DIAMOND_FINISHER_PUNCH); StandAction.replaceActionKostyl gave a follow-up only while its own
 * action was the Stand's task and the action the heavy key resolved to (StandEntityHeavyAttack.getFinisherVariationIfPresent:
 * the finisher variation from a finisher meter of 0.5, StandEntity.willHeavyPunchBeFinisher).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrazyDiamondFollowUpKey116GameTests {
	private static final short KEY = 24;

	private CrazyDiamondFollowUpKey116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void leaveObjectFollowsTheBaseHeavyPunchAndMisshapingTheFinisherAsIn116(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
		helper.assertTrue(standType != null, "Missing Crazy Diamond Stand type");
		Player user = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CrazyDiamondFollowUp"));
		Player punched = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CrazyDiamondFollowUpTarget"));
		StandPower power = null;
		try {
			user.getAbilities().instabuild = false;
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Crazy Diamond player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Crazy Diamond");
			power.setResolveLevel(4);
			for (String name : new String[] { "heavy_punch", "finisher", "leave_object", "disfiguring_punch" }) {
				power.getCurTypeData()._lockedAbilities.remove(name);
			}
			Ability heavy = power.getAbility("heavy_punch");
			Ability finisher = power.getAbility("finisher");
			Ability leaveObject = power.getAbility("leave_object");
			Ability misshaping = power.getAbility("disfiguring_punch");
			helper.assertTrue(heavy != null && finisher != null && leaveObject != null && misshaping != null
					&& finisher.isAbilityUnlocked(power) && leaveObject.isAbilityUnlocked(power) && misshaping.isAbilityUnlocked(power),
					"fixture: Crazy Diamond's heavy punch, finisher, leave object or misshaping punch is missing or locked");
			helper.assertTrue(standType.summon(user, power), "Could not summon Crazy Diamond");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Crazy Diamond is missing");
			stand.summonLockTicks = 0;

			// a player: 1.16 offered the misshape follow-ups only at a player or a player's Stand
			punched.moveTo(userPos.x, userPos.y, userPos.z + 1.5D, 180.0F, 0.0F);
			punched.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(punched), "Could not add the punched player");
			LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(punched));
			LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(punched));

			StandInstance instance = power.getStandInstance().orElseThrow();
			row(helper, power, user, stand, heavy, 0.0F, true, heavy, leaveObject, null,
					"the base heavy punch (finisher meter 0) with a chorus fruit in the off hand");
			row(helper, power, user, stand, heavy, 0.6F, true, finisher, misshaping, null,
					"the finisher (meter 0.6) with a chorus fruit in the off hand");
			row(helper, power, user, stand, heavy, 1.0F, false, finisher, misshaping, null,
					"the finisher (meter 1) with an empty off hand");
			// willHeavyPunchBeFinisher: the heavy key resolves to the finisher, which has no leave-object follow-up
			row(helper, power, user, stand, heavy, 0.0F, true, heavy, finisher, () -> stand.setFinisherMeter(0.6F),
					"the base heavy punch, the meter raised to 0.6 while it runs, with a chorus fruit in the off hand");
			// getFinisherVariationIfPresent: the finisher needs the arms, so the key resolves to the base heavy punch
			try {
				row(helper, power, user, stand, heavy, 1.0F, false, finisher, heavy, () -> instance.removePart(StandPart.ARMS),
						"the finisher (meter 1) once Crazy Diamond lost its arms, with an empty off hand");
			}
			finally {
				instance.addPart(StandPart.ARMS);
			}
			power.getCurTypeData()._lockedAbilities.add("finisher");
			helper.assertTrue(!finisher.isAbilityUnlocked(power), "fixture: the finisher is still unlocked");
			row(helper, power, user, stand, heavy, 1.0F, false, heavy, heavy, null,
					"the base heavy punch at a full meter (the finisher locked) with an empty off hand");
			helper.succeed();
		}
		finally {
			if (power != null) {
				power.getCurTypeData()._lockedAbilities.remove("finisher");
				if (power.isSummoned()) {
					standType.forceUnsummon(user, power);
				}
			}
			punched.discard();
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			user.discard();
		}
	}

	/*
	 * 1.16 CrazyDiamondMisshapeBodyPart.checkSpecificConditions took only a living target whose Stand user is a player (the
	 * mob effects of misshaping were a TODO), a player or a player's Stand: StandAction.canSetFollowUp ran it on the
	 * finisher's task target to put a misshape follow-up on the heavy key, and the click ran it again on the crosshair's
	 * target (PowerBaseImpl.clickAction with the mouse target), which refused it without a message.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void misshapingFollowsOnlyAFinisherAtAPlayerOrAPlayersStandAsIn116(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
		StandType enemyType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		helper.assertTrue(standType != null && enemyType != null, "Missing the Crazy Diamond or Star Platinum Stand type");
		Player user = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CrazyDiamondMisshapeTargets"));
		Player enemy = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CrazyDiamondMisshapeEnemy"));
		StandPower power = null;
		StandPower enemyPower = null;
		Cow cow = null;
		try {
			user.getAbilities().instabuild = false;
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			Vec3 front = userPos.add(0.0D, 0.0D, 1.5D);
			user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Crazy Diamond player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Crazy Diamond");
			power.setResolveLevel(4);
			for (String name : new String[] { "heavy_punch", "finisher", "disfiguring_punch" }) {
				power.getCurTypeData()._lockedAbilities.remove(name);
			}
			Ability heavy = power.getAbility("heavy_punch");
			Ability finisher = power.getAbility("finisher");
			Ability misshaping = power.getAbility("disfiguring_punch");
			helper.assertTrue(heavy != null && finisher != null && misshaping != null && finisher.isAbilityUnlocked(power)
					&& misshaping.isAbilityUnlocked(power), "fixture: Crazy Diamond's heavy punch, finisher or misshaping punch is"
							+ " missing or locked");
			helper.assertTrue(standType.summon(user, power), "Could not summon Crazy Diamond");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Crazy Diamond is missing");
			stand.summonLockTicks = 0;

			enemy.moveTo(front.x, front.y, front.z, 180.0F, 0.0F);
			enemy.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(enemy), "Could not add the other player");
			cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "Could not create the cow");
			cow.moveTo(userPos.x + 1.5D, userPos.y, userPos.z + 1.5D, 180.0F, 0.0F);
			cow.setNoAi(true);
			cow.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");

			misshapeRow(helper, power, user, stand, heavy, finisher, misshaping, enemy, enemy, true,
					"a finisher at a player, the crosshair on the player");
			misshapeRow(helper, power, user, stand, heavy, finisher, misshaping, cow, cow, false,
					"a finisher at a cow, the crosshair on the cow");
			misshapeRow(helper, power, user, stand, heavy, finisher, misshaping, enemy, cow, true,
					"a finisher at a player, the crosshair moved to a cow");

			// the other player 4 blocks away, its Star Platinum where it stood
			enemy.moveTo(userPos.x + 2.0D, userPos.y, userPos.z + 4.0D, 180.0F, 0.0F);
			enemyPower = PowerClass.STAND.attachGet(enemy);
			helper.assertTrue(StandPowerTransitions.insert(enemyPower, new StandInstance(enemyType)).status()
					== StandPowerTransitions.Status.APPLIED && enemyType.summon(enemy, enemyPower), "Could not give and summon Star"
							+ " Platinum");
			StandEntity enemyStand = enemyPower.getSummonedStandEntity();
			helper.assertTrue(enemyStand != null, "Summoned Star Platinum is missing");
			enemyStand.moveTo(front.x, front.y, front.z, 180.0F, 0.0F);
			misshapeRow(helper, power, user, stand, heavy, finisher, misshaping, enemyStand, enemyStand, true,
					"a finisher at another player's Stand, the crosshair on the Stand");
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned()) {
				standType.forceUnsummon(user, power);
			}
			if (enemyPower != null && enemyPower.isSummoned()) {
				enemyType.forceUnsummon(enemy, enemyPower);
			}
			if (cow != null) {
				cow.discard();
			}
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			user.discard();
			enemy.discard();
		}
	}

	// the finisher pressed at the target with a full meter, the crosshair then on the other entity: whether the key gives
	// the misshaping punch and whether a click takes it (1.16: the crosshair on a player or a player's Stand)
	private static void misshapeRow(GameTestHelper helper, StandPower power, Player user, StandEntity stand, Ability heavy,
			Ability finisher, Ability misshaping, LivingEntity target, LivingEntity crosshair, boolean followUp, String row) {
		LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
		standAction.setAction(null, user, SyncType.NO_SYNC);
		user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		power.setStamina(power.getMaxStamina());
		stand.setFinisherMeter(1.0F);
		LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(target));
		standAction.entityAim.setTarget(new ActionTarget(target));
		AbilityConditionCheck pressed = press(power, user, heavy);
		EntityActionInstance action = standAction.getAction();
		helper.assertTrue(pressed != null && pressed.ability == finisher && action instanceof StandEntityHeavyPunch punch
				&& action.ability == finisher && punch.getTaskTarget(stand.level()).getEntity() == target, "fixture (" + row
						+ "): the key did not start the finisher at " + target + ": " + describe(pressed) + ", action " + action);
		LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(crosshair));
		AbilityConditionCheck resolved = resolve(power, heavy);
		boolean usable = followUp && crosshair == target;
		helper.assertTrue(resolved != null && (followUp ? resolved.ability == misshaping
				&& resolved.conditionCheck.isPositive() == usable : resolved.ability == finisher), "1.16 gave " + row
						+ (followUp ? " the misshape follow-up on the heavy key, " + (usable ? "usable" : "refused at the click")
								: " the heavy key as the running finisher, no misshape follow-up") + ": it resolves to "
						+ describe(resolved));
		standAction.setAction(null, user, SyncType.NO_SYNC);
	}

	// the heavy key pressed at the meter, then what it resolves to while that punch runs (after whileRunning)
	private static void row(GameTestHelper helper, StandPower power, Player user, StandEntity stand, Ability heavy,
			float meter, boolean offhandItem, Ability running, Ability expected, @Nullable Runnable whileRunning, String row) {
		LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
		standAction.setAction(null, user, SyncType.NO_SYNC);
		user.setItemInHand(InteractionHand.OFF_HAND, offhandItem ? new ItemStack(Items.CHORUS_FRUIT, 8) : ItemStack.EMPTY);
		power.setStamina(power.getMaxStamina());
		stand.setFinisherMeter(meter);
		AbilityConditionCheck pressed = press(power, user, heavy);
		EntityActionInstance action = standAction.getAction();
		helper.assertTrue(pressed != null && pressed.ability == running && action instanceof StandEntityHeavyPunch punch
				&& action.ability == running && punch.getPunchModifiers().isEmpty()
				&& user.getOffhandItem().is(Items.CHORUS_FRUIT) == offhandItem, "fixture (" + row + "): the key did not start "
						+ running.name() + ": " + describe(pressed) + ", action " + action + ", off hand " + user.getOffhandItem());
		if (whileRunning != null) {
			whileRunning.run();
			helper.assertTrue(standAction.getAction() == action && !action.isOver(), "fixture (" + row + "): the punch ended");
		}
		AbilityConditionCheck resolved = resolve(power, heavy);
		boolean followUp = expected.name().equals("leave_object") || expected.name().equals("disfiguring_punch");
		helper.assertTrue(resolved != null && resolved.ability == expected && (!followUp || resolved.conditionCheck.isPositive()),
				"1.16 gave " + row + " the heavy key " + (followUp ? expected.name() + ", its follow-up" : "as " + expected.name()
						+ ", no follow-up") + ": it resolves to " + describe(resolved));
		standAction.setAction(null, user, SyncType.NO_SYNC);
	}

	// as ClAbilityInputPacket handles a click: the key's resolved ability, its condition check, then the press
	private static AbilityConditionCheck press(StandPower power, Player user, Ability base) {
		AbilityConditionCheck container = resolve(power, base);
		if (container != null && AbilityInput.withConditionCheck(container, user, InputMethod.CLICK)) {
			AbilityInput.keyPress(KEY, container.ability, user, null, InputMethod.CLICK, 0,
					BufferingState.clickCanBuffer(), base.getAbilityId());
			AbilityInput.keyRelease(KEY, user);
		}
		return container;
	}

	// the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
	private static AbilityConditionCheck resolve(StandPower power, Ability base) {
		try {
			Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
			cached.setAccessible(true);
			cached.setBoolean(power, false);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
		}
		return power.updateAvailableMoves().getContextVariationContainer(base);
	}

	private static String describe(AbilityConditionCheck container) {
		return container == null ? "nothing" : container.ability.name() + " (" + (container.conditionCheck.isPositive()
				? "usable" : "refused") + ")";
	}
}
