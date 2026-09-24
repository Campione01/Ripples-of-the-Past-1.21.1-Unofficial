package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.UUID;

import rotp.core.core.JojoMod;
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
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonSkill;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 MCUtil.isHandFree / areHandsFree counted the Gloves and Bubble Gloves (GlovesItem.openFingers) as a free hand,
 * so a Hamon user holding them could heal, charge S.Y.O., land its Hamon hit and use the barrages. 1.16
 * ModPillarmanActions: Enhanced Senses ignored the performer stun (usable in stone form), and Small Sandstorm is
 * swingHand without withUserPunch, so Action.onPerform reset the attack strength.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonGlovesPillarmanFlagGameTests {
	private static final short KEY = 9;

	private HamonGlovesPillarmanFlagGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void glovesCountAsFreeHandForHamonHealing(GameTestHelper helper) {
		for (ItemStack gloves : new ItemStack[] {
				new ItemStack(ModItems.GLOVES.get()), new ItemStack(ModItems.BUBBLE_GLOVES.get()) }) {
			try (Fixture f = Fixture.hamon(helper, "GlovesHealing", ModHamonSkills.HEALING.get())) {
				EntityActionAbility healing = f.ability("hamon_healing");
				f.user.setItemInHand(InteractionHand.MAIN_HAND, gloves.copy());
				helper.assertTrue(healing.checkSpecificConditions(f.power).isPositive(),
						"1.16 Hamon Healing started with " + gloves + " in the main hand");
				EntityActionInstance action = f.start("hamon_healing", true);
				f.tick(5);
				helper.assertTrue(f.component.getAction() == action && !action.isOver()
								&& action.getPhase() == ActionPhase.PERFORM,
						"the held check ended Hamon Healing with " + gloves + " in the main hand");
				f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
				helper.assertTrue(!healing.checkSpecificConditions(f.power).isPositive(),
						"Hamon Healing started with a sword in the main hand");
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void glovesKeepSyoChargeAndItsHamonHit(GameTestHelper helper) {
		float glovesDamage = syoPunchDamage(helper, "GlovesSyo", new ItemStack(ModItems.GLOVES.get()), true);
		// Swapping a plain item in after the release loses only the Hamon part: the 1-damage melee hit still lands.
		float stickDamage = syoPunchDamage(helper, "StickSyo", new ItemStack(Items.STICK), false);
		helper.assertTrue(glovesDamage > 3.0F,
				"the S.Y.O. punch with gloves lost its Hamon hit: damage=" + glovesDamage);
		helper.assertTrue(stickDamage > 0.0F && stickDamage < 2.0F,
				"a filled hand must lose only the S.Y.O. Hamon hit: damage=" + stickDamage);
		helper.succeed();
	}

	private static float syoPunchDamage(GameTestHelper helper, String name, ItemStack held, boolean holdFromStart) {
		try (Fixture f = Fixture.hamon(helper, name, ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE.get())) {
			// Undead, so the Hamon part lands at full strength (a living mob takes a fifth).
			Zombie target = EntityType.ZOMBIE.create(helper.getLevel());
			helper.assertTrue(target != null, "Could not create the S.Y.O. target");
			Vec3 targetPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 4)));
			target.moveTo(targetPos.x, targetPos.y, targetPos.z);
			target.setNoAi(true);
			target.setNoGravity(true);
			target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
			target.setHealth(1000.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(target), "Could not add the S.Y.O. target");
			try {
				f.component.entityAim.setTarget(new ActionTarget(target));
				if (holdFromStart) {
					f.user.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
				}
				EntityActionAbility syo = f.ability("sunlight_yellow_overdrive");
				helper.assertTrue(syo.checkSpecificConditions(f.power).isPositive(),
						"S.Y.O. could not start with " + f.user.getMainHandItem() + " in the main hand");
				EntityActionInstance action = f.start("sunlight_yellow_overdrive", true);
				f.tick(12);
				helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.WINDUP,
						"the held check dropped the S.Y.O. charge with " + f.user.getMainHandItem() + " in the main hand");
				setAttackStrengthTicker(f.user, 100);
				AbilityInput.keyRelease(KEY, f.user);
				helper.assertTrue(action.getPhase() == ActionPhase.PERFORM, "the released S.Y.O. charge did not fire");
				if (!holdFromStart) {
					f.user.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
				}
				float before = target.getHealth();
				f.tick(6);
				return before - target.getHealth();
			}
			finally {
				target.discard();
			}
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void glovesCountAsFreeHandsForHamonTechniques(GameTestHelper helper) {
		try (Fixture f = Fixture.hamon(helper, "GlovesBarrages", ModHamonSkills.OVERDRIVE_BARRAGE.get())) {
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GLOVES.get()));
			f.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.BUBBLE_GLOVES.get()));
			for (String name : new String[] { "overdrive_barrage", "sunlight_yellow_overdrive_barrage",
					"scarlet_overdrive", "zoom_punch" }) {
				helper.assertTrue("positive".equals(reason(f.ability(name), f)),
						"1.16 " + name + " counted gloves as free hands: " + reason(f.ability(name), f));
			}
			// Sendo Overdrive then asks for a block target; the hand check must not be what refuses it.
			helper.assertTrue(!reason(f.ability("sendo_overdrive"), f).endsWith(".hand"),
					"1.16 Sendo Overdrive counted gloves as a free hand");
			f.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
			for (String name : new String[] { "overdrive_barrage", "sunlight_yellow_overdrive_barrage" }) {
				helper.assertTrue(reason(f.ability(name), f).endsWith(".hands"),
						name + " started with a sword in the off hand");
			}
			helper.assertTrue(reason(f.ability("scarlet_overdrive"), f).endsWith(".hand"),
					"Scarlet Overdrive started with a sword in the off hand");
		}
		helper.succeed();
	}

	private static String reason(EntityActionAbility ability, Fixture f) {
		ConditionCheck check = ability.checkSpecificConditions(f.power);
		if (check.isPositive()) {
			return "positive";
		}
		Component warning = check.getWarning();
		return warning != null && warning.getContents() instanceof TranslatableContents translatable
				? translatable.getKey() : "negative";
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void pillarmanEnhancedSensesHoldsThroughStoneFormStun(GameTestHelper helper) {
		try (Fixture f = Fixture.pillarman(helper, "PillarmanSenses")) {
			PillarmanData data = PlayerPower.getPowerData(f.user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
			data.setEvolutionStage(2);
			data.setEnergy(f.user, data.getMaxEnergy(f.user));
			data.setStoneFormEnabled(true);
			f.user.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 200));
			helper.assertTrue(ModStatusEffects.isStunned(f.user), "the stone form fixture is not stunned");

			EntityActionAbility senses = f.ability("pillarman_enhanced_senses");
			helper.assertTrue(senses.isAbilityAvailable(f.power), "Enhanced Senses is not available at stage 2");
			helper.assertTrue(senses.checkConditions(f.power).isPositive(),
					"1.16 Enhanced Senses ignored the performer stun, so it could be used in stone form");
			helper.assertTrue(!f.ability("pillarman_heavy_punch").checkMainModLogicConditions(f.power).isPositive(),
					"the stun did not refuse an action 1.16 did not exempt");

			EntityActionInstance action = f.start("pillarman_enhanced_senses", true);
			f.tick(6);
			helper.assertTrue(f.component.getAction() == action && !action.isOver()
							&& action.getPhase() == ActionPhase.PERFORM,
					"a stun ended the held Enhanced Senses");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void pillarmanSmallSandstormResetsAttackStrength(GameTestHelper helper) {
		try (Fixture f = Fixture.pillarman(helper, "PillarmanSandstorm")) {
			PillarmanData data = PlayerPower.getPowerData(f.user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
			data.setEvolutionStage(2);
			data.setMode(PillarmanMode.WIND);
			data.setEnergy(f.user, data.getMaxEnergy(f.user));

			setAttackStrengthTicker(f.user, 100);
			EntityActionInstance regen = f.start("pillarman_regeneration", false);
			f.tickUntilOver(regen, 10);
			helper.assertTrue(getAttackStrengthTicker(f.user) >= 100,
					"Regeneration (no 1.16 swingHand) reset the attack strength");

			EntityActionInstance sandstorm = f.start("pillarman_small_sandstorm", false);
			f.tickUntilOver(sandstorm, 10);
			helper.assertTrue(getAttackStrengthTicker(f.user) <= 1,
					"performing Small Sandstorm (1.16 swingHand) did not reset the attack strength");
		}
		helper.succeed();
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

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final PlayerPower power;
		private final LivingComponentAction component;

		private Fixture(GameTestHelper helper, String name) {
			this.helper = helper;
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.setPos(origin.x, origin.y, origin.z);
			user.setYRot(0.0F);
			user.setXRot(0.0F);
			user.setYHeadRot(0.0F);
			user.getAbilities().instabuild = false;
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the test player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			component = LivingComponentAction.getComponent(user);
		}

		private static Fixture hamon(GameTestHelper helper, String name, HamonSkill skill) {
			Fixture f = new Fixture(helper, name);
			f.power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(f.user, ModPlayerPowers.HAMON).orElseThrow();
			// a starting skill (Healing) comes with the power, and learnSkill reports only a change
			hamon.learnSkill(skill);
			helper.assertTrue(hamon.isSkillLearned(skill), "Could not grant " + skill);
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			return f;
		}

		private static Fixture pillarman(GameTestHelper helper, String name) {
			Fixture f = new Fixture(helper, name);
			f.power.setPowerType(ModPlayerPowers.PILLAR_MAN.get());
			return f;
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
				EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
				input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, PowerClass.PLAYER_POWER, action));
			}
			return action;
		}

		private void tick(int count) {
			// Drive the real action lifecycle without entity physics or passive power regeneration.
			for (int tick = 0; tick < count; tick++) {
				user.tickCount++;
				component.tick();
			}
		}

		private void tickUntilOver(EntityActionInstance action, int maxTicks) {
			for (int tick = 0; tick < maxTicks && !action.isOver() && component.getAction() == action; tick++) {
				tick(1);
			}
			helper.assertTrue(action.isOver() || component.getAction() != action,
					"the action did not finish in " + maxTicks + " ticks");
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			component.setAction(null, SyncType.NO_SYNC);
			user.removeAllEffects();
			user.discard();
		}
	}
}
