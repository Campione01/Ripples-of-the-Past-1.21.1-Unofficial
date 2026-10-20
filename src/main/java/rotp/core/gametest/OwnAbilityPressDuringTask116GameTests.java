package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.hierophant.HierophantEmeraldSplashAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.reflection.CommonReflection;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityAction.checkTaskCancelling -> canClickDuringTask: a Stand action pressed again while it was its Stand's
 * task was refused, with no message and not queued, unless it was chainable (StandEntityLightAttack,
 * HierophantGreenEmeraldSplash, SilverChariotDashAttack); a chainable one cancelled its task where canBeCanceled allowed
 * it (its recovery) and was queued before that. The port's input buffer (owner decision N2) keeps the presses an action
 * holds back; where an action would let its own press cancel it, 1.16's refusal applies.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OwnAbilityPressDuringTask116GameTests {
	private static final short KEY = 23;

	private OwnAbilityPressDuringTask116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandActionThatWasNotChainableRefusesItsOwnPressDuringItsTaskAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "OwnPressStarPlatinum", "star_platinum", 4)) {
			s.unlock("inhale");
			s.refusedDuringItself("inhale", InputMethod.HOLD, action -> {});
			s.refusedDuringItself("enhanced_eyesight", InputMethod.HOLD, action -> {});

			s.user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400, 0));
			s.refusedDuringItself("barrage", InputMethod.HOLD, EntityActionInstance::startRecovery);
			s.user.removeEffect(ModStatusEffects.RESOLVE);

			// with the uppercut locked a full meter gives the plain heavy punch a finisher's strength and free recovery
			s.power.getCurTypeData()._lockedAbilities.add("finisher_uppercut");
			s.stand.setFinisherMeter(1.0F);
			s.refusedDuringItself("heavy_punch", InputMethod.CLICK, EntityActionInstance::startRecovery);

			// before its recovery the heavy punch holds its own press back, which the input buffer keeps (N2)
			s.stand.setFinisherMeter(0.0F);
			EntityActionInstance heavy = s.start("heavy_punch");
			helper.assertTrue(heavy.getPhase() == ActionPhase.WINDUP, "setup: the heavy punch is not in its windup: " + heavy.getPhase());
			AbilityConditionCheck resolved = s.resolve("heavy_punch");
			helper.assertTrue(resolved != null && resolved.ability == s.power.getAbility("heavy_punch")
					&& resolved.conditionCheck.isPositive(),
					"the heavy punch pressed in its own windup is held back, not refused: " + describe(resolved));
			BufferingState buffered = s.press("heavy_punch", InputMethod.CLICK);
			helper.assertTrue(buffered.shouldBuffer && s.standAction.getAction() == heavy,
					"the heavy punch pressed in its own windup was not buffered: buffered " + buffered.shouldBuffer
							+ ", action " + s.standAction.getAction());
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void crazyDiamondsRepairHealAndRevertRefuseTheirOwnPressDuringTheirTaskAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "OwnPressCrazyDiamond", "crazy_diamond", 4)) {
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.setDamageValue(sword.getMaxDamage() - 10);
			s.user.setItemInHand(InteractionHand.OFF_HAND, sword);
			s.unlock("repair_item");
			s.refusedDuringItself("repair_item", InputMethod.HOLD, action -> {});
			s.user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

			Mob cow = s.mob(EntityType.COW, 2.0D);
			cow.setHealth(cow.getMaxHealth() - 6.0F);
			s.aim(cow);
			s.unlock("heal");
			s.refusedDuringItself("heal", InputMethod.HOLD, action -> {});
			cow.discard();

			Creeper creeper = (Creeper) s.mob(EntityType.CREEPER, 2.0D);
			CommonReflection.setCreeperPowered(creeper, true);
			creeper.setHealth(creeper.getMaxHealth() - 6.0F);
			s.aim(creeper);
			s.unlock("revert_state");
			s.refusedDuringItself("revert_state", InputMethod.HOLD, action -> {});
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theEmeraldSplashTakesItsOwnPressOnlyInItsRecoveryAsIn116(GameTestHelper helper) {
		// below Resolve 3 and with no Resolve effect the splash has its windup and recovery (noPhases)
		try (Scene s = new Scene(helper, "OwnPressEmeraldSplash", "hierophant_green", 2)) {
			s.unlock("emerald_splash");
			Ability splash = s.power.getAbility("emerald_splash");
			EntityActionInstance first = s.start("emerald_splash");
			for (int tick = 0; tick < 60 && first.getPhase() != ActionPhase.PERFORM && !first.isOver(); tick++) {
				s.standAction.tick();
			}
			helper.assertTrue(first.getPhase() == ActionPhase.PERFORM && s.standAction.getAction() == first,
					"setup: the emerald splash did not reach its PERFORM: " + first.getPhase());
			AbilityConditionCheck inPerform = s.resolve("emerald_splash");
			helper.assertTrue(inPerform != null && inPerform.ability == splash && inPerform.conditionCheck.isPositive(),
					"1.16 took the chainable emerald splash pressed during itself: " + describe(inPerform));
			BufferingState queued = s.press("emerald_splash", InputMethod.HOLD);
			helper.assertTrue(queued.shouldBuffer && s.standAction.getAction() == first,
					"1.16 queued the emerald splash pressed again in its PERFORM (isCancelable only in RECOVERY): buffered "
							+ queued.shouldBuffer + ", the Stand's action " + s.standAction.getAction() + " (was " + first + ")");

			first.startRecovery();
			AbilityConditionCheck inRecovery = s.resolve("emerald_splash");
			helper.assertTrue(inRecovery != null && inRecovery.ability == splash && inRecovery.conditionCheck.isPositive(),
					"1.16 took the chainable emerald splash pressed in its recovery: " + describe(inRecovery));
			BufferingState chained = s.press("emerald_splash", InputMethod.HOLD);
			EntityActionInstance second = s.standAction.getAction();
			helper.assertTrue(chained.isActionSuccess && second != null && second != first && second.ability == splash,
					"1.16 restarted the emerald splash pressed in its recovery (chainable): success " + chained.isActionSuccess
							+ ", the Stand's action " + second);
			helper.succeed();
		}
	}

	/*
	 * 1.16 HierophantGreenEmeraldSplash.isCancelable allowed a cancel only in RECOVERY, for any action pressed, the other
	 * splash variant (hierophant_green_es_concentrated, a SHIFT variation in 1.16, its own slot here) included: pressed
	 * in the windup or perform it was queued (the input buffer here) and replaced the splash in its recovery.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theEmeraldSplashTakesItsOtherVariantOnlyInItsRecoveryAsIn116(GameTestHelper helper) {
		// below Resolve 3 and with no Resolve effect the splash has its windup and recovery (noPhases)
		try (Scene s = new Scene(helper, "VariantPressEmeraldSplash", "hierophant_green", 2)) {
			s.unlock("emerald_splash");
			s.unlock("emerald_splash_concentrated");
			s.power.getCurTypeData().setAbilityLearningProgressPoints(HierophantEmeraldSplashAbility.EMERALD_SPLASH_LEARNING_ABILITY,
					HierophantEmeraldSplashAbility.EMERALD_SPLASH_MAX_TRAINING, HierophantEmeraldSplashAbility.EMERALD_SPLASH_MAX_TRAINING,
					s.power);
			helper.assertTrue(HierophantEmeraldSplashAbility.isEmeraldSplashFullyTrained(s.power),
					"setup: Emerald Splash is not fully trained, so the concentrated splash is hidden");
			for (String[] variants : new String[][] { { "emerald_splash", "emerald_splash_concentrated" },
					{ "emerald_splash_concentrated", "emerald_splash" } }) {
				for (ActionPhase phase : new ActionPhase[] { ActionPhase.WINDUP, ActionPhase.PERFORM }) {
					s.power.setStamina(s.power.getMaxStamina());
					EntityActionInstance running = s.start(variants[0]);
					for (int tick = 0; tick < 60 && running.getPhase() != phase && !running.isOver(); tick++) {
						s.standAction.tick();
					}
					helper.assertTrue(running.getPhase() == phase && s.standAction.getAction() == running,
							"setup: " + variants[0] + " did not reach its " + phase + ": " + running.getPhase());
					AbilityConditionCheck other = s.resolve(variants[1]);
					helper.assertTrue(other != null && other.ability == s.power.getAbility(variants[1]) && other.conditionCheck.isPositive(),
							"setup: " + variants[1] + " cannot be pressed during " + variants[0] + ": " + describe(other));
					BufferingState queued = s.press(variants[1], InputMethod.HOLD);
					helper.assertTrue(queued.shouldBuffer && !queued.isActionSuccess && s.standAction.getAction() == running,
							"1.16 queued " + variants[1] + " pressed in the " + phase + " of " + variants[0] + " (isCancelable only in"
									+ " RECOVERY): buffered " + queued.shouldBuffer + ", started " + queued.isActionSuccess
									+ ", the Stand's action " + s.standAction.getAction() + " (was " + running + ")");
					s.user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
					s.standAction.setAction(null, s.user, SyncType.NO_SYNC);
				}
				s.power.setStamina(s.power.getMaxStamina());
				EntityActionInstance running = s.start(variants[0]);
				running.startRecovery();
				BufferingState replaced = s.press(variants[1], InputMethod.HOLD);
				EntityActionInstance next = s.standAction.getAction();
				helper.assertTrue(replaced.isActionSuccess && next != null && next != running
						&& next.ability == s.power.getAbility(variants[1]), "1.16 let " + variants[1] + " pressed in the recovery of "
								+ variants[0] + " replace it: started " + replaced.isActionSuccess + ", the Stand's action " + next);
				s.standAction.setAction(null, s.user, SyncType.NO_SYNC);
			}
			helper.succeed();
		}
	}

	private static String describe(AbilityConditionCheck container) {
		return container == null ? "nothing" : container.ability.name() + " (" + (container.conditionCheck.isPositive()
				? "usable" : "refused" + (container.conditionCheck.getWarning() != null ? " with a message" : "")) + ")";
	}

	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final MessagePlayer user;
		private final StandType standType;
		private final Vec3 origin;
		private final List<Entity> spawned = new ArrayList<>();
		private StandPower power;
		private StandEntity stand;
		private LivingComponentAction standAction;

		private Scene(GameTestHelper helper, String name, String standId, int resolveLevel) {
			this.helper = helper;
			user = new MessagePlayer(helper.getLevel(), name);
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			try {
				helper.assertTrue(standType != null, "Missing Stand type " + standId);
				user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
				user.setNoGravity(true);
				user.getAbilities().instabuild = false;
				helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
				power = PowerClass.STAND.attachGet(user);
				helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
						== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
				power.setResolveLevel(resolveLevel);
				helper.assertTrue(standType.summon(user, power), "Could not summon " + standId);
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "Summoned " + standId + " is missing");
				standAction = LivingComponentAction.getComponent(stand);
				// the summon lock skips the action tick, and StandEntity.tick, which counts it down, does not run here
				stand.summonLockTicks = 0;
				power.setStamina(power.getMaxStamina());
			}
			catch (RuntimeException | Error e) {
				close();
				throw e;
			}
		}

		// as a player who can press it has it; the scene does not run the user's tick
		private void unlock(String name) {
			power.getCurTypeData()._lockedAbilities.remove(name);
			helper.assertTrue(power.getAbility(name).isAbilityUnlocked(power), "setup: " + name + " is still locked");
		}

		private Mob mob(EntityType<? extends Mob> type, double ahead) {
			Mob mob = type.create(helper.getLevel());
			helper.assertTrue(mob != null, "Could not create " + type);
			mob.setNoAi(true);
			mob.setNoGravity(true);
			mob.moveTo(origin.x, origin.y, origin.z + ahead, 180.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Could not add " + type);
			spawned.add(mob);
			return mob;
		}

		private void aim(Entity target) {
			LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(target));
		}

		// set on the Stand as a press sets it
		private EntityActionInstance start(String name) {
			Ability ability = power.getAbility(name);
			helper.assertTrue(ability instanceof EntityActionAbility, "Missing Stand ability " + name);
			EntityActionInstance action = ((EntityActionAbility) ability).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			helper.assertTrue(action != null, "setup: " + name + " made no action");
			standAction.setAction(null, user, SyncType.NO_SYNC);
			standAction.setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(standAction.getAction() == action, "setup: " + name + " is not the Stand's action");
			return action;
		}

		/*
		 * The ability, usable while the Stand is idle, runs and is put where it would let its own press cancel it; that
		 * press is then refused with no message, and a press leaves the running action as it was.
		 */
		private void refusedDuringItself(String name, InputMethod inputMethod, Consumer<EntityActionInstance> phase) {
			standAction.setAction(null, user, SyncType.NO_SYNC);
			power.setStamina(power.getMaxStamina());
			Ability ability = power.getAbility(name);
			AbilityConditionCheck idle = resolve(name);
			helper.assertTrue(idle != null && idle.ability == ability && idle.conditionCheck.isPositive(),
					"setup: " + name + " cannot be used with the Stand idle: " + describe(idle));
			EntityActionInstance running = start(name);
			phase.accept(running);
			helper.assertTrue(!running.isOver() && running.canBeCancelledInto(running.ability),
					"setup: the running " + name + " would not let its own press cancel it (phase " + running.getPhase() + ")");
			AbilityConditionCheck resolved = resolve(name);
			helper.assertTrue(resolved != null && resolved.ability == ability && !resolved.conditionCheck.isPositive()
					&& resolved.conditionCheck.getWarning() == null,
					"1.16 refused " + name + " pressed again during its own task (not chainable), silently, phase "
							+ running.getPhase() + ": " + describe(resolved));
			int messages = user.actionBar.size();
			BufferingState pressed = press(name, inputMethod);
			helper.assertTrue(standAction.getAction() == running && !pressed.shouldBuffer && !pressed.isActionSuccess,
					"1.16 refused " + name + " pressed again during its own task, unqueued: the Stand's action is "
							+ standAction.getAction() + " (was " + running + "), buffered " + pressed.shouldBuffer);
			helper.assertTrue(user.actionBar.size() == messages,
					"1.16 refused " + name + " pressed again during its own task with no message: " + user.actionBar);
			standAction.setAction(null, user, SyncType.NO_SYNC);
		}

		// as ClAbilityInputPacket handles a press: the key's resolved ability, its condition check, then the press
		private BufferingState press(String name, InputMethod inputMethod) {
			BufferingState state = BufferingState.clickCanBuffer();
			Ability base = power.getAbility(name);
			AbilityConditionCheck container = resolve(name);
			if (container != null && AbilityInput.withConditionCheck(container, user, inputMethod)) {
				AbilityInput.keyPress(KEY, container.ability, user, null, inputMethod, 0, state, base.getAbilityId());
				AbilityInput.keyRelease(KEY, user);
			}
			return state;
		}

		// the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
		private AbilityConditionCheck resolve(String name) {
			try {
				Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
				cached.setAccessible(true);
				cached.setBoolean(power, false);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
			}
			return power.updateAvailableMoves().getContextVariationContainer(power.getAbility(name));
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			spawned.forEach(Entity::discard);
			user.discard();
		}
	}

	private static final class MessagePlayer extends FakePlayer {
		private final List<Component> actionBar = new ArrayList<>();

		private MessagePlayer(ServerLevel level, String name) {
			super(level, new GameProfile(UUID.randomUUID(), name));
		}

		@Override
		public void displayClientMessage(Component message, boolean actionBar) {
			if (actionBar) {
				this.actionBar.add(message);
			}
		}
	}
}
