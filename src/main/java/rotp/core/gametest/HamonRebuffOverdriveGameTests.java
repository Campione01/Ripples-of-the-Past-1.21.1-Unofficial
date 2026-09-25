package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.abilities.HamonRebuffOverdriveAbility.HamonRebuffOverdrive;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonRebuffOverdrive: the user stands still (getWalkSpeed 0); only a melee hit from the front is countered, a
 * Stand's only with Hermit Purple out; any other hit while charging gets Hamon Protection's cut even with Protection
 * off; the client stopped the action once it had struck or reached its recovery (onWASDInput).
 * The user faces south (+Z); the attacker stands 1.5 blocks in front of or behind it.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonRebuffOverdriveGameTests {
	private static final int COUNTER_TIMING_TICK = HamonRebuffOverdrive.WINDUP_TICKS - HamonRebuffOverdrive.COUNTER_TIMING_WINDOW + 1;

	private HamonRebuffOverdriveGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void frontMeleeHitIsCounteredAndTheActionEnds(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			EntityActionInstance action = f.start();
			f.tick(COUNTER_TIMING_TICK);
			helper.assertTrue(action.userWalkSpeed == 0.0F, "1.16 kept the user still while charging: walk speed=" + action.userWalkSpeed);
			Player attacker = f.attackerAt(1.5D);
			float before = f.user.getHealth();
			f.user.hurt(helper.getLevel().damageSources().playerAttack(attacker), 4.0F);
			helper.assertTrue(f.user.getHealth() == before, "The countered hit still landed");
			helper.assertTrue(attacker.getHealth() < attacker.getMaxHealth(), "The front melee hit was not countered");
			// 1.16 punch: the player's own fist hit lands after the Hamon
			helper.assertTrue(f.fistHitLanded(), "1.16 landed the user's fist after the Hamon: last hit=" + attacker.getLastDamageSource());
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM, "The counter did not start the attack: " + action.getPhase());
			helper.assertTrue(action.userWalkSpeed == 0.0F, "1.16 kept the user still while striking: walk speed=" + action.userWalkSpeed);
			f.tick(1);
			helper.assertTrue(f.component.getAction() != action || action.isOver(), "1.16 ended Rebuff Overdrive once it had struck");
			helper.succeed();
		}
	}

	// 1.16 doCounterAttack: the counter cancels the hit even when its Hamon cannot hurt the attacker
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void counterHoldsWhenTheHamonCannotHurt(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			EntityActionInstance action = f.start();
			f.tick(COUNTER_TIMING_TICK);
			Player attacker = f.attackerAt(1.5D);
			attacker.getAbilities().invulnerable = true;
			float before = f.user.getHealth();
			f.user.hurt(helper.getLevel().damageSources().playerAttack(attacker), 4.0F);
			helper.assertTrue(attacker.getHealth() == attacker.getMaxHealth(), "The invulnerable attacker was hurt");
			helper.assertTrue(f.user.getHealth() == before, "1.16 countered the hit though the Hamon did not land");
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM, "The counter did not start the attack: " + action.getPhase());
			helper.succeed();
		}
	}

	// 1.16 punch: the fist hit lands even when the Hamon cannot (a Hamon user in the Satiporoja scarf)
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void counterFistLandsWhenTheHamonCannot(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			EntityActionInstance action = f.start();
			f.tick(COUNTER_TIMING_TICK);
			Player attacker = f.attackerAt(1.5D);
			PowerClass.PLAYER_POWER.attachGet(attacker).setPowerType(ModPlayerPowers.HAMON.get());
			attacker.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SATIPOROJA_SCARF.get()));
			helper.assertTrue(HamonAbilityHelpers.hamonDamageAmount(attacker, 10.0F) == 0.0F,
					"The scarf did not make the Hamon user immune to Hamon");
			float before = f.user.getHealth();
			f.user.hurt(helper.getLevel().damageSources().playerAttack(attacker), 4.0F);
			helper.assertTrue(f.user.getHealth() == before, "1.16 countered the hit though the Hamon did not land");
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM, "The counter did not start the attack: " + action.getPhase());
			helper.assertTrue(attacker.getHealth() < attacker.getMaxHealth() && f.fistHitLanded(),
					"1.16 still punched a target the Hamon could not hurt: health=" + attacker.getHealth()
					+ " last hit=" + attacker.getLastDamageSource());
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void hitFromBehindGetsTheProtectionCut(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			EntityActionInstance action = f.start();
			f.tick(COUNTER_TIMING_TICK);
			helper.assertTrue(!f.hamon.isProtectionEnabled(), "Hamon Protection is on");
			Player attacker = f.attackerAt(-1.5D);
			float amount = 5.0F;
			float before = f.user.getHealth();
			f.user.hurt(helper.getLevel().damageSources().playerAttack(attacker), amount);
			float taken = before - f.user.getHealth();
			helper.assertTrue(attacker.getHealth() == attacker.getMaxHealth(), "1.16 countered only a hit from the front");
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.WINDUP,
					"A hit from behind must leave the charge going: " + action.getPhase());
			helper.assertTrue(taken > 0.0F && taken < amount - 0.5F,
					"1.16 cut a hit the charge let through with Hamon Protection: took " + taken + " of " + amount);
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standHitIsNotCounteredWithoutHermitPurple(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			Player attacker = f.attackerAt(3.0D);
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			StandPower attackerPower = PowerClass.STAND.attachGet(attacker);
			try {
				helper.assertTrue(StandPowerTransitions.insert(attackerPower, new StandInstance(type)).status()
						== StandPowerTransitions.Status.APPLIED && type.summon(attacker, attackerPower),
						"Could not give the attacker a summoned Star Platinum");
				StandEntity stand = attackerPower.getSummonedStandEntity();
				helper.assertTrue(stand != null, "The attacker's Stand is missing");
				EntityActionInstance action = f.start();
				f.tick(COUNTER_TIMING_TICK);
				// the Stand's fist, from in front of the user
				stand.moveTo(f.pos.x, f.pos.y, f.pos.z + 1.5D, 180.0F, 0.0F);
				helper.assertTrue(!f.hamon.isProtectionEnabled(), "Hamon Protection is on");
				float amount = 5.0F;
				float before = f.user.getHealth();
				boolean hurt = f.user.hurt(DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_ATTACK, stand), amount);
				float taken = before - f.user.getHealth();
				helper.assertTrue(hurt && taken > 0.0F, "1.16 did not counter a Stand's hit without Hermit Purple");
				helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.WINDUP,
						"The Stand's hit was countered: " + action.getPhase());
				// 1.16 cancelIncomingDamage: the uncountered Stand hit is marked for Hamon Protection's cut
				helper.assertTrue(taken < amount - 0.5F,
						"1.16 cut a Stand's hit the charge let through with Hamon Protection: took " + taken + " of " + amount);
			}
			finally {
				if (attackerPower.isSummoned()) {
					type.forceUnsummon(attacker, attackerPower);
				}
			}
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void recoveryEndsTheAction(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			EntityActionInstance action = f.start();
			f.tick(1);
			helper.assertTrue(action.getPhase() == ActionPhase.WINDUP && action.userWalkSpeed == 0.0F,
					"1.16 kept the user still while charging: walk speed=" + action.userWalkSpeed);
			action.setPhaseStart(ActionPhase.RECOVERY);
			f.tick(1);
			helper.assertTrue(f.component.getAction() != action || action.isOver(), "1.16 ended Rebuff Overdrive on reaching its recovery");
			helper.succeed();
		}
	}

	private static final class Fixture implements AutoCloseable {
		final GameTestHelper helper;
		final Player user;
		final PlayerPower power;
		final HamonData hamon;
		final LivingComponentAction component;
		final Vec3 pos;
		Player attacker;

		Fixture(GameTestHelper helper) {
			this.helper = helper;
			pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.getAbilities().invulnerable = false;
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Rebuff Overdrive user");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.REBUFF_OVERDRIVE.get());
			helper.assertTrue(hamon.isSkillLearned(ModHamonSkills.REBUFF_OVERDRIVE.get()), "Could not grant Rebuff Overdrive");
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			component = LivingComponentAction.getComponent(user);
		}

		EntityActionInstance start() {
			Ability found = power.getAbility("rebuff_overdrive");
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered rebuff_overdrive");
			EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			helper.assertTrue(action instanceof HamonRebuffOverdrive, "rebuff_overdrive made no Rebuff Overdrive action");
			component.setAction(action, user, SyncType.NO_SYNC);
			return action;
		}

		// the action's own ticks, without the level's entity physics
		void tick(int count) {
			for (int i = 0; i < count; i++) {
				user.tickCount++;
				component.tick();
			}
		}

		// a player facing the user, dz blocks along +Z from it
		Player attackerAt(double dz) {
			attacker = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			attacker.moveTo(pos.x, pos.y, pos.z + dz, dz > 0 ? 180.0F : 0.0F, 0.0F);
			attacker.getAbilities().invulnerable = false;
			helper.assertTrue(helper.getLevel().addFreshEntity(attacker), "Could not add the attacker");
			return attacker;
		}

		// the attacker's last hit is the user's vanilla melee attack
		boolean fistHitLanded() {
			DamageSource last = attacker != null ? attacker.getLastDamageSource() : null;
			return last != null && last.is(DamageTypes.PLAYER_ATTACK) && last.getEntity() == user;
		}

		@Override
		public void close() {
			component.setAction(null, SyncType.NO_SYNC);
			user.removeAllEffects();
			user.discard();
			if (attacker != null) {
				attacker.discard();
			}
		}
	}
}
