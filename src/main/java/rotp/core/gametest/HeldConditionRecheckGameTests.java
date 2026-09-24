package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.HamonSendoOverdriveEntity;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 PowerBaseImpl.tickHeldAction re-ran checkRequirements on every tick of a held action (the key still down):
 * a failed check (a filled hand, no soap, a stun, a lost Stand part) ended the hold with stopHeldAction(false), which fired nothing
 * except Sendo Overdrive's wave. StandEntityMeleeBarrage.stopOnHeavyAttack let a heavy attack stop a barrage, and
 * Action.onPerform reset a player's attack strength for a swingHand technique.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeldConditionRecheckGameTests {
	private static final short KEY = 7;

	private HeldConditionRecheckGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void filledHandDropsSyoChargeAndRefunds(GameTestHelper helper) {
		try (HamonFixture f = new HamonFixture(helper, "HeldSyoHand", ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE.get())) {
			float energy = f.hamon.getEnergy();
			EntityActionInstance action = f.start("sunlight_yellow_overdrive", true);
			f.tick(12);
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.WINDUP,
					"S.Y.O. is not charging");
			helper.assertTrue(f.hamon.getEnergy() < energy, "S.Y.O. charge spent no energy");
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			f.tick(1);
			helper.assertTrue(action.isOver() && f.component.getAction() == null,
					"a filled main hand did not end the held S.Y.O. charge");
			assertClose(helper, f.hamon.getEnergy(), energy, "the dropped S.Y.O. charge did not refund its energy");

			// An action no key holds (set by code, as the auto-guard is) is not re-checked.
			EntityActionInstance unheld = f.start("sunlight_yellow_overdrive", false);
			f.tick(3);
			helper.assertTrue(f.component.getAction() == unheld && unheld.getPhase() == ActionPhase.WINDUP,
					"an action no key holds was stopped by the held check");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void stunDropsHeldHamonCharge(GameTestHelper helper) {
		try (HamonFixture f = new HamonFixture(helper, "HeldSyoStun", ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE.get())) {
			EntityActionInstance action = f.start("sunlight_yellow_overdrive", true);
			f.tick(5);
			helper.assertTrue(action.getPhase() == ActionPhase.WINDUP, "S.Y.O. is not charging");
			f.user.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
			f.tick(1);
			helper.assertTrue(action.isOver() && f.component.getAction() == null,
					"a stun did not end the held S.Y.O. charge");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void lostSoapDropsBubbleBarrierCharge(GameTestHelper helper) {
		try (HamonFixture f = new HamonFixture(helper, "HeldBarrierSoap", ModHamonSkills.BUBBLE_BARRIER.get())) {
			f.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SOAP.get()));
			EntityActionInstance action = f.start("bubble_barrier", true);
			f.tick(5);
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.WINDUP,
					"the Bubble Barrier is not charging");
			f.user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			f.tick(1);
			helper.assertTrue(action.isOver() && f.component.getAction() == null,
					"losing the soap did not end the held Bubble Barrier charge");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void filledHandEndsHamonHealing(GameTestHelper helper) {
		try (HamonFixture f = new HamonFixture(helper, "HeldHealingHand", ModHamonSkills.HEALING.get())) {
			EntityActionAbility healing = f.ability("hamon_healing");
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			helper.assertTrue(!healing.checkSpecificConditions(f.power).isPositive(),
					"1.16 Hamon Healing needed a free main hand to start");
			f.user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			helper.assertTrue(healing.checkSpecificConditions(f.power).isPositive(), "Hamon Healing cannot start");
			EntityActionInstance action = f.start("hamon_healing", true);
			f.tick(5);
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.PERFORM,
					"Hamon Healing is not being held");
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			f.tick(1);
			helper.assertTrue(action.isOver() && f.component.getAction() == null,
					"a filled main hand did not end the held Hamon Healing");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void failedCheckStillSendsSendoWave(GameTestHelper helper) {
		try (HamonFixture f = new HamonFixture(helper, "HeldSendoHand", ModHamonSkills.SENDO_OVERDRIVE.get())) {
			for (int y = 2; y <= 6; y++) {
				helper.setBlock(new BlockPos(2, y, 5), Blocks.STONE.defaultBlockState());
			}
			AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12.0D);
			int wavesBefore = helper.getLevel().getEntitiesOfClass(HamonSendoOverdriveEntity.class, area).size();
			EntityActionInstance action = f.start("sendo_overdrive", true);
			f.tick(5);
			helper.assertTrue(action.getPhase() == ActionPhase.WINDUP, "Sendo Overdrive is not charging");
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			f.tick(1);
			helper.assertTrue(action.isOver() || action.getPhase() == ActionPhase.PERFORM,
					"a failed check did not end the Sendo Overdrive hold");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(HamonSendoOverdriveEntity.class, area).size() > wavesBefore,
					"1.16 Sendo Overdrive sent its wave on any stop of the hold, a failed check included");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void swingHandTechniqueResetsAttackStrength(GameTestHelper helper) {
		try (HamonFixture f = new HamonFixture(helper, "SwingHandCutter", ModHamonSkills.HAMON_CUTTER.get())) {
			helper.assertTrue(f.ability("hamon_cutter").resetsAttackStrengthOnPerform(),
					"Hamon Cutter (1.16 swingHand) must reset the attack strength");
			helper.assertTrue(!f.ability("hamon_overdrive").resetsAttackStrengthOnPerform(),
					"Hamon Overdrive punches with the user (1.16 withUserPunch) and must not reset it");
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOAP.get()));
			setAttackStrengthTicker(f.user, 100);
			EntityActionInstance action = f.start("hamon_cutter", false);
			for (int tick = 0; tick < 12 && action.getPhase() != ActionPhase.PERFORM && !action.isOver(); tick++) {
				f.tick(1);
			}
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM, "Hamon Cutter did not reach its perform phase");
			helper.assertTrue(getAttackStrengthTicker(f.user) >= 100, "the attack strength was reset before the technique fired");
			// The perform phase's first tick is 1.16's onPerform.
			f.tick(1);
			helper.assertTrue(!action.isOver() && action.getPhase() == ActionPhase.PERFORM, "Hamon Cutter did not perform");
			helper.assertTrue(getAttackStrengthTicker(f.user) <= 1,
					"performing Hamon Cutter did not reset the attack strength");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void heavyAttackAndStunStopStandBarrage(GameTestHelper helper) {
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HeldBarrageStop"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		try {
			helper.assertTrue(standType != null, "Missing Star Platinum Stand type");
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add barrage player");
			power = PowerClass.STAND.attachGet(user);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
			helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
					"Could not grant Star Platinum: " + inserted.status());
			helper.assertTrue(standType.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Star Platinum entity is missing");
			LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
			Ability barrage = power.getAbility("barrage");
			helper.assertTrue(barrage instanceof EntityActionType, "Star Platinum barrage is not an entity action");

			EntityActionInstance hit = startBarrage(helper, (EntityActionType) barrage, user, stand, standAction);
			EntityActionAbility.onHitByHeavyAttack(stand);
			helper.assertTrue(hit.getPhase() == ActionPhase.RECOVERY,
					"a heavy attack that hurt the Stand did not send its barrage into recovery");

			EntityActionInstance stunned = startBarrage(helper, (EntityActionType) barrage, user, stand, standAction);
			holdByKey(user, stunned, PowerClass.STAND);
			stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
			standAction.tick();
			helper.assertTrue(stunned.getPhase() == ActionPhase.RECOVERY,
					"a stun on the Stand did not end the held barrage");
			stand.removeEffect(ModStatusEffects.STUN);
			helper.succeed();
		}
		finally {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	// 1.16 StandAction.checkConditions (partsRequired) ran on every held tick and before a release fired.
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void lostStandArmsEndHeldBarrage(GameTestHelper helper) {
		try (StandFixture f = new StandFixture(helper, "HeldBarrageArms", "star_platinum")) {
			EntityActionAbility barrage = f.ability("barrage");
			EntityActionInstance held = startBarrage(helper, barrage, f.user, f.stand, f.standAction);
			holdByKey(f.user, held, PowerClass.STAND);
			f.standAction.tick();
			helper.assertTrue(held.getPhase() == ActionPhase.PERFORM && barrage.canFireReleasedHold(held),
					"the held barrage did not go on while the Stand had its arms" + f.state(barrage, held));
			f.instance.removePart(StandPart.ARMS);
			helper.assertTrue(!barrage.canFireReleasedHold(held),
					"the release recheck passed after the Stand lost its arms" + f.state(barrage, held));
			f.standAction.tick();
			helper.assertTrue(held.getPhase() == ActionPhase.RECOVERY,
					"losing the Stand's arms did not end the held barrage" + f.state(barrage, held));
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void lostStandBodyDropsCrossfireCharge(GameTestHelper helper) {
		try (StandFixture f = new StandFixture(helper, "HeldCrossfireBody", "magicians_red")) {
			EntityActionAbility crossfire = f.ability("crossfire_hurricane");
			EntityActionInstance charge = crossfire.initActionOnAbilityUse(helper.getLevel(), f.user, f.stand, null);
			f.standAction.setAction(charge, f.user, SyncType.NO_SYNC);
			holdByKey(f.user, charge, PowerClass.STAND);
			f.tick(5);
			// The phase tick shows the charge really ticked (it starts in BUTTON_CHARGE).
			helper.assertTrue(f.standAction.getAction() == charge && charge.getPhase() == ActionPhase.BUTTON_CHARGE
					&& charge.getPhaseTick() > 0, "Crossfire Hurricane is not charging" + f.state(crossfire, charge));
			f.instance.removePart(StandPart.MAIN_BODY);
			f.tick(1);
			helper.assertTrue(charge.isOver() && f.standAction.getAction() == null,
					"losing the Stand's body did not drop the held Crossfire Hurricane charge" + f.state(crossfire, charge));
			// Releasing after the drop fires nothing, nor does the charge end (20 ticks) come.
			charge.onKeyRelease(f.user);
			f.tick(25);
			helper.assertTrue(charge.isOver() && f.standAction.getAction() == null,
					"the dropped Crossfire Hurricane charge still fired" + f.state(crossfire, charge));
			helper.succeed();
		}
	}

	private static EntityActionInstance startBarrage(GameTestHelper helper, EntityActionType barrage,
			Player user, StandEntity stand, LivingComponentAction standAction) {
		EntityActionInstance action = barrage.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		standAction.setAction(action, user, SyncType.NO_SYNC);
		for (int tick = 0; tick < 10 && action.getPhase() != ActionPhase.PERFORM; tick++) {
			standAction.tick();
		}
		helper.assertTrue(standAction.getAction() == action && action.getPhase() == ActionPhase.PERFORM,
				"the barrage did not start");
		return action;
	}

	private static void holdByKey(LivingEntity user, EntityActionInstance action, PowerClass<?> powerClass) {
		EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, powerClass, action));
	}

	private static Field attackStrengthTicker() {
		try {
			Field field = LivingEntity.class.getDeclaredField("attackStrengthTicker");
			field.setAccessible(true);
			return field;
		}
		catch (NoSuchFieldException e) {
			throw new AssertionError("LivingEntity.attackStrengthTicker is missing", e);
		}
	}

	private static void setAttackStrengthTicker(LivingEntity entity, int ticks) {
		try {
			attackStrengthTicker().setInt(entity, ticks);
		}
		catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	private static int getAttackStrengthTicker(LivingEntity entity) {
		try {
			return attackStrengthTicker().getInt(entity);
		}
		catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	private static void assertClose(GameTestHelper helper, float actual, float expected, String message) {
		helper.assertTrue(Math.abs(actual - expected) < 0.01F, message + ": expected=" + expected + ", actual=" + actual);
	}

	private static final class HamonFixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final PlayerPower power;
		private final HamonData hamon;
		private final LivingComponentAction component;

		private HamonFixture(GameTestHelper helper, String name, rotp.core.impl.powers.hamon.HamonSkill skill) {
			this.helper = helper;
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.setPos(origin.x, origin.y, origin.z);
			user.setYRot(0.0F);
			user.setXRot(0.0F);
			user.setYHeadRot(0.0F);
			user.getAbilities().instabuild = false;
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add held-check test player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			// a starting skill (Healing) comes with the power, and learnSkill reports only a change
			hamon.learnSkill(skill);
			helper.assertTrue(hamon.isSkillLearned(skill), "Could not grant " + skill);
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			component = LivingComponentAction.getComponent(user);
		}

		private EntityActionAbility ability(String name) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered " + name);
			return (EntityActionAbility) found;
		}

		private EntityActionInstance start(String name, boolean heldByKey) {
			EntityActionInstance action = ability(name).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			if (heldByKey) {
				holdByKey(user, action, PowerClass.PLAYER_POWER);
			}
			return action;
		}

		private void tick(int count) {
			// Drive the real action lifecycle without entity physics or passive Hamon regeneration.
			for (int tick = 0; tick < count; tick++) {
				user.tickCount++;
				component.tick();
			}
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			component.setAction(null, SyncType.NO_SYNC);
			user.removeAllEffects();
			user.discard();
		}
	}

	private static final class StandFixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final StandType standType;
		private StandPower power;
		private StandInstance instance;
		private StandEntity stand;
		private LivingComponentAction standAction;

		private StandFixture(GameTestHelper helper, String name, String standId) {
			this.helper = helper;
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			try {
				helper.assertTrue(standType != null, "Missing Stand type " + standId);
				Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
				user.moveTo(userPos.x, userPos.y, userPos.z);
				helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add Stand part test player");
				power = PowerClass.STAND.attachGet(user);
				StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
				helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
						"Could not grant " + standId + ": " + inserted.status());
				instance = power.getStandInstance().orElse(null);
				helper.assertTrue(instance != null, "Granted " + standId + " has no Stand instance");
				helper.assertTrue(standType.summon(user, power), "Could not summon " + standId);
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "Summoned " + standId + " entity is missing");
				standAction = LivingComponentAction.getComponent(stand);
				// The summon lock skips the action tick (StandEntity.tick counts it down, which the test does not run):
				// Magician's Red (speed 11) has 7 ticks of it.
				stand.summonLockTicks = 0;
				// A directional barrage pays stamina every tick and stops at 0; a new power starts with none.
				power.setStamina(power.getMaxStamina());
			}
			catch (RuntimeException | Error e) {
				close();
				throw e;
			}
		}

		private EntityActionAbility ability(String name) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found instanceof EntityActionAbility, "Missing Stand ability " + name);
			return (EntityActionAbility) found;
		}

		private void tick(int count) {
			for (int tick = 0; tick < count; tick++) {
				standAction.tick();
			}
		}

		// Failure detail: what the held recheck sees and what would skip it.
		private String state(EntityActionAbility ability, EntityActionInstance action) {
			ConditionCheck check = ability.checkHeldActionConditions(action, power);
			String reason = check.getWarning() != null ? check.getWarning().getString() : "-";
			return " [phase=" + action.getPhase() + " phaseTick=" + action.getPhaseTick()
					+ " current=" + (standAction.getAction() == action)
					+ " parts=" + instance.getAllParts()
					+ " heldCheck=" + check.isPositive() + "/" + reason
					+ " isActionHeld=" + ability.isActionHeld(action)
					+ " heldByKey=" + AbilityInput.isHeldByKey(user, action)
					+ " releaseCheck=" + ability.canFireReleasedHold(action)
					+ " summonLock=" + stand.summonLockTicks
					+ " stunned=" + ModStatusEffects.isStunned(stand)
					+ " stamina=" + power.getStamina() + "]";
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			if (instance != null) {
				for (StandPart part : StandPart.values()) {
					instance.addPart(part);
				}
			}
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
