package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
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
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.abilities.HamonRebuffOverdriveAbility.HamonRebuffOverdrive;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonRebuffOverdrive: the user stands still (getWalkSpeed 0); only a melee hit from the front is countered, a
 * Stand's only with Hermit Purple out; any other hit while charging gets Hamon Protection's cut even with Protection
 * off; the client stopped the action once it reached its recovery (onWASDInput; didAttack was never set on a client).
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
			// 1.16 doCounterAttack: Joseph's line, on the 200 tick repeat guard
			helper.assertTrue(!f.user.getData(ModDataAttachmentTypes.PLAYER_VOICE_LINES.get())
					.checkNotRepeatingVoiceLine(ModSoundEvents.JOSEPH_REBUFF_OVERDRIVE, 200),
					"1.16 said the Rebuff Overdrive line on a counter");
			// 1.16 onWASDInput ran on the client, which never learned didAttack: the strike ran its 10 ticks, taking no
			// damage, and the action ended when its recovery would start
			f.tick(HamonRebuffOverdrive.PERFORM_TICKS - 1);
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.PERFORM
					&& action.userWalkSpeed == 0.0F,
					"1.16 kept striking for 10 ticks after the counter: running=" + (f.component.getAction() == action)
					+ " phase=" + action.getPhase() + " walk speed=" + action.userWalkSpeed);
			f.user.invulnerableTime = 0;
			f.user.hurt(helper.getLevel().damageSources().playerAttack(attacker), 4.0F);
			helper.assertTrue(f.user.getHealth() == before, "1.16 took no damage while striking: health " + f.user.getHealth() + " of " + before);
			f.tick(1);
			helper.assertTrue(f.component.getAction() == action && !action.isOver(),
					"1.16 ended Rebuff Overdrive on the tick after its 10 striking ticks, not sooner");
			f.tick(1);
			helper.assertTrue(f.component.getAction() != action || action.isOver(),
					"1.16 ended Rebuff Overdrive when the recovery would start");
			helper.succeed();
		}
	}

	// 1.16 playerTick + onWASDInput: 14 ticks of charge, 10 of strike, and the client ended the action on the next
	// tick, the one that would start the recovery, whether or not anything was hit
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void whiffEndsWhenTheRecoveryWouldStart(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			EntityActionInstance action = f.start();
			f.tick(HamonRebuffOverdrive.WINDUP_TICKS);
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.PERFORM,
					"1.16 struck after 14 ticks of charge: phase=" + action.getPhase());
			f.tick(HamonRebuffOverdrive.PERFORM_TICKS);
			helper.assertTrue(f.component.getAction() == action && !action.isOver() && action.userWalkSpeed == 0.0F,
					"1.16 kept the user still for the 24 ticks of charge and strike: running="
					+ (f.component.getAction() == action) + " over=" + action.isOver() + " walk speed=" + action.userWalkSpeed);
			f.tick(1);
			helper.assertTrue(f.component.getAction() != action || action.isOver(),
					"1.16 ended a Rebuff Overdrive that hit nothing on its 25th tick, with no 16 tick recovery");
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

	// 1.16 punch sent damage * efficiency to dealHamonDamage with no efficiency check: at an efficiency of 0 a mob still
	// takes the hit of 0, so the counter's knockback of 2 and its Hamon Shock follow
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void counterAtNoEfficiencyStillLandsItsHamonOnAMob(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			f.hamon.learnSkill(ModHamonSkills.HAMON_SHOCK.get());
			helper.assertTrue(f.hamon.isSkillLearned(ModHamonSkills.HAMON_SHOCK.get()), "Could not grant Hamon Shock");
			f.hamon.setEnergy(0.0F);
			f.hamon.setBreathStability(0.0F);
			float efficiency = f.hamon.getActionEfficiency(1200.0F, true, ModHamonSkills.REBUFF_OVERDRIVE.get(), f.user);
			helper.assertTrue(efficiency == 0.0F, "premise: the user's Rebuff Overdrive efficiency is " + efficiency + ", not 0");
			EntityActionInstance action = f.start();
			f.tick(COUNTER_TIMING_TICK);
			Pig pig = EntityType.PIG.create(helper.getLevel());
			helper.assertTrue(pig != null, "Could not create the pig");
			pig.moveTo(f.pos.x, f.pos.y, f.pos.z + 1.5D, 180.0F, 0.0F);
			List<Float> knockbacks = new ArrayList<>();
			Consumer<LivingKnockBackEvent> knockback = event -> {
				if (event.getEntity() == pig) {
					knockbacks.add(event.getOriginalStrength());
				}
			};
			try {
				helper.assertTrue(helper.getLevel().addFreshEntity(pig), "Could not add the pig");
				NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, knockback);
				boolean countered = ((HamonRebuffOverdrive) action).counterAttack(pig);
				helper.assertTrue(countered, "The counter did not strike");
				helper.assertTrue(knockbacks.contains(2.0F) && pig.hasEffect(ModStatusEffects.HAMON_SHOCK),
						"1.16: a counter at an efficiency of 0 still lands its Hamon hit of 0 on a mob, with the knockback of 2"
								+ " and Hamon Shock: knockbacks=" + knockbacks + " shocked=" + pig.hasEffect(ModStatusEffects.HAMON_SHOCK));
			}
			finally {
				NeoForge.EVENT_BUS.unregister(knockback);
				pig.discard();
			}
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

	/*
	 * 1.16 ContinuousActionInstance took Rebuff Overdrive's cooldown(100) from getCooldown as it started, 0 in Creative,
	 * and its punch set actionCooldown to 0 for a counter and to half for the strike at the end of the charge (:372), set
	 * as the action stopped only if above 0. A husk's hit inside the counter window: a Survival user counters it and is
	 * left no cooldown; a Creative user takes no hit at all (Player.hurt refuses a mob's hit to an invulnerable player), so
	 * the charge runs on. Then the strike at the husk the user aims at as the charge ends: 50 ticks in Survival, none in
	 * Creative. Each read once the action has stopped, when its recovery would start. 1.16's setCooldownTimer made the
	 * value set its total too (PowerBaseImpl.updateCooldownTimer(action, value, value)), so the slot's overlay started full.
	 */
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void aMobsHitInTheCounterWindowAndTheEndStrikeLeave116sCooldownInSurvivalAndCreative(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (GameType mode : new GameType[] { GameType.SURVIVAL, GameType.CREATIVE }) {
			boolean creative = mode == GameType.CREATIVE;
			for (boolean mobHits : new boolean[] { true, false }) {
				String row = mode.getName() + (mobHits ? " user hit by a husk in the counter window" : " user striking a husk as the charge ends");
				try (Fixture f = new Fixture(helper, mode)) {
					Husk husk = EntityType.HUSK.create(helper.getLevel());
					helper.assertTrue(husk != null, "Could not create the husk");
					husk.setNoAi(true);
					husk.moveTo(f.pos.x, f.pos.y, f.pos.z + 1.5D, 180.0F, 0.0F);
					try {
						helper.assertTrue(helper.getLevel().addFreshEntity(husk), "Could not add the husk");
						helper.assertTrue(f.user.getAbilities().instabuild == creative,
								"setup: the " + mode.getName() + " user's abilities are not its game mode's");
						f.component.entityAim.setTarget(new ActionTarget(husk));
						EntityActionInstance action = f.start();
						f.tick(COUNTER_TIMING_TICK);
						helper.assertTrue(((HamonRebuffOverdrive) action).isCounterTiming(), "setup: " + row + ": not in the counter window");
						float userHealth = f.user.getHealth();
						if (mobHits) {
							boolean hurt = f.user.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F);
							boolean countered = action.getPhase() == ActionPhase.PERFORM && husk.getHealth() < husk.getMaxHealth();
							if (creative ? hurt || countered : !countered || f.user.getHealth() != userHealth) {
								wrong.add(row + ": hurt " + hurt + ", phase " + action.getPhase() + ", husk " + husk.getHealth()
										+ ", user " + f.user.getHealth() + " of " + userHealth + "; 1.16 "
										+ (creative ? "let no mob hit a Creative player" : "countered it"));
								continue;
							}
						}
						for (int tick = 0; tick < 20 && husk.getHealth() >= husk.getMaxHealth(); tick++) {
							f.tick(1);
						}
						for (int tick = 0; tick < 20 && f.component.getAction() == action; tick++) {
							f.tick(1);
						}
						int cooldown = f.hamon.getAbilityCooldown("rebuff_overdrive");
						float drawn = f.hamon.getAbilityCooldownRatio("rebuff_overdrive", 0.0F);
						int expected = creative || mobHits ? 0 : 50;
						if (husk.getHealth() >= husk.getMaxHealth() || f.component.getAction() == action || cooldown != expected
								|| drawn != fullOverlay(expected)) {
							wrong.add(row + ": the husk at " + husk.getHealth() + " of " + husk.getMaxHealth()
									+ ", the action stopped " + (f.component.getAction() != action) + ", a cooldown of "
									+ cooldown + " drawn at " + drawn + ", 1.16's " + expected + " drawn at " + fullOverlay(expected));
						}
					}
					finally {
						husk.discard();
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	private static final int HANDS_TAKEN_TICK = 5;
	private static final int PRESSED_AGAIN_TICK = 7;

	/*
	 * 1.16 ContinuousActionInstance set the cooldown left as the action stopped, whatever stopped it (onStop): Rebuff
	 * Overdrive took cooldown(100) as it started (0 in Creative), so one that struck nothing left the full 100 as it ended
	 * at its recovery's start, so did one stopped in its windup from outside (another action, death), and one whose hands
	 * were taken in its windup was cancelled there (playerTick: cancel), leaving (int) (100 x (5 / 14)^2) = 12 at its 5th
	 * tick; pressed again at its 7th it cancelled with (int) (100 x (7 / 14)^2) = 25. None in Creative. setCooldownTimer
	 * made each value its own total, so the slot's overlay started full whatever the amount.
	 */
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void aRebuffOverdriveThatStrikesNothingOrIsStoppedLeaves116sCooldownInSurvivalAndCreative(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		String[] rows = { "striking nothing", "stopped from outside in its windup", "its hands taken in its windup",
				"pressed again in its windup" };
		int[] survival = { 100, 100, 12, 25 };
		for (GameType mode : new GameType[] { GameType.SURVIVAL, GameType.CREATIVE }) {
			for (int row = 0; row < rows.length; row++) {
				try (Fixture f = new Fixture(helper, mode)) {
					EntityActionInstance action = f.start();
					if (row == 0) {
						for (int tick = 0; tick < 40 && f.component.getAction() == action; tick++) {
							f.tick(1);
						}
					}
					else if (row == 3) {
						f.tick(PRESSED_AGAIN_TICK);
						((HamonRebuffOverdrive) action).cancel();
					}
					else {
						f.tick(HANDS_TAKEN_TICK);
						if (row == 1) {
							f.component.setAction(null, SyncType.NO_SYNC);
						}
						else {
							f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
							f.tick(1);
						}
					}
					int cooldown = f.hamon.getAbilityCooldown("rebuff_overdrive");
					float drawn = f.hamon.getAbilityCooldownRatio("rebuff_overdrive", 0.0F);
					int expected = mode == GameType.CREATIVE ? 0 : survival[row];
					boolean stopped = f.component.getAction() != action || action.isOver();
					if (!stopped || cooldown != expected || drawn != fullOverlay(expected)) {
						wrong.add(mode.getName() + " Rebuff Overdrive " + rows[row] + ": stopped " + stopped
								+ ", a cooldown of " + cooldown + " drawn at " + drawn + ", 1.16's " + expected + " drawn at "
								+ fullOverlay(expected));
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16 set the cooldown left as Rebuff Overdrive stopped: " + String.join("; ", wrong));
		helper.succeed();
	}

	// the slot overlay's fill right after the cooldown was set: full for any cooldown, as 1.16's setCooldownTimer drew it
	private static float fullOverlay(int cooldown) {
		return cooldown > 0 ? 1.0F : 0.0F;
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
			this(helper, GameType.SURVIVAL);
		}

		Fixture(GameTestHelper helper, GameType mode) {
			this.helper = helper;
			pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user = GameTestPlayers.makeServerMockPlayer(helper, mode);
			user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			if (mode == GameType.CREATIVE) {
				GameType.CREATIVE.updatePlayerAbilities(user.getAbilities());
			}
			else {
				user.getAbilities().invulnerable = false;
			}
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
