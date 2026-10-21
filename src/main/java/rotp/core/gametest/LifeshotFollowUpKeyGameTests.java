package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.goldexperience.GoldExperienceHeavyPunchAbility.ToothKnockingHeavyPunch;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.network.c2s.ClAbilityInputPacket;
import rotp.core.powersystem.Moveset;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.AbilityInput.InputEventType;
import rotp.core.powersystem.ability.input.ActionInputBuffer;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 1.16 GoldExperienceLifeshotPunch.attackRecoveryFollowup(GOLD_EXPERIENCE_ENTITY_LIFESHOT): while the Life Shot punch was
 * the Stand's task, the heavy key gave its Life Shot, as StandEntityHeavyAttack.replaceAction asked the running finisher
 * variation's replaceAction (getFinisherVariationIfPresent) and StandAction.replaceActionKostyl gave the variation's
 * follow-up until the task had it. That held for whichever heavy attack the punch was the finisher variation of: Gold
 * Experience's own and Gold Experience Requiem's ger_stand_heavy_punch (1.16 GER InitStands GER_STAND_HEAVY_PUNCH
 * setFinisherVariation(GOLD_EXPERIENCE_LIFESHOT_PUNCH)).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifeshotFollowUpKeyGameTests {
	private static final short KEY = 22;
	private static final String OTHER_HEAVY = "lifeshot_test_heavy_punch";

	private LifeshotFollowUpKeyGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theHeavyKeyGivesTheRunningLifeShotPunchsLifeShotWhicheverHeavyItFinishesAsIn116(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
		helper.assertTrue(standType != null, "Missing Gold Experience Stand type");
		Player user = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "LifeShotFollowUp"));
		StandPower power = null;
		Cow cow = null;
		try {
			user.getAbilities().instabuild = false;
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Life Shot player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Gold Experience");
			power.setResolveLevel(4);
			for (String name : new String[] { "heavy_punch", "lifeshot", "lifeshot_punch" }) {
				power.getCurTypeData()._lockedAbilities.remove(name);
			}
			Ability heavy = power.getAbility("heavy_punch");
			Ability lifeshot = power.getAbility("lifeshot");
			Ability lifeshotPunch = power.getAbility("lifeshot_punch");
			helper.assertTrue(heavy != null && lifeshot != null && lifeshotPunch != null
					&& lifeshot.isAbilityUnlocked(power) && lifeshotPunch.isAbilityUnlocked(power),
					"fixture: Gold Experience's heavy punch, Life Shot or Life Shot punch is missing or locked");
			helper.assertTrue(standType.summon(user, power), "Could not summon Gold Experience");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Gold Experience is missing");
			stand.summonLockTicks = 0;

			cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "Could not create the cow");
			cow.moveTo(userPos.x, userPos.y, userPos.z + 1.5D, 180.0F, 0.0F);
			cow.setNoAi(true);
			cow.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");
			LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(cow));
			LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(cow));

			followUpRow(helper, power, user, stand, heavy, lifeshot, lifeshotPunch, "Gold Experience's heavy punch");

			// Gold Experience Requiem's moveset: the core Life Shot punch is the finisher variation of a heavy attack
			// under another name
			Moveset moveset = power.getMoveset();
			Ability otherHeavy = ModStandAbilities.HEAVY_PUNCH.get().createInstance(
					new AbilityId(PowerClass.STAND, standType.getId(), OTHER_HEAVY));
			moveset.abilities.put(OTHER_HEAVY, otherHeavy);
			lifeshotPunch.initIsFinisher(OTHER_HEAVY);
			helper.assertTrue(power.getMoveset() == moveset && power.getAbility(OTHER_HEAVY) == otherHeavy
					&& otherHeavy.isAbilityUnlocked(power), "fixture: the heavy attack " + OTHER_HEAVY + " is not in the moveset");
			followUpRow(helper, power, user, stand, otherHeavy, lifeshot, lifeshotPunch, "a heavy attack named " + OTHER_HEAVY);
			helper.succeed();
		}
		finally {
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

	/*
	 * 1.16 StandAction.canSetFollowUp offered the Life Shot only if PowerBaseImpl.checkRequirements passed for it on the
	 * punch's target; otherwise the key stayed the running Life Shot punch, which a press could not start again. A key the
	 * punch gave back to the base heavy for a part the Stand lost (getFinisherVariationIfPresent) offered no Life Shot. The
	 * port keeps the refusal message the Life Shot gives (only_stand_user on a punched Stand) on that refused press.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theHeavyKeyGivesTheLifeShotOnlyWhenItsRequirementsPassAsIn116(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
		StandType enemyType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		helper.assertTrue(standType != null && enemyType != null, "Missing the Gold Experience or Star Platinum Stand type");
		Player user = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "LifeShotRequirements"));
		Player enemy = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "LifeShotEnemy"));
		StandPower power = null;
		StandPower enemyPower = null;
		Cow cow = null;
		try {
			user.getAbilities().instabuild = false;
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Life Shot player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Gold Experience");
			power.setResolveLevel(4);
			for (String name : new String[] { "heavy_punch", "lifeshot", "lifeshot_punch" }) {
				power.getCurTypeData()._lockedAbilities.remove(name);
			}
			Ability heavy = power.getAbility("heavy_punch");
			Ability lifeshot = power.getAbility("lifeshot");
			Ability lifeshotPunch = power.getAbility("lifeshot_punch");
			helper.assertTrue(heavy != null && lifeshot != null && lifeshotPunch != null
					&& lifeshot.isAbilityUnlocked(power) && lifeshotPunch.isAbilityUnlocked(power),
					"fixture: Gold Experience's heavy punch, Life Shot or Life Shot punch is missing or locked");
			helper.assertTrue(standType.summon(user, power), "Could not summon Gold Experience");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Gold Experience is missing");
			stand.summonLockTicks = 0;
			StandInstance instance = power.getStandInstance().orElseThrow();

			cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "Could not create the cow");
			cow.moveTo(userPos.x, userPos.y, userPos.z + 1.5D, 180.0F, 0.0F);
			cow.setNoAi(true);
			cow.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");

			// a cooldown on the Life Shot: 1.16 isActionOnCooldown refused it without a message
			ToothKnockingHeavyPunch punch = startLifeShotPunch(helper, power, user, stand, heavy, lifeshotPunch, cow);
			power.setAbilityCooldown("lifeshot", 100);
			AbilityConditionCheck onCooldown = resolve(power, heavy);
			helper.assertTrue(onCooldown != null && onCooldown.ability == lifeshotPunch && !onCooldown.conditionCheck.isPositive()
					&& onCooldown.conditionCheck.getWarning() == null, "1.16 left the heavy key on the running Life Shot punch,"
							+ " refused without a message, while the Life Shot was on cooldown: it resolves to " + describe(onCooldown));
			power.setAbilityCooldown("lifeshot", 0);
			AbilityConditionCheck offCooldown = resolve(power, heavy);
			helper.assertTrue(offCooldown != null && offCooldown.ability == lifeshot && offCooldown.conditionCheck.isPositive()
					&& punch.canAcceptLifeshotFollowup(), "fixture: without the cooldown the key does not give the Life Shot: "
							+ describe(offCooldown));

			// Gold Experience loses its arms during the punch at the cow, whose Life Shot needs no arms: 1.16 gave the key
			// back to the base heavy punch
			instance.removePart(StandPart.ARMS);
			try {
				helper.assertTrue(LivingComponentAction.getComponent(stand).getAction() == punch && punch.canAcceptLifeshotFollowup()
						&& lifeshot.checkConditions(power).isPositive(),
						"fixture: the Life Shot punch did not go on without the arms, or its Life Shot is refused");
				AbilityConditionCheck armless = resolve(power, heavy);
				helper.assertTrue(armless != null && armless.ability == heavy, "1.16 gave the heavy key back to the base heavy"
						+ " punch once the running Life Shot punch needed the arms the Stand lost, so no Life Shot: it resolves to "
						+ describe(armless));
			}
			finally {
				instance.addPart(StandPart.ARMS);
			}

			// the punch's target is a Stand: 1.16 GoldExperienceEntityLifeshot.checkTarget refused it with only_stand_user
			cow.discard();
			Vec3 enemyPos = userPos.add(2.0D, 0.0D, 4.0D);
			enemy.moveTo(enemyPos.x, enemyPos.y, enemyPos.z, 180.0F, 0.0F);
			enemy.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(enemy), "Could not add the enemy Stand user");
			enemyPower = PowerClass.STAND.attachGet(enemy);
			helper.assertTrue(StandPowerTransitions.insert(enemyPower, new StandInstance(enemyType)).status()
					== StandPowerTransitions.Status.APPLIED && enemyType.summon(enemy, enemyPower), "Could not give and summon Star Platinum");
			StandEntity enemyStand = enemyPower.getSummonedStandEntity();
			helper.assertTrue(enemyStand != null, "Summoned Star Platinum is missing");
			enemyStand.moveTo(userPos.x, userPos.y, userPos.z + 1.5D, 180.0F, 0.0F);
			punch = startLifeShotPunch(helper, power, user, stand, heavy, lifeshotPunch, enemyStand);
			helper.assertTrue(punch.getLifeshotTarget() == enemyStand, "fixture: the Life Shot punch is not at Star Platinum: "
					+ punch.getLifeshotTarget());
			AbilityConditionCheck atAStand = resolve(power, heavy);
			String message = atAStand != null && atAStand.conditionCheck.getWarning() != null
					&& atAStand.conditionCheck.getWarning().getContents() instanceof TranslatableContents contents
							? contents.getKey() : null;
			helper.assertTrue(atAStand != null && atAStand.ability == lifeshotPunch && !atAStand.conditionCheck.isPositive()
					&& "jojo.message.action_condition.only_stand_user".equals(message), "1.16 left the heavy key on the running"
							+ " Life Shot punch at a Stand, refused, and the port keeps the Life Shot's only_stand_user message on"
							+ " it: it resolves to " + describe(atAStand) + " with the message " + message);
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned()) {
				standType.forceUnsummon(user, power);
			}
			if (enemyPower != null && enemyPower.isSummoned()) {
				enemyType.forceUnsummon(enemy, enemyPower);
			}
			if (cow != null) {
				cow.discard();
			}
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			user.discard();
			enemy.discard();
		}
	}

	/*
	 * 1.16 StandEntityTask.addModifierAction sent the taken Life Shot to the clients tracking the Stand
	 * (TrStandTaskModifierPacket): the user's client then resolved the heavy key back to the running Life Shot punch too,
	 * whose press canClickDuringTask refused silently, and never sent the Life Shot again.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theUsersClientGivesTheKeyBackToTheRunningPunchOnceTheLifeShotIsTakenAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "LifeShotClientKey")) {
			ToothKnockingHeavyPunch punch = startLifeShotPunch(helper, s.power, s.user, s.stand, s.heavy, s.lifeshotPunch, s.cow);
			ToothKnockingHeavyPunch client = clientCopy(helper, punch, s.stand);
			receiveSyncedData(client, punch);
			AbilityConditionCheck offered = resolveWith(s.power, s.stand, s.heavy, client);
			helper.assertTrue(offered != null && offered.ability == s.lifeshot && offered.conditionCheck.isPositive(),
					"fixture: the client's copy of the Life Shot punch does not give the Life Shot under the heavy key: "
							+ describe(offered));

			press(s.power, s.user, s.heavy);
			helper.assertTrue(s.standAction.getAction() == punch && !punch.canAcceptLifeshotFollowup(),
					"fixture: the server's Life Shot punch did not take the Life Shot");
			receiveSyncedData(client, punch);
			AbilityConditionCheck taken = resolveWith(s.power, s.stand, s.heavy, client);
			helper.assertTrue(taken != null && taken.ability == s.lifeshotPunch && !taken.conditionCheck.isPositive()
					&& taken.conditionCheck.getWarning() == null, "1.16 sent the taken Life Shot to the user's client, whose heavy"
							+ " key then gave the running Life Shot punch, refused silently: the client's copy of the punch, with what"
							+ " the server sends it, resolves the key to " + describe(taken));
			helper.succeed();
		}
	}

	/*
	 * A client whose key still gave the Life Shot after it was taken sends it again; the server finds the key on the running
	 * punch and falls back to the requested ability (ClAbilityInputPacket.resolveServerInputAbility), or takes the key as
	 * it resolved it earlier in the same tick. 1.16 refused that press (the Life Shot was only the punch's follow-up, given
	 * once): in the punch's windup and in its recovery it starts nothing, takes no stamina and waits in no buffer.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aLifeShotSentAgainAfterItWasTakenStartsNothingAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "LifeShotSentAgain")) {
			for (ActionPhase phase : new ActionPhase[] { ActionPhase.WINDUP, ActionPhase.RECOVERY }) {
				ToothKnockingHeavyPunch punch = startLifeShotPunch(helper, s.power, s.user, s.stand, s.heavy, s.lifeshotPunch, s.cow);
				float full = s.power.getStamina();
				resetMovesCache(s.power);
				sendLifeShotClick(helper, s);
				helper.assertTrue(s.standAction.getAction() == punch && !punch.canAcceptLifeshotFollowup()
						&& s.power.getStamina() < full && buffered(s.user) == null, "fixture: the Life Shot sent under the heavy"
								+ " key did not become the running punch's follow-up: action " + s.standAction.getAction()
								+ ", stamina " + full + " -> " + s.power.getStamina() + ", buffered " + buffered(s.user));
				if (phase == ActionPhase.RECOVERY) {
					punch.startRecovery();
				}
				helper.assertTrue(punch.getPhase() == phase, "fixture: the Life Shot punch is not in its " + phase);
				float stamina = s.power.getStamina();

				// in the same tick, with the key the server resolved before the follow-up was taken
				sendLifeShotClick(helper, s);
				assertNothingStarted(helper, s, punch, stamina, "sent again in the same tick, in the punch's " + phase);

				// a tick later, through the server's fallback to the requested ability
				resetMovesCache(s.power);
				AbilityConditionCheck direct = s.power.updateAvailableMoves().getContextVariationContainer(s.lifeshot);
				helper.assertTrue(direct != null && direct.ability == s.lifeshot && !direct.conditionCheck.isPositive()
						&& direct.conditionCheck.getWarning() == null, "1.16 had no Life Shot of its own once the running Life"
								+ " Shot punch took it, so the server refuses it silently in the punch's " + phase + ": "
								+ describe(direct));
				sendLifeShotClick(helper, s);
				assertNothingStarted(helper, s, punch, stamina, "sent a tick later, in the punch's " + phase);
				s.standAction.setAction(null, s.user, SyncType.NO_SYNC);
			}
			helper.succeed();
		}
	}

	private static void assertNothingStarted(GameTestHelper helper, Scene s, ToothKnockingHeavyPunch punch, float stamina,
			String when) {
		Object buffered = buffered(s.user);
		helper.assertTrue(s.standAction.getAction() == punch && s.power.getStamina() == stamina && buffered == null,
				"1.16 refused the Life Shot " + when + " once the punch had taken it: the Stand's action " + s.standAction.getAction()
						+ " (was " + punch + "), stamina " + stamina + " -> " + s.power.getStamina() + ", buffered " + buffered);
	}

	// the heavy key's click as a client whose key gives the Life Shot sends it, through the packet's codec and handler
	private static void sendLifeShotClick(GameTestHelper helper, Scene s) {
		long generation = s.user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).latestInputGeneration(KEY) + 1;
		ClAbilityInputPacket sent = ClAbilityInputPacket.keyPress(KEY, generation, s.user, s.heavy, s.lifeshot,
				InputEventType.PRESS_CLICK, 0, new ActionTarget(s.cow), new ActionTarget(s.cow));
		IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
				new Class<?>[] { IPayloadContext.class }, (proxy, method, args) -> {
					if (method.getName().equals("player")) {
						return s.user;
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

	// the punch as the user's client holds it: decoded from the action the server sends it (EntityActionInstance.encode)
	private static ToothKnockingHeavyPunch clientCopy(GameTestHelper helper, ToothKnockingHeavyPunch punch, StandEntity stand) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			EntityActionInstance.encode(buf, punch);
			EntityActionInstance copy = EntityActionInstance.decode(helper.getLevel(), buf);
			helper.assertTrue(copy instanceof ToothKnockingHeavyPunch, "fixture: the Life Shot punch decodes as " + copy);
			Field performer = EntityActionInstance.class.getDeclaredField("performer");
			performer.setAccessible(true);
			performer.set(copy, stand);
			return (ToothKnockingHeavyPunch) copy;
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot give the client's copy its Stand", e);
		}
		finally {
			buf.release();
		}
	}

	// the punch's synced data the server sends the clients tracking the Stand on its next tick (tickSyncDirtyData)
	private static void receiveSyncedData(ToothKnockingHeavyPunch client, ToothKnockingHeavyPunch server) {
		List<SynchedEntityData.DataValue<?>> sent = server.synchedData.getDataSyncher().syncDirtyData();
		if (sent != null) {
			client.synchedData.getDataSyncher().assignValues(sent);
		}
	}

	// the key as the client resolves it, with its copy of the punch as the Stand's action
	private static AbilityConditionCheck resolveWith(StandPower power, StandEntity stand, Ability base,
			EntityActionInstance clientAction) {
		LivingComponentAction component = LivingComponentAction.getComponent(stand);
		try {
			Field field = LivingComponentAction.class.getDeclaredField("action");
			field.setAccessible(true);
			Object serverAction = field.get(component);
			field.set(component, clientAction);
			try {
				return resolve(power, base);
			}
			finally {
				field.set(component, serverAction);
				resetMovesCache(power);
			}
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot put the client's copy on the Stand", e);
		}
	}

	private static final class Scene implements AutoCloseable {
		private final StandType standType;
		private final Player user;
		private StandPower power;
		private StandEntity stand;
		private LivingComponentAction standAction;
		private Cow cow;
		private Ability heavy;
		private Ability lifeshot;
		private Ability lifeshotPunch;

		private Scene(GameTestHelper helper, String name) {
			ServerLevel level = helper.getLevel();
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
			user = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
			try {
				helper.assertTrue(standType != null, "Missing Gold Experience Stand type");
				user.getAbilities().instabuild = false;
				Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
				user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
				user.setNoGravity(true);
				helper.assertTrue(level.addFreshEntity(user), "Could not add the Life Shot player");
				power = PowerClass.STAND.attachGet(user);
				helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
						== StandPowerTransitions.Status.APPLIED, "Could not grant Gold Experience");
				power.setResolveLevel(4);
				for (String ability : new String[] { "heavy_punch", "lifeshot", "lifeshot_punch" }) {
					power.getCurTypeData()._lockedAbilities.remove(ability);
				}
				heavy = power.getAbility("heavy_punch");
				lifeshot = power.getAbility("lifeshot");
				lifeshotPunch = power.getAbility("lifeshot_punch");
				helper.assertTrue(heavy != null && lifeshot != null && lifeshotPunch != null
						&& lifeshot.isAbilityUnlocked(power) && lifeshotPunch.isAbilityUnlocked(power),
						"fixture: Gold Experience's heavy punch, Life Shot or Life Shot punch is missing or locked");
				helper.assertTrue(standType.summon(user, power), "Could not summon Gold Experience");
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "Summoned Gold Experience is missing");
				stand.summonLockTicks = 0;
				standAction = LivingComponentAction.getComponent(stand);

				cow = EntityType.COW.create(level);
				helper.assertTrue(cow != null, "Could not create the cow");
				cow.moveTo(userPos.x, userPos.y, userPos.z + 1.5D, 180.0F, 0.0F);
				cow.setNoAi(true);
				cow.setNoGravity(true);
				helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");
			}
			catch (RuntimeException | Error e) {
				close();
				throw e;
			}
		}

		@Override
		public void close() {
			if (standAction != null) {
				standAction.setAction(null, user, SyncType.NO_SYNC);
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

	// the heavy key pressed with a full finisher meter at the target, which starts the Life Shot punch
	private static ToothKnockingHeavyPunch startLifeShotPunch(GameTestHelper helper, StandPower power, Player user,
			StandEntity stand, Ability heavy, Ability lifeshotPunch, Entity target) {
		LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
		standAction.setAction(null, user, SyncType.NO_SYNC);
		LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(target));
		standAction.entityAim.setTarget(new ActionTarget(target));
		power.setStamina(power.getMaxStamina());
		stand.setFinisherMeter(1.0F);
		AbilityConditionCheck pressed = press(power, user, heavy);
		EntityActionInstance action = standAction.getAction();
		helper.assertTrue(pressed != null && pressed.ability == lifeshotPunch && action instanceof ToothKnockingHeavyPunch punch
				&& action.ability == lifeshotPunch && punch.canAcceptLifeshotFollowup(), "fixture: a full meter did not make the"
						+ " key start the Life Shot punch at " + target + ": " + describe(pressed) + ", action " + action);
		return (ToothKnockingHeavyPunch) action;
	}

	private static void followUpRow(GameTestHelper helper, StandPower power, Player user, StandEntity stand, Ability base,
			Ability lifeshot, Ability lifeshotPunch, String row) {
		LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
		standAction.setAction(null, user, SyncType.NO_SYNC);
		power.setStamina(power.getMaxStamina());
		stand.setFinisherMeter(1.0F);
		AbilityConditionCheck pressed = press(power, user, base);
		EntityActionInstance action = standAction.getAction();
		helper.assertTrue(pressed != null && pressed.ability == lifeshotPunch && action instanceof ToothKnockingHeavyPunch
				&& action.ability == lifeshotPunch, "fixture (" + row + "): a full meter did not make the key start the Life"
						+ " Shot punch: " + describe(pressed) + ", action " + action);
		ToothKnockingHeavyPunch punch = (ToothKnockingHeavyPunch) action;
		helper.assertTrue(punch.canAcceptLifeshotFollowup(),
				"fixture (" + row + "): the running Life Shot punch has no entity target to take a Life Shot");

		AbilityConditionCheck followUp = resolve(power, base);
		helper.assertTrue(followUp != null && followUp.ability == lifeshot && followUp.conditionCheck.isPositive(),
				"1.16 gave the running Life Shot punch's Life Shot under the key of the heavy attack it is the finisher of ("
						+ row + "): it resolves to " + describe(followUp));
		press(power, user, base);
		helper.assertTrue(standAction.getAction() == punch && !punch.canAcceptLifeshotFollowup(),
				"the running Life Shot punch did not take the Life Shot pressed under its key (" + row + "): action "
						+ standAction.getAction());

		AbilityConditionCheck taken = resolve(power, base);
		helper.assertTrue(taken != null && taken.ability == lifeshotPunch && !taken.conditionCheck.isPositive(),
				"1.16 gave the follow-up once (canSetFollowUp: the task had it), then the running Life Shot punch, refused ("
						+ row + "): " + describe(taken));
		standAction.setAction(null, user, SyncType.NO_SYNC);
	}

	// as ClAbilityInputPacket handles a click: the key's resolved ability, its condition check, then the press
	private static AbilityConditionCheck press(StandPower power, Player user, Ability base) {
		AbilityConditionCheck container = resolve(power, base);
		if (container != null && AbilityInput.withConditionCheck(container, user, InputMethod.CLICK)) {
			AbilityInput.keyPress(KEY, container.ability, user, null, InputMethod.CLICK, 0,
					BufferingState.clickCanBuffer(), base.getAbilityId());
			AbilityInput.keyRelease(KEY, user);
		}
		return container;
	}

	// the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
	private static AbilityConditionCheck resolve(StandPower power, Ability base) {
		resetMovesCache(power);
		return power.updateAvailableMoves().getContextVariationContainer(base);
	}

	// as the power's next tick does
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
