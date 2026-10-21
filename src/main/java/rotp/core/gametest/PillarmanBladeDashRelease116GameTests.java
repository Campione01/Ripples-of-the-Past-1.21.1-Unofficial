package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ModPillarmanActions :101-102: Blade Dash (energy 50, holdToFire(10, true), holdType(40); its getHoldDurationMax
 * Integer.MAX_VALUE) waited for its key. Letting go (InputHandler.stopHeldAction: shouldFire once held its 10 ticks) went to
 * PowerBaseImpl.stopHeldAction(true), which on the server fired it only if checkRequirements still passed (:249-286:
 * canUsePower first, then the cooldown, the performer stunned or dead, Action.checkConditions: stone form, the energy,
 * stopped time, the user on the ground; then isUnlocked) and otherwise fired nothing, without a message.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillarmanBladeDashRelease116GameTests {
	private static final short KEY = 13;
	private static final String BLADE_DASH = "pillarman_blade_dash_attack";
	private static final float ENERGY = 300.0F;

	private PillarmanBladeDashRelease116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void bladeDashLetGoFiresOnlyWhileItsRequirementsStillPassAsIn116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		try (Scene s = new Scene(helper, "PillarmanBladeDashRelease")) {
			EntityActionInstance dash = s.start();
			s.tick(15);
			helper.assertTrue(!dash.isOver() && dash.getPhase() == ActionPhase.BUTTON_CHARGE && dash.getPhaseTick() == 10.0F
					&& s.data.getEnergy() == ENERGY, "fixture: Blade Dash is not waiting for its release past its 10-tick charge"
							+ " after 15 held ticks: it " + s.describe(dash) + ", energy " + s.data.getEnergy());
			s.release();
			boolean fired = dash.getPhase() == ActionPhase.PERFORM;
			s.tick(1);
			if (!fired || s.data.getEnergy() != ENERGY - 50.0F || !s.leapt()) {
				wrong.add("1.16 fired Blade Dash let go after its charge, 50 energy and the leap: it " + s.describe(dash) + ", energy "
						+ s.data.getEnergy() + ", motion " + s.user.getDeltaMovement());
			}
			s.clear();

			s.refill();
			EntityActionInstance early = s.start();
			s.tick(9);
			s.release();
			if (!early.isOver() || s.data.getEnergy() != ENERGY || s.leapt()) {
				wrong.add("1.16 let Blade Dash go without the dash when released one tick short of its charge: it "
						+ s.describe(early) + ", energy " + s.data.getEnergy());
			}
			s.clear();

			for (String refusal : new String[] { "its user turned spectator", "its energy below the cost", "its user off the ground" }) {
				s.refill();
				EntityActionInstance refused = s.start();
				s.tick(15);
				helper.assertTrue(!refused.isOver() && refused.getPhase() == ActionPhase.BUTTON_CHARGE, "fixture: Blade Dash is not"
						+ " waiting for its release past its charge (" + refusal + "): it " + s.describe(refused));
				float energy = refusal.startsWith("its energy") ? 40.0F : ENERGY;
				switch (refusal) {
					case "its user turned spectator" -> s.user.setGameMode(GameType.SPECTATOR);
					case "its energy below the cost" -> s.data.setEnergy(s.user, energy);
					default -> s.user.setOnGround(false);
				}
				try {
					s.release();
				}
				finally {
					s.user.setGameMode(GameType.SURVIVAL);
					s.user.setOnGround(true);
				}
				if (!refused.isOver() || s.data.getEnergy() != energy || s.leapt() || !s.messages().isEmpty()) {
					wrong.add("1.16 fired nothing, silently, when Blade Dash was let go after its charge with " + refusal + ": it "
							+ s.describe(refused) + ", energy " + s.data.getEnergy() + ", motion " + s.user.getDeltaMovement()
							+ ", messages " + s.messages());
				}
				s.clear();
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** A Pillar Man in Light mode on the ground, looking south (+z) with no gravity, holding one key; its actions are ticked here. */
	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final RecordingPlayer user;
		private final PlayerPower power;
		private final PillarmanData data;
		private final LivingComponentAction actions;

		private Scene(GameTestHelper helper, String name) {
			this.helper = helper;
			user = new RecordingPlayer(helper.getLevel(), name);
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add " + name);
			actions = LivingComponentAction.getComponent(user);
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.PILLAR_MAN.get());
			data = PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
			data.setEvolutionStage(2, user);
			data.setMode(PillarmanMode.LIGHT, user);
			refill();
		}

		private void refill() {
			data.setEnergy(user, ENERGY);
			user.setOnGround(true);
			user.setDeltaMovement(Vec3.ZERO);
			helper.assertTrue(!data.isStoneFormEnabled() && data.getEnergy() == ENERGY && user.onGround(), "setup: the Light"
					+ " Pillar Man is in stone form, short of energy or off the ground");
		}

		// set on the user as a press sets it, and held by its key
		private EntityActionInstance start() {
			Ability ability = power.getAbility(BLADE_DASH);
			helper.assertTrue(ability instanceof EntityActionAbility && ability.isAbilityUnlocked(power)
					&& ability.checkConditions(power).isPositive(), "setup: " + BLADE_DASH + " is missing, locked or refused");
			EntityActionInstance action = ((EntityActionAbility) ability).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			helper.assertTrue(action != null, "setup: " + BLADE_DASH + " made no action");
			actions.setAction(null, user, SyncType.NO_SYNC);
			actions.setAction(action, user, SyncType.NO_SYNC);
			EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, PowerClass.PLAYER_POWER, action));
			helper.assertTrue(action.getPhase() == ActionPhase.BUTTON_CHARGE, "setup: " + BLADE_DASH + " did not start its charge");
			return action;
		}

		private void tick(int count) {
			for (int tick = 0; tick < count; tick++) {
				user.tickCount++;
				actions.tick();
			}
		}

		private void release() {
			AbilityInput.keyRelease(KEY, user);
		}

		private boolean leapt() {
			return user.getDeltaMovement().horizontalDistanceSqr() > 0.0D;
		}

		private List<String> messages() {
			return user.keys();
		}

		private String describe(EntityActionInstance action) {
			return action.isOver() ? "ended" : "is in its " + action.getPhase() + " at tick " + action.getPhaseTick() + " of "
					+ action.getCurPhaseLength();
		}

		private void clear() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			actions.setAction(null, SyncType.NO_SYNC);
			user.setDeltaMovement(Vec3.ZERO);
			user.actionBar.clear();
		}

		@Override
		public void close() {
			clear();
			user.discard();
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
}
