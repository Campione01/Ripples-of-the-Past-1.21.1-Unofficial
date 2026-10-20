package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.goldexperience.GoldExperienceHeavyPunchAbility.ToothKnockingHeavyPunch;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.Moveset;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
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
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GoldExperienceLifeshotPunch.attackRecoveryFollowup(GOLD_EXPERIENCE_ENTITY_LIFESHOT): while the Life Shot punch was
 * the Stand's task, the heavy key gave its Life Shot, as StandEntityHeavyAttack.replaceAction asked the running finisher
 * variation's replaceAction (getFinisherVariationIfPresent) and StandAction.replaceActionKostyl gave the variation's
 * follow-up until the task had it. That held for whichever heavy attack the punch was the finisher variation of: Gold
 * Experience's own and Gold Experience Requiem's ger_stand_heavy_punch (1.16 GER InitStands GER_STAND_HEAVY_PUNCH
 * setFinisherVariation(GOLD_EXPERIENCE_LIFESHOT_PUNCH)).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifeshotFollowUpKeyGameTests {
	private static final short KEY = 22;
	private static final String OTHER_HEAVY = "lifeshot_test_heavy_punch";

	private LifeshotFollowUpKeyGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theHeavyKeyGivesTheRunningLifeShotPunchsLifeShotWhicheverHeavyItFinishesAsIn116(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
		helper.assertTrue(standType != null, "Missing Gold Experience Stand type");
		Player user = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "LifeShotFollowUp"));
		StandPower power = null;
		Cow cow = null;
		try {
			user.getAbilities().instabuild = false;
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Life Shot player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Gold Experience");
			power.setResolveLevel(4);
			for (String name : new String[] { "heavy_punch", "lifeshot", "lifeshot_punch" }) {
				power.getCurTypeData()._lockedAbilities.remove(name);
			}
			Ability heavy = power.getAbility("heavy_punch");
			Ability lifeshot = power.getAbility("lifeshot");
			Ability lifeshotPunch = power.getAbility("lifeshot_punch");
			helper.assertTrue(heavy != null && lifeshot != null && lifeshotPunch != null
					&& lifeshot.isAbilityUnlocked(power) && lifeshotPunch.isAbilityUnlocked(power),
					"fixture: Gold Experience's heavy punch, Life Shot or Life Shot punch is missing or locked");
			helper.assertTrue(standType.summon(user, power), "Could not summon Gold Experience");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Gold Experience is missing");
			stand.summonLockTicks = 0;

			cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "Could not create the cow");
			cow.moveTo(userPos.x, userPos.y, userPos.z + 1.5D, 180.0F, 0.0F);
			cow.setNoAi(true);
			cow.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");
			LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(cow));
			LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(cow));

			followUpRow(helper, power, user, stand, heavy, lifeshot, lifeshotPunch, "Gold Experience's heavy punch");

			// Gold Experience Requiem's moveset: the core Life Shot punch is the finisher variation of a heavy attack
			// under another name
			Moveset moveset = power.getMoveset();
			Ability otherHeavy = ModStandAbilities.HEAVY_PUNCH.get().createInstance(
					new AbilityId(PowerClass.STAND, standType.getId(), OTHER_HEAVY));
			moveset.abilities.put(OTHER_HEAVY, otherHeavy);
			lifeshotPunch.initIsFinisher(OTHER_HEAVY);
			helper.assertTrue(power.getMoveset() == moveset && power.getAbility(OTHER_HEAVY) == otherHeavy
					&& otherHeavy.isAbilityUnlocked(power), "fixture: the heavy attack " + OTHER_HEAVY + " is not in the moveset");
			followUpRow(helper, power, user, stand, otherHeavy, lifeshot, lifeshotPunch, "a heavy attack named " + OTHER_HEAVY);
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned()) {
				standType.forceUnsummon(user, power);
			}
			if (cow != null) {
				cow.discard();
			}
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			user.discard();
		}
	}

	/*
	 * 1.16 StandAction.canSetFollowUp offered the Life Shot only if PowerBaseImpl.checkRequirements passed for it on the
	 * punch's target; otherwise the key stayed the running Life Shot punch, which a press could not start again. A key the
	 * punch gave back to the base heavy for a part the Stand lost (getFinisherVariationIfPresent) offered no Life Shot. The
	 * port keeps the refusal message the Life Shot gives (only_stand_user on a punched Stand) on that refused press.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theHeavyKeyGivesTheLifeShotOnlyWhenItsRequirementsPassAsIn116(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
		StandType enemyType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		helper.assertTrue(standType != null && enemyType != null, "Missing the Gold Experience or Star Platinum Stand type");
		Player user = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "LifeShotRequirements"));
		Player enemy = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "LifeShotEnemy"));
		StandPower power = null;
		StandPower enemyPower = null;
		Cow cow = null;
		try {
			user.getAbilities().instabuild = false;
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Life Shot player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Gold Experience");
			power.setResolveLevel(4);
			for (String name : new String[] { "heavy_punch", "lifeshot", "lifeshot_punch" }) {
				power.getCurTypeData()._lockedAbilities.remove(name);
			}
			Ability heavy = power.getAbility("heavy_punch");
			Ability lifeshot = power.getAbility("lifeshot");
			Ability lifeshotPunch = power.getAbility("lifeshot_punch");
			helper.assertTrue(heavy != null && lifeshot != null && lifeshotPunch != null
					&& lifeshot.isAbilityUnlocked(power) && lifeshotPunch.isAbilityUnlocked(power),
					"fixture: Gold Experience's heavy punch, Life Shot or Life Shot punch is missing or locked");
			helper.assertTrue(standType.summon(user, power), "Could not summon Gold Experience");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Gold Experience is missing");
			stand.summonLockTicks = 0;
			StandInstance instance = power.getStandInstance().orElseThrow();

			cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "Could not create the cow");
			cow.moveTo(userPos.x, userPos.y, userPos.z + 1.5D, 180.0F, 0.0F);
			cow.setNoAi(true);
			cow.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");

			// a cooldown on the Life Shot: 1.16 isActionOnCooldown refused it without a message
			ToothKnockingHeavyPunch punch = startLifeShotPunch(helper, power, user, stand, heavy, lifeshotPunch, cow);
			power.setAbilityCooldown("lifeshot", 100);
			AbilityConditionCheck onCooldown = resolve(power, heavy);
			helper.assertTrue(onCooldown != null && onCooldown.ability == lifeshotPunch && !onCooldown.conditionCheck.isPositive()
					&& onCooldown.conditionCheck.getWarning() == null, "1.16 left the heavy key on the running Life Shot punch,"
							+ " refused without a message, while the Life Shot was on cooldown: it resolves to " + describe(onCooldown));
			power.setAbilityCooldown("lifeshot", 0);
			AbilityConditionCheck offCooldown = resolve(power, heavy);
			helper.assertTrue(offCooldown != null && offCooldown.ability == lifeshot && offCooldown.conditionCheck.isPositive()
					&& punch.canAcceptLifeshotFollowup(), "fixture: without the cooldown the key does not give the Life Shot: "
							+ describe(offCooldown));

			// Gold Experience loses its arms during the punch at the cow, whose Life Shot needs no arms: 1.16 gave the key
			// back to the base heavy punch
			instance.removePart(StandPart.ARMS);
			try {
				helper.assertTrue(LivingComponentAction.getComponent(stand).getAction() == punch && punch.canAcceptLifeshotFollowup()
						&& lifeshot.checkConditions(power).isPositive(),
						"fixture: the Life Shot punch did not go on without the arms, or its Life Shot is refused");
				AbilityConditionCheck armless = resolve(power, heavy);
				helper.assertTrue(armless != null && armless.ability == heavy, "1.16 gave the heavy key back to the base heavy"
						+ " punch once the running Life Shot punch needed the arms the Stand lost, so no Life Shot: it resolves to "
						+ describe(armless));
			}
			finally {
				instance.addPart(StandPart.ARMS);
			}

			// the punch's target is a Stand: 1.16 GoldExperienceEntityLifeshot.checkTarget refused it with only_stand_user
			cow.discard();
			Vec3 enemyPos = userPos.add(2.0D, 0.0D, 4.0D);
			enemy.moveTo(enemyPos.x, enemyPos.y, enemyPos.z, 180.0F, 0.0F);
			enemy.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(enemy), "Could not add the enemy Stand user");
			enemyPower = PowerClass.STAND.attachGet(enemy);
			helper.assertTrue(StandPowerTransitions.insert(enemyPower, new StandInstance(enemyType)).status()
					== StandPowerTransitions.Status.APPLIED && enemyType.summon(enemy, enemyPower), "Could not give and summon Star Platinum");
			StandEntity enemyStand = enemyPower.getSummonedStandEntity();
			helper.assertTrue(enemyStand != null, "Summoned Star Platinum is missing");
			enemyStand.moveTo(userPos.x, userPos.y, userPos.z + 1.5D, 180.0F, 0.0F);
			punch = startLifeShotPunch(helper, power, user, stand, heavy, lifeshotPunch, enemyStand);
			helper.assertTrue(punch.getLifeshotTarget() == enemyStand, "fixture: the Life Shot punch is not at Star Platinum: "
					+ punch.getLifeshotTarget());
			AbilityConditionCheck atAStand = resolve(power, heavy);
			String message = atAStand != null && atAStand.conditionCheck.getWarning() != null
					&& atAStand.conditionCheck.getWarning().getContents() instanceof TranslatableContents contents
							? contents.getKey() : null;
			helper.assertTrue(atAStand != null && atAStand.ability == lifeshotPunch && !atAStand.conditionCheck.isPositive()
					&& "jojo.message.action_condition.only_stand_user".equals(message), "1.16 left the heavy key on the running"
							+ " Life Shot punch at a Stand, refused, and the port keeps the Life Shot's only_stand_user message on"
							+ " it: it resolves to " + describe(atAStand) + " with the message " + message);
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
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

	// the heavy key pressed with a full finisher meter at the target, which starts the Life Shot punch
	private static ToothKnockingHeavyPunch startLifeShotPunch(GameTestHelper helper, StandPower power, Player user,
			StandEntity stand, Ability heavy, Ability lifeshotPunch, Entity target) {
		LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
		standAction.setAction(null, user, SyncType.NO_SYNC);
		LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(target));
		standAction.entityAim.setTarget(new ActionTarget(target));
		power.setStamina(power.getMaxStamina());
		stand.setFinisherMeter(1.0F);
		AbilityConditionCheck pressed = press(power, user, heavy);
		EntityActionInstance action = standAction.getAction();
		helper.assertTrue(pressed != null && pressed.ability == lifeshotPunch && action instanceof ToothKnockingHeavyPunch punch
				&& action.ability == lifeshotPunch && punch.canAcceptLifeshotFollowup(), "fixture: a full meter did not make the"
						+ " key start the Life Shot punch at " + target + ": " + describe(pressed) + ", action " + action);
		return (ToothKnockingHeavyPunch) action;
	}

	private static void followUpRow(GameTestHelper helper, StandPower power, Player user, StandEntity stand, Ability base,
			Ability lifeshot, Ability lifeshotPunch, String row) {
		LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
		standAction.setAction(null, user, SyncType.NO_SYNC);
		power.setStamina(power.getMaxStamina());
		stand.setFinisherMeter(1.0F);
		AbilityConditionCheck pressed = press(power, user, base);
		EntityActionInstance action = standAction.getAction();
		helper.assertTrue(pressed != null && pressed.ability == lifeshotPunch && action instanceof ToothKnockingHeavyPunch
				&& action.ability == lifeshotPunch, "fixture (" + row + "): a full meter did not make the key start the Life"
						+ " Shot punch: " + describe(pressed) + ", action " + action);
		ToothKnockingHeavyPunch punch = (ToothKnockingHeavyPunch) action;
		helper.assertTrue(punch.canAcceptLifeshotFollowup(),
				"fixture (" + row + "): the running Life Shot punch has no entity target to take a Life Shot");

		AbilityConditionCheck followUp = resolve(power, base);
		helper.assertTrue(followUp != null && followUp.ability == lifeshot && followUp.conditionCheck.isPositive(),
				"1.16 gave the running Life Shot punch's Life Shot under the key of the heavy attack it is the finisher of ("
						+ row + "): it resolves to " + describe(followUp));
		press(power, user, base);
		helper.assertTrue(standAction.getAction() == punch && !punch.canAcceptLifeshotFollowup(),
				"the running Life Shot punch did not take the Life Shot pressed under its key (" + row + "): action "
						+ standAction.getAction());

		AbilityConditionCheck taken = resolve(power, base);
		helper.assertTrue(taken != null && taken.ability == lifeshotPunch && !taken.conditionCheck.isPositive(),
				"1.16 gave the follow-up once (canSetFollowUp: the task had it), then the running Life Shot punch, refused ("
						+ row + "): " + describe(taken));
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
