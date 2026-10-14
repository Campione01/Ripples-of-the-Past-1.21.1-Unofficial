package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.starplatinum.StarInhaleAbility.InhaleAbilityInstance;
import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StarPlatinumInhale suffocated what it pulled in with DamageUtil.suffocateTick(entity, 0.025F): plain
 * suffocation with no attacker, so it angered nothing, gave no kill credit and read "X suffocated".
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarInhaleSuffocationGameTests {
	private static final short KEY = 123;

	private StarInhaleSuffocationGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void inhaleSuffocationHasNoAttacker(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		StandPower[] power = new StandPower[1];
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Cow cow = new Cow(EntityType.COW, level);
		List<DamageSource> suffocations = new ArrayList<>();
		Consumer<LivingIncomingDamageEvent> damage = event -> {
			if (event.getEntity() == cow && event.getSource().is(ModDamageTypes.SUFFOCATION)) {
				suffocations.add(event.getSource());
			}
		};
		Runnable close = () -> {
			NeoForge.EVENT_BUS.unregister(damage);
			AbilityInput.keyRelease(KEY, user);
			if (power[0] != null && type != null && power[0].isSummoned()) {
				type.forceUnsummon(user, power[0]);
			}
			cow.discard();
			user.discard();
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, damage);
		try {
			// high above the test cell, so the pull reaches nothing else
			Vec3 origin = helper.absoluteVec(new Vec3(2.5, 12, 2.5));
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Star Platinum user");
			power[0] = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power[0], new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			power[0].setResolveLevel(2);
			helper.assertTrue(type.summon(user, power[0]), "Could not summon Star Platinum");
			StandEntity stand = power[0].getSummonedStandEntity();
			helper.assertTrue(stand != null, "The summoned Stand is missing");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.setCanFollowUser(false);
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
			power[0].setStamina(power[0].getMaxStamina());

			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(origin.x, origin.y, origin.z + 2.5, 0, 0);
			cow.setAirSupply(0);
			helper.assertTrue(level.addFreshEntity(cow), "Could not add the inhaled cow");

			Ability inhale = power[0].getAbility("inhale");
			AbilityConditionCheck selected = power[0].updateAvailableMoves().getContextVariationContainer(inhale);
			helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.HOLD),
					"fixture: Inhale press not admitted");
			BufferingState press = BufferingState.clickOnly();
			AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.HOLD, 0, press, inhale.getAbilityId());
			helper.assertTrue(press.isActionSuccess && stand.getCurStandAction() instanceof InhaleAbilityInstance,
					"fixture: Inhale did not start");
		}
		catch (RuntimeException | Error failure) {
			close.run();
			throw failure;
		}
		helper.runAfterDelay(5, () -> {
			try {
				helper.assertTrue(!suffocations.isEmpty(), "fixture: the cow without air was not suffocated");
				DamageSource source = suffocations.get(0);
				helper.assertTrue(source.getEntity() == null && source.getDirectEntity() == null,
						"Inhale suffocation must have no attacker, got " + source.getEntity());
				helper.assertTrue(cow.getLastHurtByMob() == null,
						"a sourceless suffocation angers no one, but the cow blames " + cow.getLastHurtByMob());
				String key = "death.attack." + source.getMsgId();
				helper.assertTrue(Language.getInstance().has(key), "fixture: the server has no text for " + key);
				String message = source.getLocalizedDeathMessage(cow).getString();
				helper.assertTrue(!message.contains("%") && message.startsWith(cow.getDisplayName().getString()),
						"the death message must read without an attacker: " + message);
			}
			finally {
				close.run();
			}
			helper.succeed();
		});
	}
}
