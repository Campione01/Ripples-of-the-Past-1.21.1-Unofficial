package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.PillarmanVeinEntity;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ModPillarmanActions :86-90: Giant Carthwheel Prison (holdToFire(30, false), energy 125, cooldown 100) and Self
 * Detonation (holdToFire(60, false), energy 150), each made holdType() by its constructor, so their getHoldDurationMax
 * was Integer.MAX_VALUE and PowerBaseImpl.tickHeldAction never fired them by itself. Letting go of the key
 * (InputHandler.stopHeldAction) fired one held for its length through stopHeldAction(true), which checked the
 * requirements again and fired nothing, without a message, when they failed; a release before that length ended it
 * without firing, paying its energy or setting its cooldown (performAction did those).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillarmanHeatHoldRelease116GameTests {
	private static final short KEY = 12;
	private static final String PRISON = "pillarman_giant_carthwheel_prison";
	private static final String DETONATION = "pillarman_self_detonation";

	private PillarmanHeatHoldRelease116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void giantCarthwheelPrisonAndSelfDetonationWaitForTheirReleaseAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "PillarmanHeatRelease")) {
			EntityActionInstance prison = s.start(PRISON);
			s.tick(45);
			helper.assertTrue(!prison.isOver() && prison.getPhase() == ActionPhase.BUTTON_CHARGE && prison.getPhaseTick() == 30.0F
					&& s.veins().isEmpty() && s.data.getEnergy() == 300.0F && !s.data.isAbilityOnCooldown(PRISON),
					"1.16 held Giant Carthwheel Prison past its 30-tick charge until its key was let go (holdType): after 45 held"
							+ " ticks it " + s.describe(prison));
			s.release();
			helper.assertTrue(prison.getPhase() == ActionPhase.PERFORM, "1.16 fired the held Giant Carthwheel Prison when its"
					+ " key was let go after its charge: it " + s.describe(prison));
			s.tick(1);
			helper.assertTrue(s.veins().size() == 32 && s.data.getEnergy() == 175.0F && s.data.getAbilityCooldown(PRISON) == 100
					&& s.user.hasEffect(MobEffects.FIRE_RESISTANCE), "1.16 performAction, once released: 32 veins, 125 energy"
							+ " paid, the 100-tick cooldown and fire resistance: " + s.veins().size() + " veins, energy "
							+ s.data.getEnergy() + ", cooldown " + s.data.getAbilityCooldown(PRISON));
			s.clear();

			s.refill();
			EntityActionInstance detonation = s.start(DETONATION);
			s.tick(75);
			helper.assertTrue(!detonation.isOver() && detonation.getPhase() == ActionPhase.BUTTON_CHARGE
					&& detonation.getPhaseTick() == 60.0F && s.data.getEnergy() == 300.0F, "1.16 held Self Detonation past its"
							+ " 60-tick charge until its key was let go (holdType): after 75 held ticks it " + s.describe(detonation));
			s.release();
			// its perform (the blast, 150 energy, 40 self damage) is not ticked here
			helper.assertTrue(detonation.getPhase() == ActionPhase.PERFORM, "1.16 fired the held Self Detonation when its key"
					+ " was let go after its charge: it " + s.describe(detonation));
			helper.assertTrue(s.messages().isEmpty(), "no message on these holds: " + s.messages());
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aHeatChargeLetGoEarlyOrRefusedAtItsReleaseDoesNotFireAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "PillarmanHeatEarlyRelease")) {
			for (String name : new String[] { PRISON, DETONATION }) {
				int ticksToFire = name.equals(PRISON) ? 30 : 60;
				s.refill();
				EntityActionInstance early = s.start(name);
				s.tick(ticksToFire - 1);
				s.release();
				helper.assertTrue(early.isOver() && s.veins().isEmpty() && s.data.getEnergy() == 300.0F
						&& !s.data.isAbilityOnCooldown(name), "1.16 let " + name + " go without firing, paying or a cooldown when"
								+ " released one tick short of its " + ticksToFire + "-tick charge: it " + s.describe(early)
								+ ", energy " + s.data.getEnergy() + ", cooldown " + s.data.getAbilityCooldown(name));
				s.clear();

				// what stopHeldAction(true) checked again: the energy, taken below the cost just before the key is let go
				s.refill();
				EntityActionInstance refused = s.start(name);
				s.tick(ticksToFire + 5);
				helper.assertTrue(!refused.isOver() && refused.getPhase() == ActionPhase.BUTTON_CHARGE, "fixture: " + name
						+ " is not waiting for its release past its charge: it " + s.describe(refused));
				s.data.setEnergy(s.user, 100.0F);
				s.release();
				helper.assertTrue(refused.isOver() && s.veins().isEmpty() && s.data.getEnergy() == 100.0F
						&& !s.data.isAbilityOnCooldown(name) && s.messages().isEmpty(), "1.16 fired nothing, silently, when "
								+ name + " was let go after its charge without the energy it needs: it " + s.describe(refused)
								+ ", energy " + s.data.getEnergy() + ", messages " + s.messages());
				s.clear();
			}

			// and what it checked first, canUsePower: the user turned spectator just before the key is let go
			s.refill();
			EntityActionInstance spectator = s.start(PRISON);
			s.tick(35);
			s.user.setGameMode(GameType.SPECTATOR);
			try {
				s.release();
			}
			finally {
				s.user.setGameMode(GameType.SURVIVAL);
			}
			helper.assertTrue(spectator.isOver() && s.veins().isEmpty() && s.data.getEnergy() == 300.0F
					&& !s.data.isAbilityOnCooldown(PRISON), "1.16 fired nothing when Giant Carthwheel Prison was let go after"
							+ " its charge by a user who could no longer use the power: it " + s.describe(spectator) + ", energy "
							+ s.data.getEnergy());
		}
		helper.succeed();
	}

	/** A Pillar Man in Heat mode looking south (+z) with no gravity, holding one key; its actions are ticked here. */
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
			data.setMode(PillarmanMode.HEAT, user);
			refill();
		}

		private void refill() {
			data.setEnergy(user, 300.0F);
			data.setAbilityCooldown(PRISON, 0);
			data.setAbilityCooldown(DETONATION, 0);
			helper.assertTrue(!data.isStoneFormEnabled() && data.getEnergy() == 300.0F && !data.isAbilityOnCooldown(PRISON)
					&& !data.isAbilityOnCooldown(DETONATION), "setup: the Heat Pillar Man is in stone form, short of energy or"
							+ " cooling down");
		}

		// set on the user as a press sets it, and held by its key
		private EntityActionInstance start(String name) {
			Ability ability = power.getAbility(name);
			helper.assertTrue(ability instanceof EntityActionAbility && ability.isAbilityUnlocked(power)
					&& ability.checkConditions(power).isPositive(), "setup: " + name + " is missing, locked or refused");
			EntityActionInstance action = ((EntityActionAbility) ability).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			helper.assertTrue(action != null, "setup: " + name + " made no action");
			actions.setAction(null, user, SyncType.NO_SYNC);
			actions.setAction(action, user, SyncType.NO_SYNC);
			EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, PowerClass.PLAYER_POWER, action));
			helper.assertTrue(action.getPhase() == ActionPhase.BUTTON_CHARGE, "setup: " + name + " did not start its charge");
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

		private List<PillarmanVeinEntity> veins() {
			return helper.getLevel().getEntitiesOfClass(PillarmanVeinEntity.class, user.getBoundingBox().inflate(8.0D),
					vein -> vein.getOwner() == user);
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
			veins().forEach(PillarmanVeinEntity::discard);
			user.removeAllEffects();
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
