package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonSkill;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModParticles;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.network.s2c.TrHamonParticlesPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonOverdrive and its subclasses HamonMetalSilverOverdrive and HamonMetalSilverOverdriveWeapon.
 * The click performed at once (no hold, no phases). With the Metal Silver Overdrive skill, HamonOverdrive.replaceAction
 * turned the click into the weapon variant (a weapon in the main hand) or the unarmed one (an armoured or armed target);
 * neither was ever a HUD slot (unlocks(action, false)). All three were built withUserPunch(): the player also made its
 * own vanilla attack on the target, after the Hamon hit. The unarmed ones needed a free main hand, and every entity
 * target had to be within 8 blocks (Action.checkRangeAndTarget).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonOverdriveFamilyGameTests {
	private static final short KEY = 31;
	private static final String OVERDRIVE = "hamon_overdrive";
	private static final String BEAT = "hamon_beat";
	private static final String METAL_SILVER = "metal_silver_overdrive";
	private static final String METAL_SILVER_WEAPON = "metal_silver_overdrive_weapon";
	private static final float EPS = 1.0E-4F;
	// feet of a target standing 2 blocks in front of the user
	private static final double NEAR = 2.0D;

	private HamonOverdriveFamilyGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void overdriveTurnsIntoMetalSilverOverdrive(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			Ability overdrive = f.ability(OVERDRIVE);
			Ability beat = f.ability(BEAT);
			Ability metalSilver = f.ability(METAL_SILVER);
			Ability weapon = f.ability(METAL_SILVER_WEAPON);

			f.newTarget(NEAR).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			helper.assertTrue(f.resolve(OVERDRIVE).ability == overdrive,
					"without the Metal Silver Overdrive skill the click must stay Overdrive, got " + f.describe(OVERDRIVE));
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			helper.assertTrue(f.resolve(OVERDRIVE).ability == overdrive && "hand".equals(f.refusal(OVERDRIVE)),
					"without the skill a weapon in the main hand must refuse Overdrive with the hand message, got "
							+ f.describe(OVERDRIVE));
			f.user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

			f.learn(ModHamonSkills.METAL_SILVER_OVERDRIVE.get());
			f.newTarget(NEAR);
			helper.assertTrue(f.resolve(OVERDRIVE).ability == overdrive && f.resolve(OVERDRIVE).conditionCheck.isPositive(),
					"against a target without armour or weapon the click must stay Overdrive, got " + f.describe(OVERDRIVE));

			f.newTarget(NEAR).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			helper.assertTrue(f.resolve(OVERDRIVE).ability == metalSilver && f.resolve(OVERDRIVE).conditionCheck.isPositive(),
					"1.16 HamonOverdrive.replaceAction: with the skill, Overdrive at an armoured target becomes "
							+ METAL_SILVER + ", got " + f.describe(OVERDRIVE));
			helper.assertTrue(f.resolve(BEAT).ability == beat,
					"Metal Silver Overdrive keeps Overdrive Beat as its Shift variation, got " + f.describe(BEAT));

			f.newTarget(NEAR).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			helper.assertTrue(f.resolve(OVERDRIVE).ability == metalSilver,
					"Overdrive at a target holding a weapon becomes " + METAL_SILVER + ", got " + f.describe(OVERDRIVE));

			f.newTarget(NEAR);
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			helper.assertTrue(f.resolve(OVERDRIVE).ability == weapon && f.resolve(OVERDRIVE).conditionCheck.isPositive(),
					"with a weapon in the main hand Overdrive becomes " + METAL_SILVER_WEAPON + " instead of being refused, got "
							+ f.describe(OVERDRIVE));
			helper.assertTrue(f.resolve(BEAT).ability == weapon,
					"1.16: the weapon variant has no Shift variation, so Shift still gives it, got " + f.describe(BEAT));
			f.user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			helper.assertTrue(f.resolve(BEAT).ability == beat, "an empty hand gives Overdrive Beat back, got " + f.describe(BEAT));

			// the click itself: 1000 energy, the armour multiplier and the silver spark
			f.newTarget(NEAR).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			f.ready();
			// taken before the click: a landed hit trains Hamon Strength, which raises the damage of the next one
			float expectedDamage = f.hamonAmount(2.0F * 1.2F
					* f.hamon.getActionEfficiency(1000.0F, true, ModHamonSkills.METAL_SILVER_OVERDRIVE.get(), f.user));
			float energy = f.hamon.getEnergy();
			f.press(OVERDRIVE);
			EntityActionInstance action = f.component.getAction();
			helper.assertTrue(action != null && action.ability == metalSilver,
					"the Overdrive click at an armoured target did not start " + METAL_SILVER + ": "
							+ (action != null ? action.ability.getAbilityId().nameInMoveset() : "no action"));
			f.tickUntilOver();
			assertClose(helper, energy - f.hamon.getEnergy(), 1000.0F, "Metal Silver Overdrive energy cost");
			Hit hamonHit = f.firstHit("hamon");
			helper.assertTrue(hamonHit != null, "the replaced Overdrive dealt no Hamon damage: " + f.hits);
			assertClose(helper, hamonHit.amount, expectedDamage, "Metal Silver Overdrive damage against one armour piece (x1.2)");
			TrHamonParticlesPacket spark = HamonAbilityHelpers.takeLastSparkEmitter();
			helper.assertTrue(spark != null && spark.entityId() == f.target.getId()
					&& spark.particle() == ModParticles.HAMON_SPARK_SILVER.get(),
					"the replaced Overdrive must spark silver: " + spark);

			// 1.16 unlocks(action, false): never a HUD slot, and not offered in the controls editor
			ControlSchemeTemplate template = f.power.getPowerType().makeDefaultControlSchemeTemplate();
			helper.assertTrue(countSlots(template, METAL_SILVER) == 0 && countSlots(template, METAL_SILVER_WEAPON) == 0,
					"Metal Silver Overdrive is still a HUD hotbar slot: " + countSlots(template, METAL_SILVER) + " + "
							+ countSlots(template, METAL_SILVER_WEAPON));
			helper.assertTrue(countSlots(template, OVERDRIVE) == 1 && countSlots(template, BEAT) == 1,
					"Overdrive lost its hotbar slot or its Overdrive Beat variation");
			helper.assertTrue(!metalSilver.addToControlSchemeEditing() && !weapon.addToControlSchemeEditing(),
					"Metal Silver Overdrive is offered as a key of its own in the controls editor");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void overdriveHitLandsOnThePress(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			f.learn(ModHamonSkills.METAL_SILVER_OVERDRIVE.get());
			for (String name : new String[] { OVERDRIVE, METAL_SILVER, METAL_SILVER_WEAPON }) {
				f.scene(name);
				AbilityConditionCheck pressed = f.press(name);
				EntityActionInstance action = f.component.getAction();
				helper.assertTrue(action != null && action.ability == f.ability(name),
						name + " did not start on its click: " + f.describe(pressed));
				f.tick(1);
				helper.assertTrue(f.firstHit("hamon") != null,
						"1.16 performed " + name + " on the click: no Hamon hit after the first action tick (phase="
								+ action.getPhase() + ", phaseTick=" + action.getPhaseTick() + ", hits=" + f.hits + ")");
				helper.assertTrue(action.isOver() && f.component.getAction() == null,
						name + " still holds its user after the hit (phase=" + action.getPhase() + ")");
			}
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void overdriveTargetMustBeWithinEightBlocks(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			f.learn(ModHamonSkills.METAL_SILVER_OVERDRIVE.get());
			for (String name : new String[] { OVERDRIVE, METAL_SILVER, METAL_SILVER_WEAPON }) {
				f.scene(name);
				helper.assertTrue(f.resolve(name).conditionCheck.isPositive(),
						name + " refuses a target 2 blocks away: " + f.describe(name));
				// the aim comes from the client; 1.16 Action.checkRangeAndTarget refused an entity beyond 8 blocks
				f.moveTarget(10.5D);
				float energy = f.hamon.getEnergy();
				AbilityConditionCheck far = f.press(name);
				helper.assertTrue("target_too_far".equals(refusal(far)),
						name + " accepted a target " + f.user.distanceTo(f.target) + " blocks away: " + f.describe(far));
				helper.assertTrue(f.component.getAction() == null && f.hamon.getEnergy() == energy && f.hits.isEmpty(),
						name + " still acted on the far target: " + f.hits);
			}
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void overdriveCarriesTheUsersOwnPunch(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			f.learn(ModHamonSkills.METAL_SILVER_OVERDRIVE.get());
			for (String name : new String[] { OVERDRIVE, METAL_SILVER, METAL_SILVER_WEAPON }) {
				f.scene(name);
				// taken before the click: a landed hit trains Hamon Strength, which raises the damage of the next one
				float expectedDamage = f.hamonAmount(2.0F * multiplier(name)
						* f.hamon.getActionEfficiency(cost(name), true, skill(name), f.user));
				float fist = (float) f.user.getAttributeValue(Attributes.ATTACK_DAMAGE);
				helper.assertTrue(f.user.getAttackStrengthScale(0.0F) == 1.0F && f.user.getLastHurtMob() != f.target,
						"the punch fixture is not at full attack strength");
				f.press(name);
				f.tickUntilOver();
				helper.assertTrue(f.hits.size() == 2 && "hamon".equals(f.hits.get(0).kind) && "punch".equals(f.hits.get(1).kind),
						"1.16 withUserPunch: " + name + " lands its Hamon hit and then the user's own vanilla attack, got " + f.hits);
				// the efficiency is the one of the click, not lowered by the punch's attack strength reset
				assertClose(helper, f.hits.get(0).amount, expectedDamage, name + " Hamon damage at full attack strength");
				assertClose(helper, f.hits.get(1).amount, fist, name + " punch damage at full attack strength");
				helper.assertTrue(f.user.getLastHurtMob() == f.target, name + ": the punch did not mark the target as last hurt");
				helper.assertTrue(f.user.getAttackStrengthScale(0.0F) == 0.0F,
						name + ": the punch did not reset the attack strength: " + f.user.getAttackStrengthScale(0.0F));
			}

			// The punch is the vanilla attack: beyond vanilla reach (3 + 1 blocks) only the Hamon hit lands.
			f.scene(OVERDRIVE);
			f.moveTarget(4.45D);
			helper.assertTrue(!f.user.canInteractWithEntity(f.target, 1.0D) && f.resolve(OVERDRIVE).conditionCheck.isPositive(),
					"the reach fixture is not between vanilla reach and the 4 block range without line of sight: "
							+ f.describe(OVERDRIVE));
			f.press(OVERDRIVE);
			f.tickUntilOver();
			helper.assertTrue(f.hits.size() == 1 && "hamon".equals(f.hits.get(0).kind),
					"beyond vanilla reach Overdrive lands its Hamon hit without the punch, got " + f.hits);
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void metalSilverOverdriveNeedsAFreeMainHand(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			f.learn(ModHamonSkills.METAL_SILVER_OVERDRIVE.get());
			f.newTarget(NEAR).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			helper.assertTrue(f.resolve(METAL_SILVER).conditionCheck.isPositive(),
					"Metal Silver Overdrive refuses an armoured target with an empty hand: " + f.describe(METAL_SILVER));

			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			helper.assertTrue("hand".equals(f.refusal(METAL_SILVER)),
					"1.16 needsFreeMainHand: Metal Silver Overdrive with an item in the main hand must be refused with the hand"
							+ " message, got " + f.describe(METAL_SILVER));
			helper.assertTrue(f.resolve(OVERDRIVE).ability == f.ability(METAL_SILVER) && "hand".equals(f.refusal(OVERDRIVE)),
					"the Overdrive click with an item in the main hand must be refused with the hand message, got "
							+ f.describe(OVERDRIVE));
			float energy = f.hamon.getEnergy();
			f.press(OVERDRIVE);
			helper.assertTrue(f.component.getAction() == null && f.hamon.getEnergy() == energy && f.hits.isEmpty(),
					"the refused click still acted: " + f.hits);

			// 1.16 MCUtil.isHandFree: gloves count as a free hand
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GLOVES.get()));
			helper.assertTrue(f.resolve(METAL_SILVER).conditionCheck.isPositive(),
					"gloves must count as a free main hand: " + f.describe(METAL_SILVER));

			// 1.16 PowerBaseImpl.checkRequirements asked for the target first: without one there is no hand message
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			f.clearTarget();
			for (String name : new String[] { OVERDRIVE, METAL_SILVER }) {
				ConditionCheck noTarget = f.resolve(name).conditionCheck;
				helper.assertTrue(!noTarget.isPositive() && noTarget.getWarning() == null,
						name + " without a target must fail without a message, got " + f.describe(name));
			}

			// 1.16 HamonMetalSilverOverdriveWeapon.checkHeldItems: no weapon, no message
			f.newTarget(NEAR);
			helper.assertTrue(!f.resolve(METAL_SILVER_WEAPON).conditionCheck.isPositive() && f.refusal(METAL_SILVER_WEAPON) == null,
					"the weapon variant without a weapon must fail without a message, got " + f.describe(METAL_SILVER_WEAPON));
			helper.succeed();
		}
	}

	private static float cost(String name) {
		return OVERDRIVE.equals(name) ? 600.0F : METAL_SILVER.equals(name) ? 1000.0F : 750.0F;
	}

	private static HamonSkill skill(String name) {
		return OVERDRIVE.equals(name) ? ModHamonSkills.OVERDRIVE.get() : ModHamonSkills.METAL_SILVER_OVERDRIVE.get();
	}

	// the scene of Fixture.scene: only the unarmed Metal Silver Overdrive has an armoured target
	private static float multiplier(String name) {
		return METAL_SILVER.equals(name) ? 1.2F : 1.0F;
	}

	private static String refusal(AbilityConditionCheck check) {
		Component warning = check.conditionCheck.getWarning();
		if (check.conditionCheck.isPositive() || warning == null) {
			return null;
		}
		String key = warning.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : warning.getString();
		String prefix = "jojo.message.action_condition.";
		return key.startsWith(prefix) ? key.substring(prefix.length()) : key;
	}

	// hotbar slots that offer the ability, under any modifier and input method
	private static int countSlots(ControlSchemeTemplate template, String ability) {
		int count = 0;
		for (ControlSchemeTemplate.GroupTemplate group : template.groups.values()) {
			for (ControlSchemeTemplate.AbilitiesHotbar hotbar : group.hotbars) {
				for (Map<InputKey.Modifier, Map<InputMethod, String>> slot : hotbar.slots) {
					if (slot.values().stream().anyMatch(byMethod -> byMethod.containsValue(ability))) {
						count++;
					}
				}
			}
		}
		return count;
	}

	private static void assertClose(GameTestHelper helper, float actual, float expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < EPS, what + ": expected " + expected + ", got " + actual);
	}

	private record Hit(String kind, float amount) {
		@Override
		public String toString() {
			return kind + " " + amount;
		}
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final ServerLevel level;
		private final Player user;
		private final PlayerPower power;
		private final HamonData hamon;
		private final LivingComponentAction component;
		private final List<Zombie> spawned = new ArrayList<>();
		private final List<Hit> hits = new ArrayList<>();
		private final Consumer<LivingDamageEvent.Post> damageListener = this::onDamage;
		private Zombie target;

		private Fixture(GameTestHelper helper) {
			this.helper = helper;
			level = helper.getLevel();
			// not a ServerPlayer: it loads no chunks around itself, which would disturb the tests next to this one
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			try {
				Vec3 origin = helper.absoluteVec(new Vec3(2.5D, 2.5D, 2.5D));
				user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
				user.setYHeadRot(0.0F);
				GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
				user.setNoGravity(true);
				helper.assertTrue(level.addFreshEntity(user), "Could not add the Overdrive test player");
				power = PowerClass.PLAYER_POWER.attachGet(user);
				power.setPowerType(ModPlayerPowers.HAMON.get());
				hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
				component = LivingComponentAction.getComponent(user);
				helper.assertTrue(ability(OVERDRIVE).isAbilityAvailable(power) && ability(BEAT).isAbilityAvailable(power),
						"a new Hamon user must start with Overdrive and Overdrive Beat");
				NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDamageEvent.Post.class, damageListener);
				ready();
			}
			catch (RuntimeException | Error e) {
				close();
				throw e;
			}
		}

		private void onDamage(LivingDamageEvent.Post event) {
			if (target != null && event.getEntity() == target) {
				DamageSource source = event.getSource();
				String kind = source.is(ModDamageTypes.HAMON) ? "hamon"
						: source.is(DamageTypes.PLAYER_ATTACK) ? "punch" : source.getMsgId();
				hits.add(new Hit(source.getEntity() == user ? kind : kind + " by " + source.getEntity(), event.getOriginalDamage()));
			}
		}

		private Ability ability(String name) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found != null, "Hamon has no ability " + name);
			return found;
		}

		private void learn(HamonSkill skill) {
			hamon.learnSkill(skill);
			helper.assertTrue(hamon.isSkillLearned(skill) && ability(METAL_SILVER).isAbilityAvailable(power)
					&& ability(METAL_SILVER_WEAPON).isAbilityAvailable(power), "Could not grant " + skill);
		}

		// full energy and attack strength, nothing recorded, no action
		private void ready() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			component.setAction(null, SyncType.NO_SYNC);
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			setAttackStrengthTicker(user, 100);
			hits.clear();
			HamonAbilityHelpers.takeLastSparkEmitter();
		}

		// the scene each technique can be clicked in, 2 blocks from a fresh target
		private void scene(String name) {
			newTarget(NEAR);
			if (METAL_SILVER.equals(name)) {
				target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			}
			user.setItemInHand(InteractionHand.MAIN_HAND,
					METAL_SILVER_WEAPON.equals(name) ? new ItemStack(Items.IRON_SWORD) : ItemStack.EMPTY);
			ready();
		}

		private Zombie newTarget(double distance) {
			if (target != null) {
				target.discard();
			}
			Zombie zombie = EntityType.ZOMBIE.create(level);
			helper.assertTrue(zombie != null, "Could not create the Overdrive target");
			zombie.setNoAi(true);
			zombie.setNoGravity(true);
			spawned.add(zombie);
			target = zombie;
			moveTarget(distance);
			helper.assertTrue(level.addFreshEntity(zombie), "Could not add the Overdrive target");
			component.entityAim.setTarget(new ActionTarget(zombie));
			return zombie;
		}

		// the target's feet centre, straight in front of the user, a block below its eyes' block
		private void moveTarget(double distance) {
			Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D + distance));
			target.moveTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
		}

		private void clearTarget() {
			component.entityAim.setTarget(ActionTarget.EMPTY);
		}

		private AbilityConditionCheck resolve(String slotAbility) {
			// a fresh resolution: the power caches its moves within a tick
			AvailableAbilities available = new AvailableAbilities();
			available.update(power, power.getMoveset());
			AbilityConditionCheck resolved = available.getContextVariationContainer(slotAbility);
			helper.assertTrue(resolved != null, slotAbility + " is not available to the user");
			return resolved;
		}

		private String refusal(String slotAbility) {
			return HamonOverdriveFamilyGameTests.refusal(resolve(slotAbility));
		}

		private String describe(String slotAbility) {
			return describe(resolve(slotAbility));
		}

		private String describe(AbilityConditionCheck check) {
			String warning = HamonOverdriveFamilyGameTests.refusal(check);
			return check.ability.name() + (check.conditionCheck.isPositive() ? " (usable)"
					: " (refused" + (warning != null ? ": " + warning : ", no message") + ")");
		}

		// As ClAbilityInputPacket.handlePress: the slot's ability as the server resolves it, its condition check,
		// then the click, whose release follows at once.
		private AbilityConditionCheck press(String slotAbility) {
			AbilityConditionCheck resolved = resolve(slotAbility);
			if (AbilityInput.withConditionCheck(resolved, user, InputMethod.CLICK)) {
				AbilityInput.keyPress(KEY, resolved.ability, user, null, InputMethod.CLICK, 0.0F,
						BufferingState.clickCanBuffer(), ability(slotAbility).getAbilityId());
				AbilityInput.keyRelease(KEY, user);
			}
			return resolved;
		}

		private void tick(int count) {
			// the action lifecycle alone, without entity physics or Hamon regeneration
			for (int i = 0; i < count; i++) {
				user.tickCount++;
				component.tick();
			}
		}

		private void tickUntilOver() {
			for (int i = 0; i < 40 && component.getAction() != null; i++) {
				tick(1);
			}
			helper.assertTrue(component.getAction() == null, "the technique did not end within 40 ticks");
		}

		private Hit firstHit(String kind) {
			for (Hit hit : hits) {
				if (hit.kind.equals(kind)) {
					return hit;
				}
			}
			return null;
		}

		// what DamageUtil hands to hurt() for this base damage
		private float hamonAmount(float baseDamage) {
			return HamonAbilityHelpers.hamonDamageAmount(target, baseDamage) * hamon.getHamonDamageMultiplier()
					* HamonAbilityHelpers.configHamonDamageMultiplier();
		}

		@Override
		public void close() {
			NeoForge.EVENT_BUS.unregister(damageListener);
			if (component != null) {
				user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
				component.setAction(null, SyncType.NO_SYNC);
			}
			for (Zombie zombie : spawned) {
				zombie.discard();
			}
			user.discard();
		}
	}

	private static void setAttackStrengthTicker(LivingEntity entity, int ticks) {
		try {
			Field field = LivingEntity.class.getDeclaredField("attackStrengthTicker");
			field.setAccessible(true);
			field.setInt(entity, ticks);
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionError("LivingEntity.attackStrengthTicker is not accessible", e);
		}
	}
}
