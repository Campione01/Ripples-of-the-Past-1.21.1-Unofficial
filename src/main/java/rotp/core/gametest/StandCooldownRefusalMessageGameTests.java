package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 PowerBaseImpl.checkRequirements refused an action on cooldown with ActionConditionResult.NEGATIVE, which
 * carries no message: a Stand press on cooldown is refused without an action bar line, held or clicked.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandCooldownRefusalMessageGameTests {
	private StandCooldownRefusalMessageGameTests() {}

	@GameTest(template = "empty")
	public static void standPressOnCooldownIsRefusedWithoutAMessage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RecordingPlayer user = new RecordingPlayer(level);
		user.setGameMode(GameType.SURVIVAL);
		user.setNoGravity(true);
		Vec3 pos = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
		user.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(level.addFreshEntity(user), "fixture: the Star Platinum user was not added");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = PowerClass.STAND.attachGet(user);
		try {
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "fixture: Star Platinum not granted");
			power.setResolveLevel(3);
			helper.assertTrue(type.summon(user, power), "fixture: Star Platinum not summoned");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "fixture: no summoned Stand");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			power.setStamina(power.getMaxStamina());

			helper.assertTrue(!AbilityInput.withConditionCheck(null, user, InputMethod.CLICK)
					&& user.keys().equals(List.of("jojo.message.action_condition.not_unlocked")),
					"fixture: a refusal with a message is not recorded: " + user.keys());

			for (String name : List.of("star_finger", "barrage")) {
				Ability ability = power.getAbility(name);
				InputMethod input = "barrage".equals(name) ? InputMethod.HOLD : InputMethod.CLICK;
				helper.assertTrue(ability != null && power.getAbilityCooldown(name) == 0, "fixture: " + name + " missing or on cooldown");
				helper.assertTrue(press(power, ability, user, input) && user.keys().isEmpty(),
						"fixture: " + name + " is not admitted off cooldown: " + user.keys());

				power.setAbilityCooldown(name, 80);
				helper.assertTrue(power.isAbilityOnCooldown(name), "fixture: " + name + " is not on cooldown");
				boolean admitted = press(power, ability, user, input);
				helper.assertTrue(!admitted && user.keys().isEmpty(),
						"1.16 refuses " + name + " on cooldown without a message: admitted=" + admitted
								+ " messages=" + user.keys());
				power.setAbilityCooldown(name, 0);
			}
		}
		finally {
			if (type != null && power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
		helper.succeed();
	}

	// the server side of a press: the moves are read afresh, as the per-tick cache would not see the new cooldown
	private static boolean press(StandPower power, Ability ability, RecordingPlayer user, InputMethod input) {
		user.actionBar.clear();
		AvailableAbilities available = new AvailableAbilities();
		available.update(power, power.getMoveset());
		return AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, input);
	}

	private static final class RecordingPlayer extends FakePlayer {
		private final List<Component> actionBar = new ArrayList<>();

		private RecordingPlayer(ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "CooldownRefusal"));
		}

		@Override
		public void displayClientMessage(Component message, boolean actionBar) {
			if (actionBar) {
				this.actionBar.add(message);
			}
		}

		List<String> keys() {
			return actionBar.stream()
					.map(message -> message.getContents() instanceof TranslatableContents contents
							? contents.getKey() : message.getString())
					.toList();
		}
	}
}
