package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityPunch.doAttack: a hurting hit of a disableBlocking(1.0F) heavy attack (Star Platinum's uppercut,
 * The World's kick and time-stop punch) put a player's raised shield on a 100-tick cooldown (DamageUtil.disableShield).
 * A plain heavy punch, or a hit the shield blocked, disabled nothing.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavyAttackShieldDisableGameTests {
	private HeavyAttackShieldDisableGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void heavyFinishersDisableRaisedShield(GameTestHelper helper) {
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		StandType spType = stand(helper, "star_platinum");
		StandType twType = stand(helper, "the_world");
		Player spUser = addPlayer(helper, pos.add(0, 0, 6));
		Player twUser = addPlayer(helper, pos.add(6, 0, 6));
		StandPower spPower = PowerClass.STAND.attachGet(spUser);
		StandPower twPower = PowerClass.STAND.attachGet(twUser);
		Player[] blockers = new Player[4];
		try {
			StandEntity sp = summon(helper, spType, spUser, spPower);
			StandEntity tw = summon(helper, twType, twUser, twPower);
			for (int i = 0; i < blockers.length; i++) {
				blockers[i] = addBlocker(helper, pos.add(i * 4, 0, 0));
			}
			// under 5 ticks of use the shield is up but not blocking, so the hit gets through
			assertDisabled(helper, blockers[0], sp, spPower, "finisher_uppercut", true);
			assertDisabled(helper, blockers[1], tw, twPower, "kick", true);
			assertDisabled(helper, blockers[2], tw, twPower, "ts_punch", true);
			assertDisabled(helper, blockers[3], sp, spPower, "heavy_punch", false);
		}
		finally {
			unsummon(spType, spUser, spPower);
			unsummon(twType, twUser, twPower);
			spUser.discard();
			twUser.discard();
			for (Player blocker : blockers) {
				if (blocker != null) blocker.discard();
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void blockedUppercutLeavesShieldUp(GameTestHelper helper) {
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		StandType spType = stand(helper, "star_platinum");
		Player spUser = addPlayer(helper, pos.add(0, 0, 6));
		StandPower spPower = PowerClass.STAND.attachGet(spUser);
		Player blocker = null;
		StandEntity sp;
		try {
			sp = summon(helper, spType, spUser, spPower);
			blocker = addBlocker(helper, pos);
		}
		catch (RuntimeException | AssertionError error) {
			unsummon(spType, spUser, spPower);
			spUser.discard();
			if (blocker != null) blocker.discard();
			throw error;
		}
		Player raised = blocker;
		// a raised shield blocks after 5 ticks of use
		helper.runAfterDelay(8, () -> {
			try {
				helper.assertTrue(raised.isBlocking(), "The player's shield is not raised");
				float health = raised.getHealth();
				heavyHit(helper, raised, sp, spPower, "finisher_uppercut");
				helper.assertTrue(raised.getHealth() == health, "The shield did not block the uppercut");
				helper.assertFalse(raised.getCooldowns().isOnCooldown(Items.SHIELD),
						"1.16: a blocked uppercut does not put the shield on cooldown");
				helper.assertTrue(raised.isUsingItem(), "1.16: a blocked uppercut leaves the shield raised");
			}
			finally {
				unsummon(spType, spUser, spPower);
				spUser.discard();
				raised.discard();
			}
			helper.succeed();
		});
	}

	private static void assertDisabled(GameTestHelper helper, Player blocker, StandEntity stand, StandPower power,
			String abilityName, boolean disables) {
		helper.assertTrue(blocker.isUsingItem() && !blocker.isBlocking(),
				"The " + abilityName + " target's shield is not freshly raised");
		float health = blocker.getHealth();
		heavyHit(helper, blocker, stand, power, abilityName);
		helper.assertTrue(blocker.getHealth() < health, "The " + abilityName + " did not hurt the player");
		boolean cooldown = blocker.getCooldowns().isOnCooldown(Items.SHIELD);
		if (disables) {
			helper.assertTrue(cooldown, "1.16: a hurting " + abilityName + " puts the raised shield on cooldown");
			helper.assertFalse(blocker.isUsingItem(), "1.16: a hurting " + abilityName + " lowers the shield");
		}
		else {
			helper.assertFalse(cooldown, "1.16: a plain " + abilityName + " does not put the shield on cooldown");
			helper.assertTrue(blocker.isUsingItem(), "1.16: a plain " + abilityName + " leaves the shield raised");
		}
	}

	// runs the ability's hitEntity from in front of the target, facing it
	private static void heavyHit(GameTestHelper helper, Player target, StandEntity stand, StandPower power, String abilityName) {
		Ability ability = power.getMoveset().getAbility(abilityName);
		helper.assertTrue(ability instanceof EntityActionType, "Missing entity action " + abilityName);
		Level level = helper.getLevel();
		EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(level, power.getUser(), stand, null);
		helper.assertTrue(action instanceof StandEntityHeavyPunch, abilityName + " is not a heavy punch");
		Vec3 pos = target.position();
		target.moveTo(pos.x, pos.y, pos.z, 0, 0);
		target.setYHeadRot(0);
		stand.moveTo(pos.x, pos.y, pos.z + 1.5D, 180, 0);
		DamageSource source = DamageUtil.make(level, ModDamageTypes.STAND_ATTACK, stand);
		try {
			Field performer = EntityActionInstance.class.getDeclaredField("performer");
			performer.setAccessible(true);
			performer.set(action, stand);
			Method hitEntity = StandEntityHeavyPunch.class.getDeclaredMethod("hitEntity", ActionTarget.class, Level.class,
					StandEntity.class, DamageSource.class, float.class, float.class);
			hitEntity.setAccessible(true);
			hitEntity.invoke(action, new ActionTarget(target), level, stand, source, 1.0F, 0.0F);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not run " + abilityName + " hitEntity", e);
		}
	}

	private static Player addPlayer(GameTestHelper helper, Vec3 pos) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		player.moveTo(pos.x, pos.y, pos.z, 180, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add a player");
		return player;
	}

	private static Player addBlocker(GameTestHelper helper, Vec3 pos) {
		Player blocker = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		blocker.moveTo(pos.x, pos.y, pos.z, 0, 0);
		blocker.setYHeadRot(0);
		blocker.getAbilities().invulnerable = false;
		blocker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		helper.assertTrue(helper.getLevel().addFreshEntity(blocker), "Could not add the blocker");
		blocker.startUsingItem(InteractionHand.OFF_HAND);
		return blocker;
	}

	private static StandType stand(GameTestHelper helper, String id) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(id));
		helper.assertTrue(type != null, "Missing registered Stand " + id);
		return type;
	}

	private static StandEntity summon(GameTestHelper helper, StandType type, LivingEntity user, StandPower power) {
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED && type.summon(user, power),
				"Could not give " + user.getName().getString() + " a summoned Stand");
		StandEntity stand = power.getSummonedStandEntity();
		helper.assertTrue(stand != null && stand.getUser() == user, "The summoned Stand is missing its user");
		return stand;
	}

	private static void unsummon(StandType type, LivingEntity user, StandPower power) {
		if (power.isSummoned()) {
			type.forceUnsummon(user, power);
		}
	}
}
