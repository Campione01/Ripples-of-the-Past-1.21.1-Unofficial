package rotp.core.gametest;

import java.util.function.Consumer;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonActionRuntimeAbility;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.abilities.PillarmanAbsorptionAbility;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.abilities.VampirismActionAbility;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 PowerBaseImpl.checkRequirements refused an action on cooldown with ActionConditionResult.NEGATIVE, which
 * carries no message, for every power: NonStandPowerImpl extends PowerBaseImpl. Hamon, Pillar Man and Vampirism
 * refuse a move on cooldown without the 'Ability is on cooldown' line, as Stands do.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NonStandCooldownRefusalMessageGameTests {
	private static final int COOLDOWN = 80;

	private NonStandCooldownRefusalMessageGameTests() {}

	@GameTest(template = "empty")
	public static void hamonMoveOnCooldownIsRefusedWithoutAMessage(GameTestHelper helper) {
		Player user = user(helper);
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.HAMON_CUTTER.get());
			hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.pointsAtLevel(10), true, true);
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOAP.get()));
			Ability ability = power.getAbility("hamon_cutter");
			helper.assertTrue(ability instanceof HamonActionRuntimeAbility, "fixture: no Hamon Cutter");
			assertSilentCooldownRefusal(helper, power, name -> hamon.setAbilityCooldown(name, COOLDOWN), ability, "Hamon Cutter");
		}
		finally {
			user.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void pillarManMoveOnCooldownIsRefusedWithoutAMessage(GameTestHelper helper) {
		Player user = user(helper);
		Cow prey = null;
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.PILLAR_MAN.get());
			PillarmanData data = PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
			prey = prey(helper, user);
			data.setEvolutionStage(2, user);
			data.setMode(PillarmanMode.NONE, user);
			data.setEnergy(user, 10.0F);
			Ability ability = power.getAbility("pillarman_absorption");
			helper.assertTrue(ability instanceof PillarmanAbsorptionAbility, "fixture: no Pillar Man absorption");
			assertSilentCooldownRefusal(helper, power, name -> data.setAbilityCooldown(name, COOLDOWN), ability,
					"Pillar Man absorption");
		}
		finally {
			if (prey != null) prey.discard();
			user.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void vampirismMoveOnCooldownIsRefusedWithoutAMessage(GameTestHelper helper) {
		Player user = user(helper);
		Cow prey = null;
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
			VampirismData data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
			prey = prey(helper, user);
			Ability ability = power.getAbility("vampirism_blood_drain");
			helper.assertTrue(ability instanceof VampirismActionAbility, "fixture: no Blood Drain");
			assertSilentCooldownRefusal(helper, power, name -> data.setAbilityCooldown(name, COOLDOWN), ability, "Blood Drain");
		}
		finally {
			if (prey != null) prey.discard();
			user.discard();
		}
		helper.succeed();
	}

	private static void assertSilentCooldownRefusal(GameTestHelper helper, PlayerPower power, Consumer<String> setCooldown,
			Ability ability, String move) {
		ConditionCheck ready = ability.checkSpecificConditions(power);
		helper.assertTrue(ready.isPositive(), "fixture: " + move + " is refused off cooldown: " + ready.getWarning());
		setCooldown.accept(ability.name());
		ConditionCheck refused = ability.checkSpecificConditions(power);
		helper.assertTrue(!refused.isPositive() && refused.getWarning() == null,
				"1.16 refuses " + move + " on cooldown without a message: positive=" + refused.isPositive()
						+ " warning=" + (refused.getWarning() != null ? refused.getWarning().getString() : null));
	}

	// the drain and the absorption need a living target in reach
	private static Cow prey(GameTestHelper helper, Player user) {
		Cow cow = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(cow != null, "fixture: no cow");
		cow.setNoAi(true);
		cow.setNoGravity(true);
		cow.moveTo(user.getX(), user.getY(), user.getZ() + 1.5, 180, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(cow), "fixture: the cow was not added");
		LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(cow));
		return cow;
	}

	private static Player user(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		user.setNoGravity(true);
		Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		user.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "fixture: the user was not added");
		return user;
	}
}
