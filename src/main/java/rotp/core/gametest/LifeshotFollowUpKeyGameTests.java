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
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
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
