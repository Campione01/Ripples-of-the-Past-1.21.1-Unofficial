package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 PowerBaseImpl.tickHeldAction re-ran checkRequirements on every tick a key held an action, against the target
 * under the crosshair: the target (its kind, Action.checkRangeAndTarget's 8 blocks, 4 without a line of sight, and the
 * action's own checkTarget), then the action's conditions. A failed result ended the hold with its message, unless it
 * let the hold go on (continueHold: a hold-only action's target), which only skipped that tick.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeldActionRecheck116GameTests {
	private static final short KEY = 13;
	private static final String CONDITION = "jojo.message.action_condition.";

	private HeldActionRecheck116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void hypnosisChargeEndsWhenItsTargetIsPastItsRange(GameTestHelper helper) {
		try (PowerScene s = new PowerScene(helper, "HeldHypnosisRange", ModPlayerPowers.HAMON.get())) {
			HamonData hamon = PlayerPower.getPowerData(s.user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.HYPNOSIS.get());
			helper.assertTrue(hamon.isSkillLearned(ModHamonSkills.HYPNOSIS.get()), "setup: Hypnosis is not learned");
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			Mob near = s.mob(EntityType.WOLF, 2.0D);
			Mob far = s.mob(EntityType.WOLF, 12.0D);
			s.aim(near);
			EntityActionInstance charge = s.start("hypnosis");
			s.tick(3);
			helper.assertTrue(charge.getPhase() == ActionPhase.WINDUP && !charge.isOver(),
					"setup: Hypnosis is not charging at the wolf 2 blocks away: phase " + charge.getPhase());
			s.aim(far);
			s.tick(1);
			helper.assertTrue(charge.isOver(),
					"1.16 Action.checkRangeAndTarget ended the held Hypnosis once the wolf under the crosshair was past 8 blocks:"
							+ " phase " + charge.getPhase());
			helper.assertTrue(s.user.keys().contains(CONDITION + "target_too_far"),
					"1.16 sent target_too_far as the hold ended: messages " + s.user.keys());
			helper.succeed();
		}
	}

	/** A server player that keeps the action bar messages it is sent. */
	private static final class RecordingPlayer extends FakePlayer {
		private final List<Component> actionBar = new ArrayList<>();

		private RecordingPlayer(ServerLevel level, String name) {
			super(level, new GameProfile(UUID.randomUUID(), name));
		}

		@Override
		public void displayClientMessage(Component message, boolean actionBar) {
			if (actionBar) {
				this.actionBar.add(message);
			}
		}

		private List<String> keys() {
			return actionBar.stream()
					.map(message -> message.getContents() instanceof TranslatableContents contents
							? contents.getKey() : message.getString())
					.toList();
		}
	}

	/** A player with a non-Stand power, looking south (+z) with no gravity, and the mobs it aims at. */
	private static final class PowerScene implements AutoCloseable {
		private final GameTestHelper helper;
		private final RecordingPlayer user;
		private final PlayerPower power;
		private final LivingComponentAction component;
		private final Vec3 origin;
		private final List<Entity> spawned = new ArrayList<>();

		private PowerScene(GameTestHelper helper, String name, PlayerPowerType<?> type) {
			this.helper = helper;
			user = new RecordingPlayer(helper.getLevel(), name);
			origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the held-check player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(type);
			component = LivingComponentAction.getComponent(user);
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
			component.entityAim.setTarget(target != null ? new ActionTarget(target) : ActionTarget.EMPTY);
		}

		private EntityActionAbility ability(String name) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered " + name);
			return (EntityActionAbility) found;
		}

		// set as a press sets it and held by its key
		private EntityActionInstance start(String name) {
			EntityActionInstance action = ability(name).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, PowerClass.PLAYER_POWER, action));
			return action;
		}

		private void tick(int count) {
			for (int tick = 0; tick < count; tick++) {
				user.tickCount++;
				component.tick();
			}
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			component.entityAim.setTarget(ActionTarget.EMPTY);
			component.setAction(null, SyncType.NO_SYNC);
			user.removeAllEffects();
			spawned.forEach(Entity::discard);
			user.discard();
		}
	}
}
