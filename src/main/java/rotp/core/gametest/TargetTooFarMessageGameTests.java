package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.zombie.ZombieData;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * 1.16 Action.checkRangeAndTarget: a press on an entity beyond the action's range is refused with
 * jojo.message.action_condition.target_too_far, before the action looks at what kind of entity it is
 * (PowerBaseImpl.checkTarget). Zombie Devour, Pillar Man Absorption, Blood Drain and Blood Gift reach 2 blocks.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TargetTooFarMessageGameTests {
	private static final String BATCH = "target_too_far_message";
	private static final String TOO_FAR = "jojo.message.action_condition.target_too_far";
	private static final String PLAYER_TARGET = "jojo.message.action_condition.player_target";
	private static final double NEAR = 1.5D;
	private static final double FAR = 3.4D;
	private static final short KEY = 79;

	private TargetTooFarMessageGameTests() {}

	@GameTest(template = "empty", batch = BATCH)
	public static void devourPressOnAnEntityOutOfRangeSaysTargetTooFar(GameTestHelper helper) {
		pressOutOfRange(helper, Kind.DEVOUR);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void absorptionPressOnAnEntityOutOfRangeSaysTargetTooFar(GameTestHelper helper) {
		pressOutOfRange(helper, Kind.ABSORPTION);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void bloodDrainPressOnAnEntityOutOfRangeSaysTargetTooFar(GameTestHelper helper) {
		pressOutOfRange(helper, Kind.BLOOD_DRAIN);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void bloodGiftPressOnAPlayerOutOfRangeSaysTargetTooFar(GameTestHelper helper) {
		pressOutOfRange(helper, Kind.BLOOD_GIFT);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void devourPressWithoutATargetStaysSilent(GameTestHelper helper) {
		pressWithoutTarget(helper, Kind.DEVOUR, List.of());
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void absorptionPressWithoutATargetStaysSilent(GameTestHelper helper) {
		pressWithoutTarget(helper, Kind.ABSORPTION, List.of());
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void bloodDrainPressWithoutATargetStaysSilent(GameTestHelper helper) {
		pressWithoutTarget(helper, Kind.BLOOD_DRAIN, List.of());
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void bloodGiftPressWithoutATargetStillAsksForAPlayer(GameTestHelper helper) {
		pressWithoutTarget(helper, Kind.BLOOD_GIFT, List.of(PLAYER_TARGET));
	}

	// 1.16 checks the range before VampirismBloodGift.checkTarget looks at the kind of entity
	@GameTest(template = "empty", batch = BATCH)
	public static void bloodGiftPressOnAnAnimalAsksForAPlayerInRangeAndSaysTooFarBeyondIt(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.BLOOD_GIFT)) {
			Cow cow = fixture.cow(NEAR);
			fixture.aim(cow);
			boolean admitted = fixture.press();
			helper.assertTrue(!admitted && fixture.keys().equals(List.of(PLAYER_TARGET)),
					"Blood Gift press on a cow in range: admitted=" + admitted + " messages=" + fixture.keys()
							+ " expected the single message " + PLAYER_TARGET);
			fixture.move(cow, FAR);
			admitted = fixture.press();
			helper.assertTrue(!admitted && fixture.keys().equals(List.of(TOO_FAR)),
					"Blood Gift press on a cow out of range: admitted=" + admitted + " messages=" + fixture.keys()
							+ " expected the single message " + TOO_FAR);
		}
		helper.succeed();
	}

	// 1.16 Blood Gift is holdToFire(60, false), not hold-only: PowerBaseImpl.tickHeldAction checks the range on every
	// held tick, and a recipient out of range stops the hold and sends target_too_far.
	@GameTest(template = "empty", batch = BATCH)
	public static void bloodGiftRecipientLeavingRangeDuringTheHoldEndsItWithTargetTooFar(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.BLOOD_GIFT)) {
			Player recipient = fixture.recipient(NEAR);
			EntityActionInstance action = fixture.holdSomeTicks(recipient);
			fixture.move(recipient, FAR);
			fixture.component.tick();
			boolean ended = fixture.component.getAction() != action || action.isOver();
			helper.assertTrue(ended && fixture.keys().equals(List.of(TOO_FAR))
					&& PlayerPower.get(recipient).getPowerType() != ModPlayerPowers.VAMPIRISM.get(),
					"Blood Gift hold with the recipient moved out of range: ended=" + ended + " messages=" + fixture.keys()
							+ " expected the hold to end with the single message " + TOO_FAR);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void bloodGiftHoldEndedByAHealedRecipientInRangeDoesNotSayTargetTooFar(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.BLOOD_GIFT)) {
			Player recipient = fixture.recipient(NEAR);
			EntityActionInstance action = fixture.holdSomeTicks(recipient);
			recipient.setHealth(recipient.getMaxHealth());
			fixture.component.tick();
			boolean ended = fixture.component.getAction() != action || action.isOver();
			helper.assertTrue(ended && !fixture.keys().contains(TOO_FAR),
					"Blood Gift hold with the recipient in range healed above 6: ended=" + ended + " messages=" + fixture.keys()
							+ " expected the hold to end without " + TOO_FAR);
		}
		helper.succeed();
	}

	private static void pressOutOfRange(GameTestHelper helper, Kind kind) {
		try (Fixture fixture = Fixture.open(helper, kind)) {
			LivingEntity target = kind == Kind.BLOOD_GIFT ? fixture.recipient(NEAR) : fixture.cow(NEAR);
			fixture.aim(target);
			helper.assertTrue(fixture.press() && fixture.keys().isEmpty(),
					"TOO-FAR premise: " + kind + " is not admitted on the same target in range: " + fixture.keys());
			fixture.move(target, FAR);
			// the 1.16 distance (eye to the box, less half the user's width) is at least this
			double least = FAR - target.getBbWidth() / 2.0F - fixture.user.getBbWidth() / 2.0F;
			helper.assertTrue(least > 2.0D && fixture.user.hasLineOfSight(target) && target.isAlive(),
					"TOO-FAR premise: the target is not a visible living entity beyond 2 blocks: least=" + least);
			boolean admitted = fixture.press();
			helper.assertTrue(!admitted && fixture.keys().equals(List.of(TOO_FAR)),
					kind + " press on a visible target at least " + least + " blocks away: admitted=" + admitted
							+ " messages=" + fixture.keys() + " expected the single message " + TOO_FAR);
		}
		helper.succeed();
	}

	private static void pressWithoutTarget(GameTestHelper helper, Kind kind, List<String> expected) {
		try (Fixture fixture = Fixture.open(helper, kind)) {
			LivingEntity target = kind == Kind.BLOOD_GIFT ? fixture.recipient(NEAR) : fixture.cow(NEAR);
			fixture.aim(target);
			helper.assertTrue(fixture.press() && fixture.keys().isEmpty(),
					"TOO-FAR premise: " + kind + " is not admitted on a target in range: " + fixture.keys());
			fixture.aim(null);
			boolean admitted = fixture.press();
			helper.assertTrue(!admitted && fixture.keys().equals(expected),
					kind + " press with nothing under the crosshair: admitted=" + admitted + " messages=" + fixture.keys()
							+ " expected " + expected);
		}
		helper.succeed();
	}

	private enum Kind {
		DEVOUR("zombie_devour"), ABSORPTION("pillarman_absorption"),
		BLOOD_DRAIN("vampirism_blood_drain"), BLOOD_GIFT("vampirism_blood_gift");

		final String abilityName;

		Kind(String abilityName) {
			this.abilityName = abilityName;
		}
	}

	private static final class RecordingPlayer extends FakePlayer {
		private final List<Component> actionBar = new ArrayList<>();

		private RecordingPlayer(ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "TargetTooFar"));
		}

		@Override
		public void displayClientMessage(Component message, boolean actionBar) {
			if (actionBar) {
				this.actionBar.add(message);
			}
		}
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final ServerLevel level;
		private final List<Entity> targets = new ArrayList<>();
		private RecordingPlayer user;
		private PlayerPower power;
		private Ability ability;
		private LivingComponentAction component;
		private Vec3 origin;
		private boolean held;

		private Fixture(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel();
		}

		private static Fixture open(GameTestHelper helper, Kind kind) {
			Fixture fixture = new Fixture(helper);
			try {
				fixture.setUp(kind);
				return fixture;
			}
			catch (RuntimeException | Error error) {
				fixture.close();
				throw error;
			}
		}

		private void setUp(Kind kind) {
			helper.assertTrue(level.getDifficulty() == Difficulty.NORMAL, "TOO-FAR premise: requires the Normal difficulty world");
			BlockPos template = helper.absolutePos(BlockPos.ZERO);
			ChunkPos chunk = new ChunkPos(template);
			origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 40.0D, chunk.getMinBlockZ() + 6.5D);
			helper.assertTrue(level.getEntities((Entity) null, new AABB(origin, origin).inflate(5.0D)).isEmpty(),
					"TOO-FAR premise: scene contains a foreign entity");
			user = new RecordingPlayer(level);
			user.setGameMode(GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			level.addNewPlayer(user);
			power = PowerClass.PLAYER_POWER.attachGet(user);
			switch (kind) {
			case DEVOUR -> {
				power.setPowerType(ModPlayerPowers.ZOMBIE.get());
				ZombieData zombie = PlayerPower.getPowerData(user, ModPlayerPowers.ZOMBIE).orElseThrow();
				helper.assertTrue(!zombie.isDisguiseEnabled(), "TOO-FAR premise: the zombie disguise is on");
			}
			case ABSORPTION -> {
				power.setPowerType(ModPlayerPowers.PILLAR_MAN.get());
				PillarmanData pillarman = PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
				pillarman.setEvolutionStage(2, user);
				pillarman.setMode(PillarmanMode.NONE, user);
				pillarman.setEnergy(user, 100.0F);
			}
			default -> {
				power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
				VampirismData vampirism = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
				vampirism.setVampireFullPower(false, user);
				VampirismState.get(user).blood().setCurrent(320.0F);
				vampirism.setBloodLevel(320.0F);
			}
			}
			user.setHealth(user.getMaxHealth());
			ability = power.getAbility(kind.abilityName);
			component = LivingComponentAction.getComponent(user);
			helper.assertTrue(ability != null && user.getMainHandItem().isEmpty() && user.getHealth() > 10.0F,
					"TOO-FAR premise: " + kind.abilityName + " is missing or its user is not ordinary");
		}

		private Cow cow(double separation) {
			Cow cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "TOO-FAR premise: could not create the cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(origin.x, origin.y, origin.z + separation, 180.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(cow), "TOO-FAR premise: could not add the cow");
			targets.add(cow);
			return cow;
		}

		private Player recipient(double separation) {
			Player recipient = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			recipient.setNoGravity(true);
			recipient.moveTo(origin.x, origin.y, origin.z + separation, 180.0F, 0.0F);
			recipient.setHealth(6.0F);
			helper.assertTrue(level.addFreshEntity(recipient), "TOO-FAR premise: could not add the recipient");
			targets.add(recipient);
			PowerClass.PLAYER_POWER.attachGet(recipient);
			return recipient;
		}

		private void move(Entity target, double separation) {
			target.moveTo(origin.x, origin.y, origin.z + separation, 180.0F, 0.0F);
		}

		private void aim(Entity target) {
			component.entityAim.setTarget(target != null ? new ActionTarget(target) : ActionTarget.EMPTY);
		}

		// the server side of a key press: the conditions are checked and a refusal sends its message
		private boolean press() {
			user.actionBar.clear();
			AvailableAbilities available = new AvailableAbilities();
			available.update(power, power.getMoveset());
			return AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.HOLD);
		}

		// an admitted HOLD on the target, run for some ticks of its windup without a message
		private EntityActionInstance holdSomeTicks(LivingEntity target) {
			aim(target);
			helper.assertTrue(press() && keys().isEmpty(), "TOO-FAR premise: the hold is not admitted on the target in range: " + keys());
			HeldInputEntry entry = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.HOLD,
					0.0F, BufferingState.clickOnly(), ability.getAbilityId());
			held = true;
			EntityActionInstance action = component.getAction();
			helper.assertTrue(entry != null && action != null && entry.action == action,
					"TOO-FAR premise: the admitted HOLD did not start its action");
			for (int tick = 0; tick < 5; tick++) {
				component.tick();
			}
			helper.assertTrue(component.getAction() == action && !action.isOver() && action.getPhase() == ActionPhase.WINDUP
					&& keys().isEmpty(),
					"TOO-FAR premise: the hold did not stay in its windup for 5 ticks: phase=" + action.getPhase() + " over="
							+ action.isOver() + " messages=" + keys());
			return action;
		}

		private List<String> keys() {
			return user.actionBar.stream()
					.map(message -> message.getContents() instanceof TranslatableContents contents
							? contents.getKey() : message.getString())
					.toList();
		}

		@Override
		public void close() {
			try {
				if (held) AbilityInput.keyRelease(KEY, user);
				if (component != null) {
					component.entityAim.setTarget(ActionTarget.EMPTY);
					component.setAction(null, user, SyncType.NO_SYNC);
				}
			}
			finally {
				targets.forEach(Entity::discard);
				if (user != null) user.discard();
			}
		}
	}
}
