package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityHeavyAttack.getFinisherVariationIfPresent: the heavy key resolved to the finisher variation while it
 * was the Stand's current task, whatever the finisher meter, unless the variation needed a part the Stand had lost; and
 * StandEntityAction.canClickDuringTask refused it there (a heavy attack is not chainable), silently and not queued.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandFinisherKeyGameTests {
	private static final short KEY = 21;

	private StandFinisherKeyGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theHeavyKeyStaysOnARunningFinisherAndIsRefusedAsIn116(GameTestHelper helper) {
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		helper.assertTrue(standType != null, "Missing Star Platinum Stand type");
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "FinisherKey"));
		StandPower power = null;
		try {
			user.getAbilities().instabuild = false;
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the finisher player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			power.setResolveLevel(4);
			power.getCurTypeData()._lockedAbilities.remove("heavy_punch");
			power.getCurTypeData()._lockedAbilities.remove("finisher_uppercut");
			Ability heavy = power.getAbility("heavy_punch");
			Ability uppercut = power.getAbility("finisher_uppercut");
			helper.assertTrue(heavy != null && uppercut != null && uppercut.isAbilityUnlocked(power),
					"fixture: Star Platinum's heavy punch or unlocked finisher uppercut is missing");
			helper.assertTrue(standType.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Star Platinum is missing");
			stand.summonLockTicks = 0;
			power.setStamina(power.getMaxStamina());
			LivingComponentAction standAction = LivingComponentAction.getComponent(stand);

			stand.setFinisherMeter(1.0F);
			AbilityConditionCheck pressed = press(power, user, heavy);
			EntityActionInstance finisher = standAction.getAction();
			helper.assertTrue(pressed != null && pressed.ability == uppercut && finisher != null && finisher.ability == uppercut,
					"fixture: a full finisher meter did not make the heavy key start the finisher uppercut: "
							+ describe(pressed) + ", action " + (finisher != null ? finisher.ability : null));

			// the meter emptied: only the running task keeps the key on the variation
			stand.setFinisherMeter(0.0F);
			for (ActionPhase phase : new ActionPhase[] { ActionPhase.WINDUP, ActionPhase.RECOVERY }) {
				for (int tick = 0; tick < 60 && finisher.getPhase() != phase && !finisher.isOver(); tick++) {
					standAction.tick();
				}
				helper.assertTrue(finisher.getPhase() == phase && standAction.getAction() == finisher,
						"fixture: the finisher uppercut did not reach its " + phase + ": phase " + finisher.getPhase());
				AbilityConditionCheck resolved = resolve(power, heavy);
				helper.assertTrue(resolved != null && resolved.ability == uppercut,
						"1.16 kept the heavy key on the finisher uppercut while it was the Stand's task (" + phase
								+ "), whatever the meter: it resolves to " + describe(resolved));
				helper.assertTrue(!resolved.conditionCheck.isPositive() && resolved.conditionCheck.getWarning() == null,
						"1.16 refused the running finisher pressed again, silently (" + phase + "): " + describe(resolved));
				AbilityConditionCheck again = press(power, user, heavy);
				helper.assertTrue(standAction.getAction() == finisher && finisher.getPhase() == phase,
						"the heavy key pressed in the finisher uppercut's " + phase + " started " + describe(again)
								+ ": the Stand's action is " + standAction.getAction());
			}

			// a variation that needs a part the Stand lost gives the key back to the base
			StandInstance instance = power.getStandInstance().orElseThrow();
			instance.removePart(StandPart.ARMS);
			try {
				helper.assertTrue(standAction.getAction() == finisher, "fixture: the finisher uppercut ended with the arms");
				AbilityConditionCheck resolved = resolve(power, heavy);
				helper.assertTrue(resolved != null && resolved.ability == heavy,
						"1.16 gave the heavy key back to the base heavy punch once the running finisher needed a lost part: "
								+ describe(resolved));
			}
			finally {
				instance.addPart(StandPart.ARMS);
			}

			for (int tick = 0; tick < 60 && standAction.getAction() != null; tick++) {
				standAction.tick();
			}
			helper.assertTrue(standAction.getAction() == null, "fixture: the finisher uppercut did not end");
			AbilityConditionCheck after = resolve(power, heavy);
			helper.assertTrue(after != null && after.ability == heavy && after.conditionCheck.isPositive(),
					"with the finisher over and the meter empty the heavy key is the heavy punch again: " + describe(after));
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned()) {
				standType.forceUnsummon(user, power);
			}
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			user.discard();
		}
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
