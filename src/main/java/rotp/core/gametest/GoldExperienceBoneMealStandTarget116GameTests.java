package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GoldExperienceBoneMeal took a baby AnimalEntity (checkTarget), and StandEntityAction.checkRangeAndTarget then asked
 * checkStandTarget while the Stand was out: an entity only if canStandTargetEntity, StandEntity.canAttack, which refuses the
 * user's own tamed animal and an animal on the user's vehicle, silently (noMessage). PowerBaseImpl.onClickAction summoned
 * the Stand (StandEntityAction.onClick) before checkRequirements, so a press with the Stand away was refused alike and its
 * Stand taken back (afterClick). A mob's press went through the same click. A refused press ran nothing: no aging, no
 * stamina, no cooldown.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoldExperienceBoneMealStandTarget116GameTests {
	private static final String BATCH = "ge_bone_meal_stand_target_116";
	private static final short KEY = 166;

	private GoldExperienceBoneMealStandTarget116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void boneMealPressedAtItsUsersOwnTamedBabyWolfIsRefusedAsIn116(GameTestHelper helper) {
		for (boolean standOut : new boolean[] { true, false }) {
			try (Scene scene = new Scene(helper, new RecordingPlayer(helper.getLevel(), "BoneMealOwner"))) {
				RecordingPlayer user = (RecordingPlayer) scene.user;
				Wolf wolf = scene.baby(EntityType.WOLF);
				wolf.setTame(true, false);
				wolf.setOwnerUUID(user.getUUID());
				helper.assertTrue(wolf.getOwner() == user, "setup: the wolf does not see its owner");
				scene.summon(true);
				helper.assertTrue(!scene.power.getSummonedStandEntity().canAttack(wolf),
						"setup: Gold Experience may attack its user's own tamed wolf");
				scene.summon(standOut);
				String where = standOut ? "with the Stand out" : "with the Stand away (the press summons it)";
				int age = wolf.getAge();
				scene.aim(wolf);
				String answer = scene.answer();
				BufferingState press = scene.press();
				helper.assertTrue(!press.isActionSuccess && wolf.getAge() == age
						&& scene.power.getStamina() == scene.power.getMaxStamina()
						&& !scene.power.isAbilityOnCooldown("bone_meal") && user.messages.isEmpty()
						&& (!standOut || "none".equals(answer)) && (standOut || !scene.power.isSummoned()),
						"1.16 refused silently a Bone Meal pressed at its user's own tamed baby wolf " + where
								+ " (checkStandTarget: StandEntity.canAttack) and took back a Stand the press summoned: the slot"
								+ " answered " + answer + ", the press went off " + press.isActionSuccess + ", the wolf aged by "
								+ (wolf.getAge() - age) + ", stamina " + scene.power.getStamina() + " of "
								+ scene.power.getMaxStamina() + ", cooldown " + scene.power.isAbilityOnCooldown("bone_meal")
								+ ", messages " + user.messages + ", the Stand out after it " + scene.power.isSummoned());
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void boneMealPressedAtAWildBabyCowAgesItAsIn116(GameTestHelper helper) {
		for (boolean standOut : new boolean[] { true, false }) {
			try (Scene scene = new Scene(helper, new RecordingPlayer(helper.getLevel(), "BoneMealWild"))) {
				Cow calf = scene.baby(EntityType.COW);
				scene.summon(standOut);
				int age = calf.getAge();
				scene.aim(calf);
				String answer = scene.answer();
				BufferingState press = scene.press();
				helper.assertTrue("accepted".equals(answer) && press.isActionSuccess && calf.getAge() > age
						&& scene.power.getStamina() == scene.power.getMaxStamina() - 2.0F,
						"1.16 aged a wild calf Bone Meal was pressed at " + (standOut ? "with the Stand out" : "with the Stand away")
								+ " and took 2 stamina: the slot answered " + answer + ", the press went off "
								+ press.isActionSuccess + ", the calf aged by " + (calf.getAge() - age) + ", stamina "
								+ scene.power.getStamina() + " of " + scene.power.getMaxStamina());
			}
		}
		helper.succeed();
	}

	// a zombie and a calf in one boat: 1.16 StandEntity.canAttack refused an animal on its user's vehicle
	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void aMobsBoneMealAtABabyOnItsOwnVehicleIsRefusedAsIn116(GameTestHelper helper) {
		for (int row = 0; row < 3; row++) {
			boolean standOut = row == 0;
			boolean shared = row < 2;
			Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
			helper.assertTrue(zombie != null, "setup: could not create the zombie");
			zombie.setNoAi(true);
			try (Scene scene = new Scene(helper, zombie)) {
				Cow calf = scene.baby(EntityType.COW);
				Boat boat = EntityType.BOAT.create(helper.getLevel());
				helper.assertTrue(boat != null, "setup: could not create the boat");
				boat.setNoGravity(true);
				boat.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 1.0D, 0.0F, 0.0F);
				scene.extras.add(boat);
				helper.assertTrue(helper.getLevel().addFreshEntity(boat), "setup: could not add the boat");
				helper.assertTrue(zombie.startRiding(boat, true) && (!shared || calf.startRiding(boat, true)),
						"setup: the zombie or the calf could not board the boat");
				zombie.setYRot(0.0F);
				zombie.setXRot(0.0F);
				zombie.setYHeadRot(0.0F);
				if (!shared) {
					Vec3 onRay = zombie.getEyePosition().add(zombie.getLookAngle().scale(2.0D));
					calf.moveTo(onRay.x, onRay.y - calf.getBbHeight() / 2.0D, onRay.z, 180.0F, 0.0F);
				}
				scene.summon(true);
				helper.assertTrue(scene.power.getSummonedStandEntity().canAttack(calf) != shared,
						"setup: Gold Experience may attack the calf " + (shared ? "in" : "out of") + " its user's boat");
				scene.summon(standOut);
				int age = calf.getAge();
				scene.aim(calf);
				ConditionCheck check = scene.ability.checkConditions(scene.power);
				if (check.isPositive()) {
					AbilityInput.keyPressMob(scene.ability, zombie, new FriendlyByteBuf(Unpooled.EMPTY_BUFFER), InputMethod.HOLD);
					scene.run();
				}
				String what = (shared ? "a calf in its own boat" : "a calf out of its boat")
						+ (standOut ? " with the Stand out" : " with the Stand away");
				if (shared) {
					helper.assertTrue(calf.getAge() == age && scene.power.getStamina() == scene.power.getMaxStamina()
							&& (!standOut || !check.isPositive() && check.getWarning() == null)
							&& (standOut || !scene.power.isSummoned()),
							"1.16 refused silently a zombie's Bone Meal at " + what + ": its check answered "
									+ Scene.answer(check) + ", the calf aged by " + (calf.getAge() - age) + ", stamina "
									+ scene.power.getStamina() + " of " + scene.power.getMaxStamina() + ", the Stand out after it "
									+ scene.power.isSummoned());
				}
				else {
					helper.assertTrue(check.isPositive() && calf.getAge() > age, "1.16 aged " + what + " for a zombie's Bone"
							+ " Meal: its check answered " + Scene.answer(check) + ", the calf aged by " + (calf.getAge() - age));
				}
			}
		}
		helper.succeed();
	}

	private static final class RecordingPlayer extends FakePlayer {
		private final List<String> messages = new ArrayList<>();

		private RecordingPlayer(ServerLevel level, String name) {
			super(level, new GameProfile(UUID.randomUUID(), name));
		}

		@Override
		public void displayClientMessage(Component message, boolean actionBar) {
			messages.add(message.getContents() instanceof TranslatableContents contents ? contents.getKey() : message.getString());
		}
	}

	// a Gold Experience user looking south with a baby 2.5 blocks ahead on its look ray
	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final LivingEntity user;
		private final Vec3 origin;
		private final List<Entity> extras = new ArrayList<>();
		private final StandType type;
		private final StandPower power;
		private final Ability ability;

		private Scene(GameTestHelper helper, LivingEntity user) {
			this.helper = helper;
			this.user = user;
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.setNoGravity(true);
			if (user instanceof FakePlayer player) {
				player.setGameMode(GameType.SURVIVAL);
			}
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the Stand user");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
			helper.assertTrue(type != null, "setup: missing Gold Experience");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: could not give Gold Experience");
			ability = power.getAbility("bone_meal");
			helper.assertTrue(ability != null, "setup: missing Gold Experience's bone_meal");
		}

		private <T extends Animal> T baby(EntityType<T> entityType) {
			T baby = entityType.create(helper.getLevel());
			helper.assertTrue(baby != null, "setup: could not create the baby");
			baby.setNoAi(true);
			baby.setNoGravity(true);
			baby.setAge(-24000);
			baby.moveTo(origin.x, user.getEyeY() - baby.getBbHeight() / 2.0D, origin.z + 2.5D, 180.0F, 0.0F);
			extras.add(baby);
			helper.assertTrue(helper.getLevel().addFreshEntity(baby) && baby.isBaby(), "setup: could not add the baby");
			return baby;
		}

		private void summon(boolean out) {
			if (out && !power.isSummoned()) {
				helper.assertTrue(type.summon(user, power), "setup: could not summon Gold Experience");
				StandEntity stand = power.getSummonedStandEntity();
				stand.summonLockTicks = 0;
				stand.gradualSummonWeaknessTicks = 0;
			}
			else if (!out && power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			helper.assertTrue(power.isSummoned() == out, "setup: Gold Experience is " + (out ? "not out" : "still out"));
			power.setStamina(power.getMaxStamina());
		}

		private void aim(Entity target) {
			LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(target));
		}

		// the slot's answer as the HUD asks it
		private String answer() {
			AvailableAbilities available = new AvailableAbilities();
			available.update(power, power.getMoveset());
			AbilityConditionCheck container = available.getContextVariationContainer(ability);
			return container != null ? answer(container.conditionCheck) : "no slot";
		}

		private static String answer(ConditionCheck check) {
			if (check.isPositive()) {
				return "accepted";
			}
			Component warning = check.getWarning();
			if (warning == null) {
				return "none";
			}
			return warning.getContents() instanceof TranslatableContents contents ? contents.getKey() : warning.getString();
		}

		// as ClAbilityInputPacket handles a click: the slot's condition check, then the press, run to its end
		private BufferingState press() {
			BufferingState state = BufferingState.clickOnly();
			AvailableAbilities available = new AvailableAbilities();
			available.update(power, power.getMoveset());
			AbilityConditionCheck container = available.getContextVariationContainer(ability);
			helper.assertTrue(container != null, "setup: no slot resolves bone_meal");
			if (AbilityInput.withConditionCheck(container, user, InputMethod.CLICK)) {
				AbilityInput.keyPress(KEY, container.ability, user, null, InputMethod.CLICK, 0.0F, state, ability.getAbilityId());
			}
			AbilityInput.keyRelease(KEY, user);
			run();
			return state;
		}

		private void run() {
			LivingComponentAction userActions = LivingComponentAction.getComponent(user);
			for (int tick = 0; tick < 30; tick++) {
				StandEntity stand = power.getSummonedStandEntity();
				if (stand != null) {
					LivingComponentAction.getComponent(stand).tick();
				}
				userActions.tick();
			}
			StandEntity stand = power.getSummonedStandEntity();
			if (stand != null) {
				LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			}
		}

		@Override
		public void close() {
			try {
				LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
				if (power.isSummoned()) {
					type.forceUnsummon(user, power);
				}
			}
			finally {
				user.stopRiding();
				for (Entity extra : extras) {
					extra.ejectPassengers();
					extra.discard();
				}
				user.discard();
			}
		}
	}
}
