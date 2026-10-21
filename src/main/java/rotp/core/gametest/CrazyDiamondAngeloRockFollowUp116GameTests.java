package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModGamerules;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.network.c2s.ClAbilityInputPacket;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.AbilityInput.InputEventType;
import rotp.core.powersystem.ability.input.ActionInputBuffer;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/*
 * 1.16 ModStandsInit :710-716: Crazy Diamond's Angelo Rock (CRAZY_DIAMOND_ANGELO_ROCK, a StandEntityActionModifier) was the
 * restore-terrain key's follow-up during the finisher variation of its heavy punch
 * (CRAZY_DIAMOND_RESTORE_TERRAIN.attackRecoveryFollowup(CRAZY_DIAMOND_ANGELO_ROCK, CRAZY_DIAMOND_FINISHER_PUNCH)):
 * StandAction.replaceActionKostyl gave it while that finisher was the Stand's task, whatever finisher value made the heavy
 * key the finisher, and only while canSetFollowUp held: the task did not have it yet and PowerBaseImpl.checkRequirements
 * passed for it, CrazyDiamondAngeloRock.checkSpecificConditions asking, without a message, for block breaking, no
 * TURN_INTO_ANGELO_ROCK effect of the user and a task with no follow-up at all (hasTaskWithNoModifiers).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrazyDiamondAngeloRockFollowUp116GameTests {
	private static final short HEAVY_KEY = 27;
	private static final short RESTORE_KEY = 28;

	private CrazyDiamondAngeloRockFollowUp116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theRestoreTerrainKeyGivesTheAngeloRockDuringTheFinisherWhateverItsMeterAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "AngeloRockKey")) {
			s.keyRow(0.6F, s.finisher, s.angeloRock, "the finisher (meter 0.6)");
			s.keyRow(1.0F, s.finisher, s.angeloRock, "the finisher (meter 1)");
			s.keyRow(0.0F, s.heavy, s.restoreTerrain, "the base heavy punch (meter 0)");
			s.power.getCurTypeData()._lockedAbilities.add("finisher");
			try {
				helper.assertTrue(!s.finisher.isAbilityUnlocked(s.power), "fixture: the finisher is still unlocked");
				s.keyRow(1.0F, s.heavy, s.restoreTerrain, "the base heavy punch at a full meter, the finisher locked");
			}
			finally {
				s.power.getCurTypeData()._lockedAbilities.remove("finisher");
			}
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theRestoreTerrainKeyGivesTheAngeloRockOnlyWhileItsOwnChecksPassAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "AngeloRockChecks")) {
			ServerLevel level = helper.getLevel();
			GameRules.BooleanValue breakBlocks = level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS);
			boolean breakBlocksBefore = breakBlocks.get();
			StandEntityHeavyPunch punch = s.startPunch(1.0F, s.finisher, "the finisher (meter 1)");
			breakBlocks.set(false, level.getServer());
			try {
				s.notGiven("with block breaking off (jojoAbilitiesBreakBlocks false)");
			}
			finally {
				breakBlocks.set(breakBlocksBefore, level.getServer());
			}
			s.given("fixture: block breaking back on");
			s.clearAction();

			// an earlier finisher's Angelo Rock, still turning the cow into rock
			StandEffectInstance underWay = ModStandAbilities.EFFECT_CD_PUNCH_ANGELO_ROCK.get().create(level).withTarget(s.cow);
			s.power.userStandEffects.addEffect(underWay);
			try {
				punch = s.startPunch(1.0F, s.finisher, "the finisher while an earlier Angelo Rock is under way");
				helper.assertTrue(punch.getPunchModifiers().isEmpty(), "fixture: the earlier Angelo Rock is on the new finisher");
				s.notGiven("while an earlier Angelo Rock of the user is still under way");
			}
			finally {
				s.power.userStandEffects.removeEffect(underWay);
			}
			s.given("fixture: the earlier Angelo Rock is over");
			s.clearAction();

			// the finisher's other follow-up taken first, as CrazyDMisshapingPunchInput.onClick adds it
			punch = s.startPunch(1.0F, s.finisher, "the finisher before its misshaping punch");
			StandEffectInstance misshaping = ModStandAbilities.EFFECT_CD_PUNCH_MISSHAPING.get().create(level);
			s.power.userStandEffects.addEffect(misshaping);
			try {
				helper.assertTrue(punch.getPunchModifiers().contains(misshaping), "fixture: the misshaping punch is not on the finisher");
				s.notGiven("once the finisher took its misshaping punch");
			}
			finally {
				s.power.userStandEffects.removeEffect(misshaping);
			}
			s.clearAction();
			helper.succeed();
		}
	}

	/*
	 * Taken, the Angelo Rock is on the punch (its modifiers), the server's and, with the effect the server sends, the user's
	 * client's copy of it. A client that sent it again before that arrived reaches the server's cached keys of the same tick
	 * or, a tick later, its fallback to the requested ability (ClAbilityInputPacket.resolveServerInputAbility); 1.16 refused
	 * it (hasTaskWithNoModifiers) and gave the key back to the restore terrain.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void anAngeloRockSentAgainAfterItWasTakenStartsNothingAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "AngeloRockSentAgain")) {
			StandEntityHeavyPunch punch = s.startPunch(1.0F, s.finisher, "the finisher (meter 1)");
			float full = s.power.getStamina();
			resetMovesCache(s.power);
			s.sendAngeloRock();
			helper.assertTrue(s.standAction.getAction() == punch && punch.getPunchModifiers().size() == 1 && s.angeloRocks() == 1
					&& Math.abs(full - s.power.getStamina() - 50.0F) < 0.01F && buffered(s.user) == null,
					"fixture: the Angelo Rock sent under the restore-terrain key did not become the finisher's follow-up: action "
							+ s.standAction.getAction() + ", modifiers " + punch.getPunchModifiers() + ", stamina " + full + " -> "
							+ s.power.getStamina() + ", buffered " + buffered(s.user));
			float stamina = s.power.getStamina();

			s.sendAngeloRock();
			s.nothingStarted(punch, stamina, "sent again in the same tick, with the key the server resolved before");

			AbilityConditionCheck key = s.resolve(s.restoreTerrain);
			AbilityConditionCheck own = s.resolve(s.angeloRock);
			helper.assertTrue(key != null && key.ability == s.restoreTerrain && own != null && own.ability == s.angeloRock
					&& !own.conditionCheck.isPositive() && own.conditionCheck.getWarning() == null, "1.16 gave the restore-terrain"
							+ " key back to the restore terrain once the finisher had its Angelo Rock, which it refused silently: the key"
							+ " resolves to " + describe(key) + ", the Angelo Rock is " + describe(own));
			s.sendAngeloRock();
			s.nothingStarted(punch, stamina, "sent a tick later, through the server's fallback to the requested ability");
			s.clearAction();
			helper.succeed();
		}
	}

	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final StandType standType;
		private final Player user;
		private StandPower power;
		private StandEntity stand;
		private LivingComponentAction standAction;
		private Cow cow;
		private Ability heavy;
		private Ability finisher;
		private Ability restoreTerrain;
		private Ability angeloRock;

		private Scene(GameTestHelper helper, String name) {
			this.helper = helper;
			ServerLevel level = helper.getLevel();
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
			user = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
			try {
				helper.assertTrue(standType != null, "Missing Crazy Diamond Stand type");
				user.getAbilities().instabuild = false;
				Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
				user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
				user.setNoGravity(true);
				helper.assertTrue(level.addFreshEntity(user), "Could not add the Crazy Diamond player");
				power = PowerClass.STAND.attachGet(user);
				helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
						== StandPowerTransitions.Status.APPLIED, "Could not grant Crazy Diamond");
				power.setResolveLevel(4);
				for (String ability : new String[] { "heavy_punch", "finisher", "restore_terrain", "fuse_with_rock" }) {
					power.getCurTypeData()._lockedAbilities.remove(ability);
				}
				heavy = power.getAbility("heavy_punch");
				finisher = power.getAbility("finisher");
				restoreTerrain = power.getAbility("restore_terrain");
				angeloRock = power.getAbility("fuse_with_rock");
				helper.assertTrue(heavy != null && finisher != null && restoreTerrain != null && angeloRock != null
						&& finisher.isAbilityUnlocked(power) && restoreTerrain.isAbilityUnlocked(power)
						&& angeloRock.isAbilityUnlocked(power), "fixture: Crazy Diamond's heavy punch, finisher, restore terrain or"
								+ " Angelo Rock is missing or locked");
				helper.assertTrue(standType.summon(user, power), "Could not summon Crazy Diamond");
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "Summoned Crazy Diamond is missing");
				stand.summonLockTicks = 0;
				standAction = LivingComponentAction.getComponent(stand);

				cow = EntityType.COW.create(level);
				helper.assertTrue(cow != null, "Could not create the cow");
				cow.moveTo(userPos.x, userPos.y, userPos.z + 1.5D, 180.0F, 0.0F);
				cow.setNoAi(true);
				cow.setNoGravity(true);
				helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");
				LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(cow));
				standAction.entityAim.setTarget(new ActionTarget(cow));
			}
			catch (RuntimeException | Error e) {
				close();
				throw e;
			}
		}

		// the heavy key pressed at the meter, then what the restore-terrain key resolves to while that punch runs
		private void keyRow(float meter, Ability running, Ability expected, String row) {
			startPunch(meter, running, row);
			AbilityConditionCheck key = resolve(restoreTerrain);
			boolean followUp = expected == angeloRock;
			helper.assertTrue(key != null && key.ability == expected && (!followUp || key.conditionCheck.isPositive()),
					"1.16 gave the restore-terrain key during " + row + (followUp ? " the Angelo Rock, its follow-up"
							: " as the restore terrain, no follow-up") + ": it resolves to " + describe(key));
			clearAction();
		}

		// as ClAbilityInputPacket handles a click of the heavy key
		private StandEntityHeavyPunch startPunch(float meter, Ability running, String row) {
			clearAction();
			power.setStamina(power.getMaxStamina());
			stand.setFinisherMeter(meter);
			AbilityConditionCheck pressed = resolve(heavy);
			if (pressed != null && AbilityInput.withConditionCheck(pressed, user, InputMethod.CLICK)) {
				AbilityInput.keyPress(HEAVY_KEY, pressed.ability, user, null, InputMethod.CLICK, 0,
						BufferingState.clickCanBuffer(), heavy.getAbilityId());
				AbilityInput.keyRelease(HEAVY_KEY, user);
			}
			EntityActionInstance action = standAction.getAction();
			helper.assertTrue(pressed != null && pressed.ability == running && action instanceof StandEntityHeavyPunch punch
					&& action.ability == running && punch.getPunchModifiers().isEmpty(), "fixture (" + row + "): the heavy key did"
							+ " not start " + running.name() + ": " + describe(pressed) + ", action " + action);
			return (StandEntityHeavyPunch) action;
		}

		private void notGiven(String when) {
			AbilityConditionCheck key = resolve(restoreTerrain);
			AbilityConditionCheck own = resolve(angeloRock);
			helper.assertTrue(key != null && key.ability == restoreTerrain && own != null && own.ability == angeloRock
					&& !own.conditionCheck.isPositive() && own.conditionCheck.getWarning() == null, "1.16 gave no Angelo Rock "
							+ when + " (canSetFollowUp: its checks refused it silently): the restore-terrain key resolves to "
							+ describe(key) + ", the Angelo Rock is " + describe(own));
		}

		private void given(String when) {
			AbilityConditionCheck key = resolve(restoreTerrain);
			helper.assertTrue(key != null && key.ability == angeloRock && key.conditionCheck.isPositive(),
					when + ": the restore-terrain key resolves to " + describe(key));
		}

		private void nothingStarted(StandEntityHeavyPunch punch, float stamina, String when) {
			Object buffered = buffered(user);
			helper.assertTrue(standAction.getAction() == punch && punch.getPunchModifiers().size() == 1 && angeloRocks() == 1
					&& power.getStamina() == stamina && buffered == null, "1.16 refused the Angelo Rock " + when + " once the"
							+ " finisher had it: the Stand's action " + standAction.getAction() + " (was " + punch + "), modifiers "
							+ punch.getPunchModifiers() + ", Angelo Rock effects " + angeloRocks() + ", stamina " + stamina + " -> "
							+ power.getStamina() + ", buffered " + buffered);
		}

		private long angeloRocks() {
			return power.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_CD_PUNCH_ANGELO_ROCK.get()).count();
		}

		private AbilityConditionCheck resolve(Ability base) {
			resetMovesCache(power);
			return power.updateAvailableMoves().getContextVariationContainer(base);
		}

		// the restore-terrain key's hold as a client whose key gives the Angelo Rock sends it, through the packet's codec and
		// handler
		private void sendAngeloRock() {
			long generation = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).latestInputGeneration(RESTORE_KEY) + 1;
			ClAbilityInputPacket sent = ClAbilityInputPacket.keyPress(RESTORE_KEY, generation, user, restoreTerrain, angeloRock,
					InputEventType.PRESS_HOLD, 0, new ActionTarget(cow), new ActionTarget(cow));
			IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
					new Class<?>[] { IPayloadContext.class }, (proxy, method, args) -> {
						if (method.getName().equals("player")) {
							return user;
						}
						throw new UnsupportedOperationException("Fixture: the handler asked the context for " + method.getName());
					});
			Field typeField;
			Object registeredType;
			try {
				typeField = ClAbilityInputPacket.class.getDeclaredField("type");
				typeField.setAccessible(true);
				registeredType = typeField.get(null);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Fixture: cannot read the packet type", e);
			}
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			try {
				// the Handler constructor sets the packet's static type; the registered one is put back
				ClAbilityInputPacket.Handler handler = new ClAbilityInputPacket.Handler(JojoMod.resLoc("clkey"));
				handler.encode(sent, buf);
				handler.handle(handler.decode(buf), context);
			}
			finally {
				buf.release();
				try {
					typeField.set(null, registeredType);
				}
				catch (ReflectiveOperationException e) {
					throw new IllegalStateException("Fixture: cannot restore the packet type", e);
				}
				AbilityInput.keyRelease(RESTORE_KEY, user);
			}
		}

		private void clearAction() {
			if (standAction != null) {
				standAction.setAction(null, user, SyncType.NO_SYNC);
			}
		}

		@Override
		public void close() {
			clearAction();
			if (power != null) {
				power.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_CD_PUNCH_ANGELO_ROCK.get()).toList()
						.forEach(power.userStandEffects::removeEffect);
				power.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_CD_PUNCH_MISSHAPING.get()).toList()
						.forEach(power.userStandEffects::removeEffect);
			}
			if (power != null && power.isSummoned()) {
				standType.forceUnsummon(user, power);
			}
			if (cow != null) {
				cow.discard();
			}
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			user.discard();
		}
	}

	private static Object buffered(Player user) {
		try {
			Field field = ActionInputBuffer.class.getDeclaredField("buffered");
			field.setAccessible(true);
			return field.get(user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).inputBuffer);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot read the input buffer", e);
		}
	}

	// the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
	private static void resetMovesCache(StandPower power) {
		try {
			Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
			cached.setAccessible(true);
			cached.setBoolean(power, false);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
		}
	}

	private static String describe(AbilityConditionCheck container) {
		return container == null ? "nothing" : container.ability.name() + " (" + (container.conditionCheck.isPositive()
				? "usable" : "refused") + ")";
	}
}
